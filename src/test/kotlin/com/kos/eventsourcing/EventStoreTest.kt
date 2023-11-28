package com.kos.eventsourcing

import com.kos.common.DatabaseFactory
import com.kos.eventsourcing.events.Event
import com.kos.eventsourcing.events.EventData
import com.kos.eventsourcing.events.repository.EventStore
import com.kos.eventsourcing.events.repository.EventStoreDatabase
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
data class TestData(val int: Int, val string: String): EventData

abstract class EventStoreTest {

    abstract val store: EventStore

    @BeforeTest
    abstract fun beforeEach()

    @Test
    fun `given an empty store i can save events`() {
        runBlocking {
            val event1 = Event("TestAggregate","TestDataEvent", TestData(1, "Data"))
            val event2 = Event("TestAggregate2","TestDataEvent", TestData(2, "Data2"))
            val savedEvent1 = store.save(event1)
            val savedEvent2 = store.save(event2)
            val expected = listOf(savedEvent1, savedEvent2)

            assertEquals(expected, store.state())
        }
    }
}

class EventStoreInMemoryTest : EventStoreTest() {
    override val store = EventStoreInMemory()
    override fun beforeEach() {
        store.clear()
    }
}

class EventStoreDatabaseTest : EventStoreTest() {
    override val store = EventStoreDatabase()
    override fun beforeEach() {
        DatabaseFactory.init(mustClean = true)
    }
}