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

/** Only copied into a disposable V1 test checkout by the CI upgrade script. */
class UpgradeFixtureTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val proof get() = File(context.filesDir, "synthetic-upgrade-proof.json")
    private fun guard() { check(context.packageName == "uk.co.traynor.privategallery.hiddenprototype") }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun encryptedHashes(): JSONObject = JSONObject().also { result ->
        context.filesDir.walkTopDown().filter { it.isFile && it != proof }.forEach {
            result.put(it.relativeTo(context.filesDir).path, hash(it.readBytes()))
        }
    }
    @Test fun seed() {
        guard(); check(!proof.exists())
        assertEquals(28, context.packageManager.getPackageInfo(context.packageName, 0).versionCode)
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
            proof.writeText(JSONObject().put("media",media).put("collection",collection.id).put("files",encryptedHashes()).toString())
        } finally { key.fill(0) }
    }
    @Test fun verify() {
        guard()
        assertTrue(context.packageManager.getPackageInfo(context.packageName,0).versionCode > 28)
        val before = JSONObject(proof.readText())
        val files = before.getJSONObject("files")
        files.keys().forEach { path -> assertEquals("Preserve encrypted file $path", files.getString(path), hash(File(context.filesDir,path).readBytes())) }
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
