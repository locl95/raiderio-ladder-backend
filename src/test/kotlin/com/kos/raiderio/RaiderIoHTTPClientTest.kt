package com.kos.raiderio

import arrow.core.Either
import com.kos.characters.Character
import com.kos.common.HttpError
import com.kos.raiderio.RaiderioHttpClientHelper.client
import com.kos.raiderio.RaiderioHttpClientHelper.raiderioProfileResponse
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RaiderIoHTTPClientTest {

    private val raiderIoClient = RaiderIoHTTPClient(client)

    @AfterTest
    fun tearDown() {
        client.close()
    }

    //TODO: This suite must be more extensive

    @Test
    fun `test get() method with successful response`() {
        runBlocking {
            val result: Either<HttpError, RaiderIoResponse> = raiderIoClient.get(
                Character(1, "region", "realm", "name")
            )
            assertEquals(Either.Right(raiderioProfileResponse), result)
        }
    }
}
