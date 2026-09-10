package com.kos.sources.lol

import arrow.core.Either
import arrow.core.raise.either
import com.kos.clients.ClientError
import com.kos.clients.domain.*
import com.kos.clients.riot.RiotClient
import com.kos.clients.toSyncProcessingError
import com.kos.common.DynamicCache
import com.kos.common.WithLogger
import com.kos.common._fold
import com.kos.common.error.ServiceError
import com.kos.common.error.SyncProcessingError
import com.kos.datacache.DataCache
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.domain.Entity
import com.kos.entities.domain.LolEntity
import com.kos.entities.sync.EntitySynchronizer
import com.kos.views.Game
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import java.time.Duration
import java.time.OffsetDateTime

class LolEntitySynchronizer(
    private val dataCacheRepository: DataCacheRepository,
    private val riotClient: RiotClient,
) : EntitySynchronizer, WithLogger("LolEntitySynchronizer") {

    override val game: Game = Game.LOL
    override val json: Json = Json {
        serializersModule = SerializersModule {
            polymorphic(Data::class) {
                subclass(RiotData::class, RiotData.serializer())
            }
        }
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    override suspend fun synchronize(entities: List<Entity>): List<ServiceError> =
        coroutineScope {
            val lolEntities = entities.filterIsInstance<LolEntity>()

            val dataChannel = Channel<DataCache>()
            val errorsChannel = Channel<ServiceError>()
            val errorsList = mutableListOf<ServiceError>()
            val matchCache = DynamicCache<Either<ServiceError, GetMatchResponse>>()

            val errorsCollector = launch {
                errorsChannel.consumeAsFlow().collect { error ->
                    logger.error(error.toString())
                    errorsList.add(error)
                }
            }

            val dataCollector = launch {
                dataChannel.consumeAsFlow()
                    .buffer(50)
                    .collect { data ->
                        dataCacheRepository.insert(listOf(data))
                        logger.info("Cached entity ${data.entityId}")
                    }
            }

            val start = OffsetDateTime.now()
            lolEntities.asFlow()
                .buffer(10)
                .collect { lolEntity ->
                    val result = cacheLolEntity(lolEntity, matchCache)
                    result.fold(
                        ifLeft = { error -> errorsChannel.send(error) },
                        ifRight = { (id, riotData) ->
                            dataChannel.send(
                                DataCache(
                                    id,
                                    json.encodeToString<Data>(riotData),
                                    OffsetDateTime.now(),
                                    Game.LOL
                                )
                            )
                        }
                    )
                }

            dataChannel.close()
            errorsChannel.close()

            errorsCollector.join()
            dataCollector.join()

            logger.info("Finished Caching Lol entities")
            logger.debug(
                "cached ${entities.size} entities in ${
                    Duration.between(start, OffsetDateTime.now()).toMinutes()
                } minutes"
            )
            logger.debug("dynamic match cache hit rate: ${"%.2f".format(matchCache.hitRate)}%")
            errorsList
        }

    private suspend fun cacheLolEntity(
        lolEntity: LolEntity,
        matchCache: DynamicCache<Either<ServiceError, GetMatchResponse>>
    ): Either<ServiceError, Pair<Long, RiotData>> =
        either {

            val newestDataCacheEntry: RiotData? =
                dataCacheRepository.get(lolEntity.id).maxByOrNull { it.inserted }?.let {
                    try {
                        json.decodeFromString<RiotData>(it.data)
                    } catch (e: Throwable) {
                        logger.debug(
                            "Couldn't deserialize entity ${lolEntity.id} " +
                                    "while trying to obtain newest cached record.\n${e.message}"
                        )
                        null
                    }
                }

            val leagues: List<LeagueEntryResponse> =
                execute("getLeagueEntriesByPUUID") {
                    riotClient.getLeagueEntriesByPUUID(lolEntity.puuid)
                }.bind()

            val leagueWithMatches: List<LeagueMatchData> =
                coroutineScope {
                    leagues.map { leagueEntry ->
                        async {
                            val lastMatchesForLeague: List<String> =
                                execute("getMatchesByPuuid") {
                                    riotClient.getMatchesByPuuid(lolEntity.puuid, leagueEntry.queueType.toInt())
                                }.bind()

                            val matchesToRequest = newestDataCacheEntry._fold(
                                left = { lastMatchesForLeague },
                                right = { record ->
                                    lastMatchesForLeague
                                        .filterNot { id ->
                                            record.leagues[leagueEntry.queueType]?.matches?.map { it.id }
                                                ?.contains(id)._fold({ false }, { it })
                                        }
                                }
                            )

                            val matchResponses: List<GetMatchResponse> = matchesToRequest.map { matchId ->
                                matchCache.get(matchId) {
                                    execute("getMatchById") {
                                        riotClient.getMatchById(matchId)
                                    }
                                }.bind()
                            }

                            LeagueMatchData(
                                leagueEntry,
                                matchResponses,
                                newestDataCacheEntry?.leagues?.get(leagueEntry.queueType)?.matches.orEmpty()
                                    .filter { lastMatchesForLeague.contains(it.id) }
                            )
                        }
                    }.awaitAll()
                }

            Pair(lolEntity.id, RiotData.Companion.apply(lolEntity, leagueWithMatches))
        }

    private suspend fun <A> execute(
        operation: String,
        block: suspend () -> Either<ClientError, A>
    ): Either<ServiceError, A> =
        try {
            block().mapLeft { it.toSyncProcessingError(operation) }
        } catch (e: RequestNotPermitted) {
            Either.Left(SyncProcessingError(operation, "Rate limiter timeout: ${e.message}"))
        }
}