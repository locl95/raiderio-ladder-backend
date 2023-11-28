package com.kos.eventsourcing.subscriptions.repository

import com.kos.common.WithState
import com.kos.eventsourcing.subscriptions.SubscriptionState

interface SubscriptionsRepository: WithState<Map<String, SubscriptionState>, SubscriptionsRepository> {
    suspend fun getState(name: String): SubscriptionState?
    suspend fun setState(subscriptionName: String, subscriptionState: SubscriptionState)
}
