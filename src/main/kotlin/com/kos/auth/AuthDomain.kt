package com.kos.auth

import com.kos.common.OffsetDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
data class Authorization(
    val userName: String,
    val token: String,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val lastUsed: OffsetDateTime,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val validUntil: OffsetDateTime?,
    val isAccess: Boolean
)

fun Authorization.isRefresh() = !isAccess

@Serializable
data class LoginResponse(
    val accessToken: Authorization?,
    val refreshToken: Authorization?
)

interface TokenError {
    val token: String
}

data class TokenNotFound(override val token: String) : TokenError
data class TokenExpired(override val token: String, val validUntil: OffsetDateTime) : TokenError
data class TokenWrongMode(override val token: String, val isAccess: Boolean) : TokenError
