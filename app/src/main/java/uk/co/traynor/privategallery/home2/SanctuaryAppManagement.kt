package uk.co.traynor.privategallery.home2

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.sanctuary.PanicLauncherVisibility
import uk.co.traynor.privategallery.core.sanctuary.SANCTUARY_NATIVE_WHATSAPP
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryAppVisibility
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryGuardian
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode
import uk.co.traynor.privategallery.core.sanctuary.SanctuarySession
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore
import uk.co.traynor.privategallery.launcher.LauncherApp
import uk.co.traynor.privategallery.launcher.LauncherAppDirectory

internal fun authenticateSanctuaryAppManagement(
    activity: Activity,
    onFinished: (Boolean) -> Unit,
): CancellationSignal? {
    if (activity.getSystemService(KeyguardManager::class.java)?.isDeviceSecure != true) return null
    val cancellation = CancellationSignal()
    val prompt = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            if (!cancellation.isCanceled && !activity.isDestroyed && !activity.isFinishing) {
                onFinished(result.authenticationType == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL)
            }
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            if (!cancellation.isCanceled && !activity.isDestroyed && !activity.isFinishing) onFinished(false)
        }
    }
    return runCatching {
        BiometricPrompt.Builder(activity)
            .setTitle("Unlock Sanctuary app controls")
            .setSubtitle("Confirm your Android screen lock")
            .setAllowedAuthenticators(DEVICE_CREDENTIAL)
            .build()
            .authenticate(cancellation, activity.mainExecutor, prompt)
        cancellation
    }.getOrNull()
}

@Composable
internal fun SanctuaryAllowedAppInstances(
    store: SanctuaryStore,
    mode: SanctuaryMode,
    session: SanctuarySession?,
    onBack: () -> Unit,
) {
    val application = LocalContext.current.applicationContext
    var apps by remember { mutableStateOf<List<LauncherApp>?>(null) }
    var essential by remember { mutableStateOf(emptySet<String>()) }
    var settings by remember(store) { mutableStateOf(store.launcherSettings()) }
    var failed by remember { mutableStateOf(false) }
    var selectingFriends by remember { mutableStateOf<LauncherApp?>(null) }
    LaunchedEffect(application, store) {
        val catalogue = withContext(Dispatchers.IO) {
            runCatching {
                val entries = LauncherAppDirectory(application).apps()
                SanctuaryLauncherPolicy.appDecisions(application, entries)
                entries to SanctuaryGuardian.essentialPackages(application)
            }.getOrDefault(emptyList<LauncherApp>() to emptySet())
        }
        settings = store.launcherSettings()
        apps = catalogue.first
        essential = catalogue.second
    }
    val installed = apps.orEmpty().filterNot { it.profileSerial == null && it.packageName in essential }
    val ordered = installed.sortedBy { it.packageName != SANCTUARY_NATIVE_WHATSAPP }
    val preset = store.preset(mode)
    val legacy = session?.androidApps ?: preset.androidApps
    val externalAppsAllowed = session?.allowExternalApps ?: preset.allowExternalApps
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Allowed Apps", style = MaterialTheme.typography.headlineSmall)
        }
        LazyColumn(Modifier.fillMaxSize().testTag("sanctuary-allowed-app-instances"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Text(
                    if (mode == SanctuaryMode.PANIC) {
                        "Panic Sanctuary uses its own approved app list and the selected Friends WhatsApp profile."
                    } else {
                        "These choices apply to exact app instances in Nova Launcher. Main WhatsApp stays blocked. " +
                            "Android’s screen lock and emergency controls remain available."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!externalAppsAllowed) {
                item { Text("This mode turns off external apps. Your rules still apply in modes that allow them.") }
            }
            if (apps == null) item { Text("Finding available app instances…") }
            if (apps != null && ordered.isEmpty()) item { Text("No additional app instances are available.") }
            if (failed) item { Text("Could not save the change.", color = MaterialTheme.colorScheme.error) }
            items(ordered, key = LauncherApp::component) { app ->
                val configured = settings.allows(app.component, app.packageName, app.profileSerial, legacy, mode)
                val allowed = externalAppsAllowed && configured
                val fixed = mode == SanctuaryMode.PANIC ||
                    app.packageName == SANCTUARY_NATIVE_WHATSAPP && app.profileSerial == null
                val label = when {
                    app.packageName != SANCTUARY_NATIVE_WHATSAPP -> app.label
                    app.profileSerial == null -> "WhatsApp — Main"
                    app.component == settings.friendsWhatsApp -> "WhatsApp — Friends ❤️ (Dual Messenger)"
                    else -> "WhatsApp"
                }
                val details = listOfNotNull(app.profileLabel, if (allowed) "Allowed" else "Blocked").joinToString(" · ")
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .toggleable(configured, enabled = !fixed, role = Role.Checkbox) { approved ->
                        failed = !store.saveLauncherAppRule(app.component, approved)
                        if (!failed) {
                            settings = store.launcherSettings()
                            SanctuaryLauncherPolicy.signalPolicyChange()
                        }
                    },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(configured, onCheckedChange = null, enabled = !fixed)
                    Column(Modifier.weight(1f)) {
                        Text(label)
                        Text(details, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (app.packageName == SANCTUARY_NATIVE_WHATSAPP && app.profileSerial != null &&
                    app.component != settings.friendsWhatsApp
                ) {
                    TextButton(onClick = { selectingFriends = app }) { Text("Use as Friends WhatsApp") }
                }
                HorizontalDivider()
            }
        }
    }
    selectingFriends?.let { app ->
        AlertDialog(
            onDismissRequest = { selectingFriends = null },
            title = { Text("Use this profile for Friends WhatsApp?") },
            text = { Text("Choose this only if you recognize your Friends account in this Android profile. The default rule will follow this exact app instance.") },
            confirmButton = {
                TextButton(onClick = {
                    selectingFriends = null
                    failed = !store.selectFriendsWhatsApp(app.component)
                    if (!failed) {
                        settings = store.launcherSettings()
                        SanctuaryLauncherPolicy.signalPolicyChange()
                    }
                }) { Text("Use this profile") }
            },
            dismissButton = { TextButton(onClick = { selectingFriends = null }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun SanctuaryAppVisibilitySettings(store: SanctuaryStore, onBack: () -> Unit) {
    var settings by remember(store) { mutableStateOf(store.launcherSettings().visibility) }
    var panicVisibility by remember(store) { mutableStateOf(store.launcherSettings().panicVisibility) }
    var failed by remember { mutableStateOf(false) }
    fun save(next: SanctuaryAppVisibility) {
        failed = !store.saveLauncherVisibility(next)
        if (!failed) {
            settings = next
            SanctuaryLauncherPolicy.signalPolicyChange()
        }
    }
    SanctuaryScaffold("App Visibility", "While Sanctuary protection is active", onBack, "sanctuary-app-visibility-settings") {
        Text("Panic Sanctuary", style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup()) {
            PanicLauncherVisibility.entries.forEach { option ->
                val selected = option == panicVisibility
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(selected, role = Role.RadioButton) {
                        failed = !store.savePanicLauncherVisibility(option)
                        if (!failed) {
                            panicVisibility = option
                            SanctuaryLauncherPolicy.signalPolicyChange()
                        }
                    }.testTag("sanctuary-panic-visibility-${option.name}"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected, onClick = null)
                    Column(Modifier.weight(1f)) {
                        Text(option.label)
                        Text(option.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Text("Other Sanctuary modes", style = MaterialTheme.typography.titleSmall)
        SanctuaryModeSwitch("Hide Blocked Apps", "Hide blocked shortcuts from Home, dock, Drawer, App Library and folders.",
            settings.hideBlockedApps, "sanctuary-hide-blocked-apps") { save(settings.copy(hideBlockedApps = it)) }
        SanctuaryModeSwitch("Hide Blocked Apps from Search", "Keep blocked apps out of search and the alphabetical list.",
            settings.hideFromSearch, "sanctuary-hide-blocked-search") { save(settings.copy(hideFromSearch = it)) }
        SanctuaryModeSwitch("Hide Blocked Apps from Suggestions", "Keep blocked apps out of recommendations.",
            settings.hideFromSuggestions, "sanctuary-hide-blocked-suggestions") { save(settings.copy(hideFromSuggestions = it)) }
        SanctuaryModeSwitch("Hide Blocked Apps from Recently Used", "Hide blocked apps without changing launch history.",
            settings.hideFromRecentlyUsed, "sanctuary-hide-blocked-recents") { save(settings.copy(hideFromRecentlyUsed = it)) }
        Text("Restore Layout When Sanctuary Ends", style = MaterialTheme.typography.titleSmall)
        Text("Always on. Hidden shortcuts return to their original positions.", style = MaterialTheme.typography.bodySmall)
        if (failed) Text("Could not save the change.", color = MaterialTheme.colorScheme.error)
    }
}
