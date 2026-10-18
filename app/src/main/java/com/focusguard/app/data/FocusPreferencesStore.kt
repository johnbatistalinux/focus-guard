package com.focusguard.app.data

import com.focusguard.app.domain.AppUsageState
import com.focusguard.app.domain.FocusConfig
import com.focusguard.app.domain.FocusPolicyStore

/**
 * Implementação ultraleve de [FocusPolicyStore] com cache em memória e Lazy Daily Reset.
 *
 * Otimizações para dispositivos de 4 GB de RAM:
 * 1. Mantém [cachedConfig] em memória para evitar alocações durante os eventos de janela e ticks de 1 Hz.
 * 2. Realiza o reset diário de forma preguiçosa (*lazy*) ao detectar mudança no `epochDay`,
 *    eliminando a necessidade de `AlarmManager` ou serviços agendados à meia-noite.
 * 3. Preserva o `cooldownUntilRealtimeMs` mesmo quando o dia vira às 00:00, impedindo burla na meia-noite.
 */
class FocusPreferencesStore(
    private val backend: KeyValueBackend
) : FocusPolicyStore {

    private var cachedConfig: FocusConfig? = null

    override fun getConfig(): FocusConfig {
        cachedConfig?.let { return it }

        val enabledIds = backend.getStringSet(KEY_ENABLED_APP_IDS, DEFAULT_ENABLED_APPS)
        val pauseSec = backend.getInt(KEY_MINDFUL_PAUSE_SEC, DEFAULT_PAUSE_SEC).coerceIn(3, 120)
        val sessionMin = backend.getInt(KEY_SESSION_DURATION_MIN, DEFAULT_SESSION_MIN).coerceIn(1, 60)
        val cooldownMin = backend.getInt(KEY_COOLDOWN_MIN, DEFAULT_COOLDOWN_MIN).coerceIn(1, 180)
        val dailyQuotaMin = backend.getInt(KEY_DAILY_QUOTA_MIN, DEFAULT_DAILY_QUOTA_MIN).coerceIn(5, 240)

        val loaded = FocusConfig(
            enabledAppIds = enabledIds,
            mindfulPauseSeconds = pauseSec,
            sessionDurationMinutes = sessionMin,
            cooldownMinutes = cooldownMin,
            dailyQuotaMinutes = dailyQuotaMin
        )
        cachedConfig = loaded
        return loaded
    }

    override fun saveConfig(config: FocusConfig) {
        cachedConfig = config
        backend.putStringSet(KEY_ENABLED_APP_IDS, config.enabledAppIds)
        backend.putInt(KEY_MINDFUL_PAUSE_SEC, config.mindfulPauseSeconds)
        backend.putInt(KEY_SESSION_DURATION_MIN, config.sessionDurationMinutes)
        backend.putInt(KEY_COOLDOWN_MIN, config.cooldownMinutes)
        backend.putInt(KEY_DAILY_QUOTA_MIN, config.dailyQuotaMinutes)
    }

    /**
     * Força a recarga da configuração a partir do [backend] caso tenha sido alterada pela Activity.
     */
    fun invalidateConfigCache() {
        cachedConfig = null
    }

    override fun getUsageState(appId: String, currentEpochDay: Long): AppUsageState {
        val storedEpochDay = backend.getLong(keyEpochDay(appId), -1L)
        val storedCooldownMs = backend.getLong(keyCooldownUntil(appId), 0L).coerceAtLeast(0L)

        // Lazy Daily Reset: se o dia virou (ou nunca foi gravado), zera os segundos consumidos no dia,
        // mas preserva o cooldown ativo para evitar burla às 23:59 -> 00:00.
        if (storedEpochDay != currentEpochDay) {
            val resetState = AppUsageState(
                appId = appId,
                epochDay = currentEpochDay,
                usedDailySeconds = 0L,
                cooldownUntilRealtimeMs = storedCooldownMs
            )
            saveUsageState(resetState)
            return resetState
        }

        val usedSeconds = backend.getLong(keyUsedSeconds(appId), 0L).coerceAtLeast(0L)
        return AppUsageState(
            appId = appId,
            epochDay = storedEpochDay,
            usedDailySeconds = usedSeconds,
            cooldownUntilRealtimeMs = storedCooldownMs
        )
    }

    override fun saveUsageState(state: AppUsageState) {
        backend.putLong(keyEpochDay(state.appId), state.epochDay)
        backend.putLong(keyUsedSeconds(state.appId), state.usedDailySeconds.coerceAtLeast(0L))
        backend.putLong(keyCooldownUntil(state.appId), state.cooldownUntilRealtimeMs.coerceAtLeast(0L))
    }

    private fun keyEpochDay(appId: String): String = "usage_${appId}_epoch_day"
    private fun keyUsedSeconds(appId: String): String = "usage_${appId}_used_sec"
    private fun keyCooldownUntil(appId: String): String = "usage_${appId}_cooldown_ms"

    companion object {
        private const val KEY_ENABLED_APP_IDS = "cfg_enabled_app_ids"
        private const val KEY_MINDFUL_PAUSE_SEC = "cfg_mindful_pause_sec"
        private const val KEY_SESSION_DURATION_MIN = "cfg_session_duration_min"
        private const val KEY_COOLDOWN_MIN = "cfg_cooldown_min"
        private const val KEY_DAILY_QUOTA_MIN = "cfg_daily_quota_min"

        private val DEFAULT_ENABLED_APPS = setOf("instagram", "tiktok")
        private const val DEFAULT_PAUSE_SEC = 10
        private const val DEFAULT_SESSION_MIN = 5
        private const val DEFAULT_COOLDOWN_MIN = 10
        private const val DEFAULT_DAILY_QUOTA_MIN = 30
    }
}
