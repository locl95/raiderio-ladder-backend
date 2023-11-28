package com.kos.eventsourcing.events

import kotlinx.serialization.Serializable

interface EventData

@Serializable
data class Event(
    val aggregateRoot: String,
    val eventType: String,
    val eventData: EventData
)

data class EventWithVersion(val version: Long, val event: Event)