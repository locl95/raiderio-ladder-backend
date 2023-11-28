package com.kos.eventsourcing.events.repository

import com.kos.common.WithState
import com.kos.eventsourcing.events.Event
import com.kos.eventsourcing.events.EventWithVersion

interface EventStore : WithState<List<EventWithVersion>, EventStore> {
    suspend fun save(event: Event): EventWithVersion
    suspend fun getEvents(version: Long?): Sequence<EventWithVersion>
}