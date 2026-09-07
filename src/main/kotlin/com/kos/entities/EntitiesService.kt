package com.kos.entities

import arrow.core.Either
import com.kos.common.WithLogger
import com.kos.common.error.RepositoryError
import com.kos.common.error.ServiceError
import com.kos.entities.domain.*
import com.kos.entities.repository.EntitiesRepository
import com.kos.entities.repository.wowguilds.WowGuildsRepository
import com.kos.sources.lol.LolEntityUpdater
import com.kos.sources.wow.WowGuildUpdater
import com.kos.sources.wowhc.WowHardcoreGuildUpdater
import com.kos.views.Game
import com.kos.views.GuildArgs
import com.kos.views.ViewExtraArguments
import com.kos.views.WowExtraArguments

data class EntitiesService(
    private val entitiesRepository: EntitiesRepository,
    private val wowGuildsRepository: WowGuildsRepository,
    private val entitiesResolverProvider: EntityResolverProvider,
    private val lolUpdater: LolEntityUpdater,
    private val wowHardcoreGuildUpdater: WowHardcoreGuildUpdater,
    private val wowGuildUpdater: WowGuildUpdater,

    ) : WithLogger("EntitiesService") {

    suspend fun exists(
        requestedEntities: List<EntityRequest>,
        game: Game
    ): Either<ServiceError, EntitiesExistResponse> {
        return resolveEntities(requestedEntities, game)
            .map { resolved ->
                val exist =
                    resolved.entities.map { it.first.toRequest() } + resolved.existing.map { it.first.toRequest() }
                val unchecked = resolved.unchecked.map { it.first }
                val nonExisting = requestedEntities.filterNot { it in exist || it in unchecked }

                EntitiesExistResponse(
                    exist = exist.map { it.toResponse() },
                    nonExisting = nonExisting.map { it.toResponse() },
                    unchecked = unchecked.map { it.toResponse() }
                )
            }
    }

    suspend fun guildExists(name: String, region: String, realm: String): Either<ServiceError, GuildExistsResponse> {
        val guildRequest = WowEntityRequest(name, region, realm)
        val extraArguments = WowExtraArguments(season = 0, guild = GuildArgs.EXISTENCE)

        return resolveEntities(listOf(guildRequest), Game.WOW, extraArguments)
            .map { resolved -> GuildExistsResponse(resolved.guild) }
    }

    suspend fun resolveEntities(
        requestedEntities: List<EntityRequest>,
        game: Game,
        extraArguments: ViewExtraArguments? = null
    ): Either<ServiceError, ResolvedEntities> =
        entitiesResolverProvider.resolverFor(game).resolve(requestedEntities, extraArguments)

    suspend fun updateEntities(
        game: Game
    ): List<ServiceError> {
        @Suppress("UNCHECKED_CAST")
        return when (game) {
            Game.LOL -> lolUpdater.update(entitiesRepository.get(game) as List<LolEntity>)
            Game.WOW -> listOf()
            Game.WOW_HC -> listOf()
        }
    }

    suspend fun updateWowHardcoreGuilds(): List<ServiceError> {
        val guildsWithViewId = wowGuildsRepository.getGuilds(Game.WOW_HC)
        return wowHardcoreGuildUpdater.update(guildsWithViewId)
    }

    suspend fun updateWowGuilds(): List<ServiceError> {
        val guildsWithViewId = wowGuildsRepository.getGuilds(Game.WOW)
        return wowGuildUpdater.update(guildsWithViewId)
    }

    suspend fun get(id: Long, game: Game): Entity? = entitiesRepository.get(id, game)
    suspend fun get(game: Game): List<Entity> = entitiesRepository.get(game)

    suspend fun insert(entities: List<InsertEntityRequest>, game: Game) = entitiesRepository.insert(entities, game)
    suspend fun insertGuild(payload: GuildPayload, viewId: String, game: Game): Either<RepositoryError, Unit> =
        wowGuildsRepository.insertGuild(
            payload.blizzardId, payload.name, payload.realm, payload.region, viewId, game
        )

    suspend fun findTrackedGuild(name: String, realm: String, region: String, game: Game): Pair<GuildPayload, String>? =
        wowGuildsRepository.findTrackedGuild(name, realm, region, game)

    suspend fun getViewsFromEntity(id: Long, game: Game?): List<String> =
        entitiesRepository.getViewsFromEntity(id, game)

    suspend fun delete(id: Long): Unit = entitiesRepository.delete(id)
}