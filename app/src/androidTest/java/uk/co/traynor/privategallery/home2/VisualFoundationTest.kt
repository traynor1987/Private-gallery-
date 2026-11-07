package uk.co.traynor.privategallery.home2

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.ReleaseScreenshots62
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.ui.PrivateGalleryTheme
import java.io.File

class VisualFoundationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun coreModulesNavigateWithDemoStatusAtFoldWidths() = verifyModules(
        listOf("health", "notes", "files", "calendar", "tasks", "clipboard", "time"),
    )

    private fun verifyModules(ids: List<String>) {
        val navigator = VisualNavigator()
        var width by mutableIntStateOf(360)
        val launches = mutableListOf<String>()
        compose.setContent {
            PrivateGalleryTheme(AppTheme.DARK) {
                Box(Modifier.requiredSize(width.dp, 720.dp)) {
                    VisualFoundation(navigator, onClose = {}, onRealApp = { launches.add(it) })
                }
            }
        }
        for (targetWidth in listOf(360, 840)) {
            compose.runOnIdle { width = targetWidth }
            for (id in ids) {
                compose.onNodeWithTag("visual-catalogue-list").performScrollToNode(hasTestTag("visual-module-$id"))
                compose.onNodeWithTag("visual-module-$id").assertIsDisplayed().performClick()
                compose.onNodeWithTag("visual-demo-indicator").assertIsDisplayed()
                compose.onNodeWithTag("visual-screen-$id").assertIsDisplayed()
                val module = visualModules.single { it.id == id }
                module.sections.indices.forEach { index ->
                    compose.onNodeWithTag("visual-sections").performScrollToNode(hasTestTag("visual-section-$index"))
                    compose.onNodeWithTag("visual-section-$index").assertIsDisplayed().performClick()
                    compose.onNodeWithTag("visual-demo-indicator").assertIsDisplayed()
                    val section = module.sections[index]
                    compose.onNodeWithText(section.headline).performScrollTo().assertIsDisplayed()
                    if (section.timeline.isNotEmpty()) {
                        assertTrue(section.timeline.sumOf { it.minutes } == 1440)
                        compose.onNodeWithTag("visual-daily-timeline").performScrollTo().assertIsDisplayed()
                    }
                    if (section.trend != null) {
                        compose.onNodeWithText(section.trend.title).performScrollTo().assertIsDisplayed()
                        ReleaseScreenshots62.capture(compose.onNodeWithTag("visual-screen-$id"), "visual-$id-trend-$index-$targetWidth")
                    }
                    if (section.rows.isNotEmpty()) {
                        compose.onNodeWithTag("visual-row-0").performScrollTo().assertIsDisplayed().performClick()
                        compose.onNodeWithText("Example detail · no action has been performed").performScrollTo().assertIsDisplayed()
                        compose.onNodeWithContentDescription("Back to demo section").performClick()
                    }
                }
                compose.runOnIdle { navigator.openSection(0) }
                ReleaseScreenshots62.capture(compose.onNodeWithTag("visual-screen-$id"), "visual-$id-$targetWidth")
                compose.onNodeWithContentDescription("Back to visual catalogue").performClick()
                compose.onNodeWithTag("visual-catalogue-list").assertIsDisplayed()
            }
        }
        compose.runOnIdle { assertTrue(launches.isEmpty()) }
    }

    @Test fun catalogueOpensFromRealPrimaryHomeAndStoreButIsAbsentInHidden() {
        val base = compose.activity
        val directory = File(base.cacheDir, "visual-foundation-fixture").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(directory, "protected").apply { mkdirs() }
            override fun getApplicationContext(): Context = base.applicationContext
        }
        val identity = Any()
        val primary = EvidenceAccess.primary(context, identity, { identity }, { true }, { ByteArray(32) { 41 } }, Any())
        val session = HiddenSession(android.os.SystemClock::elapsedRealtime)
        check(session.complete(session.beginAuthentication(), ByteArray(32) { 73 }))
        val hidden = EvidenceAccess.hidden(context, session) { true }
        var access by mutableStateOf(primary)
        var attached by mutableStateOf(true)
        try {
            compose.setContent {
                if (attached) key(access) {
                    CompositionLocalProvider(LocalContext provides context) {
                        IntegratedHome(access, true, {}, {}, {}, null, null)
                    }
                }
            }
            compose.onNodeWithTag("open-apps").performClick()
            compose.onNodeWithTag("drawer-visual-foundation").assertIsDisplayed().performClick()
            compose.onNodeWithTag("visual-catalogue-list").assertIsDisplayed()
            compose.onNodeWithTag("nova-home").performClick()
            compose.onNodeWithTag("open-apps").performClick()
            compose.onNodeWithTag("drawer-search").performTextInput("Nova Store")
            compose.onNodeWithTag("app-store").performClick()
            compose.onNodeWithTag("store-visual-foundation").assertIsDisplayed().performClick()
            compose.onNodeWithTag("visual-catalogue-list").assertIsDisplayed()
            compose.onNodeWithContentDescription("Close visual catalogue").performClick()
            compose.onNodeWithTag("nova-store").assertIsDisplayed()

            compose.runOnIdle { access = hidden }
            compose.onNodeWithTag("open-apps").performClick()
            compose.onNodeWithTag("drawer-sheet").assertIsDisplayed()
            compose.onNodeWithTag("drawer-visual-foundation").assertDoesNotExist()
            compose.onNodeWithTag("visual-catalogue-list").assertDoesNotExist()
        } finally {
            compose.runOnIdle { attached = false }
            compose.waitForIdle()
            primary.close()
            hidden.close()
            session.lock()
            directory.deleteRecursively()
        }
    }
}
