package com.kos.characters.repository

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.kos.characters.Character
import com.kos.characters.CharacterRequest
import com.kos.common.DatabaseFactory.dbQuery
import com.kos.views.InsertCharacterError
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.SQLException

class CharactersDatabaseRepository : CharactersRepository {

    override suspend fun withState(initialState: List<Character>): CharactersDatabaseRepository {
        dbQuery {
            Characters.batchInsert(initialState) {
                this[Characters.id] = it.id
                this[Characters.name] = it.name
                this[Characters.realm] = it.realm
                this[Characters.region] = it.region
            }
        }
        return this
    }

    object Characters : Table() {
        val id = long("id")
        val name = text("name")
        val realm = text("realm")
        val region = text("region")

        override val primaryKey = PrimaryKey(id)
    }

    private fun resultRowToCharacter(row: ResultRow) = Character(
        row[Characters.id],
        row[Characters.name],
        row[Characters.region],
        row[Characters.realm]
    )

    override suspend fun insert(characters: List<CharacterRequest>): Either<InsertCharacterError, List<Character>> {
        return dbQuery {
            val charsToInsert = characters.map {
                Character(selectNextId(), it.name, it.region, it.realm)
            }
            try {
                val insertedCharacters = Characters.batchInsert(charsToInsert) {
                    this[Characters.id] = it.id
                    this[Characters.name] = it.name
                    this[Characters.region] = it.region
                    this[Characters.realm] = it.realm
                }.map { resultRowToCharacter(it) }
                Either.Right(insertedCharacters)
            } catch (e: SQLException) {
                Either.Left(InsertCharacterError(e.message ?: e.stackTraceToString()))
            }
        }
    }

    override suspend fun get(id: Long): Character? {
        return dbQuery {
            Characters.select { Characters.id.eq(id) }.singleOrNull()?.let {
                resultRowToCharacter(it)
            }
        }
    }

    override suspend fun get(): List<Character> =
        dbQuery { Characters.selectAll().map { resultRowToCharacter(it) } }

    override suspend fun state(): List<Character> {
        return dbQuery {
            Characters.selectAll().map { resultRowToCharacter(it) }
        }
    }

    suspend fun selectNextId(): Long =
        dbQuery {
            TransactionManager.current().exec("""select nextval('characters_ids') as id""") { rs ->
                if (rs.next()) rs.getLong("id")
                else -1
            }
        } ?: -1
}