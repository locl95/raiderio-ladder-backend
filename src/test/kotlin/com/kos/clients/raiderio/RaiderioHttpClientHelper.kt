package com.kos.clients.raiderio

import com.kos.clients.domain.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import java.time.OffsetDateTime

object RaiderIoHttpClientHelper {

    object ResourceLoader {
        fun readResource(path: String): String =
            object {}.javaClass.classLoader
                .getResourceAsStream(path)!!
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
    }

    val client = HttpClient(MockEngine) {
        install(ContentNegotiation) {
            json()
        }
        engine {
            addHandler { request ->
                when (request.url.encodedPath) {

                    "/api/v1/mythic-plus/static-data" -> {
                        val response = ResourceLoader.readResource("unit/wow/raiderio-tww-seasons.json")
                        respond(
                            content = response,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    "/api/v1/mythic-plus/season-cutoffs" -> {
                        val response = ResourceLoader.readResource("unit/wow/raiderio-cutoff-response.json")
                        respond(
                            content = response,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    "/api/v1/characters/profile" -> {
                        when (request.url.parameters["name"]) {
                            "unknown-character" -> respond(
                                content = """{"statusCode":400,"error":"Bad Request","message":"Could not find requested character"}""",
                                status = HttpStatusCode.BadRequest,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )

                            "malformed-request-character" -> respond(
                                content = """{"statusCode":400,"error":"Bad Request","message":"Invalid request query input"}""",
                                status = HttpStatusCode.BadRequest,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )

                            else -> {
                                val response = when (request.url.parameters["fields"]) {
                                    "talents" -> ResourceLoader.readResource("unit/wow/raiderio-classic-talents-response.json")
                                    "mythic_plus_scores_by_season:current" ->
                                        """{"mythic_plus_scores_by_season":[{"season":"season-tww-3","scores":{"all":1234.5,"spec_0":1234.5,"spec_1":0.0,"spec_2":0.0,"spec_3":0.0}}]}"""

                                    else -> ResourceLoader.readResource("unit/wow/raiderio-profile-response.json")
                                }
                                respond(
                                    content = response,
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                                )
                            }
                        }
                    }

                    "/api/v1/mythic-plus/run-details" -> {
                        val response = ResourceLoader.readResource("unit/wow/raiderio-run-details-response.json")
                        respond(
                            content = response,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    else -> error("Unhandled ${request.url.encodedPath}")
                }
            }
        }
    }

    val raiderioProfileResponse =
        RaiderIoResponse(
            RaiderIoProfile(
                name = "Nareez",
                realm = "Blackrock",
                region = "eu",
                `class` = "Warlock",
                spec = "Affliction",
                seasonScores = listOf(
                    MythicPlusSeasonScore(
                        "season-df-3",
                        SeasonScores(2708.4, 2708.4, 0.0, 0.0, 0.0)
                    )
                ),
                mythicPlusRanks = MythicPlusRanks(
                    overall = MythicPlusRank(43389, 24021, 887),
                    `class` = MythicPlusRank(1989, 1077, 58),
                    specs = mapOf(
                        "spec_265" to MythicPlusRank(4, 2, 2),
                        "spec_266" to MythicPlusRank(0, 0, 0),
                        "spec_267" to MythicPlusRank(0, 0, 0)
                    )
                ),
                mythicPlusRecentRuns = listOf(),
                mythicPlusBestRuns = listOf(
                    MythicPlusRun(
                        4462779L,
                        "Throne of the Tides",
                        "TOTT",
                        20,
                        2,
                        OffsetDateTime.parse("2023-11-25T12:12:22.000Z"),
                        1380287L,
                        2040999L,
                        174.0F,
                        "https://raider.io/mythic-plus-runs/season-df-3/4462779-20-throne-of-the-tides",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    ),
                    MythicPlusRun(
                        3814291L,
                        "Atal'Dazar",
                        "AD",
                        20,
                        2,
                        OffsetDateTime.parse("2023-11-23T20:35:15.000Z"),
                        1260658L,
                        1800999L,
                        173.8F,
                        "https://raider.io/mythic-plus-runs/season-df-3/3814291-20-ataldazar",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    ),
                    MythicPlusRun(
                        4879212L,
                        "Waycrest Manor",
                        "WM",
                        20,
                        2,
                        OffsetDateTime.parse("2023-11-26T13:06:05.000Z"),
                        1605776L,
                        2220999L,
                        173.5F,
                        "https://raider.io/mythic-plus-runs/season-df-3/4879212-20-waycrest-manor",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    ),
                    MythicPlusRun(
                        3709222L,
                        "Darkheart Thicket",
                        "DHT",
                        20,
                        2,
                        OffsetDateTime.parse("2023-11-23T16:32:21.000Z"),
                        1310595L,
                        1800999L,
                        173.4F,
                        "https://raider.io/mythic-plus-runs/season-df-3/3709222-20-darkheart-thicket",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    ),
                    MythicPlusRun(
                        3628977L,
                        "Black Rook Hold",
                        "BRH",
                        20,
                        2,
                        OffsetDateTime.parse("2023-11-23T10:33:37.000Z"),
                        1724166L,
                        2160999L,
                        172.5F,
                        "https://raider.io/mythic-plus-runs/season-df-3/3628977-20-black-rook-hold",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    ),
                    MythicPlusRun(
                        3775347L,
                        "DOTI: Galakrond's Fall",
                        "FALL",
                        20,
                        1,
                        OffsetDateTime.parse("2023-11-23T20:04:33.000Z"),
                        1642682L,
                        2040999L,
                        172.4F,
                        "https://raider.io/mythic-plus-runs/season-df-3/3775347-20-doti-galakronds-fall",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    ),
                    MythicPlusRun(
                        4267361L,
                        "The Everbloom",
                        "EB",
                        20,
                        1,
                        OffsetDateTime.parse("2023-11-24T19:42:55.000Z"),
                        1719391L,
                        1980999L,
                        171.7F,
                        "https://raider.io/mythic-plus-runs/season-df-3/4267361-20-everbloom",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    ),
                    MythicPlusRun(
                        4382848L,
                        "DOTI: Murozond's Rise",
                        "RISE",
                        20,
                        1,
                        OffsetDateTime.parse("2023-11-25T09:38:33.000Z"),
                        2097610L,
                        2100999L,
                        170.0F,
                        "https://raider.io/mythic-plus-runs/season-df-3/4382848-20-doti-murozonds-rise",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    )
                )

            ),
            specs = listOf(
                MythicPlusRankWithSpecName("Affliction", 2708.4, 4, 2, 2),
                MythicPlusRankWithSpecName("Demonology", 0.0, 0, 0, 0),
                MythicPlusRankWithSpecName("Destruction", 0.0, 0, 0, 0)
            )
        )

    val mythicPlusRunJson = """
        {
            "keystone_run_id": 4462779,
            "dungeon": "Throne of the Tides",
            "short_name": "TOTT",
            "mythic_level": 20,
            "num_keystone_upgrades": 2,
            "completed_at": "2023-11-25T12:12:22.000Z",
            "clear_time_ms": 1380287,
            "par_time_ms": 2040999,
            "score": 174.0,
            "url": "https://raider.io/mythic-plus-runs/season-df-3/4462779-20-throne-of-the-tides",
            "affixes": [
                { "name": "Tyrannical" },
                { "name": "Entangling" },
                { "name": "Bursting" }
            ]
        }
    """.trimIndent()

    private val usRegion = RunDetailsCharacterRegion("United States & Oceania", "US", "us")

    val runDetails = RunDetails(
        roster = listOf(
            RunDetailsRosterEntry(
                character = RunDetailsCharacter(
                    "Nareez",
                    RunDetailsCharacterClass("Warlock"),
                    RunDetailsCharacterSpec("Affliction"),
                    RunDetailsCharacterRealm(1, "Blackrock", "blackrock"),
                    usRegion
                ),
                role = "dps",
                ranks = RunDetailsRosterRanks(3200.5)
            ),
            RunDetailsRosterEntry(
                character = RunDetailsCharacter(
                    "Surmana",
                    RunDetailsCharacterClass("Warrior"),
                    RunDetailsCharacterSpec("Protection"),
                    RunDetailsCharacterRealm(2, "Soulseeker", "soulseeker"),
                    usRegion
                ),
                role = "tank",
                ranks = RunDetailsRosterRanks(2800.0)
            )
        ),
        loggedDetails = LoggedDetails(
            deaths = listOf(
                RunDetailsDeath(1441041),
                RunDetailsDeath(1446140),
                RunDetailsDeath(914295)
            )
        )
    )

    val mythicPlusRun = MythicPlusRun(
        runId = 4462779L,
        dungeon = "Throne of the Tides",
        shortName = "TOTT",
        keyLevel = 20,
        upgrades = 2,
        dateCompleted = OffsetDateTime.parse("2023-11-25T12:12:22.000Z"),
        clearTimeMs = 1380287L,
        dungeonTimeMs = 2040999L,
        score = 174.0F,
        url = "https://raider.io/mythic-plus-runs/season-df-3/4462779-20-throne-of-the-tides",
        affixes = listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
    )
}