package com.kos.sources.wow

import arrow.core.Either
import arrow.core.raise.either
import arrow.fx.coroutines.parMap
import com.kos.clients.ClientError
import com.kos.clients.domain.*
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.toSyncProcessingError
import com.kos.common.DynamicCache
import com.kos.common.WithLogger
import com.kos.common.error.ServiceError
import com.kos.common.error.SyncProcessingError
import com.kos.datacache.DataCache
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.domain.Entity
import com.kos.entities.domain.WowEntity
import com.kos.entities.sync.EntitySynchronizer
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonRepository
import com.kos.views.Game
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.OffsetDateTime

class WowEntitySynchronizer(
    private val dataCacheRepository: DataCacheRepository,
    private val raiderIoClient: RaiderIoClient,
    private val wowSeasonRepository: WowSeasonRepository,
    private val concurrency: Int = 10,
) : EntitySynchronizer, WithLogger("WowEntitySynchronizer") {

    override val game: Game = Game.WOW
    override val json: Json = Json {
        serializersModule = SerializersModule {
            polymorphic(Data::class) {
                subclass(RaiderIoData::class, RaiderIoData.serializer())
            }
        }
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override suspend fun synchronize(entities: List<Entity>): List<ServiceError> =
        coroutineScope {
            val wowEntities = entities.filterIsInstance<WowEntity>()

            val dataChannel = Channel<DataCache>()
            val errorsChannel = Channel<ServiceError>()
            val errorsList = mutableListOf<ServiceError>()
            val runDetailsCache = DynamicCache<Either<ServiceError, RunDetails>>()

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

            val currentSeasonSlug = wowSeasonRepository.getCurrentSeason()?.slug?.takeIf { it.isNotBlank() }
            if (currentSeasonSlug == null) {
                logger.warn("No current season found — cutoff and quantile will be skipped for this sync")
            }

            val cutoff = currentSeasonSlug?.let {
                executeClientCall("raiderIoCutoff") { raiderIoClient.cutoff(it) }
                    .onLeft { logger.warn("Failed to fetch cutoff, quantile will be null for this sync: ${it.error()}") }
                    .getOrNull()
            }

            val start = OffsetDateTime.now()
            wowEntities.asFlow()
                .parMap(concurrency = concurrency) { entity ->
                    synchronizeWowEntity(entity, currentSeasonSlug, cutoff, runDetailsCache)
                }
                .collect { result ->
                    result.fold(
                        ifLeft = { errorsChannel.send(it) },
                        ifRight = { dataChannel.send(it) }
                    )
                }

            errorsChannel.close()
            dataChannel.close()

            errorsCollector.join()
            dataCollector.join()

            logger.info("Finished Caching Wow entities")
            logger.debug(
                "cached ${entities.size} entities in ${
                    Duration.between(start, OffsetDateTime.now()).toMinutes()
                } minutes"
            )
            logger.debug("dynamic match cache hit rate: ${"%.2f".format(runDetailsCache.hitRate)}%")

            errorsList
        }


    private suspend fun synchronizeWowEntity(
        entity: WowEntity,
        season: String?,
        cutoff: RaiderIoCutoff?,
        runDetailsCache: DynamicCache<Either<ServiceError, RunDetails>>
    ): Either<ServiceError, DataCache> =
        either {

            val newestDataCacheEntry: RaiderIoData? = getNewestDataCacheEntry(entity.id)

            val raiderIoResponse = executeClientCall("raiderIoGet") {
                raiderIoClient.get(entity)
            }.bind()

            val quantile = getQuantile(cutoff, raiderIoResponse)

            val enrichedRuns = fetchRunDetails(
                raiderIoResponse.profile.mythicPlusBestRuns,
                season,
                runDetailsCache,
                newestDataCacheEntry
            )

            DataCache(
                entity.id,
                json.encodeToString<Data>(
                    raiderIoResponse.profile.toRaiderIoData(
                        entity.id,
                        quantile,
                        raiderIoResponse.specs,
                        enrichedRuns
                    )
                ),
                OffsetDateTime.now(),
                Game.WOW
            )
        }

    private suspend fun getNewestDataCacheEntry(entityId: Long): RaiderIoData? =
        dataCacheRepository.get(entityId)
            .maxByOrNull { it.inserted }
            ?.let {
                try {
                    json.decodeFromString<RaiderIoData>(it.data)
                } catch (e: SerializationException) {
                    logger.debug(
                        "Couldn't deserialize entity $entityId " +
                                "while trying to obtain newest cached record.\n${e.message}"
                    )
                    null
                }
            }

    private fun getQuantile(
        cutoff: RaiderIoCutoff?,
        raiderIoResponse: RaiderIoResponse
    ): Double? = cutoff?.let {
        BigDecimal(raiderIoResponse.profile.mythicPlusRanks.overall.region.toDouble() / it.totalPopulation * 100)
            .setScale(2, RoundingMode.HALF_EVEN)
            .toDouble()
    }

    private suspend fun fetchRunDetails(
        responseRuns: List<MythicPlusRun>,
        currentSeasonSlug: String?,
        runDetailsCache: DynamicCache<Either<ServiceError, RunDetails>>,
        newestDataCacheEntry: RaiderIoData?
    ): List<EnrichedMythicPlusRun> {
        if (currentSeasonSlug == null) {
            return responseRuns.map { EnrichedMythicPlusRun(it, null) }
        }

        val responseRunIds = responseRuns
            .map { it.runId }.toSet()
        val cachedRunIds = newestDataCacheEntry?.mythicPlusBestRuns
            ?.filter { it.details != null }
            ?.map { it.run.runId }?.toSet().orEmpty()

        val uncachedRuns = responseRuns.filterNot { it.runId in cachedRunIds }

        val fetchedRunDetails: Map<Long, RunDetails?> = uncachedRuns.associate { run ->
            run.runId to runDetailsCache.get(run.runId.toString()) {
                executeClientCall("raiderIoGetRunDetails") {
                    raiderIoClient.getRunDetails(currentSeasonSlug, run.runId.toString())
                }
            }.fold(
                ifLeft = { error ->
                    logger.warn("Failed to fetch run details for runId=${run.runId}: ${error.error()}")
                    null
                },
                ifRight = { it }
            )
        }

        val cachedRunDetails: Map<Long, RunDetails?> = newestDataCacheEntry?.mythicPlusBestRuns
            ?.filter { it.run.runId in responseRunIds }
            ?.associate { it.run.runId to it.details }
            .orEmpty()

        logger.debug("Not requesting ${cachedRunDetails.size} run details because they are already cached")

        return responseRuns.map { run ->
            EnrichedMythicPlusRun(run, fetchedRunDetails[run.runId] ?: cachedRunDetails[run.runId])
        }
    }

    private suspend fun <A> executeClientCall(
        operation: String,
        block: suspend () -> Either<ClientError, A>
    ): Either<ServiceError, A> =
        try {
            block().mapLeft { it.toSyncProcessingError(operation) }
        } catch (e: RequestNotPermitted) {
            Either.Left(SyncProcessingError(operation, "Rate limiter timeout: ${e.message}"))
        }
}
