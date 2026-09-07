package com.kos.entities.domain

import com.kos.clients.domain.Data
import com.kos.common.error.ServiceError
import com.kos.eventsourcing.events.Operation
import com.kos.views.Game
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = WithAliasSerializer::class)
data class WithAlias<T>(
    val value: T,
    val alias: String?
)

@Serializable
sealed interface Entity {
    val id: Long
    val name: String
    fun toRequest(): EntityRequest
}

@Polymorphic
@Serializable
sealed interface EntityRequest {
    val name: String
    val alias: String?
    fun same(other: Entity): Boolean
    fun toResponse(): EntityResponse
}

@Serializable(with = EntityResponseSerializer::class)
sealed interface EntityResponse {
    val name: String
}

sealed interface InsertEntityRequest {
    val name: String
    fun toEntity(id: Long): Entity
    fun same(other: Entity): Boolean
    fun toRequest(): EntityRequest
}

typealias EntityWithAlias = WithAlias<Entity>
typealias InsertEntityRequestWithAlias = WithAlias<out InsertEntityRequest>

fun <T> T.withAlias(alias: String?): WithAlias<T> = WithAlias(this, alias)

@Serializable
data class EntityDataResponse(val data: Data?, val operation: Operation?)

@Serializable
data class EntitiesExistRequest(
    val entities: List<EntityRequest>,
    val game: Game
)

@Serializable
data class EntitiesExistResponse(
    val exist: List<EntityResponse>,
    val nonExisting: List<EntityResponse>,
    val unchecked: List<EntityResponse>
)

@Serializable
data class GuildPayload(
    val name: String,
    val realm: String,
    val region: String,
    val blizzardId: Long
)

@Serializable
data class GuildExistsRequest(
    val name: String,
    val region: String,
    val realm: String
)

@Serializable
data class GuildExistsResponse(
    val guild: GuildPayload?
)

data class ResolvedEntities(
    val entities: List<Pair<InsertEntityRequest, String?>>,
    val existing: List<Pair<Entity, String?>>,
    val unchecked: List<Pair<EntityRequest, ServiceError>> = emptyList(),
    val guild: GuildPayload?,
)

class WithAliasSerializer<T>(private val valueSerializer: KSerializer<T>) : KSerializer<WithAlias<T>> {
    override val descriptor: SerialDescriptor = valueSerializer.descriptor

    override fun serialize(encoder: Encoder, value: WithAlias<T>) {
        val jsonEncoder = encoder as JsonEncoder
        val jsonObject = JsonObject(
            buildMap {
                putAll(jsonEncoder.json.encodeToJsonElement(valueSerializer, value.value).jsonObject)
                put("alias", JsonPrimitive(value.alias))
            }
        )
        jsonEncoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): WithAlias<T> {
        val jsonDecoder = decoder as JsonDecoder
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        val alias = jsonObject["alias"]?.jsonPrimitive?.contentOrNull
        val entityJson = JsonObject(jsonObject - "alias")

        val value = jsonDecoder.json.decodeFromJsonElement(valueSerializer, entityJson)

        return WithAlias(value, alias)
    }
}

object EntityResponseSerializer : KSerializer<EntityResponse> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("EntityResponse")

    override fun serialize(encoder: Encoder, value: EntityResponse) {
        when (value) {
            is WowEntityResponse -> encoder.encodeSerializableValue(WowEntityResponse.serializer(), value)
            is LolEntityResponse -> encoder.encodeSerializableValue(LolEntityResponse.serializer(), value)
        }
    }

    override fun deserialize(decoder: Decoder): EntityResponse {
        val jsonDecoder = decoder as JsonDecoder
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        return when {
            "region" in jsonObject -> jsonDecoder.json.decodeFromJsonElement(WowEntityResponse.serializer(), jsonObject)
            "tag" in jsonObject -> jsonDecoder.json.decodeFromJsonElement(LolEntityResponse.serializer(), jsonObject)
            else -> throw SerializationException("Cannot determine EntityResponse subtype from $jsonObject")
        }
    }
}