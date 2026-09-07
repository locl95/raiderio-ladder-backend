package com.kos.entities

import arrow.core.Either
import com.kos.clients.HttpError
import com.kos.clients.TimeoutError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.domain.GetPUUIDResponse
import com.kos.clients.domain.GetSummonerResponse
import com.kos.clients.domain.GetWowRosterResponse
import com.kos.clients.domain.WowGuildResponse
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.datacache.BlizzardMockHelper
import com.kos.datacache.BlizzardMockHelper.hardcoreRealm
import com.kos.datacache.BlizzardMockHelper.notHardcoreRealm
import com.kos.entities.EntitiesTestHelper.basicGetAccountResponse
import com.kos.entities.EntitiesTestHelper.basicGetPuuidResponse
import com.kos.entities.EntitiesTestHelper.basicGetSummonerResponse
import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.EntitiesTestHelper.basicLolEntity2
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.EntitiesTestHelper.basicWowHardcoreEntity
import com.kos.entities.EntitiesTestHelper.basicWowRequest
import com.kos.entities.EntitiesTestHelper.basicWowRequest2
import com.kos.entities.EntitiesTestHelper.emptyEntitiesState
import com.kos.entities.EntitiesTestHelper.gigaLolCharacterRequestList
import com.kos.entities.EntitiesTestHelper.gigaLolEntityList
import com.kos.entities.domain.*
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.repository.wowguilds.WowGuildsInMemoryRepository
import com.kos.sources.lol.LolEntityResolver
import com.kos.sources.lol.LolEntityUpdater
import com.kos.sources.wow.WowEntityResolver
import com.kos.sources.wow.WowGuildUpdater
import com.kos.sources.wowhc.WowHardcoreEntityResolver
import com.kos.sources.wowhc.WowHardcoreGuildUpdater
import com.kos.views.Game
import com.kos.views.repository.ViewsInMemoryRepository
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.*
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class EntitiesServiceTest {
    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val riotClient = mock(RiotClient::class.java)
    private val blizzardClient = mock(BlizzardClient::class.java)

    @Test
    fun `resolving two characters over an empty repository resolves both as new`() {
        runBlocking {
            val request1 =
                WowEntityRequest(basicWowEntity.name, basicWowEntity.region, basicWowEntity.realm)
            val request2 = WowEntityRequest("kakarøna", basicWowEntity.region, basicWowEntity.realm)

            `when`(blizzardClient.getRetailProfile(request1.region, request1.realm, request1.name)).thenReturn(
                BlizzardMockHelper.getCharacterProfile(request1)
            )
            `when`(blizzardClient.getRetailProfile(request2.region, request2.realm, request2.name)).thenReturn(
                BlizzardMockHelper.getCharacterProfile(request2)
            )


            val entitiesService = createService(emptyEntitiesState)

            val request = listOf(request1, request2)
            val expected = ResolvedEntities(
                entities = listOf(request1 to null, request2 to null),
                existing = listOf(),
                guild = null,
            )

            entitiesService.resolveEntities(request, Game.WOW)
                .onLeft { fail() }
                .onRight { res -> assertResolvedEntities(expected, res) }
        }
    }

    @Test
    fun `resolving two characters over an empty wow hardcore repository resolves both as new`() {
        runBlocking {
            val request1 =
                WowEntityRequest(basicWowEntity.name, basicWowEntity.region, basicWowEntity.realm)
            val request2 = WowEntityRequest("kakarøna", basicWowEntity.region, basicWowEntity.realm)

            `when`(blizzardClient.getClassicProfile(request1.region, request1.realm, request1.name)).thenReturn(
                BlizzardMockHelper.getCharacterProfile(request1)
            )
            `when`(blizzardClient.getClassicProfile(request2.region, request2.realm, request2.name)).thenReturn(
                BlizzardMockHelper.getCharacterProfile(request2)
            )
            `when`(blizzardClient.getRealm(request1.region, 5220)).thenReturn(Either.Right(hardcoreRealm))
            `when`(blizzardClient.getRealm(request2.region, 5220)).thenReturn(Either.Right(hardcoreRealm))

            val entitiesService = createService(emptyEntitiesState)

            val request = listOf(request1, request2)

            val expected = ResolvedEntities(
                entities = listOf(request1 to null, request2 to null),
                existing = listOf(),
                guild = null,
            )

            entitiesService.resolveEntities(request, Game.WOW_HC)
                .onLeft { fail() }
                .onRight { res -> assertResolvedEntities(expected, res) }
        }
    }

    @Test
    fun `resolving a character from a non hardcore realm does not get inserted over an empty wow hardcore repository`() {
        runBlocking {
            val request1 =
                WowEntityRequest(basicWowEntity.name, basicWowEntity.region, basicWowEntity.realm)

            `when`(blizzardClient.getClassicProfile(request1.region, request1.realm, request1.name)).thenReturn(
                BlizzardMockHelper.getCharacterProfile(request1)
            )
            `when`(blizzardClient.getRealm(request1.region, 5220)).thenReturn(Either.Right(notHardcoreRealm))

            val entitiesService = createService(emptyEntitiesState)

            val request = listOf(request1)
            val expected = ResolvedEntities(
                listOf(),
                listOf(),
                guild = null
            )

            entitiesService.resolveEntities(request, Game.WOW_HC)
                .onLeft { fail() }
                .onRight { res -> assertResolvedEntities(expected, res) }
        }
    }

    @Test
    fun `resolving a character that does not exist does not get inserted`() {
        runBlocking {
            val request1 =
                WowEntityRequest(basicWowEntity.name, basicWowEntity.region, basicWowEntity.realm)
            val request2 = WowEntityRequest("kakarøna", basicWowEntity.region, basicWowEntity.realm)

            `when`(blizzardClient.getRetailProfile(request1.region, request1.realm, request1.name)).thenReturn(
                BlizzardMockHelper.getCharacterProfile(request1)
            )
            `when`(
                blizzardClient.getRetailProfile(request2.region, request2.realm, request2.name)
            ).thenReturn(Either.Left(HttpError(404, null)))

            val entitiesService = createService(emptyEntitiesState)

            val request = listOf(request1, request2)
            val expected = ResolvedEntities(
                listOf(request1 to null),
                listOf(),
                guild = null
            )

            entitiesService.resolveEntities(request, Game.WOW)
                .onLeft { fail() }
                .onRight { res -> assertResolvedEntities(expected, res) }
        }
    }

    @Test
    fun `resolving a character with same blizzard id does not get inserted`() {
        runBlocking {
            val request = WowEntityRequest(
                basicWowHardcoreEntity.name,
                basicWowHardcoreEntity.region,
                basicWowHardcoreEntity.realm
            )

            `when`(
                blizzardClient.getClassicProfile(
                    basicWowHardcoreEntity.region,
                    basicWowHardcoreEntity.realm,
                    basicWowHardcoreEntity.name
                )
            )
                .thenReturn(
                    BlizzardMockHelper.getCharacterProfile(request)
                        .map { it.copy(id = basicWowHardcoreEntity.blizzardId ?: 12345) })
            `when`(blizzardClient.getRealm(request.region, 5220)).thenReturn(Either.Right(hardcoreRealm))

            val initialState = EntitiesState(listOf(), listOf(basicWowHardcoreEntity), listOf())

            val entitiesService = createService(initialState)

            val expected = ResolvedEntities(
                listOf(),
                listOf(basicWowHardcoreEntity to null),
                guild = null
            )
            entitiesService.resolveEntities(listOf(request), Game.WOW_HC)
                .onLeft { fail() }
                .onRight { res -> assertResolvedEntities(expected, res) }
        }
    }

    @Test
    fun `resolving a lof of characters where half exists half doesn't must split them correctly`() {
        runBlocking {
            val state = EntitiesState(listOf(), listOf(), gigaLolEntityList)

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

            val entitiesService = createService(state)
            val resolvedEntities = entitiesService.resolveEntities(gigaLolCharacterRequestList, Game.LOL)
            val expected = ResolvedEntities(
                entities = listOf(
                    LolEnrichedEntityRequest(
                        name = "sanxei7",
                        tag = "euw7",
                        puuid = "be5213a7-4546-4a94-86db-3f607cf0fa04",
                        summonerIconId = 10,
                        summonerLevel = 200
                    ) to null,
                    LolEnrichedEntityRequest(
                        name = "sanxei8",
                        tag = "euw8",
                        puuid = "7cf45999-05ef-4473-b245-2028e4169111",
                        summonerIconId = 10,
                        summonerLevel = 200
                    ) to null,
                    LolEnrichedEntityRequest(
                        name = "sanxei9",
                        tag = "euw9",
                        puuid = "262b2d4e-ba6c-4e3b-a13c-e168b278164f",
                        summonerIconId = 10,
                        summonerLevel = 200
                    ) to null,
                    LolEnrichedEntityRequest(
                        name = "sanxei10",
                        tag = "euw10",
                        puuid = "2f06cb6f-d9ac-4962-84a5-ca77c5376711",
                        summonerIconId = 10,
                        summonerLevel = 200
                    ) to null,
                    LolEnrichedEntityRequest(
                        name = "sanxei11",
                        tag = "euw11",
                        puuid = "e1d0d3d1-69b2-41b5-9951-161132b52912",
                        summonerIconId = 10,
                        summonerLevel = 200
                    ) to null,
                    LolEnrichedEntityRequest(
                        name = "sanxei12",
                        tag = "euw12",
                        puuid = "45fc6752-d210-44fd-a02a-cb146fc9d8ec",
                        summonerIconId = 10,
                        summonerLevel = 200
                    ) to null,
                    LolEnrichedEntityRequest(
                        name = "sanxei13",
                        tag = "euw13",
                        puuid = "68b7f23a-5c9d-4904-82b2-409322dd6713",
                        summonerIconId = 10,
                        summonerLevel = 200
                    ) to null
                ),
                existing = listOf(
                    LolEntity(
                        id = 0,
                        name = "sanxei0",
                        tag = "euw0",
                        puuid = "0",
                        summonerIcon = 0,
                        summonerLevel = 400
                    ) to null,
                    LolEntity(
                        id = 1,
                        name = "sanxei1",
                        tag = "euw1",
                        puuid = "1",
                        summonerIcon = 1,
                        summonerLevel = 400
                    ) to null,
                    LolEntity(
                        id = 2,
                        name = "sanxei2",
                        tag = "euw2",
                        puuid = "2",
                        summonerIcon = 2,
                        summonerLevel = 400
                    ) to null,
                    LolEntity(
                        id = 3,
                        name = "sanxei3",
                        tag = "euw3",
                        puuid = "3",
                        summonerIcon = 3,
                        summonerLevel = 400
                    ) to null,
                    LolEntity(
                        id = 4,
                        name = "sanxei4",
                        tag = "euw4",
                        puuid = "4",
                        summonerIcon = 4,
                        summonerLevel = 400
                    ) to null,
                    LolEntity(
                        id = 5,
                        name = "sanxei5",
                        tag = "euw5",
                        puuid = "5",
                        summonerIcon = 5,
                        summonerLevel = 400
                    ) to null,
                    LolEntity(
                        id = 6,
                        name = "sanxei6",
                        tag = "euw6",
                        puuid = "6",
                        summonerIcon = 6,
                        summonerLevel = 400
                    ) to null
                ),
                guild = null
            )

            resolvedEntities
                .onLeft { fail() }
                .onRight { res -> assertResolvedEntities(expected, res) }
        }
    }

    @Test
    fun `it should skip inserting same league character even if he changed his name`() {
        runBlocking {

            val state = EntitiesState(listOf(), listOf(), listOf(basicLolEntity))
            val request = LolEntityRequest("R7 Disney Girl", "EUW")

            `when`(riotClient.getPUUIDByRiotId("R7 Disney Girl", "EUW")).thenReturn(Either.Right(basicGetPuuidResponse))
            `when`(riotClient.getSummonerByPuuid("1")).thenReturn(Either.Right(basicGetSummonerResponse))

            val entitiesService = createService(state)
            val expected = ResolvedEntities(
                listOf(),
                listOf(),
                guild = null
            )

            val resolvedEntities = entitiesService.resolveEntities(listOf(request), Game.LOL)
            resolvedEntities
                .onLeft { fail() }
                .onRight { res -> assertResolvedEntities(expected, res) }
        }
    }

    @Test
    fun `i can get a wow character`() {
        runBlocking {
            val initialState = EntitiesState(listOf(basicWowEntity), listOf(), listOf())

            val entitiesService = createService(initialState)

            assertEquals(basicWowEntity, entitiesService.get(basicWowEntity.id, Game.WOW))
        }
    }

    @Test
    fun `i can get a lol character`() {
        runBlocking {
            val initialState = EntitiesState(listOf(), listOf(), listOf(basicLolEntity))
            val entitiesService = createService(initialState)

            assertEquals(basicLolEntity, entitiesService.get(basicLolEntity.id, Game.LOL))
        }
    }

    @Test
    fun `i can get all wow characters`() {
        runBlocking {
            val initialState = EntitiesState(
                listOf(basicWowEntity),
                listOf(),
                listOf(basicLolEntity)
            )

            val entitiesService = createService(initialState)
            assertEquals(listOf(basicWowEntity), entitiesService.get(Game.WOW))
        }
    }

    @Test
    fun `i can get all lol characters`() {
        runBlocking {
            val initialState = EntitiesState(
                listOf(basicWowEntity),
                listOf(),
                listOf(basicLolEntity)
            )

            val entitiesService = createService(initialState)
            assertEquals(listOf(basicLolEntity), entitiesService.get(Game.LOL))
        }
    }


    //TODO: Move wherever it belongs
    @Test
    fun `i can update lol characters`() {
        runBlocking {
            val initialState = EntitiesState(listOf(), listOf(), listOf(basicLolEntity))
            val entitiesService = createService(initialState)
            `when`(riotClient.getSummonerByPuuid(basicLolEntity.puuid)).thenReturn(
                Either.Right(
                    basicGetSummonerResponse
                )
            )
            `when`(riotClient.getAccountByPUUID(basicLolEntity.puuid)).thenReturn(
                Either.Right(
                    basicGetAccountResponse
                )
            )

            val res = entitiesService.updateEntities(Game.LOL)
            assertEquals(listOf(), res)
        }
    }

    @Test
    fun `given a request of create entity return a list of pairs with Ids and the alias propagated`() {
        runBlocking {
            val alias = "kako"
            val alias2 = "sancho"
            val initialState = EntitiesState(
                listOf(),
                listOf(),
                listOf(basicLolEntity)
            )
            val entitiesService = createService(initialState)

            val request = LolEntityRequest(basicLolEntity.name, basicLolEntity.tag, alias)
            val requestNotInState = LolEntityRequest(basicLolEntity2.name, basicLolEntity2.tag, alias2)

            `when`(riotClient.getPUUIDByRiotId(basicLolEntity2.name, basicLolEntity2.tag)).thenReturn(
                Either.Right(
                    GetPUUIDResponse(basicLolEntity2.puuid, basicLolEntity2.name, basicLolEntity2.tag)
                )
            )

            `when`(riotClient.getSummonerByPuuid(basicLolEntity2.puuid)).thenReturn(
                Either.Right(
                    GetSummonerResponse(
                        basicLolEntity2.puuid,
                        basicLolEntity2.summonerIcon,
                        1L,
                        basicLolEntity2.summonerLevel
                    )
                )
            )

            val insertRequest = LolEnrichedEntityRequest(
                name = basicLolEntity2.name,
                tag = basicLolEntity2.tag,
                puuid = basicLolEntity2.puuid,
                summonerIconId = basicLolEntity2.summonerIcon,
                summonerLevel = basicLolEntity2.summonerLevel
            )

            val expected = ResolvedEntities(
                listOf(insertRequest to alias2),
                listOf(basicLolEntity to alias),
                guild = null,
            )
            val result = entitiesService.resolveEntities(listOf(request, requestNotInState), Game.LOL)


            result
                .onLeft { fail() }
                .onRight { res ->
                    assertResolvedEntities(expected, res)
                    assertEquals(expected.entities.map { it.second }, res.entities.map { it.second })
                    assertEquals(expected.existing.map { it.second }, res.existing.map { it.second })
                }

        }
    }

    @Test
    fun `exists merges characters confirmed by the repository and by the third party into exist`() {
        runBlocking {
            `when`(
                blizzardClient.getRetailProfile(basicWowRequest2.region, basicWowRequest2.realm, basicWowRequest2.name)
            ).thenReturn(BlizzardMockHelper.getCharacterProfile(basicWowRequest2))

            val entitiesService = createService(EntitiesState(listOf(basicWowEntity), listOf(), listOf()))

            val result = entitiesService.exists(listOf(basicWowRequest, basicWowRequest2), Game.WOW)

            result.onLeft { fail() }.onRight { res ->
                assertEquals(
                    setOf(basicWowRequest.toResponse(), basicWowRequest2.toResponse()),
                    res.exist.toSet()
                )
                assertEquals(listOf(), res.nonExisting)
            }
        }
    }

    @Test
    fun `exists reports characters that don't exist anywhere as nonExisting`() {
        runBlocking {
            `when`(
                blizzardClient.getRetailProfile(basicWowRequest.region, basicWowRequest.realm, basicWowRequest.name)
            ).thenReturn(Either.Left(HttpError(404, null)))

            val entitiesService = createService(emptyEntitiesState)

            val result = entitiesService.exists(listOf(basicWowRequest), Game.WOW)

            result.onLeft { fail() }.onRight { res ->
                assertEquals(listOf(), res.exist)
                assertEquals(listOf(basicWowRequest.toResponse()), res.nonExisting)
            }
        }
    }

    @Test
    fun `exists reports a character as unchecked instead of nonExisting when blizzard can't be reached`() {
        runBlocking {
            `when`(
                blizzardClient.getRetailProfile(basicWowRequest.region, basicWowRequest.realm, basicWowRequest.name)
            ).thenReturn(Either.Left(TimeoutError("Request timeout has expired")))

            val entitiesService = createService(emptyEntitiesState)

            val result = entitiesService.exists(listOf(basicWowRequest), Game.WOW)

            result.onLeft { fail() }.onRight { res ->
                assertEquals(listOf(), res.exist)
                assertEquals(listOf(), res.nonExisting)
                assertEquals(listOf(basicWowRequest.toResponse()), res.unchecked)
            }
        }
    }

    @Test
    fun `guildExists returns the guild payload when the guild exists`() {
        runBlocking {
            `when`(blizzardClient.getRetailGuildRoster("eu", "twisting-nether", "Method")).thenReturn(
                Either.Right(
                    GetWowRosterResponse(listOf(), WowGuildResponse(999))
                )
            )

            val entitiesService = createService(emptyEntitiesState)

            val result = entitiesService.guildExists("Method", "eu", "twisting-nether")

            result.onLeft { fail() }.onRight { res ->
                assertEquals(GuildPayload("method", "twisting-nether", "eu", 999), res.guild)
            }
        }
    }

    @Test
    fun `guildExists returns a null guild when it doesn't exist`() {
        runBlocking {
            `when`(blizzardClient.getRetailGuildRoster("eu", "twisting-nether", "Method"))
                .thenReturn(Either.Left(HttpError(404, null)))

            val entitiesService = createService(emptyEntitiesState)

            val result = entitiesService.guildExists("Method", "eu", "twisting-nether")

            result.onLeft { fail() }.onRight { res ->
                assertEquals(null, res.guild)
            }
        }
    }

    @Test
    fun `guildExists returns a Left when blizzard can't be reached`() {
        runBlocking {
            `when`(blizzardClient.getRetailGuildRoster("eu", "twisting-nether", "Method"))
                .thenReturn(Either.Left(TimeoutError("Request timeout has expired")))

            val entitiesService = createService(emptyEntitiesState)

            val result = entitiesService.guildExists("Method", "eu", "twisting-nether")

            result.onRight { fail() }
        }
    }

    private suspend fun createService(entitiesState: EntitiesState): EntitiesService {
        val entitiesRepository = EntitiesInMemoryRepository().withState(entitiesState)
        val wowGuildsRepository = WowGuildsInMemoryRepository()
        val viewsRepository = ViewsInMemoryRepository()

        val wowResolver = WowEntityResolver(entitiesRepository, raiderIoClient, blizzardClient)
        val wowHardcoreResolver = WowHardcoreEntityResolver(entitiesRepository, blizzardClient)
        val lolResolver = LolEntityResolver(entitiesRepository, riotClient)

        val entitiesResolverProvider = EntityResolverProvider(
            wowResolver = wowResolver,
            wowHardcoreResolver = wowHardcoreResolver,
            lolResolver = lolResolver
        )

        val lolUpdater = LolEntityUpdater(riotClient, entitiesRepository)
        val wowHardcoreGuildUpdater = WowHardcoreGuildUpdater(wowHardcoreResolver, entitiesRepository, viewsRepository)
        val wowGuildUpdater = WowGuildUpdater(wowResolver, entitiesRepository, viewsRepository)

        return EntitiesService(
            entitiesRepository,
            wowGuildsRepository,
            entitiesResolverProvider,
            lolUpdater,
            wowHardcoreGuildUpdater,
            wowGuildUpdater
        )
    }

    private fun assertResolvedEntities(expected: ResolvedEntities, actual: ResolvedEntities) {
        assertEquals(expected.entities.size, actual.entities.size)
        assertEquals(expected.existing, actual.existing)
        assertEquals(expected.guild, actual.guild)
    }
}