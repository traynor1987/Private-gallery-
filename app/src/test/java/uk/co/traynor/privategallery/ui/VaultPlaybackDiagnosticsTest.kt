package uk.co.traynor.privategallery.ui

import org.junit.Assert.*
import org.junit.Test

class VaultPlaybackDiagnosticsTest {
    @Test fun boundedSummaryRetainsFailuresAndClearsForNewAttempt() {
        VaultPlaybackDiagnostics.begin()
        VaultPlaybackDiagnostics.record(VaultPlaybackEvent.AUTHENTICATED)
        VaultPlaybackDiagnostics.record(VaultPlaybackEvent.PLAYER_ERROR, 4001)
        VaultPlaybackDiagnostics.record(VaultPlaybackEvent.RELEASED)
        assertTrue(VaultPlaybackDiagnostics.summary().contains("PLAYER_ERROR=4001"))
        VaultPlaybackDiagnostics.begin()
        assertEquals("READ_STARTED", VaultPlaybackDiagnostics.summary())
        repeat(100) { VaultPlaybackDiagnostics.record(VaultPlaybackEvent.PLAYER_STATE, it % 4) }
        assertTrue(VaultPlaybackDiagnostics.summary().lines().size <= 24)
    }

    @Test fun duplicateSamplesDoNotHideEarlierStage() {
        VaultPlaybackDiagnostics.begin()
        repeat(100) { VaultPlaybackDiagnostics.record(VaultPlaybackEvent.READ_PROGRESS, 40) }
        assertEquals("READ_STARTED\nREAD_PROGRESS=40", VaultPlaybackDiagnostics.summary())
    }
}
