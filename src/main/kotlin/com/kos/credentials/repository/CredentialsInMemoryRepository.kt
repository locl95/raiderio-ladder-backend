package com.kos.credentials.repository

import com.kos.credentials.Credentials

class CredentialsInMemoryRepository(initialState: List<Credentials> = mutableListOf()) : CredentialsRepository {
    private val users = mutableListOf<Credentials>()

    init {
        users.addAll(initialState)
    }

    override suspend fun getCredentials(userName: String): Credentials? {
        return users.find { it.userName == userName }
    }

    override suspend fun insertCredentials(credentials: Credentials): Unit {
        users.add(credentials)
    }

    override suspend fun state(): List<Credentials> {
        return users
    }
}