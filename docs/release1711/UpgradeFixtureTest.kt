package uk.co.traynor.privategallery.upgrade

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore
import uk.co.traynor.privategallery.core.vault.*
import uk.co.traynor.privategallery.core.vpn.*
import uk.co.traynor.privategallery.core.hidden.*
import uk.co.traynor.privategallery.core.privateapps.*
import uk.co.traynor.privategallery.core.camera.CameraOptions
import uk.co.traynor.privategallery.core.camera.VaultCaptureAccess
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.evidence.EvidenceChronology68
import uk.co.traynor.privategallery.core.evidence.EvidenceCollectionConnection
import uk.co.traynor.privategallery.core.evidence.EvidenceCollectionSync
import uk.co.traynor.privategallery.core.evidence.EvidenceCredentials
import uk.co.traynor.privategallery.core.evidence.EvidenceReview68
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryPreset
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore

/** Reviewed release-control instrumentation for original-key Code 69 to Code 71 upgrade.
 * Captured and hash-checked before frozen application checkout; never modifies baseline app.
 */
class UpgradeFixtureTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val proof get() = File(context.filesDir, "synthetic-upgrade-proof.json")
    private val arguments = InstrumentationRegistry.getArguments()
    private val baselineCode get() = checkNotNull(arguments.getString("baselineCode")).toInt()
    private val candidateCode get() = checkNotNull(arguments.getString("candidateCode")).toInt()
    private fun guard() {
        check(context.packageName == "uk.co.traynor.privategallery.hiddenprototype")
        check(baselineCode == 69 && candidateCode == 71)
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun encryptedHashes(): JSONObject = JSONObject().also { result ->
        // AndroidX rewrites these exact runtime files with PackageInfo.lastUpdateTime
        // and compilation status after an APK update. No vault subpath is excluded.
        val runtimeProfileFiles = setOf(
            File(context.filesDir, "profileinstaller_profileWrittenFor_lastUpdateTime.dat"),
            File(context.filesDir, "profileInstalled")
        )
        context.filesDir.walkTopDown().filter { it.isFile && it != proof && it !in runtimeProfileFiles }.forEach {
            result.put(it.relativeTo(context.filesDir).path, hash(it.readBytes()))
        }
    }
    private fun hiddenHashes(): JSONObject = JSONObject().also { result ->
        context.noBackupFilesDir.walkTopDown().filter {it.isFile}.forEach {
            result.put(it.relativeTo(context.noBackupFilesDir).path,hash(it.readBytes()))
        }
    }
    private val hiddenAttemptsPath="hidden-v1-default/attempts-v1"
    private var verifiedHiddenAttemptsSha256:String?=null

    private fun authenticatedAttempts():ByteArray =
        checkNotNull(HiddenProtectedRecord(context,"default","attempts-v1").read())

    private fun hiddenSession(create:Boolean):HiddenSession {
        val store=HiddenCredentialStore(context)
        val key=if(create)store.create("905184".toCharArray()) else store.unlock("905184".toCharArray())
        if(!create)try {
            val actual=authenticatedAttempts()
            val expected=HiddenPinThrottle.empty().encode()
            try {
                assertArrayEquals("Successful Hidden PIN resets the authenticated attempt record",expected,actual)
                verifiedHiddenAttemptsSha256=hash(File(context.noBackupFilesDir,hiddenAttemptsPath).readBytes())
            } finally {actual.fill(0);expected.fill(0)}
        } catch(failure:Throwable) {key.fill(0);throw failure}
        return HiddenSession(android.os.SystemClock::elapsedRealtime).also {assertTrue(it.complete(it.beginAuthentication(),key))}
    }
    /** Encrypted browser metadata; persistent synthetic CookieManager login is checked separately below. */
    private fun primaryBrowser(key:ByteArray,seed:Boolean) {
        if(baselineCode<45)return
        val url="https://upgrade.invalid/primary"
        val sessions=uk.co.traynor.privategallery.core.browser.v2.EncryptedBrowserSessionStore(File(context.filesDir,"browser-session"),key)
        val history=uk.co.traynor.privategallery.core.browser.v2.EncryptedBrowserHistoryStore(File(context.filesDir,"browser-history"),key)
        val bookmarks=uk.co.traynor.privategallery.core.browser.EncryptedBookmarkStore(File(context.filesDir,"browser-bookmarks"),key)
        if(seed) {
            val tab=uk.co.traynor.privategallery.core.browser.v2.BrowserTab(id="upgrade-tab",url=url,title="Upgrade tab",desktopSite=true)
            sessions.save(uk.co.traynor.privategallery.core.browser.v2.BrowserSessionSnapshot(listOf(tab),tab.id))
            history.add("Upgrade history",url);bookmarks.add("Upgrade bookmark",url)
        }
        val session=checkNotNull(sessions.load());assertEquals("upgrade-tab",session.selectedTabId)
        val tab=session.tabs.single();assertEquals(url,tab.url);assertEquals("Upgrade tab",tab.title);assertTrue(tab.desktopSite)
        assertEquals(url,history.list().single().url);assertEquals("Upgrade history",history.list().single().title)
        assertEquals(url,bookmarks.list().single().url);assertEquals("Upgrade bookmark",bookmarks.list().single().title)
    }
    private fun hiddenBrowser(session:HiddenSession,seed:Boolean) {
        if(baselineCode<45)return
        for(vpn in listOf(true,false)) {
            val library=uk.co.traynor.privategallery.core.browser.hidden.HiddenBrowserLibrary(context.noBackupFilesDir,session,session.authority(),vpn)
            val url="https://upgrade.invalid/"+if(vpn)"hidden-vpn" else "hidden-tor"
            if(seed){library.saveHistory(vpn);library.bookmark(url,"Hidden upgrade",1000);library.visit(url,"Hidden visit",1000)}
            assertEquals(listOf(url),library.bookmarks());assertEquals(vpn,library.savesHistory())
            assertEquals(if(vpn)listOf(url) else emptyList<String>(),library.history())
            assertEquals("Hidden upgrade",library.snapshot().bookmarks.single().title)
        }
    }
    /** Baseline-compatible v2 definitions: all old fields, order and owner survive read/restart.
     * No candidate-only API is referenced, so instrumentation compiles against the actual code50 source.
     * This helper performs no candidate write: the existing full ciphertext checks detect eager migration.
     */
    private fun privateApps(store:PrivateAppStore,seed:Boolean) {
        val owner=store.owner()
        val label=when(owner) {
            PrivateAppStore.Owner.PRIMARY -> "Primary"
            PrivateAppStore.Owner.HIDDEN_VPN -> "Hidden VPN"
            PrivateAppStore.Owner.HIDDEN_TOR -> "Hidden Tor"
        }
        val origin=if(owner==PrivateAppStore.Owner.HIDDEN_TOR)"http://"+"a".repeat(56)+".onion" else "https://upgrade.invalid"
        val icon="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII="
        fun id(index:Int)=java.util.UUID.nameUUIDFromBytes("upgrade50:$label:$index".toByteArray(Charsets.UTF_8)).toString()
        val expected=listOf(
            PrivateAppCatalog.App(id(1),"Upgrade $label PWA","$origin/app/home?upgrade=49","$origin/app/","standalone",icon,true,false,true,false,"Synthetic baseline PWA discovery report"),
            PrivateAppCatalog.App(id(2),"Upgrade $label Website","https://upgrade.invalid/site/${owner.name.lowercase()}","","browser","",false,true,false,true,"Synthetic baseline website fallback report")
        )
        if(seed) {
            assertTrue("Disposable baseline catalogue starts empty for $label",store.read().apps.isEmpty())
            for(app in expected)store.update {catalog ->catalog.upsert(app)}
        }
        val actual=store.read()
        assertEquals("Preserve $label catalogue transport owner",owner==PrivateAppStore.Owner.HIDDEN_TOR,actual.tor)
        assertEquals("Preserve $label private app IDs/order/name/start/scope/display/icon/pin/address/PIN flags",expected,actual.apps)
        if(seed) {
            val encoded=actual.encode()
            try {assertEquals("Original code50 seed uses catalogue format2",2,java.io.DataInputStream(ByteArrayInputStream(encoded)).readInt())}
            finally {encoded.fill(0)}
        }
    }
    private fun primaryPrivateApps(key:ByteArray,seed:Boolean) {
        val copy=key.copyOf()
        try {
            val store=PrimaryPrivateAppStore(File(context.filesDir,"private-apps"),copy,{true},{commit ->commit()})
            assertEquals(PrivateAppStore.Owner.PRIMARY,store.owner())
            privateApps(store,seed)
        } finally {copy.fill(0)}
    }
    private fun hiddenPrivateApps(session:HiddenSession,seed:Boolean) {
        for(tor in listOf(false,true)) {
            val store=HiddenPrivateAppStore(context.noBackupFilesDir,session,session.authority(),tor)
            assertEquals(if(tor)PrivateAppStore.Owner.HIDDEN_TOR else PrivateAppStore.Owner.HIDDEN_VPN,store.owner())
            privateApps(store,seed)
        }
    }
    /** Candidate-only commands are looked up reflectively: baseline instrumentation keeps its code50 ABI. */
    private fun privateAppStyleRoundTrip(open:()->PrivateAppStore) {
        val original=open().read().apps
        assertEquals(2,original.size)
        val style=PrivateAppCatalog.App::class.java.getMethod("appStyle")
        val report=PrivateAppCatalog.App::class.java.getMethod("discoveryReport")
        val command=PrivateAppCommands::class.java.getMethod("execute",PrivateAppStore::class.java,
            Int::class.javaPrimitiveType,ByteArray::class.java,String::class.java)
        val browser=PrivateAppCommands::class.java.getField("BROWSER_STYLE").getInt(null)
        val app=PrivateAppCommands::class.java.getField("APP_STYLE").getInt(null)
        assertEquals(13,browser);assertEquals(12,app)
        assertEquals(listOf(false,true),original.map {style.invoke(it)})
        fun unchanged(actual:List<PrivateAppCatalog.App>) {
            assertEquals(original.map {it.id()},actual.map {it.id()})
            for((before,after) in original.zip(actual)) {
                for(field in listOf("id","name","startUrl","scope","display","icon","pinned","addressBar","requireAuthentication")) {
                    val read=PrivateAppCatalog.App::class.java.getMethod(field)
                    assertEquals("Style write preserves original $field",read.invoke(before),read.invoke(after))
                }
                assertEquals("Style write preserves discovery report",report.invoke(before),report.invoke(after))
            }
        }
        command.invoke(null,open(),app,null,original.first().id())
        val browserState=open().read().apps
        unchanged(browserState)
        assertEquals(true,style.invoke(browserState.first()))
        assertEquals(true,style.invoke(browserState.last()))
        command.invoke(null,open(),browser,null,original.first().id())
        val restored=open().read().apps
        unchanged(restored)
        assertEquals("Restoring app style preserves full decoded definitions",original,restored)
        // Candidate-only atomic reorder is resolved reflectively so baseline50 instrumentation compiles.
        val moveTo=PrivateAppCommands::class.java.getField("MOVE_TO").getInt(null)
        assertEquals(14,moveTo)
        fun move(index:Int) {
            val payload=java.nio.ByteBuffer.allocate(4).putInt(index).array()
            try {command.invoke(null,open(),moveTo,payload,original.first().id())}
            finally {payload.fill(0)}
        }
        move(1)
        assertEquals("Atomic reorder preserves every original definition",original.reversed(),open().read().apps)
        move(0)
        assertEquals("Restoring order preserves all eleven fields",original,open().read().apps)
    }
    private fun privateAppCiphertext(path:String)=path=="private-apps/primary-private-apps-v1.enc" || path.startsWith("hidden-private-apps-v1/")
    private fun privateAppCiphertexts():JSONObject = JSONObject().also {out ->
        for((root,label) in listOf(context.filesDir to "files",context.noBackupFilesDir to "hiddenFiles")) {
            val hashes=JSONObject()
            root.walkTopDown().filter {it.isFile && privateAppCiphertext(it.relativeTo(root).path)}.forEach {
                hashes.put(it.relativeTo(root).path,hash(it.readBytes()))
            }
            assertEquals("Expected independent encrypted private-app records for $label",if(label=="files")1 else 2,hashes.length())
            out.put(label,hashes)
        }
    }
    private fun rewriteCandidatePrivateAppStyles() {
        val key=PinVaultKeyStore(context).unlock("738291".toCharArray())
        try {
            privateAppStyleRoundTrip {PrimaryPrivateAppStore(File(context.filesDir,"private-apps"),key,{true},{commit ->commit()})}
        } finally {key.fill(0)}
        val hidden=hiddenSession(false)
        try {
            for(tor in listOf(false,true))privateAppStyleRoundTrip {HiddenPrivateAppStore(context.noBackupFilesDir,hidden,hidden.authority(),tor)}
        } finally {hidden.lock()}
    }
    private fun expectedCiphertext(before:JSONObject,field:String,path:String,restarted:Boolean,afterHiddenAuthentication:Boolean=false):String {
        // Successful authentication deliberately reseals the attempt state with a fresh IV.
        // Before authentication compare the exact seed or pre-restart ciphertext.
        if(field=="hiddenFiles"&&path==hiddenAttemptsPath) {
            if(afterHiddenAuthentication)return checkNotNull(verifiedHiddenAttemptsSha256)
            if(restarted)return before.getString("candidateHiddenAttemptsSha256")
        }
        if(restarted && privateAppCiphertext(path))return before.getJSONObject("candidatePrivateAppFiles").getJSONObject(field).getString(path)
        if(restarted && path.endsWith("presentation-v1/state.enc"))return before.getJSONObject(if(field=="files")"candidatePresentationFiles" else "candidateHiddenPresentationFiles").getString(path)
        return before.getJSONObject(field).getString(path)
    }
    private fun browserConfiguration(seed:Boolean) {
        val prefs=context.getSharedPreferences("private-gallery-settings",0)
        if(seed)check(prefs.edit().putString("browser-search-engine","DUCKDUCKGO")
            .putBoolean("browser-clear-data-on-lock",false).putBoolean("browser-save-history",true)
            .putBoolean("browser-content-blocking",false).putBoolean("browser-vpn-auto-connect",true)
            .putBoolean("browser-vpn-required",true).commit())
        assertEquals("DUCKDUCKGO",prefs.getString("browser-search-engine",null))
        for((name,expected) in mapOf("browser-clear-data-on-lock" to false,"browser-save-history" to true,
            "browser-content-blocking" to false,"browser-vpn-auto-connect" to true,"browser-vpn-required" to true)) {
            assertTrue("Preserve explicit browser preference $name",prefs.contains(name))
            assertEquals("Preserve browser preference $name",expected,prefs.getBoolean(name,!expected))
        }
        // Synthetic persistent website state only; no network request or live credential.
        val origin="https://upgrade.invalid/"
        var cookies:android.webkit.CookieManager?=null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {cookies=android.webkit.CookieManager.getInstance()}
        val manager=checkNotNull(cookies)
        if(seed) {
            val completed=java.util.concurrent.CountDownLatch(1)
            val accepted=java.util.concurrent.atomic.AtomicBoolean(false)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                manager.setCookie(origin,"upgrade178=synthetic-preserved; Secure; SameSite=Lax; Max-Age=86400; Path=/") {ok ->accepted.set(ok);completed.countDown()}
            }
            assertTrue("Persistent synthetic cookie callback completes",completed.await(10,java.util.concurrent.TimeUnit.SECONDS))
            assertTrue("Persistent synthetic cookie accepted",accepted.get())
            // Persistent synthetic login marker: no account credentials and no network request.
            val loginCompleted=java.util.concurrent.CountDownLatch(1)
            val loginAccepted=java.util.concurrent.atomic.AtomicBoolean(false)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                manager.setCookie(origin,"nova1711_login=synthetic-persistent-auth; HttpOnly; Secure; SameSite=Lax; Max-Age=604800; Path=/") {ok ->loginAccepted.set(ok);loginCompleted.countDown()}
            }
            assertTrue("Persistent synthetic login callback completes",loginCompleted.await(10,java.util.concurrent.TimeUnit.SECONDS))
            assertTrue("Persistent synthetic login accepted",loginAccepted.get())
            InstrumentationRegistry.getInstrumentation().runOnMainSync {manager.flush()}
        }
        var retained:String?=null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {retained=manager.getCookie(origin)}
        assertTrue("Existing Primary browser cookie survives upgrade/restart",retained.orEmpty().split(';').any {it.trim()=="upgrade178=synthetic-preserved"})
        assertTrue("Existing persistent HttpOnly browser login survives upgrade/restart",retained.orEmpty().split(';').any {it.trim()=="nova1711_login=synthetic-persistent-auth"})
    }
    /** Configuration only: no camera hardware or unrelated capture regression. */
    private fun cameraConfiguration(key:ByteArray?,hidden:HiddenSession?,seed:Boolean) {
        val access=VaultCaptureAccess(context,hidden,{key?.copyOf()},{true})
        val expected=if(hidden==null)CameraOptions(photoQuality=85,videoQuality=0,grid=true,timer=3,vibration=true,defaultVideo=true)
            else CameraOptions(photoQuality=100,videoQuality=2,grid=true,timer=10,vibration=true,defaultVideo=true)
        try {
            if(seed){access.options(expected);access.audio(true)}
            assertEquals("Preserve independent encrypted ${access.domain} camera options",expected,access.options())
            assertTrue("Preserve encrypted ${access.domain} camera audio consent",access.audio())
        } finally {access.revoke()}
    }

    /** Candidate-only APIs stay reflective: this fixture compiles against archived code50 ABI.
     * Adds only encrypted evidence metadata referencing already-verified original image bytes.
     * Synthetic OCR is labelled as such; this is persistence/upgrade proof, not OCR recognition.
     */
    private fun evidenceType(name:String):Class<*> = Class.forName(
        "uk.co.traynor.privategallery.core.evidence.$name",true,context.classLoader)
    private fun evidenceCall(target:Any,name:String,vararg args:Any):Any =
        target.javaClass.methods.single {it.name==name&&it.parameterCount==args.size}.invoke(target,*args)
    private fun evidenceRecord(name:String,count:Int,vararg args:Any):Any =
        evidenceType("EvidenceCatalog\$$name").constructors.single {it.parameterCount==count}.newInstance(*args)
    private fun evidenceSnapshots():JSONObject = JSONObject().also { result ->
        for ((base,label,owner) in listOf(
            Triple(context.filesDir,"files","primary"),
            Triple(context.noBackupFilesDir,"hiddenFiles","hidden"))) {
            val suffix = if (owner == "primary") "17901" else "17902"
            val folder=File(base,"evidence-state/evidence-179-$owner")
            val records=folder.walkTopDown().filter {it.isFile}.toList()
            assertEquals("Exactly one encrypted $owner evidence record; no plaintext/stages",1,records.size)
            val record=records.single()
            assertEquals("metadata-00000000-0000-4000-8000-0000000$suffix.enc",record.name)
            val bytes=record.readBytes()
            try {
                assertFalse("No evidence annotations in plaintext storage",String(bytes,Charsets.UTF_8).contains("UPGRADE-EVIDENCE-"))
                result.put(label.toString(),JSONObject().put(record.relativeTo(base).path,hash(bytes)))
            } finally {bytes.fill(0)}
        }
    }
    private fun evidenceOwnerState(label:String,root:File,factory:Any,item:VaultItem,expectedHash:String,seed:Boolean):String {
        val ownerType=evidenceType("EvidenceCatalog\$Owner")
        val owner=ownerType.getField(label).get(null)
        val storeType=evidenceType("EvidenceEncryptedStore")
        if(seed) {
            val folder=File(root,"evidence-state/evidence-179-${label.lowercase()}")
            assertFalse("Fresh disposable Code 69 evidence fixture starts empty",folder.walkTopDown().any {it.isFile})
        }
        val store=storeType.getConstructor(File::class.java,ownerType,evidenceType("EvidenceEncryptedStore\$LeaseFactory"))
            .newInstance(File(root,"evidence-state"),owner,factory)
        val recordId=java.util.UUID.nameUUIDFromBytes("upgrade179:$label:evidence".toByteArray()).toString()
        val ocrId=java.util.UUID.nameUUIDFromBytes("upgrade179:$label:ocr".toByteArray()).toString()
        val incidentId=java.util.UUID.nameUUIDFromBytes("upgrade179:$label:incident".toByteArray()).toString()
        val eventId=java.util.UUID.nameUUIDFromBytes("upgrade179:$label:event".toByteArray()).toString()
        val analysisId=java.util.UUID.nameUUIDFromBytes("upgrade179:$label:analysis".toByteArray()).toString()
        val raw="UPGRADE-EVIDENCE-$label synthetic OCR (unverified), visible time14:05"
        val corrected="2026-10-31\nAlice: UPGRADE-EVIDENCE-$label owner-corrected transcript 14:05"
        var catalog=evidenceCall(store,"read")
        if(seed) {
            assertEquals("Fresh Code 69 fixture catalogue starts empty",0,(evidenceCall(catalog,"evidence") as List<*>).size)
            val dimension = if (label == "PRIMARY") 64 else 32
            val metadata=evidenceRecord("Metadata",9,item.displayName,0L,0L,item.importedAtEpochMillis,0L,dimension,dimension,
                mapOf("provenance" to "Synthetic upgrade fixture metadata; timestamps are not screenshot facts"),false)
            val record=evidenceRecord("Evidence",14,recordId,item.id,"UPGRADE-EVIDENCE-$label",item.mimeType,expectedHash,
                item.plaintextSize,1000L,metadata,"Preserved original reference","Owner synthetic note",listOf("upgrade179"),
                listOf("Explicit synthetic participant"),emptyList<Any>(),emptyList<Any>())
            val time=evidenceRecord("ExtractedTime",5,0,"14:05",0L,"Synthetic OCR quote; uncertain date",0.25f)
            val extraction=evidenceRecord("Ocr",12,ocrId,1001L,"SYNTHETIC UPGRADE FIXTURE (not recognition)",0,dimension,dimension,raw,
                0.25f,emptyList<Any>(),listOf(time),1,false)
            val event=evidenceRecord("Event",6,eventId,listOf(recordId),0L,"USER","Uncertain synthetic event","Owner-entered; no inferred timestamp")
            val incident=evidenceRecord("Incident",12,incidentId,"UPGRADE-EVIDENCE-$label incident",0L,0L,"Synthetic incident","Synthetic owner note",
                listOf("upgrade179"),listOf(recordId),listOf(event),false,false,emptyList<Any>())
            val update=java.util.function.UnaryOperator<Any> { before ->
                var next=evidenceCall(before,"upsertEvidence",record,1000L,"REGISTER_UPGRADE_EVIDENCE")
                next=evidenceCall(next,"saveOcr",recordId,extraction,1001L)
                next=evidenceCall(next,"reviseOcr",recordId,ocrId,corrected,1002L)
                next=evidenceCall(next,"upsertIncident",incident,1003L,"CREATE_UPGRADE_INCIDENT")
                val fingerprint=evidenceRecord("SourceFingerprint",5,item.id,item.mimeType,expectedHash,item.plaintextSize,false)
                val reference=evidenceRecord("SourceReference",2,recordId,fingerprint)
                val analysis=evidenceRecord("Analysis",12,analysisId,1004L,"SYNTHETIC (not a network AI response)","upgrade persistence",
                    hash(raw.toByteArray()),listOf(recordId),"Synthetic advisory; source reference preserved",0L,0L,false,listOf(fingerprint),listOf(reference))
                evidenceCall(next,"saveAnalysis",analysis,1004L)
            }
            catalog=storeType.getMethod("update",java.util.function.UnaryOperator::class.java).invoke(store,update)
        }
        // Reopen through the production encrypted store even within the first candidate process.
        catalog=evidenceCall(store,"read")
        assertEquals(owner,evidenceCall(catalog,"owner"))
        val records=evidenceCall(catalog,"evidence") as List<*>
        assertEquals(1,records.size)
        val record=checkNotNull(records.single())
        assertEquals(recordId,evidenceCall(record,"id"));assertEquals(item.id,evidenceCall(record,"sourceId"))
        assertEquals(expectedHash,evidenceCall(record,"sha256"));assertEquals(item.plaintextSize,(evidenceCall(record,"size") as Number).toLong())
        val metadata=evidenceCall(record,"metadata")
        assertEquals(item.displayName,evidenceCall(metadata,"sourceName"))
        assertEquals(item.importedAtEpochMillis,(evidenceCall(metadata,"dateAddedAt") as Number).toLong())
        for(field in listOf("fileCreatedAt","fileModifiedAt","captureAt"))assertEquals(0L,(evidenceCall(metadata,field) as Number).toLong())
        for(field in listOf("width","height"))assertEquals(if(label=="PRIMARY")64 else 32,(evidenceCall(metadata,field) as Number).toInt())
        assertEquals(false,evidenceCall(metadata,"vaultOnly"))
        assertEquals(mapOf("provenance" to "Synthetic upgrade fixture metadata; timestamps are not screenshot facts"),evidenceCall(metadata,"embedded"))
        assertEquals(corrected,evidenceCall(record,"transcript"))
        val extracts=evidenceCall(record,"ocr") as List<*>
        assertEquals(raw,evidenceCall(checkNotNull(extracts.single()),"text"))
        assertEquals(0L,(evidenceCall(checkNotNull((evidenceCall(checkNotNull(extracts.single()),"times") as List<*>).single()),"epochMillis") as Number).toLong())
        assertEquals(1,(evidenceCall(record,"corrections") as List<*>).size)
        assertEquals(1,(evidenceCall(catalog,"incidents") as List<*>).size)
        val timeline=evidenceCall(catalog,"timeline",incidentId) as List<*>
        assertEquals(1,timeline.size);assertEquals("USER",evidenceCall(checkNotNull(timeline.single()),"provenance"))
        assertEquals(0L,(evidenceCall(checkNotNull(timeline.single()),"timestamp") as Number).toLong())
        assertEquals(1,(evidenceCall(catalog,"search",corrected,0L,0L,true) as List<*>).size)
        assertEquals(0,(evidenceCall(catalog,"search",("UPGRADE-EVIDENCE-" + if (label == "PRIMARY") "HIDDEN" else "PRIMARY"),0L,0L,true) as List<*>).size)
        val analysis=checkNotNull((evidenceCall(catalog,"analyses") as List<*>).single())
        val reference=checkNotNull((evidenceCall(analysis,"sourceReferences") as List<*>).single())
        assertEquals(recordId,evidenceCall(reference,"evidenceId"))
        val fingerprint=evidenceCall(reference,"fingerprint")
        assertEquals(item.id,evidenceCall(fingerprint,"sourceId"));assertEquals(expectedHash,evidenceCall(fingerprint,"sha256"))
        assertEquals(item.mimeType,evidenceCall(fingerprint,"mime"));assertEquals(item.plaintextSize,(evidenceCall(fingerprint,"size") as Number).toLong())
        assertEquals(false,evidenceCall(fingerprint,"vaultOnly"));assertEquals(false,evidenceCall(analysis,"applied"))
        assertEquals(5,(evidenceCall(catalog,"audit") as List<*>).size)
        val encoded=evidenceCall(catalog,"encode") as ByteArray
        return try {hash(encoded)}finally{encoded.fill(0)}
    }
    /** Seed with existing Code 68 production APIs. Reopen the exact same sidecars after upgrade and restart. */
    private fun connectedEvidence68(before:JSONObject,seed:Boolean) {
        val key=PinVaultKeyStore(context).unlock("738291".toCharArray())
        val hidden=hiddenSession(false)
        val identity=Any()
        try {
            EvidenceAccess.primary(context,identity,{identity},{true},{key.copyOf()},Any()).use {primary ->
                EvidenceAccess.hidden(context,hidden){true}.use {secret ->
                    val inputs=listOf(
                        Triple(primary,before.getString("collection"),"Synthetic upgrade favourites"),
                        Triple(secret,before.getString("hiddenCollection"),"Hidden upgrade favourites"),
                    )
                    for((access,collectionId,collectionName) in inputs) {
                        val label=access.owner.name
                        val sync=EvidenceCollectionSync(access)
                        val expected=listOf(EvidenceCollectionConnection(collectionId,collectionName))
                        val otherCollection=inputs.single {it.first!==access}.second
                        assertTrue("Another owner's collection cannot be resolved",sync.available().none {it.id==otherCollection})
                        if(seed) {
                            assertTrue(sync.connections().isEmpty())
                            assertEquals(expected,sync.connect(collectionId))
                        }
                        assertEquals("Preserve the originating owner's encrypted collection connection",expected,sync.connections())

                        val zone=ZoneId.of("Europe/London")
                        val catalog=access.store.read()
                        val raw=EvidenceChronology68.analyse(catalog.evidence(),conversationZone=zone)
                        val message=raw.messages.single {it.text.contains("UPGRADE-EVIDENCE-$label owner-corrected transcript")}
                        val timestamp=Instant.parse(if(label=="PRIMARY")"2026-10-31T14:05:00Z" else "2026-10-31T14:06:00Z")
                        EvidenceReview68(context,access).use {review ->
                            if(seed) {
                                review.setConversationZone(zone)
                                review.confirm(message.locations,timestamp.toEpochMilli())
                            }
                            assertEquals("Preserve the owner's conversation time zone",zone,review.conversationZone())
                            assertEquals("Preserve the manual chronology correction for the exact OCR revision",
                                message.locations.associateWith {timestamp},review.confirmed())
                            val reviewed=EvidenceChronology68.analyse(catalog.evidence(),conversationZone=zone,confirmed=review.confirmed())
                                .messages.single {it.text==message.text}
                            assertEquals(timestamp,reviewed.timestamp.instant)
                            assertEquals(EvidenceChronology68.Confidence.CONFIRMED,reviewed.timestamp.confidence)
                        }

                        // Synthetic distinct credentials are never used for network I/O.
                        val token="synthetic-evidence-upgrade-$label"
                        val credentials=EvidenceCredentials(context,access)
                        if(seed) {
                            assertFalse(credentials.isConfigured())
                            val temporary=token.toCharArray()
                            try {credentials.setup(temporary)} finally {temporary.fill('\u0000')}
                        }
                        assertTrue(credentials.isConfigured())
                        val expectedToken=token.toByteArray()
                        val actualToken=checkNotNull(uk.co.traynor.privategallery.core.editor.AiCredentialStore(context)
                            .read("evidence-openai-179-"+label.lowercase(java.util.Locale.ROOT)))
                        try {
                            assertTrue("Preserve the originating owner's Evidence AI credential",
                                MessageDigest.isEqual(expectedToken,actualToken))
                        } finally {expectedToken.fill(0);actualToken.fill(0)}
                    }
                    val hiddenSync=EvidenceCollectionSync(secret)
                    hidden.lock()
                    assertTrue("Hidden connections retire when the Hidden session locks",runCatching {hiddenSync.connections()}.isFailure)
                    assertEquals(listOf(EvidenceCollectionConnection(before.getString("collection"),"Synthetic upgrade favourites")),
                        EvidenceCollectionSync(primary).connections())
                }
            }
        } finally {hidden.lock();key.fill(0)}
    }

    private fun candidateEvidence(before:JSONObject,restarted:Boolean,baselineSeed:Boolean=false) {
        val authorities=evidenceType("EvidenceAuthorities")
        val key=PinVaultKeyStore(context).unlock("738291".toCharArray())
        val hidden=hiddenSession(false)
        try {
            val identity=Any();val gate=Any()
            val primary=authorities.getMethod("primary",Any::class.java,java.util.function.Supplier::class.java,
                java.util.function.BooleanSupplier::class.java,java.util.function.Supplier::class.java,Any::class.java)
                .invoke(null,identity,java.util.function.Supplier<Any>{identity},java.util.function.BooleanSupplier{true},
                    java.util.function.Supplier<ByteArray>{key.copyOf()},gate)
            val secret=authorities.getMethod("hidden",HiddenSession::class.java,HiddenSession.Authority::class.java,java.util.function.BooleanSupplier::class.java)
                .invoke(null,hidden,hidden.authority(),java.util.function.BooleanSupplier{true})
            val primaryItem=AndroidVaultRepository(context,key).items().single{it.mimeType=="image/png"}
            val hiddenItem=HiddenRepository(context,hidden).items().single{it.mimeType=="image/png"}
            val primaryHash=evidenceOwnerState("PRIMARY",context.filesDir,primary,primaryItem,before.getJSONObject("media").getString(primaryItem.id),baselineSeed)
            val hiddenHash=evidenceOwnerState("HIDDEN",context.noBackupFilesDir,secret,hiddenItem,before.getJSONObject("hiddenMedia").getString(hiddenItem.id),baselineSeed)
            val current=evidenceSnapshots()
            if(!baselineSeed) {
                assertEquals(before.getString("primaryEvidenceCatalogSha256"),primaryHash)
                assertEquals(before.getString("hiddenEvidenceCatalogSha256"),hiddenHash)
                val expected=before.getJSONObject("candidateEvidenceFiles")
                for(field in listOf("files","hiddenFiles")) {
                    val wanted=expected.getJSONObject(field);val actual=current.getJSONObject(field)
                    assertEquals(wanted.length(),actual.length())
                    wanted.keys().forEach {path->assertEquals("Existing Code 69 owner-encrypted evidence survives upgrade/restart unchanged",wanted.getString(path),actual.getString(path))}
                }
            } else before.put("primaryEvidenceCatalogSha256",primaryHash).put("hiddenEvidenceCatalogSha256",hiddenHash).put("candidateEvidenceFiles",current)
            // Recheck every original encrypted file after additive writes, including the
            // attempt ciphertext verified after this authentication and before evidence reads.
            if(!baselineSeed)for((root,field) in listOf(context.filesDir to "files",context.noBackupFilesDir to "hiddenFiles")) {
                before.getJSONObject(field).keys().forEach {path->assertEquals("Additive evidence preserves original $field/$path",expectedCiphertext(before,field,path,true,afterHiddenAuthentication=true),hash(File(root,path).readBytes()))}
            }
            assertEquals(2,AndroidVaultRepository(context,key).items().size)
            assertEquals(2,HiddenRepository(context,hidden).items().size)
        } finally {hidden.lock();key.fill(0)}
    }

    /** Compiles against the actual signed code64 model; verifies code65 upgrade without rewriting ciphertext. */
    private fun homePreferences(seed:Boolean) {
        val key=PinVaultKeyStore(context).unlock("738291".toCharArray())
        val hidden=hiddenSession(false)
        val identity=Any()
        val primary=uk.co.traynor.privategallery.core.evidence.EvidenceAccess.primary(context,identity,{identity},{true},{key.copyOf()},Any())
        val secret=uk.co.traynor.privategallery.core.evidence.EvidenceAccess.hidden(context,hidden){true}
        try {
            val p=uk.co.traynor.privategallery.core.home.HomePreferencesStore(primary,true)
            val h=uk.co.traynor.privategallery.core.home.HomePreferencesStore(secret,true)
            val camera=uk.co.traynor.privategallery.core.home.HomeLayout.Shortcut.CAMERA
            val browser=uk.co.traynor.privategallery.core.home.HomeLayout.Shortcut.BROWSER
            val settings=uk.co.traynor.privategallery.core.home.HomeLayout.Shortcut.SETTINGS
            val gallery=uk.co.traynor.privategallery.core.home.HomeLayout.Shortcut.GALLERY
            val collections=uk.co.traynor.privategallery.core.home.HomeLayout.Shortcut.COLLECTIONS
            val apps=uk.co.traynor.privategallery.core.home.HomeLayout.Shortcut.PRIVATE_APPS
            if(seed) {
                p.write(uk.co.traynor.privategallery.core.home.HomePreferences(
                    uk.co.traynor.privategallery.core.home.HomeLayout.defaults().move(settings,0).hide(browser).pin(camera),false,true,false,listOf(camera)))
                h.write(uk.co.traynor.privategallery.core.home.HomePreferences(
                    uk.co.traynor.privategallery.core.home.HomeLayout.defaults().move(collections,0).hide(gallery).pin(apps),true,false,true,listOf(browser)))
            }
            val primaryValue=p.read();val hiddenValue=h.read()
            assertFalse(primaryValue.startupHome);assertTrue(primaryValue.compact);assertFalse(primaryValue.largeIcons)
            assertTrue(hiddenValue.startupHome);assertFalse(hiddenValue.compact);assertTrue(hiddenValue.largeIcons)
            assertEquals(listOf(camera),primaryValue.recent);assertEquals(listOf(browser),hiddenValue.recent)
            assertEquals(setOf(camera),primaryValue.layout.favourites());assertEquals(setOf(apps),hiddenValue.layout.favourites())
            fun legacyNames(value:uk.co.traynor.privategallery.core.home.HomePreferences)=value.layout.visible().map{it.name}.filter{it !in setOf("VAULT","FAVOURITES","TOR_BROWSER")}
            assertEquals(listOf("SETTINGS","GALLERY","PRIVATE_APPS","EVIDENCE_LOCKER","CAMERA","COLLECTIONS","RECENTLY_DELETED"),legacyNames(primaryValue))
            assertEquals(listOf("COLLECTIONS","BROWSER","PRIVATE_APPS","EVIDENCE_LOCKER","CAMERA","RECENTLY_DELETED","SETTINGS"),legacyNames(hiddenValue))
            if(!seed)assertEquals("Separate Tor app added to the migrated model",11,uk.co.traynor.privategallery.core.home.HomeLayout.Shortcut.values().size)
        } finally {primary.close();secret.close();hidden.lock();key.fill(0)}
    }

    /** Home2 code64 APIs seed real owner-encrypted records. Candidate reads are deliberately
     * raw: opening the UI model may perform an explicit theme migration, outside preservation.
     * All these ciphertext files remain inside the existing complete filesystem assertions.
     */
    private fun home2State(before:JSONObject,seed:Boolean) {
        val key=PinVaultKeyStore(context).unlock("738291".toCharArray())
        val hidden=hiddenSession(false);val identity=Any()
        val primary=uk.co.traynor.privategallery.core.evidence.EvidenceAccess.primary(context,identity,{identity},{true},{key.copyOf()},Any())
        val secret=uk.co.traynor.privategallery.core.evidence.EvidenceAccess.hidden(context,hidden){true}
        try {
            for((owner,access) in listOf("primary" to primary,"hidden" to secret)) {
                uk.co.traynor.privategallery.core.home2.SpaceContext(context,access).use { space ->
                    val prefs=space.getSharedPreferences("nova_preview_v1",0)
                    val widgets=uk.co.traynor.privategallery.home2.WidgetData(space)
                    val mind=uk.co.traynor.privategallery.home2.MindData(space)
                    val theme=if(owner=="primary")"light" else "dark"
                    val note="Synthetic $owner upgrade note: keep every word"
                    val journal="Synthetic $owner private journal"
                    val mood=if(owner=="primary")"calm" else "hopeful"
                    if(seed) {
                        val wallpaper="Synthetic $owner encrypted wallpaper bytes".toByteArray()
                        val appliedWallpaper=try {uk.co.traynor.privategallery.core.home2.WallpaperStore(space).apply(
                            uk.co.traynor.privategallery.home2.Wallpaper("Dusk",null,1.5f,.2f,-.3f,4f,.25f,17L),wallpaper)
                        }finally{wallpaper.fill(0)}
                        val wallpaperId=checkNotNull(appliedWallpaper.image)
                        val model=uk.co.traynor.privategallery.home2.PreviewData(space,null)
                        model.setTheme(theme)
                        assertNull(model.error)
                        assertEquals(theme,model.state.theme)
                        model.change { profile -> profile.copy(
                            pages=listOf(listOf("mind","vault","folder:upgrade"),listOf("camera","widgets")),
                            dock=listOf("browser-v1","store","photos"),
                            folders=listOf(uk.co.traynor.privategallery.home2.AppFolder("folder:upgrade","$owner tools",listOf("editor","ai-edit"))),
                            drawerOrder=listOf("folder:upgrade","mind","vault","camera"),columns=5,iconSize=68,
                            wallpaper=appliedWallpaper) }
                        assertNull(model.error)
                        assertTrue(widgets.save(owner,listOf(uk.co.traynor.privategallery.home2.WidgetSlot(
                            "upgrade-$owner-slot",1,0,0,2,2,listOf(
                                uk.co.traynor.privategallery.home2.WidgetItem("upgrade-$owner-note","note",note),
                                uk.co.traynor.privategallery.home2.WidgetItem("upgrade-$owner-clock","clock")),1))))
                        assertTrue(widgets.reduced(true));assertTrue(widgets.demoLock(true));widgets.clock(owner,false)
                        mind.prefer("grounding")
                        assertTrue(mind.put(owner,journal,mood,true,"upgrade-$owner-journal"))
                        assertTrue(mind.put(owner,"",mood,false,"upgrade-$owner-mood"))
                        before.put("home2-$owner",JSONObject().put("state",prefs.getString("state",null))
                            .put("widgets",space.getSharedPreferences("nova_widgets_v1",0).getString("layout:$owner",null))
                            .put("mind",space.getSharedPreferences("nova_mind_v1",0).getString(owner,null)).put("wallpaper",wallpaperId)
                            .put("wallpaperRecord",checkNotNull(prefs.getString(uk.co.traynor.privategallery.core.home2.WallpaperStore.KEY,null))))
                    }
                    val expected=before.getJSONObject("home2-$owner")
                    val raw=checkNotNull(prefs.getString("state",null))
                    assertEquals("Preserve every serialized Home2 layout/theme field for $owner",expected.getString("state"),raw)
                    val state=JSONObject(raw);assertEquals(owner,state.getString("current"));assertEquals(theme,state.getString("theme"))
                    val profile=state.getJSONObject(owner)
                    assertEquals(5,profile.getInt("columns"));assertEquals(68,profile.getInt("iconSize"))
                    assertEquals("$owner tools",profile.getJSONArray("folders").getJSONObject(0).getString("name"))
                    assertEquals(expected.getString("wallpaper"),profile.getJSONObject("wallpaper").getString("image"))
                    assertEquals(expected.getString("wallpaperRecord"),prefs.getString(uk.co.traynor.privategallery.core.home2.WallpaperStore.KEY,null))
                    val bytes=checkNotNull(space.readWallpaper(expected.getString("wallpaper")))
                    try {assertArrayEquals("Synthetic $owner encrypted wallpaper bytes".toByteArray(),bytes)}finally{bytes.fill(0)}
                    assertEquals(expected.getString("widgets"),space.getSharedPreferences("nova_widgets_v1",0).getString("layout:$owner",null))
                    val slot=widgets.slots(owner).single();assertEquals(1,slot.page);assertEquals(1,slot.active)
                    assertEquals(listOf("note","clock"),slot.items.map{it.type});assertEquals(note,slot.items.first().config)
                    assertTrue(widgets.reduced);assertTrue(widgets.demoLock);assertFalse(widgets.clockVisible(owner))
                    assertEquals(expected.getString("mind"),space.getSharedPreferences("nova_mind_v1",0).getString(owner,null))
                    assertEquals("grounding",mind.preferred)
                    val entries=mind.entries(owner);assertEquals(2,entries.size)
                    assertEquals(journal,entries.single{it.journal}.text);assertEquals(mood,entries.single{!it.journal}.mood)
                    assertTrue(entries.all{it.id.startsWith("upgrade-$owner-")&&it.time>0})
                    val other=if(owner=="primary")"hidden" else "primary"
                    assertTrue("Mind owners remain isolated",mind.entries(other).isEmpty())
                    assertTrue("Widget owners remain isolated",widgets.slots(other).isEmpty())
                }
            }
        } finally {primary.close();secret.close();hidden.lock();key.fill(0)}
    }

    // BEGIN NOVA62 PORTABLE APP FIXTURE: baseline64 SpaceContext APIs only.
    private fun portableApps62(seed:Boolean) {
        val key=PinVaultKeyStore(context).unlock("738291".toCharArray())
        val hidden=hiddenSession(false);val identity=Any()
        val primary=uk.co.traynor.privategallery.core.evidence.EvidenceAccess.primary(context,identity,{identity},{true},{key.copyOf()},Any())
        val secret=uk.co.traynor.privategallery.core.evidence.EvidenceAccess.hidden(context,hidden){true}
        val arcade=linkedMapOf<String,Any>(
            "save:2048" to "TkFSAQEAAAAQAAAAAgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAtUzaWPu+6LwBAAAAEAAAAAIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAHjd5uX9KfCS",
            "save:sudoku" to "TkFSAQIAAAAAAAAAUQAAAAkAAAAAAAAAAAAAAAUAAAAGAAAAAwAAAAEAAAAHAAAAAAAAAAAAAAACAAAABwAAAAkAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABgAAAAAAAAAHAAAAAgAAAAAAAAAAAAAACAAAAAcAAAABAAAACAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAJAAAAAwAAAAAAAAACAAAAAAAAAAcAAAAIAAAAAQAAAAYAAAAFAAAAAAAAAAcAAAAAAAAAAAAAAAQAAAADAAAAAAAAAAMAAAAEAAAABQAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAAAAgAAAAJAAAAAAAAAAMAAAAFAAAABAAAAAgAAAAHAAAACQAAAAAAAAAAAAAAAAAAAAIAAAAAAAAAAAAAAFEAAAAJAAAACAAAAAQAAAAFAAAABgAAAAMAAAABAAAABwAAAAIAAAABAAAAAgAAAAcAAAAJAAAABAAAAAgAAAAFAAAABgAAAAMAAAAFAAAAAwAAAAYAAAABAAAABwAAAAIAAAAJAAAABAAAAAgAAAAHAAAAAQAAAAgAAAAEAAAAAwAAAAkAAAAGAAAAAgAAAAUAAAAEAAAACQAAAAMAAAAGAAAAAgAAAAUAAAAHAAAACAAAAAEAAAAGAAAABQAAAAIAAAAHAAAACAAAAAEAAAAEAAAAAwAAAAkAAAADAAAABAAAAAUAAAACAAAAAQAAAAYAAAAIAAAACQAAAAcAAAACAAAABgAAAAEAAAAIAAAACQAAAAcAAAADAAAABQAAAAQAAAAIAAAABwAAAAkAAAADAAAABQAAAAQAAAACAAAAAQAAAAYAAAAAAAAAAAAAAABRAAAACQAAAAAAAAAAAAAABQAAAAYAAAADAAAAAQAAAAcAAAAAAAAAAAAAAAIAAAAHAAAACQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAGAAAAAAAAAAcAAAACAAAAAAAAAAAAAAAIAAAABwAAAAEAAAAIAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAkAAAADAAAAAAAAAAIAAAAAAAAABwAAAAgAAAABAAAABgAAAAUAAAAAAAAABwAAAAAAAAAAAAAABAAAAAMAAAAAAAAAAwAAAAQAAAAFAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAACAAAAAkAAAAAAAAAAwAAAAUAAAAEAAAACAAAAAcAAAAJAAAAAAAAAAAAAAAAAAAAAgAAAAAAAAAAAAAAUQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "save:snake" to "TkFSAQMAAAADAAAAiAAAAIcAAACGAAAAAAAAAAAAAABwAAAAAJ43ebl/SnxTAQA=",
            "sound" to true,"haptics" to false,"speed" to 3)
        val calculator=linkedMapOf<String,Any>("angle" to "DEG","scientific" to true,"history-enabled" to true,
            "memory" to "0.3","history" to "AAAAAQAAAAEABzAuMSswLjIAAzAuMwA=")
        try {
            for(access in listOf(primary,secret))uk.co.traynor.privategallery.core.home2.SpaceContext(context,access).use {space->
                for((namespace,expected) in listOf("nova_arcade_v1" to arcade,"nova_calculator_v1" to calculator)) {
                    val preferences=space.getSharedPreferences(namespace,0)
                    if(seed)assertTrue(preferences.edit().also{editor->expected.forEach{(name,value)->when(value){is String->editor.putString(name,value);is Boolean->editor.putBoolean(name,value);is Int->editor.putInt(name,value)}}}.commit())
                    assertEquals("Owner-encrypted app preferences remain unchanged across real package upgrade",expected,preferences.all)
                    if(!seed){
                        val validator=Class.forName(if(namespace=="nova_arcade_v1")"uk.co.traynor.privategallery.core.arcade.ArcadePreferences" else "uk.co.traynor.privategallery.core.calculator.CalculatorPreferences")
                        validator.getMethod("validate",Map::class.java).invoke(null,preferences.all)
                    }
                }
                // BEGIN CODE621 BACKUP HISTORY FIXTURE
                val primarySpace=access===primary
                val destination=if(primarySpace)"fixture-primary-backup.nova" else "fixture-hidden-backup.nova"
                val archiveBytes=if(primarySpace)64_000_000L else 2_500_000_000L
                val verified=JSONObject()
                    .put("status","Verified").put("operation","Backup")
                    .put("destination",destination).put("bytes",archiveBytes).put("date",1_700_000_000_000L)
                val failed=JSONObject()
                    .put("status","Failed").put("operation","Restore")
                    .put("destination",destination).put("bytes",0L).put("date",1_700_000_060_000L)
                val history=space.getSharedPreferences("nova_backup_history_v1",0)
                val expectedHistory=mapOf(
                    "lastBackup" to verified.toString(),
                    "events" to org.json.JSONArray().put(failed).put(verified).toString(),
                )
                if(seed) {
                    val editor=history.edit()
                    expectedHistory.forEach{(name,value)->editor.putString(name,value)}
                    assertTrue(editor.commit())
                }
                assertEquals("Owner-encrypted Backup history survives package upgrade and restart",expectedHistory,history.all)
                // END CODE621 BACKUP HISTORY FIXTURE
            }
        }finally{primary.close();secret.close();hidden.lock();key.fill(0)}
    }
    // END NOVA62 PORTABLE APP FIXTURE

    /** The original package remains a regular app and Android keeps its previous Home selection. */
    private fun launcherRegistration(seed: Boolean): String {
        val packageManager = context.packageManager
        val application = context.packageName
        val all = PackageManager.ResolveInfoFlags.of(0)
        val defaultOnly = PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
        val roles = checkNotNull(context.getSystemService(RoleManager::class.java))
        assertTrue("The Android 17 device offers the Home role", roles.isRoleAvailable(RoleManager.ROLE_HOME))
        assertFalse("Installing or upgrading NovaOS must not acquire the Home role", roles.isRoleHeld(RoleManager.ROLE_HOME))

        val appEntry = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(application)
        val existingEntry = packageManager.queryIntentActivities(appEntry, all)
            .single { it.activityInfo.name == "$application.LauncherActivity" }.activityInfo
        assertTrue("Keep the existing exported private workspace entry point", existingEntry.exported)

        val systemHome = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolvedHome = checkNotNull(packageManager.resolveActivity(systemHome, defaultOnly)).activityInfo
        assertNotEquals("Android Home still resolves outside NovaOS until the owner opts in", application, resolvedHome.packageName)
        val registered = packageManager.queryIntentActivities(Intent(systemHome).setPackage(application), defaultOnly)
        if (seed) {
            assertTrue("Original Code 69 did not register a Home activity", registered.isEmpty())
        } else {
            val launcher = registered.single().activityInfo
            assertEquals("$application.launcher.NovaLauncherActivity", launcher.name)
            assertTrue("Android can launch the Home activity", launcher.exported)
            assertFalse("The Home activity is separate from the existing app entry", packageManager.queryIntentActivities(appEntry, all)
                .any { it.activityInfo.name == launcher.name })
            assertNotNull("Launcher has a dedicated task affinity", launcher.taskAffinity)
            assertNotEquals("Launcher task affinity is separate from the existing workspace entry", existingEntry.taskAffinity, launcher.taskAffinity)
        }
        return ComponentName(resolvedHome.packageName, resolvedHome.name).flattenToString()
    }

    /** Exercise the delivered production HOME Activity before using any Vault credentials. */
    private fun verifyProductionLauncher(previousHome: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val launcher = ComponentName(context.packageName, "${context.packageName}.launcher.NovaLauncherActivity")
        val workspace = ComponentName(context.packageName, "${context.packageName}.MainActivity")
        assertFalse("The private workspace is not an exported Android entry point",
            context.packageManager.getActivityInfo(workspace, 0).exported)

        val hostile = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(launcher)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .setData(Uri.parse("content://foreign.invalid/evidence/forged"))
            .putExtra("workspace", "hidden").putExtra("unlock", true)
        val lifecycle = ActivityLifecycleMonitorRegistry.getInstance()
        var started: android.app.Activity? = null
        try {
            context.startActivity(hostile)
            val deadline = SystemClock.uptimeMillis() + 15_000L
            var verified = false
            while (!verified && SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync {
                    val resumed = lifecycle.getActivitiesInStage(Stage.RESUMED)
                    val home = resumed.singleOrNull { it.componentName == launcher }
                    if (home != null) {
                        started = home
                        if (home.hasWindowFocus()) {
                            assertTrue("Optional Home runs in its own root task", home.isTaskRoot)
                            assertEquals(Intent.ACTION_MAIN, home.intent.action)
                            assertEquals(setOf(Intent.CATEGORY_HOME), home.intent.categories)
                            assertNull("Home discards untrusted URI data", home.intent.data)
                            assertTrue("Home discards forged workspace and unlock extras",
                                home.intent.extras?.isEmpty != false)
                            assertFalse("Opening optional Home does not open the private workspace",
                                resumed.any { it.componentName == workspace })
                            verified = true
                        }
                    }
                }
                if (!verified) SystemClock.sleep(50)
            }
            assertTrue("Production HOME activity resumed with window focus", verified)
            assertEquals("Opening optional Home preserves Android's previous default",
                previousHome, launcherRegistration(seed = false))
        } finally {
            instrumentation.runOnMainSync {
                (started ?: lifecycle.getActivitiesInStage(Stage.RESUMED)
                    .singleOrNull { it.componentName == launcher })?.finishAndRemoveTask()
            }
            instrumentation.waitForIdleSync()
        }
    }

    /** Seed one authentic existing policy without starting the Guardian or granting any permissions. */
    private fun sanctuaryPolicy(seed: Boolean) {
        val store = SanctuaryStore.get(context)
        val preset = SanctuaryPreset(
            minutes = 37,
            androidApps = setOf("com.android.settings", "uk.co.synthetic.reader"),
            novaApps = setOf("mind", "arcade"),
            enableDnd = true,
            offerPinning = true,
            enableGuardian = true,
            allowExternalApps = false,
            blockedNovaApps = setOf("browser-v1"),
            allowWhatsApp = true,
        )
        if (seed) assertTrue("Seed original Sanctuary approved-app policy", store.savePreset(SanctuaryMode.LEARNING, preset))
        assertEquals("Preserve the original Sanctuary policy", preset, store.preset(SanctuaryMode.LEARNING))
        assertNull("Upgrade must not start a Sanctuary session", store.refresh())
    }

    @Test fun seed() {
        guard(); check(!proof.exists())
        assertEquals(baselineCode, context.packageManager.getPackageInfo(context.packageName, 0).versionCode)
        val previousHome = launcherRegistration(seed = true)
        sanctuaryPolicy(seed = true)
        val key = PinVaultKeyStore(context).create("738291".toCharArray())
        try {
            val repository = AndroidVaultRepository(context, key)
            primaryBrowser(key,true)
            primaryPrivateApps(key,true)
            browserConfiguration(true)
            cameraConfiguration(key,null,true)
            val photo = Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).let { bitmap ->
                try { ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray() } }
                finally { bitmap.recycle() }
            }
            val video = InstrumentationRegistry.getInstrumentation().context.assets.open("browser-media/valid.mp4").use { it.readBytes() }
            val media = JSONObject()
            val primaryDeletedIds = mutableListOf<String>()
            listOf(Triple("upgrade.png","image/png",photo), Triple("upgrade.mp4","video/mp4",video)).forEach { (name,mime,bytes) ->
                try {
                    val item = (repository.importVerified(VaultImportSource(name,mime,{ByteArrayInputStream(bytes)})) as ImportResult.Imported).item
                    media.put(item.id,hash(bytes))
                    val deleted = (repository.importVerified(VaultImportSource("deleted-$name",mime,
                        {ByteArrayInputStream(bytes)},createDistinctCopy=true)) as ImportResult.Imported).item
                    primaryDeletedIds += deleted.id
                } finally { bytes.fill(0) }
            }
            val collection = repository.createCollection("Synthetic upgrade favourites")
            repository.addItemsToCollection(collection.id, repository.items().map { it.id })
            repository.setFavouriteCollection(collection.id)
            repository.retentionDays(60)
            primaryDeletedIds.forEach { repository.moveToRecentlyDeleted(it) }
            val primaryDeleted = deletedSnapshot(repository.recentlyDeleted())
            assertRecentlyDeleted(primaryDeleted,repository.recentlyDeleted(),repository.items())
            val profile = VpnProfile("upgrade-fixture", "Synthetic upgrade profile", VpnProtocol.WIREGUARD, "synthetic configuration; never connected")
            EncryptedVpnProfileStore(File(context.filesDir,"vpn-profiles")).save(VpnProfileSnapshot(listOf(profile),profile.id),key)
            check(context.getSharedPreferences("private-gallery-settings",0).edit().putString("app-theme","dark").putString("allow-screenshots","true").commit())
            val credential="synthetic-upgrade-credential-not-a-live-token".toByteArray()
            try {uk.co.traynor.privategallery.core.editor.AiCredentialStore(context).save("upgrade-fixture",credential)}finally{credential.fill(0)}
            check(context.getSharedPreferences("ai_editing_privacy",0).edit().putBoolean("keep_ai_edits_in_vault",true).putString("consented_provider_v1","synthetic-provider").commit())
            check(context.getSharedPreferences("hidden-v1-policy",0).edit().putString("background-timeout","SECONDS_30").commit())
            if(baselineCode>=41) {
                check(context.getSharedPreferences("private-gallery-settings",0).edit().putString("hide-content","true").commit())
                check(context.getSharedPreferences("privacy-shield",0).edit().putBoolean("enabled",true).putBoolean("three-finger",true).putBoolean("quick-lock",true).commit())
            }
            val hidden=hiddenSession(true)
            val hiddenMedia=JSONObject()
            var hiddenDates=JSONObject()
            var hiddenDeleted=JSONObject()
            var hiddenCollection=""
            try {
                val repo=HiddenRepository(context,hidden)
                val hiddenDeletedIds=mutableListOf<String>()
                hiddenBrowser(hidden,true)
                hiddenPrivateApps(hidden,true)
                cameraConfiguration(null,hidden,true)
                val bytes=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).let {bitmap ->
                    try {ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}}
                    finally {bitmap.recycle()}
                }
                val videoBytes=InstrumentationRegistry.getInstrumentation().context.assets.open("browser-media/valid.mp4").use {it.readBytes()}
                listOf(Triple("hidden-upgrade.png","image/png",bytes),Triple("hidden-upgrade.mp4","video/mp4",videoBytes)).forEach {(name,mime,data) ->
                    try {
                        val item=repo.importMedia(name,mime,ByteArrayInputStream(data));hiddenMedia.put(item.id,hash(data))
                        hiddenDeletedIds += repo.importMedia("deleted-$name",mime,ByteArrayInputStream(data),createDistinctCopy=true).id
                    }
                    finally {data.fill(0)}
                }
                hiddenCollection=repo.createCollection("Hidden upgrade favourites").id
                repo.addItemsToCollection(hiddenCollection,repo.items().map {it.id});repo.setFavouriteCollection(hiddenCollection)
                repo.retentionDays(15)
                repo.trash(hiddenDeletedIds)
                hiddenDeleted=deletedSnapshot(repo.recentlyDeleted())
                assertRecentlyDeleted(hiddenDeleted,repo.recentlyDeleted(),repo.items())
                if(baselineCode>=36)repo.javaClass.getMethod("presentationSort",String::class.java).invoke(repo,"OLDEST")
                hiddenDates=dateSnapshot(repo)
            } finally {hidden.lock()}
            if(baselineCode>=36)repository.javaClass.getMethod("presentationSort",String::class.java).invoke(repository,"NAME")
            val seeded=JSONObject().put("primaryDeleted",primaryDeleted).put("hiddenDeleted",hiddenDeleted).put("primaryDates",dateSnapshot(repository)).put("hiddenDates",hiddenDates).put("hiddenMedia",hiddenMedia).put("hiddenCollection",hiddenCollection).put("hiddenFiles",hiddenHashes()).put("media",media).put("collection",collection.id).put("files",encryptedHashes())
            candidateEvidence(seeded,false,baselineSeed=true)
            connectedEvidence68(seeded,seed=true)
            homePreferences(seed=true)
            home2State(seeded,seed=true)
            portableApps62(seed=true) // NOVA62 addition
            seeded.put("files",encryptedHashes()).put("hiddenFiles",hiddenHashes()).put("previousHome",previousHome)
            proof.writeText(seeded.toString())
        } finally { key.fill(0) }
    }
    /** Capture deletion metadata and original authenticated payload identity without restoring/mutating it. */
    private fun deletedSnapshot(items:List<VaultItem>):JSONObject = JSONObject().also { result ->
        items.forEach { item -> result.put(item.id,JSONObject()
            .put("state",item.state.name).put("deleted",item.deletedAtEpochMillis)
            .put("name",item.displayName).put("mime",item.mimeType)
            .put("imported",item.importedAtEpochMillis).put("size",item.plaintextSize)
            .put("hash",hash(item.plaintextSha256)).put("nonce",hash(item.payloadNonce)))
        }
    }
    /** JSON text may box the same integral value as Int or Long after restart. */
    private fun assertSnapshotValue(label:String,expected:Any,actual:Any) {
        if(expected is Int || expected is Long) {
            assertTrue("$label remains an integral JSON number",actual is Int || actual is Long)
            assertEquals(label,(expected as Number).toLong(),(actual as Number).toLong())
        } else assertEquals(label,expected,actual)
    }
    private fun assertRecentlyDeleted(expected:JSONObject,deleted:List<VaultItem>,active:List<VaultItem>) {
        assertEquals(2,expected.length())
        assertEquals(setOf("image/png","video/mp4"),deleted.map {it.mimeType}.toSet())
        assertEquals(expected.keys().asSequence().toSet(),deleted.map {it.id}.toSet())
        val actual=deletedSnapshot(deleted)
        deleted.forEach { item ->
            assertEquals(VaultItemState.TRASHED,item.state)
            assertTrue(checkNotNull(item.deletedAtEpochMillis)>0)
            assertFalse("Trashed media must stay out of the active gallery",active.any {it.id==item.id})
            val prior=expected.getJSONObject(item.id);val current=actual.getJSONObject(item.id)
            prior.keys().forEach { field -> assertSnapshotValue("Preserve Recently Deleted ${item.id}/$field",prior.get(field),current.get(field)) }
        }
    }
    /** Run again after reads/reconcile: only the explicitly changed presentation sort may differ. */
    private fun assertCiphertextAfterReads(before:JSONObject) {
        for((root,field) in listOf(context.filesDir to "files",context.noBackupFilesDir to "hiddenFiles")) {
            val expected=before.getJSONObject(field)
            expected.keys().forEach { path ->
                if(!path.endsWith("presentation-v1/state.enc")) {
                    assertEquals("Preserve ciphertext after reads $field/$path",expectedCiphertext(before,field,path,arguments.getString("restartCheck")=="true",afterHiddenAuthentication=true),hash(File(root,path).readBytes()))
                }
            }
        }
    }
    private fun verifyCandidateSort(repository:Any,expected:String) {
        if(candidateCode<36)return
        val read=repository.javaClass.getMethod("presentationSort")
        val restarted=arguments.getString("restartCheck")=="true"
        if(!restarted) {
            if(baselineCode>=36)assertEquals(if(repository is HiddenRepository)"OLDEST" else "NAME",read.invoke(repository))
            else assertNull(read.invoke(repository))
            repository.javaClass.getMethod("presentationSort",String::class.java).invoke(repository,expected)
        }
        if(repository is HiddenRepository)repository.reconcile()
        assertEquals("Independent sort survives reconcile/restart",expected,read.invoke(repository))
    }
    private fun dateSnapshot(repository:Any):JSONObject {
        val result=JSONObject()
        if(baselineCode<36)return result
        @Suppress("UNCHECKED_CAST") val items=repository.javaClass.getMethod("items").invoke(repository) as List<VaultItem>
        items.forEach {item->result.put(item.id,JSONObject().put("added",item.javaClass.getMethod("getAddedAtEpochMillis").invoke(item) ?: JSONObject.NULL).put("captured",item.javaClass.getMethod("getCapturedAtEpochMillis").invoke(item) ?: JSONObject.NULL))}
        return result
    }
    private fun assertDates(expected:JSONObject,actual:JSONObject) {
        assertEquals(expected.length(),actual.length())
        expected.keys().forEach {id->for(field in listOf("added","captured"))assertSnapshotValue("Preserve original presentation $id/$field",expected.getJSONObject(id).get(field),actual.getJSONObject(id).get(field))}
    }
    @Test fun verify() {
        guard()
        assertEquals(candidateCode, context.packageManager.getPackageInfo(context.packageName,0).versionCode)
        val before = JSONObject(proof.readText())
        val previousHome = before.getString("previousHome")
        assertEquals("Keep Android's previous Home activity after update and restart", previousHome,
            launcherRegistration(seed = false))
        verifyProductionLauncher(previousHome)
        sanctuaryPolicy(seed = false)
        val restarted=arguments.getString("restartCheck")=="true"
        val files = before.getJSONObject("files")
        files.keys().forEach { path -> assertEquals("Preserve encrypted file $path", expectedCiphertext(before,"files",path,restarted), hash(File(context.filesDir,path).readBytes())) }
        assertEquals("SECONDS_30",context.getSharedPreferences("hidden-v1-policy",0).getString("background-timeout",null))
        val hiddenFiles=before.getJSONObject("hiddenFiles")
        assertTrue("Seed snapshot includes Hidden PIN attempts",hiddenFiles.has(hiddenAttemptsPath))
        hiddenFiles.keys().forEach {path ->assertEquals("Preserve Hidden encrypted file $path",expectedCiphertext(before,"hiddenFiles",path,restarted),hash(File(context.noBackupFilesDir,path).readBytes()))}
        val rejected=runCatching {HiddenCredentialStore(context).unlock("738291".toCharArray()).fill(0)}
        assertTrue(rejected.isFailure)
        assertTrue("The Primary PIN is an actual Hidden PIN mismatch",rejected.exceptionOrNull() is HiddenPinEnvelope.InvalidPin)
        val failedAttempts=authenticatedAttempts()
        try {
            assertEquals("The rejected PIN persists one authenticated attempt",1,HiddenPinThrottle.decode(failedAttempts).failures)
        } finally {failedAttempts.fill(0)}
        val hidden=hiddenSession(false)
        try {
            val repo=HiddenRepository(context,hidden)
            assertEquals(15,repo.retentionDays())
            assertRecentlyDeleted(before.getJSONObject("hiddenDeleted"),repo.recentlyDeleted(),repo.items())
            hiddenBrowser(hidden,false)
            hiddenPrivateApps(hidden,false)
            cameraConfiguration(null,hidden,false)
            verifyCandidateSort(repo,"TAKEN_OLDEST")
            assertRecentlyDeleted(before.getJSONObject("hiddenDeleted"),repo.recentlyDeleted(),repo.items())
            assertDates(before.getJSONObject("hiddenDates"),dateSnapshot(repo))
            val media=before.getJSONObject("hiddenMedia")
            assertEquals(2,repo.items().size)
            repo.items().forEach {item ->val plain=repo.read(item.id);try {assertEquals(media.getString(item.id),hash(plain))}finally {plain.fill(0)}}
            assertEquals(before.getString("hiddenCollection"),repo.favouriteCollection()?.id)
            assertEquals(2,repo.itemsInCollection(before.getString("hiddenCollection")).size)
        } finally {hidden.lock()}
        assertTrue(runCatching { PinVaultKeyStore(context).unlock("905184".toCharArray()) }.isFailure)
        val key = PinVaultKeyStore(context).unlock("738291".toCharArray())
        try {
            val repository=AndroidVaultRepository(context,key)
            assertEquals(60,repository.retentionDays())
            assertRecentlyDeleted(before.getJSONObject("primaryDeleted"),repository.recentlyDeleted(),repository.items())
            primaryBrowser(key,false)
            primaryPrivateApps(key,false)
            browserConfiguration(false)
            cameraConfiguration(key,null,false)
            verifyCandidateSort(repository,"NAME_DESC")
            assertDates(before.getJSONObject("primaryDates"),dateSnapshot(repository))
            val expected=before.getJSONObject("media")
            assertEquals(2,repository.items().size)
            repository.items().forEach { item ->
                val plain=repository.readForViewing(item)
                try { assertEquals(expected.getString(item.id),hash(plain)) } finally { plain.fill(0) }
            }
            val collection=before.getString("collection")
            assertEquals(collection,repository.favouriteCollection()?.id)
            assertEquals(2,repository.itemsInCollection(collection).size)
            val vpn=EncryptedVpnProfileStore(File(context.filesDir,"vpn-profiles")).load(key)
            assertEquals("upgrade-fixture",vpn.activeProfileId)
            assertEquals("synthetic configuration; never connected",vpn.profiles.single().privateConfiguration)
            val credential=checkNotNull(uk.co.traynor.privategallery.core.editor.AiCredentialStore(context).read("upgrade-fixture"))
            try {assertArrayEquals("synthetic-upgrade-credential-not-a-live-token".toByteArray(),credential)}finally{credential.fill(0)}
            val ai=context.getSharedPreferences("ai_editing_privacy",0)
            assertTrue(ai.getBoolean("keep_ai_edits_in_vault",false));assertEquals("synthetic-provider",ai.getString("consented_provider_v1",null))
            val prefs=context.getSharedPreferences("private-gallery-settings",0)
            assertEquals("dark",prefs.getString("app-theme",null));assertEquals("true",prefs.getString("allow-screenshots",null))
            if(baselineCode>=41) {
                assertEquals("Presentation flag survives genuine package upgrade/restart","true",prefs.getString("hide-content",null))
                val panic=context.getSharedPreferences("privacy-shield",0)
                for(name in listOf("enabled","three-finger","quick-lock"))assertTrue("Preserve Panic preference $name",panic.getBoolean(name,false))
            }
        } finally { key.fill(0) }
        homePreferences(seed=false)
        home2State(before,seed=false)
        portableApps62(seed=false) // NOVA62 addition
        assertCiphertextAfterReads(before)
        if(baselineCode>=36&&!restarted) {
            // Every original ciphertext has just been checked after reads. Only now may
            // explicit code51 style commands rewrite their three originating catalogues.
            rewriteCandidatePrivateAppStyles()
            before.put("candidatePrivateAppFiles",privateAppCiphertexts())
            fun presentationOnly(all:JSONObject)=JSONObject().also {out->all.keys().asSequence().filter {it.endsWith("presentation-v1/state.enc")}.forEach {out.put(it,all.getString(it))}}
            val primary=presentationOnly(encryptedHashes());val secret=presentationOnly(hiddenHashes())
            assertEquals(1,primary.length());assertEquals(1,secret.length())
            before.put("candidatePresentationFiles",primary).put("candidateHiddenPresentationFiles",secret)
            connectedEvidence68(before,seed=false)
            candidateEvidence(before,false)
            before.put("candidateHiddenAttemptsSha256",checkNotNull(verifiedHiddenAttemptsSha256))
            proof.writeText(before.toString())
        }
        if(restarted) {
            connectedEvidence68(before,seed=false)
            candidateEvidence(before,true)
        }
    }
}
