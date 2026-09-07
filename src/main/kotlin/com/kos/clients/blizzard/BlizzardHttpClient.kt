package com.kos.clients.blizzard

import arrow.core.Either
import arrow.core.raise.either
import com.kos.clients.ClientError
import com.kos.clients.Retry.retryEitherWithFixedDelay
import com.kos.clients.RetryConfig
import com.kos.clients.blizzard.BlizzardHttpClient.BlizzardHttpClientConstants.BATTLENET_NAMESPACE
import com.kos.clients.blizzard.BlizzardHttpClient.BlizzardHttpClientConstants.DYNAMIC_CLASSIC1X_NAMESPACE
import com.kos.clients.blizzard.BlizzardHttpClient.BlizzardHttpClientConstants.PROFILE_CLASSIC1X_EU_NAMESPACE
import com.kos.clients.blizzard.BlizzardHttpClient.BlizzardHttpClientConstants.PROFILE_CLASSIC1X_NAMESPACE
import com.kos.clients.blizzard.BlizzardHttpClient.BlizzardHttpClientConstants.STATIC_CLASSIC_NAMESPACE
import com.kos.clients.domain.*
import com.kos.clients.fetchFromApi
import com.kos.common.WithLogger
import io.github.resilience4j.kotlin.ratelimiter.RateLimiterConfig
import io.github.resilience4j.kotlin.ratelimiter.executeSuspendFunction
import io.github.resilience4j.ratelimiter.RateLimiter
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.OffsetDateTime

data class TokenState(val obtainedAt: OffsetDateTime, val tokenResponse: TokenResponse)

class BlizzardHttpClient(
    private val client: HttpClient,
    private val retryConfig: RetryConfig,
    private val blizzardAuthClient: BlizzardAuthClient
) : BlizzardClient, WithLogger("blizzardClient") {

    object BlizzardHttpClientConstants {
        const val PROFILE_CLASSIC1X_NAMESPACE = "profile-classic1x"
        const val BATTLENET_NAMESPACE = "Battlenet-Namespace"
        const val STATIC_CLASSIC_NAMESPACE = "static-classic"
        const val DYNAMIC_CLASSIC1X_NAMESPACE = "dynamic-classic1x"
        const val PROFILE_CLASSIC1X_EU_NAMESPACE = "profile-classic1x-eu"
    }

    private val baseURI: (String) -> URI = { region -> URI("https://$region.api.blizzard.com") }
    private var token: Either<ClientError, TokenState>? = null

    override suspend fun getClassicProfile(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, GetWowCharacterResponse> {
        return throttleRequest {
            either {
                logger.debug("getClassicProfile for $region $realm $character")
                val tokenResponse = getAndUpdateToken().bind()
                val partialURI = URI("/profile/wow/character/$realm/${encodedName(character)}?locale=en_US")


                retryEitherWithFixedDelay(
                    retryConfig = retryConfig,
                    functionName = "getClassicProfile",
                ) {
                    fetchFromApi<GetWowCharacterResponse> {
                        client.get((baseURI(region).toString() + partialURI.toString()).lowercase()) {
                            headers {
                                append(HttpHeaders.Authorization, "Bearer ${tokenResponse.tokenResponse.accessToken}")
                                append(HttpHeaders.Accept, "*/*")
                                append(BATTLENET_NAMESPACE, PROFILE_CLASSIC1X_NAMESPACE)
                            }
                        }
                    }
                }.bind()
            }
        }
    }

    override suspend fun getRetailProfile(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, GetWowCharacterResponse> {
        return throttleRequest {
            either {
                logger.debug("getRetailProfile for $region $realm $character")
                val tokenResponse = getAndUpdateToken().bind()
                val namespace = "profile-$region"
                val partialURI = URI("/profile/wow/character/$realm/${encodedName(character)}?locale=en_US")

                retryEitherWithFixedDelay(
                    retryConfig = retryConfig,
                    functionName = "getRetailProfile",
                ) {
                    fetchFromApi<GetWowCharacterResponse> {
                        client.get((baseURI(region).toString() + partialURI.toString()).lowercase()) {
                            headers {
                                append(HttpHeaders.Authorization, "Bearer ${tokenResponse.tokenResponse.accessToken}")
                                append(HttpHeaders.Accept, "*/*")
                                append(BATTLENET_NAMESPACE, namespace)
                            }
                        }
                    }
                }.bind()
            }
        }
    }

    override suspend fun getCharacterMedia(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, GetWowMediaResponse> {
        return throttleRequest {
            either {
                logger.debug("getCharacterMedia for $region $realm $character")
                val tokenResponse = getAndUpdateToken().bind()
                val partialURI =
                    URI("/profile/wow/character/$realm/${encodedName(character)}/character-media?locale=en_US")

                retryEitherWithFixedDelay(
                    retryConfig = retryConfig,
                    functionName = "getCharacterMedia",
                ) {
                    fetchFromApi<GetWowMediaResponse> {
                        client.get((baseURI(region).toString() + partialURI.toString()).lowercase()) {
                            headers {
                                append(HttpHeaders.Authorization, "Bearer ${tokenResponse.tokenResponse.accessToken}")
                                append(HttpHeaders.Accept, "*/*")
                                append(BATTLENET_NAMESPACE, PROFILE_CLASSIC1X_NAMESPACE)
                            }
                        }
                    }
                }.bind()
            }
        }
    }

    override suspend fun getCharacterEquipment(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, GetWowEquipmentResponse> {
        return throttleRequest {
            either {
                logger.debug("getCharacterEquipment for $region $realm $character")
                val tokenResponse = getAndUpdateToken().bind()
                val partialURI = URI("/profile/wow/character/$realm/${encodedName(character)}/equipment?locale=en_US")

                retryEitherWithFixedDelay(
                    retryConfig = retryConfig,
                    functionName = "getCharacterEquipment",
                ) {
                    fetchFromApi<GetWowEquipmentResponse> {
                        client.get((baseURI(region).toString() + partialURI.toString()).lowercase()) {
                            headers {
                                append(HttpHeaders.Authorization, "Bearer ${tokenResponse.tokenResponse.accessToken}")
                                append(HttpHeaders.Accept, "*/*")
                                append(BATTLENET_NAMESPACE, PROFILE_CLASSIC1X_NAMESPACE)
                            }
                        }
                    }
                }.bind()
            }
        }
    }

    override suspend fun getCharacterSpecializations(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, GetWowSpecializationsResponse> {
        return throttleRequest {
            either {
                logger.debug("getCharacterSpecialitzation for $region $realm $character")
                val tokenResponse = getAndUpdateToken().bind()
                val partialURI =
                    URI("/profile/wow/character/$realm/${encodedName(character)}/specializations?namespace=profile-classic1x-eu&locale=en_US")

                retryEitherWithFixedDelay(
                    retryConfig = retryConfig,
                    functionName = "getCharacterSpecializations",
                ) {
                    fetchFromApi<GetWowSpecializationsResponse> {
                        client.get((baseURI(region).toString() + partialURI.toString()).lowercase()) {
                            headers {
                                append(HttpHeaders.Authorization, "Bearer ${tokenResponse.tokenResponse.accessToken}")
                                append(HttpHeaders.Accept, "*/*")
                                append(BATTLENET_NAMESPACE, PROFILE_CLASSIC1X_NAMESPACE)
                            }
                        }
                    }
                }.bind()
            }
        }
    }

    override suspend fun getCharacterStats(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, GetWowCharacterStatsResponse> {
        return throttleRequest {
            either {
                logger.debug("getCharacterStats for $region $realm $character")
                val tokenResponse = getAndUpdateToken().bind()
                val partialURI = URI("/profile/wow/character/$realm/${encodedName(character)}/statistics?locale=en_US")


                retryEitherWithFixedDelay(
                    retryConfig = retryConfig,
                    functionName = "getCharacterStats",
                ) {
                    fetchFromApi<GetWowCharacterStatsResponse> {
                        client.get((baseURI(region).toString() + partialURI.toString()).lowercase()) {
                            headers {
                                append(HttpHeaders.Authorization, "Bearer ${tokenResponse.tokenResponse.accessToken}")
                                append(HttpHeaders.Accept, "*/*")
                                append(BATTLENET_NAMESPACE, PROFILE_CLASSIC1X_NAMESPACE)
                            }
                        }
                    }
                }.bind()
            }
        }
    }

    override suspend fun getItemMedia(
        region: String,
        id: Long
    ): Either<ClientError, GetWowMediaResponse> {
        return throttleRequest {
            either {
                logger.debug("getItemMedia for $id")
                val tokenResponse = getAndUpdateToken().bind()
                val partialURI = URI("/data/wow/media/item/$id?locale=en_US")

                retryEitherWithFixedDelay(
                    retryConfig = retryConfig,
                    functionName = "getItemMedia",
                ) {
                    fetchFromApi<GetWowMediaResponse> {
                        client.get((baseURI(region).toString() + partialURI.toString()).lowercase()) {
                            headers {
                                append(HttpHeaders.Authorization, "Bearer ${tokenResponse.tokenResponse.accessToken}")
                                append(HttpHeaders.Accept, "*/*")
                                append(BATTLENET_NAMESPACE, STATIC_CLASSIC_NAMESPACE)
                            }
                        }
                    }
                }.bind()
            }
        }
    }

    override suspend fun getItem(region: String, id: Long): Either<ClientError, GetWowItemResponse> {
        return throttleRequest {
            either {
                logger.debug("getItem for $id")
                val tokenResponse = getAndUpdateToken().bind()
                val partialURI = URI("/data/wow/item/$id?locale=en_US")

                retryEitherWithFixedDelay(
                    retryConfig = retryConfig,
                    functionName = "getItem",
                ) {
                    fetchFromApi<GetWowItemResponse> {
                        client.get((baseURI(region).toString() + partialURI.toString()).lowercase()) {
                            headers {
                                append(HttpHeaders.Authorization, "Bearer ${tokenResponse.tokenResponse.accessToken}")
                                append(HttpHeaders.Accept, "*/*")
                                append(BATTLENET_NAMESPACE, STATIC_CLASSIC_NAMESPACE)
                            }
                        }
                    }
                }.bind()
            }
        }
    }

    override suspend fun getRealm(
        region: String,
        id: Long
    ): Either<ClientError, GetWowRealmResponse> {
        return throttleRequest {
            either {
                val tokenResponse = getAndUpdateToken().bind()
                val partialURI = URI("/data/wow/realm/$id?locale=en_US")

                retryEitherWithFixedDelay(
                    retryConfig = retryConfig,
                    functionName = "getRealm",
                ) {
                    fetchFromApi<GetWowRealmResponse> {
                        client.get((baseURI(region).toString() + partialURI.toString()).lowercase()) {
                            headers {
                                append(HttpHeaders.Authorization, "Bearer ${tokenResponse.tokenResponse.accessToken}")
                                append(HttpHeaders.Accept, "*/*")
                                append(BATTLENET_NAMESPACE, DYNAMIC_CLASSIC1X_NAMESPACE)
                            }
                        }
                    }
                }.bind()
            }
        }
    }

    override suspend fun getHardcoreGuildRoster(
        region: String,
        realm: String,
        guild: String
    ): Either<ClientError, GetWowRosterResponse> {
        return throttleRequest {
            either {
                val tokenResponse = getAndUpdateToken().bind()
                val partialURI = URI("/data/wow/guild/$realm/$guild/roster?namespace=profile-classic1x-eu&locale=en_US")

                retryEitherWithFixedDelay(
                    retryConfig = retryConfig,
                    functionName = "getGuildRoster",
                ) {
                    fetchFromApi<GetWowRosterResponse> {
                        client.get((baseURI(region).toString() + partialURI.toString()).lowercase()) {
                            headers {
                                append(HttpHeaders.Authorization, "Bearer ${tokenResponse.tokenResponse.accessToken}")
                                append(HttpHeaders.Accept, "*/*")
                                append(BATTLENET_NAMESPACE, PROFILE_CLASSIC1X_EU_NAMESPACE)
                            }
                        }
                    }
                }.bind()
            }
        }
    }

    override suspend fun getRetailGuildRoster(
        region: String,
        realm: String,
        guild: String
    ): Either<ClientError, GetWowRosterResponse> {
        return throttleRequest {
            either {
                val tokenResponse = getAndUpdateToken().bind()
                val namespace = "profile-$region"
                val partialURI = URI("/data/wow/guild/$realm/$guild/roster?namespace=$namespace&locale=en_US")

                retryEitherWithFixedDelay(
                    retryConfig = retryConfig,
                    functionName = "getRetailGuildRoster",
                ) {
                    fetchFromApi<GetWowRosterResponse> {
                        client.get((baseURI(region).toString() + partialURI.toString()).lowercase()) {
                            headers {
                                append(HttpHeaders.Authorization, "Bearer ${tokenResponse.tokenResponse.accessToken}")
                                append(HttpHeaders.Accept, "*/*")
                                append(BATTLENET_NAMESPACE, namespace)
                            }
                        }
                    }
                }.bind()
            }
        }
    }

    private suspend fun getAndUpdateToken(): Either<ClientError, TokenState> {
        val newTokenState = when (token) {
            null -> {
                logger.debug("null token state")
                blizzardAuthClient.getAccessToken()
            }

            else -> token!!.fold(
                ifLeft = {
                    logger.debug("token state with ClientError: {}", it.toString())
                    blizzardAuthClient.getAccessToken()
                },
                ifRight = {
                    if (it.obtainedAt.plusSeconds(it.tokenResponse.expiresIn)
                            .minusSeconds(10)
                            .isBefore(OffsetDateTime.now())
                    ) {
                        logger.debug(
                            "token state expired: expiresIn - {} obtainedAt - {} ",
                            it.tokenResponse.expiresIn,
                            it.obtainedAt
                        )
                        blizzardAuthClient.getAccessToken()
                    } else {
                        logger.debug("token in good state")
                        Either.Right(it.tokenResponse)
                    }
                }
            )
        }.map {
            TokenState(OffsetDateTime.now(), it)
        }
        token = newTokenState
        return newTokenState
    }

    private val perSecondRateLimiter = RateLimiter.of(
        "perSecondLimiter",
        RateLimiterConfig {
            this.limitForPeriod(100)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofSeconds(1))
                .build()
        }
    )

    private suspend fun <T> throttleRequest(request: suspend () -> T): T {
        return perSecondRateLimiter.executeSuspendFunction(request)
    }

    private fun encodedName(name: String) = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
}