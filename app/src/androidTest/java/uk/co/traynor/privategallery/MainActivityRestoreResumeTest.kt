package uk.co.traynor.privategallery

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.ui.PrivateGalleryTheme
import uk.co.traynor.privategallery.core.ui.AppTheme

class MainActivityRestoreResumeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lockedRestartCanRequestArchiveResumeWithoutCreatingFreshVault() {
        var resumeRequests = 0
        var unlockRequests = 0
        compose.setContent {
            PrivateGalleryTheme(AppTheme.SYSTEM) {
                PinUnlock(onUnlock = { unlockRequests++; Result.failure(IllegalStateException("Synthetic locked root")) },
                    biometricEnabled = false, onBiometricUnlock = {}, onForgotPin = {},
                    onResumeBackupRestore = { resumeRequests++ })
            }
        }
        compose.onNodeWithText("Resume encrypted backup restore").performClick()
        compose.runOnIdle { assertEquals(1, resumeRequests); assertEquals(0, unlockRequests) }
    }
}
