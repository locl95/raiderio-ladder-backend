package com.kos.entities

import arrow.core.Either
import com.kos.activities.Activities
import com.kos.activities.Activity
import com.kos.common.error.ControllerError
import com.kos.common.error.EntityError
import com.kos.common.error.NotAuthorized
import com.kos.common.error.NotEnoughPermissions
import com.kos.datacache.DataCacheService
import com.kos.entities.domain.EntitiesExistResponse
import com.kos.entities.domain.EntityDataResponse
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.GuildExistsResponse
import com.kos.views.Game

class EntitiesController(
    private val dataCacheService: DataCacheService,
    private val entitiesService: EntitiesService
) {
    suspend fun getEntityData(
        client: String?,
        activities: Set<Activity>,
        maybeSearchRequestAndGame: Pair<EntityRequest, Game>
    ): Either<ControllerError, EntityDataResponse> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> {
                if (activities.contains(Activities.searchEntity)) {
                    dataCacheService.getOrSync(maybeSearchRequestAndGame)
                        .mapLeft { EntityError(it.error()) }
                } else Either.Left(NotEnoughPermissions(client))
            }
        }
    }

    suspend fun exists(
        client: String?,
        activities: Set<Activity>,
        entities: List<EntityRequest>,
        game: Game,
    ): Either<ControllerError, EntitiesExistResponse> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> {
                if (activities.contains(Activities.checkEntitiesExist))
                    entitiesService.exists(entities, game).mapLeft { EntityError(it.error()) }
                else Either.Left(NotEnoughPermissions(client))
            }
        }
    }

    suspend fun guildExists(
        client: String?,
        activities: Set<Activity>,
        name: String,
        region: String,
        realm: String,
    ): Either<ControllerError, GuildExistsResponse> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> {
                if (activities.contains(Activities.checkEntitiesExist))
                    entitiesService.guildExists(name, region, realm).mapLeft { EntityError(it.error()) }
                else Either.Left(NotEnoughPermissions(client))
            }
        }
    }
}