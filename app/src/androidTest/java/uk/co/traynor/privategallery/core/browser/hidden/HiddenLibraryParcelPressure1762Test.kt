package uk.co.traynor.privategallery.core.browser.hidden

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.traynor.privategallery.BuildConfig
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.vpn.*

/** Maximum production library envelope, plus unchanged child/caller-authenticated Binder path.
 * The owned-VPN case requires the same opt-in CI fixture as HiddenVpnLive1762Test.
 */
class HiddenLibraryParcelPressure1762Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private fun url(kind: String, index: Int): String {
        val prefix = "https://library-pressure.example/$kind-$index/"
        // '/' is URI-valid and escaped by Android JSONStringer; this is larger than
        // a maximum-length alphanumeric URL after ordinary JSON serialization.
        return prefix + "/".repeat(2048 - prefix.length)
    }
    private fun unlock(session: HiddenSession, key: Byte) {
        assertTrue(session.complete(session.beginAuthentication(), ByteArray(32) { key }))
    }
    private fun fill(root: File, session: HiddenSession): HiddenBrowserLibrary {
        val library = HiddenBrowserLibrary(root, session, session.authority(), true)
        repeat(50) { library.bookmark(url("bookmark", it)) }
        repeat(100) { library.visit(url("history", it)) }
        assertEquals(50, library.bookmarks().size)
        assertEquals(100, library.history().size)
        assertTrue((library.bookmarks() + library.history()).all { it.length == 2048 })
        return library
    }
    private fun waitFor(message: String, seconds: Int = 30, predicate: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + seconds * 1000L
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            Thread.sleep(100)
        }
        assertTrue(message, predicate())
    }
    private fun texts(): List<String> {
        val result = mutableListOf<String>()
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
            if (node == null) return
            node.text?.toString()?.let(result::add)
            repeat(node.childCount) { visit(node.getChild(it)) }
        }
        visit(automation.rootInActiveWindow)
        return result
    }
    private fun click(text: String) {
        waitFor("Visible actionable '$text'") {
            fun find(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
                if (node == null) return false
                if (node.text?.toString() == text || node.contentDescription?.toString() == text) {
                    var target: android.view.accessibility.AccessibilityNodeInfo? = node
                    while (target != null && !target.isClickable) target = target.parent
                    target?.performAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                    return target?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true
                }
                repeat(node.childCount) { if (find(node.getChild(it))) return true }
                return false
            }
            find(automation.rootInActiveWindow)
        }
    }
    private fun ciphertext(root: File): Map<String, ByteArray> = root.walkTopDown()
        .filter { it.isFile }.associate { it.relativeTo(root).path to it.readBytes() }
    private fun unchanged(before: Map<String, ByteArray>, root: File) {
        val after = ciphertext(root)
        assertEquals(before.keys, after.keys)
        before.forEach { (path, bytes) -> assertArrayEquals(bytes, after.getValue(path)) }
    }
    @Test fun maximalResponseFitsParcelAndAuthenticationFailuresPreserveCiphertext() {
        val root = File(instrumentation.targetContext.noBackupFilesDir, "library-pressure-${UUID.randomUUID()}")
        val session = HiddenSession(android.os.SystemClock::elapsedRealtime)
        try {
            unlock(session, 31)
            val library = fill(root, session)
            // This is the same production serializer called by the LIBRARY broker reply.
            val response = HiddenVpnBrowserBroker.librarySnapshot(library)
            val parsed = org.json.JSONObject(response)
            assertEquals(50, parsed.getJSONArray("bookmarks").length())
            assertEquals(100, parsed.getJSONArray("history").length())
            assertEquals(url("bookmark", 49), parsed.getJSONArray("bookmarks").getString(0))
            val parcel = Parcel.obtain()
            try {
                parcel.writeNoException()
                parcel.writeString(response)
                assertTrue("Fixture must exercise at least 600 KiB of response pressure", parcel.dataSize() >= 600 * 1024)
                assertTrue("Library reply needs margin below Binder's shared 1 MiB transaction budget", parcel.dataSize() < 900 * 1024)
                parcel.setDataPosition(0)
                parcel.readException()
                assertEquals(response, parcel.readString())
            } finally { parcel.recycle() }
            val before = ciphertext(root)
            session.lock()
            unlock(session, 32)
            val wrongKey = HiddenBrowserLibrary(root, session, session.authority(), true)
            assertTrue(runCatching { wrongKey.bookmarks() }.isFailure)
            assertTrue(runCatching { wrongKey.bookmark("https://library-pressure.example/rejected") }.isFailure)
            unchanged(before, root)
            session.lock()
            unlock(session, 31)
            assertTrue(runCatching { library.bookmark("https://library-pressure.example/stale") }.isFailure)
            unchanged(before, root)
            assertEquals(50, HiddenBrowserLibrary(root, session, session.authority(), true).bookmarks().size)
        } finally { session.lock(); root.deleteRecursively() }
    }
    @Test fun maximalLibraryTraversesActualClaimedChildBinderWithoutTransactionTooLarge() {
        val configPath = InstrumentationRegistry.getArguments().getString("hiddenVpnConfig")
        assumeTrue("Dedicated real VPN fixture is opt-in", configPath != null)
        check(BuildConfig.DEBUG)
        require(configPath == "/data/local/tmp/hidden-vpn1762.conf")
        val context = instrumentation.targetContext
        val info = automation.serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = info
        fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes().toString(Charsets.UTF_8) }
        shell("appops set ${context.packageName} ACTIVATE_VPN allow")
        assertNull(VpnService.prepare(context))
        val profile = (VpnProfileParser.import("Disposable library pressure VPN", shell("cat $configPath")) as VpnProfileImportResult.Accepted).profile
        val engine = WireGuardVpnEngine(OfficialWireGuardBackend(context))
        val session = HiddenSession(android.os.SystemClock::elapsedRealtime)
        val allowed = AtomicBoolean(true)
        val root = File(context.noBackupFilesDir, "library-binder-${UUID.randomUUID()}")
        var broker: HiddenVpnBrowserBroker? = null
        try {
            unlock(session, 33)
            fill(root, session)
            engine.connect(profile)
            waitFor("Authenticated owned VPN ready", 90) { engine.ownsTunnel && engine.state == VpnConnectionState.CONNECTED }
            val manager = context.getSystemService(ConnectivityManager::class.java)
            var pinned: Network? = null
            waitFor("Exactly one owned VPN Network") {
                pinned = manager.allNetworks.singleOrNull { network -> manager.getNetworkCapabilities(network)?.let { it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && it.ownerUid == Process.myUid() } == true }
                pinned != null
            }
            val network = checkNotNull(pinned)
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val isolated = object : ContextWrapper(activity) { override fun getNoBackupFilesDir() = root }
                    broker = HiddenVpnBrowserBroker(isolated, session, network,
                        { allowed.get() && engine.ownsTunnel && engine.state == VpnConnectionState.CONNECTED }, {})
                    broker!!.launch()
                }
                // Both actions invoke production LIBRARY from the actually claimed child PID.
                // A TransactionTooLargeException becomes "Hidden library unavailable" in the
                // child and cannot satisfy these saved-entry assertions.
                click("Settings"); click("Bookmarks")
                waitFor("Maximum bookmarks response reaches actual child") { texts().contains(url("bookmark", 49)) }
                assertFalse(texts().contains("Hidden library unavailable."))
                click("Done")
                click("Settings"); click("History")
                waitFor("Maximum history response reaches actual child") { texts().contains(url("history", 99)) }
                assertFalse(texts().contains("Hidden library unavailable."))
                click("Done") // Close History, then explicitly finish the still-authorized child.
                click("Done")
                waitFor("Explicit authorized Done acknowledges supported profile clearing",10) {broker!!.profileClearAcknowledged}
                waitFor("Ordinary Done exits the isolated process",10) {!HiddenVpnBrowserBroker.hasDedicatedProcess(context)}
                waitFor("Ordinary Done removes owned browser roots",15) {
                    !File(context.noBackupFilesDir,"hidden-vpn-webview-v1/${broker!!.token}").exists() &&
                        !File(context.cacheDir,"hidden-vpn-webview-v1/${broker!!.token}").exists()
                }
                assertTrue("Ordinary browser Done must not replace independent Hidden credentials",session.isUnlocked)
                session.lock(); allowed.set(false); broker!!.close()
                waitFor("Claimed child is revoked") { !broker!!.valid() }
            }
        } finally {
            allowed.set(false); broker?.close(); session.lock(); engine.disconnect()
            waitFor("VPN teardown", 15) { !engine.ownsTunnel }
            root.deleteRecursively()
        }
    }
}
