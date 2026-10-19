package com.focusguard.app.data

import com.focusguard.app.domain.AppUsageState
import com.focusguard.app.domain.FocusConfig
import com.focusguard.app.domain.FocusPolicyStore
import com.focusguard.app.domain.FocusRewardState

/**
 * Implementação ultraleve de [FocusPolicyStore] com cache em memória e Lazy Daily Reset.
 *
 * Otimizações para dispositivos de 4 GB de RAM:
 * 1. Mantém [cachedConfig] em memória para evitar alocações durante os eventos de janela e ticks de 1 Hz.
 * 2. Realiza o reset diário de forma preguiçosa (*lazy*) ao detectar mudança no `epochDay`,
 *    eliminando a necessidade de `AlarmManager` ou serviços agendados à meia-noite.
 * 3. Preserva o `cooldownUntilRealtimeMs` mesmo quando o dia vira às 00:00, impedindo burla na meia-noite.
 * 4. Faz rollover da máscara de bits de consistência (`consistencyBitmask`) em 8 bytes (`Long`).
 */
class FocusPreferencesStore(
    private val backend: KeyValueBackend
) : FocusPolicyStore {

    private var cachedConfig: FocusConfig? = null
    private val cachedUsageByApp = HashMap<String, AppUsageState>(8)
    private var cachedRewardState: FocusRewardState? = null

    override fun getConfig(): FocusConfig {
        cachedConfig?.let { return it }

        val enabledIds = backend.getStringSet(KEY_ENABLED_APP_IDS, DEFAULT_ENABLED_APPS)
        val pauseSec = backend.getInt(KEY_MINDFUL_PAUSE_SEC, DEFAULT_PAUSE_SEC).coerceIn(3, 120)
        val sessionMin = backend.getInt(KEY_SESSION_DURATION_MIN, DEFAULT_SESSION_MIN).coerceIn(1, 60)
        val cooldownMin = backend.getInt(KEY_COOLDOWN_MIN, DEFAULT_COOLDOWN_MIN).coerceIn(1, 180)
        val dailyQuotaMin = backend.getInt(KEY_DAILY_QUOTA_MIN, DEFAULT_DAILY_QUOTA_MIN).coerceIn(5, 240)
        val progressivePause = backend.getBoolean(KEY_PROGRESSIVE_PAUSE_ENABLED, true)
        val anchorHabit = backend.getString(
            KEY_SELECTED_ANCHOR_HABIT,
            FocusConfig.DEFAULT_ANCHOR_HABIT
        ).ifBlank { FocusConfig.DEFAULT_ANCHOR_HABIT }

        val loaded = FocusConfig(
            enabledAppIds = enabledIds,
            mindfulPauseSeconds = pauseSec,
            sessionDurationMinutes = sessionMin,
            cooldownMinutes = cooldownMin,
            dailyQuotaMinutes = dailyQuotaMin,
            progressivePauseEnabled = progressivePause,
            selectedAnchorHabit = anchorHabit
        )
        cachedConfig = loaded
        return loaded
    }

    override fun saveConfig(config: FocusConfig) {
        cachedConfig = config
        backend.editBatch {
            putStringSet(KEY_ENABLED_APP_IDS, config.enabledAppIds)
            putInt(KEY_MINDFUL_PAUSE_SEC, config.mindfulPauseSeconds)
            putInt(KEY_SESSION_DURATION_MIN, config.sessionDurationMinutes)
            putInt(KEY_COOLDOWN_MIN, config.cooldownMinutes)
            putInt(KEY_DAILY_QUOTA_MIN, config.dailyQuotaMinutes)
            putBoolean(KEY_PROGRESSIVE_PAUSE_ENABLED, config.progressivePauseEnabled)
            putString(KEY_SELECTED_ANCHOR_HABIT, config.selectedAnchorHabit)
        }
    }

    /**
     * Força a recarga da configuração a partir do [backend] caso tenha sido alterada externamente.
     */
    fun invalidateConfigCache() {
        cachedConfig = null
    }

    override fun getUsageState(appId: String, currentEpochDay: Long): AppUsageState {
        val inMemory = cachedUsageByApp[appId]
        if (inMemory != null && inMemory.epochDay == currentEpochDay) {
            return inMemory
        }

        val storedEpochDay = inMemory?.epochDay ?: backend.getLong(keyEpochDay(appId), -1L)
        val storedCooldownMs = (inMemory?.cooldownUntilRealtimeMs ?: backend.getLong(keyCooldownUntil(appId), 0L))
            .coerceAtLeast(0L)

        // Lazy Daily Reset: se o dia virou (ou nunca foi gravado), zera os segundos consumidos e sessões no dia,
        // mas preserva o cooldown ativo para evitar burla às 23:59 -> 00:00.
        if (storedEpochDay != currentEpochDay) {
            val resetState = AppUsageState(
                appId = appId,
                epochDay = currentEpochDay,
                usedDailySeconds = 0L,
                cooldownUntilRealtimeMs = storedCooldownMs,
                sessionsStartedToday = 0
            )
            saveUsageState(resetState)
            return resetState
        }

        val usedSeconds = backend.getLong(keyUsedSeconds(appId), 0L).coerceAtLeast(0L)
        val sessionsToday = backend.getInt(keySessionsToday(appId), 0).coerceAtLeast(0)
        val loaded = AppUsageState(
            appId = appId,
            epochDay = storedEpochDay,
            usedDailySeconds = usedSeconds,
            cooldownUntilRealtimeMs = storedCooldownMs,
            sessionsStartedToday = sessionsToday
        )
        cachedUsageByApp[appId] = loaded
        return loaded
    }

    override fun saveUsageState(state: AppUsageState) {
        val sanitized = state.copy(
            usedDailySeconds = state.usedDailySeconds.coerceAtLeast(0L),
            cooldownUntilRealtimeMs = state.cooldownUntilRealtimeMs.coerceAtLeast(0L),
            sessionsStartedToday = state.sessionsStartedToday.coerceAtLeast(0)
        )
        cachedUsageByApp[state.appId] = sanitized
        backend.editBatch {
            putLong(keyEpochDay(sanitized.appId), sanitized.epochDay)
            putLong(keyUsedSeconds(sanitized.appId), sanitized.usedDailySeconds)
            putLong(keyCooldownUntil(sanitized.appId), sanitized.cooldownUntilRealtimeMs)
            putInt(keySessionsToday(sanitized.appId), sanitized.sessionsStartedToday)
        }
    }

    override fun getRewardState(currentEpochDay: Long): FocusRewardState {
        val inMemory = cachedRewardState
        if (inMemory != null && inMemory.epochDay == currentEpochDay) {
            return inMemory
        }

        val storedEpochDay = inMemory?.epochDay ?: backend.getLong(KEY_REWARD_EPOCH_DAY, -1L)
        val totalReclaimed = (inMemory?.totalReclaimedSeconds ?: backend.getLong(KEY_REWARD_TOTAL_RECLAIMED_SEC, 0L))
            .coerceAtLeast(0L)
        val totalBeaten = (inMemory?.totalImpulsesBeaten ?: backend.getInt(KEY_REWARD_TOTAL_IMPULSES_BEATEN, 0))
            .coerceAtLeast(0)
        val storedMask = inMemory?.consistencyBitmask ?: backend.getLong(KEY_REWARD_CONSISTENCY_MASK, 1L)
        val storedTrackedDays = (inMemory?.trackedDaysCount ?: backend.getInt(KEY_REWARD_TRACKED_DAYS, 1))
            .coerceIn(1, 62)

        if (storedEpochDay == -1L) {
            val initial = FocusRewardState(
                epochDay = currentEpochDay,
                totalReclaimedSeconds = totalReclaimed,
                totalImpulsesBeaten = totalBeaten,
                consistencyBitmask = 1L,
                trackedDaysCount = 1
            )
            saveRewardState(initial)
            return initial
        }

        if (storedEpochDay != currentEpochDay) {
            val daysDelta = (currentEpochDay - storedEpochDay).coerceIn(1L, 62L).toInt()
            val shiftedOld = storedMask shl daysDelta
            val gapOnes = (1L shl daysDelta) - 1L
            val newMask = shiftedOld or gapOnes
            val newTrackedDays = (storedTrackedDays + daysDelta).coerceIn(1, 62)

            val rolledOver = FocusRewardState(
                epochDay = currentEpochDay,
                attemptsToday = 0,
                abortedToday = 0,
                voluntaryExitsToday = 0,
                reclaimedSecondsToday = 0L,
                totalReclaimedSeconds = totalReclaimed,
                totalImpulsesBeaten = totalBeaten,
                consistencyBitmask = newMask,
                trackedDaysCount = newTrackedDays
            )
            saveRewardState(rolledOver)
            return rolledOver
        }

        val loaded = FocusRewardState(
            epochDay = storedEpochDay,
            attemptsToday = backend.getInt(KEY_REWARD_ATTEMPTS_TODAY, 0).coerceAtLeast(0),
            abortedToday = backend.getInt(KEY_REWARD_ABORTED_TODAY, 0).coerceAtLeast(0),
            voluntaryExitsToday = backend.getInt(KEY_REWARD_VOLUNTARY_EXITS_TODAY, 0).coerceAtLeast(0),
            reclaimedSecondsToday = backend.getLong(KEY_REWARD_RECLAIMED_TODAY_SEC, 0L).coerceAtLeast(0L),
            totalReclaimedSeconds = totalReclaimed,
            totalImpulsesBeaten = totalBeaten,
            consistencyBitmask = storedMask,
            trackedDaysCount = storedTrackedDays
        )
        cachedRewardState = loaded
        return loaded
    }

    override fun saveRewardState(state: FocusRewardState) {
        val sanitized = state.copy(
            attemptsToday = state.attemptsToday.coerceAtLeast(0),
            abortedToday = state.abortedToday.coerceAtLeast(0),
            voluntaryExitsToday = state.voluntaryExitsToday.coerceAtLeast(0),
            reclaimedSecondsToday = state.reclaimedSecondsToday.coerceAtLeast(0L),
            totalReclaimedSeconds = state.totalReclaimedSeconds.coerceAtLeast(0L),
            totalImpulsesBeaten = state.totalImpulsesBeaten.coerceAtLeast(0),
            trackedDaysCount = state.trackedDaysCount.coerceIn(1, 62)
        )
        cachedRewardState = sanitized
        backend.editBatch {
            putLong(KEY_REWARD_EPOCH_DAY, sanitized.epochDay)
            putInt(KEY_REWARD_ATTEMPTS_TODAY, sanitized.attemptsToday)
            putInt(KEY_REWARD_ABORTED_TODAY, sanitized.abortedToday)
            putInt(KEY_REWARD_VOLUNTARY_EXITS_TODAY, sanitized.voluntaryExitsToday)
            putLong(KEY_REWARD_RECLAIMED_TODAY_SEC, sanitized.reclaimedSecondsToday)
            putLong(KEY_REWARD_TOTAL_RECLAIMED_SEC, sanitized.totalReclaimedSeconds)
            putInt(KEY_REWARD_TOTAL_IMPULSES_BEATEN, sanitized.totalImpulsesBeaten)
            putLong(KEY_REWARD_CONSISTENCY_MASK, sanitized.consistencyBitmask)
            putInt(KEY_REWARD_TRACKED_DAYS, sanitized.trackedDaysCount)
        }
    }

    private fun keyEpochDay(appId: String): String = "usage_${appId}_epoch_day"
    private fun keyUsedSeconds(appId: String): String = "usage_${appId}_used_sec"
    private fun keyCooldownUntil(appId: String): String = "usage_${appId}_cooldown_ms"
    private fun keySessionsToday(appId: String): String = "usage_${appId}_sessions_today"

    companion object {
        private const val KEY_ENABLED_APP_IDS = "cfg_enabled_app_ids"
        private const val KEY_MINDFUL_PAUSE_SEC = "cfg_mindful_pause_sec"
        private const val KEY_SESSION_DURATION_MIN = "cfg_session_duration_min"
        private const val KEY_COOLDOWN_MIN = "cfg_cooldown_min"
        private const val KEY_DAILY_QUOTA_MIN = "cfg_daily_quota_min"
        private const val KEY_PROGRESSIVE_PAUSE_ENABLED = "cfg_progressive_pause"
        private const val KEY_SELECTED_ANCHOR_HABIT = "cfg_selected_anchor_habit"

        private const val KEY_REWARD_EPOCH_DAY = "reward_epoch_day"
        private const val KEY_REWARD_ATTEMPTS_TODAY = "reward_attempts_today"
        private const val KEY_REWARD_ABORTED_TODAY = "reward_aborted_today"
        private const val KEY_REWARD_VOLUNTARY_EXITS_TODAY = "reward_voluntary_exits_today"
        private const val KEY_REWARD_RECLAIMED_TODAY_SEC = "reward_reclaimed_today_sec"
        private const val KEY_REWARD_TOTAL_RECLAIMED_SEC = "reward_total_reclaimed_sec"
        private const val KEY_REWARD_TOTAL_IMPULSES_BEATEN = "reward_total_impulses_beaten"
        private const val KEY_REWARD_CONSISTENCY_MASK = "reward_consistency_mask"
        private const val KEY_REWARD_TRACKED_DAYS = "reward_tracked_days"

        private val DEFAULT_ENABLED_APPS = setOf("instagram", "tiktok")
        private const val DEFAULT_PAUSE_SEC = 10
        private const val DEFAULT_SESSION_MIN = 5
        private const val DEFAULT_COOLDOWN_MIN = 10
        private const val DEFAULT_DAILY_QUOTA_MIN = 30
    }
}
