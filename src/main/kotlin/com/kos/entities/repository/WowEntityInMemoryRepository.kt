package com.kos.entities.repository

import arrow.core.Either
import com.kos.common.InMemoryRepository
import com.kos.common.WithState
import com.kos.common.error.RepositoryError
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.domain.*
import java.time.OffsetDateTime

class WowEntityInMemoryRepository(
    private val dataCacheRepository: DataCacheInMemoryRepository,
    private val nextId: suspend () -> Long
) :
    GameEntityRepository,
    WithState<List<WowEntity>, WowEntityInMemoryRepository>,
    InMemoryRepository {

    private val entities: MutableList<WowEntity> = mutableListOf()

    override suspend fun insert(entities: List<InsertEntityRequest>): Either<RepositoryError, List<Entity>> {
        val initial = this.entities.toList()
        val inserted = entities.fold(listOf<Entity>()) { acc, it ->
            when (it) {
                is WowEntityRequest -> {
                    val normalized = it.copy(name = it.name.lowercase())
                    if (this.entities.any { entity -> normalized.same(entity) }) {
                        this.entities.clear()
                        this.entities.addAll(initial)
                        return Either.Left(RepositoryError("Error inserting entity $it"))
                    }
                    val entity = normalized.toEntity(nextId())
                    this.entities.add(entity)
                    acc + entity
                }

                else -> {
                    this.entities.clear()
                    this.entities.addAll(initial)
                    return Either.Left(RepositoryError("Error inserting entity $it"))
                }
            }
        }
        return Either.Right(inserted)
    }

    override suspend fun update(id: Long, entity: InsertEntityRequest): Either<RepositoryError, Int> =
        when (entity) {
            is WowEntityRequest -> {
                val index = entities.indexOfFirst { it.id == id }
                val current = entities[index]
                entities.removeAt(index)
                entities.add(
                    index,
                    WowEntity(id, entity.name.lowercase(), entity.region, entity.realm, entity.blizzardId ?: current.blizzardId)
                )
                Either.Right(1)
            }

            else -> Either.Left(RepositoryError("error updating $id $entity for WOW"))
        }

    override suspend fun get(id: Long): Entity? = entities.find { it.id == id }

    override suspend fun get(request: EntityRequest): Entity? {
        request as WowEntityRequest
        return entities.find {
            it.name == request.name.lowercase() &&
                    it.realm == request.realm &&
                    it.region == request.region
        }
    }

    override suspend fun get(entity: InsertEntityRequest): Entity? {
        val normalized = when (entity) {
            is WowEntityRequest -> entity.copy(name = entity.name.lowercase())
            else -> entity
        }
        return entities.find { normalized.same(it) }
    }

    override suspend fun getAll(): List<Entity> = entities

    override suspend fun getOlderThan(olderThanMinutes: Long, maxEntities: Int): List<Entity> {
        val threshold = OffsetDateTime.now().minusMinutes(olderThanMinutes)
        return entitiesOlderThan(entities, dataCacheRepository, threshold, maxEntities)
    }

    fun deleteIfPresent(id: Long): Boolean {
        val index = entities.indexOfFirst { it.id == id }
        if (index == -1) return false
        entities.removeAt(index)
        return true
    }

    override fun clear() = entities.clear()

    override suspend fun state(): List<WowEntity> = entities.toList()

    override suspend fun withState(initialState: List<WowEntity>): WowEntityInMemoryRepository {
        entities.addAll(initialState.map { it.copy(name = it.name.lowercase()) })
        return this
    }
}
