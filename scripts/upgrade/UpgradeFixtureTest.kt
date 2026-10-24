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

/** Only copied into the pinned disposable baseline checkout by the CI upgrade script. */
class UpgradeFixtureTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val proof get() = File(context.filesDir, "synthetic-upgrade-proof.json")
    private val arguments = InstrumentationRegistry.getArguments()
    private val baselineCode get() = checkNotNull(arguments.getString("baselineCode")).toInt()
    private val candidateCode get() = checkNotNull(arguments.getString("candidateCode")).toInt()
    private fun guard() {
        check(context.packageName == "uk.co.traynor.privategallery.hiddenprototype")
        check((baselineCode == 32 && candidateCode == 33) || (baselineCode == 33 && candidateCode == 34) || (baselineCode == 34 && candidateCode == 35) || (baselineCode == 35 && candidateCode == 36) || (baselineCode == 36 && candidateCode == 37) || (baselineCode == 37 && candidateCode == 38))
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
    @Test fun seed() {
        guard(); check(!proof.exists())
        assertEquals(baselineCode, context.packageManager.getPackageInfo(context.packageName, 0).versionCode)
        val key = PinVaultKeyStore(context).create("738291".toCharArray())
        try {
            val repository = AndroidVaultRepository(context, key)
            val photo = Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).let { bitmap ->
                try { ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray() } }
                finally { bitmap.recycle() }
            }
            val video = InstrumentationRegistry.getInstrumentation().context.assets.open("browser-media/valid.mp4").use { it.readBytes() }
            val media = JSONObject()
            listOf(Triple("upgrade.png","image/png",photo), Triple("upgrade.mp4","video/mp4",video)).forEach { (name,mime,bytes) ->
                try {
                    val item = (repository.importVerified(VaultImportSource(name,mime,{ByteArrayInputStream(bytes)})) as ImportResult.Imported).item
                    media.put(item.id,hash(bytes))
                } finally { bytes.fill(0) }
            }
            val collection = repository.createCollection("Synthetic upgrade favourites")
            repository.addItemsToCollection(collection.id, repository.items().map { it.id })
            repository.setFavouriteCollection(collection.id)
            val profile = VpnProfile("upgrade-fixture", "Synthetic upgrade profile", VpnProtocol.WIREGUARD, "synthetic configuration; never connected")
            EncryptedVpnProfileStore(File(context.filesDir,"vpn-profiles")).save(VpnProfileSnapshot(listOf(profile),profile.id),key)
            check(context.getSharedPreferences("private-gallery-settings",0).edit().putString("app-theme","dark").putString("allow-screenshots","true").commit())
            val credential="synthetic-upgrade-credential-not-a-live-token".toByteArray()
            try {uk.co.traynor.privategallery.core.editor.AiCredentialStore(context).save("upgrade-fixture",credential)}finally{credential.fill(0)}
            check(context.getSharedPreferences("ai_editing_privacy",0).edit().putBoolean("keep_ai_edits_in_vault",true).putString("consented_provider_v1","synthetic-provider").commit())
            check(context.getSharedPreferences("hidden-v1-policy",0).edit().putString("background-timeout","SECONDS_30").commit())
            val hidden=hiddenSession(true)
            val hiddenMedia=JSONObject()
            var hiddenDates=JSONObject()
            var hiddenCollection=""
            try {
                val repo=HiddenRepository(context,hidden)
                val bytes=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).let {bitmap ->
                    try {ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}}
                    finally {bitmap.recycle()}
                }
                val videoBytes=InstrumentationRegistry.getInstrumentation().context.assets.open("browser-media/valid.mp4").use {it.readBytes()}
                listOf(Triple("hidden-upgrade.png","image/png",bytes),Triple("hidden-upgrade.mp4","video/mp4",videoBytes)).forEach {(name,mime,data) ->
                    try {val item=repo.importMedia(name,mime,ByteArrayInputStream(data));hiddenMedia.put(item.id,hash(data))}
                    finally {data.fill(0)}
                }
                hiddenCollection=repo.createCollection("Hidden upgrade favourites").id
                repo.addItemsToCollection(hiddenCollection,repo.items().map {it.id});repo.setFavouriteCollection(hiddenCollection)
                if(baselineCode>=36)repo.javaClass.getMethod("presentationSort",String::class.java).invoke(repo,"OLDEST")
                hiddenDates=dateSnapshot(repo)
            } finally {hidden.lock()}
            if(baselineCode>=36)repository.javaClass.getMethod("presentationSort",String::class.java).invoke(repository,"NAME")
            proof.writeText(JSONObject().put("primaryDates",dateSnapshot(repository)).put("hiddenDates",hiddenDates).put("hiddenMedia",hiddenMedia).put("hiddenCollection",hiddenCollection).put("hiddenFiles",hiddenHashes()).put("media",media).put("collection",collection.id).put("files",encryptedHashes()).toString())
        } finally { key.fill(0) }
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
        expected.keys().forEach {id->for(field in listOf("added","captured"))assertEquals("Preserve original presentation $id/$field",expected.getJSONObject(id).get(field),actual.getJSONObject(id).get(field))}
    }
    @Test fun verify() {
        guard()
        assertEquals(candidateCode, context.packageManager.getPackageInfo(context.packageName,0).versionCode)
        val before = JSONObject(proof.readText())
        val restarted=arguments.getString("restartCheck")=="true"
        val files = before.getJSONObject("files")
        files.keys().forEach { path -> assertEquals("Preserve encrypted file $path", if(baselineCode>=36&&restarted&&path.endsWith("presentation-v1/state.enc"))before.getJSONObject("candidatePresentationFiles").getString(path) else files.getString(path), hash(File(context.filesDir,path).readBytes())) }
        assertEquals("SECONDS_30",context.getSharedPreferences("hidden-v1-policy",0).getString("background-timeout",null))
        val hiddenFiles=before.getJSONObject("hiddenFiles")
        hiddenFiles.keys().forEach {path ->assertEquals("Preserve Hidden encrypted file $path",if(baselineCode>=36&&restarted&&path.endsWith("presentation-v1/state.enc"))before.getJSONObject("candidateHiddenPresentationFiles").getString(path) else hiddenFiles.getString(path),hash(File(context.noBackupFilesDir,path).readBytes()))}
        assertTrue(runCatching {HiddenCredentialStore(context).unlock("738291".toCharArray())}.isFailure)
        val hidden=hiddenSession(false)
        try {
            val repo=HiddenRepository(context,hidden)
            verifyCandidateSort(repo,"TAKEN_OLDEST")
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
        } finally { key.fill(0) }
        if(baselineCode>=36&&!restarted) {
            fun presentationOnly(all:JSONObject)=JSONObject().also {out->all.keys().asSequence().filter {it.endsWith("presentation-v1/state.enc")}.forEach {out.put(it,all.getString(it))}}
            val primary=presentationOnly(encryptedHashes());val secret=presentationOnly(hiddenHashes())
            assertEquals(1,primary.length());assertEquals(1,secret.length())
            before.put("candidatePresentationFiles",primary).put("candidateHiddenPresentationFiles",secret)
            proof.writeText(before.toString())
        }
    }
}
