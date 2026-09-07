package com.kos.eventsourcing.events.repository

import arrow.core.Either
import com.kos.common.WithState
import com.kos.common.error.RepositoryError
import com.kos.eventsourcing.events.Event
import com.kos.eventsourcing.events.EventWithVersion
import com.kos.eventsourcing.events.Operation
import com.kos.eventsourcing.events.OperationFailedEvent
import java.time.OffsetDateTime

interface EventStore : WithState<List<EventWithVersion>, EventStore> {
    suspend fun save(event: Event): Either<RepositoryError, Operation>
    suspend fun getEvents(version: Long?): Sequence<EventWithVersion>
    suspend fun getEventsByOperationId(operationId: String): List<EventWithVersion>
    suspend fun deleteEventsOlderThan(timestamp: OffsetDateTime, upToVersion: Long): Int

    suspend fun saveFailedEvent(
        operationId: String,
        aggregateRoot: String,
        reason: String
    ): Either<RepositoryError, Operation> =
        save(Event(aggregateRoot, operationId, OperationFailedEvent(operationId, reason)))
}