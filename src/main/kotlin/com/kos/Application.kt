package com.kos

import com.kos.auth.AuthService
import com.kos.auth.repository.AuthDatabaseRepository
import com.kos.characters.repository.CharactersInMemoryRepository
import com.kos.characters.CharactersService
import com.kos.common.DatabaseFactory
import com.kos.datacache.DataCacheInMemoryRepository
import com.kos.datacache.DataCacheService
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.kos.plugins.*
import com.kos.raiderio.RaiderIoHTTPClient
import com.kos.views.ViewsService
import com.kos.views.repository.ViewsDatabaseRepository
import io.ktor.client.*
import io.ktor.client.engine.cio.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {

    DatabaseFactory.init(
        "org.h2.Driver",
        "jdbc:h2:file:./build/db",
        "",
        "",
        mustClean = false
    )

    /* val authRepository = AuthInMemoryRepository(
         Pair(
             listOf(User("eric", "1234")),
             listOf(Authorization("admin", "admin", OffsetDateTime.now(), OffsetDateTime.now().plusHours(24)))
         )
     ) */

    val authRepository = AuthDatabaseRepository()
    val authService = AuthService(authRepository)

    val charactersRepository = CharactersInMemoryRepository()
    val charactersService = CharactersService(charactersRepository)

    val dataCacheRepository = DataCacheInMemoryRepository()
    val dataCacheService = DataCacheService(dataCacheRepository)

    val viewsRepository = ViewsDatabaseRepository()
    val client = HttpClient(CIO)
    val raiderIoHTTPClient = RaiderIoHTTPClient(client)
    val viewsService = ViewsService(viewsRepository, charactersService, dataCacheService, raiderIoHTTPClient)


    configureAuthentication(authService)
    configureCors()
    configureRouting(authService, viewsService)
    configureSerialization()
}
