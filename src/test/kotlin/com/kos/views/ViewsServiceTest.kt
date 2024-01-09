package com.kos.views

import com.kos.characters.CharacterRequest
import com.kos.characters.CharactersService
import com.kos.characters.repository.CharactersInMemoryRepository
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.datacache.DataCacheService
import com.kos.raiderio.RaiderIoClient
import com.kos.views.ViewsTestHelper.basicSimpleView
import com.kos.views.ViewsTestHelper.id
import com.kos.views.ViewsTestHelper.name
import com.kos.views.ViewsTestHelper.owner
import com.kos.views.repository.ViewsInMemoryRepository
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class ViewsServiceTest {
    private val raiderIoClient = mock(RaiderIoClient::class.java)

    @Test
    fun `i can get own views`() {
        runBlocking {
            val viewsRepository = ViewsInMemoryRepository().withState(listOf(basicSimpleView))
            val charactersRepository = CharactersInMemoryRepository().withState(listOf())
            val charactersService = CharactersService(charactersRepository, raiderIoClient)
            val dataCacheRepository = DataCacheInMemoryRepository().withState(listOf())
            val dataCacheService = DataCacheService(dataCacheRepository, raiderIoClient)
            val service = ViewsService(viewsRepository, charactersService, dataCacheService, raiderIoClient)
            assertEquals(listOf(basicSimpleView), service.getOwnViews(owner))
        }
    }

    @Test
    fun `i can get a simple view`() {
        runBlocking {
            val viewsRepository = ViewsInMemoryRepository().withState(listOf(basicSimpleView))
            val charactersRepository = CharactersInMemoryRepository().withState(listOf())
            val charactersService = CharactersService(charactersRepository, raiderIoClient)
            val dataCacheRepository = DataCacheInMemoryRepository().withState(listOf())
            val dataCacheService = DataCacheService(dataCacheRepository, raiderIoClient)
            val service = ViewsService(viewsRepository, charactersService, dataCacheService, raiderIoClient)
            assertEquals(basicSimpleView, service.getSimple("1"))
        }
    }

    @Test
    fun `i can create views`() {
        runBlocking {
            val viewsRepository = ViewsInMemoryRepository().withState(listOf())
            val charactersRepository = CharactersInMemoryRepository().withState(listOf())
            val charactersService = CharactersService(charactersRepository, raiderIoClient)
            val dataCacheRepository = DataCacheInMemoryRepository().withState(listOf())
            val dataCacheService = DataCacheService(dataCacheRepository, raiderIoClient)
            val service = ViewsService(viewsRepository, charactersService, dataCacheService, raiderIoClient)
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
            val charactersService = CharactersService(charactersRepository, raiderIoClient)
            val dataCacheRepository = DataCacheInMemoryRepository().withState(listOf())
            val dataCacheService = DataCacheService(dataCacheRepository, raiderIoClient)
            val service = ViewsService(viewsRepository, charactersService, dataCacheService, raiderIoClient)

            assertTrue(viewsRepository.state().size == 2)
            assertTrue(viewsRepository.state().all { it.owner == owner })
            assertTrue(service.create(owner, ViewRequest(name, listOf())).isLeft())
            assertTrue(viewsRepository.state().size == 2)
        }
    }

    @Test
    fun `i can edit a view modifying more than one character`(): Unit {
        runBlocking {

            val request1 = CharacterRequest("a", "r", "r")
            val request2 = CharacterRequest("b", "r", "r")
            val request3 = CharacterRequest("c", "r", "r")
            val request4 = CharacterRequest("d", "r", "r")

            `when`(raiderIoClient.exists(request1)).thenReturn(true)
            `when`(raiderIoClient.exists(request2)).thenReturn(true)
            `when`(raiderIoClient.exists(request3)).thenReturn(true)
            `when`(raiderIoClient.exists(request4)).thenReturn(true)

            val viewsRepository =
                ViewsInMemoryRepository().withState(listOf(basicSimpleView.copy(characterIds = listOf(1))))
            val charactersRepository = CharactersInMemoryRepository().withState(listOf())
            val charactersService = CharactersService(charactersRepository, raiderIoClient)
            val dataCacheRepository = DataCacheInMemoryRepository().withState(listOf())
            val dataCacheService = DataCacheService(dataCacheRepository, raiderIoClient)
            val service = ViewsService(viewsRepository, charactersService, dataCacheService, raiderIoClient)
            assertTrue(viewsRepository.state().all { it.characterIds.size == 1 })

            service.edit(
                id, ViewRequest(name, listOf(request1, request2, request3, request4))
            ).fold({ fail() }) { assertEquals(ViewModified(id, listOf(1, 2, 3, 4)), it) }

            assertTrue(viewsRepository.state().all { it.characterIds.size == 4 })
        }
    }

    @Test
    fun `i can delete a view`(): Unit {
        runBlocking {
            val viewsRepository =
                ViewsInMemoryRepository().withState(listOf(basicSimpleView.copy(characterIds = listOf(1))))
            val charactersRepository = CharactersInMemoryRepository().withState(listOf())
            val charactersService = CharactersService(charactersRepository, raiderIoClient)
            val dataCacheRepository = DataCacheInMemoryRepository().withState(listOf())
            val dataCacheService = DataCacheService(dataCacheRepository, raiderIoClient)
            val service = ViewsService(viewsRepository, charactersService, dataCacheService, raiderIoClient)
            assertTrue(viewsRepository.state().size == 1)
            assertEquals(service.delete("1"), ViewDeleted("1"))
            assertTrue(viewsRepository.state().isEmpty())
        }
    }
}