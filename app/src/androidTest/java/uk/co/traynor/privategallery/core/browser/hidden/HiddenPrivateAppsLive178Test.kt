package uk.co.traynor.privategallery.core.browser.hidden

import android.app.ActivityManager
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Binder
import android.os.Bundle
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.BuildConfig
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.privateapps.HiddenPrivateAppStore
import uk.co.traynor.privategallery.core.privateapps.PrimaryPrivateAppStore
import uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog
import uk.co.traynor.privategallery.core.vpn.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real authenticated parent, unchanged separate-process child and real owned transports.
 * Authentication uses a disposable synthetic key, not credential-entry UI. The controlled
 * origins are synthetic, but launch requests traverse the official WireGuard/packaged Tor
 * implementations. Full packet/DNS leak evidence remains the existing transport suites' job.
 * Required fixture arguments fail closed: these selected release cases never silently skip.
 */
class HiddenPrivateAppsLive178Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val context get() = instrumentation.targetContext

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)
    ).use { it.readBytes().toString(Charsets.UTF_8) }

    private fun waitFor(message: String, seconds: Int = 30, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + seconds * 1000L
        var lastFailure: Throwable? = null
        do {
            try { if (predicate()) return } catch (failure: Exception) { lastFailure = failure }
            Thread.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError(message, lastFailure)
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            result += node
            repeat(node.childCount) { visit(node.getChild(it)) }
        }
        visit(automation.rootInActiveWindow)
        return result
    }

    private fun visible(label: String) = nodes().any {
        it.isVisibleToUser && (it.text?.toString() == label || it.contentDescription?.toString() == label)
    }

    private fun click(label: String) {
        waitFor("Actual native control: $label") {
            var node = nodes().firstOrNull {
                it.isVisibleToUser && (it.text?.toString() == label || it.contentDescription?.toString() == label)
            } ?: return@waitFor false
            while (!node.isClickable) node = node.parent ?: return@waitFor false
            node.isEnabled && node.isVisibleToUser && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    /** The active editor dialog has precisely its name and URL fields, never the browser bar. */
    private fun setEditorField(index: Int, value: String) {
        waitFor("Private App editor field $index accepts complete fixture value") {
            val fields = nodes().filter { it.isEditable && it.isVisibleToUser && it.isEnabled }
            if (fields.size != 2) return@waitFor false
            val field = fields[index]
            if (field.text?.toString() != value) {
                field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                })
            }
            nodes().filter { it.isEditable && it.isVisibleToUser && it.isEnabled }
                .getOrNull(index)?.text?.toString() == value
        }
    }

    private fun fixtureGuard() {
        assertTrue("Never run against a production APK", BuildConfig.DEBUG)
        assertEquals("Disposable debug package required", "uk.co.traynor.privategallery.hiddenprototype", context.packageName)
        assertEquals("Disposable emulator required", "1", shell("getprop ro.kernel.qemu").trim())
        val service = automation.serviceInfo
        service.flags = service.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = service
        shell("appops set ${context.packageName} ACTIVATE_VPN allow")
        assertNull("Fixture must have Android VPN consent", VpnService.prepare(context))
        waitFor("Previous native transport and admission lease are retired", 20) {
            HiddenVpnBrowserBroker.admissionRetired() && !HiddenVpnBrowserBroker.hasDedicatedProcess(context) &&
                HiddenTorIsolationService.released() && !HiddenTorIsolationService.hasTorProcess(context)
        }
    }

    private fun app(name: String, url: String) = PrivateAppCatalog.App(
        UUID.randomUUID().toString(), name, url, "", "browser", "", false, true, false
    )

    private fun fingerprint(root: File): Map<String, String> = root.walkTopDown().filter { it.isFile }.associate {
        it.relativeTo(root).path to MessageDigest.getInstance("SHA-256").digest(it.readBytes())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    /** A same-UID unclaimed parent must not impersonate the child, even with the Binder. */
    private fun assertUnclaimedCallerDenied(broker: HiddenVpnBrowserBroker) {
        val request = Parcel.obtain()
        val response = Parcel.obtain()
        try {
            request.writeInterfaceToken(HiddenVpnBrowserBroker.DESCRIPTOR)
            request.writeInt(0)
            request.writeInt(-1)
            request.writeString("")
            assertTrue(broker.transact(HiddenVpnBrowserBroker.PRIVATE_APPS, request, response, 0))
            assertThrows(RuntimeException::class.java) { response.readException() }
        } finally { request.recycle(); response.recycle() }
    }

    private fun requestCount(events: JSONArray, path: String): Int = (0 until events.length()).count {
        events.getJSONObject(it).let { event -> event.optString("method") == "GET" && event.optString("path") == path }
    }

    /** All mutations below are ordinary native UI actions that invoke the production Binder. */
    private fun exercise(
        scenario: ActivityScenario<ComponentActivity>, network: Network, mode: HiddenConnectionPolicy.Mode,
        authorized: () -> Boolean, path: String?, launchUrl: String, rendered: String,
        events: () -> JSONArray,
    ) {
        val root = File(context.noBackupFilesDir, "private-apps-live178-${UUID.randomUUID()}")
        assertFalse("Never reuse an existing fixture directory", root.exists())
        assertTrue(root.mkdirs())
        val session = HiddenSession(SystemClock::elapsedRealtime)
        val key = ByteArray(32) { 83 }
        val primaryKey = ByteArray(32) { 29 }
        assertTrue(session.complete(session.beginAuthentication(), key.copyOf()))
        val torMode = mode == HiddenConnectionPolicy.Mode.TOR_ONLY
        val selected = HiddenPrivateAppStore(root, session, session.authority(), torMode)
        val other = HiddenPrivateAppStore(root, session, session.authority(), !torMode)
        val primary = PrimaryPrivateAppStore(File(root, "primary-fixture"), primaryKey, { true }, { it() })
        val otherSentinel = app("Other Hidden owner sentinel", "https://example.test/other")
        val primarySentinel = app("Primary owner sentinel", "https://example.test/primary")
        other.update { it.upsert(otherSentinel) }
        primary.update { it.upsert(primarySentinel) }
        val ownerLabel = if (torMode) "Hidden Vault · Tor" else "Hidden Vault · VPN"
        var broker: HiddenVpnBrowserBroker? = null
        val ended = AtomicBoolean()
        val parentBinding = context.getSystemService(ConnectivityManager::class.java).boundNetworkForProcess
        fun openChild() {
            ended.set(false)
            scenario.onActivity { activity ->
                val owned = object : ContextWrapper(activity) { override fun getNoBackupFilesDir() = root }
                broker = HiddenVpnBrowserBroker(owned, session, network, authorized, { ended.set(true) },
                    mode = mode, torUnixPath = path)
            }
            if (torMode) checkNotNull(broker).verifyTorReady()
            scenario.onActivity { checkNotNull(broker).launch() }
            waitFor("Actual claimed child enforces selected transport security") {
                checkNotNull(broker).childSecuritySnapshot() == ((!torMode) to true)
            }
            val child = context.getSystemService(ActivityManager::class.java).runningAppProcesses.single {
                it.uid == Process.myUid() && it.processName == context.packageName + ":hidden_vpn_browser"
            }
            assertNotEquals("CRUD must cross a real process boundary", Process.myPid(), child.pid)
            click("Private Apps")
            waitFor("Native catalogue belongs to selected immutable owner") { visible(ownerLabel) }
            assertFalse(visible(otherSentinel.name()))
            assertFalse(visible(primarySentinel.name()))
        }
        try {
            openChild()
            waitFor("Selected catalogue starts independently empty") { visible("No private apps yet") }
            assertUnclaimedCallerDenied(checkNotNull(broker))
            assertTrue(selected.read().apps.isEmpty())
            click("Add Private App")
            setEditorField(0, "Owned launch fixture")
            setEditorField(1, launchUrl)
            click("Save")
            waitFor("Native save committed to authenticated selected encrypted catalogue") {
                visible("Owned launch fixture") && selected.read().apps.singleOrNull()?.startUrl() == launchUrl
            }
            val createdId = selected.read().apps.single().id()
            click("Edit Owned launch fixture")
            setEditorField(0, "Renamed owned fixture")
            click("Save")
            waitFor("Native edit preserves identity and persists new title") {
                visible("Renamed owned fixture") && selected.read().apps.singleOrNull()?.let {
                    it.id() == createdId && it.name() == "Renamed owned fixture" && it.startUrl() == launchUrl
                } == true
            }
            click("Close")
            click("Private Apps")
            waitFor("Reopened cross-process catalogue loads encrypted edited entry") { visible("Renamed owned fixture") }
            val requestPath = URI(launchUrl).rawPath
            val before = requestCount(events(), requestPath)
            click("Open")
            waitFor("Private App launch reached controlled origin through selected transport", 60) {
                requestCount(events(), requestPath) > before && visible(rendered)
            }
            assertTrue(checkNotNull(broker).valid())
            assertEquals("Parent process routing was never rebound", parentBinding,
                context.getSystemService(ConnectivityManager::class.java).boundNetworkForProcess)
            if (!torMode) {
                val observed = events()
                assertTrue("VPN launch uses HTTPS from the owned tunnel", (0 until observed.length()).any { index ->
                    observed.getJSONObject(index).let { event ->
                        event.optString("path") == requestPath && event.optString("method") == "GET" &&
                            event.optString("source") == "10.91.0.2" && event.optBoolean("tls")
                    }
                })
            }
            waitFor("Launched Private App has actual dedicated controls") {
                visible("App home") && visible("Private app settings") && visible("Private Apps library")
            }
            click("Private Apps library")
            waitFor("Launched app remains owned") { visible("Renamed owned fixture") }
            click("Remove Renamed owned fixture")
            click("Remove app")
            waitFor("Confirmed removal persisted across real Binder") {
                selected.read().apps.isEmpty() && visible("No private apps yet")
            }
            click("Add Private App")
            setEditorField(0, "Persisted survivor")
            setEditorField(1, launchUrl)
            click("Save")
            waitFor("Survivor committed before revocation") {
                visible("Persisted survivor") && selected.read().apps.singleOrNull()?.name() == "Persisted survivor"
            }
            val survivor = selected.read().apps.single()
            assertEquals(listOf(otherSentinel), other.read().apps)
            assertEquals(listOf(primarySentinel), primary.read().apps)
            click("Edit Persisted survivor")
            setEditorField(0, "Uncommitted revoked draft")
            val beforeRevoke = fingerprint(root)
            val retired = checkNotNull(broker)
            session.lock()
            waitFor("Authentication revocation kills the child and retires capability", 20) {
                ended.get() && !retired.valid() && !retired.childIsAlive() &&
                    !HiddenVpnBrowserBroker.hasDedicatedProcess(context) && HiddenVpnBrowserBroker.admissionRetired()
            }
            assertFalse("Revoked editor is no longer visible", visible("Uncommitted revoked draft"))
            assertThrows(IllegalStateException::class.java) { selected.read() }
            assertUnclaimedCallerDenied(retired)
            assertEquals("Lock never commits a dirty editor or modifies other owners", beforeRevoke, fingerprint(root))
            assertTrue(session.complete(session.beginAuthentication(), key.copyOf()))
            assertThrows("Old store authority cannot recover on reauthentication", IllegalStateException::class.java) { selected.read() }
            assertThrows("Old browser capability cannot recover on reauthentication", IllegalStateException::class.java) { retired.launch() }
            openChild()
            waitFor("Fresh authenticated native child reloads survivor without revoked draft") { visible("Persisted survivor") }
            val fresh = HiddenPrivateAppStore(root, session, session.authority(), torMode)
            assertEquals(listOf(survivor), fresh.read().apps)
            assertEquals(listOf(otherSentinel), HiddenPrivateAppStore(root, session, session.authority(), !torMode).read().apps)
            assertEquals(listOf(primarySentinel), primary.read().apps)
            click("Remove Persisted survivor")
            click("Remove app")
            waitFor("Fresh child can mutate only its newly authorised catalogue") {
                fresh.read().apps.isEmpty() && visible("No private apps yet")
            }
            val output = File(checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")))
            check(output.isDirectory || output.mkdirs())
            File(output, "private-apps-${if (torMode) "tor" else "vpn"}178-result.json").writeText(
                JSONObject().put("mode", mode.name).put("real_child", true).put("catalogue_crud", true)
                    .put("owner_isolation", true).put("origin_observed_launch", true).put("dedicated_controls", true)
                    .put("same_uid_unclaimed_caller_denied", true).put("authentication_revoked", true)
                    .put("dirty_editor_not_committed", true).put("old_authority_denied_after_reauthentication", true)
                    .put("catalogue_survived_reauthentication", true).toString()
            )
        } finally {
            broker?.close()
            session.lock()
            key.fill(0)
            primaryKey.fill(0)
            waitFor("Private Apps child retirement finishes before fixture deletion", 20) {
                !HiddenVpnBrowserBroker.hasDedicatedProcess(context) && HiddenVpnBrowserBroker.admissionRetired()
            }
            root.deleteRecursively()
        }
    }

    @Test(timeout = 300_000)
    fun vpnCatalogueCrudLaunchAndRevocationUseAuthenticatedSeparateProcess() {
        fixtureGuard()
        val arguments = InstrumentationRegistry.getArguments()
        val configPath = checkNotNull(arguments.getString("hiddenVpnConfig")) { "Real VPN fixture argument required" }
        require(configPath == "/data/local/tmp/hidden-vpn1762.conf")
        require(arguments.getString("hiddenVpnOrigin") == "http://fixture.invalid")
        val profile = (VpnProfileParser.import("Disposable Private Apps VPN", shell("cat $configPath"))
            as VpnProfileImportResult.Accepted).profile
        val engine = WireGuardVpnEngine(OfficialWireGuardBackend(context))
        try {
            engine.connect(profile)
            waitFor("Official WireGuard authenticated peer handshake", 90) {
                engine.ownsTunnel && engine.state == VpnConnectionState.CONNECTED
            }
            val manager = context.getSystemService(ConnectivityManager::class.java)
            var network: Network? = null
            waitFor("Exactly one app-owned immutable VPN Network") {
                network = manager.allNetworks.singleOrNull { candidate ->
                    manager.getNetworkCapabilities(candidate)?.let {
                        it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && it.ownerUid == Process.myUid()
                    } == true
                }
                network != null
            }
            val pinned = checkNotNull(network)
            assertEquals("203.0.113.10", pinned.getAllByName("native-phase.fixture.invalid").single().hostAddress)
            val launchUrl = "https://native-phase.fixture.invalid/private-apps178-${UUID.randomUUID()}"
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                exercise(scenario, pinned, HiddenConnectionPolicy.Mode.VPN_ONLY, {
                    engine.ownsTunnel && engine.state == VpnConnectionState.CONNECTED &&
                        manager.getNetworkCapabilities(pinned)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                }, null, launchUrl, "VPN fixture ready") {
                    val connection = pinned.openConnection(URL("http://fixture.invalid/evidence")) as HttpURLConnection
                    connection.connectTimeout = 3000
                    connection.readTimeout = 3000
                    try { connection.inputStream.use { JSONObject(it.readBytes().toString(Charsets.UTF_8)).getJSONArray("events") } }
                    finally { connection.disconnect() }
                }
            }
        } finally { engine.disconnect() }
    }

    @Test(timeout = 360_000)
    fun torCatalogueCrudOnionLaunchAndRevocationUseAuthenticatedSeparateProcess() {
        fixtureGuard()
        val origin = URI(checkNotNull(InstrumentationRegistry.getArguments().getString("hiddenOnionProbeUrl")) {
            "Real controlled onion fixture argument required"
        })
        val host = checkNotNull(origin.host)
        require(origin.scheme == "http" && host.matches(Regex("[a-z2-7]{56}\\.onion")))
        val allowed = AtomicBoolean(true)
        var isolation: HiddenTorIsolationConnection? = null
        var tor: HiddenTorConnection? = null
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity {
                    isolation = HiddenTorIsolationConnection(context, { allowed.get() }, { allowed.set(false) }).also { it.start() }
                }
                waitFor("Owned Tor IPv4 and IPv6 sink") { isolation?.network() != null }
                val network = checkNotNull(isolation!!.network())
                val config = isolation!!.configuration(Binder())
                scenario.onActivity { tor = HiddenTorConnection(context, config).also { it.start() } }
                waitFor("Packaged Tor reaches authenticated app-private Unix SOCKS", 120) { tor?.unixReady() == true }
                exercise(scenario, network, HiddenConnectionPolicy.Mode.TOR_ONLY, {
                    allowed.get() && tor?.unixReady() == true && isolation?.network() == network
                }, config.unixPath, "http://$host/interactive", "Tor native fixture") {
                    HiddenTorSockets.open(config.unixPath, host, 80).use { stream ->
                        stream.output.write("GET /evidence HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray())
                        stream.output.flush()
                        val raw = stream.input.readBytes().toString(Charsets.UTF_8)
                        require(raw.startsWith("HTTP/1.1 200") || raw.startsWith("HTTP/1.0 200"))
                        JSONObject(raw.substringAfter("\r\n\r\n")).getJSONArray("events")
                    }
                }
            }
        } finally {
            allowed.set(false)
            tor?.close()
            isolation?.close()
            waitFor("Tor fixture transport fully retires", 20) {
                HiddenTorIsolationService.released() && !HiddenTorIsolationService.hasTorProcess(context)
            }
        }
    }
}
