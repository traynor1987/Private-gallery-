package uk.co.traynor.privategallery.home2

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryPersistence
import uk.co.traynor.privategallery.core.sanctuary.SanctuarySnapshot
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryTime
import java.io.File

class SanctuaryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun opensFromPrimaryAndSettingsDoNotExposeGuardianInHidden() {
        val base = compose.activity
        val directory = File(base.cacheDir, "sanctuary-home-fixture").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(directory, "protected").apply { mkdirs() }
        }
        val identity = Any()
        val primary = EvidenceAccess.primary(context, identity, { identity }, { true }, { ByteArray(32) { 32 } }, Any())
        val hiddenSession = HiddenSession(android.os.SystemClock::elapsedRealtime)
        check(hiddenSession.complete(hiddenSession.beginAuthentication(), ByteArray(32) { 38 }))
        val hidden = EvidenceAccess.hidden(context, hiddenSession) { true }
        val access = mutableStateOf(primary)
        val attached = mutableStateOf(true)
        val store = SanctuaryStore.get(base.applicationContext)
        try {
            compose.setContent {
                if (attached.value) key(access.value) {
                    CompositionLocalProvider(LocalContext provides context) {
                        IntegratedHome(access.value, true, {}, {}, {}, null, null)
                    }
                }
            }
            compose.onNodeWithTag("open-apps").performClick()
            compose.onNodeWithTag("drawer-search").performTextInput("Nova Sanctuary")
            compose.onNode(hasTestTag("app-sanctuary") and hasAnyAncestor(hasTestTag("drawer-sheet")))
                .assertIsDisplayed().performClick()
            compose.onNodeWithText("Nova Sanctuary").assertIsDisplayed()
            compose.onNodeWithTag("nova-home").performClick()
            openHomeSettings()
            compose.onNodeWithText("Sanctuary").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText("Sanctuary settings", ignoreCase = true).assertIsDisplayed()

            compose.runOnIdle { access.value = hidden }
            openHomeSettings()
            compose.onNodeWithText("Sanctuary").assertDoesNotExist()
        } finally {
            compose.runOnIdle { attached.value = false }
            compose.waitForIdle()
            store.end(SanctuaryStore.END_PHRASE)
            primary.close()
            hidden.close()
            hiddenSession.lock()
            directory.deleteRecursively()
        }
    }

    @Test fun startsPanicSessionAndRequiresTypedConfirmationToEnd() {
        val persistence = object : SanctuaryPersistence {
            private var state: SanctuarySnapshot? = null
            override fun read() = state
            override fun write(value: SanctuarySnapshot): Boolean {
                state = value
                return true
            }
        }
        val store = SanctuaryStore(persistence) { SanctuaryTime(1_000_000_000L, 120_000L, 1) }
        val settings = mutableStateOf(false)
        compose.setContent {
            if (settings.value) SanctuarySettings(store, { settings.value = false }, {})
            else SanctuaryHome(store, emptyMap(), emptyMap(), "Android protection is inactive",
                store::start, {}, {}, {}, {})
        }
        compose.onNodeWithTag("sanctuary-mode-panic").performScrollTo().performClick()
        compose.onNodeWithTag("sanctuary-start").performScrollTo().performClick()
        compose.onNodeWithTag("sanctuary-countdown").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            check(store.current.value?.mode == SanctuaryMode.PANIC)
            settings.value = true
        }
        compose.onNodeWithTag("sanctuary-end-review").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("sanctuary-end-phrase").performScrollTo().performTextInput(SanctuaryStore.END_PHRASE)
        compose.onNodeWithTag("sanctuary-end-review").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("sanctuary-confirm-end").performClick()
        compose.runOnIdle { check(store.current.value == null) }
    }

    private fun openHomeSettings() {
        compose.onNodeWithTag("open-apps").performClick()
        compose.onNodeWithTag("drawer-search").performTextInput("Home settings")
        compose.onNode(hasTestTag("app-settings") and hasAnyAncestor(hasTestTag("drawer-sheet")))
            .assertIsDisplayed().performClick()
        compose.onNodeWithTag("nova-home-settings").assertIsDisplayed()
    }
}
