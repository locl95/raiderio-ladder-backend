package com.kos.auth

import arrow.core.Either
import arrow.core.contains
import com.kos.auth.repository.AuthDatabaseRepository
import com.kos.common.DatabaseFactory
import kotlinx.coroutines.runBlocking
import org.junit.Before
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthDatabaseRepositoryTest: AuthRepositoryTest {

    @Before
    fun beforeEach() {
        DatabaseFactory.init(mustClean = true)
    }

    @Test
    override fun ICanValidateCredentials() {
        runBlocking {
            val repository = AuthDatabaseRepository().withState(Pair(listOf(User("test", "test")), listOf()))
            assertTrue(repository.validateCredentials("test", "test"))
        }
    }
    @Test
    override fun ICanValidateToken() {
        runBlocking {
            val repository = AuthDatabaseRepository().withState(
                Pair(
                    listOf(),
                    listOf(Authorization("test", "test", OffsetDateTime.now(), OffsetDateTime.now().plusHours(24)))
                )
            )
            assertTrue(repository.validateToken("test").contains("test"))
            assertEquals(1, repository.state().second.size)
        }
    }

    @Test
    override fun ICanValidateExpiredToken() {
        runBlocking {
            val validUntil = OffsetDateTime.now().minusHours(1)
            val repository = AuthDatabaseRepository().withState(
                Pair(
                    listOf(),
                    listOf(Authorization("test", "test", OffsetDateTime.now(), validUntil))
                )
            )
            val tokenOrError = repository.validateToken("test")
            assertEquals(tokenOrError, Either.Left(TokenExpired("test", validUntil)))
            assertEquals(1, repository.state().second.size)
        }
    }

    @Test
    override fun ICanValidatePersistentToken() {
        runBlocking {
            val repository = AuthDatabaseRepository().withState(
                Pair(
                    listOf(),
                    listOf(Authorization("test", "test", OffsetDateTime.now(), null))
                )
            )
            val tokenOrError = repository.validateToken("test")
            assertEquals(tokenOrError, Either.Right("test"))
            assertEquals(1, repository.state().second.size)
        }
    }

    @Test
    override fun ICanLogin() {
        runBlocking {
            val repository = AuthDatabaseRepository()
            val userName = repository.insertToken("test")?.userName
            assertEquals("test", userName)
            val finalStateOfAuthorizations = repository.state().second
            assertContains(finalStateOfAuthorizations.map { it.userName }, "test")
        }
    }

    @Test
    override fun ICanLogout() {
        runBlocking {
            val repository = AuthDatabaseRepository().withState(
                Pair(
                    listOf(),
                    listOf(Authorization("test", "test", OffsetDateTime.now(), OffsetDateTime.now().plusHours(24)))
                )
            )
            assertTrue(repository.deleteToken("test"))
            assertTrue(repository.state().second.isEmpty())
        }
    }
}