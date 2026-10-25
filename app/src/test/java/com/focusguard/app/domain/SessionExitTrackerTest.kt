package com.focusguard.app.domain

import com.focusguard.app.domain.WindowExitArbiter.Verdict
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionExitTrackerTest {

    @Test
    fun `sessao visivel nunca confirma saida e zera as sequencias`() {
        val tracker = SessionExitTracker()
        repeat(10) {
            assertFalse(tracker.onTick(Verdict.SESSION_VISIBLE))
        }

        assertFalse(tracker.onTick(Verdict.SESSION_HIDDEN))
        assertFalse(tracker.onTick(Verdict.SESSION_VISIBLE))
        // A sequencia de HIDDEN foi zerada pelo tick visivel.
        assertFalse(tracker.onTick(Verdict.SESSION_HIDDEN))
        assertTrue(tracker.onTick(Verdict.SESSION_HIDDEN))
    }

    @Test
    fun `um tick escondido nao basta mas dois confirmam a saida`() {
        val tracker = SessionExitTracker()

        assertFalse(tracker.onTick(Verdict.SESSION_HIDDEN))
        assertTrue(tracker.onTick(Verdict.SESSION_HIDDEN))
    }

    @Test
    fun `desconhecido intercalado reinicia a sequencia escondida`() {
        val tracker = SessionExitTracker()

        assertFalse(tracker.onTick(Verdict.SESSION_HIDDEN))
        assertFalse(tracker.onTick(Verdict.UNKNOWN))
        assertFalse(tracker.onTick(Verdict.SESSION_HIDDEN))
        assertTrue(tracker.onTick(Verdict.SESSION_HIDDEN))
    }

    @Test
    fun `cinco ticks desconhecidos confirmam a saida e quatro nao`() {
        val tracker = SessionExitTracker()

        repeat(4) {
            assertFalse(tracker.onTick(Verdict.UNKNOWN))
        }
        assertTrue(tracker.onTick(Verdict.UNKNOWN))
    }

    @Test
    fun `reset limpa as duas sequencias`() {
        val tracker = SessionExitTracker()

        repeat(4) { tracker.onTick(Verdict.UNKNOWN) }
        assertFalse(tracker.onTick(Verdict.SESSION_HIDDEN))
        tracker.reset()

        repeat(4) {
            assertFalse(tracker.onTick(Verdict.UNKNOWN))
        }
        assertFalse(tracker.onTick(Verdict.SESSION_HIDDEN))
        assertTrue(tracker.onTick(Verdict.SESSION_HIDDEN))
    }
}
