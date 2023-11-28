package com.kos.eventsourcing.subscriptions.repository

import com.kos.common.InMemoryRepository
import com.kos.eventsourcing.subscriptions.SubscriptionState
import com.kos.eventsourcing.subscriptions.repository.SubscriptionsRepository

class SubscriptionsInMemoryRepository : SubscriptionsRepository, InMemoryRepository {
    private val subscriptions = mutableMapOf<String, SubscriptionState>()

    override suspend fun getState(name: String): SubscriptionState? {
        return subscriptions[name]
    }

    override suspend fun setState(subscriptionName: String, subscriptionState: SubscriptionState) {
        subscriptions[subscriptionName] = subscriptionState
    }

    override suspend fun state(): Map<String, SubscriptionState> {
        return subscriptions
    }

    override suspend fun withState(initialState: Map<String, SubscriptionState>): SubscriptionsRepository {
        subscriptions.putAll(initialState)
        return this
    }

    override fun clear() {
        subscriptions.clear()
    }
}
