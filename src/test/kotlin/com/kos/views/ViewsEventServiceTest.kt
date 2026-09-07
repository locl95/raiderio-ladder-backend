package com.kos.views

import arrow.core.Either
import com.kos.clients.blizzard.BlizzardClient
import com.kos.datacache.BlizzardMockHelper
import com.kos.clients.domain.GetPUUIDResponse
import com.kos.clients.domain.GetSummonerResponse
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.entities.EntitiesService
import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.EntitiesTestHelper.basicLolEntity2
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.EntitiesTestHelper.basicWowEntity2
import com.kos.entities.EntitiesTestHelper.emptyEntitiesState
import com.kos.entities.EntityResolverProvider
import com.kos.entities.domain.GuildPayload
import com.kos.entities.domain.LolEntityRequest
import com.kos.entities.domain.WowEntityRequest
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.repository.wowguilds.WowGuildsInMemoryRepository
import com.kos.entities.repository.wowguilds.WowGuildsState
import com.kos.eventsourcing.events.*
import com.kos.eventsourcing.events.repository.EventStore
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.sources.lol.LolEntityResolver
import com.kos.sources.lol.LolEntityUpdater
import com.kos.sources.wow.WowEntityResolver
import com.kos.sources.wow.WowGuildUpdater
import com.kos.sources.wowhc.WowHardcoreEntityResolver
import com.kos.sources.wowhc.WowHardcoreGuildUpdater
import com.kos.views.ViewsTestHelper.basicSimpleLolView
import com.kos.views.ViewsTestHelper.basicSimpleWowView
import com.kos.views.ViewsTestHelper.id
import com.kos.views.ViewsTestHelper.name
import com.kos.views.ViewsTestHelper.owner
import com.kos.views.ViewsTestHelper.published
import com.kos.views.repository.ViewsInMemoryRepository
import com.kos.views.repository.ViewsState
import io.mockk.InternalPlatformDsl.toStr
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.mockito.Mockito.*
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class ViewsEventServiceTest {
    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val riotClient = mock(RiotClient::class.java)
    private val blizzardClient = mock(BlizzardClient::class.java)

    private val aggregateRoot = "/credentials/owner"

    @Nested
    inner class BehaviorOfCreateView {

        @Test
        fun `create view processing view to be created event stores an event`() {
            runBlocking {
                val (eventStore, viewsEventService) = createService(
                    ViewsState(listOf(), listOf()),
                    emptyEntitiesState
                )

                createViewFromEventAndAssert(
                    viewsEventService,
                    ViewToBeCreatedEvent(id, name, published, listOf(), Game.LOL, owner, false, null)
                )

                assertEventStoredCorrectly(
                    eventStore,
                    ViewCreatedEventEvent(id, name, owner, listOf(), published, Game.LOL, false, null)
                )
            }
        }

        @Test
        fun `creating a guild view for an already-tracked guild reuses its entities without calling blizzard or raiderio`() {
            runBlocking {
                val existingViewId = "existing-guild-view"
                val guildExtraArguments = WowExtraArguments(season = 0, guild = GuildArgs.RESOLVE)
                val guildRequest = WowEntityRequest("method", "eu", "twisting-nether")

                val existingView = SimpleView(
                    existingViewId, "Method roster", owner, false,
                    listOf(basicWowEntity.id), Game.WOW, false, guildExtraArguments
                )

                val (eventStore, viewsEventService) = createService(
                    ViewsState(
                        listOf(existingView),
                        listOf(ViewEntity(basicWowEntity.id, existingViewId, "alias"))
                    ),
                    EntitiesState(listOf(basicWowEntity), listOf(), listOf()),
                    wowGuildsState = listOf(
                        Triple(GuildPayload("method", "twisting-nether", "eu", 999L), existingViewId, Game.WOW)
                    )
                )

                createViewFromEventAndAssert(
                    viewsEventService,
                    ViewToBeCreatedEvent(
                        id, "Method roster copy", published, listOf(guildRequest), Game.WOW, owner, false, guildExtraArguments
                    )
                )

                assertEventStoredCorrectly(
                    eventStore,
                    ViewCreatedEventEvent(
                        id, "Method roster copy", owner, listOf(basicWowEntity.id), published, Game.WOW, false, guildExtraArguments
                    )
                )

                verifyNoInteractions(blizzardClient)
                verifyNoInteractions(raiderIoClient)
            }
        }

        private suspend fun createViewFromEventAndAssert(
            viewsEventService: ViewsEventService,
            viewToBeCreatedEvent: ViewToBeCreatedEvent
        ) {
            viewsEventService.createView(
                id,
                aggregateRoot,
                viewToBeCreatedEvent
            ).onRight {
                assertOperation(it, EventType.VIEW_CREATED)
            }.onLeft {
                fail()
            }
        }
    }

    @Nested
    inner class BehaviorOfEditView {

        @Test
        fun `editing a lol view processing view to be edited stores an event`() {
            runBlocking {
                val (eventStore, viewsEventService) = createService(
                    ViewsState(
                        listOf(basicSimpleLolView),
                        basicSimpleLolView.entitiesIds.map { ViewEntity(it, basicSimpleLolView.id, "alias") }),
                    emptyEntitiesState
                )

                val newName = "new-name"
                viewsEventService.editView(
                    id,
                    aggregateRoot,
                    ViewToBeEditedEvent(id, newName, published, listOf(), Game.LOL, false)
                ).onRight {
                    assertOperation(it, EventType.VIEW_EDITED)
                }.onLeft {
                    fail(it.toStr())
                }

                assertEventStoredCorrectly(
                    eventStore,
                    ViewEditedEventEvent(id, newName, listOf(), published, Game.LOL, false)
                )
            }
        }

        @Test
        fun `editing a view processing view to be edited, an event is stored with the actual characters of the view`() {
            runBlocking {
                val request1 = WowEntityRequest("a", "r", "r")
                val request2 = WowEntityRequest("b", "r", "r")
                val request3 = WowEntityRequest("c", "r", "r")
                val request4 = WowEntityRequest("d", "r", "r")

                `when`(blizzardClient.getRetailProfile(request1.region, request1.realm, request1.name))
                    .thenReturn(BlizzardMockHelper.getCharacterProfile(request1))
                `when`(blizzardClient.getRetailProfile(request2.region, request2.realm, request2.name))
                    .thenReturn(BlizzardMockHelper.getCharacterProfile(request2))
                `when`(blizzardClient.getRetailProfile(request3.region, request3.realm, request3.name))
                    .thenReturn(BlizzardMockHelper.getCharacterProfile(request3))
                `when`(blizzardClient.getRetailProfile(request4.region, request4.realm, request4.name))
                    .thenReturn(BlizzardMockHelper.getCharacterProfile(request4))

                val (eventStore, viewsEventService) = createService(
                    ViewsState(
                        listOf(basicSimpleWowView.copy(entitiesIds = listOf(1))),
                        basicSimpleWowView.entitiesIds.map { ViewEntity(it, basicSimpleWowView.id, "alias") }),
                    EntitiesState(
                        listOf(basicWowEntity, basicWowEntity2),
                        listOf(),
                        listOf()
                    )
                )

                viewsEventService.editView(
                    id,
                    aggregateRoot,
                    ViewToBeEditedEvent(
                        id,
                        name,
                        published,
                        listOf(request1, request2, request3, request4),
                        Game.WOW,
                        false
                    )
                ).onRight {
                    assertOperation(it, EventType.VIEW_EDITED)
                }.onLeft {
                    fail(it.toStr())
                }

                assertEventStoredCorrectly(
                    eventStore,
                    ViewEditedEventEvent(id, name, listOf(3, 4, 5, 6), published, Game.WOW, false)
                )
            }
        }

        @Test
        fun `editing a lol view processing view to be edited, an event is stored with the actual characters of the view`() {
            runBlocking {
                val charactersRequest = (3..6).map { LolEntityRequest(it.toString(), it.toString()) }

                val (eventStore, viewsEventService) = createService(
                    ViewsState(
                        listOf(basicSimpleLolView.copy(entitiesIds = listOf(1))),
                        basicSimpleLolView.entitiesIds.map { ViewEntity(it, basicSimpleLolView.id, "alias") }),
                    EntitiesState(
                        listOf(),
                        listOf(),
                        listOf(basicLolEntity, basicLolEntity2)
                    )
                )

                `when`(riotClient.getPUUIDByRiotId(anyString(), anyString())).thenAnswer { invocation ->
                    val name = invocation.getArgument<String>(0)
                    val tag = invocation.getArgument<String>(1)
                    Either.Right(GetPUUIDResponse(UUID.randomUUID().toString(), name, tag))
                }

                `when`(riotClient.getSummonerByPuuid(anyString())).thenAnswer { invocation ->
                    val puuid = invocation.getArgument<String>(0)
                    Either.Right(
                        GetSummonerResponse(
                            puuid,
                            10,
                            10L,
                            200
                        )
                    )
                }

                viewsEventService.editView(
                    id,
                    aggregateRoot,
                    ViewToBeEditedEvent(id, name, published, charactersRequest, Game.LOL, false)
                ).onRight {
                    assertOperation(it, EventType.VIEW_EDITED)
                }.onLeft {
                    fail(it.toStr())
                }

                assertEventStoredCorrectly(
                    eventStore,
                    ViewEditedEventEvent(id, name, listOf(3, 4, 5, 6), published, Game.LOL, false)
                )
            }
        }
    }

    @Nested
    inner class BehaviorOfPatchView {
        @Test
        fun `patching a lol view processing event stores an event`() {
            runBlocking {
                val charactersRequest = (3..6).map { LolEntityRequest(it.toString(), it.toString()) }

                val (eventStore, viewsEventService) = createService(
                    ViewsState(
                        listOf(basicSimpleLolView.copy(entitiesIds = listOf(1))),
                        basicSimpleLolView.entitiesIds.map { ViewEntity(it, basicSimpleLolView.id, "alias") }),
                    emptyEntitiesState
                )

                `when`(riotClient.getPUUIDByRiotId(anyString(), anyString())).thenAnswer { invocation ->
                    val name = invocation.getArgument<String>(0)
                    val tag = invocation.getArgument<String>(1)
                    Either.Right(GetPUUIDResponse(UUID.randomUUID().toString(), name, tag))
                }

                `when`(riotClient.getSummonerByPuuid(anyString())).thenAnswer { invocation ->
                    val puuid = invocation.getArgument<String>(0)
                    Either.Right(
                        GetSummonerResponse(
                            puuid,
                            10,
                            10L,
                            200
                        )
                    )
                }

                viewsEventService.patchView(
                    id,
                    aggregateRoot,
                    ViewToBePatchedEvent(id, null, null, charactersRequest, Game.LOL, false)
                ).onRight {
                    assertOperation(it, EventType.VIEW_PATCHED)
                }.onLeft {
                    fail(it.toStr())
                }

                assertEventStoredCorrectly(
                    eventStore,
                    ViewPatchedEventEvent(id, null, listOf(1, 2, 3, 4), null, Game.LOL, false)
                )
            }
        }

        @Test
        fun `patching a wow view processing event stores an event`() {
            runBlocking {

                val request1 = WowEntityRequest("a", "r", "r")
                val request2 = WowEntityRequest("b", "r", "r")
                val request3 = WowEntityRequest("c", "r", "r")
                val request4 = WowEntityRequest("d", "r", "r")

                `when`(blizzardClient.getRetailProfile(request1.region, request1.realm, request1.name))
                    .thenReturn(BlizzardMockHelper.getCharacterProfile(request1))
                `when`(blizzardClient.getRetailProfile(request2.region, request2.realm, request2.name))
                    .thenReturn(BlizzardMockHelper.getCharacterProfile(request2))
                `when`(blizzardClient.getRetailProfile(request3.region, request3.realm, request3.name))
                    .thenReturn(BlizzardMockHelper.getCharacterProfile(request3))
                `when`(blizzardClient.getRetailProfile(request4.region, request4.realm, request4.name))
                    .thenReturn(BlizzardMockHelper.getCharacterProfile(request4))

                val (eventStore, viewsEventService) = createService(
                    ViewsState(
                        listOf(basicSimpleWowView.copy(entitiesIds = listOf(1))),
                        basicSimpleWowView.entitiesIds.map { ViewEntity(it, basicSimpleWowView.id, "alias") }),
                    emptyEntitiesState
                )

                val charactersRequest = listOf(request1, request2, request3, request4)

                viewsEventService.patchView(
                    id,
                    aggregateRoot,
                    ViewToBePatchedEvent(id, null, null, charactersRequest, Game.WOW, false)
                ).onRight {
                    assertOperation(it, EventType.VIEW_PATCHED)
                }.onLeft {
                    fail(it.toStr())
                }

                assertEventStoredCorrectly(
                    eventStore,
                    ViewPatchedEventEvent(id, null, listOf(1, 2, 3, 4), null, Game.WOW, false)
                )
            }
        }
    }

    private suspend fun createService(
        viewsState: ViewsState,
        entitiesState: EntitiesState,
        wowGuildsState: List<Triple<GuildPayload, String, Game>> = listOf(),
    ): Pair<EventStore, ViewsEventService> {
        val viewsRepository = ViewsInMemoryRepository()
            .withState(viewsState)
        val entitiesRepository = EntitiesInMemoryRepository()
            .withState(entitiesState)
        val eventStore = EventStoreInMemory()

        val wowGuildsRepository = WowGuildsInMemoryRepository()
            .withState(WowGuildsState(wowGuildsState))

        val wowResolver = WowEntityResolver(entitiesRepository, raiderIoClient, blizzardClient)
        val wowHardcoreResolver = WowHardcoreEntityResolver(entitiesRepository, blizzardClient)
        val lolResolver = LolEntityResolver(entitiesRepository, riotClient)

        val entitiesResolver = EntityResolverProvider(
            wowResolver = wowResolver,
            wowHardcoreResolver = wowHardcoreResolver,
            lolResolver = lolResolver
        )

        val lolUpdater = LolEntityUpdater(riotClient, entitiesRepository)
        val wowHardcoreGuildUpdater = WowHardcoreGuildUpdater(wowHardcoreResolver, entitiesRepository, viewsRepository)
        val wowGuildUpdater = WowGuildUpdater(wowResolver, entitiesRepository, viewsRepository)

        val entitiesService = EntitiesService(
            entitiesRepository,
            wowGuildsRepository,
            entitiesResolver,
            lolUpdater,
            wowHardcoreGuildUpdater,
            wowGuildUpdater
        )

        val service = ViewsEventService(viewsRepository, entitiesService, eventStore)

        return Pair(eventStore, service)
    }

    private fun assertOperation(operation: Operation, expectedType: EventType) {
        assertTrue(operation.id.isNotEmpty())
        assertEquals(expectedType, operation.type)
    }

    private suspend fun assertEventStoredCorrectly(eventStore: EventStore, eventData: EventData) {
        val events = eventStore.getEvents(null).toList()
        assertEquals(1, events.size)
        val actual = events.first().event
        assertTrue(actual.operationId.isNotEmpty())
        assertEquals(aggregateRoot, actual.aggregateRoot)
        assertEquals(eventData, actual.eventData)
    }
}
