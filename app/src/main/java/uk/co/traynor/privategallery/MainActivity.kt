package uk.co.traynor.privategallery

import android.os.Bundle
import android.os.Build
import android.provider.MediaStore
import android.view.WindowManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uk.co.traynor.privategallery.core.vault.AndroidVaultRepository
import uk.co.traynor.privategallery.core.vault.ImportResult
import uk.co.traynor.privategallery.core.vault.VaultItem
import uk.co.traynor.privategallery.core.ui.VaultGridPolicy
import uk.co.traynor.privategallery.core.ui.VaultSummary
import uk.co.traynor.privategallery.core.crypto.InvalidPinException
import uk.co.traynor.privategallery.core.security.AutoLockTimeout
import uk.co.traynor.privategallery.core.security.AutoLockPreference
import uk.co.traynor.privategallery.core.security.LockSession
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore
import uk.co.traynor.privategallery.core.security.BiometricVaultKeyStore
import uk.co.traynor.privategallery.ui.PrivateGalleryTheme
import uk.co.traynor.privategallery.ui.ProtectedVideoViewer

class MainActivity : FragmentActivity() {
    private lateinit var keys: PinVaultKeyStore
    private lateinit var biometrics: BiometricVaultKeyStore
    private lateinit var appSettings: android.content.SharedPreferences
    private val session = LockSession(AutoLockTimeout.IMMEDIATELY)
    private var route by mutableStateOf(Route.LOCK)
    private var biometricEnabled by mutableStateOf(false)
    private var biometricAvailable by mutableStateOf(false)
    private var autoLockTimeout by mutableStateOf(AutoLockTimeout.IMMEDIATELY)
    private var sessionKey: ByteArray? = null
    private var pendingSourceDeletion: List<VaultItem> = emptyList()
    private var biometricPurpose: BiometricPurpose? = null
    private val biometricPrompt by lazy {
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val cipher = result.cryptoObject?.cipher ?: return
                runCatching {
                    when (biometricPurpose) {
                        BiometricPurpose.UNLOCK -> {
                            sessionKey?.fill(0)
                            sessionKey = biometrics.unwrapAuthenticated(cipher)
                            session.unlock()
                            route = Route.VAULT
                        }
                        BiometricPurpose.ENROLL -> {
                            val key = checkNotNull(sessionKey)
                            biometrics.saveAuthenticated(cipher, key)
                            biometricEnabled = true
                        }
                        null -> Unit
                    }
                }
                biometricPurpose = null
            }
        })
    }
    private val sourceDeletionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val key = sessionKey?.copyOf() ?: return@registerForActivityResult
        val pending = pendingSourceDeletion
        pendingSourceDeletion = emptyList()
        lifecycleScope.launch(Dispatchers.IO) {
            val repository = AndroidVaultRepository(applicationContext, key)
            pending.forEach { repository.finishSourceDeletionRequest(it, result.resultCode == RESULT_OK) }
            key.fill(0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        keys = PinVaultKeyStore(this)
        biometrics = BiometricVaultKeyStore(this)
        biometricAvailable = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        appSettings = getSharedPreferences("private-gallery-settings", MODE_PRIVATE)
        autoLockTimeout = AutoLockPreference.decode(appSettings.getString("auto-lock-timeout", null))
        session.setTimeout(autoLockTimeout)
        biometricEnabled = biometrics.isEnabled
        route = if (keys.isConfigured) Route.LOCK else Route.SETUP
        setContent {
            PrivateGalleryTheme {
                PrivateGalleryApp(route, ::createPin, ::unlock, ::changePin, ::lock, ::importSelected, ::moveSelected, ::loadItems, ::readForViewing, ::restore, ::delete, biometricEnabled, ::unlockWithBiometrics, ::enrollBiometrics, ::finishSetup, autoLockTimeout, ::openSettings, ::applyAutoLockTimeout)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        session.onAppBackgrounded(System.currentTimeMillis())
        if (!session.isUnlocked) lock()
    }

    override fun onStart() {
        super.onStart()
        session.onForegrounded(System.currentTimeMillis())
        if (!session.isUnlocked && keys.isConfigured) route = Route.LOCK
    }

    private fun createPin(pin: CharArray): Result<Unit> = runCatching {
        sessionKey?.fill(0)
        sessionKey = keys.create(pin)
        session.unlock()
        route = if (biometricAvailable) Route.BIOMETRIC_SETUP else Route.VAULT
    }

    private fun unlock(pin: CharArray): Result<Unit> = runCatching {
        sessionKey?.fill(0)
        sessionKey = keys.unlock(pin)
        session.unlock()
        route = Route.VAULT
    }

    private fun changePin(currentPin: CharArray, newPin: CharArray): Result<Unit> = runCatching {
        require(newPin.size >= 6) { "PIN must be at least six digits" }
        keys.changePin(currentPin, newPin)
    }

    private fun lock() {
        session.lock()
        sessionKey?.fill(0)
        sessionKey = null
        if (::keys.isInitialized && keys.isConfigured) route = Route.LOCK
    }

    private fun openSettings() {
        route = Route.SETTINGS
    }

    private fun finishSetup() {
        route = Route.VAULT
    }

    private fun applyAutoLockTimeout(timeout: AutoLockTimeout) {
        autoLockTimeout = timeout
        session.setTimeout(timeout)
        appSettings.edit().putString("auto-lock-timeout", AutoLockPreference.encode(timeout)).apply()
    }

    private fun importSelected(uris: List<android.net.Uri>, onComplete: (String) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val repository = AndroidVaultRepository(applicationContext, key)
                var copied = 0
                var duplicates = 0
                uris.forEach { uri ->
                    when (repository.import(uri)) {
                        is ImportResult.Imported -> copied++
                        is ImportResult.Duplicate -> duplicates++
                    }
                }
                runOnUiThread {
                    onComplete(
                        buildString {
                            append("Saved ").append(copied).append(" item")
                            if (copied != 1) append('s')
                            if (duplicates > 0) append("; ").append(duplicates).append(" duplicate(s) already in Vault")
                        },
                    )
                }
            } catch (_: Throwable) {
                runOnUiThread { onComplete("Unable to copy the selected media. Originals were not changed.") }
            } finally {
                key.fill(0)
            }
        }
    }

    private fun loadItems(onLoaded: (List<VaultItem>) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val items = runCatching { AndroidVaultRepository(applicationContext, key).items() }.getOrDefault(emptyList())
            key.fill(0)
            runOnUiThread { onLoaded(items) }
        }
    }

    private fun moveSelected(uris: List<android.net.Uri>, onComplete: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onComplete("Saved to Vault only. Android deletion confirmation requires Android 11 or later.")
            importSelected(uris, onComplete)
            return
        }
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val repository = AndroidVaultRepository(applicationContext, key)
            try {
                val imported = uris.mapNotNull { uri ->
                    (repository.import(uri) as? ImportResult.Imported)?.item
                }
                imported.forEach(repository::markDeletePending)
                val sources = imported.mapNotNull { it.sourceUri?.let(android.net.Uri::parse) }
                if (sources.isEmpty()) {
                    runOnUiThread { onComplete("Saved to Vault. The selected media was already protected or unavailable.") }
                } else {
                    pendingSourceDeletion = imported
                    val request = MediaStore.createDeleteRequest(contentResolver, sources)
                    runOnUiThread {
                        onComplete("Saved to Vault. Waiting for Gallery deletion confirmation…")
                        sourceDeletionLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    }
                }
            } catch (_: Throwable) {
                runOnUiThread { onComplete("Unable to move media. Originals were not changed.") }
            } finally {
                key.fill(0)
            }
        }
    }

    private fun restore(item: VaultItem, removeAfter: Boolean, onComplete: (String) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val repository = AndroidVaultRepository(applicationContext, key)
                if (removeAfter) repository.restoreAndRemove(item) else repository.restore(item)
                runOnUiThread { onComplete(if (removeAfter) "Restored to Gallery and removed from Vault." else "Restored to Gallery. Vault copy retained.") }
            } catch (_: Throwable) {
                runOnUiThread { onComplete("Restore failed. The Vault copy was retained.") }
            } finally { key.fill(0) }
        }
    }

    private fun readForViewing(item: VaultItem, onComplete: (Result<ByteArray>) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { AndroidVaultRepository(applicationContext, key).readForViewing(item) }
            key.fill(0)
            runOnUiThread { onComplete(result) }
        }
    }

    private fun delete(item: VaultItem, onComplete: (String) -> Unit) {
        val key = sessionKey?.copyOf() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                AndroidVaultRepository(applicationContext, key).deleteFromVault(item)
                runOnUiThread { onComplete("Removed from Vault.") }
            } catch (_: Throwable) {
                runOnUiThread { onComplete("Unable to remove this item from Vault.") }
            } finally { key.fill(0) }
        }
    }

    private fun unlockWithBiometrics() {
        if (!biometrics.isEnabled) return
        biometricPurpose = BiometricPurpose.UNLOCK
        biometricPrompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Private Gallery")
                .setSubtitle("Unlock your Vault")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Use PIN")
                .build(),
            BiometricPrompt.CryptoObject(biometrics.newDecryptCipher()),
        )
    }

    private fun enrollBiometrics() {
        if (sessionKey == null) return
        biometricPurpose = BiometricPurpose.ENROLL
        biometricPrompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Enable biometric unlock")
                .setSubtitle("Use biometrics to unlock Private Gallery")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Cancel")
                .build(),
            BiometricPrompt.CryptoObject(biometrics.newEncryptCipher()),
        )
    }
}

private enum class Route { SETUP, BIOMETRIC_SETUP, LOCK, VAULT, SETTINGS }
private enum class BiometricPurpose { UNLOCK, ENROLL }

@Composable
private fun PrivateGalleryApp(
    route: Route,
    onCreatePin: (CharArray) -> Result<Unit>,
    onUnlock: (CharArray) -> Result<Unit>,
    onChangePin: (CharArray, CharArray) -> Result<Unit>,
    onLock: () -> Unit,
    onImport: (List<android.net.Uri>, (String) -> Unit) -> Unit,
    onMove: (List<android.net.Uri>, (String) -> Unit) -> Unit,
    onLoadItems: ((List<VaultItem>) -> Unit) -> Unit,
    onReadForViewing: (VaultItem, (Result<ByteArray>) -> Unit) -> Unit,
    onRestore: (VaultItem, Boolean, (String) -> Unit) -> Unit,
    onDelete: (VaultItem, (String) -> Unit) -> Unit,
    biometricEnabled: Boolean,
    onBiometricUnlock: () -> Unit,
    onEnrollBiometrics: () -> Unit,
    onFinishSetup: () -> Unit,
    autoLockTimeout: AutoLockTimeout,
    onOpenSettings: () -> Unit,
    onAutoLockTimeoutChanged: (AutoLockTimeout) -> Unit,
) = when (route) {
    Route.SETUP -> PinSetup(onCreatePin)
    Route.BIOMETRIC_SETUP -> BiometricSetup(onEnrollBiometrics, onFinishSetup)
    Route.LOCK -> PinUnlock(onUnlock, biometricEnabled, onBiometricUnlock)
    Route.VAULT -> VaultHome(onLock, onImport, onMove, onLoadItems, onReadForViewing, onRestore, onDelete, biometricEnabled, onEnrollBiometrics, onOpenSettings)
    Route.SETTINGS -> SettingsHome(autoLockTimeout, biometricEnabled, onAutoLockTimeoutChanged, onChangePin, onLock)
}

@Composable
private fun PinSetup(onCreatePin: (CharArray) -> Result<Unit>) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    PinPage(
        title = "Set up Private Gallery",
        detail = "Selected media is encrypted in this device's private storage. If you lose your PIN and biometric access is unavailable, the Vault may be unrecoverable.",
        pin = pin,
        onPinChange = { pin = it },
        confirmation = confirmation,
        onConfirmationChange = { confirmation = it },
        action = "Create PIN",
        message = message,
    ) {
        if (pin.length < 6 || pin != confirmation) {
            message = "Use and confirm a PIN of at least six digits."
        } else {
            onCreatePin(pin.toCharArray()).onSuccess {
                pin = ""
                confirmation = ""
            }.onFailure { message = "Unable to create the vault." }
        }
    }
}

@Composable
private fun BiometricSetup(onEnrollBiometrics: () -> Unit, onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Use biometrics?", style = MaterialTheme.typography.headlineMedium)
        Text("Use your device biometrics to unlock Private Gallery more quickly. Your PIN remains available.")
        Button(onClick = onEnrollBiometrics, modifier = Modifier.fillMaxWidth()) { Text("Enable biometric unlock") }
        androidx.compose.material3.OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Finish") }
    }
}

@Composable
private fun PinUnlock(onUnlock: (CharArray) -> Result<Unit>, biometricEnabled: Boolean, onBiometricUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    PinPage(
        title = "Private Gallery",
        detail = "Unlock to view protected media.",
        pin = pin,
        onPinChange = { pin = it },
        action = "Unlock",
        message = message,
        secondaryAction = if (biometricEnabled) {
            { TextButton(onClick = onBiometricUnlock) { Text("Use biometrics") } }
        } else {
            null
        },
    ) {
        onUnlock(pin.toCharArray()).onSuccess {
            pin = ""
            message = null
        }.onFailure {
            pin = ""
            message = if (it is InvalidPinException) "Incorrect PIN." else "Unable to unlock the vault."
        }
    }
}

@Composable
private fun PinPage(
    title: String,
    detail: String,
    pin: String,
    onPinChange: (String) -> Unit,
    action: String,
    message: String?,
    confirmation: String? = null,
    onConfirmationChange: ((String) -> Unit)? = null,
    secondaryAction: (@Composable () -> Unit)? = null,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(detail)
        OutlinedTextField(
            value = pin,
            onValueChange = { onPinChange(it.filter(Char::isDigit)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
        )
        if (confirmation != null && onConfirmationChange != null) {
            OutlinedTextField(
                value = confirmation,
                onValueChange = { onConfirmationChange(it.filter(Char::isDigit)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Confirm PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
            )
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(action) }
        secondaryAction?.invoke()
    }
}

@Composable
private fun SettingsHome(
    autoLockTimeout: AutoLockTimeout,
    biometricEnabled: Boolean,
    onAutoLockTimeoutChanged: (AutoLockTimeout) -> Unit,
    onChangePin: (CharArray, CharArray) -> Result<Unit>,
    onLock: () -> Unit,
) {
    var changingPin by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        SettingsSection("Security") {
            androidx.compose.material3.OutlinedButton(onClick = { changingPin = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Change PIN")
            }
            Text("Biometric unlock", style = MaterialTheme.typography.titleMedium)
            Text(
                if (biometricEnabled) "Enabled on this device" else "Enable it from the Vault after unlocking.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Auto-lock", style = MaterialTheme.typography.titleMedium)
            Text("Lock protected content after Private Gallery leaves the foreground.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            AutoLockTimeout.entries.forEach { timeout ->
                androidx.compose.material3.OutlinedButton(
                    onClick = { onAutoLockTimeoutChanged(timeout) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (timeout == autoLockTimeout) "✓ " + timeout.label else timeout.label)
                }
            }
        }
        SettingsSection("Privacy") {
            Text("Secure-screen protection", style = MaterialTheme.typography.titleMedium)
            Text("Protected screens are excluded from screenshots and Recents previews where Android supports it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Backup protection", style = MaterialTheme.typography.titleMedium)
            Text("Private Gallery data is excluded from Android backup.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsSection("About") {
            Text("Private Gallery 1.0.0", style = MaterialTheme.typography.titleMedium)
            Text("Media stays in encrypted private app storage until you restore it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = onLock, modifier = Modifier.fillMaxWidth()) { Text("Lock") }
    }
    if (changingPin) ChangePinDialog(onChangePin) { changingPin = false }
}

@Composable
private fun ChangePinDialog(onChangePin: (CharArray, CharArray) -> Result<Unit>, onDismiss: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This updates protection around the Vault key; media files are not re-encrypted.")
                OutlinedTextField(current, { current = it.filter(Char::isDigit) }, label = { Text("Current PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                OutlinedTextField(replacement, { replacement = it.filter(Char::isDigit) }, label = { Text("New PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                OutlinedTextField(confirm, { confirm = it.filter(Char::isDigit) }, label = { Text("Confirm new PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (replacement != confirm || replacement.length < 6) {
                    error = "Use and confirm a PIN of at least six digits."
                } else {
                    onChangePin(current.toCharArray(), replacement.toCharArray()).onSuccess { onDismiss() }
                        .onFailure { error = if (it is InvalidPinException) "Current PIN is incorrect." else "Unable to change PIN." }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

private val AutoLockTimeout.label: String
    get() = when (this) {
        AutoLockTimeout.IMMEDIATELY -> "Immediately"
        AutoLockTimeout.SECONDS_30 -> "After 30 seconds"
        AutoLockTimeout.MINUTE_1 -> "After 1 minute"
        AutoLockTimeout.MINUTES_5 -> "After 5 minutes"
    }

@Composable
private fun VaultHome(
    onLock: () -> Unit,
    onImport: (List<android.net.Uri>, (String) -> Unit) -> Unit,
    onMove: (List<android.net.Uri>, (String) -> Unit) -> Unit,
    onLoadItems: ((List<VaultItem>) -> Unit) -> Unit,
    onReadForViewing: (VaultItem, (Result<ByteArray>) -> Unit) -> Unit,
    onRestore: (VaultItem, Boolean, (String) -> Unit) -> Unit,
    onDelete: (VaultItem, (String) -> Unit) -> Unit,
    biometricEnabled: Boolean,
    onEnrollBiometrics: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var status by remember { mutableStateOf("Select photos or videos to copy into the encrypted Vault.") }
    var vaultItems by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<VaultItem?>(null) }
    var viewingItem by remember { mutableStateOf<VaultItem?>(null) }
    var viewingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var viewerError by remember { mutableStateOf<String?>(null) }
    var deleteConfirmationItem by remember { mutableStateOf<VaultItem?>(null) }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED_MEDIA),
    ) { uris ->
        if (uris.isNotEmpty()) {
            status = "Importing selected media…"
            onImport(uris) {
                status = it
                onLoadItems { updated -> vaultItems = updated; loaded = true }
            }
        }
    }
    val movePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED_MEDIA),
    ) { uris ->
        if (uris.isNotEmpty()) {
            status = "Encrypting and verifying…"
            onMove(uris) { status = it }
        }
    }
    LaunchedEffect(Unit) { onLoadItems { items -> vaultItems = items; loaded = true } }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Private Gallery", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onOpenSettings) { Text("Settings") }
        }
        Text("Vault", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (!biometricEnabled) TextButton(onClick = onEnrollBiometrics) { Text("Enable biometric unlock") }
        if (loaded && vaultItems.isNotEmpty()) {
            val summary = VaultSummary.from(vaultItems)
            Text(
                summary.photos.toString() + " photos · " + summary.videos.toString() + " videos",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(VaultGridPolicy.columnsFor(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp)),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(vaultItems, key = { it.id }) { item ->
                    Card(
                        onClick = { selectedItem = item },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(if (item.mimeType.startsWith("video/")) "VIDEO" else "PHOTO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text("Protected media", style = MaterialTheme.typography.bodyMedium)
                            Text("Encrypted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("PG", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Text("Your vault is empty", style = MaterialTheme.typography.headlineSmall)
                Text("Photos and videos you add here are stored privately and encrypted on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add media") }
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        movePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Move to Vault") }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Text(status, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onLock, modifier = Modifier.fillMaxWidth()) { Text("Lock") }
    }
    selectedItem?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedItem = null },
            title = { Text("Protected media") },
            text = { Text("Choose what to do with this item.") },
            confirmButton = {
                TextButton(onClick = {
                    onReadForViewing(item) { result ->
                        result.onSuccess { bytes ->
                            viewingItem = item
                            viewingBytes = bytes
                            selectedItem = null
                        }.onFailure {
                            selectedItem = null
                            viewerError = "Unable to open this protected item. The Vault copy was not changed."
                        }
                    }
                }) { Text("View") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        selectedItem = null
                        onRestore(item, false) { status = it }
                    }) { Text("Restore") }
                    TextButton(onClick = {
                        selectedItem = null
                        onRestore(item, true) { status = it; onLoadItems { updated -> vaultItems = updated } }
                    }) { Text("Restore and remove") }
                    TextButton(onClick = {
                        selectedItem = null
                        deleteConfirmationItem = item
                    }) { Text("Delete") }
                }
            },
        )
    }
    deleteConfirmationItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteConfirmationItem = null },
            title = { Text("Delete from Vault?") },
            text = { Text("Delete this item permanently? This cannot be undone unless another copy exists elsewhere.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteConfirmationItem = null
                    onDelete(item) { status = it; onLoadItems { updated -> vaultItems = updated } }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmationItem = null }) { Text("Cancel") } },
        )
    }
    viewerError?.let { error ->
        AlertDialog(
            onDismissRequest = { viewerError = null },
            title = { Text("Protected media") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { viewerError = null }) { Text("Close") } },
        )
    }
    viewingItem?.let { item ->
        val bytes = viewingBytes
        if (bytes != null && item.mimeType.startsWith("image/")) {
            ProtectedImageViewer(bytes, onClose = {
                bytes.fill(0)
                viewingBytes = null
                viewingItem = null
            })
        } else if (bytes != null) {
            ProtectedVideoViewer(bytes, onClose = {
                    bytes.fill(0)
                    viewingBytes = null
                    viewingItem = null
            })
        }
    }
}

@Composable
private fun ProtectedImageViewer(bytes: ByteArray, onClose: () -> Unit) {
    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    Dialog(onDismissRequest = onClose) {
        Box(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Protected image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } ?: Text("Unable to render image", color = MaterialTheme.colorScheme.error)
            TextButton(
                onClick = onClose,
                modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd),
            ) { Text("Close") }
        }
    }
}

private const val MAX_PICKED_MEDIA = 50
