package com.kos.eventsourcing.events.repository

import com.kos.common.DatabaseFactory.dbQuery
import com.kos.eventsourcing.events.Event
import com.kos.eventsourcing.events.EventWithVersion
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import kotlin.sequences.Sequence

class EventStoreDatabase : EventStore {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    object Events : Table() {
        val version = long("version")
        val aggregateRoot = varchar("aggregate_root", 128)
        val eventType = varchar("event_type", 48)
        val data = text("data")

        override val primaryKey = PrimaryKey(version)
        override val tableName = "events"
    }

    private fun resultRowToEventWithVersion(row: ResultRow): EventWithVersion =
        EventWithVersion(
            row[Events.version],
            Event(row[Events.aggregateRoot], row[Events.eventType], json.decodeFromString(row[Events.data]))
        )


    override suspend fun save(event: Event): EventWithVersion {
        return dbQuery {
            Events.insert {
                it[aggregateRoot] = event.aggregateRoot
                it[data] = json.encodeToString(event.eventData)
            }.resultedValues?.map { resultRowToEventWithVersion(it) }?.singleOrNull() ?: throw Exception(":(")
        }
    }

    override suspend fun getEvents(version: Long?): Sequence<EventWithVersion> {
        TODO("Not yet implemented")
    }

    override suspend fun state(): List<EventWithVersion> {
        TODO("Not yet implemented")
    }

    override suspend fun withState(initialState: List<EventWithVersion>): EventStore {
        TODO("Not yet implemented")
    }
}