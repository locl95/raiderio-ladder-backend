package com.kos.sources.wow

import com.kos.common.WithLogger
import com.kos.common.error.ServiceError
import com.kos.common.error.toEntityResolverError
import com.kos.common.split
import com.kos.entities.EntityUpdater
import com.kos.entities.domain.*
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game
import com.kos.views.repository.ViewsRepository

class WowGuildUpdater(
    private val resolver: WowEntityResolver,
    private val entitiesRepository: EntitiesRepository,
    private val viewsRepository: ViewsRepository
) : EntityUpdater<Pair<GuildPayload, String>>, WithLogger("WowGuildUpdater") {

    private data class ResolvedGuild(
        val guild: GuildPayload,
        val viewIds: List<String>,
        val roster: List<WowEntityRequest>
    )

    override suspend fun update(entities: List<Pair<GuildPayload, String>>): List<ServiceError> {
        val (resolutionErrors, resolvedGuilds) = resolveGuildRosters(entities)
        val updateErrors = resolvedGuilds.flatMap { updateGuild(it) }
        return resolutionErrors + updateErrors
    }

    private suspend fun resolveGuildRosters(
        entities: List<Pair<GuildPayload, String>>
    ): Pair<List<ServiceError>, List<ResolvedGuild>> =
        entities.groupBy { it.first.blizzardId }
            .values
            .map { rows ->
                val guild = rows.first().first
                val viewIds = rows.map { it.second }
                resolver.resolveRoster(guild.region, guild.realm, guild.name)
                    .map { (_, roster) -> ResolvedGuild(guild, viewIds, roster) }
            }
            .split()

    private suspend fun updateGuild(resolvedGuild: ResolvedGuild): List<ServiceError> {
        val (guild, viewIds, roster) = resolvedGuild
        logger.info("Updating Wow Guild ${guild.name} - ${guild.realm} - ${guild.region} (${viewIds.size} view(s))")

        val (current, newEntities) = resolver.getCurrentAndNewEntities(entitiesRepository, roster, Game.WOW)
        val (updateMemberErrors, renamedEntities, newMembers) = updateAlreadyTrackedMembers(newEntities)

        val entityIdsStillInGuild = current.map { it.value.id }.toSet() + renamedEntities
        val insertionErrors = resolveAndInsertNewMembers(guild, viewIds, newMembers, entityIdsStillInGuild)

        return updateMemberErrors + insertionErrors
    }

    private suspend fun updateAlreadyTrackedMembers(
        newEntities: List<EntityRequest>
    ): Triple<List<ServiceError>, List<Long>, List<WowEntityRequest>> {
        val (renamed, newMembers) = newEntities
            .map { it as WowEntityRequest }
            .map { request -> request to trackedMemberByBlizzardId(request) }
            .partition { (_, trackedEntity) -> trackedEntity != null }

        val (errors, renamedMembers) = renamed.map { (request, trackedEntity) ->
            trackedEntity!!
            entitiesRepository.update(trackedEntity.id, request, Game.WOW)
                .mapLeft { it.toEntityResolverError(Game.WOW, it.message) }
                .map { trackedEntity.id }
        }.split()

        return Triple(errors, renamedMembers, newMembers.map { it.first })
    }

    private suspend fun trackedMemberByBlizzardId(request: WowEntityRequest): WowEntity? =
        request.blizzardId?.let { entitiesRepository.get(request as InsertEntityRequest, Game.WOW) as? WowEntity }

    private suspend fun resolveAndInsertNewMembers(
        guild: GuildPayload,
        viewIds: List<String>,
        newMembers: List<WowEntityRequest>,
        entityIdsStillInGuild: Set<Long>
    ): List<ServiceError> {
        val newMembers = resolver.resolveGuildMembers(newMembers)

        return entitiesRepository.insert(newMembers.map { it.first }, Game.WOW).fold(
            ifLeft = { insertError ->
                listOf(insertError.toEntityResolverError(Game.WOW, insertError.message))
            },
            ifRight = { inserted ->
                logger.info("Inserted new entities $inserted to EntityRepository")
                val insertedWithAlias = inserted.zip(newMembers) { entity, member -> entity.id to member.second }

                syncViewMembership(
                    guild,
                    viewIds,
                    stillInGuild = entityIdsStillInGuild + insertedWithAlias.map { it.first },
                    insertedWithAlias = insertedWithAlias
                )

                logger.info("Finished updating Wow Guild ${guild.name} - ${guild.realm} - ${guild.region}")
                emptyList()
            }
        )
    }

    private suspend fun syncViewMembership(
        guild: GuildPayload,
        viewIds: List<String>,
        stillInGuild: Set<Long>,
        insertedWithAlias: List<Pair<Long, String?>>
    ) {
        val currentlyAssociated = viewsRepository.get(viewIds.first())?.entitiesIds?.toSet()
        val noLongerInGuild = currentlyAssociated?.minus(stillInGuild)

        if (!noLongerInGuild.isNullOrEmpty()) {
            viewIds.forEach { viewId ->
                logger.info("Disassociating ${noLongerInGuild.size} entities from viewId $viewId and guild ${guild.name}")
                viewsRepository.disassociateEntitiesFromView(noLongerInGuild, viewId)
            }
        }

        if (insertedWithAlias.isNotEmpty()) {
            viewIds.forEach { viewId ->
                logger.info("Associating [${insertedWithAlias.map { it.first }}] entities to viewId $viewId and guild ${guild.name}")
                viewsRepository.associateEntitiesIdsToView(insertedWithAlias, viewId)
            }
        }
    }
}
