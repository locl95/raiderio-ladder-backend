package com.kos.raiderio

import arrow.core.Either
import arrow.core.traverse
import com.kos.common.JsonParseError
import com.kos.characters.Spec
import com.kos.eventsourcing.events.Event
import com.kos.eventsourcing.events.EventData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class RaiderIoCutoff(val totalPopulation: Int)

object RaiderIoProtocol {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parseCutoffJson(jsonString: String): Either<JsonParseError, RaiderIoCutoff> {
        val totalPopulation: Int? = json.parseToJsonElement(jsonString)
            .jsonObject["cutoffs"]
            ?.jsonObject
            ?.get("p999")
            ?.jsonObject
            ?.get("all")
            ?.jsonObject
            ?.get("totalPopulationCount")
            ?.jsonPrimitive
            ?.int

        return when (totalPopulation) {
            null -> Either.Left(JsonParseError(jsonString, "cutoffs/p999/all/totalPopulationCount"))
            else -> Either.Right(RaiderIoCutoff(totalPopulation))
        }
    }

    fun parseMythicPlusRanks(
        jsonString: String,
        specs: List<Spec>,
        scores: SeasonScores
    ): Either<JsonParseError, List<MythicPlusRankWithSpecName>> {
        val mythicPlusRanks = json.parseToJsonElement(jsonString)
            .jsonObject["mythic_plus_ranks"]
            ?.jsonObject

        return specs.traverse {
            val ranks = mythicPlusRanks
                ?.get("spec_${it.externalSpec}")
                ?.jsonObject

            val world = ranks?.get("world")?.jsonPrimitive?.int
            val region = ranks?.get("region")?.jsonPrimitive?.int
            val realm = ranks?.get("realm")?.jsonPrimitive?.int

            val specScore = when (it.internalSpec) {
                0 -> scores.spec0
                1 -> scores.spec1
                2 -> scores.spec2
                3 -> scores.spec3
                else -> 0.0
            }

            if (world != null && region != null && realm != null) Either.Right(
                MythicPlusRankWithSpecName(
                    it.name,
                    specScore,
                    world,
                    region,
                    realm
                )
            )
            else Either.Left(JsonParseError(jsonString, "/mythic_plus_ranks/spec_$it"))
        }
    }
}


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

@Serializable
data class MythicPlusRanks(
    val overall: MythicPlusRank,
    val `class`: MythicPlusRank,
)

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
data class MythicPlusRun(
    val dungeon: String,
    @SerialName("short_name")
    val shortName: String,
    @SerialName("mythic_level")
    val keyLevel: Int,
    @SerialName("num_keystone_upgrades")
    val upgrades: Int,
    val score: Float,
    val url: String,
    val affixes: List<Affix>
)

@Serializable
data class RaiderIoProfile(
    val name: String,
    val `class`: String,
    @SerialName("active_spec_name")
    val spec: String,
    @SerialName("mythic_plus_scores_by_season")
    val seasonScores: List<MythicPlusSeasonScore>,
    @SerialName("mythic_plus_ranks")
    val mythicPlusRanks: MythicPlusRanks,
    @SerialName("mythic_plus_best_runs")
    val mythicPlusBestRuns: List<MythicPlusRun>,
    @SerialName("mythic_plus_alternate_runs")
    val mythicPlusAlternateRuns: List<MythicPlusRun>
) {
    fun toRaiderIoData(characterId: Long, quantile: Double, specRanks: List<MythicPlusRankWithSpecName>) = RaiderIoData(
        characterId,
        name,
        seasonScores[0].scores.all,
        `class`,
        spec,
        quantile,
        MythicPlusRanksWithSpecs(mythicPlusRanks.overall, mythicPlusRanks.`class`, specRanks),
        mythicPlusBestRuns,
        mythicPlusAlternateRuns
    )
}

data class RaiderIoResponse(
    val profile: RaiderIoProfile,
    val specs: List<MythicPlusRankWithSpecName>
)

@Serializable
data class RaiderIoData(
    val id: Long,
    val name: String,
    val score: Double,
    val `class`: String,
    val spec: String,
    val quantile: Double,
    val mythicPlusRanks: MythicPlusRanksWithSpecs,
    val mythicPlusBestRuns: List<MythicPlusRun>,
    val mythicPlusAlternateRuns: List<MythicPlusRun>
): EventData


