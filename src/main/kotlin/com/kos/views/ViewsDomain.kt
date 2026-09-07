package com.kos.views

import arrow.core.Either
import com.kos.clients.domain.Data
import com.kos.common.OffsetDateTimeSerializer
import com.kos.common.error.InvalidGameType
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.EntityWithAlias
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
enum class Game {
    WOW {
        override fun toString(): String = "wow"
    },
    LOL {
        override fun toString(): String = "lol"
    },
    WOW_HC {
        override fun toString(): String = "wow_hc"
    };

    companion object {
        fun fromString(value: String): Either<InvalidGameType, Game> = when (value) {
            "wow" -> Either.Right(WOW)
            "lol" -> Either.Right(LOL)
            "wow_hc" -> Either.Right(WOW_HC)
            else -> Either.Left(InvalidGameType(value))
        }
    }
}

@Serializable
sealed interface ViewExtraArguments

@Serializable
enum class GuildArgs {
    EXISTENCE,
    RESOLVE
}

@Serializable
data class WowExtraArguments(
    val season: Int,
    val guild: GuildArgs? = null
) : ViewExtraArguments

@Serializable
data class WowHardcoreExtraArguments(
    val isGuild: Boolean
) : ViewExtraArguments

@Serializable
data class GetViewsResponse(val metadata: ViewMetadata? = null, val records: List<SimpleView>)

@Serializable
data class ViewMetadata(val totalCount: Int?)

data class GetViewsQuery(
    val game: Game?,
    val featured: Boolean,
    val page: Int?,
    val limit: Int?,
    val includeMetadata: Boolean
)

@Serializable
data class SimpleView(
    val id: String,
    val name: String,
    val owner: String,
    val published: Boolean,
    val entitiesIds: List<Long>,
    val game: Game,
    val featured: Boolean,
    val extraArguments: ViewExtraArguments? = null,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val lastSyncedAt: OffsetDateTime? = null
)

@Serializable
data class View(
    val id: String,
    val name: String,
    val owner: String,
    val published: Boolean,
    val entities: List<EntityWithAlias>,
    val game: Game,
    val featured: Boolean,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val lastSyncedAt: OffsetDateTime? = null
)

//TODO: We need to decode/encode the entity request based on Game.
//TODO: Right now we are using the type discriminator and that's cringe as fuck.
@Serializable
data class ViewRequest(
    val name: String,
    val published: Boolean,
    val entities: List<EntityRequest>,
    val game: Game,
    val featured: Boolean,
    val extraArguments: ViewExtraArguments? = null
)

@Serializable
data class ViewPatchRequest(
    val name: String? = null,
    val published: Boolean? = null,
    val entities: List<EntityRequest>? = null,
    val game: Game,
    val featured: Boolean? = null
)

@Serializable
sealed interface ViewResult {
    val isSuccess: Boolean
}

@Serializable
data class ViewModified(
    val viewId: String,
    val name: String,
    val published: Boolean,
    val entities: List<Long>,
    val featured: Boolean
) :
    ViewResult {
    override val isSuccess: Boolean = true
}

@Serializable
data class ViewPatched(
    val viewId: String,
    val name: String?,
    val published: Boolean?,
    val entities: List<Long>?,
    val featured: Boolean?
) :
    ViewResult {
    override val isSuccess: Boolean = true
}

@Serializable
data class ViewData(val viewName: String, val data: List<Data>)

@Serializable
data class ViewEntity(val entityId: Long, val viewId: String, val alias: String?)

typealias entityIdWithAlias = Pair<Long, String?>