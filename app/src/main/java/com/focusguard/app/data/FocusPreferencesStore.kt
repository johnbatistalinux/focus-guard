package com.focusguard.app.data

import com.focusguard.app.domain.AppUsageState
import com.focusguard.app.domain.FocusConfig
import com.focusguard.app.domain.FocusGroup
import com.focusguard.app.domain.FocusLimits
import com.focusguard.app.domain.FocusPolicyStore
import com.focusguard.app.domain.FocusRewardState
import com.focusguard.app.domain.GroupIcon
import com.focusguard.app.domain.SessionGuard
import com.focusguard.app.domain.TargetApp
import java.util.concurrent.ConcurrentHashMap

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

    @Volatile
    private var cachedConfig: FocusConfig? = null
    private val cachedUsageByApp = ConcurrentHashMap<String, AppUsageState>(8)
    @Volatile
    private var cachedRewardState: FocusRewardState? = null

    override fun getConfig(): FocusConfig {
        cachedConfig?.let { return it }

        val enabledIds = backend.getStringSet(KEY_ENABLED_APP_IDS, DEFAULT_ENABLED_APPS)
        val pauseSec = backend.getInt(KEY_MINDFUL_PAUSE_SEC, DEFAULT_PAUSE_SEC).coerceIn(FocusLimits.PAUSE_SECONDS)
        val sessionMin = backend.getInt(KEY_SESSION_DURATION_MIN, DEFAULT_SESSION_MIN).coerceIn(FocusLimits.SESSION_MINUTES)
        val cooldownMin = backend.getInt(KEY_COOLDOWN_MIN, DEFAULT_COOLDOWN_MIN).coerceIn(FocusLimits.COOLDOWN_MINUTES)
        val dailyQuotaMin = backend.getInt(KEY_DAILY_QUOTA_MIN, DEFAULT_DAILY_QUOTA_MIN).coerceIn(FocusLimits.DAILY_QUOTA_MINUTES)
        val progressivePause = backend.getBoolean(KEY_PROGRESSIVE_PAUSE_ENABLED, true)
        val rawAnchorHabit = backend.getString(
            KEY_SELECTED_ANCHOR_HABIT,
            FocusConfig.DEFAULT_ANCHOR_HABIT
        )
        val anchorHabit = FocusLimits.sanitizeAnchorHabit(rawAnchorHabit)

        val sharedQuotaDefault = backend.getBoolean(KEY_SHARED_QUOTA_DEFAULT, true)
        val rawGroups = backend.getString(KEY_FOCUS_GROUPS_V1, "")
        val rawCustomApps = backend.getString(KEY_CUSTOM_APPS_V1, "")

        val deserializedGroups = if (rawGroups.isNotBlank()) deserializeGroups(rawGroups) else emptyList()
        val effectiveGroups = if (deserializedGroups.isNotEmpty()) {
            deserializedGroups
        } else {
            // Migração automática v1.3 -> v1.4 a partir das chaves legadas existentes
            listOf(
                FocusGroup(
                    id = FocusGroup.DEFAULT_GROUP_SOCIAL_ID,
                    name = "Redes Sociais",
                    iconKey = GroupIcon.SOCIAL.key,
                    enabled = true,
                    appIds = enabledIds,
                    mindfulPauseSeconds = pauseSec,
                    sessionDurationMinutes = sessionMin,
                    cooldownMinutes = cooldownMin,
                    dailyQuotaMinutes = dailyQuotaMin,
                    progressivePauseEnabled = progressivePause,
                    sharedQuota = sharedQuotaDefault
                ),
                FocusGroup(
                    id = FocusGroup.DEFAULT_GROUP_GAMES_ID,
                    name = "Jogos & Outros",
                    iconKey = GroupIcon.GAMES.key,
                    enabled = true,
                    appIds = emptySet(),
                    mindfulPauseSeconds = pauseSec,
                    sessionDurationMinutes = sessionMin,
                    cooldownMinutes = cooldownMin,
                    dailyQuotaMinutes = dailyQuotaMin,
                    progressivePauseEnabled = progressivePause,
                    sharedQuota = sharedQuotaDefault
                )
            )
        }

        val customApps = if (rawCustomApps.isNotBlank()) deserializeCustomApps(rawCustomApps) else emptyList()
        val activeGroupAppIds = effectiveGroups.filter { it.enabled }.flatMap { it.appIds }.toSet()
        val allEnabledAppIds = if (deserializedGroups.isNotEmpty()) activeGroupAppIds else enabledIds

        val loaded = FocusConfig(
            enabledAppIds = allEnabledAppIds,
            mindfulPauseSeconds = pauseSec,
            sessionDurationMinutes = sessionMin,
            cooldownMinutes = cooldownMin,
            dailyQuotaMinutes = dailyQuotaMin,
            progressivePauseEnabled = progressivePause,
            selectedAnchorHabit = anchorHabit,
            groups = effectiveGroups,
            customApps = customApps,
            sharedQuotaEnabled = sharedQuotaDefault
        )
        cachedConfig = loaded
        return loaded
    }

    override fun saveConfig(config: FocusConfig) {
        val effectiveGroups = config.getEffectiveGroups()
        val activeGroupAppIds = effectiveGroups.filter { it.enabled }.flatMap { it.appIds }.toSet()
        val primaryGroup = effectiveGroups.firstOrNull { it.id == FocusGroup.DEFAULT_GROUP_SOCIAL_ID }
            ?: effectiveGroups.firstOrNull()

        val pauseSec = primaryGroup?.mindfulPauseSeconds ?: config.mindfulPauseSeconds
        val sessionMin = primaryGroup?.sessionDurationMinutes ?: config.sessionDurationMinutes
        val cooldownMin = primaryGroup?.cooldownMinutes ?: config.cooldownMinutes
        val dailyQuotaMin = primaryGroup?.dailyQuotaMinutes ?: config.dailyQuotaMinutes
        val progPause = primaryGroup?.progressivePauseEnabled ?: config.progressivePauseEnabled

        val immutableConfig = config.copy(
            enabledAppIds = activeGroupAppIds,
            groups = effectiveGroups,
            customApps = config.customApps.toList()
        )
        cachedConfig = immutableConfig

        backend.editBatch {
            putStringSet(KEY_ENABLED_APP_IDS, immutableConfig.enabledAppIds)
            putInt(KEY_MINDFUL_PAUSE_SEC, pauseSec)
            putInt(KEY_SESSION_DURATION_MIN, sessionMin)
            putInt(KEY_COOLDOWN_MIN, cooldownMin)
            putInt(KEY_DAILY_QUOTA_MIN, dailyQuotaMin)
            putBoolean(KEY_PROGRESSIVE_PAUSE_ENABLED, progPause)
            putString(KEY_SELECTED_ANCHOR_HABIT, immutableConfig.selectedAnchorHabit)
            putString(KEY_FOCUS_GROUPS_V1, serializeGroups(effectiveGroups))
            putString(KEY_CUSTOM_APPS_V1, serializeCustomApps(immutableConfig.customApps))
            putBoolean(KEY_SHARED_QUOTA_DEFAULT, immutableConfig.sharedQuotaEnabled)
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
        if (inMemory != null && currentEpochDay <= inMemory.epochDay) {
            return inMemory
        }

        val storedEpochDay = inMemory?.epochDay ?: backend.getLong(keyEpochDay(appId), -1L)
        val storedCooldownMs = (inMemory?.cooldownUntilRealtimeMs ?: backend.getLong(keyCooldownUntil(appId), 0L))
            .coerceAtLeast(0L)

        // Lazy Daily Reset: só zera quando o dia realmente avançou (ou nunca foi gravado),
        // preservando o cooldown ativo para evitar burla às 23:59 -> 00:00.
        if (storedEpochDay == -1L || currentEpochDay > storedEpochDay) {
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

        // Retrocesso de relógio/fuso: mantém o estado do dia mais avançado já registrado.
        // Sobrescrever aqui zeraria a cota e as sessões, abrindo brecha para burlar o limite diário.

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

        if (currentEpochDay > storedEpochDay) {
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
        } else if (currentEpochDay < storedEpochDay) {
            // Protege a máscara de consistência caso o relógio do sistema seja atrasado ou mude de fuso
            val adjusted = FocusRewardState(
                epochDay = currentEpochDay,
                attemptsToday = 0,
                abortedToday = 0,
                voluntaryExitsToday = 0,
                reclaimedSecondsToday = 0L,
                totalReclaimedSeconds = totalReclaimed,
                totalImpulsesBeaten = totalBeaten,
                consistencyBitmask = storedMask,
                trackedDaysCount = storedTrackedDays
            )
            saveRewardState(adjusted)
            return adjusted
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

    override fun getSessionGuards(): List<SessionGuard> {
        val raw = backend.getString(KEY_SESSION_GUARDS_V1, "")
        if (raw.isBlank()) return emptyList()
        return raw.lines().mapNotNull { line ->
            val parts = line.trim().split("|")
            if (parts.size < 3) return@mapNotNull null
            val appId = parts[0].trim()
            if (appId.isEmpty()) return@mapNotNull null
            val groupId = parts[1].trim()
            val cooldownSeconds = parts[2].trim().toLongOrNull()?.coerceAtLeast(0L) ?: return@mapNotNull null
            SessionGuard(appId = appId, groupId = groupId, cooldownSeconds = cooldownSeconds)
        }
    }

    override fun saveSessionGuards(guards: List<SessionGuard>) {
        val serialized = guards.joinToString("\n") { guard ->
            "${sanitizeField(guard.appId)}|${sanitizeField(guard.groupId)}|${guard.cooldownSeconds.coerceAtLeast(0L)}"
        }
        backend.editBatch {
            putString(KEY_SESSION_GUARDS_V1, serialized)
        }
    }

    /**
     * First-run detection. Existing installations (already carrying groups or a stored
     * app selection) are considered onboarded, so the v2 wizard never interrupts them.
     */
    fun isOnboardingCompleted(): Boolean {
        if (backend.getBoolean(KEY_ONBOARDING_COMPLETED_V2, false)) return true
        val hasStoredGroups = backend.getString(KEY_FOCUS_GROUPS_V1, "").isNotBlank()
        val hasStoredApps = backend.getStringSet(KEY_ENABLED_APP_IDS, emptySet()).isNotEmpty()
        val completed = hasStoredGroups || hasStoredApps
        if (completed) {
            backend.putBoolean(KEY_ONBOARDING_COMPLETED_V2, true)
        }
        return completed
    }

    fun markOnboardingCompleted() {
        backend.putBoolean(KEY_ONBOARDING_COMPLETED_V2, true)
    }

    /**
     * Clears today's usage, cooldowns, session guards and daily rewards while preserving limits.
     */
    fun resetTodayStats() {
        val today = SystemTimeProvider.currentEpochDay()
        val config = getConfig()
        val appIds = buildSet {
            addAll(config.getEffectiveGroups().flatMap { it.appIds })
            addAll(config.customApps.map { it.id })
        }
        for (appId in appIds) {
            val current = getUsageState(appId, today)
            saveUsageState(
                current.copy(
                    epochDay = today,
                    usedDailySeconds = 0L,
                    cooldownUntilRealtimeMs = 0L,
                    sessionsStartedToday = 0
                )
            )
        }
        for (group in config.getEffectiveGroups()) {
            val key = "group_${group.id}"
            val current = getUsageState(key, today)
            saveUsageState(
                current.copy(
                    epochDay = today,
                    usedDailySeconds = 0L,
                    cooldownUntilRealtimeMs = 0L,
                    sessionsStartedToday = 0
                )
            )
        }
        saveSessionGuards(emptyList())
        val reward = getRewardState(today)
        saveRewardState(
            reward.copy(
                attemptsToday = 0,
                abortedToday = 0,
                voluntaryExitsToday = 0,
                reclaimedSecondsToday = 0L
            )
        )
    }

    /**
     * Clears all usage history and accumulated achievements, keeping groups and limits.
     */
    fun resetAllStats() {
        resetTodayStats()
        val today = SystemTimeProvider.currentEpochDay()
        val reward = getRewardState(today)
        saveRewardState(
            reward.copy(
                attemptsToday = 0,
                abortedToday = 0,
                voluntaryExitsToday = 0,
                reclaimedSecondsToday = 0L,
                totalReclaimedSeconds = 0L,
                totalImpulsesBeaten = 0,
                consistencyBitmask = 1L,
                trackedDaysCount = 1
            )
        )
    }

    private fun keyEpochDay(appId: String): String = "usage_${appId}_epoch_day"
    private fun keyUsedSeconds(appId: String): String = "usage_${appId}_used_sec"
    private fun keyCooldownUntil(appId: String): String = "usage_${appId}_cooldown_ms"
    private fun keySessionsToday(appId: String): String = "usage_${appId}_sessions_today"

    private fun sanitizeField(value: String): String = value.replace("|", "_").replace("\n", " ").trim()

    private fun serializeGroups(groups: List<FocusGroup>): String {
        return groups.joinToString("\n") { group ->
            val cleanId = sanitizeField(group.id)
            val cleanName = sanitizeField(group.name)
            val cleanIcon = sanitizeField(group.iconKey)
            val appIdsStr = group.appIds.joinToString(",") { sanitizeField(it) }
            "$cleanId|$cleanName|$cleanIcon|${group.enabled}|$appIdsStr|${group.mindfulPauseSeconds}|${group.sessionDurationMinutes}|${group.cooldownMinutes}|${group.dailyQuotaMinutes}|${group.progressivePauseEnabled}|${group.sharedQuota}"
        }
    }

    private fun deserializeGroups(raw: String): List<FocusGroup> {
        if (raw.isBlank()) return emptyList()
        val result = ArrayList<FocusGroup>()
        for (line in raw.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split("|")
            if (parts.size >= 11) {
                val id = parts[0].trim()
                val name = parts[1].trim()
                if (id.isEmpty() || name.isEmpty()) continue
                val emoji = parts[2].trim().ifEmpty { GroupIcon.SOCIAL.key }
                val icon = GroupIcon.fromStored(emoji)
                val enabled = parts[3].toBooleanStrictOrNull() ?: true
                val appIds = if (parts[4].isBlank()) emptySet() else parts[4].split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                val pauseSec = parts[5].toIntOrNull()?.coerceIn(FocusLimits.PAUSE_SECONDS) ?: DEFAULT_PAUSE_SEC
                val sessionMin = parts[6].toIntOrNull()?.coerceIn(FocusLimits.SESSION_MINUTES) ?: DEFAULT_SESSION_MIN
                val cooldownMin = parts[7].toIntOrNull()?.coerceIn(FocusLimits.COOLDOWN_MINUTES) ?: DEFAULT_COOLDOWN_MIN
                val dailyQuotaMin = parts[8].toIntOrNull()?.coerceIn(FocusLimits.DAILY_QUOTA_MINUTES) ?: DEFAULT_DAILY_QUOTA_MIN
                val progressive = parts[9].toBooleanStrictOrNull() ?: true
                val shared = parts[10].toBooleanStrictOrNull() ?: true

                result.add(
                    FocusGroup(
                        id = id,
                        name = name,
                        iconKey = icon.key,
                        enabled = enabled,
                        appIds = appIds,
                        mindfulPauseSeconds = pauseSec,
                        sessionDurationMinutes = sessionMin,
                        cooldownMinutes = cooldownMin,
                        dailyQuotaMinutes = dailyQuotaMin,
                        progressivePauseEnabled = progressive,
                        sharedQuota = shared
                    )
                )
            }
        }
        return result
    }

    private fun serializeCustomApps(apps: List<TargetApp>): String {
        return apps.joinToString("\n") { app ->
            val cleanId = sanitizeField(app.id)
            val cleanName = sanitizeField(app.displayName)
            val pkgsStr = app.packageNames.joinToString(",") { sanitizeField(it) }
            val cleanColor = sanitizeField(app.brandColorHex)
            "$cleanId|$cleanName|$pkgsStr|$cleanColor"
        }
    }

    private fun deserializeCustomApps(raw: String): List<TargetApp> {
        if (raw.isBlank()) return emptyList()
        val result = ArrayList<TargetApp>()
        for (line in raw.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split("|")
            if (parts.size >= 4) {
                val id = parts[0].trim()
                val name = parts[1].trim()
                if (id.isEmpty() || name.isEmpty()) continue
                val pkgs = if (parts[2].isBlank()) emptySet() else parts[2].split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                val color = parts[3].trim().ifEmpty { "#38BDF8" }
                result.add(
                    TargetApp(
                        id = id,
                        displayName = name,
                        packageNames = pkgs,
                        brandColorHex = color,
                        isCustom = true
                    )
                )
            }
        }
        return result
    }

    companion object {
        private const val KEY_ENABLED_APP_IDS = "cfg_enabled_app_ids"
        private const val KEY_MINDFUL_PAUSE_SEC = "cfg_mindful_pause_sec"
        private const val KEY_SESSION_DURATION_MIN = "cfg_session_duration_min"
        private const val KEY_COOLDOWN_MIN = "cfg_cooldown_min"
        private const val KEY_DAILY_QUOTA_MIN = "cfg_daily_quota_min"
        private const val KEY_PROGRESSIVE_PAUSE_ENABLED = "cfg_progressive_pause"
        private const val KEY_SELECTED_ANCHOR_HABIT = "cfg_selected_anchor_habit"

        private const val KEY_FOCUS_GROUPS_V1 = "cfg_focus_groups_v1"
        private const val KEY_CUSTOM_APPS_V1 = "cfg_custom_apps_v1"
        private const val KEY_SHARED_QUOTA_DEFAULT = "cfg_shared_quota_default"
        private const val KEY_SESSION_GUARDS_V1 = "session_guards_v1"
        private const val KEY_ONBOARDING_COMPLETED_V2 = "onboarding_completed_v2"

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
