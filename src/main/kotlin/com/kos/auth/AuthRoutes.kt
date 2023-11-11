package com.kos.auth

import arrow.core.Either
import arrow.core.left
import com.kos.credentials.Activities
import com.kos.credentials.CredentialsService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRouting(authService: AuthService, credentialsService: CredentialsService) {

    route("/auth") {
        authenticate("auth-basic") {
            post {
                when (val userIdPrincipal = call.principal<UserIdPrincipal>()) {
                    null -> call.respond(HttpStatusCode.Unauthorized)
                    else -> {
                        if (credentialsService.hasPermissions(userIdPrincipal.name, Activities.login)) {
                            when (val token = authService.login(userIdPrincipal.name)) {
                                null -> call.respond(HttpStatusCode.BadRequest)
                                else -> call.respond(HttpStatusCode.OK, token)
                            }
                        } else call.respond(HttpStatusCode.Forbidden)
                    }
                }
            }
        }
        authenticate("auth-bearer") {
            delete {
                when (val id = call.principal<UserIdPrincipal>()) {
                    null -> call.respond(HttpStatusCode.Unauthorized)
                    else -> if (credentialsService.hasPermissions(id.name, Activities.logout)) {
                        authService.logout(id.name)
                        call.respond(HttpStatusCode.OK)
                    } else call.respond(HttpStatusCode.Forbidden)
                }
            }
        }
        route("/refresh") {
            authenticate("auth-bearer") {
                post {
                    when (call.principal<UserIdPrincipal>()) {
                        null -> call.respond(HttpStatusCode.Unauthorized)
                        else -> {
                            val token: String? = call.request.authorization()?.removePrefix("Bearer ")
                            token?.let {
                                when (val tokenOrError = authService.refresh(it)) {
                                    is Either.Left -> call.respond(HttpStatusCode.BadRequest, tokenOrError.left())
                                    is Either.Right -> tokenOrError.value?.let { auth ->
                                        call.respond(HttpStatusCode.OK, auth)
                                    } ?: call.respond(HttpStatusCode.NotFound)
                                }
                                call.respond(HttpStatusCode.OK, authService.refresh(it))
                            } ?: call.respond(HttpStatusCode.Unauthorized)
                        }
                    }
                }
            }
        }
    }
}