package com.kos.characters

import com.kos.characters.CharactersTestHelper.basicCharacter
import com.kos.characters.repository.CharactersInMemoryRepository
import com.kos.raiderio.RaiderIoClient
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class CharactersServiceTest {
    private val raiderIoClient = mock(RaiderIoClient::class.java)

    @Test
    fun `inserting two characters over an empty repository returns the ids of both new characters`() {
        runBlocking {
            val request1 = CharacterRequest(basicCharacter.name, basicCharacter.region, basicCharacter.realm)
            val request2 = CharacterRequest("kakarøna", basicCharacter.region, basicCharacter.realm)

            `when`(raiderIoClient.exists(request1)).thenReturn(true)
            `when`(raiderIoClient.exists(request2)).thenReturn(true)

            val charactersRepository = CharactersInMemoryRepository()
            val charactersService = CharactersService(charactersRepository, raiderIoClient)


            val request = listOf(request1, request2)
            val expected: List<Long> = listOf(1, 2)

            charactersService.createAndReturnIds(request).fold({ fail() }) { assertEquals(expected, it) }

        }
    }

    @Test
    fun `inserting a character that does not exist does not get inserted`() {
        runBlocking {
            val request1 = CharacterRequest(basicCharacter.name, basicCharacter.region, basicCharacter.realm)
            val request2 = CharacterRequest("kakarøna", basicCharacter.region, basicCharacter.realm)

            `when`(raiderIoClient.exists(request1)).thenReturn(true)
            `when`(raiderIoClient.exists(request2)).thenReturn(false)

            val charactersRepository = CharactersInMemoryRepository()
            val charactersService = CharactersService(charactersRepository, raiderIoClient)


            val request = listOf(request1, request2)
            val expected: List<Long> = listOf(1)
            charactersService.createAndReturnIds(request).fold({ fail() }) { assertEquals(expected, it) }

        }
    }
}