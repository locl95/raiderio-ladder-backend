package com.kos.clients.domain

import com.kos.common.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

data class BlizzardCredentials(val client: String, val secret: String)

@Serializable
data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String,
    @SerialName("expires_in")
    val expiresIn: Long,
    val sub: String
)

object NameExtractorSerializer : SingleFieldSerializer<String>(
    extractValue = { it.requireString("name") },
    encodeValue = { builder, value -> builder.put("name", value) }
)

object EffectiveExtractorSerializer : SingleFieldSerializer<Int>(
    extractValue = { it.requireInt("effective") },
    encodeValue = { builder, value -> builder.put("effective", value) }
)

object ValueExtractorSerializer : SingleFieldSerializer<Double>(
    extractValue = { it.requireDouble("value") },
    encodeValue = { builder, value -> builder.put("value", value) }
)

object NestedDisplayableStringExtractorSerializer : SingleFieldSerializer<String>(
    extractValue = { it.requireNestedString("display", "display_string") },
    encodeValue = { builder, value ->
        builder.putJsonObject("display") {
            put("display_string", value)
        }
    }
)

object DisplayableStringExtractorSerializer : SingleFieldSerializer<String>(
    extractValue = { it.requireString("display_string") },
    encodeValue = { builder, value -> builder.put("display_string", value) }
)

object DescriptionStringExtractorSerializer : SingleFieldSerializer<String>(
    extractValue = { it.requireString("description") },
    encodeValue = { builder, value -> builder.put("description", value) }
)


object DescriptionListSerializer : ListFieldSerializer<String>(
    elementSerializer = DescriptionStringExtractorSerializer,
    extractElement = { it.requireString("description") }
)

object DisplayStringListSerializer : ListFieldSerializer<String>(
    elementSerializer = DisplayableStringExtractorSerializer,
    extractElement = { it.requireString("display_string") }
)

object NestedDisplayableStringListSerializer : ListFieldSerializer<String>(
    elementSerializer = NestedDisplayableStringExtractorSerializer,
    extractElement = { it.requireNestedString("display", "display_string") }
)

object WowPriceSerializer : KSerializer<WowPriceResponse> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("WowPrice") {
            element<String>("header")
            element<String>("gold")
            element<String>("silver")
            element<String>("copper")
        }

    override fun deserialize(decoder: Decoder): WowPriceResponse {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement().jsonObject
        val display = jsonObject["display_strings"]!!.jsonObject
        return WowPriceResponse(
            header = display["header"]!!.jsonPrimitive.content,
            gold = display["gold"]!!.jsonPrimitive.content,
            silver = display["silver"]!!.jsonPrimitive.content,
            copper = display["copper"]!!.jsonPrimitive.content
        )
    }

    override fun serialize(encoder: Encoder, value: WowPriceResponse) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(
            buildJsonObject {
                put(
                    "display_strings",
                    buildJsonObject {
                        put("header", value.header)
                        put("gold", value.gold)
                        put("silver", value.silver)
                        put("copper", value.copper)
                    }
                )
            }
        )
    }
}

@Serializable
data class Realm(val name: String, val id: Long)

@Serializable
data class GetWowCharacterResponse(
    val id: Long,
    val name: String,
    val level: Int,
    @SerialName("is_ghost")
    val isDead: Boolean? = null,
    @SerialName("is_self_found")
    val isSelfFound: Boolean? = null,
    @SerialName("average_item_level")
    val averageItemLevel: Int,
    @SerialName("equipped_item_level")
    val equippedItemLevel: Int,
    @Serializable(with = NameExtractorSerializer::class)
    @SerialName("character_class")
    val characterClass: String,
    @Serializable(with = NameExtractorSerializer::class)
    val faction: String,
    @Serializable(with = NameExtractorSerializer::class)
    val race: String,
    @Serializable(with = NameExtractorSerializer::class)
    val gender: String,
    val realm: Realm,
    @Serializable(with = NameExtractorSerializer::class)
    val guild: String? = null,
    val experience: Int,
    @SerialName("last_login_timestamp")
    val lastLogin: Long
)

@Serializable
data class GetWowCharacterStatsResponse(
    val health: Int,
    val power: Int,
    @SerialName("power_type")
    @Serializable(with = NameExtractorSerializer::class)
    val powerType: String,
    @Serializable(with = EffectiveExtractorSerializer::class)
    val strength: Int,
    @Serializable(with = EffectiveExtractorSerializer::class)
    val agility: Int,
    @Serializable(with = EffectiveExtractorSerializer::class)
    val intellect: Int,
    @Serializable(with = EffectiveExtractorSerializer::class)
    val stamina: Int,
    @Serializable(with = ValueExtractorSerializer::class)
    @SerialName("melee_crit")
    val meleeCrit: Double,
    @SerialName("attack_power")
    val attackPower: Int,
    @SerialName("main_hand_damage_min")
    val mainHandDamageMin: Double,
    @SerialName("main_hand_damage_max")
    val mainHandDamageMax: Double,
    @SerialName("main_hand_speed")
    val mainHandSpeed: Double,
    @SerialName("main_hand_dps")
    val mainHandDps: Double,
    @SerialName("off_hand_damage_min")
    val offHandDamageMin: Double,
    @SerialName("off_hand_damage_max")
    val offHandDamageMax: Double,
    @SerialName("off_hand_speed")
    val offHandSpeed: Double,
    @SerialName("off_hand_dps")
    val offHandDps: Double,
    @SerialName("spell_power")
    val spellPower: Double,
    @SerialName("spell_penetration")
    val spellPenetration: Double,
    @Serializable(with = ValueExtractorSerializer::class)
    @SerialName("spell_crit")
    val spellCrit: Double,
    @SerialName("mana_regen")
    val manaRegen: Double,
    @SerialName("mana_regen_combat")
    val manaRegenCombat: Double,
    @Serializable(with = EffectiveExtractorSerializer::class)
    val armor: Int,
    @Serializable(with = ValueExtractorSerializer::class)
    val dodge: Double,
    @Serializable(with = ValueExtractorSerializer::class)
    val parry: Double,
    @Serializable(with = ValueExtractorSerializer::class)
    val block: Double,
    @SerialName("ranged_crit")
    @Serializable(with = ValueExtractorSerializer::class)
    val rangedCrit: Double,
    @Serializable(with = EffectiveExtractorSerializer::class)
    val spirit: Int,
    @Serializable(with = EffectiveExtractorSerializer::class)
    val defense: Int,
    @SerialName("fire_resistance")
    @Serializable(with = EffectiveExtractorSerializer::class)
    val fireResistance: Int,
    @SerialName("holy_resistance")
    @Serializable(with = EffectiveExtractorSerializer::class)
    val holyResistance: Int,
    @SerialName("shadow_resistance")
    @Serializable(with = EffectiveExtractorSerializer::class)
    val shadowResistance: Int,
    @Serializable(with = EffectiveExtractorSerializer::class)
    @SerialName("nature_resistance")
    val natureResistance: Int,
    @Serializable(with = EffectiveExtractorSerializer::class)
    @SerialName("arcane_resistance")
    val arcaneResistance: Int
)

@Serializable
data class AssetKeyValue(val key: String, val value: String)

@Serializable
data class GetWowMediaResponse(
    val assets: List<AssetKeyValue>
)

@Serializable(with = WowPriceSerializer::class)
data class WowPriceResponse(val header: String, val gold: String, val silver: String, val copper: String)

@Serializable
data class WowPrice(val header: String, val gold: String, val silver: String, val copper: String) {
    companion object {
        fun apply(priceResponse: WowPriceResponse): WowPrice = WowPrice(
            priceResponse.header,
            priceResponse.gold,
            priceResponse.silver,
            priceResponse.copper
        )
    }
}

@Serializable
data class WowWeaponStatsResponse(
    @Serializable(with = DisplayableStringExtractorSerializer::class)
    val damage: String,
    @Serializable(with = DisplayableStringExtractorSerializer::class)
    val dps: String,
    @SerialName("attack_speed")
    @Serializable(with = DisplayableStringExtractorSerializer::class)
    val attackSpeed: String
)

@Serializable
data class WowPreviewItem(
    @Serializable(with = NameExtractorSerializer::class)
    val quality: String,
    @SerialName("item_subclass")
    @Serializable(with = NameExtractorSerializer::class)
    val itemSubclass: String,
    @SerialName("inventory_type")
    @Serializable(with = NameExtractorSerializer::class)
    val slot: String,
    @Serializable(with = NameExtractorSerializer::class)
    val binding: String? = null,
    @Serializable(with = NestedDisplayableStringExtractorSerializer::class)
    val armor: String? = null,
    @Serializable(with = NestedDisplayableStringListSerializer::class)
    val stats: List<String> = listOf(),
    @Serializable(with = DescriptionListSerializer::class)
    val spells: List<String> = listOf(),
    @SerialName("sell_price")
    val sellPrice: WowPriceResponse? = null,
    @Serializable(with = DisplayableStringExtractorSerializer::class)
    val durability: String? = null,
    val weapon: WowWeaponStatsResponse? = null
)

@Serializable
data class GetWowItemResponse(
    val id: Long,
    val name: String,
    val level: Int,
    @SerialName("required_level")
    val requiredLevel: Int,
    @SerialName("preview_item")
    val previewItem: WowPreviewItem
)

@Serializable
data class WowItemId(val id: Long)

@Serializable
data class WowItemSlot(val name: String)

@Serializable
data class WowItemQuality(val type: String)

@Serializable
data class WowEquippedItemResponse(
    val item: WowItemId,
    val slot: WowItemSlot,
    @Serializable(with = DisplayStringListSerializer::class)
    val enchantments: List<String> = listOf(),
    val quality: WowItemQuality,
    val name: String,
)

@Serializable
data class GetWowEquipmentResponse(
    @SerialName("equipped_items")
    val equippedItems: List<WowEquippedItemResponse>
)

@Serializable
data class GetWowSpecializationsResponse(
    @SerialName("specialization_groups")
    val specializationGroups: List<SpecializationGroup>
)

@Serializable
data class SpecializationGroup(
    val specializations: List<Specialization>? = null,
    @SerialName("is_active")
    val isActive: Boolean
)

@Serializable
data class Specialization(
    @SerialName("specialization_name")
    val specializationName: String,
    @SerialName("spent_points")
    val spentPoints: Int,
    val talents: List<Talent>
)

@Serializable
data class Talent(
    val talent: TalentInfo,
    @SerialName("talent_rank")
    val talentRank: Int
)

@Serializable
data class TalentInfo(
    val id: Int
)

@Serializable
data class GetWowRealmResponse(val category: String)

@Serializable
data class WowWeaponDisplayableStats(
    val damage: String,
    val dps: String,
    val attackSpeed: String
) {
    companion object {
        fun apply(response: WowWeaponStatsResponse) =
            WowWeaponDisplayableStats(
                response.damage,
                response.dps,
                response.attackSpeed
            )
    }
}

@Serializable
data class WowItem(
    val id: Long,
    val slot: String,
    val quality: String,
    val name: String,
    val level: Int,
    val binding: String?,
    val requiredLevel: Int,
    val itemSubclass: String,
    val armor: String?,
    val stats: List<String>,
    val spells: List<String>,
    val sellPrice: WowPrice?,
    val durability: String?,
    val weaponStats: WowWeaponDisplayableStats?,
    val icon: String?,
    val enchantments: List<String>
)

@Serializable
data class WowResource(val type: String, val value: Int)

@Serializable
data class WowWeaponStats(
    val minDamage: Double,
    val maxDamage: Double,
    val speed: Double,
    val dps: Double
)

@Serializable
data class WowResistances(
    val fire: Int,
    val holy: Int,
    val shadow: Int,
    val nature: Int,
    val arcane: Int
)

@Serializable
data class WowStats(
    val health: Int,
    val resource: WowResource,
    val strength: Int,
    val agility: Int,
    val intellect: Int,
    val stamina: Int,
    val meleeCrit: Double,
    val attackPower: Int,
    val mainHandStats: WowWeaponStats,
    val offHandStats: WowWeaponStats,
    val spellPower: Double,
    val spellPenetration: Double,
    val spellCrit: Double,
    val manaRegen: Double,
    val manaRegenCombat: Double,
    val armor: Int,
    val dodge: Double,
    val parry: Double,
    val block: Double,
    val rangedCrit: Double,
    val spirit: Int,
    val defense: Int,
    val resistances: WowResistances
) {
    companion object {
        fun apply(response: GetWowCharacterStatsResponse): WowStats =
            WowStats(
                response.health,
                WowResource(response.powerType, response.power),
                response.strength,
                response.agility,
                response.intellect,
                response.stamina,
                response.meleeCrit,
                response.attackPower,
                WowWeaponStats(
                    response.mainHandDamageMin,
                    response.mainHandDamageMax,
                    response.mainHandSpeed,
                    response.mainHandDps
                ),
                WowWeaponStats(
                    response.offHandDamageMin,
                    response.offHandDamageMax,
                    response.offHandSpeed,
                    response.offHandDps
                ),
                response.spellPower,
                response.spellPenetration,
                response.spellCrit,
                response.manaRegen,
                response.manaRegenCombat,
                response.armor,
                response.dodge,
                response.parry,
                response.block,
                response.rangedCrit,
                response.spirit,
                response.defense,
                WowResistances(
                    response.fireResistance,
                    response.holyResistance,
                    response.shadowResistance,
                    response.natureResistance,
                    response.arcaneResistance
                )
            )
    }
}

@Serializable
data class WowTalent(
    val id: Int,
    val rank: Int
)

@Serializable
data class WowSpecialization(
    val name: String,
    val points: Int,
    val talents: List<WowTalent>
) {
    companion object {
        fun apply(specialization: Specialization): WowSpecialization =
            WowSpecialization(
                specialization.specializationName,
                specialization.spentPoints,
                specialization.talents.map {
                    WowTalent(
                        it.talent.id,
                        it.talentRank
                    )
                }
            )
    }
}

@Serializable
data class WowTalents(
    val wowHeadEmbeddedTalents: String?,
    val specializations: List<WowSpecialization>
)

@Serializable
data class HardcoreData(
    val id: Long,
    val name: String,
    val level: Int,
    val isDead: Boolean,
    val isSelfFound: Boolean,
    val averageItemLevel: Int,
    val equippedItemLevel: Int,
    val characterClass: String,
    val race: String,
    val gender: String,
    val realm: String,
    val region: String,
    val guild: String?,
    val experience: Int,
    val items: List<WowItem>,
    val faction: String,
    val avatar: String?,
    val stats: WowStats,
    val specializations: WowTalents,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val lastLogin: OffsetDateTime
) : Data {
    companion object {
        fun apply(
            region: String,
            characterResponse: GetWowCharacterResponse,
            mediaResponse: GetWowMediaResponse,
            alreadyExistentItems: List<WowItem>,
            //TODO: Refactor this triple into an apply
            equipmentResponse: List<Triple<WowEquippedItemResponse, GetWowItemResponse, GetWowMediaResponse?>>,
            statsResponse: GetWowCharacterStatsResponse,
            specializationsResponse: GetWowSpecializationsResponse,
            wowHeadEmbeddedResponse: RaiderioWowHeadEmbeddedResponse?
        ) = HardcoreData(
            characterResponse.id,
            characterResponse.name,
            characterResponse.level,
            characterResponse.isDead ?: false,
            characterResponse.isSelfFound ?: false,
            characterResponse.averageItemLevel,
            characterResponse.equippedItemLevel,
            characterResponse.characterClass,
            characterResponse.race,
            characterResponse.gender,
            characterResponse.realm.name,
            region,
            characterResponse.guild,
            characterResponse.experience,
            alreadyExistentItems + equipmentResponse.map { (equipped, item, icon) ->
                WowItem(
                    item.id,
                    equipped.slot.name,
                    item.previewItem.quality,
                    item.name,
                    item.level,
                    item.previewItem.binding,
                    item.requiredLevel,
                    item.previewItem.itemSubclass,
                    item.previewItem.armor,
                    item.previewItem.stats,
                    item.previewItem.spells,
                    item.previewItem.sellPrice?.let { WowPrice.apply(it) },
                    item.previewItem.durability,
                    item.previewItem.weapon?.let { WowWeaponDisplayableStats.apply(it) },
                    icon?.assets?.find { it.key == "icon" }?.value,
                    equipped.enchantments
                )
            },
            characterResponse.faction,
            mediaResponse.assets.find { it.key == "avatar" }?.value,
            WowStats.apply(statsResponse),
            WowTalents(
                wowHeadEmbeddedTalents = wowHeadEmbeddedResponse?.talentLoadout?.wowheadCalculator,
                specializations = specializationsResponse.specializationGroups.firstOrNull { it.isActive }?.specializations?.map { specialization ->
                    WowSpecialization.apply(specialization)
                }.orEmpty()
            ),
            OffsetDateTime.ofInstant(Instant.ofEpochMilli(characterResponse.lastLogin), ZoneId.systemDefault())
        )
    }
}

@Serializable
data class WowRosterMemberRealmResponse(
    val slug: String
)

@Serializable
data class WowCharacterResponse(
    val name: String,
    val level: Int,
    val id: Long? = null,
    val realm: WowRosterMemberRealmResponse? = null
)

@Serializable
data class WowMemberResponse(
    val character: WowCharacterResponse
)

@Serializable
data class WowGuildResponse(
    val id: Long
)

@Serializable
data class GetWowRosterResponse(
    val members: List<WowMemberResponse>,
    val guild: WowGuildResponse
)