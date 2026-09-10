package com.kos.clients.raiderio

import arrow.core.Either
import com.kos.assertTrue
import com.kos.clients.ClientError
import com.kos.clients.HttpError
import com.kos.clients.RetryConfig
import com.kos.clients.domain.*
import com.kos.clients.raiderio.RaiderIoHttpClientHelper.client
import com.kos.clients.raiderio.RaiderIoHttpClientHelper.raiderioProfileResponse
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.domain.WowEntity
import com.kos.entities.domain.WowEntityRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class RaiderIoHTTPClientTest {

    private val raiderIoClient = RaiderIoHTTPClient(client, RetryConfig(0, 0), apiKey = "test-key")

    @Test
    fun `test get() method with successful response`() {
        runBlocking {
            val result: Either<ClientError, RaiderIoResponse> = raiderIoClient.get(
                WowEntity(1, "region", "realm", "name", null)
            )
            assertEquals(Either.Right(raiderioProfileResponse), result)
        }
    }

    @Test
    fun `test getExpansionSeasons() method with successful response`() {
        runBlocking {
            val result: Either<ClientError, ExpansionSeasons> = raiderIoClient.getExpansionSeasons(10)
            result.onLeft { fail() }
            result.onRight {
                assertEquals(it.seasons[0].name, "TWW Season 3")
                assertTrue(it.seasons[0].isCurrentSeason)
                assertTrue(
                    it.seasons[0].dungeons.contains(
                        Dungeon(
                            "Eco-Dome Al'dani",
                            "EDA",
                            542,
                            "https://cdn.raiderio.net/images/wow/icons/large/inv_112_achievement_dungeon_ecodome.jpg"
                        )
                    )
                )
            }
        }
    }

    @Test
    fun `test getScore() method with successful response`() {
        runBlocking {
            val result = raiderIoClient.getScore(
                WowEntityRequest(basicWowEntity.name, basicWowEntity.region, basicWowEntity.realm)
            )

            assertEquals(Either.Right(1234.5), result)
        }
    }

    @Test
    fun `test getScore() method returns a score of 0 when raiderio confirms the character was not found`() {
        runBlocking {
            val result = raiderIoClient.getScore(
                WowEntityRequest("unknown-character", "eu", "zuljin")
            )

            assertEquals(Either.Right(0.0), result)
        }
    }

    @Test
    fun `test getScore() method does not treat an unrelated 400 as the character not existing`() {
        runBlocking {
            val result = raiderIoClient.getScore(
                WowEntityRequest("malformed-request-character", "eu", "zuljin")
            )

            result.onRight { fail() }.onLeft { error -> assertTrue(error is HttpError) }
        }
    }

    @Test
    fun `test getRunDetails() method with successful response`() {
        runBlocking {
            val result = raiderIoClient.getRunDetails("season-mn-1", "3415343")
            result.onLeft { fail() }
            result.onRight {
                assertEquals(RaiderIoHttpClientHelper.runDetails, it)
                assertEquals(3, it.loggedDetails?.deaths?.size)
            }
        }
    }

    @Test
    fun `test  cutoff() method with successful response`() {
        runBlocking {
            assertEquals(Either.Right(RaiderIoCutoff(1860760)), raiderIoClient.cutoff("season-tww-1"))
        }
    }

    @Test
    fun `test wowheadEmbeddedCalculator() method with successful response`() {
        runBlocking {
            val result: Either<ClientError, RaiderioWowHeadEmbeddedResponse> =
                raiderIoClient.wowheadEmbeddedCalculator("eu", "Soulseeker", "Surmana")
            result.onLeft { fail() }
            result.onRight {
                assertNotNull(it.talentLoadout)
            }
        }
    }

}
