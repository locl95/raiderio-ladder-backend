package com.kos.clients.blizzard

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.clients.domain.*

interface BlizzardClient {
    suspend fun getClassicProfile(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, GetWowCharacterResponse>

    suspend fun getRetailProfile(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, GetWowCharacterResponse>

    suspend fun getCharacterMedia(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, GetWowMediaResponse>

    suspend fun getCharacterEquipment(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, GetWowEquipmentResponse>

    suspend fun getCharacterSpecializations(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, GetWowSpecializationsResponse>

    suspend fun getCharacterStats(
        region: String,
        realm: String,
        character: String
    ): Either<ClientError, GetWowCharacterStatsResponse>

    suspend fun getItemMedia(region: String, id: Long): Either<ClientError, GetWowMediaResponse>
    suspend fun getItem(region: String, id: Long): Either<ClientError, GetWowItemResponse>
    suspend fun getRealm(region: String, id: Long): Either<ClientError, GetWowRealmResponse>
    suspend fun getHardcoreGuildRoster(region: String, realm: String, guild: String): Either<ClientError, GetWowRosterResponse>
    suspend fun getRetailGuildRoster(
        region: String,
        realm: String,
        guild: String
    ): Either<ClientError, GetWowRosterResponse>

}