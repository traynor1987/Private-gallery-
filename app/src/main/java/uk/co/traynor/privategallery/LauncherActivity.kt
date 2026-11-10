package uk.co.traynor.privategallery

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryDnd
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore
import uk.co.traynor.privategallery.launcher.LauncherDefaultHomeDialog
import uk.co.traynor.privategallery.launcher.NovaLauncherStore
import uk.co.traynor.privategallery.launcher.readDefaultHome
import uk.co.traynor.privategallery.launcher.requestDefaultHomeRole
import uk.co.traynor.privategallery.ui.PrivateGalleryTheme

class LauncherActivity : ComponentActivity() {
    private val launcherStore by lazy { NovaLauncherStore(applicationContext) }
    private var showReminder by mutableStateOf(false)
    private var topResumed = false
    private var selection: HomeSelection? = null
    private val selectHome = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        openWorkspace()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Reconcile expired Sanctuary DND before continuing to the workspace.
        SanctuaryDnd.reconcile(applicationContext)
        selection = savedInstanceState?.getString(STATE_SELECTION)?.let(HomeSelection::valueOf)
        if (selection == null && !shouldOfferReminder()) {
            openWorkspace()
            return
        }

        setContent {
            PrivateGalleryTheme {
                if (showReminder) LauncherDefaultHomeDialog(
                    onDismiss = ::openWorkspace,
                    onSetDefault = ::chooseHome,
                    onOptOut = {
                        if (!launcherStore.setAskForDefaultHome(false)) {
                            message("The default launcher reminder setting could not be saved.")
                        }
                        openWorkspace()
                    },
                )
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                showReminderWhenTopResumed()
                combine(SanctuaryStore.get(applicationContext).current, SanctuaryLauncherPolicy.state()) { _, _ ->
                    sanctuaryProtectionActive()
                }.collect { protected ->
                    if (protected && selection == null) openWorkspace()
                }
            }
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        // Home Settings opens in Android's own task. Continue when the user returns.
        if (selection == HomeSelection.SETTINGS) openWorkspace()
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        topResumed = isTopResumedActivity
        if (isTopResumedActivity) showReminderWhenTopResumed()
        else showReminder = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SELECTION, selection?.name)
    }

    private fun showReminderWhenTopResumed() {
        // The modal owns its own window. Top-resumed state identifies the foreground
        // activity independently of focus transfers between the host and the modal.
        if (isFinishing || selection != null || !topResumed ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) return
        if (shouldOfferReminder()) showReminder = true
        else openWorkspace()
    }

    private fun shouldOfferReminder() =
        launcherStore.askForDefaultHome() &&
            getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == false &&
            !sanctuaryProtectionActive() && readDefaultHome(this) == false

    private fun sanctuaryProtectionActive() =
        runCatching { SanctuaryStore.get(applicationContext).refresh() != null }.getOrDefault(true)

    private fun chooseHome() {
        if (isFinishing || selection != null || !topResumed) return
        if (!shouldOfferReminder() || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            openWorkspace()
            return
        }
        showReminder = false
        selection = HomeSelection.ROLE
        if (requestDefaultHomeRole(this) { selectHome.launch(it) }) return
        selection = HomeSelection.SETTINGS
        if (SanctuaryLauncherPolicy.openHomeSettings(this)) return
        message("Android Home settings could not be opened.")
        openWorkspace()
    }

    private fun openWorkspace() {
        if (isFinishing || isDestroyed) return
        showReminder = false
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun message(value: String) = Toast.makeText(this, value, Toast.LENGTH_SHORT).show()

    private enum class HomeSelection { ROLE, SETTINGS }

    private companion object {
        const val STATE_SELECTION = "default-home-selection"
    }
}
