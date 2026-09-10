package com.kos.clients.domain

import arrow.core.Either
import arrow.core.raise.either
import com.kos.common.OffsetDateTimeSerializer
import com.kos.entities.domain.Spec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import java.time.OffsetDateTime

@Serializable
data class RaiderIoCutoff(val totalPopulation: Int)

object RaiderIoProtocol {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parseCutoffJson(jsonString: String): RaiderIoCutoff {
        val totalPopulation =
            json.parseToJsonElement(jsonString)
                .jsonObject["cutoffs"]
                ?.jsonObject
                ?.get("p999")
                ?.jsonObject
                ?.get("all")
                ?.jsonObject
                ?.get("totalPopulationCount")
                ?.jsonPrimitive
                ?.int
                ?: throw IllegalStateException(
                    "cutoffs/p999/all/totalPopulationCount missing"
                )

        return RaiderIoCutoff(totalPopulation)
    }


    fun getMythicPlusRanks(
        profile: RaiderIoProfile,
        specs: List<Spec>
    ): Either<com.kos.clients.JsonParseError, List<MythicPlusRankWithSpecName>> =
        either {
            val ranksByExternalSpec = profile.mythicPlusRanks.specs

            specs.map { spec ->
                val rank = ranksByExternalSpec["spec_${spec.externalSpec}"]
                    ?: raise(
                        com.kos.clients.JsonParseError(
                            raw = "",
                            error = "/mythic_plus_ranks/spec_${spec.externalSpec}"
                        )
                    )

                val score = when (spec.internalSpec) {
                    0 -> profile.seasonScores[0].scores.spec0
                    1 -> profile.seasonScores[0].scores.spec1
                    2 -> profile.seasonScores[0].scores.spec2
                    3 -> profile.seasonScores[0].scores.spec3
                    else -> 0.0
                }

                MythicPlusRankWithSpecName(
                    name = spec.name,
                    score = score,
                    world = rank.world,
                    region = rank.region,
                    realm = rank.realm
                )
            }
        }

}

@Serializable
data class Season(
    @SerialName("is_main_season")
    val isCurrentSeason: Boolean,
    val name: String,
    @SerialName("slug")
    val slug: String,
    @SerialName("blizzard_season_id")
    val blizzardSeasonId: Int,
    val dungeons: List<Dungeon>
)

@Serializable
data class Dungeon(
    val name: String,
    @SerialName("short_name")
    val shortName: String,
    @SerialName("challenge_mode_id")
    val dungeonId: Int,
    @SerialName("icon_url")
    val icon: String,
)

@Serializable
data class ExpansionSeasons(
    @Serializable
    val seasons: List<Season>
)

@Serializable
data class SeasonScores(
    val all: Double,
    @SerialName("spec_0")
    val spec0: Double,
    @SerialName("spec_1")
    val spec1: Double,
    @SerialName("spec_2")
    val spec2: Double,
    @SerialName("spec_3")
    val spec3: Double,
)

@Serializable
data class MythicPlusSeasonScore(
    val season: String,
    val scores: SeasonScores,
)

@Serializable
data class MythicPlusRank(
    val world: Int,
    val region: Int,
    val realm: Int
)

@Serializable
data class MythicPlusRankWithSpecName(
    val name: String,
    val score: Double,
    val world: Int,
    val region: Int,
    val realm: Int
)

@Serializable(with = MythicPlusRanksSerializer::class)
data class MythicPlusRanks(
    val overall: MythicPlusRank,
    val `class`: MythicPlusRank,
    val specs: Map<String, MythicPlusRank>
)

object MythicPlusRanksSerializer : KSerializer<MythicPlusRanks> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("MythicPlusRanks") {
            element("overall", MythicPlusRank.serializer().descriptor)
            element("class", MythicPlusRank.serializer().descriptor)
            element("specs", MapSerializer(String.serializer(), MythicPlusRank.serializer()).descriptor)
        }

    override fun deserialize(decoder: Decoder): MythicPlusRanks {
        require(decoder is JsonDecoder)

        val jsonObject = decoder.decodeJsonElement().jsonObject

        val overall = jsonObject["overall"]
            ?: error("Missing overall rank")

        val clazz = jsonObject["class"]
            ?: error("Missing class rank")

        val specs = jsonObject
            .filterKeys { it.startsWith("spec_") }
            .mapValues { (_, value) ->
                decoder.json.decodeFromJsonElement(MythicPlusRank.serializer(), value)
            }

        return MythicPlusRanks(
            overall = decoder.json.decodeFromJsonElement(overall),
            `class` = decoder.json.decodeFromJsonElement(clazz),
            specs = specs
        )
    }

    override fun serialize(encoder: Encoder, value: MythicPlusRanks) {
        require(encoder is JsonEncoder)

        val jsonObject = buildJsonObject {
            put("overall", encoder.json.encodeToJsonElement(value.overall))
            put("class", encoder.json.encodeToJsonElement(value.`class`))
            value.specs.forEach { (key, rank) ->
                put(key, encoder.json.encodeToJsonElement(rank))
            }
        }

        encoder.encodeJsonElement(jsonObject)
    }
}


@Serializable
data class MythicPlusRanksWithSpecs(
    val overall: MythicPlusRank,
    val `class`: MythicPlusRank,
    val specs: List<MythicPlusRankWithSpecName>
)

@Serializable
data class Affix(
    @SerialName("name")
    val affix: String
)

@Serializable
data class ClassSpec(
    val id: Int,
    val name: String,
    val role: String,
)

@Serializable
data class MythicPlusRun(
    @SerialName("keystone_run_id")
    val runId: Long,
    val dungeon: String,
    @SerialName("short_name")
    val shortName: String,
    @SerialName("mythic_level")
    val keyLevel: Int,
    @SerialName("num_keystone_upgrades")
    val upgrades: Int,
    @SerialName("completed_at")
    @Serializable(with = OffsetDateTimeSerializer::class)
    val dateCompleted: OffsetDateTime,
    @SerialName("clear_time_ms")
    val clearTimeMs: Long,
    @SerialName("par_time_ms")
    val dungeonTimeMs: Long,
    val score: Float,
    val url: String,
    val affixes: List<Affix>,
    val spec: ClassSpec? = null
)

@Serializable
data class RaiderIoScoreResponse(
    @SerialName("mythic_plus_scores_by_season")
    val seasonScores: List<MythicPlusSeasonScore>
)

@Serializable
data class RaiderIoProfile(
    val name: String,
    val realm: String,
    val region: String,
    val `class`: String,
    @SerialName("active_spec_name")
    val spec: String,
    @SerialName("mythic_plus_scores_by_season")
    val seasonScores: List<MythicPlusSeasonScore>,
    @SerialName("mythic_plus_ranks")
    val mythicPlusRanks: MythicPlusRanks,
    @SerialName("mythic_plus_best_runs")
    val mythicPlusBestRuns: List<MythicPlusRun>,
    @SerialName("mythic_plus_recent_runs")
    val mythicPlusRecentRuns: List<MythicPlusRun> = emptyList()
) {
    fun toRaiderIoData(
        characterId: Long,
        quantile: Double?,
        specRanks: List<MythicPlusRankWithSpecName>,
        bestRuns: List<EnrichedMythicPlusRun>,
    ) = RaiderIoData(
        characterId,
        name,
        realm,
        region,
        seasonScores[0].scores.all,
        `class`,
        spec,
        quantile,
        MythicPlusRanksWithSpecs(mythicPlusRanks.overall, mythicPlusRanks.`class`, specRanks),
        bestRuns,
        mythicPlusRecentRuns
    )
}

data class RaiderIoResponse(
    val profile: RaiderIoProfile,
    val specs: List<MythicPlusRankWithSpecName>
)

@Serializable
data class RunDetailsCharacterClass(val name: String)

@Serializable
data class RunDetailsCharacterSpec(val name: String)

@Serializable
data class RunDetailsCharacterRealm(
    val id: Int,
    val name: String,
    val slug: String
)

@Serializable
data class RunDetailsCharacterRegion(
    val name: String,
    @SerialName("short_name")
    val shortName: String,
    val slug: String
)

@Serializable
data class RunDetailsRosterRanks(
    val score: Double
)

@Serializable
data class RunDetailsCharacter(
    val name: String,
    val `class`: RunDetailsCharacterClass,
    val spec: RunDetailsCharacterSpec,
    val realm: RunDetailsCharacterRealm,
    val region: RunDetailsCharacterRegion
)

@Serializable
data class RunDetailsRosterEntry(
    val character: RunDetailsCharacter,
    val role: String,
    val ranks: RunDetailsRosterRanks
)

@Serializable
data class RunDetailsDeath(
    @SerialName("approximate_died_at")
    val approximateDiedAt: Int
)

@Serializable
data class LoggedDetails(val deaths: List<RunDetailsDeath> = emptyList())

@Serializable
data class RunDetails(
    val roster: List<RunDetailsRosterEntry>,
    @SerialName("logged_details")
    val loggedDetails: LoggedDetails? = null
) {
}


object CodeExtractorSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CodeExtractor", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement().jsonObject
        return jsonObject["code"]!!.jsonPrimitive.content
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

@Serializable
data class TalentLoadout(
    @Serializable(with = CodeExtractorSerializer::class)
    val wowheadCalculator: String
)


@Serializable
data class RaiderioWowHeadEmbeddedResponse(
    val talentLoadout: TalentLoadout
)

@Serializable
data class EnrichedMythicPlusRun(
    val run: MythicPlusRun,
    val details: RunDetails? = null
)

@Serializable
data class RaiderIoData(
    val id: Long,
    val name: String,
    val realm: String,
    val region: String,
    val score: Double,
    val `class`: String,
    val spec: String,
    val quantile: Double?,
    val mythicPlusRanks: MythicPlusRanksWithSpecs,
    val mythicPlusBestRuns: List<EnrichedMythicPlusRun>,
    val mythicPlusRecentRuns: List<MythicPlusRun> = emptyList()
) : Data

