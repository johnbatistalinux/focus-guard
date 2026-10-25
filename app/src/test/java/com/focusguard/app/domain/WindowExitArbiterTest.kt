package com.focusguard.app.domain

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WindowExitArbiterTest {

    private val instagramPackages = setOf("com.instagram.android", "com.instagram.lite")

    @Test
    fun `teclado aberto mantem a sessao quando a janela do Instagram segue visivel`() {
        val snapshot = WindowExitArbiter.WindowSnapshot(
            visibleApplicationPackages = listOf("com.instagram.android"),
            visibleApplicationWindowCount = 1,
            isImeVisible = true
        )

        assertFalse(
            WindowExitArbiter.isRealExit(
                eventPackage = "com.samsung.android.honeyboard.helper",
                monitoredPackages = instagramPackages,
                isHomeLauncherEvent = false,
                snapshot = snapshot
            )
        )
    }

    @Test
    fun `painel auxiliar da OEM sobre o app nao pausa a sessao`() {
        val snapshot = WindowExitArbiter.WindowSnapshot(
            visibleApplicationPackages = listOf("com.samsung.android.suggestions", "com.instagram.android"),
            visibleApplicationWindowCount = 2,
            isImeVisible = true
        )

        assertFalse(
            WindowExitArbiter.isRealExit(
                eventPackage = "com.samsung.android.suggestions",
                monitoredPackages = instagramPackages,
                isHomeLauncherEvent = false,
                snapshot = snapshot
            )
        )
    }

    @Test
    fun `troca real de app pausa a sessao quando o Instagram nao esta mais visivel`() {
        val snapshot = WindowExitArbiter.WindowSnapshot(
            visibleApplicationPackages = listOf("com.whatsapp"),
            visibleApplicationWindowCount = 1,
            isImeVisible = false
        )

        assertTrue(
            WindowExitArbiter.isRealExit(
                eventPackage = "com.whatsapp",
                monitoredPackages = instagramPackages,
                isHomeLauncherEvent = false,
                snapshot = snapshot
            )
        )
    }

    @Test
    fun `launcher sempre conta como saida explicita`() {
        val snapshot = WindowExitArbiter.WindowSnapshot(
            visibleApplicationPackages = listOf("com.sec.android.app.launcher"),
            visibleApplicationWindowCount = 1,
            isImeVisible = false
        )

        assertTrue(
            WindowExitArbiter.isRealExit(
                eventPackage = "com.sec.android.app.launcher",
                monitoredPackages = instagramPackages,
                isHomeLauncherEvent = true,
                snapshot = snapshot
            )
        )
    }

    @Test
    fun `evento do proprio app monitorado nunca e saida`() {
        assertFalse(
            WindowExitArbiter.isRealExit(
                eventPackage = "com.instagram.android",
                monitoredPackages = instagramPackages,
                isHomeLauncherEvent = false,
                snapshot = WindowExitArbiter.WindowSnapshot.UNKNOWN
            )
        )
    }

    @Test
    fun `janela de alias do app monitorado tambem protege a sessao`() {
        val snapshot = WindowExitArbiter.WindowSnapshot(
            visibleApplicationPackages = listOf("com.instagram.lite"),
            visibleApplicationWindowCount = 1,
            isImeVisible = true
        )

        assertFalse(
            WindowExitArbiter.isRealExit(
                eventPackage = "com.samsung.android.honeyboard",
                monitoredPackages = instagramPackages,
                isHomeLauncherEvent = false,
                snapshot = snapshot
            )
        )
    }

    @Test
    fun `janela sem raiz legivel com teclado na tela mantem a sessao`() {
        val snapshot = WindowExitArbiter.WindowSnapshot(
            visibleApplicationPackages = emptyList(),
            visibleApplicationWindowCount = 1,
            isImeVisible = true
        )

        assertFalse(
            WindowExitArbiter.isRealExit(
                eventPackage = "com.samsung.android.honeyboard.helper",
                monitoredPackages = instagramPackages,
                isHomeLauncherEvent = false,
                snapshot = snapshot
            )
        )
    }

    @Test
    fun `janela sem raiz legivel e sem teclado aceita a saida`() {
        val snapshot = WindowExitArbiter.WindowSnapshot(
            visibleApplicationPackages = emptyList(),
            visibleApplicationWindowCount = 1,
            isImeVisible = false
        )

        assertTrue(
            WindowExitArbiter.isRealExit(
                eventPackage = "com.whatsapp",
                monitoredPackages = instagramPackages,
                isHomeLauncherEvent = false,
                snapshot = snapshot
            )
        )
    }

    @Test
    fun `leitura de janelas indisponivel mantem a sessao enquanto o teclado esta na tela`() {
        val snapshot = WindowExitArbiter.WindowSnapshot(
            visibleApplicationPackages = emptyList(),
            visibleApplicationWindowCount = 0,
            isImeVisible = true
        )

        assertFalse(
            WindowExitArbiter.isRealExit(
                eventPackage = "com.samsung.android.honeyboard.helper",
                monitoredPackages = instagramPackages,
                isHomeLauncherEvent = false,
                snapshot = snapshot
            )
        )
    }
}
