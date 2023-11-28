package com.kos.eventsourcing.subscriptions

import com.kos.common.OffsetDateTimeSerializer
import com.kos.common.Retry.retryWithExponentialBackoff
import com.kos.common.WithLogger
import com.kos.eventsourcing.events.EventWithVersion
import com.kos.eventsourcing.events.repository.EventStore
import com.kos.eventsourcing.subscriptions.repository.SubscriptionsRepository
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

enum class SubscriptionStatus {
    WAITING,
    RUNNING,
    FAILED
}


@Serializable
data class SubscriptionState(
    val status: SubscriptionStatus,
    val version: Long,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val time: OffsetDateTime,
    val lastError: String? = null
)

class EventSubscription(
    private val subscriptionName: String,
    private val eventStore: EventStore,
    private val subscriptionsRepository: SubscriptionsRepository,
    private val process: suspend (EventWithVersion) -> Unit
) : WithLogger("event-subscription-$subscriptionName") {
    suspend fun processPendingEvents(): Unit {
        val initialState: SubscriptionState =
            subscriptionsRepository.getState(subscriptionName) ?: throw Exception("Not found subscription")
        val hasSucceededWithVersion =
            eventStore.getEvents(initialState.version).fold(Pair(false, initialState.version)) { _, event ->
                try {
                    retryWithExponentialBackoff(10) { process(event) }
                    subscriptionsRepository.setState(
                        subscriptionName,
                        SubscriptionState(SubscriptionStatus.RUNNING, event.version, OffsetDateTime.now())
                    )
                    Pair(true, event.version)
                } catch (e: Exception) {
                    subscriptionsRepository.setState(
                        subscriptionName,
                        SubscriptionState(SubscriptionStatus.FAILED, event.version, OffsetDateTime.now(), e.message)
                    )
                    logger.error("processing event ${event.version} has failed because ${e.message}")
                    Pair(false, event.version)
                }
            }
        if (hasSucceededWithVersion.first) {
            subscriptionsRepository.setState(
                subscriptionName,
                SubscriptionState(SubscriptionStatus.WAITING, hasSucceededWithVersion.second, OffsetDateTime.now())
            )
        }
    }
}