package com.kos.characters

import arrow.core.Either
import com.kos.characters.repository.CharactersRepository
import com.kos.common.collect
import com.kos.common.split
import com.kos.raiderio.RaiderIoClient
import com.kos.views.InsertCharacterError
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.sql.SQLException

data class CharactersService(
    private val charactersRepository: CharactersRepository,
    private val raiderioClient: RaiderIoClient
) {
    suspend fun createAndReturnIds(characters: List<CharacterRequest>): Either<InsertCharacterError, List<Long>> {
        fun splitExistentAndNew(
            charactersRequest: List<CharacterRequest>,
            currentCharacters: List<Character>
        ): List<Either<Character, CharacterRequest>> {
            return charactersRequest.fold(emptyList()) { acc, character ->
                when (val maybeCharacter =
                    currentCharacters.find { character.same(it) }) {
                    null -> acc + Either.Right(character)
                    else -> acc + Either.Left(maybeCharacter)
                }
            }
        }

        val currentCharacters = charactersRepository.get()
        val existentAndNew = splitExistentAndNew(characters, currentCharacters).split()

        val newThatExist = coroutineScope {
            existentAndNew.second.map { async { it to raiderioClient.exists(it) } }.awaitAll()
        }.collect({ it.second }) { it.first }

        return charactersRepository.insert(newThatExist)
            .map { list -> list.map { it.id } + existentAndNew.first.map { it.id } }
    }

    suspend fun get(id: Long): Character? = charactersRepository.get(id)
    suspend fun get(): List<Character> = charactersRepository.get()
}