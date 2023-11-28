package com.kos.eventsourcing.events.repository

import com.kos.common.InMemoryRepository
import com.kos.eventsourcing.events.Event
import com.kos.eventsourcing.events.EventWithVersion

class EventStoreInMemory : EventStore, InMemoryRepository {
    private val events = mutableListOf<EventWithVersion>()
    private var currentVersion = 1L // Assuming versions start from 1 and increment

    override suspend fun save(event: Event): EventWithVersion {
        val eventWithVersion = EventWithVersion(currentVersion++, event)
        events.add(eventWithVersion)
        return eventWithVersion
    }

    override suspend fun getEvents(version: Long?): Sequence<EventWithVersion> {
        return version?.let { v ->
            events.filter { it.version >= v }.asSequence()
        } ?: events.asSequence()
    }

    override suspend fun state(): List<EventWithVersion> {
        return events
    }

    override suspend fun withState(initialState: List<EventWithVersion>): EventStore {
        currentVersion = initialState.map { it.version }.maxByOrNull { it } ?: 1
        events.addAll(initialState)
        return this
    }

    override fun clear() {
        events.clear()
    }
}