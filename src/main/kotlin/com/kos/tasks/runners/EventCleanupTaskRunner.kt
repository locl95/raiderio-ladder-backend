package com.kos.tasks.runners

import com.kos.common.WithLogger
import com.kos.common.fold
import com.kos.eventsourcing.events.repository.EventStore
import com.kos.eventsourcing.subscriptions.repository.SubscriptionsRepository
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksRepository
import java.time.OffsetDateTime

class EventCleanupTaskRunner(
    private val tasksRepository: TasksRepository,
    private val eventStore: EventStore,
    private val subscriptionsRepository: SubscriptionsRepository
) : TaskRunner, WithLogger("eventCleanupTaskRunner") {

    override val type = TaskType.EVENT_CLEANUP_TASK
    private val olderThanDays: Long = 3

    override suspend fun run(id: String, arguments: Map<String, String>?) {
        logger.info("Running event cleanup task")
        val cutoff = OffsetDateTime.now().minusDays(olderThanDays)
        val message = subscriptionsRepository.getEventSubscriptions().values.minOfOrNull { it.version }
            .fold(
                left = { "No subscriptions found, nothing deleted" },
                right = { minProcessedVersion ->
                    val deletedEvents = eventStore.deleteEventsOlderThan(cutoff, minProcessedVersion)
                    "Deleted $deletedEvents old events"
                }
            )
        logger.info(message)

        tasksRepository.updateTask(
            Task(id, type, TaskStatus(Status.SUCCESSFUL, message), OffsetDateTime.now())
        )
    }
}
