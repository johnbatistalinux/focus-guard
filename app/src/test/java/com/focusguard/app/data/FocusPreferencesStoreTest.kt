package com.focusguard.app.data

import com.focusguard.app.domain.AppUsageState
import com.focusguard.app.domain.FocusConfig
import org.junit.jupiter.api.Assertions.assertEquals
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
    }

    @Test
    fun `persiste e recupera FocusConfig corretamente`() {
        val custom = FocusConfig(
            enabledAppIds = setOf("instagram", "youtube", "reddit"),
            mindfulPauseSeconds = 15,
            sessionDurationMinutes = 10,
            cooldownMinutes = 20,
            dailyQuotaMinutes = 45
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
    fun `lazy daily reset zera uso diario na virada do epochDay mas preserva cooldown ativo`() {
        val day1 = 20_000L
        val day2 = 20_001L

        // No dia 1, o usuário consumiu 1200s (20 min) e entrou em cooldown até 500_000ms
        store.saveUsageState(
            AppUsageState(
                appId = "instagram",
                epochDay = day1,
                usedDailySeconds = 1200L,
                cooldownUntilRealtimeMs = 500_000L
            )
        )

        val sameDayState = store.getUsageState("instagram", day1)
        assertEquals(1200L, sameDayState.usedDailySeconds)
        assertEquals(500_000L, sameDayState.cooldownUntilRealtimeMs)

        // Virou meia-noite (day2): o tempo usado hoje zera, mas o cooldown das 23:58 continua valendo
        val nextDayState = store.getUsageState("instagram", day2)
        assertEquals(day2, nextDayState.epochDay)
        assertEquals(0L, nextDayState.usedDailySeconds)
        assertEquals(500_000L, nextDayState.cooldownUntilRealtimeMs)
    }

    private class FakeKeyValueBackend : KeyValueBackend {
        private val stringSets = mutableMapOf<String, Set<String>>()
        private val ints = mutableMapOf<String, Int>()
        private val longs = mutableMapOf<String, Long>()

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
    }
}
