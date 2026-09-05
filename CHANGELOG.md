# Changelog

## [5.12.0] - 06-09-2026

### Added

- **`EVENT_CLEANUP_TASK`**: new scheduled task, run every 3 days, that purges old rows from the `events` table to keep
  it bounded on a resource-constrained Postgres plan.
    - New `events.timestamp` column (migration `V1788644922`), plus an index — `version` alone (a monotonic sequence)
      carried no wall-clock information to filter by age.
    - `EventStore.deleteEventsOlderThan(timestamp, upToVersion)` deletes only rows that are both older than the cutoff
      and at or below `upToVersion`, implemented in `EventStoreDatabase` and `EventStoreInMemory`.
    - `EventCleanupTaskRunner` computes `upToVersion` as the minimum `version` already processed across all registered
      `EventSubscription`s (`views`/`sync-lol`/`sync-wow`/`sync-wow-hc`/`entities`), so an event is never deleted
      before every subscription has consumed it — guards against silent event loss if a subscription falls behind or
      a future one needs to replay from the beginning.

## [5.11.0] - 29-08-2026

### Added

- **WoW retail guild views**:
    - `WowEntityResolver` now supports `isGuild` for WOW (previously only WOW_HC), resolving the current roster from
      Blizzard's retail guild endpoint (new `BlizzardClient.getRetailGuildRoster()`) and filtering to max-level (90)
      characters.
    - Guild members are only tracked if they hold a RaiderIO mythic+ score — new `RaiderIoClient.getScore()` — rather
      than merely existing. Characters with no score are reported as `unchecked` (`NotCompetitiveCharacter`) instead
      of being silently dropped or included.
- **`UPDATE_WOW_GUILDS` task**:
    - New `UpdateWowGuildsTaskRunner` + `WowGuildUpdater`, scheduled daily like the existing
      `UPDATE_WOW_HARDCORE_GUILDS`. Refreshes each tracked WOW guild view's roster: inserts newly-qualifying members
      and disassociates entities that have left the guild since the last sync.

### Changed

- **`wow_guild` / `wow_hardcore_guild` tables merged**: both guild types are now stored in a single `wow_guild` table,
  distinguished by `game` (`WOW` vs `WOW_HC`). `WowGuildsRepository` (`insertGuild`/`getGuilds`) now takes/filters by
  `game` accordingly.

### Fixed

- **RaiderIO "character not found" no longer misclassified in `getScore()`**: the same disambiguation `exists()`
  already had (a 400 with "Could not find requested character" in the body isn't a real failure) is now applied to
  `getScore()` too — resolves to a score of `0.0` instead of noisy retry-abort logging and an incorrect `unchecked`
  result.
- **WOW_HC roster resolution could crash on mixed-case character names**: `WowHardcoreEntityResolver` built lookup
  requests directly from Blizzard's roster response, which preserves the original name casing, surfacing as a
  `NullPointerException` inside `resolve()`. Character names from the roster are now normalized to lowercase before
  use, matching how WOW character names are stored everywhere else.

## [5.10.0] - 20-08-2026

### Added

- **`POST /entities/exists`**:
    - Checks whether a batch of characters exist for a game (WOW/LOL/WOW_HC) before adding them to a view, reusing each
      game's existing `EntityResolver`. Response is `{exist, nonExisting, unchecked}` — `unchecked` (WOW only so far)
      separates "confirmed doesn't exist" from "couldn't confirm due to a third-party error", so a transient RaiderIO
      hiccup no longer gets reported as a nonexistent character.
    - Gated by a new `check entities exist` activity.
    - `CreateEntityRequest` renamed to `EntityRequest` project-wide (it now also backs search and existence-checking,
      not just creation) — pure rename, no wire-format change.

### Fixed

- **Third-party request timeouts surfaced as unhandled exceptions**: `fetchFromApi` now catches
  `HttpRequestTimeoutException` and maps it to a modeled `TimeoutError` instead of letting it escape as a raw exception.
- **RaiderIO "character not found" was indistinguishable from a malformed request**: both return HTTP 400.
  `RaiderIoClient.exists()` now returns `Either<ClientError, Boolean>` (previously a bare `Boolean` that silently
  treated any failure as "doesn't exist") and disambiguates "not found" by checking the response body's message text.

### Improved

- **Resolver concurrency**: `WowEntityResolver`/`WowHardcoreEntityResolver`/`LolEntityResolver.resolve()` and
  `EntityResolver.getCurrentAndNewEntities` now use bounded `arrow.fx.coroutines.parMap` instead of unbounded
  `map { async {} }.awaitAll()`, closing the same class of unbounded-concurrency risk previously fixed in the sync
  layer — WOW_HC could otherwise cancel an entire batch via one timed-out Blizzard call.

### Removed

- Unused exponential-backoff retry helper (`Retry.retryEitherWithExponentialBackoff`) — dead code, no caller.

## [5.9.0] - 12-08-2026

### Fixed

- **Event subscriptions could wedge forever on a bad event**:
    - A processing exception used to leave the subscription's cursor rewound to the same event, retrying it indefinitely
      and blocking every event behind it. The exponential-backoff retry wrapper around `process()` was removed — it was
      dead code (no processor ever returned a modeled failure, and thrown exceptions bypassed it anyway) and retrying
      was the wrong instinct regardless: a failure here means something is broken in the event's own data, not a
      transient external condition.
    - Every failure (thrown or modeled) now produces a terminal, redeliverable `OperationFailedEvent`, and the
      subscription's cursor advances past it instead of stalling.
- **Operation status could be permanently poisoned by a stale failure**:
    - `OperationsService.getOperationStatus` used to let any past `OperationFailedEvent` mark an operation `FAILED`
      forever, even if a later redelivery completed it successfully. Completion now always wins over a stale failure.
- **Graceful shutdown could be misrecorded as a processing failure**:
    - `CancellationException`, thrown when a subscription's coroutine is cancelled on shutdown, is a `RuntimeException`
      subtype and was being caught by the same handler as real failures. It's now excluded via Arrow's `NonFatal`,
      matching how `Either.catch` already treats it.

### Improved

- **Failure recording centralized into `EventSubscription`**:
    - Processors (`ViewsEventProcessor`, `EntitiesEventProcessor`, `GameSyncEventProcessor`) no longer hand-construct
      failure events — they just propagate `Either<ServiceError, EventProcessOutcome>`, and
      `EventSubscription.processPendingEvents` is now the single place that records failures and logs outcomes.
    - Added `EventProcessOutcome` (`Processed`/`Skipped`) so a processor that ignores an event it doesn't own is
      distinguished from one that actually did work — fixing misleading `INFO ... processed successfully` logs that
      previously fired from every subscription (`sync-wow`, `sync-wow-hc`, `sync-lol`, etc.) even for events irrelevant
      to them.

## [5.8.0] - 07-08-2026

### Improved

- **WoW and WoW HC sync now process entities concurrently**:
    - Replaced the sequential `buffer(10).collect{}` entity loop (which never actually ran entities in parallel despite
      the naming) with `arrow.fx.coroutines.parMap`, bounded by `WOW_SYNC_CONCURRENCY` / `WOW_HC_SYNC_CONCURRENCY`
      (default 10 each).
    - A rate-limiter timeout (`RequestNotPermitted`) on one entity no longer aborts the whole sync batch — it's now
      isolated as a per-entity error like any other sync failure.
- **Database connection pool size increased**:
    - `HikariConfig.maximumPoolSize` default raised from 3 to 10, configurable via `POSTGRES_MAX_POOL_SIZE`. The old
      hardcoded value was shared across all HTTP traffic and background sync work, causing endpoints to stall during
      sync.

### Fixed

- **Concurrent WoW sync crash on shared cached data**:
    - Entities sharing a cached RaiderIO mythic+ run could crash the sync with
      `IllegalStateException: Flow invariant is violated` once entities were processed concurrently. Fixed by using
      `parMap`'s ordered variant instead of `parMapUnordered`, which avoids emitting from within the racing coroutine.

## [5.7.0] - 29-07-2026

### Added

- **Per-game sync budget cap**:
    - `getEntitiesOlderThan(game, olderThanMinutes, maxEntities)` now caps how many entities are selected per sync
      cycle, per game, prioritizing never-synced entities over stale ones.
    - Configurable via `LOL_SYNC_BUDGET` / `WOW_SYNC_BUDGET` / `WOW_HC_SYNC_BUDGET` env vars (defaults 65 / 3300 /
      4860).
    - New `data_cache_game_entity_id_inserted_idx` index supports the ordering/truncation at the query level.
- WoW HC sync is now also filtered by the 30-minute staleness threshold, matching WoW/LoL (previously synced
  unconditionally every cycle).

### Refactor

- `CacheLolDataTaskRunner` / `CacheWowDataTaskRunner` / `CacheWowHcDataTaskRunner` merged into a single
  `CacheGameDataTaskRunner(game, type, ...)`.

## [5.6.0] - 12-07-2026

### Improved

- **`RaiderIoHTTPClient` rate limiting and API key support**:
    - All requests now include an `access_key` query parameter sourced from the `RAIDERIO_API_KEY` environment variable.
    - A Resilience4j `RateLimiter` (1000 requests/min, 5s timeout) is applied transparently via a single internal
      `apiGet()` wrapper, covering all client methods without changes at the call site.
- **WoW and WoW HC sync refactored to channel-based concurrent pattern**:
    - Both synchronizers now use a producer/consumer architecture with `asFlow().buffer(10)` for concurrent entity
      fetching and a `Channel<DataCache>` with `buffer(50)` for decoupled DB writes.
    - Per-entity errors are isolated — a failed sync for one entity no longer aborts the rest.
    - Season and cutoff data are fetched once upfront for all entities rather than once per entity.
- **30-minute cache filter applied to WoW entity selection**:
    - `getEntitiesOlderThan` for WoW now skips entities synced within the last 30 minutes, matching the existing LoL
      behaviour. Entities with no cache entry are always included.

## [5.5.0] - 18-05-2026

### Added

- **`POST /api/tasks` now returns the task ID in the response body**:
    - The response body is `{"id": "<taskId>"}` so consumers can poll `GET /api/tasks/{id}` without parsing the
      `Location` header.
- **`CACHE_GAME_VIEW_DATA_TASK` cooldown support**:
    - Returns `ERROR` with a `retryAfter` timestamp if the view was synced within the cooldown window (default 300s,
      configurable via `VIEW_SYNC_COOLDOWN_SECONDS`).
    - Returns `SUCCESSFUL` with a `retryAfter` timestamp indicating when the next sync is allowed, so the frontend can
      disable the sync button accordingly.
- **`SERVICE` role can now run and query tasks**:
    - Granted the `run task`, `get task`, and `get tasks` activities to the `service` role via DB migration.

### Refactor

- **`CacheGameViewDataTaskRunner` validation uses Arrow `either`**:
    - Early-exit validation (missing `viewId`, view not found, cooldown active) is now expressed as an `either` block
      with `raise`/`ensure`, replacing nested `if`/`return` imperative checks.

## [5.4.0] - 17-05-2026

### Added

- **`CACHE_GAME_VIEW_DATA_TASK`**:
    - New task that syncs entity data for a specific view, identified by a `viewId` argument.
    - The game is inferred from the view itself — no game argument needed.
    - Returns `ERROR` if `viewId` is missing or the view does not exist.

### Refactor

- **`TaskRunner` abstraction**:
    - Extracted all task logic out of `TasksService` into dedicated `TaskRunner` implementations, one per task type.
    - `TasksService` is now a thin dispatcher (~20 lines) delegating to a `TaskRunnerProvider`.
    - Abstract `CacheGameDataTaskRunner(game: Game)` base class shared by `CacheLolDataTaskRunner`,
      `CacheWowDataTaskRunner`, and `CacheWowHcDataTaskRunner`.
- **`ScheduledTaskRunnable` consolidates four identical runnables**:
    - `TokenCleanupRunnable`, `TasksCleanupRunnable`, `UpdateLolEntitiesRunnable`, and `UpdateWowGuildsRunnable`
      replaced by a single generic `ScheduledTaskRunnable`.

### Fixed

- **`CACHE_CLEAR_TASK` with no game argument now clears all caches**:
    - Previously returned `ERROR` when no game was provided. A null game is now correctly forwarded to
      `clearCache(null)`, which clears data for all games.

## [5.3.0] - 12-05-2026

### Added

- **Operation Status Endpoint** (`GET /api/operations/{id}`):
    - Introduced a new endpoint to check the status of an async operation by its ID.
    - Requires JWT authentication and the `get operation status` activity.
    - Returns a JSON object with the following shape:
        ```json
        {
          "id": "<operationId>",
          "status": "PENDING | COMPLETED | FAILED",
          "resourceId": "<viewId, if completed>",
          "reason": "<failure reason, if failed>"
        }
        ```
    - `PENDING` — the operation has been queued but not yet resolved.
    - `COMPLETED` — the operation finished successfully; `resourceId` contains the ID of the created or synced view.
    - `FAILED` — the operation could not be completed; `reason` describes why.
    - Returns `404` if no events are found for the given operation ID.

### Refactor

- **`GameSyncEventProcessor` consolidates game-specific processors**:
    - `LolEventProcessor`, `WowEventProcessor`, and `WowHardcoreEventProcessor` have been merged into a single
      `GameSyncEventProcessor`, removing ~380 lines of duplicated synchronization logic.

## [5.2.0] - 17-04-2026

### Improved

- **`WowEntitySynchronizer` avoids redundant `getRunDetails` calls across syncs**:
    - `RunDetails` (roster composition, death logs) for a Mythic+ run don't change between syncs. Previously, every
      synchronization called `raiderIoClient.getRunDetails` for every best run of every character unconditionally.
    - The synchronizer now loads the most recent cached `RaiderIoData` for each entity before enriching runs. Runs
      already present in the persistent cache skip the third-party call entirely and reuse the stored details.
    - Runs not found in the persistent cache are still fetched via the existing `DynamicCache`, which deduplicates
      requests within a single sync for runs shared across multiple entities.

## [5.1.0] - 02-04-2026

### Added

- **Run details embedded in `WowEntitySynchronizer`**:
    - The synchronizer now fetches Mythic+ run details from Raider.IO for each best run during entity synchronization,
      so the frontend no longer needs a dedicated API call.
    - Run details (roster with character name, class, spec, realm, region, role, score, and death count) are stored in
      `RaiderIoData.mythicPlusBestRuns` as `EnrichedMythicPlusRun(run=..., details=...)` rather than embedded inside
      `MythicPlusRun`.
    - A `DynamicCache` is used to deduplicate requests — since multiple characters can share the same keystone run ID,
      each unique run is fetched only once per sync cycle. Hit rate is logged at debug level.
    - If a run detail fetch fails, the sync continues with `details` set to `null` for the affected
      `EnrichedMythicPlusRun` (logged as a warning; not propagated as a task error). If the current season is
      unavailable, run detail fetching is skipped entirely and all runs are stored with `details = null`, but profile
      caching proceeds normally.
- **Extended `MythicPlusRun` fields**:
    - Added `keystone_run_id` (`runId`), `completed_at` (`completionTime`), and `clear_time_ms` (`clearTimeMs`) to the
      Raider.IO run domain model.
- **Season slug support**:
    - Added `slug` field to the `Season` and `WowSeason` domain models.
    - Season slug is now persisted to the database during the Mythic+ season sync task.
    - Added DB migration to add the `slug` column to the `mythic_plus_seasons` table.

### Fixed

- **Nullable `logged_details` in run details response**:
    - `logged_details` returned by Raider.IO can be `null` for unlogged runs. The field is now correctly treated as
      nullable, preventing a JSON parse error for those runs. Death count defaults to `0` when `logged_details` is
      absent.

## [5.0.0] - 20-01-2026

### Refactor

- **Improved Error Handling Across Layers**:
    - Refactored error handling between **client**, **service**, and **controller** layers to ensure consistent error
      propagation and mapping.

## [4.5.1] - 05-12-2025

### Added

- **Mythic+ Season Sync Task**:
    - Implemented a new task that can be **manually triggered via controller** to retrieve the **current Mythic+
      season** of the active World of Warcraft expansion.
    - When executed, the task updates the database with season-related information, including:
        - The **current dungeon pool**
        - Any additional season metadata exposed by the Blizzard API
    - Ensures that tracked Mythic+ data remains aligned with the official live season.

## [4.5.0] - 18-11-2025

### Added

- **Mythic+ Season Sync Task**:
    - Implemented a new task that can be **manually triggered via controller** to retrieve the **current Mythic+
      season** of the active World of Warcraft expansion.
    - When executed, the task updates the database with season-related information, including:
        - The **current dungeon pool**
        - Any additional season metadata exposed by the Blizzard API
    - Ensures that tracked Mythic+ data remains aligned with the official live season.

### Added

- **Extra Arguments Support for WoW & WoW Hardcore Views**:
    - Added the ability to include **extraArguments** when creating new views for **World of Warcraft** and **World of
      Warcraft Hardcore**.
    - This enables specifying additional configuration such as:
      ```json
      "extraArguments": {
          "type": "com.kos.views.WowExtraArguments",
          "season": 15,
          "isGuild": false
      }
      ```
    - The system now correctly validates these arguments according to the selected game type.

## [4.4.0] - 22-02-2025

### Added

- **Search Individual Entities Endpoint** (`GET /api/entities`):
    - Introduced a new endpoint to search for **individual entities** rather than only tracking groups through views.
    - Supports searching for **World of Warcraft (WOW), WoW Hardcore (WOW_HC), and League of Legends (LOL)** entities.
    - **Required query parameters depend on the game type**:
        - **WoW & WoW Hardcore** → `name`, `region`, `realm`
        - **LoL** → `name`, `tag`
        - `game` parameter is always required (Possible values are `wow`, `wow_hc` and `lol`)
    - If required parameters are missing or an unknown game is provided, the API returns `400 Bad Request`.

## [4.3.3] 25-01-2025

### Added

- **Feature Alias**: Added a feature to define aliases for entities associated with views.
    - This allows users to create and manage custom names for entities, enhancing clarity and usability when working
      with complex data structures or multiple entities in a view.

## [4.3.2] 16-01-2025

### Added

- **League of Legends API Update**: The League of Legends API now retrieves the match-up for every game, allowing users
  to view the match-up information for each character in every game.

## [4.3.1] 10-01-2025

### Added

- **WoW Classic Characters Differentiation**: Characters of WoW Classic with the same name, server, and region are now
  uniquely identified using the internal Blizzard identifier.
    - This ensures accurate handling and distinction of characters sharing similar attributes.

### Fixed

- **WoW Hardcore Characters Death Flagging**:
    - **Non-Existent Characters**: Hardcore characters that do not exist are now flagged as dead.
    - **API Removal Handling**: Characters returning a `404` from the Blizzard API are flagged as dead, reflecting their
      removal from the Blizzard database.
    - **Repository Cleanup**: Characters returning a `404` are removed from the characters repository if no record
      exists for them in the cache.
        - This prevents stale or invalid entries from persisting in the database.

## [4.3.0] 22-12-2024

### Added

- **Metadata in Get Views Endpoint**: Introduced support for including metadata in the `Get Views` endpoint. By using
  the `include` parameter with the value `metadata`, users can now receive additional information, such as the total
  number of views. This is especially useful for users implementing pagination.

### Fixed

- **Featured Views Query Parameter**: Resolved an issue where the `featured` query parameter in the `Get Views` endpoint
  was not functioning as intended.

## [4.2.3] 17-12-2024

- **Limit and pagination added for Get Views Endpoint**:

## [4.2.2] 15-12-2024

### Added

- **Queue Status Endpoint**: Introduced a new endpoint to check the status of queues.
    - This feature provides visibility into the progress and state of queued events.

## [4.2.1] 12-12-2024

### Added

- **Featured Filter for Get Views Endpoint**: Added a new filter to the Get Views endpoint, enabling the retrieval of
  featured views from all games or a specific game. This enhancement allows users to quickly access highlighted views.

## [4.2.0] 15-11-2024

### Added

- **WoW Hardcore Views**: Introduced support for **World of Warcraft Hardcore Views**, allowing users to create and
  manage views specifically for hardcore characters.
- **Event Sourcing for WoW Characters**: Synchronization for WoW characters, including **Mythic+** and **Hardcore**, is
  now handled via event sourcing, improving efficiency and scalability.

## [4.1.1] 15-11-2024

### Added

- **Game-Based View Filtering**: Introduced a new filter to retrieve views specific to a particular game.
    - This enhancement improves user experience by allowing targeted retrieval of views for games like "World of
      Warcraft" or "League of Legends."

## [4.1.0] 12-11-2024

### Changed

- **Credentials System Update**: Enhanced the credentials management system with new requirements and modifications:
    - **Create Credential**: Now requires a set of roles to be provided in the request, ensuring that each credential is
      created with defined permissions.
    - **Edit Credential**: Endpoint updated to `/credentials/{user}` (previously `/credentials`). This operation now
      requires both `password` and `roles` to be included in the request.
    - **Patch Credential**: Introduced a new `PATCH` endpoint for credentials, similar to the edit functionality but
      with flexibility—fields such as `password` and `roles` can be optionally included.

### Removed

- **Activity and Role Management**: Removed the ability to create or delete activities and roles directly, streamlining
  the credential's system.

## [4.0.1] 10-11-2024

### Changed

- **League of Legends Background Sync**: Now league of legend background sync is optimized and only syncs characters
  that have not been synced by any other source.

## [4.0.0] 09-11-2024

### Added

- **Event Sourcing Implementation**: Introduced a major architecture change with event sourcing for resource management.
  Previously, creating large views was not sustainable, as it required waiting for external systems to respond before
  proceeding. Now, when a user creates a view, an operation is queued, and an operation ID is returned, which will be
  used to track the status of the requested action over the resource.
- **View Creation Process**: Views will be created once the subscriptions process the queued events, improving the
  overall efficiency of resource handling and allowing for better scalability.
- **Queue System for Syncing League of Legends Characters**: League of Legends view updates now send characters for
  updates via queues, in addition to the background task. This ensures that views can be populated faster, as characters
  receive individual updates immediately, instead of waiting for a scheduled or forced background task to run.

### Changed

- **JWT-Based Authentication System**: Replaced the existing token system with JSON Web Tokens (JWT) to enhance
  authentication efficiency and reduce database load.
    - **Self-Contained Permissions**: Permissions are now embedded directly within the JWT, removing the need to query
      the database for permission checks on each request.
    - **Improved Performance**: This change significantly improves response times for authenticated requests by reducing
      dependency on database lookups for role-based access validation.
    - **Security Enhancements**: JWTs are securely signed, ensuring token authenticity and integrity without frequent
      database validation.

## [3.5.1] 04-11-2024

### Added

- **Character Limit by Role in Views**: Introduced a new feature that limits the maximum number of characters allowed
  per view based on user roles.

## [3.5.0] 03-11-2024

### Added

- Introduced a daily update for League characters to refresh summoner details, including summoner icon, summoner level,
  Riot name, and Riot tag every 24 hours.
- Optimized further the League Character's Sync by allowing the reuse of match data across multiple players in the same
  synchronization batch, leveraging dynamic programming to minimize calls. While this may not drastically increase
  capacity, it significantly improves efficiency in the synchronization process.

### Changed

- Updated the `getData` and `getCachedData` endpoints to include the `viewName` in the response. This change may break
  integration with existing frontends expecting the previous response format.

## [3.4.1] 01-11-2024

### Improved

- Implemented a mechanism to reuse cached matches, significantly reducing unnecessary API calls and improving League
  caching time.

## [3.4.0] 31-10-2024

### Improved

- Enhanced caching service for League characters, allowing for larger views with a greater number of matches per
  character.
- Integrated `Flow` and `Channels` to optimize memory usage, ensuring more efficient handling of concurrent data
  streams.

## [3.3.0] 30-10-2024

### Added

- **Task Filtering by Type**: Introduced a new feature allowing tasks to be filtered by `taskType`.
    - This enhancement improves user control and efficiency by enabling targeted task retrieval.

- **Query Parameter Validation**: Added validation for query parameters to ensure data integrity and prevent potential
  errors.

## [3.2.0] 28-10-2024

### Changed

- **View Limit by Role**: Updated the view creation limit to be role-based instead of a fixed number:
    - **Admin** now have no limit on the number of views they can create.
    - **User** remain limited to a maximum of **2** views.

  This enhancement provides greater flexibility and control, especially for administrators managing multiple views.

## [3.1.0] 18-10-2024

### Added

- **Behavior Change**: The run task endpoint now returns the task ID in the `Location` header upon successful execution.

### Changed

- **Endpoint Update**: Changed endpoint from `POST /api/tasks/run` to `POST /api/tasks`.

## [3.0.1] 17-10-2024

### Fixed

- **League Match Retrieval Bug**: Resolved a bug that prevented League matches for cached characters from being
  retrieved correctly across different queue types.
    - This fix ensures accurate and reliable match data for all queue types, improving the integrity of character
      performance information.

## [3.0.0] 13-10-2024

### Added

- **Background Task Management API**: Implemented a comprehensive API for managing background tasks:
    - **Immediate Execution**: Added an endpoint to execute background tasks immediately.
    - **Task Retrieval**: Introduced endpoints to retrieve all tasks and to fetch a specific task by its ID.
        - This enhancement improves task management capabilities and provides users with better control and visibility
          over background processes.

### Changed

- **Task Execution Timing**: Modified the execution logic for background tasks so that they are not executed every time
  the server starts. Instead, the first execution is delayed, taking into account the last time each task was executed.

## [2.0.0] 05-10-2024

### Added

- **Multi-Game View Creation**: Expanded functionality to allow the creation of both World of Warcraft views and League
  of Legends views.
    - Users can now create, manage, and customize views for both games within the same application, enhancing
      versatility and user engagement.

- **New Field: Game**: Introduced a new field, "game," within the scope of views, which can be "WOW" or "LOL." This
  field is required in both create and edit requests for views.
- **Character Request Types**: Specified that the types `com.kos.characters.WowCharacterRequest`
  and `com.kos.characters.LolCharacterRequest` need to be sent as part of the view requests when adding characters due
  to a software limitation.

## [1.3.0] 30-09-2024

### Added

- **Background Task Execution Results Registration**: Enhanced the existing background task execution logging by now
  registering the results of each execution in the database.
    - This addition allows for detailed tracking of task outcomes, improving the monitoring and analysis capabilities of
      background processes.

## [1.2.0] 23-09-2024

### Added

- **PATCH Method for Views**: Implemented a PATCH feature over views with its endpoint.
    - Users can now modify view fields one by one, offering greater flexibility compared to the previous PUT method,
      which required submitting all fields.

## [1.1.0] 09-06-2024

### Added

- **View Publishing Feature**: Introduced the ability to publish views.
    - Views can now be marked as published, making them visible to everyone, or kept unpublished, allowing only the
      owner to view and edit them.

## [1.0.0] 25-04-2024

### Added

- **Role-Based Access Control**: Implemented endpoint protection by activities, where users are assigned roles
  containing the activities they can perform.
    - This change enhances application security and user management, allowing for fine-grained access control.

## [0.3.0] 24-01-2024

### Fixed

- **Character Duplication Bug**: Resolved a bug that allowed the creation of characters with the same name due to
  capitalization differences.
    - Now, character names are normalized to ensure uniqueness regardless of letter casing, preventing duplication.

## [0.2.0] 29-11-2023

### Added

- **Character Existence Check**: Implemented a validation step to check for the existence of characters in the external
  API before allowing their creation in the system.
    - This ensures that only valid and existing characters are created, improving data integrity and reducing potential
      errors.

## [0.1.9] 22-11-2023

### Changed

- **Cache Data Task Optimization**: Updated the character caching background task to retrieve data from the external API
  concurrently instead of sequentially.
    - This enhancement significantly improves the speed and efficiency of the caching process, reducing overall latency
      and resource consumption.

## [0.1.8] 17-11-2023

### Added

- **Background Tasks**: Implemented two new background tasks to improve application performance and maintenance:
    - **Character Caching Task**: A scheduled task to cache character data, enhancing retrieval speed and reducing load
      on the database.
    - **Expired Token Cleanup Task**: A scheduled task to regularly clean up expired tokens, ensuring efficient use of
      storage and maintaining security.

## [0.1.7] 11-11-2023

### Added

- **Password Encryption**: Implemented encryption for user passwords before storing them in the repository.
    - Passwords are now hashed using a secure algorithm, enhancing security and protecting user data.

## [0.1.6] 11-11-2023

### Added

- **Refresh Token Feature**: Implemented a new refresh token mechanism to enhance user authentication.
    - The login response now delivers two tokens:
        - **Access Token**: Used for authenticating API requests.
        - **Refresh Token**: Allows users to obtain a new access token without re-entering credentials.

### Changed

- **Login Response**: Updated the login endpoint to return both the access token and the refresh token, improving
  session management and security.

## [0.1.5] 08-11-2023

### Added

- **Access Control System**: Implemented a new authentication system using access tokens and user credentials.

### Changed

- **Endpoint Authorization**: Updated existing API endpoints to require authentication via access tokens or credentials.

## [0.1.4] 07-11-2023

### Added

- **Edit Password Endpoint**: Introduced a new endpoint `PUT /api/credentials` for users to update their passwords.

## [0.1.3] 04-11-2023

### Added

- **Delete View Feature**: Implemented the ability to delete views.
    - Users can now remove views they no longer need, enhancing view management capabilities.

## [0.1.2] 04-11-2023

### Added

- **Expired Token Prevention**: Implemented logic to prevent the use of expired tokens.
    - API endpoints now validate token expiration before processing requests, ensuring enhanced security and user
      experience.
- **Persistent Tokens**: Introduced persistent tokens that never expire.
    - These tokens allow users to maintain long-term sessions without needing to re-authenticate frequently.

## [0.1.1] 04-11-2023

### Added

- **Name Field for Views**: Introduced an extra field, **name**, for views.
    - This field is now required when creating and editing views, enhancing the identification and management of views.

## [0.1.0] 02-11-2023

### Fixed

- **View Editing Issue**: Resolved a problem that prevented users from editing views correctly.
    - The fix ensures that all necessary data is correctly loaded and updated during the edit process, enhancing user
      experience and functionality.

## [0.0.4] 01-11-2023

### Added

- **Dockerfile**: Created a Dockerfile to simplify the deployment process.
    - This Dockerfile allows for easy containerization of the application, ensuring consistent environments across
      different deployment targets.

## [0.0.3] 14-09-2023

### Added

- **Database Configuration**: Introduced configuration settings for connecting to the database, including connection
  strings and credentials.

### Changed

- **Repository Upgrade**: Transitioned the repository from a volatile in-memory storage to a real database.
    - This change enhances data persistence, reliability, and scalability.

## [0.0.2] 26-07-2024

### Added

- **Maximum Views Limit**: Set the maximum number of views that a user can create to **2**.
    - This limit is enforced to optimize resource usage and ensure fair access for all users.

## [0.0.1] 26-07-2024

### Added

- **Code Coverage Enforcement**: Implemented code coverage checks for the project.
    - Pull requests will now fail if tests do not cover at least **75%** of the code, ensuring better testing practices
      and higher code quality.

### Changed

- **Continuous Integration Configuration**: Updated CI pipeline to include code coverage metrics as part of the testing
  process.

## [0.0.0] 25-07-2023

### Added

- Initial release with core features.