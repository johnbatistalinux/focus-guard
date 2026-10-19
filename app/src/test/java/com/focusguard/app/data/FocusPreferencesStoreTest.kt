package com.focusguard.app.data

import com.focusguard.app.domain.AppUsageState
import com.focusguard.app.domain.FocusConfig
import com.focusguard.app.domain.FocusRewardState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FocusPreferencesStoreTest {

    private lateinit var backend: FakeKeyValueBackend
    private lateinit var store: FocusPreferencesStore

    @BeforeEach
    fun setUp() {
        backend = FakeKeyValueBackend()
        store = FocusPreferencesStore(backend)
    }

    @Test
    fun `carrega configuracao padrao quando o armazenamento esta vazio`() {
        val config = store.getConfig()
        assertEquals(setOf("instagram", "tiktok"), config.enabledAppIds)
        assertEquals(10, config.mindfulPauseSeconds)
        assertEquals(5, config.sessionDurationMinutes)
        assertEquals(10, config.cooldownMinutes)
        assertEquals(30, config.dailyQuotaMinutes)
        assertTrue(config.progressivePauseEnabled)
        assertEquals(FocusConfig.DEFAULT_ANCHOR_HABIT, config.selectedAnchorHabit)
    }

    @Test
    fun `persiste e recupera FocusConfig corretamente`() {
        val custom = FocusConfig(
            enabledAppIds = setOf("instagram", "youtube", "reddit"),
            mindfulPauseSeconds = 15,
            sessionDurationMinutes = 10,
            cooldownMinutes = 20,
            dailyQuotaMinutes = 45,
            progressivePauseEnabled = false,
            selectedAnchorHabit = "Beber um copo d'água 💧"
        )
        store.saveConfig(custom)
        store.invalidateConfigCache()

        val reloaded = store.getConfig()
        assertEquals(custom, reloaded)
    }

    @Test
    fun `aplica clamp seguro caso valores fora dos limites estejam gravados no disco`() {
        backend.putInt("cfg_mindful_pause_sec", 999)
        backend.putInt("cfg_session_duration_min", 0)
        backend.putInt("cfg_cooldown_min", -5)
        backend.putInt("cfg_daily_quota_min", 9999)

        val config = store.getConfig()
        assertEquals(120, config.mindfulPauseSeconds)
        assertEquals(1, config.sessionDurationMinutes)
        assertEquals(1, config.cooldownMinutes)
        assertEquals(240, config.dailyQuotaMinutes)
    }

    @Test
    fun `lazy daily reset zera uso diario e sessoes na virada do epochDay mas preserva cooldown ativo`() {
        val day1 = 20_000L
        val day2 = 20_001L

        // No dia 1, o usuário consumiu 1200s (20 min), iniciou 3 sessões e entrou em cooldown até 500_000ms
        store.saveUsageState(
            AppUsageState(
                appId = "instagram",
                epochDay = day1,
                usedDailySeconds = 1200L,
                cooldownUntilRealtimeMs = 500_000L,
                sessionsStartedToday = 3
            )
        )

        val sameDayState = store.getUsageState("instagram", day1)
        assertEquals(1200L, sameDayState.usedDailySeconds)
        assertEquals(500_000L, sameDayState.cooldownUntilRealtimeMs)
        assertEquals(3, sameDayState.sessionsStartedToday)

        // Virou meia-noite (day2): o tempo usado hoje e as sessões zeram, mas o cooldown das 23:58 continua valendo
        val nextDayState = store.getUsageState("instagram", day2)
        assertEquals(day2, nextDayState.epochDay)
        assertEquals(0L, nextDayState.usedDailySeconds)
        assertEquals(0, nextDayState.sessionsStartedToday)
        assertEquals(500_000L, nextDayState.cooldownUntilRealtimeMs)
    }

    @Test
    fun `rollover do FocusRewardState desloca mascara de consistencia e preserva acumulados vitalicios`() {
        val day1 = 20_000L
        val day2 = 20_001L

        store.saveRewardState(
            FocusRewardState(
                epochDay = day1,
                attemptsToday = 5,
                abortedToday = 4,
                voluntaryExitsToday = 1,
                reclaimedSecondsToday = 1200L,
                totalReclaimedSeconds = 3600L,
                totalImpulsesBeaten = 12,
                consistencyBitmask = 1L,
                trackedDaysCount = 1
            )
        )

        val nextDayReward = store.getRewardState(day2)
        assertEquals(day2, nextDayReward.epochDay)
        assertEquals(0, nextDayReward.attemptsToday)
        assertEquals(0, nextDayReward.abortedToday)
        assertEquals(0, nextDayReward.voluntaryExitsToday)
        assertEquals(0L, nextDayReward.reclaimedSecondsToday)
        assertEquals(3600L, nextDayReward.totalReclaimedSeconds)
        assertEquals(12, nextDayReward.totalImpulsesBeaten)
        assertEquals(0b11L, nextDayReward.consistencyBitmask)
        assertEquals(2, nextDayReward.trackedDaysCount)
    }

    @Test
    fun `grava estado em transacao unica editBatch e serve leituras subsequentes do cache em memoria`() {
        val batchesBefore = backend.batchTransactionCount
        store.saveUsageState(
            AppUsageState(
                appId = "instagram",
                epochDay = 20_000L,
                usedDailySeconds = 45L,
                cooldownUntilRealtimeMs = 0L,
                sessionsStartedToday = 1
            )
        )
        assertEquals(batchesBefore + 1, backend.batchTransactionCount)

        // Altera o backend por fora sem invalidar o cache: getUsageState deve retornar a referência em memória
        backend.putLong("usage_instagram_used_sec", 999L)
        val cached = store.getUsageState("instagram", 20_000L)
        assertEquals(45L, cached.usedDailySeconds)
    }

    @Test
    fun `retrocesso de relogio ou fuso horario nao desloca consistencyBitmask indevidamente`() {
        val day10 = 20_010L
        val day9 = 20_009L

        store.saveRewardState(
            FocusRewardState(
                epochDay = day10,
                attemptsToday = 3,
                abortedToday = 2,
                voluntaryExitsToday = 1,
                reclaimedSecondsToday = 600L,
                totalReclaimedSeconds = 1800L,
                totalImpulsesBeaten = 5,
                consistencyBitmask = 0b1011L,
                trackedDaysCount = 4
            )
        )

        val rolledBack = store.getRewardState(day9)
        assertEquals(day9, rolledBack.epochDay)
        assertEquals(0b1011L, rolledBack.consistencyBitmask)
        assertEquals(4, rolledBack.trackedDaysCount)
        assertEquals(1800L, rolledBack.totalReclaimedSeconds)
        assertEquals(5, rolledBack.totalImpulsesBeaten)
    }

    private class FakeKeyValueBackend : KeyValueBackend {
        private val stringSets = mutableMapOf<String, Set<String>>()
        private val ints = mutableMapOf<String, Int>()
        private val longs = mutableMapOf<String, Long>()
        private val booleans = mutableMapOf<String, Boolean>()
        private val strings = mutableMapOf<String, String>()
        var batchTransactionCount: Int = 0
            private set

        override fun getStringSet(key: String, defaultValues: Set<String>): Set<String> =
            stringSets[key]?.toSet() ?: defaultValues

        override fun putStringSet(key: String, values: Set<String>) {
            stringSets[key] = values.toSet()
        }

        override fun getInt(key: String, defaultValue: Int): Int =
            ints[key] ?: defaultValue

        override fun putInt(key: String, value: Int) {
            ints[key] = value
        }

        override fun getLong(key: String, defaultValue: Long): Long =
            longs[key] ?: defaultValue

        override fun putLong(key: String, value: Long) {
            longs[key] = value
        }

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            booleans[key] ?: defaultValue

        override fun putBoolean(key: String, value: Boolean) {
            booleans[key] = value
        }

        override fun getString(key: String, defaultValue: String): String =
            strings[key] ?: defaultValue

        override fun putString(key: String, value: String) {
            strings[key] = value
        }

        override fun editBatch(block: BatchEditor.() -> Unit) {
            batchTransactionCount++
            super.editBatch(block)
        }
    }
}
