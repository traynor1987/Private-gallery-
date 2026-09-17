package uk.co.traynor.privategallery

import android.os.Bundle
import android.os.Build
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
import uk.co.traynor.privategallery.core.security.LockSession
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore
import uk.co.traynor.privategallery.core.security.BiometricVaultKeyStore
import uk.co.traynor.privategallery.ui.PrivateGalleryTheme

class MainActivity : FragmentActivity() {
    private lateinit var keys: PinVaultKeyStore
    private lateinit var biometrics: BiometricVaultKeyStore
    private val session = LockSession(AutoLockTimeout.IMMEDIATELY)
    private var route by mutableStateOf(Route.LOCK)
    private var biometricEnabled by mutableStateOf(false)
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
        biometricEnabled = biometrics.isEnabled
        route = if (keys.isConfigured) Route.LOCK else Route.SETUP
        setContent {
            PrivateGalleryTheme {
                PrivateGalleryApp(route, ::createPin, ::unlock, ::lock, ::importSelected, ::moveSelected, ::loadItems, ::restore, ::delete, biometricEnabled, ::unlockWithBiometrics, ::enrollBiometrics)
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
        route = Route.VAULT
    }

    private fun unlock(pin: CharArray): Result<Unit> = runCatching {
        sessionKey?.fill(0)
        sessionKey = keys.unlock(pin)
        session.unlock()
        route = Route.VAULT
    }

    private fun lock() {
        session.lock()
        sessionKey?.fill(0)
        sessionKey = null
        if (::keys.isInitialized && keys.isConfigured) route = Route.LOCK
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
                .setNegativeButtonText("Cancel")
                .build(),
            BiometricPrompt.CryptoObject(biometrics.newEncryptCipher()),
        )
    }
}

private enum class Route { SETUP, LOCK, VAULT }
private enum class BiometricPurpose { UNLOCK, ENROLL }

@Composable
private fun PrivateGalleryApp(
    route: Route,
    onCreatePin: (CharArray) -> Result<Unit>,
    onUnlock: (CharArray) -> Result<Unit>,
    onLock: () -> Unit,
    onImport: (List<android.net.Uri>, (String) -> Unit) -> Unit,
    onMove: (List<android.net.Uri>, (String) -> Unit) -> Unit,
    onLoadItems: ((List<VaultItem>) -> Unit) -> Unit,
    onRestore: (VaultItem, Boolean, (String) -> Unit) -> Unit,
    onDelete: (VaultItem, (String) -> Unit) -> Unit,
    biometricEnabled: Boolean,
    onBiometricUnlock: () -> Unit,
    onEnrollBiometrics: () -> Unit,
) = when (route) {
    Route.SETUP -> PinSetup(onCreatePin)
    Route.LOCK -> PinUnlock(onUnlock, biometricEnabled, onBiometricUnlock)
    Route.VAULT -> VaultHome(onLock, onImport, onMove, onLoadItems, onRestore, onDelete, biometricEnabled, onEnrollBiometrics)
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
private fun VaultHome(
    onLock: () -> Unit,
    onImport: (List<android.net.Uri>, (String) -> Unit) -> Unit,
    onMove: (List<android.net.Uri>, (String) -> Unit) -> Unit,
    onLoadItems: ((List<VaultItem>) -> Unit) -> Unit,
    onRestore: (VaultItem, Boolean, (String) -> Unit) -> Unit,
    onDelete: (VaultItem, (String) -> Unit) -> Unit,
    biometricEnabled: Boolean,
    onEnrollBiometrics: () -> Unit,
) {
    var status by remember { mutableStateOf("Select photos or videos to copy into the encrypted Vault.") }
    var vaultItems by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<VaultItem?>(null) }
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
        Text("Private Gallery", style = MaterialTheme.typography.headlineMedium)
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
                    selectedItem = null
                    onRestore(item, false) { status = it }
                }) { Text("Restore") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        selectedItem = null
                        onRestore(item, true) { status = it; onLoadItems { updated -> vaultItems = updated } }
                    }) { Text("Restore and remove") }
                    TextButton(onClick = {
                        selectedItem = null
                        onDelete(item) { status = it; onLoadItems { updated -> vaultItems = updated } }
                    }) { Text("Delete") }
                }
            },
        )
    }
}

private const val MAX_PICKED_MEDIA = 50
