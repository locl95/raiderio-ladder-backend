package com.kos.eventsourcing

import com.kos.eventsourcing.events.Event
import com.kos.eventsourcing.events.EventWithVersion
import com.kos.eventsourcing.events.ViewToBeCreatedEvent
import com.kos.eventsourcing.events.repository.EventStore
import com.kos.eventsourcing.events.repository.EventStoreDatabase
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.views.ViewsTestHelper.basicSimpleWowView
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

abstract class EventStoreTest {

    abstract val store: EventStore

    @Test
    fun `given an empty store i can save events`() {
        runBlocking {
            val payload = ViewToBeCreatedEvent(
                UUID.randomUUID().toString(),
                basicSimpleWowView.name,
                basicSimpleWowView.published,
                listOf(),
                basicSimpleWowView.game,
                basicSimpleWowView.owner,
                basicSimpleWowView.featured,
                null
            )
            val event1 = Event("/credentials/client1", UUID.randomUUID().toString(), payload)
            val event2 = Event("/credentials/client1", UUID.randomUUID().toString(), payload)
            store.save(event1)
            store.save(event2)
            val expected = listOf(EventWithVersion(1, event1), EventWithVersion(2, event2))

            assertEquals(expected, store.state())
        }
    }

    @Test
    fun `given an store with events i can retrieve them`() {
        runBlocking {
            val payload = ViewToBeCreatedEvent(
                UUID.randomUUID().toString(),
                basicSimpleWowView.name,
                basicSimpleWowView.published,
                listOf(),
                basicSimpleWowView.game,
                basicSimpleWowView.owner,
                basicSimpleWowView.featured,
                null
            )
            val event1 = EventWithVersion(1, Event("/credentials/client1", UUID.randomUUID().toString(), payload))
            val event2 = EventWithVersion(2, Event("/credentials/client1", UUID.randomUUID().toString(), payload))
            val storeWithEvents = store.withState(listOf(event1, event2))
            val expected = listOf(event1, event2)

            assertEquals(expected, storeWithEvents.getEvents(null).toList())
        }
    }

    @Test
    fun `given an store with events i can retrieve them starting from a version`() {
        runBlocking {
            val payload = ViewToBeCreatedEvent(
                UUID.randomUUID().toString(),
                basicSimpleWowView.name,
                basicSimpleWowView.published,
                listOf(),
                basicSimpleWowView.game,
                basicSimpleWowView.owner,
                basicSimpleWowView.featured,
                null
            )
            val event1 = EventWithVersion(1, Event("/credentials/client1", UUID.randomUUID().toString(), payload))
            val event2 = EventWithVersion(2, Event("/credentials/client1", UUID.randomUUID().toString(), payload))
            val storeWithEvents = store.withState(listOf(event1, event2))
            val expected = listOf(event2)

            assertEquals(expected, storeWithEvents.getEvents(1).toList())
        }
    }

    @Test
    fun `given a store with events i can retrieve events by operation id`() {
        runBlocking {
            val payload = ViewToBeCreatedEvent(
                UUID.randomUUID().toString(),
                basicSimpleWowView.name,
                basicSimpleWowView.published,
                listOf(),
                basicSimpleWowView.game,
                basicSimpleWowView.owner,
                basicSimpleWowView.featured,
                null
            )
            val targetOperationId = UUID.randomUUID().toString()
            val otherOperationId = UUID.randomUUID().toString()
            val event1 = EventWithVersion(1, Event("/credentials/client1", targetOperationId, payload))
            val event2 = EventWithVersion(2, Event("/credentials/client1", otherOperationId, payload))
            val event3 = EventWithVersion(3, Event("/credentials/client1", targetOperationId, payload))
            val storeWithEvents = store.withState(listOf(event1, event2, event3))

            assertEquals(listOf(event1, event3), storeWithEvents.getEventsByOperationId(targetOperationId))
        }
    }

    @Test
    fun `given a store with events i get empty list for an unknown operation id`() {
        runBlocking {
            val payload = ViewToBeCreatedEvent(
                UUID.randomUUID().toString(),
                basicSimpleWowView.name,
                basicSimpleWowView.published,
                listOf(),
                basicSimpleWowView.game,
                basicSimpleWowView.owner,
                basicSimpleWowView.featured,
                null
            )
            val event1 = EventWithVersion(1, Event("/credentials/client1", UUID.randomUUID().toString(), payload))
            val storeWithEvents = store.withState(listOf(event1))

            assertEquals(emptyList(), storeWithEvents.getEventsByOperationId("unknown-op"))
        }
    }

    @Test
    fun `given a store with events deleteEventsOlderThan does not delete events newer than the given timestamp`() {
        runBlocking {
            val payload = ViewToBeCreatedEvent(
                UUID.randomUUID().toString(),
                basicSimpleWowView.name,
                basicSimpleWowView.published,
                listOf(),
                basicSimpleWowView.game,
                basicSimpleWowView.owner,
                basicSimpleWowView.featured,
                null
            )
            store.save(Event("/credentials/client1", UUID.randomUUID().toString(), payload))

            val deleted = store.deleteEventsOlderThan(OffsetDateTime.now().minusDays(1), Long.MAX_VALUE)

            assertEquals(0, deleted)
            assertEquals(1, store.getEvents(null).toList().size)
        }
    }

    @Test
    fun `given a store with events deleteEventsOlderThan deletes events older than the given timestamp up to the given version`() {
        runBlocking {
            val payload = ViewToBeCreatedEvent(
                UUID.randomUUID().toString(),
                basicSimpleWowView.name,
                basicSimpleWowView.published,
                listOf(),
                basicSimpleWowView.game,
                basicSimpleWowView.owner,
                basicSimpleWowView.featured,
                null
            )
            store.save(Event("/credentials/client1", UUID.randomUUID().toString(), payload))
            store.save(Event("/credentials/client1", UUID.randomUUID().toString(), payload))
            val cutoff = OffsetDateTime.now().plusDays(1)

            val deleted = store.deleteEventsOlderThan(cutoff, 1)

            assertEquals(1, deleted)
            val remaining = store.getEvents(null).toList()
            assertEquals(1, remaining.size)
            assertEquals(2, remaining.single().version)
        }
    }
}

class EventStoreInMemoryTest : EventStoreTest() {
    override val store = EventStoreInMemory()

    @BeforeEach
    fun beforeEach() {
        store.clear()
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventStoreDatabaseTest : EventStoreTest() {
    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    override val store = EventStoreDatabase(Database.connect(embeddedPostgres.postgresDatabase))

    @BeforeEach
    fun beforeEach() {
        flyway.clean()
        flyway.migrate()
    }

    @AfterAll
    fun afterAll() {
        embeddedPostgres.close()
    }
}