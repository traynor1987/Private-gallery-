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
        check((baselineCode == 32 && candidateCode == 33) || (baselineCode == 33 && candidateCode == 34) || (baselineCode == 34 && candidateCode == 35) || (baselineCode == 35 && candidateCode == 36))
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
            } finally {hidden.lock()}
            proof.writeText(JSONObject().put("hiddenMedia",hiddenMedia).put("hiddenCollection",hiddenCollection).put("hiddenFiles",hiddenHashes()).put("media",media).put("collection",collection.id).put("files",encryptedHashes()).toString())
        } finally { key.fill(0) }
    }
    @Test fun verify() {
        guard()
        assertEquals(candidateCode, context.packageManager.getPackageInfo(context.packageName,0).versionCode)
        val before = JSONObject(proof.readText())
        val files = before.getJSONObject("files")
        files.keys().forEach { path -> assertEquals("Preserve encrypted file $path", files.getString(path), hash(File(context.filesDir,path).readBytes())) }
        assertEquals("SECONDS_30",context.getSharedPreferences("hidden-v1-policy",0).getString("background-timeout",null))
        val hiddenFiles=before.getJSONObject("hiddenFiles")
        hiddenFiles.keys().forEach {path ->assertEquals("Preserve Hidden encrypted file $path",hiddenFiles.getString(path),hash(File(context.noBackupFilesDir,path).readBytes()))}
        assertTrue(runCatching {HiddenCredentialStore(context).unlock("738291".toCharArray())}.isFailure)
        val hidden=hiddenSession(false)
        try {
            val repo=HiddenRepository(context,hidden)
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
    }
}
