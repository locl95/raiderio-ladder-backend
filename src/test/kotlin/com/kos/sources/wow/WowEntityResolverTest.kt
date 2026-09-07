package com.kos.sources.wow

import arrow.core.Either
import com.kos.clients.HttpError
import com.kos.clients.TimeoutError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.domain.GetWowRosterResponse
import com.kos.clients.domain.WowCharacterResponse
import com.kos.clients.domain.WowGuildResponse
import com.kos.clients.domain.WowMemberResponse
import com.kos.clients.domain.WowRosterMemberRealmResponse
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.datacache.BlizzardMockHelper
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.EntitiesTestHelper.basicWowRequest
import com.kos.entities.EntitiesTestHelper.basicWowRequest2
import com.kos.entities.domain.GuildPayload
import com.kos.entities.domain.WowEntityRequest
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.views.WowExtraArguments
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class WowEntityResolverTest {
    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val blizzardClient = mock(BlizzardClient::class.java)

    @Test
    fun `resolves a new character that exists in blizzard`() {
        runBlocking {
            `when`(
                blizzardClient.getRetailProfile(basicWowRequest.region, basicWowRequest.realm, basicWowRequest.name)
            ).thenReturn(BlizzardMockHelper.getCharacterProfile(basicWowEntity))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(basicWowRequest), null)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(
                        listOf(
                            WowEntityRequest(
                                basicWowRequest.name,
                                basicWowRequest.region,
                                basicWowRequest.realm,
                                basicWowEntity.id
                            ) to null
                        ),
                        res.entities
                    )
                    assertEquals(listOf(), res.existing)
                    assertEquals(listOf(), res.unchecked)
                }
        }
    }

    @Test
    fun `does not resolve a new character that does not exist in blizzard`() {
        runBlocking {
            `when`(
                blizzardClient.getRetailProfile(basicWowRequest.region, basicWowRequest.realm, basicWowRequest.name)
            ).thenReturn(Either.Left(HttpError(404, null)))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(basicWowRequest), null)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(), res.entities)
                    assertEquals(listOf(), res.unchecked)
                }
        }
    }

    @Test
    fun `a character already in the repository is returned as existing without calling blizzard`() {
        runBlocking {
            val repo =
                EntitiesInMemoryRepository().withState(EntitiesState(listOf(basicWowEntity), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(basicWowRequest), null)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(basicWowEntity to null), res.existing)
                    assertEquals(listOf(), res.entities)
                }

            verifyNoInteractions(blizzardClient)
        }
    }

    @Test
    fun `a non-guild resolve does not call raiderio`() {
        runBlocking {
            `when`(
                blizzardClient.getRetailProfile(basicWowRequest.region, basicWowRequest.realm, basicWowRequest.name)
            ).thenReturn(BlizzardMockHelper.getCharacterProfile(basicWowEntity))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(basicWowRequest), null).onLeft { fail() }

            verifyNoInteractions(raiderIoClient)
        }
    }

    @Test
    fun `resolves a batch of new characters concurrently, keeping only the ones that exist`() {
        runBlocking {
            `when`(
                blizzardClient.getRetailProfile(basicWowRequest.region, basicWowRequest.realm, basicWowRequest.name)
            ).thenReturn(BlizzardMockHelper.getCharacterProfile(basicWowEntity))
            `when`(
                blizzardClient.getRetailProfile(basicWowRequest2.region, basicWowRequest2.realm, basicWowRequest2.name)
            ).thenReturn(Either.Left(HttpError(404, null)))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(basicWowRequest, basicWowRequest2), null)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(
                        listOf(
                            WowEntityRequest(
                                basicWowRequest.name,
                                basicWowRequest.region,
                                basicWowRequest.realm,
                                basicWowEntity.id
                            ) to null
                        ),
                        res.entities
                    )
                }
        }
    }

    @Test
    fun `resolve reports a blizzard failure as unchecked instead of lying that the character doesn't exist`() {
        runBlocking {
            val timeoutError = TimeoutError("Request timeout has expired")
            `when`(
                blizzardClient.getRetailProfile(basicWowRequest.region, basicWowRequest.realm, basicWowRequest.name)
            ).thenReturn(Either.Left(timeoutError))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(basicWowRequest), null)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(), res.entities)
                    assertEquals(1, res.unchecked.size)
                    assertEquals(basicWowRequest, res.unchecked.single().first)
                }
        }
    }

    @Test
    fun `resolve partitions a mixed batch into resolved entities and unchecked ones`() {
        runBlocking {
            val timeoutError = TimeoutError("Request timeout has expired")
            val basicWowRequest3 = basicWowRequest2.copy(name = "thirdcharacter")

            `when`(
                blizzardClient.getRetailProfile(basicWowRequest.region, basicWowRequest.realm, basicWowRequest.name)
            ).thenReturn(BlizzardMockHelper.getCharacterProfile(basicWowEntity))
            `when`(
                blizzardClient.getRetailProfile(basicWowRequest2.region, basicWowRequest2.realm, basicWowRequest2.name)
            ).thenReturn(Either.Left(HttpError(404, null)))
            `when`(
                blizzardClient.getRetailProfile(basicWowRequest3.region, basicWowRequest3.realm, basicWowRequest3.name)
            ).thenReturn(Either.Left(timeoutError))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(basicWowRequest, basicWowRequest2, basicWowRequest3), null)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(
                        listOf(
                            WowEntityRequest(
                                basicWowRequest.name,
                                basicWowRequest.region,
                                basicWowRequest.realm,
                                basicWowEntity.id
                            ) to null
                        ),
                        res.entities
                    )
                    assertEquals(listOf(basicWowRequest3), res.unchecked.map { it.first })
                }
        }
    }

    private val guildRequest = WowEntityRequest("method", "eu", "twisting-nether")
    private val guildExtraArguments = WowExtraArguments(isGuild = true, season = 0)

    @Test
    fun `resolves a guild's roster, keeping max level members above the score threshold, and returns the guild payload`() {
        runBlocking {
            val member = WowEntityRequest("kakarona", guildRequest.region, guildRequest.realm)

            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse(member.name, 90, 555))),
                            WowGuildResponse(999)
                        )
                    )
                )
            `when`(raiderIoClient.getScore(member.copy(blizzardId = 555))).thenReturn(Either.Right(1500.0))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(guildRequest), guildExtraArguments)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(
                        listOf(WowEntityRequest(member.name, member.region, member.realm, 555) to null),
                        res.entities
                    )
                    assertEquals(listOf(), res.unchecked)
                    assertEquals(
                        GuildPayload(guildRequest.name, guildRequest.realm, guildRequest.region, 999),
                        res.guild
                    )
                }
        }
    }

    @Test
    fun `resolves a guild member on a different connected realm using their own realm, not the guild's`() {
        runBlocking {
            val member = WowEntityRequest("xesevi", guildRequest.region, "coilfang")

            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(
                                WowMemberResponse(
                                    WowCharacterResponse(
                                        member.name,
                                        90,
                                        105161061,
                                        WowRosterMemberRealmResponse("coilfang")
                                    )
                                )
                            ),
                            WowGuildResponse(999)
                        )
                    )
                )
            `when`(raiderIoClient.getScore(member.copy(blizzardId = 105161061))).thenReturn(Either.Right(1500.0))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(guildRequest), guildExtraArguments)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(
                        listOf(WowEntityRequest(member.name, member.region, member.realm, 105161061) to null),
                        res.entities
                    )
                }
        }
    }

    @Test
    fun `resolving a guild lowercases the guild payload's name, realm and region`() {
        runBlocking {
            val mixedCaseRequest = WowEntityRequest("Method", "EU", "Twisting-Nether")
            val member = WowEntityRequest("kakarona", mixedCaseRequest.region, mixedCaseRequest.realm)

            `when`(blizzardClient.getRetailGuildRoster(mixedCaseRequest.region, mixedCaseRequest.realm, mixedCaseRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse(member.name, 90, 555))),
                            WowGuildResponse(999)
                        )
                    )
                )
            `when`(raiderIoClient.getScore(member.copy(blizzardId = 555))).thenReturn(Either.Right(1500.0))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(mixedCaseRequest), guildExtraArguments)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(GuildPayload("method", "twisting-nether", "eu", 999), res.guild)
                }
        }
    }

    @Test
    fun `filters out guild members below max level before checking their score`() {
        runBlocking {
            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse("lowlevel", 79, 111))),
                            WowGuildResponse(999)
                        )
                    )
                )

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(guildRequest), guildExtraArguments)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(), res.entities)
                    assertEquals(listOf(), res.unchecked)
                }

            verifyNoInteractions(raiderIoClient)
        }
    }

    @Test
    fun `skips a new guild member with no score instead of resolving it`() {
        runBlocking {
            val member = WowEntityRequest("notcompeting", guildRequest.region, guildRequest.realm)

            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse(member.name, 90, 222))),
                            WowGuildResponse(999)
                        )
                    )
                )
            `when`(raiderIoClient.getScore(member.copy(blizzardId = 222))).thenReturn(Either.Right(0.0))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(guildRequest), guildExtraArguments)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(), res.entities)
                    assertEquals(listOf(), res.unchecked)
                }
        }
    }

    @Test
    fun `a new guild member with a score at the cutoff passes the filter`() {
        runBlocking {
            val member = WowEntityRequest("borderline", guildRequest.region, guildRequest.realm)

            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse(member.name, 90, 777))),
                            WowGuildResponse(999)
                        )
                    )
                )
            `when`(raiderIoClient.getScore(member.copy(blizzardId = 777))).thenReturn(Either.Right(1000.0))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(guildRequest), guildExtraArguments)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(
                        listOf(WowEntityRequest(member.name, member.region, member.realm, 777) to null),
                        res.entities
                    )
                    assertEquals(listOf(), res.unchecked)
                }
        }
    }

    @Test
    fun `skips a guild member on a raiderio failure while checking their score`() {
        runBlocking {
            val member = WowEntityRequest("flaky", guildRequest.region, guildRequest.realm)
            val timeoutError = TimeoutError("Request timeout has expired")

            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse(member.name, 90, 333))),
                            WowGuildResponse(999)
                        )
                    )
                )
            `when`(raiderIoClient.getScore(member.copy(blizzardId = 333))).thenReturn(Either.Left(timeoutError))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(guildRequest), guildExtraArguments)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(), res.entities)
                    assertEquals(listOf(), res.unchecked)
                }
        }
    }

    @Test
    fun `a guild member already tracked is returned as existing without calling raiderio`() {
        runBlocking {
            val member = WowEntityRequest(basicWowEntity.name, guildRequest.region, guildRequest.realm)
            val trackedEntity = member.toEntity(1)

            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse(member.name, 90, 444))),
                            WowGuildResponse(999)
                        )
                    )
                )

            val repo =
                EntitiesInMemoryRepository().withState(EntitiesState(listOf(trackedEntity), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(guildRequest), guildExtraArguments)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(trackedEntity to null), res.existing)
                    assertEquals(listOf(), res.entities)
                }

            verifyNoInteractions(raiderIoClient)
        }
    }
}
