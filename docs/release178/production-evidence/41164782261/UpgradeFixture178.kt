package uk.co.traynor.privategallery.upgrade

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
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

/** Reviewed release-control instrumentation for original-key code48 -> code49 upgrade.
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
        check(baselineCode == 48 && candidateCode == 49)
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun encryptedHashes(): JSONObject = JSONObject().also { result ->
        context.filesDir.walkTopDown().filter { it.isFile && it != proof }.forEach {
            result.put(it.relativeTo(context.filesDir).path, hash(it.readBytes()))
        }
    }
    private fun hiddenHashes(): JSONObject = JSONObject().also { result ->
        context.noBackupFilesDir.walkTopDown().filter {it.isFile}.forEach {
            result.put(it.relativeTo(context.noBackupFilesDir).path,hash(it.readBytes()))
        }
    }
    private fun hiddenSession(create:Boolean):HiddenSession {
        val store=HiddenCredentialStore(context)
        val key=if(create)store.create("905184".toCharArray()) else store.unlock("905184".toCharArray())
        return HiddenSession(android.os.SystemClock::elapsedRealtime).also {assertTrue(it.complete(it.beginAuthentication(),key))}
    }
    /** Explicit encrypted browser metadata only; Hidden cookies/renderers remain ephemeral. */
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
     * No candidate-only API is referenced, so instrumentation compiles against the actual code48 source.
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
        fun id(index:Int)=java.util.UUID.nameUUIDFromBytes("upgrade49:$label:$index".toByteArray(Charsets.UTF_8)).toString()
        val expected=listOf(
            PrivateAppCatalog.App(id(1),"Upgrade $label PWA","$origin/app/home?upgrade=48","$origin/app/","standalone",icon,true,false,true,false,"Synthetic baseline PWA discovery report"),
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
            try {assertEquals("Original code48 seed uses catalogue format2",2,java.io.DataInputStream(ByteArrayInputStream(encoded)).readInt())}
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
    /** Candidate-only commands are looked up reflectively: baseline instrumentation keeps its code48 ABI. */
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
    private fun expectedCiphertext(before:JSONObject,field:String,path:String,restarted:Boolean):String {
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
            InstrumentationRegistry.getInstrumentation().runOnMainSync {manager.flush()}
        }
        var retained:String?=null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {retained=manager.getCookie(origin)}
        assertTrue("Existing Primary browser cookie survives upgrade/restart",retained.orEmpty().split(';').any {it.trim()=="upgrade178=synthetic-preserved"})
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
    @Test fun seed() {
        guard(); check(!proof.exists())
        assertEquals(baselineCode, context.packageManager.getPackageInfo(context.packageName, 0).versionCode)
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
            proof.writeText(JSONObject().put("primaryDeleted",primaryDeleted).put("hiddenDeleted",hiddenDeleted).put("primaryDates",dateSnapshot(repository)).put("hiddenDates",hiddenDates).put("hiddenMedia",hiddenMedia).put("hiddenCollection",hiddenCollection).put("hiddenFiles",hiddenHashes()).put("media",media).put("collection",collection.id).put("files",encryptedHashes()).toString())
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
                    assertEquals("Preserve ciphertext after reads $field/$path",expectedCiphertext(before,field,path,arguments.getString("restartCheck")=="true"),hash(File(root,path).readBytes()))
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
        val restarted=arguments.getString("restartCheck")=="true"
        val files = before.getJSONObject("files")
        files.keys().forEach { path -> assertEquals("Preserve encrypted file $path", expectedCiphertext(before,"files",path,restarted), hash(File(context.filesDir,path).readBytes())) }
        assertEquals("SECONDS_30",context.getSharedPreferences("hidden-v1-policy",0).getString("background-timeout",null))
        val hiddenFiles=before.getJSONObject("hiddenFiles")
        hiddenFiles.keys().forEach {path ->assertEquals("Preserve Hidden encrypted file $path",expectedCiphertext(before,"hiddenFiles",path,restarted),hash(File(context.noBackupFilesDir,path).readBytes()))}
        assertTrue(runCatching {HiddenCredentialStore(context).unlock("738291".toCharArray())}.isFailure)
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
        assertCiphertextAfterReads(before)
        if(baselineCode>=36&&!restarted) {
            // Every original ciphertext has just been checked after reads. Only now may
            // explicit code49 style commands rewrite their three originating catalogues.
            rewriteCandidatePrivateAppStyles()
            before.put("candidatePrivateAppFiles",privateAppCiphertexts())
            fun presentationOnly(all:JSONObject)=JSONObject().also {out->all.keys().asSequence().filter {it.endsWith("presentation-v1/state.enc")}.forEach {out.put(it,all.getString(it))}}
            val primary=presentationOnly(encryptedHashes());val secret=presentationOnly(hiddenHashes())
            assertEquals(1,primary.length());assertEquals(1,secret.length())
            before.put("candidatePresentationFiles",primary).put("candidateHiddenPresentationFiles",secret)
            proof.writeText(before.toString())
        }
    }
}
