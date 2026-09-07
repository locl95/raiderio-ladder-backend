package com.kos.views

import com.kos.activities.Activities
import com.kos.assertTrue
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.common.error.CantFeatureView
import com.kos.common.error.NotEnoughPermissions
import com.kos.common.error.NotFound
import com.kos.common.error.TooMuchViews
import com.kos.common.getLeftOrNull
import com.kos.credentials.CredentialsService
import com.kos.credentials.CredentialsTestHelper.basicCredentials
import com.kos.credentials.CredentialsTestHelper.emptyCredentialsState
import com.kos.credentials.repository.CredentialsInMemoryRepository
import com.kos.credentials.repository.CredentialsRepositoryState
import com.kos.datacache.BlizzardMockHelper
import com.kos.datacache.DataCache
import com.kos.datacache.DataCacheService
import com.kos.datacache.RaiderIoMockHelper
import com.kos.datacache.RaiderIoMockHelper.raiderIoData
import com.kos.datacache.RaiderIoMockHelper.raiderIoDataString
import com.kos.datacache.RaiderIoMockHelper.raiderioCachedData
import com.kos.datacache.RiotMockHelper.riotData
import com.kos.datacache.TestHelper.lolDataCache
import com.kos.datacache.TestHelper.wowDataCache
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesService
import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.EntitiesTestHelper.basicWowRequest2
import com.kos.entities.EntitiesTestHelper.emptyEntitiesState
import com.kos.entities.EntitiesTestHelper.lolEntityRequest
import com.kos.entities.EntityResolverProvider
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.repository.wowguilds.WowGuildsInMemoryRepository
import com.kos.eventsourcing.events.EventType
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.roles.Role
import com.kos.roles.repository.RolesActivitiesInMemoryRepository
import com.kos.sources.lol.LolEntityResolver
import com.kos.sources.lol.LolEntityUpdater
import com.kos.sources.wow.WowEntityResolver
import com.kos.sources.wow.WowGuildUpdater
import com.kos.sources.wowhc.WowHardcoreEntityResolver
import com.kos.sources.wowhc.WowHardcoreGuildUpdater
import com.kos.views.ViewsTestHelper.basicSimpleGameViews
import com.kos.views.ViewsTestHelper.basicSimpleLolView
import com.kos.views.ViewsTestHelper.basicSimpleWowView
import com.kos.views.ViewsTestHelper.owner
import com.kos.views.repository.ViewsInMemoryRepository
import com.kos.views.repository.ViewsState
import io.mockk.InternalPlatformDsl.toStr
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import kotlin.test.*

//TODO: Behaviour of get cached data

class ViewsControllerTest {
    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val riotClient = mock(RiotClient::class.java)
    private val blizzardClient = mock(BlizzardClient::class.java)
    private val viewsRepository = ViewsInMemoryRepository()
    private val entitiesRepository = EntitiesInMemoryRepository()
    private val dataCacheRepository = DataCacheInMemoryRepository()
    private val credentialsRepository = CredentialsInMemoryRepository()
    private val rolesActivitiesRepository = RolesActivitiesInMemoryRepository()
    private val eventStore = EventStoreInMemory()

    private suspend fun createController(
        credentialsState: CredentialsRepositoryState,
        viewsState: ViewsState,
        entitiesState: EntitiesState,
        dataCacheState: List<DataCache>,
    ): ViewsController {
        val viewsRepositoryWithState = viewsRepository.withState(viewsState)
        val charactersRepositoryWithState = entitiesRepository.withState(entitiesState)
        val dataCacheRepositoryWithState = dataCacheRepository.withState(dataCacheState)
        val credentialsRepositoryWithState = credentialsRepository.withState(credentialsState)

        val dataCacheService =
            DataCacheService(
                dataCacheRepositoryWithState,
                charactersRepositoryWithState,
                eventStore
            )

        val wowGuildsRepository = WowGuildsInMemoryRepository()

        val wowResolver = WowEntityResolver(entitiesRepository, raiderIoClient, blizzardClient)
        val wowHardcoreResolver = WowHardcoreEntityResolver(entitiesRepository, blizzardClient)
        val lolResolver = LolEntityResolver(entitiesRepository, riotClient)

        val entitiesResolver = EntityResolverProvider(
            wowResolver = wowResolver,
            wowHardcoreResolver = wowHardcoreResolver,
            lolResolver = lolResolver
        )

        val lolUpdater = LolEntityUpdater(riotClient, entitiesRepository)
        val wowHardcoreGuildUpdater =
            WowHardcoreGuildUpdater(wowHardcoreResolver, entitiesRepository, viewsRepositoryWithState)
        val wowGuildUpdater = WowGuildUpdater(wowResolver, entitiesRepository, viewsRepositoryWithState)

        val entitiesService = EntitiesService(
            entitiesRepository,
            wowGuildsRepository,
            entitiesResolver,
            lolUpdater,
            wowHardcoreGuildUpdater,
            wowGuildUpdater
        )


        val credentialsService = CredentialsService(credentialsRepositoryWithState)
        val viewsService = ViewsService(
            viewsRepositoryWithState,
            entitiesService,
            dataCacheService,
            credentialsService,
            eventStore
        )

        return ViewsController(viewsService)
    }


    @BeforeTest
    fun beforeEach() {
        viewsRepository.clear()
        entitiesRepository.clear()
        dataCacheRepository.clear()
        credentialsRepository.clear()
        rolesActivitiesRepository.clear()
    }

    @Test
    fun `i can get views returns only one view since the limit is 1`() {
        runBlocking {
            val limit = 1

            val controller = createController(
                emptyCredentialsState,
                ViewsState(basicSimpleGameViews, listOf()),
                emptyEntitiesState,
                listOf()
            )
            assertEquals(
                limit,
                controller.getViews(
                    "owner",
                    setOf(Activities.getAnyViews),
                    GetViewsQuery(Game.WOW, true, null, limit, false)
                )
                    .getOrNull()?.second?.size
            )
        }
    }

    @Test
    fun `i can get views returns empty since given page and limit`() {
        runBlocking {
            val limit = 10
            val page = 2

            val controller = createController(
                emptyCredentialsState,
                ViewsState(basicSimpleGameViews, listOf()),
                emptyEntitiesState,
                listOf()
            )
            assertEquals(
                0,
                controller.getViews(
                    "owner",
                    setOf(Activities.getAnyViews),
                    GetViewsQuery(Game.WOW, true, page, limit, false)
                )
                    .getOrNull()?.second?.size
            )
        }
    }

    @Test
    fun `i can get views returns only wow featured views`() {
        runBlocking {
            val featuredView = basicSimpleWowView.copy(featured = true)

            val controller = createController(
                emptyCredentialsState,
                ViewsState(listOf(basicSimpleWowView, featuredView), listOf()),
                emptyEntitiesState,
                listOf()
            )
            assertEquals(
                listOf(featuredView),
                controller.getViews(
                    "owner",
                    setOf(Activities.getAnyViews),
                    GetViewsQuery(Game.WOW, true, null, null, false)
                ).getOrNull()?.second
            )
        }
    }

    @Test
    fun `i can get views returns featured views with metadata`() {
        runBlocking {
            val views = listOf(basicSimpleWowView, basicSimpleWowView.copy(featured = true))

            val controller = createController(
                emptyCredentialsState,
                ViewsState(views, listOf()),
                emptyEntitiesState,
                listOf()
            )

            val featuredViews =
                controller.getViews(
                    "owner",
                    setOf(Activities.getAnyViews),
                    GetViewsQuery(Game.WOW, true, null, null, true)
                ).getOrNull()

            assertEquals(
                views.count { it.game == Game.WOW && it.featured },
                featuredViews?.first?.totalCount
            )
        }
    }

    @Test
    fun `i can get views returns only owner views`() {
        runBlocking {
            val controller = createController(
                emptyCredentialsState,
                ViewsState(listOf(basicSimpleWowView, basicSimpleWowView.copy(owner = "not-owner")), listOf()),
                emptyEntitiesState,
                listOf()
            )
            assertEquals(
                listOf(basicSimpleWowView),
                controller.getViews(
                    "owner",
                    setOf(Activities.getOwnViews),
                    GetViewsQuery(null, false, null, null, false)
                ).getOrNull()?.second
            )
        }
    }

    @Test
    fun `i can get own views paginated and with metadata`() {
        runBlocking {
            val myViews = (1..3).map { basicSimpleWowView.copy(id = "own-$it") }
            val controller = createController(
                emptyCredentialsState,
                ViewsState(myViews, listOf()),
                emptyEntitiesState,
                listOf()
            )

            val result = controller.getViews(
                "owner",
                setOf(Activities.getOwnViews),
                GetViewsQuery(null, false, 1, 2, true)
            ).getOrNull()

            assertEquals(myViews.take(2), result?.second)
            assertEquals(3, result?.first?.totalCount)
        }
    }

    @Test
    fun `i can get views returns all views if perms are given`() {
        runBlocking {
            val notOwnerView = basicSimpleWowView.copy(owner = "not-owner")
            val controller = createController(
                emptyCredentialsState,
                ViewsState(listOf(basicSimpleWowView, notOwnerView), listOf()),
                emptyEntitiesState,
                listOf()
            )
            assertEquals(
                listOf(basicSimpleWowView, notOwnerView),
                controller.getViews(
                    "owner",
                    setOf(Activities.getAnyViews),
                    GetViewsQuery(null, false, null, null, false)
                ).getOrNull()?.second
            )
        }
    }

    @Test
    fun `i can get view returns view only if i own it`() {
        runBlocking {
            val notOwnerView = basicSimpleWowView.copy(owner = "not-owner", id = "2")
            val controller = createController(
                emptyCredentialsState,
                ViewsState(listOf(basicSimpleWowView, notOwnerView), listOf()),
                emptyEntitiesState,
                listOf()
            )
            assertEquals(
                basicSimpleWowView,
                controller.getView("owner", basicSimpleWowView.id, setOf(Activities.getOwnView)).getOrNull()?.toSimple()
            )
            assertEquals(
                NotEnoughPermissions("owner"),
                controller.getView("owner", notOwnerView.id, setOf(Activities.getOwnView)).getLeftOrNull()
            )
        }
    }

    @Test
    fun `i can't get view that does not exist`() {
        runBlocking {
            val controller = createController(
                emptyCredentialsState,
                ViewsState(listOf(), listOf()),
                emptyEntitiesState,
                listOf()
            )
            assertEquals(
                NotFound(basicSimpleWowView.id),
                controller.getView("owner", basicSimpleWowView.id, setOf(Activities.getOwnView)).getLeftOrNull()
            )
        }
    }

    @Test
    fun `i can create views`() {
        runBlocking {
            val user = "owner"

            val controller = createController(
                CredentialsRepositoryState(
                    listOf(basicCredentials.copy(userName = user)),
                    mapOf(owner to listOf(Role.USER))
                ),
                ViewsState(listOf(), listOf()),
                emptyEntitiesState,
                listOf()
            )

            controller.createView(
                user,
                ViewRequest(basicSimpleWowView.name, true, listOf(), Game.WOW, false),
                setOf(Activities.createViews)
            ).onRight {
                assertTrue(it.id.isNotEmpty())
                assertEquals(EventType.VIEW_TO_BE_CREATED, it.type)
            }
                .onLeft { fail(it.toStr()) }
        }
    }

    @Test
    fun `i can't create views`() {
        runBlocking {
            val user = "owner"

            val controller = createController(
                CredentialsRepositoryState(
                    listOf(basicCredentials.copy(userName = user)),
                    mapOf(owner to listOf(Role.USER))
                ),
                ViewsState(listOf(), listOf()),
                emptyEntitiesState,
                listOf()
            )
            controller.createView(
                user,
                ViewRequest(basicSimpleWowView.name, true, listOf(), Game.WOW, true),
                setOf(Activities.createViews)
            )
                .onRight {
                    fail("function behaved correctly when we were expecting failure")
                }
                .onLeft {
                    assertTrue(it is CantFeatureView)
                }
        }
    }

    @Test
    fun `i can't create too much views`() {
        runBlocking {
            val controller = createController(
                CredentialsRepositoryState(
                    listOf(basicCredentials.copy(userName = "owner")),
                    mapOf(owner to listOf(Role.USER))
                ),
                ViewsState(listOf(basicSimpleWowView, basicSimpleWowView), listOf()),
                emptyEntitiesState,
                listOf()
            )

            assertIs<TooMuchViews>(
                controller.createView(
                    "owner",
                    ViewRequest(basicSimpleWowView.name, true, listOf(), Game.WOW, false),
                    setOf(Activities.createViews)
                ).getLeftOrNull()
            )
        }
    }

    @Test
    fun `i can get wow view data`() {
        runBlocking {
            val viewWithEntities = basicSimpleWowView.copy(entitiesIds = listOf(1))
            val controller = createController(
                emptyCredentialsState,
                ViewsState(
                    listOf(viewWithEntities),
                    viewWithEntities.entitiesIds.map { ViewEntity(it, viewWithEntities.id, "alias") }),
                EntitiesState(listOf(basicWowEntity), listOf(), listOf()),
                listOf(DataCache(basicWowEntity.id, raiderIoDataString, OffsetDateTime.now(), Game.WOW))
            )

            `when`(raiderIoClient.get(basicWowEntity)).thenReturn(RaiderIoMockHelper.get(basicWowEntity))

            controller.getViewData("owner", basicSimpleWowView.id, setOf(Activities.getViewData))
                .onRight {
                    assertEquals(ViewData(basicSimpleWowView.name, raiderIoData), it)
                }
                .onLeft { fail(it.toStr()) }
        }
    }

    @Test
    fun `i can get lol view data`() {
        runBlocking {
            val viewWithEntities = basicSimpleLolView.copy(entitiesIds = listOf(2))
            val controller = createController(
                emptyCredentialsState,
                ViewsState(
                    listOf(viewWithEntities),
                    viewWithEntities.entitiesIds.map { ViewEntity(it, viewWithEntities.id, "alias") }),
                EntitiesState(listOf(), listOf(), listOf(basicLolEntity.copy(id = 2))),
                listOf(lolDataCache)
            )

            controller.getViewData("owner", basicSimpleLolView.id, setOf(Activities.getViewData))
                .onRight {
                    assertEquals(ViewData(basicSimpleLolView.name, listOf(riotData)), it)
                }
                .onLeft { fail(it.toStr()) }
        }
    }

    @Test
    fun `i can get wow cached data`() {
        runBlocking {

            val controller = createController(
                emptyCredentialsState,
                ViewsState(listOf(basicSimpleWowView.copy(entitiesIds = listOf(1))), listOf()),
                EntitiesState(listOf(basicWowEntity), listOf(), listOf()),
                listOf(wowDataCache)
            )

            controller.getViewCachedData("owner", basicSimpleWowView.id, setOf(Activities.getViewCachedData))
                .onRight {
                    assertEquals(ViewData(basicSimpleWowView.name, listOf(raiderioCachedData)), it)
                }
                .onLeft { fail(it.toStr()) }
        }
    }

    @Test
    fun `i can get lol cached data`() {
        runBlocking {
            val credentialsState = CredentialsRepositoryState(
                listOf(basicCredentials.copy(userName = "owner")),
                mapOf(Pair("owner", listOf(Role.USER)))
            )

            val controller = createController(
                credentialsState,
                ViewsState(listOf(basicSimpleLolView.copy(entitiesIds = listOf(2))), listOf()),
                EntitiesState(listOf(), listOf(), listOf(basicLolEntity)),
                listOf(lolDataCache)
            )

            controller.getViewCachedData("owner", basicSimpleLolView.id, setOf(Activities.getViewCachedData))
                .onRight {
                    assertEquals(ViewData(basicSimpleLolView.name, listOf(riotData)), it)
                }
                .onLeft { fail(it.toStr()) }
        }
    }

    @Test
    fun `i can edit wow data`() {
        runBlocking {
            val credentialsState = CredentialsRepositoryState(
                listOf(basicCredentials.copy(userName = "owner")),
                mapOf(Pair("owner", listOf(Role.USER)))
            )

            val controller = createController(
                credentialsState,
                ViewsState(listOf(basicSimpleWowView), listOf()),
                EntitiesState(listOf(basicWowEntity), listOf(), listOf(basicLolEntity)),
                listOf(lolDataCache)
            )

            `when`(
                blizzardClient.getRetailProfile(basicWowRequest2.region, basicWowRequest2.realm, basicWowRequest2.name)
            ).thenReturn(BlizzardMockHelper.getCharacterProfile(basicWowRequest2))

            val viewRequest = ViewRequest("new-name", false, entities = listOf(basicWowRequest2), Game.WOW, false)

            controller.editView("owner", viewRequest, basicSimpleWowView.id, setOf(Activities.editAnyView))
                .onRight {
                    assertTrue(it.id.isNotEmpty())
                    assertEquals(EventType.VIEW_TO_BE_EDITED, it.type)
                }
                .onLeft { fail(it.toStr()) }
        }
    }

    @Test
    fun `i can't edit wow data`() {
        runBlocking {
            val credentialsState = CredentialsRepositoryState(
                listOf(basicCredentials.copy(userName = "owner")),
                mapOf(Pair("owner", listOf(Role.USER)))
            )

            val controller = createController(
                credentialsState,
                ViewsState(listOf(basicSimpleWowView), listOf()),
                EntitiesState(listOf(basicWowEntity), listOf(), listOf(basicLolEntity)),
                listOf(lolDataCache)
            )

            `when`(
                blizzardClient.getRetailProfile(basicWowRequest2.region, basicWowRequest2.realm, basicWowRequest2.name)
            ).thenReturn(BlizzardMockHelper.getCharacterProfile(basicWowRequest2))

            val viewRequest = ViewRequest("new-name", false, entities = listOf(basicWowRequest2), Game.WOW, true)

            controller.editView("owner", viewRequest, basicSimpleWowView.id, setOf(Activities.editAnyView))
                .onRight {
                    fail("function behaved correctly when we were expecting failure")
                }
                .onLeft {
                    assertTrue(it is CantFeatureView)
                }
        }
    }

    @Test
    fun `i can patch a view`() {
        runBlocking {
            val user = "owner"

            val credentialsState = CredentialsRepositoryState(
                listOf(basicCredentials.copy(userName = user)),
                mapOf(Pair("owner", listOf(Role.USER)))
            )

            val controller = createController(
                credentialsState,
                ViewsState(listOf(basicSimpleWowView), listOf()),
                EntitiesState(listOf(basicWowEntity), listOf(), listOf(basicLolEntity)),
                listOf(lolDataCache)
            )

            val viewPatchRequest =
                ViewPatchRequest("new-name", false, entities = listOf(lolEntityRequest), Game.LOL, null)

            controller.patchView(user, viewPatchRequest, basicSimpleLolView.id, setOf(Activities.editOwnView))
                .onRight {
                    assertTrue(it.id.isNotEmpty())
                    assertEquals(EventType.VIEW_TO_BE_PATCHED, it.type)
                }
                .onLeft { fail(it.toStr()) }
        }
    }

    @Test
    fun `i can't patch a view because user can't feature a view`() {
        runBlocking {
            val user = "owner"

            val credentialsState = CredentialsRepositoryState(
                listOf(basicCredentials.copy(userName = user)),
                mapOf(Pair("owner", listOf(Role.USER)))
            )

            val controller = createController(
                credentialsState,
                ViewsState(listOf(basicSimpleWowView), listOf()),
                EntitiesState(listOf(basicWowEntity), listOf(), listOf(basicLolEntity)),
                listOf(lolDataCache)
            )

            val viewPatchRequest =
                ViewPatchRequest("new-name", false, entities = listOf(lolEntityRequest), Game.LOL, true)

            controller.patchView(user, viewPatchRequest, basicSimpleLolView.id, setOf(Activities.editOwnView))
                .onRight {
                    fail("function behaved correctly when we were expecting failure")
                }
                .onLeft {
                    assertTrue(it is CantFeatureView)
                }
        }
    }

    @Test
    fun `i can patch a view because user can feature a view`() {
        runBlocking {
            val user = "owner"

            val credentialsState = CredentialsRepositoryState(
                listOf(basicCredentials.copy(userName = user)),
                mapOf(Pair("owner", listOf(Role.ADMIN)))
            )

            val controller = createController(
                credentialsState,
                ViewsState(listOf(basicSimpleWowView), listOf()),
                EntitiesState(listOf(basicWowEntity), listOf(), listOf(basicLolEntity)),
                listOf(lolDataCache)
            )

            val viewPatchRequest =
                ViewPatchRequest("new-name", false, entities = listOf(lolEntityRequest), Game.LOL, true)

            controller.patchView(
                user,
                viewPatchRequest,
                basicSimpleLolView.id,
                setOf(Activities.editOwnView, Activities.featureView)
            )
                .onRight {
                    assertTrue(it.id.isNotEmpty())
                    assertEquals(EventType.VIEW_TO_BE_PATCHED, it.type)
                }
                .onLeft { fail(it.toStr()) }
        }
    }
}