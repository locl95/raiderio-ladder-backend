package com.kos.characters.repository

import arrow.core.Either
import com.kos.characters.Character
import com.kos.characters.CharacterRequest
import com.kos.common.InMemoryRepository
import com.kos.views.InsertCharacterError
import java.sql.SQLException

class CharactersInMemoryRepository : CharactersRepository, InMemoryRepository {
    val characters: MutableList<Character> = mutableListOf()

    private fun nextId(): Long {
        return if (characters.isEmpty()) 1
        else characters.map { it.id }.maxBy { it } + 1
    }

    override suspend fun insert(characters: List<CharacterRequest>): Either<InsertCharacterError, List<Character>> {
        val initialStateCharacters = this.characters
        characters.forEach {
            if (this.characters.any { character -> it.same(character) }) {
                this.characters.clear()
                this.characters.addAll(initialStateCharacters)
                return Either.Left(InsertCharacterError("Error inserting character $it"))
            }
            this.characters.add(it.toCharacter(nextId()))
        }
        return Either.Right(this.characters)
    }

    override suspend fun get(id: Long): Character? = characters.find { it.id == id }
    override suspend fun get(): List<Character> = characters

    override suspend fun state(): List<Character> {
        return characters
    }

    override suspend fun withState(initialState: List<Character>): CharactersInMemoryRepository {
        characters.addAll(initialState)
        return this
    }

    override fun clear() {
        characters.clear()
    }

}