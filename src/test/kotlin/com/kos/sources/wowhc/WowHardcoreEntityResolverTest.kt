package com.kos.sources.wowhc

import arrow.core.Either
import com.kos.clients.blizzard.BlizzardClient
import com.kos.datacache.BlizzardMockHelper
import com.kos.datacache.BlizzardMockHelper.hardcoreRealm
import com.kos.datacache.BlizzardMockHelper.notHardcoreRealm
import com.kos.entities.EntitiesTestHelper.basicWowHardcoreEntity
import com.kos.entities.EntitiesTestHelper.basicWowRequest
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class WowHardcoreEntityResolverTest {
    private val blizzardClient = mock(BlizzardClient::class.java)

    @Test
    fun `resolves a new character on a hardcore realm`() {
        runBlocking {
            `when`(
                blizzardClient.getClassicProfile(basicWowRequest.region, basicWowRequest.realm, basicWowRequest.name)
            ).thenReturn(BlizzardMockHelper.getCharacterProfile(basicWowRequest))
            `when`(blizzardClient.getRealm(basicWowRequest.region, 5220)).thenReturn(Either.Right(hardcoreRealm))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowHardcoreEntityResolver(repo, blizzardClient)

            resolver.resolve(listOf(basicWowRequest), null)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(1, res.entities.size)
                    assertEquals(basicWowRequest.name, res.entities.first().first.name)
                }
        }
    }

    @Test
    fun `does not resolve a character from a non hardcore realm`() {
        runBlocking {
            `when`(
                blizzardClient.getClassicProfile(basicWowRequest.region, basicWowRequest.realm, basicWowRequest.name)
            ).thenReturn(BlizzardMockHelper.getCharacterProfile(basicWowRequest))
            `when`(blizzardClient.getRealm(basicWowRequest.region, 5220)).thenReturn(Either.Right(notHardcoreRealm))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowHardcoreEntityResolver(repo, blizzardClient)

            resolver.resolve(listOf(basicWowRequest), null)
                .onLeft { fail() }
                .onRight { res -> assertEquals(listOf(), res.entities) }
        }
    }

    @Test
    fun `a character already in the repository is returned as existing without calling blizzard`() {
        runBlocking {
            val repo = EntitiesInMemoryRepository()
                .withState(EntitiesState(listOf(), listOf(basicWowHardcoreEntity), listOf()))
            val resolver = WowHardcoreEntityResolver(repo, blizzardClient)

            resolver.resolve(listOf(basicWowRequest), null)
                .onLeft { fail() }
                .onRight { res -> assertEquals(listOf(basicWowHardcoreEntity to null), res.existing) }

            verifyNoInteractions(blizzardClient)
        }
    }
}
