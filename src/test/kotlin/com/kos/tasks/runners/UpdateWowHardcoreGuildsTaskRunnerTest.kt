package com.kos.tasks.runners

import arrow.core.Either
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.domain.*
import com.kos.datacache.BlizzardMockHelper.getWowCharacterResponse
import com.kos.entities.EntitiesService
import com.kos.entities.EntityResolverProvider
import com.kos.entities.domain.GuildPayload
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.wowguilds.WowGuildsInMemoryRepository
import com.kos.entities.repository.wowguilds.WowGuildsState
import com.kos.sources.wowhc.WowHardcoreEntityResolver
import com.kos.sources.wowhc.WowHardcoreGuildUpdater
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksInMemoryRepository
import com.kos.views.Game
import com.kos.views.repository.ViewsInMemoryRepository
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateWowHardcoreGuildsTaskRunnerTest {

    private val blizzardClient = mock(BlizzardClient::class.java)
    private val entitiesRepository = EntitiesInMemoryRepository()
    private val wowGuildsRepository = WowGuildsInMemoryRepository()
    private val viewsRepository = ViewsInMemoryRepository()
    private val wowHardcoreResolver = WowHardcoreEntityResolver(entitiesRepository, blizzardClient)
    private val entitiesService = EntitiesService(
        entitiesRepository,
        wowGuildsRepository,
        EntityResolverProvider(wowResolver = mock(), wowHardcoreResolver = wowHardcoreResolver, lolResolver = mock()),
        mock(),
        WowHardcoreGuildUpdater(wowHardcoreResolver, entitiesRepository, viewsRepository),
        mock()
    )
    private val tasksRepo = TasksInMemoryRepository()
    private val runner = UpdateWowHardcoreGuildsTaskRunner(tasksRepo, entitiesService)

    @Test
    fun `wow hardcore guilds are updated and task is recorded as successful`() = runBlocking {
        val realm = "Soulseeker"
        val guild = "Balast"
        val region = "Nolase"
        val blizzardId: Long = 1
        val character = "Surmana"

        wowGuildsRepository.withState(
            WowGuildsState(listOf(Triple(GuildPayload(guild, realm, region, blizzardId), "1", Game.WOW_HC)))
        )
        `when`(blizzardClient.getHardcoreGuildRoster(region, realm, guild))
            .thenReturn(
                Either.Right(
                    GetWowRosterResponse(
                        listOf(WowMemberResponse(WowCharacterResponse(character, 60))),
                        WowGuildResponse(blizzardId)
                    )
                )
            )
        `when`(blizzardClient.getClassicProfile(region, realm, character.toLowerCasePreservingASCIIRules()))
            .thenReturn(Either.Right(getWowCharacterResponse.copy(name = character, guild = guild)))
        `when`(blizzardClient.getRealm(region, 5220))
            .thenReturn(Either.Right(GetWowRealmResponse("Hardcore")))

        val id = UUID.randomUUID().toString()
        tasksRepo.insertTask(Task(id, runner.type, TaskStatus(Status.PENDING, null), OffsetDateTime.now()))

        runner.run(id, null)

        val task = tasksRepo.state().first()
        assertEquals(id, task.id)
        assertEquals(TaskType.UPDATE_WOW_HARDCORE_GUILDS, task.type)
        assertEquals(Status.SUCCESSFUL, task.taskStatus.status)
    }
}
