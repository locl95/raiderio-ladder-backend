package com.kos.common

import kotlinx.coroutines.delay

object Retry : WithLogger("retry") {
    suspend fun <T> retryWithExponentialBackoff(
        maxAttempts: Int,
        attempt: Int = 1,
        initialDelayMillis: Long = 100,
        factor: Double = 2.0,
        maxDelayMillis: Long = Long.MAX_VALUE,
        block: suspend (attempt: Int) -> T
    ): T {
        return try {
            block(attempt)
        } catch (e: Exception) {
            if (attempt < maxAttempts) {
                delay(initialDelayMillis)
                val nextDelay = (initialDelayMillis * factor).coerceAtMost(maxDelayMillis.toDouble()).toLong()
                logger.info("Retry number: $attempt, next delay: $nextDelay, cause: ${e.message}")
                retryWithExponentialBackoff(maxAttempts, attempt + 1, nextDelay, factor, maxDelayMillis, block)
            } else {
                throw e
            }
        }
    }
}