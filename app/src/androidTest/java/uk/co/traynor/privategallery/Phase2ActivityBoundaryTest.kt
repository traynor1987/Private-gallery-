package uk.co.traynor.privategallery

import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.domain.*
import uk.co.traynor.privategallery.core.security.AutoLockTimeout
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore
import uk.co.traynor.privategallery.core.security.RecoveryVaultKeyStore
import uk.co.traynor.privategallery.core.security.SessionEpoch
import java.io.File

/** Full Activity/navigation/lifecycle wiring. Expendable instrumentation-app domain only. */
class Phase2ActivityBoundaryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val primaryPin = "111111111111"
    private val secondaryPin = "222222222222"

    private fun controller(activity: MainActivity = compose.activity): SecondaryController? =
        MainActivity::class.java.getDeclaredField("secondaryController").apply { isAccessible = true }
            .get(activity) as SecondaryController?

    private fun secure(activity: MainActivity = compose.activity) =
        activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    private fun retained(activity: MainActivity = compose.activity) =
        ViewModelProvider(activity)[ProtectedSessionState::class.java]

    private fun primaryEpoch(): SessionEpoch? = MainActivity::class.java
        .getDeclaredMethod("getPrimaryEpoch").apply { isAccessible = true }.invoke(compose.activity) as SessionEpoch?

    private fun lockPrimary() {
        val authority = compose.runOnIdle {
            MainActivity::class.java.getDeclaredMethod("lock").apply { isAccessible = true }.invoke(compose.activity)
            retained().authority
        }
        compose.waitUntil(10_000) { authority.cleanupComplete }
    }

    private fun assertPrimaryAuthenticated() = compose.runOnIdle {
        val state = retained()
        assertTrue(state.session.isUnlocked)
        checkNotNull(state.authority.bindingOrNull()).use { owner ->
            assertTrue(owner.isCurrent)
            assertEquals("Activity epoch must refresh its remembered UI callback owner", owner.epoch, primaryEpoch())
        }
    }

    private fun primarySettings() {
        lockPrimary()
        // Exercise PinUnlock -> MainActivity.unlock -> installAuthenticatedKey, including
        // primaryEpoch and the remember(primaryEpoch) owner used by Settings/About callbacks.
        compose.onNodeWithText("PIN").performTextInput(primaryPin)
        compose.onNodeWithText("Unlock").performClick()
        assertPrimaryAuthenticated()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Updates & About").assertExists()
        compose.runOnIdle {
            MainActivity::class.java.getDeclaredMethod("applyAllowScreenshots", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(compose.activity, true)
            MainActivity::class.java.getDeclaredMethod("applyAutoLockTimeout", AutoLockTimeout::class.java)
                .apply { isAccessible = true }.invoke(compose.activity, AutoLockTimeout.IMMEDIATELY)
            assertEquals("true", compose.activity.getSharedPreferences("private-gallery-settings", Context.MODE_PRIVATE)
                .getString("allow-screenshots", null))
        }
    }

    private fun assertNoPrivateContent() {
        compose.onNodeWithText("Private space").assertDoesNotExist()
        compose.onNodeWithText("Hidden settings").assertDoesNotExist()
        compose.onNodeWithText("Your independent encrypted container is ready.").assertDoesNotExist()
        compose.onNodeWithText("Recovery key confirmed").assertDoesNotExist()
        compose.onNodeWithText("Replace recovery key").assertDoesNotExist()
    }

    private fun discover() {
        compose.onNodeWithText("Updates & About").performScrollTo().performClick()
        repeat(5) { compose.onNodeWithText("Installed").performScrollTo().performClick() }
        compose.onNodeWithText("Private Gallery ${BuildConfig.VERSION_NAME}").performScrollTo().performClick()
        repeat(4) { compose.onNodeWithText("Installed").performScrollTo().performClick() }
        compose.waitUntil(20_000) { controller()?.state?.value?.route == SecondaryRoute.PIN }
        compose.onNodeWithText("Authenticate").assertExists()
        assertNoPrivateContent()
        compose.runOnIdle {
            assertTrue("discovery must override the Primary screenshot preference", secure())
            assertFalse(checkNotNull(controller()).state.value.biometricAvailable)
        }
    }

    private fun unlockSecondary() {
        compose.onNodeWithText("PIN").performTextInput(secondaryPin)
        compose.onNodeWithText("Unlock").performClick()
        compose.waitUntil(20_000) { controller()?.state?.value?.route == SecondaryRoute.READY }
        compose.onNodeWithText("Private space").assertExists()
        compose.runOnIdle { assertTrue(secure()) }
    }

    @Test fun configuredDomainDoesNotDiscloseBeforeDiscoveryAndExitBackgroundRecreationAreProtected() {
        val context = compose.activity.applicationContext
        val root = File(context.filesDir, "domain-store")
        val primaryRoot = File(context.filesDir, "vault")
        val primary = PinVaultKeyStore(context)
        check(!root.exists() && !primaryRoot.exists() && !primary.isConfigured) {
            "Instrumentation requires fresh disposable Primary and Secondary app domains"
        }
        val settings = context.getSharedPreferences("private-gallery-settings", Context.MODE_PRIVATE)
        val originalScreenshots = settings.getString("allow-screenshots", null)
        val originalTimeout = settings.getString("auto-lock-timeout", null)
        var pending: PendingSetup? = null
        var secret: ByteArray? = null
        try {
            val store = SecondaryStore(context.filesDir)
            val setup = store.create(secondaryPin.toCharArray(), {}, { it() })
            pending = setup
            secret = setup.recoverySecret
            store.confirm(setup, checkNotNull(secret), {}, { it() }).close()

            // Confirm a real synthetic Primary recovery slot, so production unlock proceeds
            // to the normal navigation shell rather than the first-run recovery display.
            val primaryKey = primary.create(primaryPin.toCharArray())
            try {
                val recovery = RecoveryVaultKeyStore(context)
                val primarySecret = recovery.create(primaryKey)
                try { recovery.confirm(primarySecret, primaryKey) } finally { primarySecret.fill('\u0000') }
            } finally { primaryKey.fill(0) }

            primarySettings()
            compose.runOnIdle { assertNull(controller()); assertFalse(secure()) }
            assertNoPrivateContent()
            compose.onNodeWithText("Security & privacy").performScrollTo().performClick()
            compose.onNodeWithText("Use recovery key").assertDoesNotExist()
            assertNoPrivateContent()
            compose.onNodeWithContentDescription("Back to Settings").performClick()
            compose.onNodeWithText("Updates & About").performScrollTo().performClick()
            repeat(3) { compose.onNodeWithText("Installed").performScrollTo().performClick() }
            compose.onNodeWithText("Private Gallery ${BuildConfig.VERSION_NAME}").performScrollTo().performClick()
            repeat(4) { compose.onNodeWithText("Installed").performScrollTo().performClick() }
            compose.runOnIdle { assertNull(controller()); assertFalse(secure()) }
            assertNoPrivateContent()
            compose.onNodeWithContentDescription("Back to Settings").performClick()

            discover()
            // Existing Primary credentials must never authorize the independent domain.
            compose.onNodeWithText("PIN").performTextInput(primaryPin)
            compose.onNodeWithText("Unlock").performClick()
            compose.waitUntil(20_000) {
                controller()?.state?.value?.let { !it.busy && it.error == SecondaryUiError.AUTHENTICATION } == true
            }
            compose.onNodeWithText("Authentication was not accepted.").assertExists()
            assertNoPrivateContent()
            compose.runOnIdle { assertEquals(SecondaryRoute.PIN, controller()?.state?.value?.route); assertTrue(secure()) }
            unlockSecondary()
            compose.onNodeWithText("Lock and return").performScrollTo().performClick()
            compose.waitUntil(10_000) { controller()?.state?.value?.route == SecondaryRoute.CLOSED }
            assertNoPrivateContent()
            assertPrimaryAuthenticated()
            compose.runOnIdle { assertTrue("exit must retain protection for previously drawn pixels", secure()) }

            primarySettings()
            discover()
            unlockSecondary()
            // Background an authenticated shell, not an already-exited controller.
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.activityRule.scenario.onActivity { activity ->
                assertTrue("backgrounding must preserve window protection", secure(activity))
                assertEquals(SecondaryRoute.CLOSED, controller(activity)?.state?.value?.route)
                assertFalse(retained(activity).session.isUnlocked)
                assertNull(retained(activity).authority.bindingOrNull())
            }
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            assertNoPrivateContent()
            compose.runOnIdle { assertTrue(secure()); assertEquals(SecondaryRoute.CLOSED, controller()?.state?.value?.route) }

            primarySettings()
            discover()
            unlockSecondary()
            val oldController = compose.runOnIdle { checkNotNull(controller()) }
            compose.activityRule.scenario.recreate()
            compose.runOnIdle {
                assertEquals("destroyed Activity must revoke its independent controller", SecondaryRoute.CLOSED, oldController.state.value.route)
                assertNull("recreation must not restore Secondary authority or its controller", controller())
            }
            assertNoPrivateContent()

            primarySettings()
            discover()
            unlockSecondary()
            lockPrimary()
            compose.waitUntil(10_000) { controller()?.state?.value?.route == SecondaryRoute.CLOSED }
            assertNoPrivateContent()
            compose.runOnIdle {
                assertFalse(retained().session.isUnlocked)
                assertNull(retained().authority.bindingOrNull())
                assertNull(primaryEpoch())
                assertTrue("Primary lock must not expose previously protected pixels", secure())
            }
        } finally {
            secret?.fill(0)
            pending?.close()
            lockPrimary()
            // Freshness was checked before any mutation; only this test's synthetic slots/root.
            context.getSharedPreferences("vault-key-envelope", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("vault-recovery-envelope", Context.MODE_PRIVATE).edit().clear().commit()
            primaryRoot.deleteRecursively()
            root.deleteRecursively()
            settings.edit().apply {
                if (originalScreenshots == null) remove("allow-screenshots") else putString("allow-screenshots", originalScreenshots)
                if (originalTimeout == null) remove("auto-lock-timeout") else putString("auto-lock-timeout", originalTimeout)
            }.commit()
        }
    }
}
