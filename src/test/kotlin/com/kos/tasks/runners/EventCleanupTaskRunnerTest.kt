package com.kos.tasks.runners

import com.kos.eventsourcing.events.Event
import com.kos.eventsourcing.events.ViewToBeCreatedEvent
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.eventsourcing.subscriptions.SubscriptionState
import com.kos.eventsourcing.subscriptions.SubscriptionStatus
import com.kos.eventsourcing.subscriptions.repository.SubscriptionsInMemoryRepository
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksInMemoryRepository
import com.kos.views.ViewsTestHelper.basicSimpleWowView
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class EventCleanupTaskRunnerTest {

    private val tasksRepo = TasksInMemoryRepository()
    private val eventStore = EventStoreInMemory()
    private val subscriptionsRepo = SubscriptionsInMemoryRepository()
    private val runner = EventCleanupTaskRunner(tasksRepo, eventStore, subscriptionsRepo)

    private val payload = ViewToBeCreatedEvent(
        UUID.randomUUID().toString(),
        basicSimpleWowView.name,
        basicSimpleWowView.published,
        listOf(),
        basicSimpleWowView.game,
        basicSimpleWowView.owner,
        basicSimpleWowView.featured,
        null
    )

    @Test
    fun `cleanup is skipped and recorded as successful when there are no subscriptions`() = runBlocking {
        eventStore.save(Event("/credentials/client1", UUID.randomUUID().toString(), payload))

        val id = UUID.randomUUID().toString()
        tasksRepo.insertTask(Task(id, runner.type, TaskStatus(Status.PENDING, null), OffsetDateTime.now()))

        runner.run(id, null)

        val recorded = tasksRepo.getTask(id)
        assertEquals(Status.SUCCESSFUL, recorded?.taskStatus?.status)
        assertEquals("No subscriptions found, nothing deleted", recorded?.taskStatus?.message)
        assertEquals(1, eventStore.getEvents(null).toList().size)
    }

    @Test
    fun `cleanup uses the least advanced subscription version as the deletion bound`() =
        runBlocking {
            eventStore.save(Event("/credentials/client1", UUID.randomUUID().toString(), payload))
            eventStore.save(Event("/credentials/client1", UUID.randomUUID().toString(), payload))
            subscriptionsRepo.withState(
                mapOf(
                    "views" to SubscriptionState(SubscriptionStatus.WAITING, 2, OffsetDateTime.now()),
                    "entities" to SubscriptionState(SubscriptionStatus.WAITING, 0, OffsetDateTime.now())
                )
            )

            val id = UUID.randomUUID().toString()
            tasksRepo.insertTask(Task(id, runner.type, TaskStatus(Status.PENDING, null), OffsetDateTime.now()))

            runner.run(id, null)

            val recorded = tasksRepo.getTask(id)
            assertEquals(Status.SUCCESSFUL, recorded?.taskStatus?.status)
            assertEquals("Deleted 0 old events", recorded?.taskStatus?.message)
            assertEquals(2, eventStore.getEvents(null).toList().size)
        }
}
