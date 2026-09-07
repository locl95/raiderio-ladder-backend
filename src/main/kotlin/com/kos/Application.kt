package com.kos

import com.kos.activities.ActivitiesController
import com.kos.activities.ActivitiesService
import com.kos.activities.repository.ActivitiesDatabaseRepository
import com.kos.auth.AuthController
import com.kos.auth.AuthService
import com.kos.auth.repository.AuthDatabaseRepository
import com.kos.clients.RetryConfig
import com.kos.clients.blizzard.BlizzardHttpAuthClient
import com.kos.clients.blizzard.BlizzardHttpClient
import com.kos.clients.domain.BlizzardCredentials
import com.kos.clients.raiderio.RaiderIoHTTPClient
import com.kos.clients.riot.RiotHTTPClient
import com.kos.common.DatabaseFactory
import com.kos.common.JWTConfig
import com.kos.common.launchSubscription
import com.kos.credentials.CredentialsController
import com.kos.credentials.CredentialsService
import com.kos.credentials.repository.CredentialsDatabaseRepository
import com.kos.datacache.DataCacheService
import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.entities.EntitiesController
import com.kos.entities.EntitiesService
import com.kos.entities.EntityResolverProvider
import com.kos.entities.repository.EntitiesDatabaseRepository
import com.kos.entities.repository.wowguilds.WowGuildsDatabaseRepository
import com.kos.entities.sync.EntitySynchronizerProvider
import com.kos.entities.sync.SyncBudget
import com.kos.entities.sync.SyncEntitySelector
import com.kos.entities.sync.rules.StalenessSyncRule
import com.kos.eventsourcing.events.repository.EventStoreDatabase
import com.kos.eventsourcing.subscriptions.EventSubscription
import com.kos.eventsourcing.subscriptions.EventSubscriptionController
import com.kos.eventsourcing.subscriptions.EventSubscriptionService
import com.kos.eventsourcing.subscriptions.repository.SubscriptionsDatabaseRepository
import com.kos.eventsourcing.subscriptions.sync.EntitiesEventProcessor
import com.kos.eventsourcing.subscriptions.sync.GameSyncEventProcessor
import com.kos.eventsourcing.subscriptions.sync.ViewsEventProcessor
import com.kos.operations.OperationsController
import com.kos.operations.OperationsService
import com.kos.plugins.*
import com.kos.roles.RolesController
import com.kos.roles.RolesService
import com.kos.roles.repository.RolesActivitiesDatabaseRepository
import com.kos.roles.repository.RolesDatabaseRepository
import com.kos.sources.SourcesController
import com.kos.sources.SourcesService
import com.kos.sources.lol.LolEntityResolver
import com.kos.sources.lol.LolEntitySynchronizer
import com.kos.sources.lol.LolEntityUpdater
import com.kos.sources.wow.WowEntityResolver
import com.kos.sources.wow.WowEntitySynchronizer
import com.kos.sources.wow.WowGuildUpdater
import com.kos.sources.wow.staticdata.wowexpansion.repository.WowExpansionDatabaseRepository
import com.kos.sources.wow.staticdata.wowseason.WowSeasonService
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonDatabaseRepository
import com.kos.sources.wowhc.WowHardcoreEntityResolver
import com.kos.sources.wowhc.WowHardcoreEntitySynchronizer
import com.kos.sources.wowhc.WowHardcoreGuildUpdater
import com.kos.sources.wowhc.staticdata.wowitems.WowItemsDatabaseRepository
import com.kos.tasks.TaskType
import com.kos.tasks.TasksController
import com.kos.tasks.TasksLauncher
import com.kos.tasks.TasksService
import com.kos.tasks.repository.TasksDatabaseRepository
import com.kos.tasks.runners.*
import com.kos.views.Game
import com.kos.views.ViewsController
import com.kos.views.ViewsEventService
import com.kos.views.ViewsService
import com.kos.views.repository.ViewsDatabaseRepository
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

fun main() {
    embeddedServer(Netty, port = System.getenv("PORT")?.toInt() ?: 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val riotApiKey = System.getenv("RIOT_API_KEY")
    val raiderIoApiKey = System.getenv("RAIDERIO_API_KEY")

    val jwtConfig = JWTConfig(
        System.getenv("JWT_ISSUER"),
        System.getenv("JWT_SECRET")
    )

    val blizzardCredentials = BlizzardCredentials(
        System.getenv("BLIZZARD_CLIENT_ID"),
        System.getenv("BLIZZARD_CLIENT_SECRET")
    )

    val coroutineScope = CoroutineScope(Dispatchers.IO)

    val db = DatabaseFactory.pooledDatabase()

    val client = HttpClient(CIO)
    val defaultRetryConfig = RetryConfig(3, 1200)
    val raiderIoHTTPClient = RaiderIoHTTPClient(client, defaultRetryConfig, raiderIoApiKey)
    val riotHTTPClient = RiotHTTPClient(client, defaultRetryConfig, riotApiKey)
    val blizzardAuthClient = BlizzardHttpAuthClient(client, blizzardCredentials)
    val blizzardClient = BlizzardHttpClient(client, defaultRetryConfig, blizzardAuthClient)
    val wowItemsDatabaseRepository = WowItemsDatabaseRepository(db)

    val eventStore = EventStoreDatabase(db)

    val credentialsRepository = CredentialsDatabaseRepository(db)
    val credentialsService = CredentialsService(credentialsRepository)
    val credentialsController = CredentialsController(credentialsService)

    val activitiesRepository = ActivitiesDatabaseRepository(db)
    val activitiesService = ActivitiesService(activitiesRepository)
    val activitiesController = ActivitiesController(activitiesService)

    val rolesRepository = RolesDatabaseRepository(db)
    val rolesActivitiesRepository = RolesActivitiesDatabaseRepository(db)
    val rolesService = RolesService(rolesRepository, rolesActivitiesRepository)
    val rolesController = RolesController(rolesService)

    val authRepository = AuthDatabaseRepository(db)
    val authService = AuthService(authRepository, credentialsService, rolesService, jwtConfig)
    val authController = AuthController(authService)

    val entitiesRepository = EntitiesDatabaseRepository(db)
    val wowGuildsDatabaseRepository = WowGuildsDatabaseRepository(db)

    val wowResolver = WowEntityResolver(entitiesRepository, raiderIoHTTPClient, blizzardClient)
    val wowHardcoreResolver = WowHardcoreEntityResolver(entitiesRepository, blizzardClient)
    val lolResolver = LolEntityResolver(entitiesRepository, riotHTTPClient)
    val entityResolverProvider = EntityResolverProvider(
        wowResolver = wowResolver,
        wowHardcoreResolver = wowHardcoreResolver,
        lolResolver = lolResolver
    )

    val lolUpdater = LolEntityUpdater(riotHTTPClient, entitiesRepository)

    val viewsRepository = ViewsDatabaseRepository(db)
    val dataCacheRepository = DataCacheDatabaseRepository(db)

    val seasonRepository = WowSeasonDatabaseRepository(db)
    val staticDataRepository = WowExpansionDatabaseRepository(db)
    val wowSeasonService = WowSeasonService(staticDataRepository, seasonRepository, raiderIoHTTPClient)

    val lolEntitySynchronizer = LolEntitySynchronizer(dataCacheRepository, riotHTTPClient)
    val wowHardcoreEntitySynchronizer = WowHardcoreEntitySynchronizer(
        dataCacheRepository,
        entitiesRepository,
        raiderIoHTTPClient,
        blizzardClient,
        wowItemsDatabaseRepository,
        System.getenv("WOW_HC_SYNC_CONCURRENCY")?.toInt() ?: 10,
    )
    val wowEntitySynchronizer = WowEntitySynchronizer(
        dataCacheRepository,
        raiderIoHTTPClient,
        seasonRepository,
        System.getenv("WOW_SYNC_CONCURRENCY")?.toInt() ?: 10,
    )

    val entitySynchronizerProvider =
        EntitySynchronizerProvider(
            wowSynchronizer = wowEntitySynchronizer,
            wowHardcoreSynchronizer = wowHardcoreEntitySynchronizer,
            lolSynchronizer = lolEntitySynchronizer
        )

    val dataCacheService =
        DataCacheService(
            dataCacheRepository,
            entitiesRepository,
            eventStore
        )

    val wowHardcoreGuildUpdater = WowHardcoreGuildUpdater(wowHardcoreResolver, entitiesRepository, viewsRepository)
    val wowGuildUpdater = WowGuildUpdater(wowResolver, entitiesRepository, viewsRepository)

    val entitiesService = EntitiesService(
        entitiesRepository,
        wowGuildsDatabaseRepository,
        entityResolverProvider,
        lolUpdater,
        wowHardcoreGuildUpdater,
        wowGuildUpdater
    )
    //TODO: This feels weird. Probably the responsibility of getOrSync should be on EntitiesService rather than DataCacheService
    val entitiesController = EntitiesController(dataCacheService, entitiesService)

    val viewsService =
        ViewsService(
            viewsRepository,
            entitiesService,
            dataCacheService,
            credentialsService,
            eventStore
        )
    val viewsEventService = ViewsEventService(viewsRepository, entitiesService, eventStore)
    val viewsController = ViewsController(viewsService)

    val operationsService = OperationsService(eventStore)
    val operationsController = OperationsController(operationsService)

    val sourcesService = SourcesService(wowSeasonService)
    val sourcesController = SourcesController(sourcesService)

    val stalenessRule = StalenessSyncRule(
        entitiesRepository,
        30,
        SyncBudget(
            mapOf(
                Game.LOL to (System.getenv("LOL_SYNC_BUDGET")?.toInt() ?: 65),
                Game.WOW to (System.getenv("WOW_SYNC_BUDGET")?.toInt() ?: 3300),
                Game.WOW_HC to (System.getenv("WOW_HC_SYNC_BUDGET")?.toInt() ?: 4860)
            )
        )
    )
    val syncEntitySelector = SyncEntitySelector(stalenessRule)

    val executorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    val tasksRepository = TasksDatabaseRepository(db)
    val subscriptionsRepository = SubscriptionsDatabaseRepository(db)

    val taskRunnerProvider = TaskRunnerProvider(
        listOf(
            TokenCleanupTaskRunner(tasksRepository, authService),
            TaskCleanupTaskRunner(tasksRepository),
            EventCleanupTaskRunner(tasksRepository, eventStore, subscriptionsRepository),
            UpdateLolEntitiesTaskRunner(tasksRepository, entitiesService),
            CacheClearTaskRunner(tasksRepository, dataCacheService),
            UpdateWowHardcoreGuildsTaskRunner(tasksRepository, entitiesService),
            UpdateWowGuildsTaskRunner(tasksRepository, entitiesService),
            UpdateMythicPlusSeasonTaskRunner(tasksRepository, wowSeasonService),
            CacheGameDataTaskRunner(
                Game.LOL,
                TaskType.CACHE_LOL_DATA_TASK,
                tasksRepository,
                syncEntitySelector,
                entitySynchronizerProvider
            ),
            CacheGameDataTaskRunner(
                Game.WOW,
                TaskType.CACHE_WOW_DATA_TASK,
                tasksRepository,
                syncEntitySelector,
                entitySynchronizerProvider
            ),
            CacheGameDataTaskRunner(
                Game.WOW_HC,
                TaskType.CACHE_WOW_HC_DATA_TASK,
                tasksRepository,
                syncEntitySelector,
                entitySynchronizerProvider
            ),
            CacheGameViewDataTaskRunner(
                tasksRepository,
                viewsService,
                entitiesService,
                entitySynchronizerProvider,
                System.getenv("VIEW_SYNC_COOLDOWN_SECONDS")?.toLong() ?: 300L
            )
        )
    )
    val tasksService = TasksService(tasksRepository, taskRunnerProvider)
    val tasksLauncher =
        TasksLauncher(tasksService, tasksRepository, executorService, authService, dataCacheService, coroutineScope)
    val tasksController = TasksController(tasksService)

    coroutineScope.launch { tasksLauncher.launchTasks() }

    val eventSubscriptionsService = EventSubscriptionService(subscriptionsRepository)
    val eventSubscriptionController = EventSubscriptionController(eventSubscriptionsService)

    val viewsEventSubscription = EventSubscription(
        "views",
        eventStore,
        subscriptionsRepository
    ) { ViewsEventProcessor(it, viewsEventService).process() }

    val syncLolEventSubscription = EventSubscription(
        "sync-lol",
        eventStore,
        subscriptionsRepository
    ) { GameSyncEventProcessor(it, entitiesService, lolEntitySynchronizer, eventStore).process() }

    val syncWowEventSubscription = EventSubscription(
        "sync-wow",
        eventStore,
        subscriptionsRepository
    ) { GameSyncEventProcessor(it, entitiesService, wowEntitySynchronizer, eventStore).process() }

    val syncWowHardcoreEventSubscription = EventSubscription(
        "sync-wow-hc",
        eventStore,
        subscriptionsRepository
    ) { GameSyncEventProcessor(it, entitiesService, wowHardcoreEntitySynchronizer, eventStore).process() }

    val entitiesEventSubscription = EventSubscription(
        "entities",
        eventStore,
        subscriptionsRepository
    ) { EntitiesEventProcessor(it, entitiesService).process() }

    launchSubscription(viewsEventSubscription)
    launchSubscription(syncLolEventSubscription)
    launchSubscription(syncWowEventSubscription)
    launchSubscription(syncWowHardcoreEventSubscription)
    launchSubscription(entitiesEventSubscription)

    configureAuthentication(credentialsService, jwtConfig)
    configureCors()
    configureRouting(
        activitiesController,
        authController,
        credentialsController,
        rolesController,
        viewsController,
        tasksController,
        eventSubscriptionController,
        entitiesController,
        sourcesController,
        operationsController
    )
    configureSerialization()
    configureLogging()
}
