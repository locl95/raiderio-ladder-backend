package com.kos.entities.domain

import kotlinx.serialization.Serializable

data class Spec(val name: String, val externalSpec: Int, val internalSpec: Int)
data class Class(val `class`: String, val specs: List<Spec>)

@Serializable
data class WowEntityRequest(
    override val name: String,
    val region: String,
    val realm: String,
    val blizzardId: Long? = null,
    override val alias: String? = null
) :
    EntityRequest, InsertEntityRequest {
    override fun toEntity(id: Long) = WowEntity(id, name, region, realm, blizzardId)
    override fun same(other: Entity): Boolean {
        return when (other) {
            is WowEntity -> if (this.blizzardId != null && other.blizzardId != null) {
                this.blizzardId == other.blizzardId
            } else {
                this.name == other.name && this.region == other.region && this.realm == other.realm
            }

            else -> false
        }
    }

    override fun toRequest(): EntityRequest = this.copy(blizzardId = null)
    override fun toResponse(): EntityResponse = WowEntityResponse(name, region, realm)
}

@Serializable
data class WowEntityResponse(
    override val name: String,
    val region: String,
    val realm: String
) : EntityResponse

@Serializable
data class WowEntity(
    override val id: Long,
    override val name: String,
    val region: String,
    val realm: String,
    val blizzardId: Long?
) : Entity {
    fun specsWithName(`class`: String): List<Spec> = classes.find { it.`class` == `class` }?.specs.orEmpty()
    override fun toRequest(): WowEntityRequest = WowEntityRequest(this.name, this.region, this.realm, null)
}

//TODO: this should be static data in db
val classes: List<Class> = listOf(
    Class(
        "Priest", listOf(
            Spec(
                "Discipline",
                256,
                0
            ),
            Spec(
                "Holy Priest",
                257,
                1
            ),
            Spec(
                "Shadow",
                258,
                2
            )
        )
    ),
    Class(
        "Paladin", listOf(
            Spec(
                "Holy Paladin",
                65,
                0
            ),
            Spec(
                "Protection Paladin",
                66,
                1
            ),
            Spec(
                "Retribution",
                70,
                2
            )
        )
    ),
    Class(
        "Paladin", listOf(
            Spec(
                "Holy Paladin",
                65,
                0
            ),
            Spec(
                "Protection Paladin",
                66,
                1
            ),
            Spec(
                "Retribution",
                70,
                2
            )
        )
    ),
    Class(
        "Rogue", listOf(
            Spec(
                "Subtlety",
                261,
                2
            ),
            Spec(
                "Outlaw",
                260,
                1
            ),
            Spec(
                "Assassination",
                259,
                0
            )
        )
    ),
    Class(
        "Druid", listOf(
            Spec(
                "Balance",
                102,
                0
            ),
            Spec(
                "Feral",
                103,
                1
            ),
            Spec(
                "Guardian",
                104,
                2
            ),
            Spec(
                "Restoration Druid",
                105,
                3
            )
        )
    ),
    Class(
        "Monk", listOf(
            Spec(
                "Brew Master",
                268,
                0
            ),
            Spec(
                "Wind Walker",
                269,
                2
            ),
            Spec(
                "Mist Weaver",
                270,
                1
            )
        )
    ),
    Class(
        "Evoker", listOf(
            Spec(
                "Devastation",
                1467,
                0
            ),
            Spec(
                "Preservation",
                1468,
                1
            ),
            Spec(
                "Augmentation",
                1473,
                2
            )
        )
    ),
    Class(
        "Shaman", listOf(
            Spec(
                "Elemental",
                262,
                0
            ),
            Spec(
                "Enhancement",
                263,
                1
            ),
            Spec(
                "Restoration Shaman",
                264,
                2
            )
        )
    ),
    Class(
        "Warrior", listOf(
            Spec(
                "Arms",
                71,
                0
            ),
            Spec(
                "Fury",
                72,
                1
            ),
            Spec(
                "Protection Warrior",
                73,
                2
            )
        )
    ),
    Class(
        "Hunter", listOf(
            Spec(
                "Beast Mastery",
                253,
                0
            ),
            Spec(
                "Marksmanship",
                254,
                1
            ),
            Spec(
                "Survival",
                255,
                2
            )
        )
    ),
    Class(
        "Demon Hunter", listOf(
            Spec(
                "Havoc",
                577,
                0
            ),
            Spec(
                "Vengeance",
                581,
                1
            ),
            Spec(
                "Devourer",
                1480,
                2
            )
        )
    ),
    Class(
        "Mage", listOf(
            Spec(
                "Arcane",
                62,
                0
            ),
            Spec(
                "Fire",
                63,
                1
            ),
            Spec(
                "Frost Mage",
                64,
                2
            )
        )
    ),
    Class(
        "Death Knight",
        listOf(
            Spec(
                "Blood",
                250,
                0
            ),
            Spec(
                "Frost Death Knight",
                251,
                1
            ),
            Spec(
                "Unholy",
                252,
                2
            )
        ),
    ),
    Class(
        "Warlock", listOf(
            Spec(
                "Affliction",
                265,
                0
            ),
            Spec(
                "Demonology",
                266,
                1
            ),
            Spec(
                "Destruction",
                267,
                2
            )
        )
    )
)