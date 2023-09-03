package com.kos.datacache

import com.kos.datacache.TestHelper.dataCache
import com.kos.datacache.TestHelper.outdatedDataCache
import com.kos.datacache.repository.DataCacheInMemoryRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class DataCacheInMemoryRepositoryTest : DataCacheRepositoryTest {

    @Test
    override fun ICanInsertData() {
        val repo = DataCacheInMemoryRepository()
        runBlocking { assertEquals(listOf(), repo.state()) }
        runBlocking { assertEquals(true, repo.insert(dataCache)) }
        runBlocking { assertEquals(listOf(dataCache), repo.state()) }
    }

    @Test
    override fun ICanUpdateData() {
        val repo = DataCacheInMemoryRepository(listOf(outdatedDataCache))
        runBlocking { assertEquals(listOf(outdatedDataCache), repo.state()) }
        runBlocking { assertEquals(true, repo.update(dataCache)) }
        runBlocking { assertEquals(listOf(dataCache), repo.state()) }
    }

    @Test
    override fun ICanGetData() {
        val repo = DataCacheInMemoryRepository(listOf(dataCache))
        runBlocking { assertEquals(dataCache, repo.get(1)) }
    }
}