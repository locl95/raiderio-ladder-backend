package com.kos.common

import com.kos.common.Retry.retryWithExponentialBackoff
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RetryTest {

    @Test
    fun `retryWithExponentialBackoff should return result on successful attempt`() = runTest {
        val result = retryWithExponentialBackoff(5) {
            "Success"
        }

        assertEquals("Success", result)
    }

    @Test(expected = RuntimeException::class)
    fun `retryWithExponentialBackoff should throw exception after maxAttempts unsuccessful attempts`() = runTest {
        retryWithExponentialBackoff(3) {
            throw RuntimeException("Simulated error")
        }
    }

    @Test
    fun `retryWithExponentialBackoff should succeed after one unsuccessful attempt`() = runTest {
        val result = retryWithExponentialBackoff(2) {
            if (it == 0) {
                throw RuntimeException("Simulated error")
            } else {
                "Success"
            }
        }

        assertEquals("Success", result)
    }
}
