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

    @Test fun workPinningIsOffAndModeOptionsAreSavedIndependently() {
        val persistence = object : SanctuaryPersistence {
            private var state: SanctuarySnapshot? = null
            override fun read() = state
            override fun write(value: SanctuarySnapshot): Boolean {
                state = value
                return true
            }
        }
        val store = SanctuaryStore(persistence) { SanctuaryTime(1_000_000_000L, 120_000L, 1) }
        compose.setContent {
            SanctuaryHome(store, emptyMap(), emptyMap(), "Guardian off", store::start, {}, {}, {}, {})
        }
        compose.onNodeWithTag("sanctuary-mode-work").performScrollTo().performClick()
        compose.onNodeWithTag("sanctuary-edit-preset").performScrollTo().performClick()
        compose.onNodeWithTag("sanctuary-option-pinning").performScrollTo().assertIsOff()
        compose.onNodeWithTag("sanctuary-option-dnd").performScrollTo().assertIsOff().performClick()
        compose.onNodeWithTag("sanctuary-option-guardian").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithTag("sanctuary-option-external-apps").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithTag("sanctuary-save-preset").performScrollTo().performClick()
        compose.runOnIdle {
            val work = store.preset(SanctuaryMode.WORK)
            check(work.enableDnd && !work.offerPinning && !work.enableGuardian && !work.allowExternalApps)
            val gaming = store.preset(SanctuaryMode.GAMING)
            check(!gaming.enableDnd && gaming.enableGuardian && gaming.allowExternalApps)
        }
    }

    @Test fun internalAppDefaultsStayDynamicDuringTheFiveMinuteFoldTest() {
        val persistence = object : SanctuaryPersistence {
            private var state: SanctuarySnapshot? = null
            override fun read() = state
            override fun write(value: SanctuarySnapshot): Boolean {
                state = value
                return true
            }
        }
        val store = SanctuaryStore(persistence) { SanctuaryTime(1_000_000_000L, 120_000L, 1) }
        val apps = mutableStateOf(mapOf(
            "photos" to "Gallery", "web:initial" to "YouTube Music", "web:renamed" to "A renamed app",
            "chat" to "Nova Chat Lite",
        ))
        compose.setContent {
            SanctuaryHome(store, emptyMap(), apps.value, "Guardian off", store::start, {}, {}, {}, {},
                novaAppUrls = mapOf("web:renamed" to "https://web.whatsapp.com/"),
                onStartFoldTest = store::startFoldTest)
        }
        compose.onNodeWithTag("sanctuary-mode-work").performScrollTo().performClick()
        compose.onNodeWithTag("sanctuary-edit-preset").performScrollTo().performClick()
        compose.onNodeWithTag("sanctuary-option-whatsapp").performScrollTo().assertIsOff()
        compose.onNodeWithTag("sanctuary-allow-internal-photos").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithTag("sanctuary-allow-internal-web:initial").performScrollTo().assertIsOn()
        compose.onNodeWithTag("sanctuary-save-preset").performScrollTo().performClick()
        compose.onNodeWithTag("sanctuary-fold-test").performScrollTo().performClick()
        compose.onNodeWithTag("sanctuary-launch-internal-web:initial").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("sanctuary-launch-internal-photos").assertDoesNotExist()
        compose.onNodeWithTag("sanctuary-launch-internal-web:renamed").assertDoesNotExist()
        compose.onNodeWithTag("sanctuary-launch-internal-chat").assertDoesNotExist()
        compose.runOnIdle {
            val session = checkNotNull(store.current.value)
            check(store.remainingMillis(session) == 300_000L)
            check(store.preset(SanctuaryMode.WORK).minutes == SanctuaryMode.WORK.defaultMinutes)
            apps.value = apps.value + ("web:newly-installed" to "New Store app")
        }
        compose.onNodeWithTag("sanctuary-launch-internal-web:newly-installed").performScrollTo().assertIsDisplayed()
    }

    private fun openHomeSettings() {
        compose.onNodeWithTag("open-apps").performClick()
        compose.onNodeWithTag("drawer-search").performTextInput("Home settings")
        compose.onNode(hasTestTag("app-settings") and hasAnyAncestor(hasTestTag("drawer-sheet")))
            .assertIsDisplayed().performClick()
        compose.onNodeWithTag("nova-home-settings").assertIsDisplayed()
    }
}
