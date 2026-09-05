package com.kos.eventsourcing.events.repository

import arrow.core.Either
import com.kos.common.InMemoryRepository
import com.kos.common.error.RepositoryError
import com.kos.eventsourcing.events.Event
import com.kos.eventsourcing.events.EventWithVersion
import com.kos.eventsourcing.events.Operation
import java.time.OffsetDateTime

class EventStoreInMemory : EventStore, InMemoryRepository {
    private val events = mutableListOf<EventWithVersion>()
    private val timestamps = mutableMapOf<Long, OffsetDateTime>()
    private var currentVersion = 1L // Assuming versions start from 1 and increment

    override suspend fun save(event: Event): Either<RepositoryError, Operation> {
        val eventWithVersion = EventWithVersion(currentVersion++, event)
        events.add(eventWithVersion)
        timestamps[eventWithVersion.version] = OffsetDateTime.now()
        return Either.Right(Operation(event.operationId, event.eventData.eventType))
    }

    override suspend fun getEvents(version: Long?): Sequence<EventWithVersion> {
        return version?.let { v ->
            events.filter { it.version > v }.asSequence()
        } ?: events.asSequence()
    }

    override suspend fun getEventsByOperationId(operationId: String): List<EventWithVersion> {
        return events.filter { it.event.operationId == operationId }
    }

    override suspend fun deleteEventsOlderThan(timestamp: OffsetDateTime, upToVersion: Long): Int {
        fun isExpired(event: EventWithVersion) =
            event.version <= upToVersion && timestamps[event.version]?.isBefore(timestamp) == true

        val expiredVersions = events.filter(::isExpired).map { it.version }
        events.removeAll(::isExpired)
        expiredVersions.forEach(timestamps::remove)
        return expiredVersions.size
    }

    override suspend fun state(): List<EventWithVersion> {
        return events
    }

    override suspend fun withState(initialState: List<EventWithVersion>): EventStore {
        currentVersion = initialState.map { it.version }.maxByOrNull { it } ?: 1
        events.addAll(initialState)
        val now = OffsetDateTime.now()
        initialState.forEach { timestamps[it.version] = now }
        return this
    }

    override fun clear() {
        events.clear()
        timestamps.clear()
    }
}