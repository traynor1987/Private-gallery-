package uk.co.traynor.privategallery.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherExitChallengeTest {
    @Test fun pinAloneAndWrongPhraseNeverAuthorizeTheExit() {
        val start = LauncherExitChallenge("session")
        assertNull(start.verifyPin(false))
        assertNull(start.verifyPhrase("change my home app", "Change my Home app"))
        assertNull(start.beginHold(0))

        val phrase = checkNotNull(start.verifyPin(true))
        assertNull(phrase.verifyPhrase("do not change my home app", "Change my Home app"))
        assertNull(phrase.beginHold(0))
        assertFalse(phrase.finishHold(20_000, "session"))
    }

    @Test fun exactPhraseAndAContinuousFiveSecondHoldAuthorizeOnlyTheOriginalSession() {
        val hold = challengeAtHold()
        val pressed = checkNotNull(hold.beginHold(2_000))
        assertFalse(pressed.finishHold(6_999, "session"))
        assertFalse(pressed.finishHold(7_000, "replacement"))
        assertFalse(pressed.finishHold(7_000, null))
        assertTrue(pressed.finishHold(7_000, "session"))
    }

    @Test fun cancellingOrRestartingAHoldDiscardsEarlierElapsedTime() {
        val pressed = checkNotNull(challengeAtHold().beginHold(1_000))
        val cancelled = pressed.cancelHold()
        assertFalse(cancelled.finishHold(9_000, "session"))
        val restarted = checkNotNull(cancelled.beginHold(9_000))
        assertFalse(restarted.finishHold(13_999, "session"))
        assertTrue(restarted.finishHold(14_000, "session"))
    }

    @Test fun cooldownUsesMonotonicTimeUntilRebootThenBoundsWallClockRecovery() {
        val initial = LauncherExitAttempts()
        val now = LauncherExitTime(20_000, 200_000, 7)
        val failed = initial.failed(now, 60_000).failed(now, 60_000).failed(now, 60_000)
        assertEquals(60_000L, failed.remaining(now, 60_000))
        assertEquals(50_000L, failed.remaining(LauncherExitTime(30_000, 2, 7), 60_000))
        assertEquals(40_000L, failed.remaining(LauncherExitTime(1_000, 220_000, 8), 60_000))
        assertEquals(60_000L, failed.remaining(LauncherExitTime(1_000, 1, 8), 60_000))
        assertEquals(0L, failed.remaining(LauncherExitTime(80_000, 260_000, 7), 60_000))
        assertEquals(1, failed.failed(LauncherExitTime(80_000, 260_000, 7), 60_000).failures)
    }

    private fun challengeAtHold(): LauncherExitChallenge = checkNotNull(
        checkNotNull(LauncherExitChallenge("session").verifyPin(true))
            .verifyPhrase("  CHANGE my home app. ", "Change my Home app"),
    )
}
