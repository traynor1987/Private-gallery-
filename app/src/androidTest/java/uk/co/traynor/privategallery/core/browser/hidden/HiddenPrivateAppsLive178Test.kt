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
import uk.co.traynor.privategallery.core.hidden.HiddenCredentialStore
import uk.co.traynor.privategallery.core.hidden.HiddenRepository
import uk.co.traynor.privategallery.core.privateapps.HiddenPrivateAppStore
import uk.co.traynor.privategallery.core.privateapps.PrimaryPrivateAppStore
import uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog
import uk.co.traynor.privategallery.core.vpn.*
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real authenticated parent, unchanged separate-process child and real owned transports.
 * The initial session uses a disposable real Hidden credential; per-open reauthentication
 * enters and verifies that separate Hidden PIN through the production child UI. The controlled
 * origins are synthetic, but launch requests traverse the official WireGuard/packaged Tor
 * implementations. Full packet/DNS leak evidence remains the existing transport suites' job.
 * Required fixture arguments fail closed: these selected release cases never silently skip.
 */
class HiddenPrivateAppsLive178Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = FixtureAccessibility178.automation(instrumentation)
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
        automation.clearCache()
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

    /** Native touch reaches WebView links/forms even when virtual ACTION_CLICK is unavailable. */
    private fun clickWeb(label:String) {
        val bounds=android.graphics.Rect()
        var previous:android.graphics.Rect?=null
        var scrollRequested=false
        var targetSeen=false
        var visibleSeen=false
        var inWindowSeen=false
        var scrollAccepted=false
        try {
            waitFor("Visible stable website action: $label") {
                val freshNodes=nodes()
                val root=automation.rootInActiveWindow ?: return@waitFor false
                val node=freshNodes.firstOrNull{it.windowId==root.windowId && it.isEnabled &&
                    (it.text?.toString()==label || it.contentDescription?.toString()==label)} ?: return@waitFor false
                targetSeen=true
                node.getBoundsInScreen(bounds)
                val window=android.graphics.Rect();root.getBoundsInScreen(window)
                val visible=node.isVisibleToUser && !bounds.isEmpty && bounds.intersect(window)
                visibleSeen=visibleSeen || node.isVisibleToUser
                inWindowSeen=inWindowSeen || visible
                if(!visible) {
                    previous=null
                    if(!scrollRequested) {
                        scrollRequested=true
                        scrollAccepted=node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                    }
                    return@waitFor false
                }
                val stable=previous==bounds
                previous=android.graphics.Rect(bounds)
                stable
            }
        } catch(failure:AssertionError) {
            // Synthetic fixture diagnostics contain geometry/flags only, never page text,
            // URLs, form contents, credentials, or a protected-window screenshot.
            runCatching {
                val output=File(checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")))
                check(output.isDirectory || output.mkdirs())
                val geometry=JSONArray()
                nodes().take(500).forEach {node ->
                    val rect=android.graphics.Rect();node.getBoundsInScreen(rect)
                    geometry.put(JSONObject().put("class",node.className?.toString())
                        .put("visible",node.isVisibleToUser).put("enabled",node.isEnabled)
                        .put("target",node.text?.toString()==label || node.contentDescription?.toString()==label)
                        .put("window",node.windowId).put("bounds",rect.toShortString()))
                }
                File(output,"private-apps-touch178-failure.json").writeText(JSONObject()
                    .put("target_seen",targetSeen).put("visible_seen",visibleSeen).put("in_window_seen",inWindowSeen)
                    .put("scroll_requested",scrollRequested).put("scroll_accepted",scrollAccepted)
                    .put("last_bounds",bounds.toShortString()).put("nodes",geometry).toString())
            }
            throw failure
        }
        val down=SystemClock.uptimeMillis()
        for(action in intArrayOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
            val event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0)
            event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            try {assertTrue("Website touch delivered",automation.injectInputEvent(event,true))}finally{event.recycle()}
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

    /** Never include PIN values in wait/assertion messages or diagnostic output. */
    private fun setAuthenticationPin(value: String) {
        waitFor("Separate Hidden PIN field accepts fixture input") {
            val fields=nodes().filter{it.isEditable && it.isVisibleToUser && it.isEnabled && it.isPassword}
            if(fields.size!=1)return@waitFor false
            fields.single().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)
            })
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
        val credentialContext=object:ContextWrapper(context) {override fun getNoBackupFilesDir()=root}
        val key = HiddenCredentialStore(credentialContext).create("738295".toCharArray())
        val primaryKey = ByteArray(32) { 29 }
        assertTrue(session.complete(session.beginAuthentication(), key.copyOf()))
        val repository=HiddenRepository(credentialContext,session)
        val uploadBytes="Private Apps document upload fixture 178\n".toByteArray(Charsets.UTF_8)
        val uploadDocument=try {
            uploadBytes.inputStream().use{repository.importMedia("Private app upload fixture.txt","text/plain",it)}
        }finally{uploadBytes.fill(0)}
        val uploadHash=uploadDocument.plaintextSha256.joinToString(""){"%02x".format(it)}
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
                    mode = mode, torUnixPath = path, hiddenCaptureAllowed={true})
            }
            if (torMode) {
                val host=checkNotNull(URI(launchUrl).host)
                val expected=checkNotNull(InstrumentationRegistry.getArguments().getString("hiddenOnionProbeExpected"))
                // A new packaged Tor instance needs its own controlled-origin readiness,
                // independent of the previously retired test's circuit or bootstrap status.
                HiddenTorSockets.open(checkNotNull(path),host,80,handshakeTimeoutMs=60_000).use {stream ->
                    stream.output.write("GET / HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray());stream.output.flush()
                    val bytes=stream.input.readNBytes(4097)
                    assertTrue("Controlled readiness response is bounded",bytes.size<=4096)
                    val raw=try{bytes.toString(Charsets.UTF_8)}finally{bytes.fill(0)}
                    assertEquals("200",raw.lineSequence().first().split(' ').getOrNull(1))
                    assertEquals(expected,raw.substringAfter("\r\n\r\n").trim())
                }
                checkNotNull(broker).verifyTorReady()
            }
            scenario.onActivity { checkNotNull(broker).launch() }
            waitFor("Actual claimed child enforces selected transport security") {
                checkNotNull(broker).childSecuritySnapshot() == ((!torMode) to true)
            }
            val child = context.getSystemService(ActivityManager::class.java).runningAppProcesses.single {
                it.uid == Process.myUid() && it.processName == context.packageName + ":hidden_vpn_browser"
            }
            assertNotEquals("CRUD must cross a real process boundary", Process.myPid(), child.pid)
            if(torMode) {
                // A bootstrapped Tor daemon is not an established onion circuit. Warm the
                // controlled origin through the real child WebView before bounded metadata
                // discovery; do not inject responses or retry a failed discovery into green.
                fun addressField()=nodes().firstOrNull {it.isEditable && it.isVisibleToUser && it.isEnabled}
                var touched=false
                var focusBounds:android.graphics.Rect?=null
                waitFor("Native Tor address is focused and publishes the complete controlled origin") {
                    val field=addressField() ?: return@waitFor false
                    if(!field.isFocused) {
                        if(!touched) {
                            val rect=android.graphics.Rect();field.getBoundsInScreen(rect)
                            val root=automation.rootInActiveWindow ?: return@waitFor false
                            val window=android.graphics.Rect();root.getBoundsInScreen(window)
                            if(field.windowId!=root.windowId || rect.isEmpty || !window.contains(rect)) {
                                focusBounds=null;return@waitFor false
                            }
                            val stable=focusBounds==rect
                            focusBounds=android.graphics.Rect(rect)
                            if(!stable)return@waitFor false
                            val down=SystemClock.uptimeMillis()
                            for(action in intArrayOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
                                val event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,rect.exactCenterX(),rect.exactCenterY(),0)
                                event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                                try {assertTrue("Address focus touch delivered",automation.injectInputEvent(event,true))}finally{event.recycle()}
                            }
                            touched=true
                        }
                        return@waitFor false
                    }
                    if(field.text?.toString()!=launchUrl)field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,launchUrl)
                    })
                    val fresh=addressField()
                    fresh?.isFocused==true && fresh.text?.toString()==launchUrl
                }
                click("Go")
                try {
                    waitFor("Actual onion HTTP document is ready before metadata discovery",65) {visible(rendered)}
                }catch(failure:AssertionError) {
                    runCatching {
                        val current=nodes()
                        val output=File(checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")))
                        check(output.isDirectory || output.mkdirs())
                        File(output,"private-apps-tor-readiness178-failure.json").writeText(JSONObject()
                            .put("address_matches",current.any {it.isEditable && it.text?.toString()==launchUrl})
                            .put("address_focused",current.any {it.isEditable && it.isFocused})
                            .put("reload_visible",visible("Reload")).put("stop_visible",visible("Stop"))
                            .put("page_error",visible("Page unavailable"))
                            .put("webview_count",current.count {it.className?.toString()=="android.webkit.WebView"})
                            .put("webview_children",current.filter {it.className?.toString()=="android.webkit.WebView"}.sumOf {it.childCount}).toString())
                    }
                    throw failure
                }

            }
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
            waitFor("Actual broker discovery validates controlled manifest", 60) { visible("Install as Private Web App") }
            click("Install as Private Web App")
            waitFor("Native save committed to authenticated selected encrypted catalogue") {
                visible("Owned launch fixture") && selected.read().apps.singleOrNull()?.startUrl() == launchUrl
            }
            assertEquals("standalone",selected.read().apps.single().display())
            assertEquals(URI(launchUrl).resolve("/").toString(),selected.read().apps.single().scope())
            val createdId = selected.read().apps.single().id()
            click("Edit Owned launch fixture")
            setEditorField(0, "Renamed owned fixture")
            click("Save")
            waitFor("Native edit preserves identity and persists new title") {
                visible("Renamed owned fixture") && selected.read().apps.singleOrNull()?.let {
                    it.id() == createdId && it.name() == "Renamed owned fixture" && it.startUrl() == launchUrl
                } == true
            }
            selected.update {catalog ->val saved=catalog.apps.single()
                catalog.upsert(PrivateAppCatalog.App(saved.id(),saved.name(),saved.startUrl(),saved.scope(),saved.display(),saved.icon(),saved.pinned(),saved.addressBar(),true))
            }
            click("Close")
            click("Private Apps")
            waitFor("Reopened cross-process catalogue loads encrypted edited entry") { visible("Renamed owned fixture") }
            val requestPath = URI(launchUrl).rawPath
            val before = requestCount(events(), requestPath)
            click("Open")
            waitFor("Saved app requests its separate Hidden credential") {
                visible("Unlock saved app") && visible("Enter your separate Hidden PIN.")
            }
            assertEquals("Opening a protected app cannot request its origin before authentication",before,requestCount(events(),requestPath))
            setAuthenticationPin("164820")
            val attemptedAt=SystemClock.elapsedRealtime()
            click("Open app")
            waitFor("Wrong Hidden PIN is rejected by the production broker") {visible("Authentication failed or is temporarily unavailable.")}
            assertEquals("Wrong Hidden PIN must not launch the saved origin",before,requestCount(events(),requestPath))
            click("Cancel")
            waitFor("Cancelled authentication closes its prompt") {!visible("Unlock saved app")}
            assertEquals("Cancelling reauthentication must not launch the saved origin",before,requestCount(events(),requestPath))
            click("Private Apps")
            waitFor("Cancelled protected app remains in its encrypted catalogue") {visible("Renamed owned fixture")}
            click("Open")
            waitFor("Retry still requires the separate Hidden credential") {visible("Enter your separate Hidden PIN.")}
            waitFor("Production PIN retry interval has elapsed") {SystemClock.elapsedRealtime()-attemptedAt>=1_100}
            setAuthenticationPin("738295")
            click("Open app")
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
            waitFor("Standalone app has compact controls") {visible("Expand app controls")}
            assertFalse("App toolbar starts collapsed",visible("App home"))
            click("Expand app controls")
            waitFor("Launched Private App has actual dedicated controls") {
                visible("App home") && visible("Private app settings") && visible("Private Apps library")
            }
            if(!torMode) {
                waitFor("Screenshot fixture has finished the original app document") {
                    visible("Refresh app") && !visible("Stop app loading") && visible("Download app document")
                }
                val count=repository.items().size
                click("Screenshot to Vault");click("Save screenshot")
                waitFor("Actual child screenshot pipe persists authenticated encrypted PNG",30) {repository.items().size==count+1}
                val screenshot=repository.items().single{it.displayName=="browser-screenshot.png"}
                assertTrue(screenshot.vaultOnly);assertNull(screenshot.sourceUri)
                repository.verify(screenshot.id)
                val png=repository.read(screenshot.id)
                try {assertEquals(0x89,png[0].toInt() and 255);assertTrue(png.size>24)}finally{png.fill(0)}
                val saved=repository.items().map{it.id}.toSet()
                clickWeb("Download slow app document");click("Save GET file")
                click("Browser tools");click("Downloads")
                waitFor("Actual broker download is cancellable") {visible("Cancel active download")}
                click("Cancel active download")
                waitFor("Actual broker cancellation completes without publication") {visible("Download cancelled. No new item was saved.")}
                assertEquals(saved,repository.items().map{it.id}.toSet())
                assertFalse("Cancelled encrypted staging is removed",root.walkTopDown().any{it.isFile && it.name.startsWith("staged-")})
                click("Keep browsing")
            }
            val documentPath="/private-app-document178.txt"
            val beforeDownload=requestCount(events(),documentPath)
            clickWeb("Download app document")
            click("Save GET file")
            waitFor("Actual selected transport saves a validated document encrypted in Hidden Vault",60) {
                repository.items().any{it.id!=uploadDocument.id && it.mimeType=="text/plain"}
            }
            val downloaded=repository.items().single{it.id!=uploadDocument.id && it.mimeType=="text/plain"}
            repository.verify(downloaded.id)
            val downloadedBytes=repository.read(downloaded.id)
            try {assertArrayEquals("Encrypted download contains exact controlled document payload",
                "Private Apps document download fixture 178\n".toByteArray(Charsets.UTF_8),downloadedBytes)
            }finally{downloadedBytes.fill(0)}
            assertNull("Downloaded encrypted item has no public source URI",downloaded.sourceUri)
            assertTrue("Download reached the real controlled origin",requestCount(events(),documentPath)>beforeDownload)
            root.walkTopDown().filter{it.isFile}.forEach {file ->
                val bytes=file.readBytes()
                try {val contents=bytes.toString(Charsets.ISO_8859_1)
                    assertFalse("Downloaded document never appears in plaintext storage",contents.contains("Private Apps document download fixture 178"))
                    assertFalse("Upload document remains encrypted in storage",contents.contains("Private Apps document upload fixture 178"))
                }finally{bytes.fill(0)}
            }
            fun documentPosts():Int {val observed=events();return (0 until observed.length()).count {index ->
                observed.getJSONObject(index).let{it.optString("method")=="POST" && it.optString("path")=="/private-app-document-upload178"}
            }}
            val beforeUpload=documentPosts()
            clickWeb("Choose app document")
            waitFor("Native document chooser shows only this authenticated Hidden repository") {visible("Choose from Hidden Vault") && visible(uploadDocument.displayName)}
            click(uploadDocument.displayName)
            waitFor("Document upload requires explicit outward-copy confirmation") {visible("Allow upload")}
            assertEquals("Selecting a document does not upload before confirmation",beforeUpload,documentPosts())
            click("Cancel")
            assertEquals("Cancelled upload confirmation transmits nothing",beforeUpload,documentPosts())
            clickWeb("Choose app document")
            waitFor("Cancelled chooser can be reopened") {visible(uploadDocument.displayName)}
            click(uploadDocument.displayName);click("Allow upload")
            // App home starts a fresh document; the prior selection cannot silently submit.
            // Already consented bytes are not recallable; session revocation separately closes descriptors.
            val beforeAppHome=requestCount(events(),requestPath)
            click("App home")
            waitFor("Fresh App home document completes after grant cancellation") {
                requestCount(events(),requestPath)>beforeAppHome &&
                    visible("Download app document") && visible("Refresh app") && !visible("Stop app loading")
            }
            assertEquals(beforeUpload,documentPosts())
            clickWeb("Choose app document")
            waitFor("Fresh navigation requires a fresh document selection") {visible(uploadDocument.displayName)}
            click(uploadDocument.displayName);click("Allow upload")
            clickWeb("Upload app document")
            waitFor("Real WebView multipart upload preserves exact encrypted document bytes",60) {
                val observed=events()
                documentPosts()>beforeUpload && (0 until observed.length()).any{index ->observed.getJSONObject(index).let {event ->
                    event.optString("method")=="POST" && event.optString("path")=="/private-app-document-upload178" &&
                        event.optBoolean("document_exact") && event.optString("upload_sha256")==uploadHash &&
                        (torMode || (event.optString("source")=="10.91.0.2" && event.optBoolean("tls")))
                }}
            }
            assertTrue("Document transfer preserves the authenticated immutable child",checkNotNull(broker).valid())
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
                    .put("public_pwa_discovery",true).put("owner_isolation", true).put("origin_observed_launch", true).put("dedicated_controls", true)
                    .put("separate_hidden_pin_reauthentication",true).put("wrong_pin_and_cancel_no_origin_request",true)
                    .put("document_download_encrypted_payload_verified",true).put("document_native_chooser_confirmed",true)
                    .put("document_upload_origin_hash_verified",true)
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
            val previousNetworks=context.getSystemService(ConnectivityManager::class.java)
            waitFor("Previous app-owned VPN Network has actually retired") {
                previousNetworks.allNetworks.none {previousNetworks.getNetworkCapabilities(it)?.let {caps ->
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && caps.ownerUid==Process.myUid()
                }==true}
            }
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
            // Capabilities can arrive before netd installs this Network's resolver config.
            waitFor("The same app-owned VPN Network has its fixture DNS resolver ready") {
                manager.getNetworkCapabilities(pinned)?.ownerUid==Process.myUid() &&
                    uk.co.traynor.privategallery.core.vpn.OwnedVpnDns.lookup(context,pinned,"native-phase.fixture.invalid"){engine.ownsTunnel && engine.state==VpnConnectionState.CONNECTED}.single().hostAddress=="203.0.113.10"
            }
            assertEquals("203.0.113.10", uk.co.traynor.privategallery.core.vpn.OwnedVpnDns.lookup(context,pinned,"native-phase.fixture.invalid"){engine.ownsTunnel && engine.state==VpnConnectionState.CONNECTED}.single().hostAddress)
            fun dnsEvents():JSONArray = OwnedVpnFixtureClient178.evidence(context,pinned).getJSONArray("events")
            val dnsAllowed={engine.ownsTunnel && engine.state==VpnConnectionState.CONNECTED}
            assertEquals("Truncated DNS uses the same owned VPN TCP path","203.0.113.10",
                uk.co.traynor.privategallery.core.vpn.OwnedVpnDns.lookup(context,pinned,"tcp-dns-phase.fixture.invalid",dnsAllowed).single().hostAddress)
            val dnsRevoked=AtomicBoolean(false)
            val dnsWorker=java.util.concurrent.Executors.newSingleThreadExecutor()
            try {
                val pending=dnsWorker.submit<List<java.net.InetAddress>> {
                    uk.co.traynor.privategallery.core.vpn.OwnedVpnDns.lookup(context,pinned,"slow-dns-phase.fixture.invalid"){dnsAllowed() && !dnsRevoked.get()}
                }
                waitFor("Pending bound DNS request observed before authority revocation") {
                    val events=dnsEvents();(0 until events.length()).any {events.getJSONObject(it).optString("name")=="slow-dns-phase.fixture.invalid"}
                }
                assertFalse("DNS authority is revoked while the real response remains pending",pending.isDone)
                val revokedAt=SystemClock.elapsedRealtime();dnsRevoked.set(true)
                val failure=assertThrows(java.util.concurrent.ExecutionException::class.java) {pending.get(1500,java.util.concurrent.TimeUnit.MILLISECONDS)}
                assertTrue("Revoked DNS cannot publish an address",failure.cause is java.net.UnknownHostException)
                assertTrue("Revocation interrupts the pending DNS socket promptly",SystemClock.elapsedRealtime()-revokedAt<1000)
            } finally {dnsRevoked.set(true);dnsWorker.shutdownNow()}
            val launchUrl = "https://native-phase.fixture.invalid/private-apps178-${UUID.randomUUID()}"
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                exercise(scenario, pinned, HiddenConnectionPolicy.Mode.VPN_ONLY, {
                    engine.ownsTunnel && engine.state == VpnConnectionState.CONNECTED &&
                        manager.getNetworkCapabilities(pinned)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                }, null, launchUrl, "VPN fixture ready") {
                    dnsEvents()
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
                        val body=FixtureHttpResponse178.read(stream.input)
                        JSONObject(body).getJSONArray("events")
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
