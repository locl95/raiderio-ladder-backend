package com.kos.datacache

import com.kos.common.DatabaseFactory
import com.kos.datacache.TestHelper.dataCache
import com.kos.datacache.TestHelper.outdatedDataCache
import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.datacache.repository.DataCacheRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

abstract class DataCacheRepositoryTest {

    abstract val repository: DataCacheRepository

    @BeforeTest
    abstract fun beforeEach()

    @Test
    open fun `given an empty repository i can insert data`() {
        runBlocking {
            assertEquals(listOf(), repository.state())
            assertEquals(true, repository.insert(listOf(dataCache)))
            assertEquals(listOf(dataCache), repository.state())
        }
    }

    @Test
    open fun `given a repository with a single cached data i can retrieve it`() {
        runBlocking {
            val repositoryWithState = repository.withState(listOf(dataCache))
            assertEquals(listOf(dataCache), repositoryWithState.get(1))
        }
    }

    @Test
    open fun `given a repository with multiple cached data i can retrieve the only ones related to a certain character`() {
        runBlocking {
            val repositoryWithState = repository.withState(listOf(dataCache, dataCache.copy(characterId = 2)))
            assertEquals(listOf(dataCache), repositoryWithState.get(1))
        }
    }

    @Test
    open fun `giver a repository with an expired record i can clear it`() {
        runBlocking {
            val repositoryWithState = repository.withState(listOf(dataCache, outdatedDataCache))
            assertEquals(1, repositoryWithState.deleteExpiredRecord(24))
            assertEquals(listOf(dataCache), repositoryWithState.state())
        }
    }
}

class DataCacheInMemoryRepositoryTest : DataCacheRepositoryTest() {
    override val repository = DataCacheInMemoryRepository()
    override fun beforeEach() {
        repository.clear()
    }
}

class DataCacheDatabaseRepositoryTest : DataCacheRepositoryTest() {
    override val repository: DataCacheRepository = DataCacheDatabaseRepository()
    override fun beforeEach() {
        DatabaseFactory.init(mustClean = true)
    }
}
