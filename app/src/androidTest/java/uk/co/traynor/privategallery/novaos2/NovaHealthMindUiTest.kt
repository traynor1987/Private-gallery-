package uk.co.traynor.privategallery.novaos2

import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.home2.SpaceContext
import uk.co.traynor.privategallery.home2.MindData
import uk.co.traynor.privategallery.ui.BrowserViewportRule

class NovaHealthMindUiTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rule = RuleChain.outerRule(BrowserViewportRule(fontScale = 1.15f) {
        if (it.startsWith("inner")) 820 to 920 else 375 to 850
    }).around(compose)

    @Test fun coverLogsManualMeasurementWithValidationAndNoAutomaticRead() = withSpace { space, _, _ ->
        compose.setContent { MaterialTheme(colorScheme = darkColorScheme()) { HealthMindScreen(space, {}) } }
        val log = compose.onNodeWithTag("health-log-measurement")
        compose.waitUntil(8_000) { log.isDisplayed() }
        log.performClick()
        val save = compose.onNodeWithTag("health-save-measurement")
        save.assertIsNotEnabled()
        compose.onNodeWithTag("health-value").performTextInput("450")
        save.performClick()
        compose.waitUntil(8_000) { compose.onNodeWithText("450 mL").isDisplayed() }
        captureNovaSystem("health-cover")
        compose.onNodeWithText("Data & sources").performClick()
        compose.onNodeWithText("Health Connect").assertIsDisplayed()
        compose.onNodeWithTag("health-steps-status").assertDoesNotExist()
    }

    @Test fun innerWeeklyReviewAndFoldingKeepTheMeasuredValues() = withSpace { space, _, _ ->
        HealthRecordStore(space).update {
            it.withMeasurement(LocalDate.now(), HealthMetric.SLEEP, HealthMetric.SLEEP.parse("410"))
                .withMeasurement(LocalDate.now(), HealthMetric.WEIGHT, HealthMetric.WEIGHT.parse("72.25"))
        }
        var width by mutableStateOf(820.dp)
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Box(Modifier.width(width)) { HealthMindScreen(space, {}) }
            }
        }
        compose.onNodeWithText("Weekly review").performClick()
        compose.waitUntil(8_000) { compose.onNodeWithText("72.25 kg").isDisplayed() }
        compose.onNodeWithText("410 min · 1 entry").assertIsDisplayed()
        captureNovaSystem("health-inner")
        compose.runOnIdle { width = 375.dp }
        compose.onNodeWithText("72.25 kg").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { width = 820.dp }
        compose.onNodeWithText("72.25 kg").assertIsDisplayed()
    }

    @Test fun encryptedLogReopensAndHealthDeletionLeavesJournalIntact() = withSpace { space, root, reopen ->
        val marker = "Synthetic Health and Mind storage marker"
        val day = LocalDate.now()
        HealthRecordStore(space).update { it.copy(goal = marker) }
        assertTrue(MindData(space).put("primary", "Only journal", journal = true))
        assertTrue(root.walkTopDown().any { it.isFile && it.extension == "enc" })
        assertFalse(root.walkTopDown().filter(File::isFile).any { it.readBytes().decodeToString().contains(marker) })
        space.close()
        reopen().use { fresh ->
            val restarted = HealthRecordStore(fresh)
            assertEquals(marker, restarted.read().goal)
            restarted.delete()
            assertEquals(HealthRecords(), restarted.read())
            assertEquals("Only journal", MindData(fresh).entries("primary").single().text)
            assertTrue(restarted.read().days[day] == null)
        }
    }

    @Test fun deniedHealthPermissionStopsBeforeProviderQuery() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val denied = object : ContextWrapper(app) {
            override fun checkSelfPermission(permission: String): Int {
                assertEquals(HEALTH_READ_STEPS, permission)
                return PackageManager.PERMISSION_DENIED
            }
            override fun getSystemService(name: String): Any? = error("Health Connect was queried without consent")
        }
        assertEquals(HealthStepsResult.PermissionDenied, readHealthSteps(denied))
    }

    private fun withSpace(run: (SpaceContext, File, () -> SpaceContext) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "health-fixture-${UUID.randomUUID()}")
        val isolated = object : ContextWrapper(app) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "private").apply { mkdirs() }
        }
        val identity = Any()
        val gate = Any()
        try {
            EvidenceAccess.primary(isolated, identity, { identity }, { true }, { ByteArray(32) { 46 } }, gate).use { access ->
                val reopen = { SpaceContext(isolated, access) }
                reopen().use { space -> run(space, root, reopen) }
            }
        } finally { root.deleteRecursively() }
    }
}
