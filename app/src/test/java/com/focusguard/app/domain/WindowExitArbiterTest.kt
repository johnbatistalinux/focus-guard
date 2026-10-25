package com.focusguard.app.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WindowExitArbiterTest {

    private val instagramPackages = setOf("com.instagram.android", "com.instagram.lite")

    @Test
    fun `teclado aberto sobre o Instagram mantem a sessao visivel`() {
        val snapshot = WindowExitArbiter.ForegroundSnapshot(
            applicationPackages = listOf("com.instagram.android"),
            applicationWindowCount = 1,
            isImeVisible = true
        )

        assertEquals(
            WindowExitArbiter.Verdict.SESSION_VISIBLE,
            WindowExitArbiter.evaluate(instagramPackages, snapshot)
        )
    }

    @Test
    fun `painel auxiliar da OEM sobre o Instagram mantem a sessao visivel`() {
        val snapshot = WindowExitArbiter.ForegroundSnapshot(
            applicationPackages = listOf("com.samsung.android.suggestions", "com.instagram.android"),
            applicationWindowCount = 2,
            isImeVisible = true
        )

        assertEquals(
            WindowExitArbiter.Verdict.SESSION_VISIBLE,
            WindowExitArbiter.evaluate(instagramPackages, snapshot)
        )
    }

    @Test
    fun `somente o WhatsApp visivel e totalmente atribuido resolve sessao escondida`() {
        val snapshot = WindowExitArbiter.ForegroundSnapshot(
            applicationPackages = listOf("com.whatsapp"),
            applicationWindowCount = 1,
            isImeVisible = false
        )

        assertEquals(
            WindowExitArbiter.Verdict.SESSION_HIDDEN,
            WindowExitArbiter.evaluate(instagramPackages, snapshot)
        )
    }

    @Test
    fun `alias Instagram Lite visivel mantem a sessao principal visivel`() {
        val snapshot = WindowExitArbiter.ForegroundSnapshot(
            applicationPackages = listOf("com.instagram.lite"),
            applicationWindowCount = 1,
            isImeVisible = true
        )

        assertEquals(
            WindowExitArbiter.Verdict.SESSION_VISIBLE,
            WindowExitArbiter.evaluate(instagramPackages, snapshot)
        )
    }

    @Test
    fun `janela de aplicacao sem raiz legivel resolve desconhecido`() {
        val snapshot = WindowExitArbiter.ForegroundSnapshot(
            applicationPackages = emptyList(),
            applicationWindowCount = 1,
            isImeVisible = false
        )

        assertEquals(
            WindowExitArbiter.Verdict.UNKNOWN,
            WindowExitArbiter.evaluate(instagramPackages, snapshot)
        )
    }

    @Test
    fun `lista de janelas vazia resolve desconhecido`() {
        assertEquals(
            WindowExitArbiter.Verdict.UNKNOWN,
            WindowExitArbiter.evaluate(instagramPackages, WindowExitArbiter.ForegroundSnapshot.UNKNOWN)
        )
    }
}
