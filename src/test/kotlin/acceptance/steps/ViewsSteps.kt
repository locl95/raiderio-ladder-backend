package acceptance.steps

import acceptance.MockConfig
import acceptance.ScenarioVariables
import acceptance.SharedInfrastructure
import acceptance.entityRequestJson
import acceptance.existingEntityRow
import acceptance.newEntityRow
import acceptance.toGame
import com.kos.views.Game
import com.kos.views.ViewPatchRequest
import com.kos.views.ViewRequest
import com.kos.views.repository.ViewsDatabaseRepository
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ViewsSteps(private val scenarioVariables: ScenarioVariables) {

    private val client = SharedInfrastructure.client
    private val db = SharedInfrastructure.db

    @When("they create a {string} view")
    fun createView(game: String) {
        val resolvedGame = game.toGame()
        scenarioVariables.game = resolvedGame
        scenarioVariables.response = runBlocking {
            client.post("/api/views") {
                contentType(ContentType.Application.Json)
                scenarioVariables.token?.let { bearerAuth(it) }
                setBody(
                    ViewRequest(
                        name = "My View",
                        published = false,
                        entities = emptyList(),
                        game = resolvedGame,
                        featured = false
                    )
                )
            }
        }
        if (scenarioVariables.response.status == HttpStatusCode.OK) {
            val body = runBlocking { scenarioVariables.response.bodyAsText() }
            val json = Json.parseToJsonElement(body).jsonObject
            scenarioVariables.operationId = json["id"]!!.jsonPrimitive.content
            scenarioVariables.viewId = json["resourceId"]!!.jsonPrimitive.content
        }
    }

    // One entity already exists in the database (resolver should find it via the repository)
    // and one is new (resolver has to confirm it against the third party) - see TestData.kt.
    @When("they create a {string} view with an existing and a new entity")
    fun createViewWithExistingAndNewEntity(game: String) {
        val resolvedGame = game.toGame()
        scenarioVariables.game = resolvedGame
        val entities = listOf(existingEntityRow(resolvedGame), newEntityRow(resolvedGame))
            .map { resolvedGame.entityRequestJson(it) }
        val requestBody = buildJsonObject {
            put("name", "My View")
            put("published", false)
            put("entities", JsonArray(entities))
            put("game", resolvedGame.name)
            put("featured", false)
        }
        scenarioVariables.response = runBlocking {
            client.post("/api/views") {
                contentType(ContentType.Application.Json)
                scenarioVariables.token?.let { bearerAuth(it) }
                setBody(requestBody.toString())
            }
        }
        if (scenarioVariables.response.status == HttpStatusCode.OK) {
            val body = runBlocking { scenarioVariables.response.bodyAsText() }
            val json = Json.parseToJsonElement(body).jsonObject
            scenarioVariables.operationId = json["id"]!!.jsonPrimitive.content
            scenarioVariables.viewId = json["resourceId"]!!.jsonPrimitive.content
        }
    }

    private var firstGuildViewId: String? = null

    @Given("a WOW guild roster is available from the Blizzard API")
    fun wowGuildRosterIsAvailable() {
        MockConfig.wowGuildRosterMembers = listOf("Kakarona" to 90, "Threndil" to 90)
    }

    @When("they create a WOW guild view for {string}")
    fun createWowGuildView(guildName: String) {
        postCreateWowGuildView(guildName)
    }

    @When("they create a second WOW guild view for {string}")
    fun createSecondWowGuildView(guildName: String) {
        firstGuildViewId = scenarioVariables.viewId
        postCreateWowGuildView(guildName)
    }

    private fun postCreateWowGuildView(guildName: String) {
        scenarioVariables.game = Game.WOW
        val requestBody = buildJsonObject {
            put("name", "$guildName roster")
            put("published", false)
            put(
                "entities",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("type", "com.kos.entities.domain.WowEntityRequest")
                            put("name", guildName)
                            put("region", "eu")
                            put("realm", "twisting-nether")
                        }
                    )
                )
            )
            put("game", Game.WOW.name)
            put("featured", false)
            put(
                "extraArguments",
                buildJsonObject {
                    put("type", "com.kos.views.WowExtraArguments")
                    put("guild", "RESOLVE")
                    put("season", 0)
                }
            )
        }
        scenarioVariables.response = runBlocking {
            client.post("/api/views") {
                contentType(ContentType.Application.Json)
                scenarioVariables.token?.let { bearerAuth(it) }
                setBody(requestBody.toString())
            }
        }
        if (scenarioVariables.response.status == HttpStatusCode.OK) {
            val body = runBlocking { scenarioVariables.response.bodyAsText() }
            val json = Json.parseToJsonElement(body).jsonObject
            scenarioVariables.operationId = json["id"]!!.jsonPrimitive.content
            scenarioVariables.viewId = json["resourceId"]!!.jsonPrimitive.content
        }
    }

    @Then("both WOW guild views have the same entities")
    fun bothWowGuildViewsHaveSameEntities() {
        val viewsRepo = ViewsDatabaseRepository(db)
        val firstId = firstGuildViewId!!
        val secondId = scenarioVariables.viewId!!
        runBlocking {
            assertUntil(5, 1000) {
                val first = viewsRepo.get(firstId)
                val second = viewsRepo.get(secondId)
                assertNotNull(first, "Expected first guild view to exist")
                assertNotNull(second, "Expected second guild view to exist")
                assertTrue(first!!.entitiesIds.isNotEmpty(), "Expected first guild view to have members")
                assertEquals(first.entitiesIds.toSet(), second!!.entitiesIds.toSet())
            }
        }
    }

    @Given("they have an existing {string} view")
    fun existingView(game: String) {
        createView(game)
        processViewsSubscription()
    }

    @And("the views subscription processes pending events")
    fun processViewsSubscription() {
        runBlocking { SharedInfrastructure.subscriptions.views.processPendingEvents() }
    }

    @And("the sync subscription processes pending events")
    fun processSyncSubscription() {
        runBlocking {
            when (scenarioVariables.game) {
                Game.LOL -> SharedInfrastructure.subscriptions.syncLol.processPendingEvents()
                Game.WOW -> SharedInfrastructure.subscriptions.syncWow.processPendingEvents()
                Game.WOW_HC -> SharedInfrastructure.subscriptions.syncWowHc.processPendingEvents()
                null -> error("No game set in scenario variables")
            }
        }
    }

    @When("they GET the created view")
    fun getCreatedView() {
        scenarioVariables.response = runBlocking {
            client.get("/api/views/${scenarioVariables.viewId}") {
                scenarioVariables.token?.let { bearerAuth(it) }
            }
        }
    }

    @When("they edit the created view to be named {string}")
    fun editCreatedView(name: String) {
        scenarioVariables.response = runBlocking {
            client.put("/api/views/${scenarioVariables.viewId}") {
                contentType(ContentType.Application.Json)
                scenarioVariables.token?.let { bearerAuth(it) }
                setBody(
                    ViewRequest(
                        name = name,
                        published = false,
                        entities = emptyList(),
                        game = scenarioVariables.game!!,
                        featured = false
                    )
                )
            }
        }
        if (scenarioVariables.response.status == HttpStatusCode.OK) {
            val body = runBlocking { scenarioVariables.response.bodyAsText() }
            scenarioVariables.operationId = Json.parseToJsonElement(body).jsonObject["id"]!!.jsonPrimitive.content
        }
    }

    @When("they edit a view at {string} to be named {string}")
    fun editViewAtPath(path: String, name: String) {
        scenarioVariables.response = runBlocking {
            client.put(path) {
                contentType(ContentType.Application.Json)
                scenarioVariables.token?.let { bearerAuth(it) }
                setBody(
                    ViewRequest(
                        name = name,
                        published = false,
                        entities = emptyList(),
                        game = Game.LOL,
                        featured = false
                    )
                )
            }
        }
    }

    @When("they patch the created view to be named {string}")
    fun patchCreatedView(name: String) {
        scenarioVariables.response = runBlocking {
            client.patch("/api/views/${scenarioVariables.viewId}") {
                contentType(ContentType.Application.Json)
                scenarioVariables.token?.let { bearerAuth(it) }
                setBody(ViewPatchRequest(name = name, game = scenarioVariables.game!!))
            }
        }
        if (scenarioVariables.response.status == HttpStatusCode.OK) {
            val body = runBlocking { scenarioVariables.response.bodyAsText() }
            scenarioVariables.operationId = Json.parseToJsonElement(body).jsonObject["id"]!!.jsonPrimitive.content
        }
    }

    @When("they DELETE the created view")
    fun deleteCreatedView() {
        scenarioVariables.response = runBlocking {
            client.delete("/api/views/${scenarioVariables.viewId}") {
                scenarioVariables.token?.let { bearerAuth(it) }
            }
        }
        if (scenarioVariables.response.status == HttpStatusCode.OK) {
            val body = runBlocking { scenarioVariables.response.bodyAsText() }
            scenarioVariables.operationId = Json.parseToJsonElement(body).jsonObject["id"]!!.jsonPrimitive.content
        }
    }

    @When("they request DELETE {string}")
    fun requestDelete(path: String) {
        scenarioVariables.response = runBlocking {
            client.delete(path) {
                scenarioVariables.token?.let { bearerAuth(it) }
            }
        }
    }

    @Then("GET {string} returns {int} view(s)")
    fun getViewsReturnsCount(path: String, expectedCount: Int) {
        val response = runBlocking {
            client.get(path) {
                scenarioVariables.token?.let { bearerAuth(it) }
            }
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = runBlocking { response.bodyAsText() }
        val records = Json.parseToJsonElement(body).jsonObject["records"]!!.jsonArray
        assertEquals(expectedCount, records.size)
    }
}
