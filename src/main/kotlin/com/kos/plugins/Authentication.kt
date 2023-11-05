package com.kos.plugins

import arrow.core.Either
import com.kos.auth.AuthService
import com.kos.credentials.CredentialsService
import io.ktor.server.application.*
import io.ktor.server.auth.*

fun Application.configureAuthentication(authService: AuthService, credentialsService: CredentialsService) {
    install(Authentication) {
        basic("auth-basic") {
            validate { credentials ->
                if(credentialsService.validateCredentials(credentials.name, credentials.password)) UserIdPrincipal(credentials.name)
                else null
            }
        }
        bearer("auth-bearer") {
            authenticate {
                when(val eitherOwnerOrError = authService.validateToken(it.token)) {
                    is Either.Right -> UserIdPrincipal(eitherOwnerOrError.value)
                    else -> null
                }
            }
        }
    }
}