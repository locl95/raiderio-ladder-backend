package com.kos.characters.repository

import arrow.core.Either
import com.kos.characters.Character
import com.kos.characters.CharacterRequest
import com.kos.common.WithState
import com.kos.views.InsertCharacterError
import java.sql.SQLException

interface CharactersRepository: WithState<List<Character>, CharactersRepository> {

    //TODO: insert should be on conflict do nothing so we can avoid the select all + diff on service
    suspend fun insert(characters: List<CharacterRequest>): Either<InsertCharacterError, List<Character>>
    suspend fun get(id: Long): Character?
    suspend fun get(): List<Character>
}