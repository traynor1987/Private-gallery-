package uk.co.traynor.privategallery.core.update

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Updater86UiTest {
    @get:Rule val compose=createComposeRule()
    private fun presentation(width: Int) {
        val state=mutableStateOf(NovaUpdateState())
        val release=ReleaseMetadata(ReleaseVersion.parse("1.7.40"),"","","","Synthetic release notes",86,apkSizeBytes=100_000_000)
        var cancelled=false
        var retried=false
        compose.setContent { MaterialTheme { Box(Modifier.width(width.dp)) { NovaUpdateDownloadInterface(state.value,{cancelled=true},{retried=true}) } } }
        compose.runOnIdle { state.value=NovaUpdateState(UpdatePhase.DOWNLOADING,"Downloading…",release,25_000_000,100_000_000,2_000_000) }
        compose.onNodeWithTag("update-percent").assertTextEquals("25%")
        compose.onNodeWithTag("update-byte-count").assertTextEquals("25.0 / 100.0 MB")
        compose.onNodeWithText("2.0 MB/s").assertIsDisplayed()
        compose.onNodeWithText("Cancel download").performClick()
        compose.runOnIdle { assertTrue(cancelled);state.value=state.value.copy(phase=UpdatePhase.FAILED,message="Download or verification failed. Retry safely.") }
        compose.onNodeWithText("Retry download").performClick()
        compose.runOnIdle { assertTrue(retried);state.value=state.value.copy(phase=UpdatePhase.VERIFYING,message="Verifying package, signature and integrity…",bytes=100_000_000) }
        compose.onNodeWithText("Checking SHA-256, NovaOS package, original signing certificate and build number.").assertIsDisplayed()
        compose.onNodeWithTag("update-percent").assertDoesNotExist()
    }
    @Test fun foldedDownloadShowsRealPercentageBytesSpeedCancelRetryAndVerification() { presentation(320) }
    @Test fun unfoldedDownloadShowsRealPercentageBytesSpeedCancelRetryAndVerification() { presentation(700) }
}
