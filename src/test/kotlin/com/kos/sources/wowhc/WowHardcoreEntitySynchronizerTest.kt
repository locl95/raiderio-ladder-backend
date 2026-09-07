package com.kos.sources.wowhc

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.clients.HttpError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.domain.GetWowCharacterResponse
import com.kos.clients.domain.HardcoreData
import com.kos.clients.domain.RaiderioWowHeadEmbeddedResponse
import com.kos.clients.domain.TalentLoadout
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.common.error.SyncProcessingError
import com.kos.datacache.BlizzardMockHelper.getCharacterEquipment
import com.kos.datacache.BlizzardMockHelper.getCharacterMedia
import com.kos.datacache.BlizzardMockHelper.getCharacterProfile
import com.kos.datacache.BlizzardMockHelper.getCharacterSpecializations
import com.kos.datacache.BlizzardMockHelper.getCharacterStats
import com.kos.datacache.BlizzardMockHelper.getItemMedia
import com.kos.datacache.BlizzardMockHelper.getWowCharacterResponse
import com.kos.datacache.BlizzardMockHelper.getWowItemResponse
import com.kos.datacache.TestHelper.wowHardcoreDataCache
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.EntitiesTestHelper.basicWowHardcoreEntity
import com.kos.entities.domain.WowEntity
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.sources.wowhc.staticdata.wowitems.WowItemsDatabaseRepository
import com.kos.views.Game
import com.kos.views.ViewEntity
import com.kos.views.ViewsTestHelper.basicSimpleWowHardcoreView
import com.kos.views.repository.ViewsInMemoryRepository
import com.kos.views.repository.ViewsState
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import kotlin.test.Test

class WowHardcoreEntitySynchronizerTest {
    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val blizzardClient = mock(BlizzardClient::class.java)
    private val wowItemsDatabaseRepository = mock(WowItemsDatabaseRepository::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `the wow hardcore cache service retrieves a dead character and this character is skipped`() {
        runBlocking {
            val dataCacheRepository = DataCacheInMemoryRepository().withState(
                listOf(
                    wowHardcoreDataCache.copy(
                        data = wowHardcoreDataCache.data.replace(
                            """"isDead": false""",
                            """"isDead": true"""
                        )
                    )
                )
            )

            val wowHardcoreEntityCacheService = WowHardcoreEntitySynchronizer(
                dataCacheRepository,
                EntitiesInMemoryRepository(),
                raiderIoClient,
                blizzardClient,
                wowItemsDatabaseRepository,

                )

            wowHardcoreEntityCacheService.synchronize(
                listOf(
                    basicWowHardcoreEntity
                )
            )

            dataCacheRepository.get(basicWowEntity.id).maxByOrNull { it.inserted }?.let {
                val expectedHardcoreData = json.decodeFromString<HardcoreData>(it.data)
                assertTrue(expectedHardcoreData.isDead)
            }
            assertEquals(1, dataCacheRepository.state().size)
            verify(blizzardClient, times(0)).getClassicProfile(
                basicWowHardcoreEntity.region,
                basicWowHardcoreEntity.realm,
                basicWowHardcoreEntity.name
            )
        }
    }


    @Test
    fun `the wow hardcore cache service deletes the wow hardcore entity if not found neither in api or data cache repository`() {
        runBlocking {

            `when`(
                blizzardClient.getClassicProfile(
                    basicWowHardcoreEntity.region,
                    basicWowHardcoreEntity.realm,
                    basicWowHardcoreEntity.name
                )
            ).thenReturn(
                Either.Left(
                    HttpError(404, "not found")
                )
            )

            val dataCacheRepository = DataCacheInMemoryRepository().withState(listOf())
            val viewsRepository = ViewsInMemoryRepository()
                .withState(
                    ViewsState(
                        listOf(basicSimpleWowHardcoreView.copy(entitiesIds = listOf(1))),
                        basicSimpleWowHardcoreView.entitiesIds.map {
                            ViewEntity(
                                it,
                                basicSimpleWowHardcoreView.id,
                                "alias"
                            )
                        })
                )
            val entitiesRepository = EntitiesInMemoryRepository(dataCacheRepository, viewsRepository)
                .withState(
                    EntitiesState(
                        listOf(),
                        listOf(basicWowHardcoreEntity, basicWowHardcoreEntity.copy(id = 2, blizzardId = 123)),
                        listOf()
                    )
                )

            val cacheResult = WowHardcoreEntitySynchronizer(
                dataCacheRepository,
                entitiesRepository,
                raiderIoClient,
                blizzardClient,
                wowItemsDatabaseRepository,

                ).synchronize(
                listOf(
                    basicWowHardcoreEntity
                )
            )

            cacheResult.any { it is SyncProcessingError }
            assertNull(entitiesRepository.get(1, Game.WOW_HC))
        }
    }

    @Test
    fun `the wow hardcore cache service marks a character as dead if not found in blizzard api`() {
        runBlocking {

            `when`(
                blizzardClient.getClassicProfile(
                    basicWowHardcoreEntity.region,
                    basicWowHardcoreEntity.realm,
                    basicWowHardcoreEntity.name
                )
            ).thenReturn(
                Either.Left(
                    HttpError(404, "not found")
                )
            )

            val dataCacheRepository = DataCacheInMemoryRepository().withState(listOf(wowHardcoreDataCache))
            WowHardcoreEntitySynchronizer(
                dataCacheRepository,
                EntitiesInMemoryRepository(),
                raiderIoClient,
                blizzardClient,
                wowItemsDatabaseRepository,

                ).synchronize(
                listOf(
                    basicWowHardcoreEntity
                )
            )
            dataCacheRepository.get(basicWowHardcoreEntity.id).maxByOrNull { it.inserted }?.let {
                val expectedHardcoreData = json.decodeFromString<HardcoreData>(it.data)
                assertTrue(expectedHardcoreData.isDead)
            }
            assertEquals(2, dataCacheRepository.state().size)
        }
    }

    @Test
    fun `the wow hardcore cache service marks a character as dead when it is found but with different blizzard id`() {
        runBlocking {

            `when`(
                blizzardClient.getClassicProfile(
                    basicWowHardcoreEntity.region,
                    basicWowHardcoreEntity.realm,
                    basicWowHardcoreEntity.name
                )
            ).thenReturn(Either.Right(getWowCharacterResponse))

            val dataCacheRepository = DataCacheInMemoryRepository().withState(
                listOf(
                    wowHardcoreDataCache
                )
            )

            WowHardcoreEntitySynchronizer(
                dataCacheRepository,
                EntitiesInMemoryRepository(),
                raiderIoClient,
                blizzardClient,
                wowItemsDatabaseRepository,

                ).synchronize(
                listOf(
                    basicWowHardcoreEntity
                )
            )

            dataCacheRepository.get(basicWowHardcoreEntity.id).maxByOrNull { it.inserted }?.let {
                val expectedHardcoreData = json.decodeFromString<HardcoreData>(it.data)
                assertTrue(expectedHardcoreData.isDead)
            }
            assertEquals(2, dataCacheRepository.state().size)
        }
    }

    @Test
    fun `the wow hardcore cache service inserts a new cache entry when there is no recent data and character is found in blizzard api`() {
        runBlocking {
            stubSuccessfulBlizzardSync(basicWowHardcoreEntity, Either.Right(getWowCharacterResponse.copy(id = 12345)))

            val dataCacheRepository = DataCacheInMemoryRepository().withState(
                listOf()
            )

            WowHardcoreEntitySynchronizer(
                dataCacheRepository,
                EntitiesInMemoryRepository(),
                raiderIoClient,
                blizzardClient,
                wowItemsDatabaseRepository,

                ).synchronize(
                listOf(
                    basicWowHardcoreEntity
                )
            )

            dataCacheRepository.get(basicWowHardcoreEntity.id).maxByOrNull { it.inserted }?.let {
                val expectedHardcoreData = json.decodeFromString<HardcoreData>(it.data)
                assertFalse(expectedHardcoreData.isDead)
            }
            assertEquals(1, dataCacheRepository.state().size)
        }
    }

    private suspend fun stubSuccessfulBlizzardSync(
        entity: WowEntity,
        characterProfile: Either<ClientError, GetWowCharacterResponse> = getCharacterProfile(entity)
    ) {
        `when`(blizzardClient.getClassicProfile(entity.region, entity.realm, entity.name))
            .thenReturn(characterProfile)
        `when`(blizzardClient.getCharacterMedia(entity.region, entity.realm, entity.name))
            .thenReturn(getCharacterMedia(entity))
        `when`(blizzardClient.getCharacterEquipment(entity.region, entity.realm, entity.name))
            .thenReturn(getCharacterEquipment())
        `when`(blizzardClient.getCharacterStats(entity.region, entity.realm, entity.name))
            .thenReturn(getCharacterStats())
        `when`(blizzardClient.getCharacterSpecializations(entity.region, entity.realm, entity.name))
            .thenReturn(getCharacterSpecializations())
        `when`(blizzardClient.getItemMedia(entity.region, 18421))
            .thenReturn(getItemMedia())
        `when`(blizzardClient.getItem(entity.region, 18421))
            .thenReturn(getWowItemResponse())
        `when`(raiderIoClient.wowheadEmbeddedCalculator(entity.region, entity.realm, entity.name))
            .thenReturn(Either.Right(RaiderioWowHeadEmbeddedResponse(TalentLoadout("030030303-02020202-"))))
    }
}