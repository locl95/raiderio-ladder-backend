package com.kos.entities

import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.EntitiesTestHelper.basicLolEntityEnrichedRequest
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.domain.WowEntityRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntitiesDomainTest {

    @Test
    fun `i can find every wow spec`() {
        val warriorSpecs = basicWowEntity.specsWithName("Warrior").map { it.name }.toSet()
        val paladinSpecs = basicWowEntity.specsWithName("Paladin").map { it.name }.toSet()
        val hunterSpecs = basicWowEntity.specsWithName("Hunter").map { it.name }.toSet()
        val rogueSpecs = basicWowEntity.specsWithName("Rogue").map { it.name }.toSet()
        val priestSpecs = basicWowEntity.specsWithName("Priest").map { it.name }.toSet()
        val shamanSpecs = basicWowEntity.specsWithName("Shaman").map { it.name }.toSet()
        val mageSpecs = basicWowEntity.specsWithName("Mage").map { it.name }.toSet()
        val warlockSpecs = basicWowEntity.specsWithName("Warlock").map { it.name }.toSet()
        val monkSpecs = basicWowEntity.specsWithName("Monk").map { it.name }.toSet()
        val druidSpecs = basicWowEntity.specsWithName("Druid").map { it.name }.toSet()
        val demonHunterSpecs = basicWowEntity.specsWithName("Demon Hunter").map { it.name }.toSet()
        val deathKnightSpecs = basicWowEntity.specsWithName("Death Knight").map { it.name }.toSet()
        val evokerSpecs = basicWowEntity.specsWithName("Evoker").map { it.name }.toSet()

        val expectedWarriorSpecs = setOf("Protection Warrior", "Arms", "Fury")
        val expectedPaladinSpecs = setOf("Protection Paladin", "Retribution", "Holy Paladin")
        val expectedHunterSpecs = setOf("Beast Mastery", "Survival", "Marksmanship")
        val expectedRogueSpecs = setOf("Outlaw", "Assassination", "Subtlety")
        val expectedPriestSpecs = setOf("Shadow", "Holy Priest", "Discipline")
        val expectedShamanSpecs = setOf("Enhancement", "Elemental", "Restoration Shaman")
        val expectedMageSpecs = setOf("Arcane", "Fire", "Frost Mage")
        val expectedWarlockSpecs = setOf("Affliction", "Demonology", "Destruction")
        val expectedMonkSpecs = setOf("Wind Walker", "Brew Master", "Mist Weaver")
        val expectedDruidSpecs = setOf("Guardian", "Balance", "Feral", "Restoration Druid")
        val expectedDemonHunterSpecs = setOf("Havoc", "Vengeance", "Devourer")
        val expectedDeathKnightSpecs = setOf("Blood", "Frost Death Knight", "Unholy")
        val expectedEvokerSpecs = setOf("Devastation", "Preservation", "Augmentation")

        val specs = listOf(
            warriorSpecs,
            paladinSpecs,
            hunterSpecs,
            rogueSpecs,
            priestSpecs,
            shamanSpecs,
            mageSpecs,
            warlockSpecs,
            monkSpecs,
            druidSpecs,
            demonHunterSpecs,
            deathKnightSpecs,
            evokerSpecs
        )

        val expectedSpecs = listOf(
            expectedWarriorSpecs,
            expectedPaladinSpecs,
            expectedHunterSpecs,
            expectedRogueSpecs,
            expectedPriestSpecs,
            expectedShamanSpecs,
            expectedMageSpecs,
            expectedWarlockSpecs,
            expectedMonkSpecs,
            expectedDruidSpecs,
            expectedDemonHunterSpecs,
            expectedDeathKnightSpecs,
            expectedEvokerSpecs
        )

        expectedSpecs.zip(specs).forEach {
            assertEquals(it.first, it.second)
        }

    }

    @Test
    fun `toCharacter should create a Character with the correct properties for lol`() {
        val lolCharacterRequest = basicLolEntityEnrichedRequest
        val entity = lolCharacterRequest.toEntity(1L)
        assertEquals(1L, entity.id)
        assertEquals(basicLolEntity.name, entity.name)
        assertEquals(basicLolEntity.tag, entity.tag)
        assertEquals(basicLolEntity.puuid, entity.puuid)
        assertEquals(basicLolEntity.summonerIcon, entity.summonerIcon)
        assertEquals(basicLolEntity.summonerLevel, entity.summonerLevel)
    }

    @Test
    fun `same should return true for identical lol entities`() {
        val lolCharacterRequest = basicLolEntityEnrichedRequest
        val entity = lolCharacterRequest.toEntity(1L)
        val result = lolCharacterRequest.same(entity)
        assertTrue(result)
    }

    @Test
    fun `same should return true for lol entities that share same puuid or summonerId regardless of other fields`() {
        val lolCharacterRequest = basicLolEntityEnrichedRequest
        val entity = lolCharacterRequest.toEntity(1L).copy(name = "diff name", tag = "diff tag")
        val result = lolCharacterRequest.same(entity)
        assertTrue(result)
    }

    @Test
    fun `same should return true for identical wow entities`() {
        val wowCharacterRequest = WowEntityRequest("Aragorn", "Middle Earth", "Gondor")
        val entity = wowCharacterRequest.toEntity(2L)
        val result = wowCharacterRequest.same(entity)
        assertTrue(result)
    }

    @Test
    fun `same should return false for wow entities with different properties`() {
        val wowCharacterRequest = WowEntityRequest("Legolas", "Middle Earth", "Lothlorien")
        val entity = wowCharacterRequest.toEntity(3L)
        val result = wowCharacterRequest.same(entity.copy(name = "DifferentName"))
        assertFalse(result)
    }

    @Test
    fun `same should return false for lol entities with different properties`() {
        val lolCharacterRequest = basicLolEntityEnrichedRequest
        val entity = lolCharacterRequest.toEntity(1L)
        val diffPuuid = lolCharacterRequest.same(entity.copy(puuid = "diff-puuid"))
        assertFalse(diffPuuid)
    }

    @Test
    fun `same should return true for wow entities with identical Blizzard IDs`() {
        val request = createWowEntityRequest(
            name = "Arthas",
            region = "Northrend",
            realm = "Icecrown",
            blizzardId = 12345L
        )
        val entity = request.toEntity(1L)
        val result = request.same(entity)
        assertTrue(result)
    }

    @Test
    fun `same should return false for wow entities with different Blizzard IDs`() {
        val request = createWowEntityRequest(
            name = "Thrall",
            region = "Azeroth",
            realm = "Orgrimmar",
            blizzardId = 67890L
        )
        val entity = request.toEntity(2L)
        val result = request.same(entity.copy(blizzardId = 99999L))
        assertFalse(result)
    }

    @Test
    fun `toCharacter should create a WowCharacter with the correct properties for a WowEntityRequest with a blizzardId`() {
        val entityName = "Sylvanas"
        val entityRegion = "Eastern Kingdoms"
        val entityRealm = "Silvermoon"

        val request = createWowEntityRequest(
            name = "Sylvanas",
            region = "Eastern Kingdoms",
            realm = "Silvermoon",
            blizzardId = 54321L
        )
        val entity = request.toEntity(1L)

        assertEquals(1L, entity.id)
        assertEquals(entityName, entity.name)
        assertEquals(entityRegion, entity.region)
        assertEquals(entityRealm, entity.realm)
        assertEquals(54321L, entity.blizzardId)
    }

    private fun createWowEntityRequest(
        name: String = "DefaultName",
        region: String = "DefaultRegion",
        realm: String = "DefaultRealm",
        blizzardId: Long? = 12345L
    ) = WowEntityRequest(name, region, realm, blizzardId)
}