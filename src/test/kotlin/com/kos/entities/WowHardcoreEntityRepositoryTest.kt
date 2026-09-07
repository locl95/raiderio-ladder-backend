package com.kos.entities

import com.kos.common.WithState
import com.kos.datacache.DataCache
import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.EntitiesTestHelper.basicLolEntityEnrichedRequest
import com.kos.entities.EntitiesTestHelper.basicWowRequestWithBlizzardId
import com.kos.entities.EntitiesTestHelper.basicWowHardcoreEntity
import com.kos.entities.EntitiesTestHelper.basicWowRequest
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.InsertEntityRequest
import com.kos.entities.domain.WowEntity
import com.kos.entities.domain.WowEntityRequest
import com.kos.entities.repository.GameEntityRepository
import com.kos.entities.repository.WowHardcoreEntityDatabaseRepository
import com.kos.entities.repository.WowHardcoreEntityInMemoryRepository
import com.kos.views.Game
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

abstract class WowHardcoreEntityRepositoryTestCommon<T> where T : GameEntityRepository, T : WithState<List<WowEntity>, T> {

    abstract val repository: T
    abstract val dataCacheRepository: DataCacheRepository

    @Test
    fun `given an empty repository i can insert wow hardcore characters`() {
        runBlocking {
            val expected = listOf(basicWowHardcoreEntity)
            repository.insert(listOf(basicWowRequestWithBlizzardId)).fold({ fail() }) { assertEquals(expected, it) }
        }
    }

    @Test
    fun `inserting a wow hardcore character with the same blizzardId as an existing one fails`() {
        runBlocking {
            repository.withState(listOf(basicWowHardcoreEntity))
            repository.insert(
                listOf(
                    WowEntityRequest(
                        basicWowHardcoreEntity.name,
                        basicWowHardcoreEntity.region,
                        basicWowHardcoreEntity.realm,
                        basicWowHardcoreEntity.blizzardId
                    )
                )
            ).onRight { fail() }.onLeft { assertTrue(true) }
        }
    }

    @Test
    fun `inserting a request of the wrong type fails`() {
        runBlocking {
            assertTrue(repository.insert(listOf(basicLolEntityEnrichedRequest)).isLeft())
            assertEquals(listOf(), repository.state())
        }
    }

    @Test
    fun `inserting a wow character request without a blizzardId is also accepted, defaulting blizzardId to 0`() {
        runBlocking {
            val expected = basicWowHardcoreEntity.copy(blizzardId = 0)
            repository.insert(listOf(basicWowRequest)).fold({ fail() }) { assertEquals(listOf(expected), it) }
        }
    }

    @Test
    fun `given a repository with a wow hardcore character, i can update it`() {
        runBlocking {
            repository.withState(listOf(basicWowHardcoreEntity))
            val updatedName = "camilo"
            val updatedRegion = "eu"
            val updatedRealm = "stitches"
            val request = WowEntityRequest(updatedName, updatedRegion, updatedRealm)
            val update = repository.update(1, request)
            update
                .onRight { assertEquals(1, it) }
                .onLeft { fail(it.message) }
            val updated = repository.state().first()
            assertEquals(updatedName, updated.name)
            assertEquals(updatedRegion, updated.region)
            assertEquals(updatedRealm, updated.realm)
        }
    }

    @Test
    fun `updating a request of the wrong type fails`() {
        runBlocking {
            repository.withState(listOf(basicWowHardcoreEntity))
            assertTrue(repository.update(1, basicLolEntityEnrichedRequest).isLeft())
        }
    }

    @Test
    fun `given a repository of wow hardcore characters, i can retrieve one by id`() {
        runBlocking {
            repository.withState(listOf(basicWowHardcoreEntity))
            assertEquals(basicWowHardcoreEntity, repository.get(basicWowHardcoreEntity.id))
            assertEquals(null, repository.get(9999))
        }
    }

    @Test
    fun `given a repository of wow hardcore characters, i can retrieve one by a character request`() {
        runBlocking {
            repository.withState(listOf(basicWowHardcoreEntity))
            val request: EntityRequest = WowEntityRequest(
                basicWowHardcoreEntity.name,
                basicWowHardcoreEntity.region,
                basicWowHardcoreEntity.realm
            )
            assertEquals(basicWowHardcoreEntity, repository.get(request))
        }
    }

    @Test
    fun `given a repository of wow hardcore characters, i can retrieve one by an insert request`() {
        runBlocking {
            repository.withState(listOf(basicWowHardcoreEntity))
            val request: InsertEntityRequest = WowEntityRequest(
                basicWowHardcoreEntity.name,
                basicWowHardcoreEntity.region,
                basicWowHardcoreEntity.realm
            )
            assertEquals(basicWowHardcoreEntity, repository.get(request))
        }
    }

    @Test
    fun `given a repository of wow hardcore characters, i can retrieve all of them`() {
        runBlocking {
            repository.withState(listOf(basicWowHardcoreEntity))
            assertEquals(listOf(basicWowHardcoreEntity), repository.getAll())
        }
    }

    @Test
    fun `get characters to sync should filter WOW_HC characters by staleness just like other games`() {
        runBlocking {
            val wowHcEntities = (1..3).map {
                WowEntity(it.toLong(), it.toString(), it.toString(), it.toString(), it.toLong())
            }
            repository.withState(wowHcEntities)

            dataCacheRepository.withState(
                listOf(
                    DataCache(1, "", OffsetDateTime.now(), Game.WOW_HC),
                    DataCache(2, "", OffsetDateTime.now().minusMinutes(31), Game.WOW_HC)
                )
            )
            val res = repository.getOlderThan(30, Int.MAX_VALUE)

            assertEquals(setOf<Long>(2, 3), res.map { it.id }.toSet())
        }
    }
}

class WowHardcoreEntityInMemoryRepositoryTest : WowHardcoreEntityRepositoryTestCommon<WowHardcoreEntityInMemoryRepository>() {
    override val dataCacheRepository = DataCacheInMemoryRepository()
    override val repository: WowHardcoreEntityInMemoryRepository by lazy {
        WowHardcoreEntityInMemoryRepository(dataCacheRepository) {
            val ids = repository.state().map { it.id }
            if (ids.isEmpty()) 1L else ids.max() + 1
        }
    }

    @BeforeEach
    fun beforeEach() {
        dataCacheRepository.clear()
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WowHardcoreEntityDatabaseRepositoryTest : WowHardcoreEntityRepositoryTestCommon<WowHardcoreEntityDatabaseRepository>() {
    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    override val repository = WowHardcoreEntityDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))
    override val dataCacheRepository = DataCacheDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))

    @BeforeEach
    fun beforeEach() {
        flyway.clean()
        flyway.migrate()
    }

    @AfterAll
    fun afterAll() {
        embeddedPostgres.close()
    }
}
