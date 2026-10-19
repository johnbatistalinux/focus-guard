package com.focusguard.app.domain

/**
 * Abstração de relógio para permitir testes unitários 100% determinísticos sem depender do Android SDK.
 */
interface TimeProvider {
    /**
     * Retorna o tempo monotônico desde o boot em milissegundos (`SystemClock.elapsedRealtime()`).
     * Imune a alterações manuais do relógio do sistema pelo usuário.
     */
    fun elapsedRealtimeMs(): Long

    /**
     * Retorna o dia atual (`LocalDate.now().toEpochDay()`) para controle de reset diário à meia-noite.
     */
    fun currentEpochDay(): Long
}

/**
 * Contrato de leitura/escrita de configurações, estado de uso e reforço positivo.
 */
interface FocusPolicyStore {
    fun getConfig(): FocusConfig
    fun saveConfig(config: FocusConfig)
    fun getUsageState(appId: String, currentEpochDay: Long): AppUsageState
    fun saveUsageState(state: AppUsageState)
    fun getRewardState(currentEpochDay: Long): FocusRewardState
    fun saveRewardState(state: FocusRewardState)
}
