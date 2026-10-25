package com.focusguard.app.service

import android.os.Handler
import android.os.Looper

/**
 * Cronômetro determinístico de 1 Hz (1 pulso por segundo) rodando diretamente na Main Thread.
 *
 * Projetado para substituir `kotlinx-coroutines` e `TimerThread`:
 * - Reutiliza uma única instância de [Runnable] (zero alocação de objetos por segundo no Heap).
 * - Fica 100% desativado quando não há pausa consciente, sessão ativa ou tela de cooldown visível.
 */
class MainThreadTicker(
    private val onTick: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            onTick()
            if (isRunning) {
                handler.postDelayed(this, TICK_INTERVAL_MS)
            }
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        handler.removeCallbacks(tickRunnable)
        handler.postDelayed(tickRunnable, TICK_INTERVAL_MS)
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        handler.removeCallbacks(tickRunnable)
    }

    companion object {
        private const val TICK_INTERVAL_MS = 1000L
    }
}
