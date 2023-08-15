package com.kos.datacache.repository

import com.kos.common.DatabaseFactory.dbQuery
import com.kos.datacache.DataCache
import org.jetbrains.exposed.sql.*
import java.time.OffsetDateTime

class DataCacheDatabaseRepository : DataCacheRepository {

    object DataCaches : Table() {
        val characterId = long("character_id")
        val data = text("data")
        val inserted = text("inserted")

        override val primaryKey = PrimaryKey(characterId)
    }

    private fun resultRowToDataCache(row: ResultRow) = DataCache(
        row[DataCaches.characterId],
        row[DataCaches.data],
        OffsetDateTime.parse(row[DataCaches.inserted]),
    )

    override suspend fun insert(dataCache: DataCache): Boolean {
        dbQuery {
            DataCaches.insert {
                it[characterId] = dataCache.characterId
                it[data] = dataCache.data
                it[inserted] = dataCache.inserted.toString()
            }
        }
        return true
    }

    override suspend fun update(dataCache: DataCache): Boolean {
        dbQuery {
            DataCaches.update {
                it[characterId] = dataCache.characterId
                it[data] = dataCache.data
                it[inserted] = dataCache.inserted.toString()
            }
        }
        return true
    }

    override suspend fun get(characterId: Long): DataCache? =
        dbQuery {
            DataCaches.select { DataCaches.characterId.eq(characterId) }.map { resultRowToDataCache(it) }.singleOrNull()
        }

    override suspend fun state(): List<DataCache> = dbQuery { DataCaches.selectAll().map { resultRowToDataCache(it) } }
}