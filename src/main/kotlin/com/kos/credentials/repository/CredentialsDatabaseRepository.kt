package com.kos.credentials.repository

import com.kos.common.DatabaseFactory
import com.kos.credentials.Activity
import com.kos.credentials.Credentials
import org.jetbrains.exposed.sql.*

class CredentialsDatabaseRepository : CredentialsRepository {
    suspend fun withState(initialState: List<Credentials>): CredentialsDatabaseRepository {
        DatabaseFactory.dbQuery {
            Users.batchInsert(initialState) {
                this[Users.userName] = it.userName
                this[Users.password] = it.password
            }
        }
        return this
    }

    object Users : Table() {
        val userName = varchar("user_name", 48)
        val password = varchar("password", 48)

        override val primaryKey = PrimaryKey(userName)
    }

    private fun resultRowToUser(row: ResultRow) = Credentials(
        row[Users.userName],
        row[Users.password]
    )

    override suspend fun getCredentials(userName: String): Credentials? {
        return DatabaseFactory.dbQuery {
            Users.select { Users.userName.eq(userName) }.map { resultRowToUser(it) }.singleOrNull()
        }
    }

    override suspend fun insertCredentials(credentials: Credentials): Unit {
        DatabaseFactory.dbQuery {
            Users.insert {
                it[userName] = credentials.userName
                it[password] = credentials.password
            }
        }
    }

    object CredentialsRoles : Table("credentials_roles") {
        val userName = varchar("user_name", 48)
        val role = varchar("role", 48)

        override val primaryKey = PrimaryKey(userName, role)
    }

    object RolesActivities : Table("roles_activities") {
        val role = varchar("role", 48)
        val activity = varchar("activity", 128)

        override val primaryKey = PrimaryKey(role, activity)
    }

    override suspend fun getActivities(user: String): List<Activity> {
        return DatabaseFactory.dbQuery {
            CredentialsRoles.join(RolesActivities, JoinType.INNER, null, null) {
                CredentialsRoles.role eq RolesActivities.role
            }.select { CredentialsRoles.userName.eq(user) }.map { it[RolesActivities.activity] }
        }
    }

    override suspend fun editCredentials(userName: String, newPassword: String) {
        DatabaseFactory.dbQuery {
            Users.update({ Users.userName.eq(userName) }) {
                it[password] = newPassword
            }
        }
    }

    override suspend fun state(): List<Credentials> {
        return DatabaseFactory.dbQuery {
            Users.selectAll().map { resultRowToUser(it) }
        }
    }
}