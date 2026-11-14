package uk.co.traynor.privategallery.home2

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.HexFormat

private const val NOVA_PACKAGE = "uk.co.traynor.privategallery"
private const val NOVA_CERTIFICATE = "94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17"
private const val MAX_APK_BYTES = 256L * 1024 * 1024

private data class FirstPartyApp(val id: String, val name: String, val description: String)

private val firstPartyApps = listOf(
    FirstPartyApp("novaos", "NovaOS", "Private workspace, Guardian and Sanctuary."),
    FirstPartyApp("shift-tracker", "Shift Tracker", "Shift, delivery and fuel records."),
    FirstPartyApp("gig-tracker", "Gig Tracker", "Track your gig work in one place."),
)

private fun releases(context: Context): Map<String, NovaStoreRelease> {
    val catalogue = context.assets.open("nova-first-party-releases.json").bufferedReader().use { JSONObject(it.readText()) }
    return firstPartyApps.mapNotNull { app ->
        val row = catalogue.optJSONObject(app.id) ?: return@mapNotNull null
        val entry = NovaStoreRelease(
            packageName = row.getString("package"),
            versionCode = row.getLong("versionCode"),
            versionName = row.get("versionName") as String,
            certificate = row.getString("certificateSha256"),
            sha256 = row.getString("apkSha256"),
            url = row.getString("url"),
        )
        if (app.id == "novaos") require(entry.packageName == NOVA_PACKAGE && entry.certificate == NOVA_CERTIFICATE)
        app.id to entry
    }.toMap()
}

private fun PackageManager.installed(packageName: String): PackageInfo? = try {
    getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
} catch (_: PackageManager.NameNotFoundException) {
    null
}

private fun PackageInfo.signers(): List<String> = signingInfo?.apkContentsSigners.orEmpty().map {
    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray()))
}

private fun downloadVerifiedNovaApk(context: Context, release: NovaStoreRelease): File {
    val directory = File(context.cacheDir, "updates").apply { mkdirs() }
    directory.listFiles()?.filter {
        it.name.startsWith("nova-store-") && System.currentTimeMillis() - it.lastModified() > 86_400_000
    }?.forEach { it.delete() }
    val output = File.createTempFile("nova-store-", ".apk", directory)
    try {
        var target = URI(release.url)
        val digest = MessageDigest.getInstance("SHA-256")
        var finished = false
        repeat(4) {
            if (finished) return@repeat
            check(trustedNovaReleaseUrl(target.toString(), initial = false))
            val connection = target.toURL().openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                when (connection.responseCode) {
                    301, 302, 303, 307, 308 -> target = target.resolve(checkNotNull(connection.getHeaderField("Location")))
                    200 -> {
                        check(connection.contentLengthLong <= MAX_APK_BYTES)
                        var bytes = 0L
                        connection.inputStream.use { input ->
                            output.outputStream().use { destination ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    bytes += count
                                    check(bytes <= MAX_APK_BYTES)
                                    digest.update(buffer, 0, count)
                                    destination.write(buffer, 0, count)
                                }
                            }
                        }
                        finished = true
                    }
                    else -> error("Release download failed")
                }
            } finally {
                connection.disconnect()
            }
        }
        check(finished && HexFormat.of().formatHex(digest.digest()) == release.sha256)
        val manager = context.packageManager
        val candidate = checkNotNull(manager.getPackageArchiveInfo(output.path,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())))
        val installed = manager.installed(release.packageName)
        check(installed == null || installed.signers() == listOf(release.certificate))
        check(release.accepts(
            candidate.packageName, candidate.longVersionCode, candidate.versionName,
            candidate.signers(), installed?.longVersionCode,
        ))
        return output
    } catch (error: Exception) {
        output.delete()
        throw error
    }
}

@Composable
internal fun NovaFirstPartyStore(onBack: () -> Unit, onOpenWorkspace: (() -> Unit)?) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var catalogue by remember { mutableStateOf<Map<String, NovaStoreRelease>?>(null) }
    var installed by remember { mutableStateOf<Map<String, PackageInfo>>(emptyMap()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<FirstPartyApp?>(null) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(appContext, refresh) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val available = releases(appContext)
                val packages = available.values.map { it.packageName } + listOf(NOVA_PACKAGE, appContext.packageName)
                available to packages.distinct().mapNotNull { name ->
                    appContext.packageManager.installed(name)?.let { name to it }
                }.toMap()
            }
        }
        result.onSuccess { (available, packages) -> catalogue = available; installed = packages }
            .onFailure { catalogue = emptyMap(); installed = emptyMap(); status = "The release catalogue could not be verified." }
    }
    fun storeAllowed() = runCatching {
        SanctuaryStore.get(appContext).refresh()?.allowsNovaApp("store") != false
    }.getOrDefault(false)
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("nova-first-party-store")) {
            PreviewHeader("Nova Store", "First-party Android apps", onBack)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (catalogue == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                status?.let { message -> item { Text(message) } }
                items(firstPartyApps, key = { it.id }) { app ->
                    val release = catalogue?.get(app.id)
                    val local = if (app.id == "novaos") installed[NOVA_PACKAGE] ?: installed[appContext.packageName]
                        else release?.let { installed[it.packageName] }
                    ElevatedCard(onClick = { selected = app }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(app.name, style = MaterialTheme.typography.titleMedium)
                                Text(app.description, style = MaterialTheme.typography.bodyMedium)
                                Text(when {
                                    local != null -> "Installed · ${local.versionName ?: local.longVersionCode}"
                                    release != null -> "Approved release · ${release.versionName}"
                                    else -> "Awaiting an approved release"
                                }, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
    selected?.let { app ->
        val release = catalogue?.get(app.id)
        val local = release?.let { installed[it.packageName] }
        val installedIdentityValid = local == null || local.signers() == listOf(release?.certificate)
        val canUpdate = release != null && installedIdentityValid &&
            (local == null || release.versionCode > local.longVersionCode)
        AlertDialog(onDismissRequest = { if (!busy) selected = null }, title = { Text(app.name) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(app.description)
                if (release == null) Text("An approved APK, package ID, and signing certificate are needed for installation.")
                else Text("Approved version ${release.versionName} · Build ${release.versionCode}\n${release.packageName}")
                if (!installedIdentityValid) Text("The installed signing certificate does not match the approved release.")
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (app.id == "novaos" && onOpenWorkspace != null) TextButton(onClick = onOpenWorkspace) { Text("Open NovaOS") }
                if (app.id != "novaos" && local != null && installedIdentityValid && release != null) TextButton(onClick = {
                    val launch = context.packageManager.getLaunchIntentForPackage(release.packageName)
                    val component = launch?.component
                    val allowed = component != null && component.packageName == release.packageName && storeAllowed() &&
                        SanctuaryLauncherPolicy.appDecision(context, uk.co.traynor.privategallery.launcher.LauncherApp(
                            component.flattenToString(), release.packageName, app.name,
                        )).canLaunch
                    if (allowed && launch != null) runCatching { context.startActivity(launch) }
                        .onFailure { status = "Android could not open the installed app." }
                    else status = "This app is unavailable or restricted by Sanctuary."
                    selected = null
                }) { Text("Open installed app") }
            }
        }, confirmButton = {
            if (canUpdate && release != null) TextButton(enabled = !busy, onClick = {
                if (!storeAllowed() || SanctuaryStore.get(appContext).refresh() != null) {
                    status = "Install updates after Sanctuary ends."
                    selected = null
                    return@TextButton
                }
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settings = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"))
                    runCatching { context.startActivity(settings) }
                        .onFailure { status = "Android install permission settings are unavailable."; selected = null }
                    return@TextButton
                }
                busy = true
                scope.launch {
                    try {
                        val apk = withContext(Dispatchers.IO) { downloadVerifiedNovaApk(appContext, release) }
                        try {
                            check(storeAllowed() && SanctuaryStore.get(appContext).refresh() == null)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
                            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            status = "Android is ready to review the verified APK."
                        } catch (error: Exception) {
                            apk.delete()
                            throw error
                        }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        status = "The APK could not be verified or opened by Android."
                    } finally {
                        busy = false
                        selected = null
                    }
                }
            }) { Text(if (local == null) "Verify and install" else "Verify update") }
        }, dismissButton = { TextButton(enabled = !busy, onClick = { selected = null }) { Text("Close") } })
    }
}
