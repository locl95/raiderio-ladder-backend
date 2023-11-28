package com.kos.views

import com.kos.characters.CharacterRequest
import com.kos.characters.CharactersService
import com.kos.characters.CharactersTestHelper.basicCharacter
import com.kos.characters.repository.CharactersInMemoryRepository
import com.kos.datacache.DataCacheService
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.eventsourcing.events.Event
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.views.ViewsTestHelper.basicSimpleView
import com.kos.views.ViewsTestHelper.basicView
import com.kos.views.ViewsTestHelper.id
import com.kos.views.ViewsTestHelper.name
import com.kos.views.ViewsTestHelper.owner
import com.kos.views.repository.ViewsInMemoryRepository
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class ViewsServiceTest {
    private val raiderIoClient = RaiderIoMockClient()

    @Test
    fun `i can get own views`() {
        runBlocking {
            val viewsRepository = ViewsInMemoryRepository().withState(listOf(basicSimpleView))
            val charactersRepository = CharactersInMemoryRepository().withState(listOf())
            val charactersService = CharactersService(charactersRepository)
            val dataCacheRepository = DataCacheInMemoryRepository().withState(listOf())
            val dataCacheService = DataCacheService(dataCacheRepository, raiderIoClient)
            val eventStore = EventStoreInMemory()
            val service = ViewsService(viewsRepository, eventStore, charactersService, dataCacheService, raiderIoClient)
            assertEquals(listOf(basicSimpleView), service.getOwnViews(owner))
        }
    }

    @Test
    fun `i can get a simple view`() {
        runBlocking {
            val viewsRepository = ViewsInMemoryRepository().withState(listOf(basicSimpleView))
            val charactersRepository = CharactersInMemoryRepository().withState(listOf())
            val charactersService = CharactersService(charactersRepository)
            val dataCacheRepository = DataCacheInMemoryRepository().withState(listOf())
            val dataCacheService = DataCacheService(dataCacheRepository, raiderIoClient)
            val eventStore = EventStoreInMemory()
            val service = ViewsService(viewsRepository, eventStore, charactersService, dataCacheService, raiderIoClient)
            assertEquals(basicSimpleView, service.getSimple("1"))
        }
    }

    @Test
    fun `i can create views`() {
        runBlocking {
            val viewsRepository = ViewsInMemoryRepository().withState(listOf())
            val charactersRepository = CharactersInMemoryRepository().withState(listOf())
            val charactersService = CharactersService(charactersRepository)
            val dataCacheRepository = DataCacheInMemoryRepository().withState(listOf())
            val dataCacheService = DataCacheService(dataCacheRepository, raiderIoClient)
            val eventStore = EventStoreInMemory()
            val service = ViewsService(viewsRepository, eventStore, charactersService, dataCacheService, raiderIoClient)
            assertTrue(viewsRepository.state().isEmpty())
            assertTrue(service.create(owner, ViewRequest(name, listOf())).isRight())
            assertTrue(viewsRepository.state().size == 1)
            assertTrue(viewsRepository.state().all { it.owner == owner })
        }
    }

    @Test
    fun `i cant create more than maximum views`() {
        runBlocking {
            val viewsRepository =
                ViewsInMemoryRepository().withState(listOf(basicSimpleView, basicSimpleView.copy(id = "2")))
            val charactersRepository = CharactersInMemoryRepository().withState(listOf())
            val charactersService = CharactersService(charactersRepository)
            val dataCacheRepository = DataCacheInMemoryRepository().withState(listOf())
            val dataCacheService = DataCacheService(dataCacheRepository, raiderIoClient)
            val eventStore = EventStoreInMemory()
            val service = ViewsService(viewsRepository, eventStore, charactersService, dataCacheService, raiderIoClient)

            assertTrue(viewsRepository.state().size == 2)
            assertTrue(viewsRepository.state().all { it.owner == owner })
            assertTrue(service.create(owner, ViewRequest(name, listOf())).isLeft())
            assertTrue(viewsRepository.state().size == 2)
        }
    }

    @Test
    fun `i can edit a view modifying more than one character`(): Unit {
        runBlocking {
            val viewsRepository =
                ViewsInMemoryRepository().withState(listOf(basicSimpleView.copy(characterIds = listOf(1))))
            val charactersRepository = CharactersInMemoryRepository().withState(listOf())
            val charactersService = CharactersService(charactersRepository)
            val dataCacheRepository = DataCacheInMemoryRepository().withState(listOf())
            val dataCacheService = DataCacheService(dataCacheRepository, raiderIoClient)
            val eventStore = EventStoreInMemory()
            val service = ViewsService(viewsRepository, eventStore, charactersService, dataCacheService, raiderIoClient)
            assertTrue(viewsRepository.state().all { it.characterIds.size == 1 })
            assertEquals(
                service.edit(
                    id, ViewRequest(
                        name, listOf(
                            CharacterRequest("a", "r", "r"),
                            CharacterRequest("b", "r", "r"),
                            CharacterRequest("c", "r", "r"),
                            CharacterRequest("d", "r", "r")
                        )
                    )
                ), ViewModified(id, listOf(1, 2, 3, 4))
            )
            assertTrue(viewsRepository.state().all { it.characterIds.size == 4 })
        }
    }

    @Test
    fun `i can delete a view`(): Unit {
        runBlocking {
            val viewsRepository =
                ViewsInMemoryRepository().withState(listOf(basicSimpleView.copy(characterIds = listOf(1))))
            val charactersRepository = CharactersInMemoryRepository().withState(listOf())
            val charactersService = CharactersService(charactersRepository)
            val dataCacheRepository = DataCacheInMemoryRepository().withState(listOf())
            val dataCacheService = DataCacheService(dataCacheRepository, raiderIoClient)
            val eventStore = EventStoreInMemory()
            val service = ViewsService(viewsRepository, eventStore, charactersService, dataCacheService, raiderIoClient)
            assertTrue(viewsRepository.state().size == 1)
            assertEquals(service.delete("1"), ViewDeleted("1"))
            assertTrue(viewsRepository.state().isEmpty())
        }
    }

    @Test
    fun `i can get data for a view and store an event`() {
        runBlocking {
            val viewsRepository = ViewsInMemoryRepository().withState(listOf(basicSimpleView))
            val charactersRepository = CharactersInMemoryRepository()
            val charactersService = CharactersService(charactersRepository)
            val dataCacheRepository = DataCacheInMemoryRepository()
            val dataCacheService = DataCacheService(dataCacheRepository, raiderIoClient)
            val eventStore = EventStoreInMemory()
            val service = ViewsService(viewsRepository, eventStore, charactersService, dataCacheService, raiderIoClient)

            val result = service.getData(basicView.copy(characters = listOf(basicCharacter)))
            val eventStoreState = eventStore.state()

            assertEquals(1, eventStoreState.size)
            val event = eventStoreState.first()


            result.fold({ fail(it.error()) }) {
                assertEquals(1, it.size)
                assertEquals(basicCharacter.id, it.first().id)
                assertEquals(basicCharacter.name, it.first().name)
                assertEquals(1, event.version)
                val expectedEvent = Event("character/1", "RaiderioDataReceived", it.first())
                assertEquals(expectedEvent, event.event)
            }
        }
    }
}