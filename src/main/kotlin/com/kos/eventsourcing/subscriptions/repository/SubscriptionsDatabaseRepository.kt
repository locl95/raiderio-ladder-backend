package com.kos.eventsourcing.subscriptions.repository

import com.kos.common.DatabaseFactory.dbQuery
import com.kos.eventsourcing.subscriptions.SubscriptionState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*

class SubscriptionsDatabaseRepository : SubscriptionsRepository {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    object Subscriptions : Table() {
        val name = varchar("name", 128)
        val state = text("state")

        override val primaryKey = PrimaryKey(name)
        override val tableName = "subscriptions"
    }

    private fun resultRowToSubscriptionState(row: ResultRow): SubscriptionState =
        json.decodeFromString(row[Subscriptions.state])

    private fun resultRowToSubscription(row: ResultRow): Pair<String, SubscriptionState> =
        Pair(row[Subscriptions.name], json.decodeFromString(row[Subscriptions.state]))

    override suspend fun getState(name: String): SubscriptionState? =
        dbQuery {
            Subscriptions.select { Subscriptions.name.eq(name) }.map { resultRowToSubscriptionState(it) }.singleOrNull()
        }

    override suspend fun setState(subscriptionName: String, subscriptionState: SubscriptionState) {
        dbQuery {
            Subscriptions.update({ Subscriptions.name.eq(subscriptionName) }) {
                it[state] = json.encodeToString(subscriptionState)
            }
        }
    }

    override suspend fun state(): Map<String, SubscriptionState> {
        return dbQuery {
            Subscriptions.selectAll().associate { resultRowToSubscription(it) }
        }
    }

    override suspend fun withState(initialState: Map<String, SubscriptionState>): SubscriptionsRepository {
        dbQuery {
            Subscriptions.batchInsert(initialState.toList()) {
                this[Subscriptions.name] = it.first
                this[Subscriptions.state] = json.encodeToString(it.second)
            }
        }
        return this
    }
}