package com.kos.views

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kos.clients.domain.Data
import com.kos.common.WithLogger
import com.kos.common.error.*
import com.kos.credentials.CredentialsService
import com.kos.datacache.DataCacheService
import com.kos.entities.EntitiesService
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.EntityWithAlias
import com.kos.eventsourcing.events.*
import com.kos.eventsourcing.events.repository.EventStore
import com.kos.views.repository.ViewsRepository
import java.time.OffsetDateTime
import java.util.*

class ViewsService(
    private val viewsRepository: ViewsRepository,
    private val entitiesService: EntitiesService,
    private val dataCacheService: DataCacheService,
    private val credentialsService: CredentialsService,
    private val eventStore: EventStore
) : WithLogger("ViewsService") {

    suspend fun getOwnViews(owner: String, query: GetViewsQuery): Pair<ViewMetadata, List<SimpleView>> =
        viewsRepository.getOwnViews(owner, query)

    suspend fun getViews(query: GetViewsQuery): Pair<ViewMetadata, List<SimpleView>> =
        viewsRepository.getViews(query)

    suspend fun get(id: String): View? {
        return when (val simpleView = viewsRepository.get(id)) {
            null -> null
            else -> {
                View(
                    simpleView.id,
                    simpleView.name,
                    simpleView.owner,
                    simpleView.published,
                    simpleView.entitiesIds.mapNotNull {
                        entitiesService.get(it, simpleView.game)
                    }.mapNotNull { entity ->
                        viewsRepository.getViewEntity(simpleView.id, entity.id)?.let {
                            EntityWithAlias(entity, it.alias)
                        }
                    },
                    simpleView.game,
                    simpleView.featured,
                    simpleView.lastSyncedAt
                )
            }
        }
    }

    suspend fun getSimple(id: String): SimpleView? = viewsRepository.get(id)

    suspend fun updateLastSyncedAt(viewId: String, at: OffsetDateTime) =
        viewsRepository.updateLastSyncedAt(viewId, at)

    suspend fun create(owner: String, request: ViewRequest): Either<ControllerError, Operation> {
        return either {
            ensureMaxNumberOfViews(owner).bind()
            ensureMaxNumberOfEntities(owner, request.entities).bind()
            ensureRequest(request).bind()

            val operationId = UUID.randomUUID().toString()
            val viewId = UUID.randomUUID().toString()
            val aggregateRoot = "/credentials/$owner"
            val event = Event(
                aggregateRoot,
                operationId,
                ViewToBeCreatedEvent(
                    viewId,
                    request.name,
                    request.published,
                    request.entities,
                    request.game,
                    owner,
                    request.featured,
                    request.extraArguments
                )
            )
            eventStore.save(event).mapLeft { ViewDataError(it.message) }.bind().copy(resourceId = viewId)
        }
    }

    suspend fun edit(owner: String, id: String, request: ViewRequest): Either<ControllerError, Operation> {
        return either {
            ensureMaxNumberOfEntities(owner, request.entities).bind()

            val operationId = UUID.randomUUID().toString()
            val aggregateRoot = "/credentials/$owner"
            val event = Event(
                aggregateRoot,
                operationId,
                ViewToBeEditedEvent(
                    id,
                    request.name,
                    request.published,
                    request.entities,
                    request.game,
                    request.featured
                )
            )
            eventStore.save(event).mapLeft { ViewDataError(it.message) }.bind()
        }
    }

    suspend fun patch(owner: String, id: String, request: ViewPatchRequest): Either<ControllerError, Operation> {
        return either {
            ensureMaxNumberOfEntities(owner, request.entities).bind()

            val operationId = UUID.randomUUID().toString()
            val aggregateRoot = "/credentials/$owner"
            val event = Event(
                aggregateRoot,
                operationId,
                ViewToBePatchedEvent(
                    id,
                    request.name,
                    request.published,
                    request.entities,
                    request.game,
                    request.featured
                )
            )

            eventStore.save(event).mapLeft { ViewDataError(it.message) }.bind()
        }
    }

    suspend fun delete(owner: String, viewToDelete: SimpleView): Either<ControllerError, Operation> {
        val operationId = UUID.randomUUID().toString()
        val aggregateRoot = "/credentials/$owner"
        val event = Event(
            aggregateRoot,
            operationId,
            ViewToBeDeletedEvent(
                viewToDelete.id,
                viewToDelete.name,
                viewToDelete.owner,
                viewToDelete.entitiesIds,
                viewToDelete.published,
                viewToDelete.game,
                viewToDelete.featured
            )
        )
        return eventStore.save(event).mapLeft { ViewDataError(it.message) }
    }

    suspend fun getData(view: View): Either<ServiceError, List<Data>> =
        dataCacheService.getData(view.entities.map { it.value.id }, oldFirst = false)

    suspend fun getCachedData(simpleView: SimpleView) =
        dataCacheService.getData(simpleView.entitiesIds, oldFirst = true)

    private suspend fun getMaxNumberOfViewsByRole(owner: String): Either<UserWithoutRoles, Int> =
        when (val maxNumberOfViews = credentialsService.getUserRoles(owner).maxOfOrNull { it.maxNumberOfViews }) {
            null -> Either.Left(UserWithoutRoles)
            else -> Either.Right(maxNumberOfViews)
        }

    private suspend fun getMaxNumberOfEntitiesByRole(owner: String): Either<UserWithoutRoles, Int> =
        when (val maxNumberOfEntities =
            credentialsService.getUserRoles(owner).maxOfOrNull { it.maxNumberOfEntities }) {
            null -> Either.Left(UserWithoutRoles)
            else -> Either.Right(maxNumberOfEntities)
        }

    private suspend fun ensureMaxNumberOfViews(owner: String): Either<ControllerError, Unit> {
        return either {
            val ownerMaxViews = getMaxNumberOfViewsByRole(owner).bind()
            val ownViewsCount =
                viewsRepository.getOwnViews(owner, GetViewsQuery(null, false, null, null, false)).second.size
            ensure(ownViewsCount < ownerMaxViews) { TooMuchViews }
        }
    }

    private suspend fun ensureMaxNumberOfEntities(
        owner: String,
        entities: List<EntityRequest>?
    ): Either<ControllerError, Unit> {
        return either {
            val ownerMaxNumberOfEntities = getMaxNumberOfEntitiesByRole(owner).bind()
            entities?.let { entitiesToInsert ->
                ensure(entitiesToInsert.size <= ownerMaxNumberOfEntities) { TooMuchEntities }
            }
        }
    }

    private fun ensureRequest(
        request: ViewRequest
    ): Either<ControllerError, Unit> = either {
        request.extraArguments?.let { extra ->
            when (request.game) {
                Game.WOW_HC -> {
                    ensure(extra is WowHardcoreExtraArguments) { ExtraArgumentsWrongType }
                    if (extra.isGuild) ensure(request.entities.size == 1) { GuildViewMoreThanTwoEntities }
                }

                Game.WOW -> {
                    ensure(extra is WowExtraArguments) { ExtraArgumentsWrongType }
                    if (extra.guild != null) ensure(request.entities.size == 1) { GuildViewMoreThanTwoEntities }
                }

                Game.LOL -> Unit
            }
        }
    }
}