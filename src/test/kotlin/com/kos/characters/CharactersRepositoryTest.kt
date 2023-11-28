package com.kos.characters

import com.kos.characters.repository.CharactersDatabaseRepository
import com.kos.characters.repository.CharactersInMemoryRepository
import com.kos.characters.repository.CharactersRepository
import com.kos.common.DatabaseFactory
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

abstract class CharactersRepositoryTest {

    abstract val repository: CharactersRepository

    @BeforeTest
    abstract fun beforeEach()

    @Test
    fun `given an empty repository i can insert characters`() {
        runBlocking {
            val request = CharacterRequest(
                CharactersTestHelper.basicCharacter.name,
                CharactersTestHelper.basicCharacter.region,
                CharactersTestHelper.basicCharacter.realm
            )
            val expected = listOf(CharactersTestHelper.basicCharacter)

            assertEquals(expected, repository.insert(listOf(request)))
        }
    }
}

class CharactersInMemoryRepositoryTest : CharactersRepositoryTest() {
    override val repository = CharactersInMemoryRepository()
    override fun beforeEach() {
        repository.clear()
    }
}

class CharactersDatabaseRepositoryTest : CharactersRepositoryTest() {
    override val repository = CharactersDatabaseRepository()
    override fun beforeEach() {
        DatabaseFactory.init(mustClean = true)
    }
}
