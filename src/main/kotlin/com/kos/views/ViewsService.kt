package com.kos.views

import arrow.core.Either
import arrow.core.sequence
import arrow.core.traverse
import com.kos.characters.CharactersService
import com.kos.common.JsonParseError
import com.kos.datacache.DataCacheService
import com.kos.eventsourcing.events.Event
import com.kos.eventsourcing.events.repository.EventStore
import com.kos.raiderio.RaiderIoClient
import com.kos.raiderio.RaiderIoData
import com.kos.views.repository.ViewsRepository
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class ViewsService(
    private val viewsRepository: ViewsRepository,
    private val eventStore: EventStore,
    private val charactersService: CharactersService,
    private val dataCacheService: DataCacheService,
    private val raiderIoClient: RaiderIoClient
) {

    private val maxNumberOfViews: Int = 2

    suspend fun getOwnViews(owner: String): List<SimpleView> = viewsRepository.getOwnViews(owner)
    suspend fun getViews(): List<SimpleView> = viewsRepository.getViews()
    suspend fun get(id: String): View? {
        return when (val simpleView = viewsRepository.get(id)) {
            null -> null
            else -> {
                View(simpleView.id, simpleView.name, simpleView.owner, simpleView.characterIds.mapNotNull {
                    charactersService.get(it)
                })
            }
        }
    }

    suspend fun getSimple(id: String): SimpleView? = viewsRepository.get(id)

    suspend fun create(owner: String, request: ViewRequest): Either<TooMuchViews, ViewModified> {
        if (viewsRepository.getOwnViews(owner).size >= maxNumberOfViews) return Either.Left(TooMuchViews())
        val characterIds = charactersService.createAndReturnIds(request.characters)
        return Either.Right(viewsRepository.create(request.name, owner, characterIds))
    }

    suspend fun edit(id: String, request: ViewRequest): ViewModified {
        val characters = charactersService.createAndReturnIds(request.characters)
        return viewsRepository.edit(id, request.name, characters)
    }

    suspend fun delete(id: String): ViewDeleted {
        return viewsRepository.delete(id)
    }

    suspend fun getData(view: View): Either<JsonParseError, List<RaiderIoData>> = coroutineScope {
        val eitherJsonErrorOrData = when (val cutoffOrError = raiderIoClient.cutoff()) {
            is Either.Left -> Either.Left(cutoffOrError.value)
            is Either.Right -> {
                val eitherErrorOrResponse = view.characters.map { char ->
                    async {
                        raiderIoClient.get(char).map {
                            Pair(char.id, it)
                        }
                    }
                }.awaitAll().sequence()
                when (eitherErrorOrResponse) {
                    is Either.Left -> eitherErrorOrResponse
                    is Either.Right -> {
                        eitherErrorOrResponse.value.traverse {
                            val quantile =
                                BigDecimal(it.second.profile.mythicPlusRanks.overall.region.toDouble() / cutoffOrError.value.totalPopulation * 100).setScale(
                                    2,
                                    RoundingMode.HALF_EVEN
                                )
                            val raiderIoData = it.second.profile.toRaiderIoData(
                                it.first,
                                quantile.toDouble(),
                                it.second.specs
                            )
                            eventStore.save(Event("character/${it.first}", "RaiderioDataReceived", raiderIoData))
                            Either.Right(raiderIoData)
                        }
                    }
                }
            }
        }
        eitherJsonErrorOrData
    }

    suspend fun getCachedData(simpleView: SimpleView) = dataCacheService.getData(simpleView)
}
