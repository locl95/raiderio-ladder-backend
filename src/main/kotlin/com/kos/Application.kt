package com.kos

import com.kos.auth.AuthService
import com.kos.auth.repository.AuthDatabaseRepository
import com.kos.characters.CharactersService
import com.kos.characters.repository.CharactersDatabaseRepository
import com.kos.common.DatabaseFactory
import com.kos.datacache.DataCacheService
import com.kos.datacache.repository.DataCacheDatabaseRepository
import com.kos.plugins.configureAuthentication
import com.kos.plugins.configureCors
import com.kos.plugins.configureRouting
import com.kos.plugins.configureSerialization
import com.kos.raiderio.RaiderIoHTTPClient
import com.kos.views.ViewsService
import com.kos.views.repository.ViewsDatabaseRepository
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

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

    val authRepository = AuthDatabaseRepository()
    val authService = AuthService(authRepository)

    val charactersRepository = CharactersDatabaseRepository()
    val charactersService = CharactersService(charactersRepository)

    val dataCacheRepository = DataCacheDatabaseRepository()
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
