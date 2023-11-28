package com.kos

import com.kos.characters.CharactersService
import com.kos.datacache.DataCacheService
import com.kos.eventsourcing.events.repository.EventStore
import com.kos.eventsourcing.subscriptions.EventSubscription
import com.kos.eventsourcing.subscriptions.repository.SubscriptionsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

data class CacheDataTask(
    val dataCacheService: DataCacheService,
    val charactersService: CharactersService,
    val eventStore: EventStore,
    val subscriptionsRepository: SubscriptionsRepository,
    val coroutineScope: CoroutineScope
) : Runnable {

    private val logger = LoggerFactory.getLogger(CacheDataTask::class.java)

    private val eventSubscription = EventSubscription("data-cache", eventStore, subscriptionsRepository) {
        eventWithVersion -> println(eventWithVersion)
    }

    override fun run() {
        coroutineScope.launch {
            logger.info("Running filling cache data task")
            eventSubscription.processPendingEvents()
            val characters = charactersService.get()
            dataCacheService.cache(characters)
            val deletedRecords = dataCacheService.clear()
            logger.info("Deleted $deletedRecords cached records")
        }

    }
}