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

    @Test fun privacyModulesNavigateWithDemoStatusAtFoldWidths() = verifyModules(
        listOf("keys", "app-permissions", "privacy-dashboard", "spaces", "sanctuary"),
    )

    @Test fun aiModulesNavigateWithDemoStatusAtFoldWidths() = verifyModules(listOf("people", "ai", "ai-search"))

    @Test fun systemModulesNavigateWithDemoStatusAtFoldWidths() = verifyModules(listOf(
        "desktop-mode", "control-centre", "themes-studio", "lock-studio", "pip-controls", "contacts",
        "notifications", "media", "downloads", "screen-recorder", "share-hub", "automations",
        "app-usage", "update-centre", "browser-privacy",
    ))

    @Test fun studioControlsChangeOnlyTheLocalExample() {
        val navigator = VisualNavigator().apply { open("desktop-mode") }
        compose.setContent { VisualFoundation(navigator, onClose = {}, onRealApp = { error("Unexpected real app") }) }
        compose.onNodeWithTag("studio-desktop-layout-1").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Sample calendar").assertDoesNotExist()
        compose.runOnIdle { navigator.open("themes-studio") }
        compose.onNodeWithTag("studio-theme-palette-1").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { navigator.open("lock-studio") }
        compose.onNodeWithTag("studio-lock-alert").performScrollTo().performClick()
        compose.onNodeWithText("Example alert · Calendar").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("visual-demo-indicator").assertIsDisplayed()
    }

    @Test fun peopleShowsOnlyScriptedRepliesAndClearsItsOwnDemoState() {
        val navigator = VisualNavigator().apply { open("people") }
        compose.setContent { VisualFoundation(navigator, onClose = {}, onRealApp = { error("Unexpected real app") }) }
        compose.onNodeWithTag("people-character-orbit").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("people-profile").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("people-open-conversation").performScrollTo().performClick()
        compose.onNodeWithTag("people-show-reply").performScrollTo().performClick()
        compose.onNodeWithTag("people-demo-reply").performScrollTo().assertIsDisplayed()
        ReleaseScreenshots62.capture(compose.onNodeWithTag("visual-screen-people"), "visual-people-conversation")
        compose.onNodeWithTag("visual-sections").performScrollToNode(hasTestTag("visual-section-7"))
        compose.onNodeWithTag("visual-section-7").performClick()
        compose.onNodeWithTag("people-delete-demo").performScrollTo().performClick()
        compose.onNodeWithTag("people-confirm-delete").performScrollTo().performClick()
        compose.onNodeWithTag("people-deleted").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("visual-demo-indicator").assertIsDisplayed()
    }

    @Test fun aiEntryPointsOnlyLaunchExistingAppsOnExplicitTap() {
        val navigator = VisualNavigator().apply { open("ai") }
        val launches = mutableListOf<String>()
        compose.setContent { VisualFoundation(navigator, onClose = {}, onRealApp = { launches.add(it) }) }
        compose.runOnIdle { assertTrue(launches.isEmpty()) }
        compose.onNodeWithText("Open Nova Mind").performScrollTo().performClick()
        compose.runOnIdle { navigator.open("ai-search") }
        compose.onNodeWithText("Open Evidence Locker").performScrollTo().performClick()
        compose.runOnIdle { navigator.open("update-centre") }
        compose.onNodeWithText("Open Nova Backup").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(launches == listOf("mind", "evidence", "backup")) }
    }

    @Test fun sanctuaryCountdownAndTypedExitAreOnlySimulated() {
        val navigator = VisualNavigator().apply { open("sanctuary"); openSection(1) }
        compose.setContent { VisualFoundation(navigator, onClose = {}, onRealApp = { error("Unexpected real app") }) }
        compose.onNodeWithTag("guardian-duration-15").performScrollTo().performClick()
        compose.onNodeWithTag("guardian-start").performScrollTo().performClick()
        compose.onNodeWithTag("guardian-remaining").performScrollTo().assertTextEquals("15:00")
        ReleaseScreenshots62.capture(compose.onNodeWithTag("visual-screen-sanctuary"), "visual-sanctuary-countdown")
        compose.onNodeWithTag("guardian-advance").performScrollTo().performClick()
        compose.onNodeWithTag("guardian-remaining").performScrollTo().assertTextEquals("10:00")
        compose.onNodeWithTag("guardian-open-exit").performScrollTo().performClick()
        compose.onNodeWithTag("guardian-confirm-exit").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("guardian-exit-phrase").performScrollTo().performTextInput("END DEMO")
        compose.onNodeWithTag("guardian-confirm-exit").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("guardian-remaining").performScrollTo().assertTextEquals("Demo session ended")
        compose.onNodeWithTag("guardian-inactive").performScrollTo().assertIsDisplayed()
    }

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
                compose.onNodeWithTag("visual-module-$id").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
                compose.onNodeWithTag("visual-demo-indicator").assertIsDisplayed()
                compose.onNodeWithTag("visual-screen-$id").assertIsDisplayed()
                val module = visualModules.single { it.id == id }
                module.sections.indices.forEach { index ->
                    compose.onNodeWithTag("visual-sections").performScrollToNode(hasTestTag("visual-section-$index"))
                    compose.onNodeWithTag("visual-section-$index").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
                    compose.onNodeWithTag("visual-demo-indicator").assertIsDisplayed()
                    val section = module.sections[index]
                    compose.onNodeWithTag("visual-active-section").assertIsDisplayed()
                    if (section.timeline.isNotEmpty()) {
                        assertTrue(section.timeline.sumOf { it.minutes } == 1440)
                        compose.onNodeWithTag("visual-daily-timeline").performScrollTo().assertIsDisplayed()
                    }
                    if (section.trend != null) {
                        compose.onNodeWithText(section.trend.title).performScrollTo().assertIsDisplayed()
                        ReleaseScreenshots62.capture(compose.onNodeWithTag("visual-screen-$id"), "visual-$id-trend-$index-$targetWidth")
                    }
                    if (section.rows.isNotEmpty() && id in setOf("health", "time", "keys", "app-permissions", "privacy-dashboard", "spaces")) {
                        compose.onNodeWithTag("visual-row-0").performScrollTo().assertIsDisplayed().performClick()
                        compose.onNodeWithTag("visual-detail").assertIsDisplayed()
                        compose.onNodeWithContentDescription("Back to demo section").performClick()
                    }
                }
                compose.runOnIdle { navigator.openSection(0) }
                compose.onNodeWithTag("visual-sections").performScrollToNode(hasTestTag("visual-section-0"))
                ReleaseScreenshots62.capture(compose.onNodeWithTag("visual-screen-$id"), "visual-$id-$targetWidth")
                when (id) {
                    "notes" -> verifyNotesEditor(targetWidth)
                    "files" -> verifyFilesHierarchy(targetWidth)
                }
                compose.onNodeWithContentDescription("Back to visual catalogue").performClick()
                compose.onNodeWithTag("visual-catalogue-list").assertIsDisplayed()
            }
        }
        compose.runOnIdle { assertTrue(launches.isEmpty()) }
    }

    private fun verifyNotesEditor(width: Int) {
        val screen = compose.onNodeWithTag("visual-screen-notes")
        compose.onNodeWithTag("visual-notes-new").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("visual-detail").assertIsDisplayed()
        compose.onNodeWithTag("visual-note-title").performScrollTo().performTextReplacement("Review note")
        compose.onNodeWithTag("visual-note-body").performScrollTo().performTextInput("A temporary example.")
        compose.onNodeWithText("Add checklist item").performScrollTo().performClick()
        compose.onNodeWithTag("visual-note-check-0").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithContentDescription("Back to demo section").assertIsDisplayed()
        ReleaseScreenshots62.capture(screen, "visual-notes-editor-$width")
        compose.onNodeWithContentDescription("Back to demo section").performClick()
        compose.onNodeWithText("Review note").performScrollTo().assertIsDisplayed()
    }

    private fun verifyFilesHierarchy(width: Int) {
        compose.onNodeWithTag("visual-files-folder-Project Aurora").performScrollTo().performClick()
        compose.onNodeWithTag("visual-files-folder-Maps").performScrollTo().performClick()
        compose.onNodeWithTag("visual-files-preview-Trail-map.png").performScrollTo().performClick()
        compose.onNodeWithTag("visual-detail").assertIsDisplayed()
        ReleaseScreenshots62.capture(compose.onNodeWithTag("visual-screen-files"), "visual-files-preview-$width")
        compose.onNodeWithContentDescription("Back to demo section").performClick()
        compose.onNodeWithTag("visual-files-preview-Trail-map.png").performScrollTo().assertIsDisplayed()
        repeat(2) { compose.onNodeWithContentDescription("Back to demo section").performClick() }
        compose.onNodeWithTag("visual-files-folder-Project Aurora").performScrollTo().assertIsDisplayed()
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
            compose.onNode(hasTestTag("app-store") and hasAnyAncestor(hasTestTag("drawer-sheet"))).assertIsDisplayed().performClick()
            compose.onNodeWithTag("store-visual-foundation").assertIsDisplayed().performClick()
            compose.onNodeWithTag("visual-catalogue-list").assertIsDisplayed()
            compose.onNodeWithContentDescription("Close visual catalogue").performClick()
            compose.onNodeWithTag("nova-store").assertIsDisplayed()

            compose.onNodeWithTag("nova-home").performClick()
            compose.onNodeWithTag("open-apps").performClick()
            compose.onNodeWithTag("drawer-search").performTextInput("Nova Time")
            compose.onNodeWithTag("drawer-visual-time").assertIsDisplayed().performClick()
            compose.onNodeWithTag("visual-screen-time").assertIsDisplayed()
            compose.onNodeWithTag("visual-demo-indicator").assertIsDisplayed()

            compose.runOnIdle { access = hidden }
            compose.onNodeWithTag("open-apps").performClick()
            compose.onNodeWithTag("drawer-sheet").assertIsDisplayed()
            compose.onNodeWithTag("drawer-visual-foundation").assertDoesNotExist()
            compose.onNodeWithTag("drawer-search").performTextInput("Nova Time")
            compose.onNodeWithTag("drawer-visual-time").assertDoesNotExist()
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
