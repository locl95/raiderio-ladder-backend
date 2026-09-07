package com.kos.clients.blizzard

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.clients.RetryConfig
import com.kos.clients.blizzard.BlizzardHttpClientHelper.client
import com.kos.clients.domain.GetWowCharacterResponse
import com.kos.datacache.BlizzardMockHelper
import com.kos.datacache.BlizzardMockHelper.getWowCharacterResponse
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals

class BlizzardHTTPClientTest {

    private val blizzardAuthClient = mock(BlizzardAuthClient::class.java)
    private val blizzardClient = BlizzardHttpClient(client, RetryConfig(0, 0), blizzardAuthClient)

    @Test
    fun `getCharacterProfile returns successful response`() {
        runBlocking {
            givenAValidToken()
            val result: Either<ClientError, GetWowCharacterResponse> = blizzardClient.getClassicProfile(
                "region", "realm", "name"
            )
            assertEquals(Either.Right(getWowCharacterResponse), result)
        }
    }

    @Test
    fun `getCharacterMedia returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getCharacterMedia("region", "realm", "name")

        assertEquals(
            BlizzardMockHelper.getCharacterMedia(basicWowEntity),
            result
        )
    }

    @Test
    fun `getCharacterEquipment returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getCharacterEquipment("region", "realm", "name")

        assertEquals(
            Either.Right(BlizzardMockHelper.getWowEquipmentResponse),
            result
        )
    }

    @Test
    fun `getCharacterSpecializations returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getCharacterSpecializations("region", "realm", "name")

        assertEquals(
            Either.Right(BlizzardMockHelper.getWowSpecializationsResponse),
            result
        )
    }

    @Test
    fun `getCharacterStats returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getCharacterStats("region", "realm", "name")

        assertEquals(
            Either.Right(BlizzardMockHelper.getWowStatsResponse),
            result
        )
    }

    @Test
    fun `getItemMedia returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getItemMedia("region", 123L)

        assertEquals(
            BlizzardMockHelper.getItemMedia(),
            result
        )
    }

    @Test
    fun `getItem returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getItem("region", 18421)

        assertEquals(
            Either.Right(BlizzardMockHelper.getWowItemResponse),
            result
        )
    }

    @Test
    fun `getRealm returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getRealm("region", 1L)

        assertEquals(
            Either.Right(BlizzardMockHelper.notHardcoreRealm),
            result
        )
    }

    @Test
    fun `getGuildRoster returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getHardcoreGuildRoster("region", "realm", "guild")

        assertEquals(
            Either.Right(BlizzardMockHelper.getWowGuildRosterResponse),
            result
        )
    }


    @Test
    fun `getRetailGuildRoster returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getRetailGuildRoster("us", "realm", "guild")

        assertEquals(Either.Right(BlizzardMockHelper.getWowRetailGuildRosterResponse), result)
    }

    private suspend fun givenAValidToken() {
        `when`(blizzardAuthClient.getAccessToken())
            .thenReturn(BlizzardMockHelper.getToken())
    }
}
