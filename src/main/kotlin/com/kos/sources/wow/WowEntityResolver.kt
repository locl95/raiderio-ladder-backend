package com.kos.sources.wow

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.fx.coroutines.parMap
import com.kos.clients.HttpError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.domain.GetWowRosterResponse
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.toSyncProcessingError
import com.kos.common.WithLogger
import com.kos.common.collect
import com.kos.common.error.NotCompetitiveCharacter
import com.kos.common.error.ServiceError
import com.kos.common.split
import com.kos.entities.EntityResolver
import com.kos.entities.domain.*
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game
import com.kos.views.GuildArgs
import com.kos.views.ViewExtraArguments
import com.kos.views.WowExtraArguments
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class WowEntityResolver(
    private val repo: EntitiesRepository,
    private val raiderioClient: RaiderIoClient,
    private val blizzardClient: BlizzardClient
) : EntityResolver, WithLogger("WowEntityResolver") {
    override val game: Game = Game.WOW

    companion object {
        private const val MAX_CHARACTER_LEVEL = 90
        private const val SCORE_CUTOFF = 1000
    }

    override suspend fun resolve(
        requested: List<EntityRequest>,
        extra: ViewExtraArguments?
    ): Either<ServiceError, ResolvedEntities> = either {
        val guild = (extra as? WowExtraArguments)?.guild

        if (guild == null) {
            val (existing, newRequests) = getCurrentAndNewEntities(repo, requested, Game.WOW)
            val (entities, unchecked) = resolveCharacters(newRequests)
            ResolvedEntities(
                entities = entities,
                existing = existing.map { it.value to it.alias },
                unchecked = unchecked,
                guild = null
            )
        } else {
            val guildReq = requested.first() as WowEntityRequest

            when (guild) {
                GuildArgs.EXISTENCE ->
                    ResolvedEntities(
                        entities = emptyList(),
                        existing = emptyList(),
                        unchecked = emptyList(),
                        guild = getGuildPayloadIfExists(guildReq).bind()
                    )

                GuildArgs.RESOLVE -> {
                    val (guildResponse, roster) = resolveRoster(guildReq.region, guildReq.realm, guildReq.name).bind()
                    val (existing, newRequests) = getCurrentAndNewEntities(repo, roster, Game.WOW)

                    ResolvedEntities(
                        entities = resolveGuildMembers(newRequests),
                        existing = existing.map { it.value to it.alias },
                        unchecked = emptyList(),
                        guild = getGuildPayload(guildResponse, guildReq)
                    )
                }
            }
        }
    }

    private suspend fun getGuildPayloadIfExists(guildReq: WowEntityRequest): Either<ServiceError, GuildPayload?> =
        blizzardClient.getRetailGuildRoster(guildReq.region, guildReq.realm, guildReq.name).fold(
            ifLeft = { error ->
                if (error is HttpError && error.status == 404) Either.Right(null)
                else Either.Left(error.toSyncProcessingError("GetRetailGuildRoster"))
            },
            ifRight = { roster -> Either.Right(getGuildPayload(roster, guildReq)) }
        )

    private fun getGuildPayload(guildRosterResponse: GetWowRosterResponse, entityRequest: EntityRequest): GuildPayload {
        val guildReq = entityRequest as WowEntityRequest
        return GuildPayload(
            guildReq.name.lowercase(),
            guildReq.realm.lowercase(),
            guildReq.region.lowercase(),
            guildRosterResponse.guild.id
        )
    }

    suspend fun resolveRoster(
        region: String,
        realm: String,
        name: String
    ): Either<ServiceError, Pair<GetWowRosterResponse, List<WowEntityRequest>>> = either {
        val roster = blizzardClient.getRetailGuildRoster(region, realm, name)
            .mapLeft { it.toSyncProcessingError("GetRetailGuildRoster") }
            .bind()

        val members = roster.members
            .asSequence()
            .filter { it.character.level >= MAX_CHARACTER_LEVEL }
            .map {
                WowEntityRequest(
                    it.character.name,
                    region,
                    it.character.realm?.slug ?: realm,
                    it.character.id
                )
            }
            .toList()

        Pair(roster, members)
    }

    suspend fun resolveGuildMembers(
        newRequests: List<EntityRequest>
    ): List<Pair<InsertEntityRequest, String?>> {
        val (errors, resolved) = newRequests.asFlow()
            .parMap(10) { req ->
                req as WowEntityRequest
                either {
                    val score = raiderioClient.getScore(req)
                        .mapLeft { it.toSyncProcessingError("raiderIoScore") }
                        .bind()

                    ensure(score >= SCORE_CUTOFF) { NotCompetitiveCharacter(req) }

                    req to req.alias
                }.mapLeft { req to it }
            }
            .toList()
            .split()

        errors.forEach { (req, error) ->
            logger.warn("Skipping guild member ${req.name}-${req.realm}: ${error.error()}")
        }

        return resolved
    }

    private suspend fun resolveCharacters(
        newRequests: List<EntityRequest>
    ): Pair<List<Pair<InsertEntityRequest, String?>>, List<Pair<EntityRequest, ServiceError>>> {
        val (unchecked, checked) = newRequests.asFlow()
            .parMap(10) { req ->
                req as WowEntityRequest
                blizzardClient.getRetailProfile(req.region, req.realm, req.name).fold(
                    ifLeft = { error ->
                        if (error is HttpError && error.status == 404) Either.Right(req to null)
                        else Either.Left(req to error.toSyncProcessingError("getRetailProfile"))
                    },
                    ifRight = { profile -> Either.Right(req to profile.id) }
                )
            }
            .toList()
            .split()

        val entities = checked.collect(
            filter = { it.second != null },
            map = { it.first.copy(blizzardId = it.second) to it.first.alias }
        )

        return entities to unchecked
    }
}
