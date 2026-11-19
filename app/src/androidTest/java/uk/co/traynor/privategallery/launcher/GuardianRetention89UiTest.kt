package uk.co.traynor.privategallery.launcher

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.guardian.*

class GuardianRetention89UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val rows = listOf(GuardianInboxRow("synthetic", 1_800_000_000_000, GuardianInboxSection.HISTORY, 0,
        savedForTraining = true))
    @Test fun retentionAndBulkDeletionRequireExplicitTrainingDeletion() {
        var days: Int? = null
        var deletion: Pair<Set<String>, Boolean>? = null
        compose.setContent { GuardianTheme { Surface(Modifier.fillMaxSize()) {
            GuardianRetentionScreen89(rows, 30, false, null, null, false,
                { days = it }, {}, { ids, training -> deletion = ids to training }, {}, {}, {})
        } } }
        compose.onNodeWithTag("guardian-retention89").performScrollToNode(hasText("Keep Forever"))
        compose.onNodeWithText("Keep Forever").performClick()
        assertEquals(0, days)
        compose.onNodeWithTag("guardian-retention89").performScrollToNode(hasText("Select all shown"))
        compose.onNodeWithText("Select all shown").performClick()
        compose.onNodeWithText("Delete selected…").performClick()
        assertNull(deletion)
        compose.onNodeWithText("Cancel").performClick()
        assertNull(deletion)
        compose.onNodeWithText("Delete selected…").performClick()
        compose.onNodeWithText("Permanently delete").performClick()
        assertEquals(setOf("synthetic") to false, deletion)
    }
    @Test fun recoveryDoesNotRunUntilOwnerApprovesAndDirectReviewIsAccessible() {
        var inspected = false
        var restored = false
        var opened: String? = null
        compose.setContent { GuardianTheme { Surface(Modifier.fillMaxSize()) {
            GuardianRetentionScreen89(rows, 0, false, GuardianRecoveryReport89(1, 2, 1, 0), null, false,
                {}, { opened = it.id }, { _, _ -> }, {}, { inspected = true }, { restored = true })
        } } }
        compose.onNodeWithTag("guardian-retention89").performScrollToNode(hasText("Check encrypted storage & recovery"))
        compose.onNodeWithText("Check encrypted storage & recovery").performClick()
        assertTrue(inspected)
        assertFalse(restored)
        compose.onNodeWithTag("guardian-retention89").performScrollToNode(hasText("Review recovery"))
        compose.onNodeWithText("Review recovery").performClick()
        assertFalse(restored)
        compose.onNodeWithText("Restore to History").performClick()
        assertTrue(restored)
        compose.onNodeWithTag("guardian-retention89").performScrollToNode(hasText("Review / Teach Guardian"))
        compose.onNodeWithText("Review / Teach Guardian").performClick()
        assertEquals("synthetic", opened)
    }
}
