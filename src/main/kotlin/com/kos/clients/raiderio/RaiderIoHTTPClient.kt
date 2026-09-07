package com.kos.clients.raiderio

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.clients.HttpError
import com.kos.clients.Retry.retryEitherWithFixedDelay
import com.kos.clients.RetryConfig
import com.kos.clients.domain.*
import com.kos.clients.fetchFromApi
import com.kos.clients.raiderio.RaiderIoHTTPClient.RaiderIoHTTPClientConstants.BASE_URI
import com.kos.clients.raiderio.RaiderIoHTTPClient.RaiderIoHTTPClientConstants.CHARACTERS_PROFILE_PATH
import com.kos.clients.raiderio.RaiderIoHTTPClient.RaiderIoHTTPClientConstants.CLASSIC_BASE_URI
import com.kos.clients.raiderio.RaiderIoHTTPClient.RaiderIoHTTPClientConstants.MYTHIC_PLUS_CUTOFFS_PATH
import com.kos.clients.raiderio.RaiderIoHTTPClient.RaiderIoHTTPClientConstants.MYTHIC_PLUS_RUN_DETAILS_PATH
import com.kos.clients.raiderio.RaiderIoHTTPClient.RaiderIoHTTPClientConstants.MYTHIC_PLUS_STATIC_DATA_PATH
import com.kos.common.WithLogger
import com.kos.entities.domain.WowEntity
import com.kos.entities.domain.WowEntityRequest
import io.github.resilience4j.kotlin.ratelimiter.RateLimiterConfig
import io.github.resilience4j.kotlin.ratelimiter.executeSuspendFunction
import io.github.resilience4j.ratelimiter.RateLimiter
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.net.URI
import java.time.Duration

data class RaiderIoHTTPClient(
    val client: HttpClient,
    val retryConfig: RetryConfig,
    val apiKey: String
) : RaiderIoClient, WithLogger("RaiderioClient") {
    object RaiderIoHTTPClientConstants {
        val BASE_URI = URI("https://raider.io/api/v1")
        val CLASSIC_BASE_URI = URI("https://era.raider.io/api/v1")

        const val CHARACTERS_PROFILE_PATH = "/characters/profile"
        const val MYTHIC_PLUS_STATIC_DATA_PATH = "/mythic-plus/static-data"
        const val MYTHIC_PLUS_CUTOFFS_PATH = "/mythic-plus/season-cutoffs"
        const val MYTHIC_PLUS_RUN_DETAILS_PATH = "/mythic-plus/run-details"
    }

    override suspend fun getExpansionSeasons(expansionId: Int): Either<ClientError, ExpansionSeasons> {
        return retryEitherWithFixedDelay(
            retryConfig = retryConfig,
            functionName = "getExpansionSeasons",
        ) {
            fetchFromApi<ExpansionSeasons> {
                apiGet(BASE_URI.toString() + MYTHIC_PLUS_STATIC_DATA_PATH) {
                    headers {
                        append(HttpHeaders.Accept, "*/*")
                    }
                    url {
                        parameters.append("expansion_id", expansionId.toString())
                    }
                }
            }
        }
    }

    override suspend fun getRunDetails(season: String, runId: String): Either<ClientError, RunDetails> {
        return retryEitherWithFixedDelay(
            retryConfig = retryConfig,
            functionName = "getRunDetails",
        ) {
            fetchFromApi<RunDetails> {
                apiGet(BASE_URI.toString() + MYTHIC_PLUS_RUN_DETAILS_PATH) {
                    headers {
                        append(HttpHeaders.Accept, "*/*")
                    }
                    url {
                        parameters.append("season", season)
                        parameters.append("id", runId)
                    }
                }
            }
        }
    }

    override suspend fun get(wowEntity: WowEntity): Either<ClientError, RaiderIoResponse> {
        return retryEitherWithFixedDelay(
            retryConfig = retryConfig,
            functionName = "getRaiderIoResponse",
        ) {
            fetchFromApi<RaiderIoProfile> {
                apiGet(BASE_URI.toString() + CHARACTERS_PROFILE_PATH) {
                    headers {
                        append(HttpHeaders.Accept, "*/*")
                    }
                    url {
                        parameters.append("region", wowEntity.region)
                        parameters.append("realm", wowEntity.realm)
                        parameters.append("name", wowEntity.name)
                        parameters.append(
                            "fields",
                            "mythic_plus_scores_by_season:current,mythic_plus_best_runs:all,mythic_plus_ranks,mythic_plus_recent_runs"
                        )
                    }
                }
            }.fold(
                { clientError -> Either.Left(clientError) },
                {
                    RaiderIoProtocol.getMythicPlusRanks(
                        it,
                        wowEntity.specsWithName(it.`class`),
                    ).fold({ jsonError -> Either.Left(jsonError) }) { specsWithName ->
                        Either.Right(RaiderIoResponse(it, specsWithName))
                    }
                })
        }
    }

    override suspend fun getScore(wowEntityRequest: WowEntityRequest): Either<ClientError, Double> =
        retryEitherWithFixedDelay(
            retryConfig = retryConfig,
            functionName = "raiderIoScore",
        ) {
            fetchFromApi<RaiderIoScoreResponse> {
                apiGet(BASE_URI.toString() + CHARACTERS_PROFILE_PATH) {
                    headers {
                        append(HttpHeaders.Accept, "*/*")
                    }
                    url {
                        parameters.append("region", wowEntityRequest.region)
                        parameters.append("realm", wowEntityRequest.realm)
                        parameters.append("name", wowEntityRequest.name)
                        parameters.append("fields", "mythic_plus_scores_by_season:current")
                    }
                }
            }.map { it.seasonScores.firstOrNull()?.scores?.all ?: 0.0 }.orCharacterNotFound(0.0)
        }

    override suspend fun cutoff(seasonSlug: String): Either<ClientError, RaiderIoCutoff> {
        return fetchFromApi(
            request = {
                apiGet(BASE_URI.toString() + MYTHIC_PLUS_CUTOFFS_PATH) {
                    headers {
                        append(HttpHeaders.Accept, "*/*")
                    }
                    url {
                        parameters.append("region", "eu")
                        parameters.append("season", seasonSlug)
                    }
                }
            },
            parseResponse = { cutoff ->
                RaiderIoProtocol.parseCutoffJson(cutoff)
            }
        )
    }

    override suspend fun wowheadEmbeddedCalculator(
        region: String,
        realm: String,
        name: String
    ): Either<ClientError, RaiderioWowHeadEmbeddedResponse> {
        logger.debug("Getting Wowhead talents for {}/{}/{}", region, realm, name)
        return retryEitherWithFixedDelay(
            retryConfig = retryConfig,
            functionName = "getRaiderioWowHeadEmbeddedResponse",
        ) {
            fetchFromApi<RaiderioWowHeadEmbeddedResponse> {
                apiGet(CLASSIC_BASE_URI.toString() + CHARACTERS_PROFILE_PATH) {
                    headers {
                        append(HttpHeaders.Accept, "*/*")
                    }
                    url {
                        parameters.append("region", region)
                        parameters.append("realm", realm)
                        parameters.append("name", name)
                        parameters.append("fields", "talents")
                    }
                }
            }
        }
    }

    private val rateLimiter = RateLimiter.of(
        "raiderIoRateLimiter",
        RateLimiterConfig {
            this.limitForPeriod(1000)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ofSeconds(5))
                .build()
        }
    )

    private suspend fun apiGet(url: String, block: HttpRequestBuilder.() -> Unit = {}): HttpResponse =
        rateLimiter.executeSuspendFunction {
            client.get(url) {
                block()
                url { parameters.append("access_key", apiKey) }
            }
        }

    private fun <T> Either<ClientError, T>.orCharacterNotFound(fallback: T): Either<ClientError, T> =
        fold(
            ifLeft = { error ->
                if (error is HttpError && error.status == 400 &&
                    error.body?.contains("Could not find requested character") == true
                ) Either.Right(fallback)
                else Either.Left(error)
            },
            ifRight = { Either.Right(it) }
        )
}
