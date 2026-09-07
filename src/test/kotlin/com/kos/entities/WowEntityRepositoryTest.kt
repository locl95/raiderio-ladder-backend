package com.kos.entities

import com.kos.datacache.DataCache
import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.EntitiesTestHelper.basicLolEntityEnrichedRequest
import com.kos.entities.EntitiesTestHelper.basicWowRequestWithBlizzardId
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.EntitiesTestHelper.basicWowEntity2
import com.kos.entities.EntitiesTestHelper.basicWowRequest
import com.kos.entities.EntitiesTestHelper.basicWowRequest2
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.InsertEntityRequest
import com.kos.entities.domain.WowEntity
import com.kos.entities.domain.WowEntityRequest
import com.kos.common.WithState
import com.kos.entities.repository.GameEntityRepository
import com.kos.entities.repository.WowEntityDatabaseRepository
import com.kos.entities.repository.WowEntityInMemoryRepository
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

abstract class WowEntityRepositoryTestCommon<T> where T : GameEntityRepository, T : WithState<List<WowEntity>, T> {

    abstract val repository: T
    abstract val dataCacheRepository: DataCacheRepository

    @Test
    fun `given an empty repository i can insert wow characters`() {
        runBlocking {
            val expected = listOf(basicWowEntity)
            repository.insert(listOf(basicWowRequest)).fold({ fail() }) { assertEquals(expected, it) }
        }
    }

    @Test
    fun `given an empty repository inserting a wow character that already exists fails`() {
        runBlocking {
            val character = WowEntityRequest(basicWowEntity.name, basicWowEntity.region, basicWowEntity.realm)

            assertTrue(repository.insert(listOf(character, character)).isLeft())
        }
    }

    @Test
    fun `given a repository that includes a wow character, adding the same one fails`() {
        runBlocking {
            repository.withState(listOf(basicWowEntity, basicWowEntity2))
            assertTrue(repository.insert(listOf(basicWowRequest)).isLeft())
            assertEquals(listOf(basicWowEntity, basicWowEntity2), repository.state())
        }
    }

    @Test
    fun `given a repository with wow characters, I can insert more`() {
        runBlocking {
            repository.withState(listOf(basicWowEntity))
            val inserted = repository.insert(listOf(basicWowRequest2))
            inserted
                .onRight { characters -> assertEquals(listOf<Long>(2), characters.map { it.id }) }
                .onLeft { fail(it.message) }
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
    fun `given an empty repository i can insert a wow character with a blizzardId`() {
        runBlocking {
            val expected = listOf(basicWowEntity.copy(blizzardId = basicWowRequestWithBlizzardId.blizzardId))
            repository.insert(listOf(basicWowRequestWithBlizzardId)).fold({ fail() }) { assertEquals(expected, it) }
        }
    }

    @Test
    fun `given a repository with a wow character, i can update it`() {
        runBlocking {
            repository.withState(listOf(basicWowEntity))
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
            repository.withState(listOf(basicWowEntity))
            assertTrue(repository.update(1, basicLolEntityEnrichedRequest).isLeft())
        }
    }

    @Test
    fun `given a repository of wow characters, i can retrieve one by id`() {
        runBlocking {
            repository.withState(listOf(basicWowEntity, basicWowEntity2))
            assertEquals(basicWowEntity, repository.get(basicWowEntity.id))
            assertEquals(null, repository.get(9999))
        }
    }

    @Test
    fun `given a repository of wow characters, i can retrieve one by a character request`() {
        runBlocking {
            repository.withState(listOf(basicWowEntity))
            val request: EntityRequest =
                WowEntityRequest(basicWowEntity.name, basicWowEntity.region, basicWowEntity.realm)
            assertEquals(basicWowEntity, repository.get(request))
        }
    }

    @Test
    fun `given a repository of wow characters, i can retrieve one by an insert request`() {
        runBlocking {
            repository.withState(listOf(basicWowEntity))
            val request: InsertEntityRequest =
                WowEntityRequest(basicWowEntity.name, basicWowEntity.region, basicWowEntity.realm)
            assertEquals(basicWowEntity, repository.get(request))
        }
    }

    @Test
    fun `given a repository of wow characters, i can retrieve all of them`() {
        runBlocking {
            repository.withState(listOf(basicWowEntity, basicWowEntity2))
            assertEquals(listOf(basicWowEntity, basicWowEntity2), repository.getAll())
        }
    }

    @Test
    fun `get characters to sync should filter WOW characters by staleness`() {
        runBlocking {
            val wowEntities = (1..3).map { WowEntity(it.toLong(), it.toString(), it.toString(), it.toString(), null) }
            repository.withState(wowEntities)

            dataCacheRepository.withState(
                listOf(
                    DataCache(1, "", OffsetDateTime.now(), Game.WOW),
                    DataCache(2, "", OffsetDateTime.now().minusMinutes(31), Game.WOW)
                )
            )
            val res = repository.getOlderThan(30, Int.MAX_VALUE)

            assertEquals(setOf<Long>(2, 3), res.map { it.id }.toSet())
        }
    }
}

class WowEntityInMemoryRepositoryTest : WowEntityRepositoryTestCommon<WowEntityInMemoryRepository>() {
    override val dataCacheRepository = DataCacheInMemoryRepository()
    override val repository: WowEntityInMemoryRepository by lazy {
        WowEntityInMemoryRepository(dataCacheRepository) {
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
class WowEntityDatabaseRepositoryTest : WowEntityRepositoryTestCommon<WowEntityDatabaseRepository>() {
    private val embeddedPostgres = EmbeddedPostgres.start()

    private val flyway = Flyway
        .configure()
        .locations("db/migration/test")
        .dataSource(embeddedPostgres.postgresDatabase)
        .cleanDisabled(false)
        .load()

    override val repository = WowEntityDatabaseRepository(Database.connect(embeddedPostgres.postgresDatabase))
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
