package com.kos.common

import arrow.atomic.Atomic
import arrow.atomic.update
import arrow.atomic.value
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DynamicCache<T> : WithLogger("dynamicCache") {

    //TODO: Add size limit. We don't want to fuck up heap because we stored millions of values.
    private val cache: MutableMap<String, T> = mutableMapOf()
    private val inFlight: MutableMap<String, CompletableDeferred<T>> = mutableMapOf()
    private val mutex = Mutex()
    private val hits = Atomic<Int>(0)
    private val miss = Atomic<Int>(0)

    val hitRate: Double
        get() = if (hits.value + miss.value == 0) 0.0 else hits.value.toDouble() / (hits.value + miss.value)

    val numberOfAccess: Int
        get() = hits.value + miss.value

    suspend fun get(id: String, fetch: suspend () -> T): T {
        val (deferred, shouldFetch) = mutex.withLock {
            cache[id]?.let { cached ->
                hits.update { it + 1 }
                return cached
            }
            inFlight[id]?.let { existing ->
                hits.update { it + 1 }
                return@withLock Pair(existing, false)
            }
            miss.update { it + 1 }
            val d = CompletableDeferred<T>()
            inFlight[id] = d
            Pair(d, true)
        }

        return if (shouldFetch) {
            try {
                val result = fetch()
                mutex.withLock {
                    cache[id] = result
                    inFlight.remove(id)
                }
                deferred.complete(result)
                result
            } catch (e: Throwable) {
                mutex.withLock { inFlight.remove(id) }
                deferred.completeExceptionally(e)
                throw e
            }
        } else {
            deferred.await()
        }
    }

}
