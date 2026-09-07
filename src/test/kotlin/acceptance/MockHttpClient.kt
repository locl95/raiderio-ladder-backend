package acceptance

import com.kos.clients.domain.GetWowRosterResponse
import com.kos.clients.domain.WowCharacterResponse
import com.kos.clients.domain.WowGuildResponse
import com.kos.clients.domain.WowMemberResponse
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private fun readResource(path: String): String =
    object {}.javaClass.classLoader.getResourceAsStream(path)!!
        .bufferedReader(Charsets.UTF_8).readText()

val mockHttpClient = HttpClient(MockEngine) {
    engine {
        addHandler { request ->
            val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            val path = request.url.encodedPath
            when {
                path.contains("/token") ->
                    respond(
                        readResource("acceptance/files/responses/wow-hc/blizzard-token-response.json"),
                        HttpStatusCode.OK,
                        json
                    )

                path.contains("/character-media") ->
                    respond(
                        readResource("acceptance/files/responses/wow-hc/blizzard-character-media-response.json"),
                        HttpStatusCode.OK,
                        json
                    )

                path.contains("/equipment") ->
                    respond(
                        readResource("acceptance/files/responses/wow-hc/blizzard-equipment-response.json"),
                        HttpStatusCode.OK,
                        json
                    )

                path.contains("/statistics") ->
                    respond(
                        readResource("acceptance/files/responses/wow-hc/blizzard-statistics-response.json"),
                        HttpStatusCode.OK,
                        json
                    )

                path.contains("/specializations") ->
                    respond(
                        readResource("acceptance/files/responses/wow-hc/blizzard-specializations-response.json"),
                        HttpStatusCode.OK,
                        json
                    )

                path.contains("/data/wow/guild/") -> {
                    val namespace = request.url.parameters["namespace"]
                    if (namespace == "profile-classic1x-eu") {
                        respond(
                            readResource("acceptance/files/responses/wow-hc/blizzard-guild-roster-response.json"),
                            HttpStatusCode.OK,
                            json
                        )
                    } else {
                        val members = (MockConfig.wowGuildRosterMembers ?: emptyList()).map { (name, level) ->
                            WowMemberResponse(WowCharacterResponse(name, level))
                        }
                        respond(
                            Json.encodeToString(GetWowRosterResponse(members, WowGuildResponse(12345))),
                            HttpStatusCode.OK,
                            json
                        )
                    }
                }

                path.contains("/data/wow/realm/") ->
                    respond(
                        readResource("acceptance/files/responses/wow-hc/blizzard-realm-response.json"),
                        HttpStatusCode.OK,
                        json
                    )

                path.contains("/profile/wow/character/") -> {
                    val status = MockConfig.blizzardProfileStatusOverride ?: HttpStatusCode.OK
                    val characterName = path.substringAfterLast("/")
                    when {
                        status != HttpStatusCode.OK -> respond("", status, json)
                        characterName.equals("NonExistentEntity", ignoreCase = true) ->
                            respond("", HttpStatusCode.NotFound, json)

                        characterName.equals("UncheckedEntity", ignoreCase = true) ->
                            respond("", HttpStatusCode.InternalServerError, json)
                        else -> respond(
                            readResource("acceptance/files/responses/wow-hc/blizzard-character-profile-response.json"),
                            status,
                            json
                        )
                    }
                }

                path.contains("/mythic-plus/static-data") ->
                    respond(
                        readResource("acceptance/files/responses/wow/raiderio-expansion-seasons-response.json"),
                        HttpStatusCode.OK,
                        json
                    )

                path.contains("/characters/profile") -> {
                    val status = MockConfig.raiderIoProfileStatusOverride ?: HttpStatusCode.OK
                    when {
                        status != HttpStatusCode.OK -> respond("", status, json)

                        request.url.parameters["name"] == "NonExistentEntity" -> respond(
                            """{"statusCode":400,"error":"Bad Request","message":"Could not find requested character"}""",
                            HttpStatusCode.BadRequest, json
                        )

                        request.url.parameters["name"] == "UncheckedEntity" -> respond(
                            """{"statusCode":400,"error":"Bad Request","message":"Invalid request query input"}""",
                            HttpStatusCode.BadRequest, json
                        )

                        else -> respond(
                            readResource("acceptance/files/responses/wow/raiderio-profile-response.json"),
                            HttpStatusCode.OK, json
                        )
                    }
                }

                path.contains("/season-cutoffs") -> {
                    val status = MockConfig.raiderIoCutoffStatusOverride ?: HttpStatusCode.OK
                    if (status == HttpStatusCode.OK)
                        respond(
                            readResource("acceptance/files/responses/wow/raiderio-cutoff-response.json"),
                            status,
                            json
                        )
                    else
                        respond("", status, json)
                }

                path.contains("/run-details") ->
                    respond(
                        readResource("acceptance/files/responses/wow/raiderio-run-details-response.json"),
                        HttpStatusCode.OK,
                        json
                    )

                path.contains("/accounts/by-riot-id") ->
                    respond(
                        readResource("acceptance/files/responses/lol/riot-get-puuid-by-riot-id-response.json"),
                        HttpStatusCode.OK,
                        json
                    )

                path.contains("/accounts/by-puuid") ->
                    respond(
                        readResource("acceptance/files/responses/lol/riot-get-account-by-puuid-response.json"),
                        HttpStatusCode.OK,
                        json
                    )

                path.contains("/summoners/by-puuid") ->
                    respond(
                        readResource("acceptance/files/responses/lol/riot-get-summoner-by-puuid-response.json"),
                        HttpStatusCode.OK,
                        json
                    )

                path.contains("/entries/by-puuid") ->
                    respond(
                        readResource("acceptance/files/responses/lol/riot-get-leagues-by-summoner-id.json"),
                        HttpStatusCode.OK,
                        json
                    )

                path.contains("/matches/by-puuid") ->
                    respond(
                        readResource("acceptance/files/responses/lol/riot-get-matches-by-puuid-response.json"),
                        HttpStatusCode.OK,
                        json
                    )

                path.contains("/matches/") ->
                    respond(
                        readResource("acceptance/files/responses/lol/riot-get-match-by-id-response.json"),
                        HttpStatusCode.OK,
                        json
                    )

                else -> respond("{}", HttpStatusCode.OK, json)
            }
        }
    }
}
