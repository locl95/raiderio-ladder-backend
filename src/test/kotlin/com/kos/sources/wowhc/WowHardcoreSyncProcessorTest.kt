package com.kos.sources.wowhc

import arrow.core.Either
import com.kos.clients.domain.RaiderioWowHeadEmbeddedResponse
import com.kos.clients.domain.TalentLoadout
import com.kos.datacache.BlizzardMockHelper
import com.kos.entities.EntitiesTestHelper
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.eventsourcing.events.ViewCreatedEventEvent
import com.kos.eventsourcing.events.ViewEditedEventEvent
import com.kos.eventsourcing.events.ViewPatchedEventEvent
import com.kos.eventsourcing.events.ViewToBeCreatedEvent
import com.kos.sources.SyncGameCharactersTestCommon
import com.kos.views.Game
import com.kos.views.ViewsTestHelper
import com.kos.views.ViewsTestHelper.owner
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.`when`
import kotlin.test.Test

class WowHardcoreSyncProcessorTest : SyncGameCharactersTestCommon() {

    @Test
    fun `syncWowHcCharactersProcessor calls cache on VIEW_CREATED with WOW_HC game`() = runBlocking {
        stubWowHcEntitySync()

        val (charactersService, dataCacheRepository) = createService()

        val eventWithVersion = createEventWithVersion(
            ViewCreatedEventEvent(
                ViewsTestHelper.id,
                ViewsTestHelper.name,
                ViewsTestHelper.owner,
                listOf(EntitiesTestHelper.basicWowEntity.id),
                true,
                Game.WOW_HC,
                ViewsTestHelper.featured,
                null
            ), Game.WOW_HC
        )

        val wowHardcoreEntityCacheService = WowHardcoreEntitySynchronizer(
            dataCacheRepository,
            entitiesRepository = EntitiesInMemoryRepository(),
            raiderIoClient,
            blizzardClient,
            blizzardDatabaseClient,

            )
        val spied = spyk(wowHardcoreEntityCacheService)
        assertWowHardcoreCacheInvocation(

            EntitiesTestHelper.basicWowEntity,
            eventWithVersion,
            charactersService,
            dataCacheRepository,
            spied,
            true,
            1
        )
    }

    @Test
    fun `syncWowHcCharactersProcessor calls cache on VIEW_EDITED with WOW game`() = runBlocking {
        stubWowHcEntitySync()

        val (charactersService, dataCacheRepository) = createService()

        val eventWithVersion = createEventWithVersion(
            ViewEditedEventEvent(
                ViewsTestHelper.id,
                ViewsTestHelper.name,
                listOf(EntitiesTestHelper.basicWowEntity.id),
                true,
                Game.WOW_HC,
                ViewsTestHelper.featured
            ), Game.WOW_HC
        )

        val wowHardcoreEntityCacheService = WowHardcoreEntitySynchronizer(
            dataCacheRepository,
            entitiesRepository = EntitiesInMemoryRepository(),
            raiderIoClient,
            blizzardClient,
            blizzardDatabaseClient,

            )
        val spied = spyk(wowHardcoreEntityCacheService)
        assertWowHardcoreCacheInvocation(

            EntitiesTestHelper.basicWowEntity,
            eventWithVersion,
            charactersService,
            dataCacheRepository,
            spied,
            true,
            1
        )

    }

    @Test
    fun `syncWowHcCharactersProcessor calls cache on VIEW_PATCHED with WOW game`() = runBlocking {
        stubWowHcEntitySync()

        val (charactersService, dataCacheRepository) = createService()

        val eventWithVersion = createEventWithVersion(
            ViewPatchedEventEvent(
                ViewsTestHelper.id,
                ViewsTestHelper.name,
                listOf(EntitiesTestHelper.basicWowEntity.id),
                true,
                Game.WOW_HC,
                ViewsTestHelper.featured
            ), Game.WOW_HC
        )


        val wowHardcoreEntityCacheService = WowHardcoreEntitySynchronizer(
            dataCacheRepository,
            entitiesRepository = EntitiesInMemoryRepository(),
            raiderIoClient,
            blizzardClient,
            blizzardDatabaseClient,

            )
        val spied = spyk(wowHardcoreEntityCacheService)
        assertWowHardcoreCacheInvocation(

            EntitiesTestHelper.basicWowEntity,
            eventWithVersion,
            charactersService,
            dataCacheRepository,
            spied,
            true,
            1
        )
    }

    @Test
    fun `should ignore not related events`() {
        runBlocking {
            val (charactersService, dataCacheRepository) = createService()

            val eventWithVersion = createEventWithVersion(
                ViewToBeCreatedEvent(
                    ViewsTestHelper.id,
                    ViewsTestHelper.name,
                    false,
                    listOf(EntitiesTestHelper.basicWowRequest),
                    Game.WOW_HC,
                    owner,
                    ViewsTestHelper.featured,
                    null
                ), Game.WOW_HC
            )


            val wowHardcoreEntityCacheService = WowHardcoreEntitySynchronizer(
                dataCacheRepository,
                entitiesRepository = EntitiesInMemoryRepository(),
                raiderIoClient,
                blizzardClient,
                blizzardDatabaseClient,

                )
            val spied = spyk(wowHardcoreEntityCacheService)
            assertWowHardcoreCacheInvocation(

                EntitiesTestHelper.basicWowEntity,
                eventWithVersion,
                charactersService,
                dataCacheRepository,
                spied,
                false,
                0
            )
        }
    }

    private suspend fun stubWowHcEntitySync() {
        val entity = EntitiesTestHelper.basicWowEntity
        `when`(blizzardClient.getClassicProfile(entity.region, entity.realm, entity.name))
            .thenReturn(BlizzardMockHelper.getCharacterProfile(entity))
        `when`(blizzardClient.getCharacterMedia(entity.region, entity.realm, entity.name))
            .thenReturn(BlizzardMockHelper.getCharacterMedia(entity))
        `when`(blizzardClient.getCharacterEquipment(entity.region, entity.realm, entity.name))
            .thenReturn(BlizzardMockHelper.getCharacterEquipment())
        `when`(blizzardClient.getCharacterStats(entity.region, entity.realm, entity.name))
            .thenReturn(BlizzardMockHelper.getCharacterStats())
        `when`(blizzardClient.getCharacterSpecializations(entity.region, entity.realm, entity.name))
            .thenReturn(BlizzardMockHelper.getCharacterSpecializations())
        `when`(blizzardClient.getItemMedia(entity.region, 18421))
            .thenReturn(BlizzardMockHelper.getItemMedia())
        `when`(blizzardClient.getItem(entity.region, 18421))
            .thenReturn(BlizzardMockHelper.getWowItemResponse())
        `when`(raiderIoClient.wowheadEmbeddedCalculator(entity.region, entity.realm, entity.name))
            .thenReturn(Either.Right(RaiderioWowHeadEmbeddedResponse(TalentLoadout("030030303-02020202-"))))
    }
}