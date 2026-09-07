package com.kos.views

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.kos.common.WithLogger
import com.kos.common.error.ServiceError
import com.kos.common.error.ViewEventError
import com.kos.common.error.toEventPersistenceError
import com.kos.entities.EntitiesService
import com.kos.entities.domain.ResolvedEntities
import com.kos.entities.domain.WowEntityRequest
import com.kos.eventsourcing.events.*
import com.kos.eventsourcing.events.repository.EventStore
import com.kos.views.repository.ViewsRepository

class ViewsEventService(
    private val viewsRepository: ViewsRepository,
    private val entitiesService: EntitiesService,
    private val eventStore: EventStore
) : WithLogger("ViewsEventService") {

    suspend fun createView(
        operationId: String,
        aggregateRoot: String,
        viewToBeCreatedEvent: ViewToBeCreatedEvent
    ): Either<ServiceError, Operation> {
        return either {
            val resolved = resolveEntitiesForCreate(viewToBeCreatedEvent).bind()

            if (resolved.unchecked.isNotEmpty()) {
                logger.warn("Could not verify existence for entities, they will be skipped: ${resolved.unchecked}")
            }

            val inserted = entitiesService
                .insert(resolved.entities.map { it.first }, viewToBeCreatedEvent.game)
                .mapLeft { ViewEventError(viewToBeCreatedEvent, it.message) }
                .bind()

            val entities = inserted.zip(resolved.entities.map { it.second }) +
                    resolved.existing

            val view = viewsRepository.create(
                viewToBeCreatedEvent.id,
                viewToBeCreatedEvent.name,
                viewToBeCreatedEvent.owner,
                entities.map { it.first.id to it.second },
                viewToBeCreatedEvent.game,
                viewToBeCreatedEvent.featured,
                viewToBeCreatedEvent.extraArguments
            ).mapLeft { ViewEventError(viewToBeCreatedEvent, it.message) }.bind()

            resolved.guild?.let {
                entitiesService.insertGuild(it, view.id, viewToBeCreatedEvent.game)
                    .mapLeft { ViewEventError(viewToBeCreatedEvent, it.message) }
                    .bind()
            }

            val event = Event(
                aggregateRoot,
                operationId,
                ViewCreatedEventEvent.fromSimpleView(view)
            )
            eventStore.save(event).mapLeft { it.toEventPersistenceError() }.bind()
        }
    }

    private suspend fun resolveEntitiesForCreate(
        event: ViewToBeCreatedEvent
    ): Either<ServiceError, ResolvedEntities> = either {
        val guildReq = (event.extraArguments as? WowExtraArguments)
            ?.takeIf { it.guild != null && event.game == Game.WOW }
            ?.let { event.entities.first() as WowEntityRequest }

        val trackedGuild = guildReq?.let { req ->
            entitiesService.findTrackedGuild(
                req.name.lowercase(),
                req.realm.lowercase(),
                req.region.lowercase(),
                Game.WOW
            )
        }

        when (trackedGuild) {
            null -> entitiesService.resolveEntities(event.entities, event.game, event.extraArguments).bind()
            else -> {
                val (guildPayload, sourceViewId) = trackedGuild
                val sourceView = ensureNotNull(viewsRepository.get(sourceViewId)) {
                    ViewEventError(event, "tracked guild's source view $sourceViewId no longer exists")
                }
                val existing = sourceView.entitiesIds.mapNotNull { id ->
                    entitiesService.get(id, Game.WOW)?.let { entity ->
                        entity to viewsRepository.getViewEntity(sourceViewId, id)?.alias
                    }
                }
                ResolvedEntities(
                    entities = emptyList(),
                    existing = existing,
                    unchecked = emptyList(),
                    guild = guildPayload
                )
            }
        }
    }

    suspend fun editView(
        operationId: String,
        aggregateRoot: String,
        viewToBeEditedEvent: ViewToBeEditedEvent
    ): Either<ServiceError, Operation> {
        return either {
            val resolved =
                entitiesService.resolveEntities(
                    viewToBeEditedEvent.entities,
                    viewToBeEditedEvent.game
                ).bind()

            if (resolved.unchecked.isNotEmpty()) {
                logger.warn("Could not verify existence for entities, they will be skipped: ${resolved.unchecked}")
            }

            val inserted = entitiesService
                .insert(resolved.entities.map { it.first }, viewToBeEditedEvent.game)
                .mapLeft { ViewEventError(viewToBeEditedEvent, it.message) }
                .bind()

            val entities = inserted.zip(resolved.entities.map { it.second }) +
                    resolved.existing
            val viewModified = viewsRepository.edit(
                viewToBeEditedEvent.id,
                viewToBeEditedEvent.name,
                viewToBeEditedEvent.published,
                entities.map { it.first.id to it.second },
                viewToBeEditedEvent.featured
            ).mapLeft { ViewEventError(viewToBeEditedEvent, it.message) }.bind()

            val event = Event(
                aggregateRoot,
                operationId,
                ViewEditedEventEvent.fromViewModified(operationId, viewToBeEditedEvent.game, viewModified)
            )
            eventStore.save(event).mapLeft { it.toEventPersistenceError() }.bind()
        }
    }

    suspend fun patchView(
        operationId: String,
        aggregateRoot: String,
        viewToBePatchedEvent: ViewToBePatchedEvent
    ): Either<ServiceError, Operation> {
        return either {
            val entitiesToInsert = viewToBePatchedEvent.entities?.let { entitiesToInsert ->
                val resolved =
                    entitiesService.resolveEntities(
                        entitiesToInsert,
                        viewToBePatchedEvent.game
                    ).bind()

                if (resolved.unchecked.isNotEmpty()) {
                    logger.warn("Could not verify existence for entities, they will be skipped: ${resolved.unchecked}")
                }

                val inserted = entitiesService
                    .insert(resolved.entities.map { it.first }, viewToBePatchedEvent.game)
                    .mapLeft { ViewEventError(viewToBePatchedEvent, it.message) }
                    .bind()

                inserted.zip(resolved.entities.map { it.second }) +
                        resolved.existing
            }
            val patchedView = viewsRepository.patch(
                viewToBePatchedEvent.id,
                viewToBePatchedEvent.name,
                viewToBePatchedEvent.published,
                entitiesToInsert?.map { it.first.id to it.second },
                viewToBePatchedEvent.featured
            ).mapLeft { ViewEventError(viewToBePatchedEvent, it.message) }.bind()

            val event = Event(
                aggregateRoot,
                operationId,
                ViewPatchedEventEvent.fromViewPatched(operationId, viewToBePatchedEvent.game, patchedView)
            )
            eventStore.save(event).mapLeft { it.toEventPersistenceError() }.bind()
        }
    }

    suspend fun deleteView(
        operationId: String,
        aggregateRoot: String,
        event: ViewToBeDeletedEvent
    ): Either<ServiceError, Operation> {
        return either {
            viewsRepository.delete(event.id).mapLeft { ViewEventError(event, it.message) }.bind()
            val completionEvent = Event(
                aggregateRoot,
                operationId,
                ViewDeletedEvent(
                    event.id,
                    event.name,
                    event.owner,
                    event.entities,
                    event.published,
                    event.game,
                    event.featured
                )
            )
            eventStore.save(completionEvent).mapLeft { it.toEventPersistenceError() }.bind()
        }
    }
}
