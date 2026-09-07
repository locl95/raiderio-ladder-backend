package acceptance.steps

import acceptance.*
import com.kos.datacache.DataCache
import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.entities.domain.LolEnrichedEntityRequest
import com.kos.entities.domain.WowEntityRequest
import com.kos.entities.repository.EntitiesDatabaseRepository
import com.kos.eventsourcing.events.RequestToBeSynced
import com.kos.eventsourcing.events.repository.EventStoreDatabase
import com.kos.views.Game
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.When
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntitiesSteps(private val scenarioVariables: ScenarioVariables) {

    private val client = SharedInfrastructure.client
    private val db = SharedInfrastructure.db

    @Given("a {string} entity {string} on realm {string} region {string} exists with cached data {string}")
    fun entityExistsWithCachedData(game: String, name: String, realm: String, region: String, jsonFile: String) {
        val resolvedGame = game.toGame()
        val entitiesRepo = EntitiesDatabaseRepository(db)
        val dataCacheRepo = DataCacheDatabaseRepository(db)

        val entity = runBlocking {
            entitiesRepo.insert(listOf(wowEntityRequest(name, realm, region)), resolvedGame)
        }.getOrNull()!!.first()

        val data = javaClass.getResourceAsStream("/acceptance/files/entity/$jsonFile.json")!!
            .bufferedReader()
            .readText()

        runBlocking {
            dataCacheRepo.insert(listOf(DataCache(entity.id, data, OffsetDateTime.now(), resolvedGame)))
        }
    }

    @Given("a {string} entity {string} on realm {string} region {string} exists in the database")
    fun entityExistsWithoutCachedData(game: String, name: String, realm: String, region: String) {
        val resolvedGame = game.toGame()
        val entitiesRepo = EntitiesDatabaseRepository(db)
        runBlocking {
            entitiesRepo.insert(listOf(wowEntityRequest(name, realm, region)), resolvedGame)
        }
    }

    @Given("a LOL entity {string} with tag {string} exists in the database")
    fun lolEntityExists(name: String, tag: String) {
        val entitiesRepo = EntitiesDatabaseRepository(db)
        runBlocking {
            entitiesRepo.insert(
                listOf(
                    LolEnrichedEntityRequest(
                        name,
                        tag,
                        "test-puuid-${name.replace(" ", "-")}",
                        0,
                        0
                    )
                ), Game.LOL
            )
        }
    }

    @Given("a {string} entity already exists in the database")
    fun anEntityAlreadyExistsInTheDatabase(game: String) {
        val resolvedGame = game.toGame()
        val row = existingEntityRow(resolvedGame)
        when (resolvedGame) {
            Game.WOW, Game.WOW_HC -> entityExistsWithoutCachedData(
                game, row.getValue("name"), row.getValue("realm"), row.getValue("region")
            )

            Game.LOL -> lolEntityExists(row.getValue("name"), row.getValue("tag"))
        }
    }

    @And("the resolved entities for the {string} view are persisted in the database")
    fun resolvedEntitiesArePersisted(game: String) {
        val resolvedGame = game.toGame()
        val entitiesRepo = EntitiesDatabaseRepository(db)
        val expectedRows = listOf(existingEntityRow(resolvedGame), newEntityRow(resolvedGame))
        runBlocking {
            expectedRows.forEach { row ->
                val entity = entitiesRepo.get(resolvedGame.entityRequest(row), resolvedGame)
                assertNotNull(entity, "Expected entity $row to be resolved and persisted for game $game")
            }
        }
    }

    @Given("a LOL entity {string} with tag {string} exists with cached data {string}")
    fun lolEntityExistsWithCachedData(name: String, tag: String, jsonFile: String) {
        val entitiesRepo = EntitiesDatabaseRepository(db)
        val dataCacheRepo = DataCacheDatabaseRepository(db)

        val entity = runBlocking {
            entitiesRepo.insert(
                listOf(
                    LolEnrichedEntityRequest(
                        name,
                        tag,
                        "test-puuid-${name.replace(" ", "-")}",
                        0,
                        0
                    )
                ), Game.LOL
            )
        }.getOrNull()!!.first()

        val data = javaClass.getResourceAsStream("/acceptance/files/entity/$jsonFile.json")!!
            .bufferedReader()
            .readText()

        runBlocking {
            dataCacheRepo.insert(listOf(DataCache(entity.id, data, OffsetDateTime.now(), Game.LOL)))
        }
    }

    @When("they search for a {string} entity {string} on realm {string} region {string}")
    fun searchEntity(game: String, name: String, realm: String, region: String) {
        scenarioVariables.response = runBlocking {
            client.get("/api/entities") {
                scenarioVariables.token?.let { bearerAuth(it) }
                parameter("game", game.lowercase())
                parameter("name", name)
                parameter("realm", realm)
                parameter("region", region)
            }
        }
    }

    @When("they search for a {string} entity {string} with tag {string}")
    fun searchLolEntity(game: String, name: String, tag: String) {
        scenarioVariables.response = runBlocking {
            client.get("/api/entities") {
                scenarioVariables.token?.let { bearerAuth(it) }
                parameter("game", game.lowercase())
                parameter("name", name)
                parameter("tag", tag)
            }
        }
    }

    @When("they check existence of {string} entities:")
    fun checkEntitiesExist(game: String, dataTable: DataTable) {
        val resolvedGame = game.toGame()
        val entities = dataTable.asMaps(String::class.java, String::class.java).map { row ->
            resolvedGame.entityRequestJson(row)
        }
        val requestBody = buildJsonObject {
            put("entities", JsonArray(entities))
            put("game", resolvedGame.name)
        }
        scenarioVariables.response = runBlocking {
            client.post("/api/entities/exists") {
                contentType(ContentType.Application.Json)
                scenarioVariables.token?.let { bearerAuth(it) }
                setBody(requestBody.toString())
            }
        }
    }

    @When("they check existence of guild {string} on realm {string} region {string}")
    fun checkGuildExists(name: String, realm: String, region: String) {
        val requestBody = buildJsonObject {
            put("name", name)
            put("realm", realm)
            put("region", region)
        }
        scenarioVariables.response = runBlocking {
            client.post("/api/entities/exists/guild") {
                contentType(ContentType.Application.Json)
                scenarioVariables.token?.let { bearerAuth(it) }
                setBody(requestBody.toString())
            }
        }
    }

    @And("the guild exists in the response")
    fun guildExistsInResponse() {
        val body = runBlocking { scenarioVariables.response.bodyAsText() }
        val guild = Json.parseToJsonElement(body).jsonObject["guild"]
        assertNotNull(guild?.takeIf { it.toString() != "null" }, "Expected guild to be present in the response")
    }

    @And("{string} is in the {string} bucket")
    fun entityIsInBucket(name: String, bucket: String) {
        val body = runBlocking { scenarioVariables.response.bodyAsText() }
        val json = Json.parseToJsonElement(body).jsonObject
        val names = json[bucket]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertTrue(names.contains(name), "Expected \"$name\" to be in the \"$bucket\" bucket, got $names")
    }

    @And("the response data is null")
    fun responseDataIsNull() {
        val body = runBlocking { scenarioVariables.response.bodyAsText() }
        val data = Json.parseToJsonElement(body).jsonObject["data"]
        assertNull(data?.takeIf { it.toString() != "null" })
    }

    @And("the response data is a valid WOW entity")
    fun responseDataIsValidWowEntity() {
        val body = runBlocking { scenarioVariables.response.bodyAsText() }
        val dataElement = Json.parseToJsonElement(body).jsonObject["data"]
        assertNotNull(dataElement?.takeIf { it.toString() != "null" }, "Expected response data to be present")
        val data = dataElement!!.jsonObject
        assertEquals("com.kos.clients.domain.RaiderIoData", data["type"]!!.jsonPrimitive.content)
        assertNotNull(data["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() })
        assertNotNull(data["score"])
        assertNotNull(data["mythicPlusRanks"])
    }

    @And("the response data is a valid LOL entity")
    fun responseDataIsValidLolEntity() {
        val body = runBlocking { scenarioVariables.response.bodyAsText() }
        val dataElement = Json.parseToJsonElement(body).jsonObject["data"]
        assertNotNull(dataElement?.takeIf { it.toString() != "null" }, "Expected response data to be present")
        val data = dataElement!!.jsonObject
        assertEquals("com.kos.clients.domain.RiotData", data["type"]!!.jsonPrimitive.content)
        assertNotNull(data["summonerName"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() })
        assertNotNull(data["summonerTag"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() })
        assertNotNull(data["leagues"])
    }

    @And("the response contains an operation")
    fun responseContainsOperation() {
        val body = runBlocking { scenarioVariables.response.bodyAsText() }
        val operation = Json.parseToJsonElement(body).jsonObject["operation"]
        assertNotNull(operation?.takeIf { it.toString() != "null" })
    }

    @And("the operation is null")
    fun operationIsNull() {
        val body = runBlocking { scenarioVariables.response.bodyAsText() }
        val operation = Json.parseToJsonElement(body).jsonObject["operation"]
        assertNull(operation?.takeIf { it.toString() != "null" })
    }

    @And("a sync event exists for {string} entity {string} on realm {string} region {string}")
    fun syncEventExistsForEntity(game: String, name: String, realm: String, region: String) {
        val eventStore = EventStoreDatabase(db)
        val resolvedGame = game.toGame()
        runBlocking {
            val events = eventStore.state()
            assertTrue(
                events.any { eventWithVersion ->
                    val data = eventWithVersion.event.eventData
                    data is RequestToBeSynced &&
                            data.game == resolvedGame &&
                            (data.request as? WowEntityRequest)?.let {
                                it.name == name && it.realm == realm && it.region == region
                            } == true
                },
                "Expected a sync event for $game entity $name on $realm/$region but none found"
            )
        }
    }
}
