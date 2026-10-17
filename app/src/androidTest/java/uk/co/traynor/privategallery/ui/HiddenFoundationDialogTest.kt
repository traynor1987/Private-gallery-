package uk.co.traynor.privategallery.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore

class HiddenFoundationDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val id = UUID.randomUUID().toString()
    private val parent = File(base.noBackupFilesDir, "hidden-ui-test-$id")
    private val preferenceNames = mutableSetOf<String>()
    private val context = object : ContextWrapper(base) {
        override fun getPackageName() = "${base.packageName}.test.$id"
        override fun getNoBackupFilesDir() = parent
        override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
            val qualified = "test-$id-$name"
            preferenceNames.add(qualified)
            return base.getSharedPreferences(qualified, mode)
        }
    }
    @After fun cleanup() {
        preferenceNames.forEach { base.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("${context.packageName}.hidden.v1.default") }
        parent.deleteRecursively()
    }
    @Test fun setupOwnPinUnlocksAndHiddenLockDoesNotReplacePrimaryCredentials() {
        PinVaultKeyStore(context).create("123456".toCharArray()).fill(0)
        val shown = mutableStateOf(true)
        compose.setContent { MaterialTheme { if (shown.value) HiddenFoundationDialog(context) { shown.value = false } } }
        compose.runOnIdle {
            if (android.os.Build.VERSION.SDK_INT >= 29) assertTrue(
                android.view.inspector.WindowInspector.getGlobalWindowViews().any {
                    val flags = (it.layoutParams as? android.view.WindowManager.LayoutParams)?.flags ?: 0
                    flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0
                }
            )
        }
        compose.onNodeWithText("Hidden PIN").performTextInput("728491")
        compose.onNodeWithText("Confirm Hidden PIN").performTextInput("728491")
        compose.onNodeWithText("Create Hidden").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Lock Hidden").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Lock Hidden").performClick()
        compose.onNodeWithText("Hidden PIN").assertIsDisplayed()
        compose.onNodeWithText("Unlock").assertIsDisplayed()
        PinVaultKeyStore(context).unlock("123456".toCharArray()).fill(0)
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Hidden Vault").assertDoesNotExist()
    }
    @Test fun backgroundDismissesUnsubmittedPinWithoutCredentialCreation() {
        val shown = mutableStateOf(true)
        compose.setContent { MaterialTheme { if (shown.value) HiddenFoundationDialog(context) { shown.value = false } } }
        compose.onNodeWithText("Hidden PIN").performTextInput("728491")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse(shown.value)
        assertFalse(File(parent, "hidden-v1-default/credential").exists())
    }
}
