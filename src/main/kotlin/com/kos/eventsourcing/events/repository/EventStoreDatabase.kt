package com.kos.eventsourcing.events.repository

import arrow.core.Either
import com.kos.common._fold
import com.kos.common.error.RepositoryError
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.LolEntityRequest
import com.kos.entities.domain.WowEntityRequest
import com.kos.eventsourcing.events.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import kotlin.sequences.Sequence

class EventStoreDatabase(private val db: Database) : EventStore {

    private val json = Json {
        serializersModule = SerializersModule {
            polymorphic(EventData::class) {
                subclass(ViewToBeCreatedEvent::class, ViewToBeCreatedEvent.serializer())
                subclass(ViewToBePatchedEvent::class, ViewToBePatchedEvent.serializer())
                subclass(ViewToBeEditedEvent::class, ViewToBeEditedEvent.serializer())
                subclass(ViewCreatedEventEvent::class, ViewCreatedEventEvent.serializer())
                subclass(ViewEditedEventEvent::class, ViewEditedEventEvent.serializer())
                subclass(ViewPatchedEventEvent::class, ViewPatchedEventEvent.serializer())
                subclass(ViewToBeDeletedEvent::class, ViewToBeDeletedEvent.serializer())
                subclass(ViewDeletedEvent::class, ViewDeletedEvent.serializer())
                subclass(RequestToBeSynced::class, RequestToBeSynced.serializer())
                subclass(OperationFailedEvent::class, OperationFailedEvent.serializer())
                subclass(ViewSyncCompletedEvent::class, ViewSyncCompletedEvent.serializer())
                subclass(EntitySyncCompletedEvent::class, EntitySyncCompletedEvent.serializer())
            }

            //TODO: This is repeated code. We could do it better
            polymorphic(EntityRequest::class) {
                subclass(WowEntityRequest::class, WowEntityRequest.serializer())
                subclass(LolEntityRequest::class, LolEntityRequest.serializer())
            }
        }
        ignoreUnknownKeys = true
    }

    object Events : Table("events") {
        val version = long("version")
        val aggregateRoot = varchar("aggregate_root", 128)
        val operationId = varchar("operation_id", 128)
        val eventType = varchar("event_type", 48)
        val data = text("data")
        val timestamp = text("timestamp")

        override val primaryKey = PrimaryKey(version)
    }

    private fun resultRowToEventWithVersion(row: ResultRow): EventWithVersion =
        EventWithVersion(
            row[Events.version],
            Event(row[Events.aggregateRoot], row[Events.operationId], json.decodeFromString(row[Events.data]))
        )

    private fun resultRowToOperation(row: ResultRow): Operation =
        Operation(
            row[Events.operationId],
            EventType.fromString(row[Events.eventType])
        )


    override suspend fun save(event: Event): Either<RepositoryError, Operation> {
        return Either.catch {
            newSuspendedTransaction(Dispatchers.IO, db) {
                val id = selectNextId()
                Events.insert {
                    it[aggregateRoot] = event.aggregateRoot
                    it[operationId] = event.operationId
                    it[version] = id
                    it[eventType] = event.eventData.eventType.toString()
                    it[data] = json.encodeToString(event.eventData)
                    it[timestamp] = OffsetDateTime.now().toString()
                }.resultedValues?.map { resultRowToOperation(it) }?.singleOrNull() ?: throw Exception(":(")
            }
        }.mapLeft { RepositoryError(it.message ?: it.stackTraceToString()) }
    }

    override suspend fun getEvents(version: Long?): Sequence<EventWithVersion> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            version._fold(
                left = { Events.selectAll().map { resultRowToEventWithVersion(it) }.asSequence() },
                right = {
                    Events.selectAll().where { Events.version greater it }.orderBy(Events.version)
                        .map { resultRowToEventWithVersion(it) }.asSequence()
                })
        }
    }

    override suspend fun getEventsByOperationId(operationId: String): List<EventWithVersion> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            Events.selectAll().where { Events.operationId eq operationId }
                .orderBy(Events.version)
                .map { resultRowToEventWithVersion(it) }
        }
    }

    override suspend fun deleteEventsOlderThan(timestamp: OffsetDateTime, upToVersion: Long): Int {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            Events.deleteWhere {
                Events.timestamp.less(timestamp.toString()) and Events.version.lessEq(upToVersion)
            }
        }
    }

    override suspend fun state(): List<EventWithVersion> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            Events.selectAll().map { resultRowToEventWithVersion(it) }
        }
    }

    override suspend fun withState(initialState: List<EventWithVersion>): EventStore {
        val now = OffsetDateTime.now().toString()
        newSuspendedTransaction(Dispatchers.IO, db) {
            Events.batchInsert(initialState) {
                this[Events.aggregateRoot] = it.event.aggregateRoot
                this[Events.operationId] = it.event.operationId
                this[Events.version] = it.version
                this[Events.eventType] = it.event.eventData.eventType.toString()
                this[Events.data] = json.encodeToString(it.event.eventData)
                this[Events.timestamp] = now
            }
        }
        return this
    }

    private suspend fun selectNextId(): Long =
        newSuspendedTransaction(Dispatchers.IO, db) {
            TransactionManager.current().exec("""select nextval('event_versions') as id""") { rs ->
                if (rs.next()) rs.getLong("id")
                else -1
            }
        } ?: -1
}