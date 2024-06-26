package com.kos.roles.repository

import com.kos.activities.Activity
import com.kos.common.InMemoryRepository
import com.kos.roles.Role

class RolesActivitiesInMemoryRepository : RolesActivitiesRepository, InMemoryRepository {
    private val rolesActivities = mutableMapOf<Role, List<Activity>>()

    override suspend fun state(): Map<Role, List<Activity>> {
        return rolesActivities.mapValues { it.value.reversed() }
    }

    override suspend fun withState(initialState: Map<Role, List<Activity>>): RolesActivitiesRepository {
        rolesActivities.putAll(initialState)
        return this
    }

    override suspend fun insertActivityToRole(activity: Activity, role: Role): Unit {
        rolesActivities.compute(role) { _, currentActivities -> (currentActivities ?: mutableListOf()) + activity }
    }

    override suspend fun deleteActivityFromRole(activity: Activity, role: Role): Unit {
        rolesActivities.computeIfPresent(role) { _, currentActivities ->
            currentActivities.toMutableList().apply { remove(activity) }
        }
    }

    override suspend fun getActivitiesFromRole(role: Role): Set<Activity> {
        return rolesActivities[role].orEmpty().toSet()
    }

    override fun clear() {
        rolesActivities.clear()
    }
}