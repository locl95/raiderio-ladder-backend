package com.kos.credentials

import com.kos.credentials.repository.CredentialsInMemoryRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class CredentialsInMemoryRepositoryTest : CredentialsRepositoryTest {
    @Test
    override fun ICanGetCredentials() {
        runBlocking {
            val credentialsInMemoryRepository = CredentialsInMemoryRepository(listOf(Credentials("test", "test")))
            assertEquals(credentialsInMemoryRepository.getCredentials("test"), Credentials("test", "test"))
        }
    }

}