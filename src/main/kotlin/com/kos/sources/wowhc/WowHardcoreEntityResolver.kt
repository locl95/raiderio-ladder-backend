package com.kos.sources.wowhc

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.fx.coroutines.parMap
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.domain.GetWowRosterResponse
import com.kos.clients.toSyncProcessingError
import com.kos.common.WithLogger
import com.kos.common.error.NonHardcoreCharacter
import com.kos.common.error.ServiceError
import com.kos.common.split
import com.kos.entities.EntityResolver
import com.kos.entities.domain.*
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game
import com.kos.views.ViewExtraArguments
import com.kos.views.WowHardcoreExtraArguments
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

class WowHardcoreEntityResolver(
    private val repo: EntitiesRepository,
    private val blizzardClient: BlizzardClient
) : EntityResolver, WithLogger("WowHardcoreResolver") {
    override val game: Game = Game.WOW_HC

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override suspend fun resolve(
        requested: List<EntityRequest>,
        extra: ViewExtraArguments?
    ): Either<ServiceError, ResolvedEntities> = either {
        val args = extra as? WowHardcoreExtraArguments
        val (effectiveRequests, guildPayload) = if (args?.isGuild == true) {
            val guildReq = requested.first() as WowEntityRequest

            val (guildResponse, roster) = resolveRoster(guildReq.region, guildReq.realm, guildReq.name).bind()

            Pair(
                roster,
                GuildPayload(
                    name = guildReq.name,
                    realm = guildReq.realm,
                    region = guildReq.region,
                    blizzardId = guildResponse.guild.id
                )
            )
        } else {
            Pair(requested, null)
        }

        val (existing, newRequests) = getCurrentAndNewEntities(repo, effectiveRequests, Game.WOW_HC)

        val (errors, oks) = newRequests.asFlow()
            .parMap(10) { req ->
                either {
                    req as WowEntityRequest
                    val profile = blizzardClient.getClassicProfile(
                        req.region, req.realm, req.name
                    ).bind()

                    val realm = blizzardClient.getRealm(req.region, profile.realm.id).bind()

                    ensure(
                        realm.category == "Hardcore" || realm.category == "Anniversary"
                    ) { NonHardcoreCharacter(req) }

                    WowEntityRequest(
                        req.name,
                        req.region,
                        req.realm,
                        profile.id
                    ) to req.alias
                }
            }
            .toList()
            .split()

        errors.forEach { logger.error(it.toString()) }

        val validatedTuples: List<Pair<InsertEntityRequest, String?>> = oks

        ResolvedEntities(
            entities = validatedTuples,
            existing = existing.map { it.value to it.alias },
            guild = guildPayload
        )
    }

    suspend fun resolveRoster(
        region: String,
        realm: String,
        name: String
    ): Either<ServiceError, Pair<GetWowRosterResponse, List<WowEntityRequest>>> {
        return either {
            val roster = blizzardClient
                .getHardcoreGuildRoster(region, realm, name)
                .mapLeft { it.toSyncProcessingError("GetHardcoreGuildRoster") }
                .bind()

            val memberReqs = roster.members
                .asSequence()
                .filter { it.character.level >= 10 }
                .map { WowEntityRequest(it.character.name.lowercase(), region, it.character.realm?.slug ?: realm) }
                .toList()

            Pair(roster, memberReqs)
        }

    }
}