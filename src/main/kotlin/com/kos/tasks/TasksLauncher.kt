package com.kos.tasks

import com.kos.auth.AuthService
import com.kos.common.WithLogger
import com.kos.datacache.DataCacheService
import com.kos.tasks.repository.TasksRepository
import com.kos.tasks.runnables.CacheGameDataRunnable
import com.kos.tasks.runnables.ScheduledTaskRunnable
import com.kos.views.Game
import kotlinx.coroutines.CoroutineScope
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

data class TasksLauncher(
    private val tasksService: TasksService,
    private val tasksRepository: TasksRepository,
    private val executorService: ScheduledExecutorService,
    private val authService: AuthService,
    private val dataCacheService: DataCacheService,
    private val coroutineScope: CoroutineScope
) : WithLogger("tasksLauncher") {
    suspend fun launchTasks() {
        val now = OffsetDateTime.now()
        val thirtyMinutesDelay = 30
        val fifteenMinutesDelay = 15
        val oneWeekDelay = 10080
        val oneDayDelay = 1440
        val threeDaysDelay = oneDayDelay * 3


        suspend fun getTaskInitialDelay(now: OffsetDateTime, taskType: TaskType, timeDelay: Int): Long =
            tasksRepository.getLastExecution(taskType)?.inserted?.let {
                val differenceBetweenNowAndLastExecutionTime = Duration.between(it, now).toMinutes()
                return if (differenceBetweenNowAndLastExecutionTime >= timeDelay) 0
                else timeDelay - differenceBetweenNowAndLastExecutionTime
            } ?: 0

        val cacheWowDataTaskInitDelay: Long = getTaskInitialDelay(now, TaskType.CACHE_WOW_DATA_TASK, thirtyMinutesDelay)
        val cacheWowHcDataTaskInitDelay: Long =
            getTaskInitialDelay(now, TaskType.CACHE_WOW_HC_DATA_TASK, thirtyMinutesDelay)
        val cacheLolDataTaskInitDelay: Long = getTaskInitialDelay(now, TaskType.CACHE_LOL_DATA_TASK, thirtyMinutesDelay)
        val tokenCleanupInitDelay: Long = getTaskInitialDelay(now, TaskType.TOKEN_CLEANUP_TASK, fifteenMinutesDelay)
        val tasksCleanupInitDelay: Long = getTaskInitialDelay(now, TaskType.TASK_CLEANUP_TASK, oneWeekDelay)
        val updateLolEntitiesInitDelay: Long = getTaskInitialDelay(now, TaskType.UPDATE_LOL_ENTITIES_TASK, oneWeekDelay)
        val updateWowGuildsInitDelay: Long = getTaskInitialDelay(now, TaskType.UPDATE_WOW_HARDCORE_GUILDS, oneDayDelay)
        val updateWowRetailGuildsInitDelay: Long = getTaskInitialDelay(now, TaskType.UPDATE_WOW_GUILDS, oneDayDelay)
        val eventCleanupInitDelay: Long = getTaskInitialDelay(now, TaskType.EVENT_CLEANUP_TASK, threeDaysDelay)

        logger.info("Setting $cacheWowDataTaskInitDelay minutes of delay before launching ${TaskType.CACHE_WOW_DATA_TASK}")
        logger.info("Setting $cacheLolDataTaskInitDelay minutes of delay before launching ${TaskType.CACHE_LOL_DATA_TASK}")
        logger.info("Setting $cacheWowHcDataTaskInitDelay minutes of delay before launching ${TaskType.CACHE_WOW_HC_DATA_TASK}")
        logger.info("Setting $tokenCleanupInitDelay minutes of delay before launching ${TaskType.TOKEN_CLEANUP_TASK}")
        logger.info("Setting $tasksCleanupInitDelay minutes of delay before launching ${TaskType.TASK_CLEANUP_TASK}")
        logger.info("Setting $updateLolEntitiesInitDelay minutes of delay before launching ${TaskType.UPDATE_LOL_ENTITIES_TASK}")
        logger.info("Setting $updateWowGuildsInitDelay minutes of delay before launching ${TaskType.UPDATE_WOW_HARDCORE_GUILDS}")
        logger.info("Setting $updateWowRetailGuildsInitDelay minutes of delay before launching ${TaskType.UPDATE_WOW_GUILDS}")
        logger.info("Setting $eventCleanupInitDelay minutes of delay before launching ${TaskType.EVENT_CLEANUP_TASK}")

        executorService.scheduleAtFixedRate(
            ScheduledTaskRunnable(tasksService, TaskType.TOKEN_CLEANUP_TASK, coroutineScope),
            tokenCleanupInitDelay, fifteenMinutesDelay.toLong(), TimeUnit.MINUTES
        )

        executorService.scheduleAtFixedRate(
            CacheGameDataRunnable(
                tasksService,
                dataCacheService,
                coroutineScope,
                Game.LOL,
                TaskType.CACHE_LOL_DATA_TASK
            ),
            cacheLolDataTaskInitDelay, thirtyMinutesDelay.toLong(), TimeUnit.MINUTES
        )

        executorService.scheduleAtFixedRate(
            CacheGameDataRunnable(
                tasksService,
                dataCacheService,
                coroutineScope,
                Game.WOW,
                TaskType.CACHE_WOW_DATA_TASK
            ),
            cacheWowDataTaskInitDelay, thirtyMinutesDelay.toLong(), TimeUnit.MINUTES
        )

        executorService.scheduleAtFixedRate(
            CacheGameDataRunnable(
                tasksService,
                dataCacheService,
                coroutineScope,
                Game.WOW_HC,
                TaskType.CACHE_WOW_HC_DATA_TASK
            ),
            cacheWowHcDataTaskInitDelay, thirtyMinutesDelay.toLong(), TimeUnit.MINUTES
        )

        executorService.scheduleAtFixedRate(
            ScheduledTaskRunnable(tasksService, TaskType.TASK_CLEANUP_TASK, coroutineScope),
            tasksCleanupInitDelay, oneWeekDelay.toLong(), TimeUnit.MINUTES
        )

        executorService.scheduleAtFixedRate(
            ScheduledTaskRunnable(tasksService, TaskType.UPDATE_LOL_ENTITIES_TASK, coroutineScope),
            updateLolEntitiesInitDelay, oneDayDelay.toLong(), TimeUnit.MINUTES
        )

        executorService.scheduleAtFixedRate(
            ScheduledTaskRunnable(tasksService, TaskType.UPDATE_WOW_HARDCORE_GUILDS, coroutineScope),
            updateWowGuildsInitDelay, oneDayDelay.toLong(), TimeUnit.MINUTES
        )

        executorService.scheduleAtFixedRate(
            ScheduledTaskRunnable(tasksService, TaskType.UPDATE_WOW_GUILDS, coroutineScope),
            updateWowRetailGuildsInitDelay, oneDayDelay.toLong(), TimeUnit.MINUTES
        )

        executorService.scheduleAtFixedRate(
            ScheduledTaskRunnable(tasksService, TaskType.EVENT_CLEANUP_TASK, coroutineScope),
            eventCleanupInitDelay, threeDaysDelay.toLong(), TimeUnit.MINUTES
        )

        Runtime.getRuntime().addShutdownHook(Thread {
            executorService.shutdown()
        })
    }
}