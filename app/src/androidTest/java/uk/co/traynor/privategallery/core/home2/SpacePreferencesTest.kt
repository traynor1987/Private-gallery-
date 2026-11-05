package uk.co.traynor.privategallery.core.home2

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.hidden.HiddenSession

class SpacePreferencesTest {
 private class Fixture : AutoCloseable {
  val base = InstrumentationRegistry.getInstrumentation().targetContext
  val root = File(base.cacheDir, "home2-${UUID.randomUUID()}")
  val context = object : ContextWrapper(base) {
   override fun getFilesDir() = File(root, "files").apply { mkdirs() }
   override fun getNoBackupFilesDir() = File(root, "hidden").apply { mkdirs() }
   override fun getApplicationContext(): Context = this
  }
  val key = ByteArray(32) { 31 }
  val gate = Any()
  var identity: Any = Any()
  val hidden = HiddenSession { android.os.SystemClock.elapsedRealtime() }
  val primary = access()
  val space = SpaceContext(context, primary)
  fun access(): EvidenceAccess {
   val captured = identity
   return EvidenceAccess.primary(context, captured, { identity }, { true }, { key.copyOf() }, gate)
  }
  override fun close() { space.close(); primary.close(); hidden.lock(); key.fill(0); root.deleteRecursively() }
 }
 private fun refused(action: () -> Unit) {
  try { action(); fail("Retired handle accepted") } catch (_: IllegalStateException) {}
 }
 @Test fun standard_types_are_encrypted_round_trip_with_defensive_copies_and_merge() = Fixture().use { f ->
  assertSame(f.space, f.space.applicationContext)
  assertEquals("primary", f.space.spaceId)
  val prefs = f.space.getSharedPreferences("../../private-layout", Context.MODE_PRIVATE)
  val source = linkedSetOf("secret-one", "secret-two")
  assertTrue(prefs.edit().putString("label", "private-label").putStringSet("set", source)
   .putInt("int", 17).putLong("long", Long.MAX_VALUE).putFloat("float", 1.25f).putBoolean("bool", true).commit())
  source.clear()
  assertEquals("private-label", prefs.getString("label", null))
  assertEquals(setOf("secret-one", "secret-two"), prefs.getStringSet("set", null))
  prefs.getStringSet("set", null)!!.clear()
  assertEquals(2, prefs.getStringSet("set", null)!!.size)
  (prefs.all["set"] as MutableSet<*>).clear()
  assertEquals(2, prefs.getStringSet("set", null)!!.size)
  assertEquals(17, prefs.getInt("int", 0)); assertEquals(Long.MAX_VALUE, prefs.getLong("long", 0))
  assertEquals(1.25f, prefs.getFloat("float", 0f), 0f); assertTrue(prefs.getBoolean("bool", false))
  val early = prefs.edit().putString("early", "one")
  prefs.edit().putString("late", "two").apply(); assertTrue(early.commit())
  assertEquals("two", prefs.getString("late", null))
  prefs.edit().clear().putString("only", "survivor").apply()
  assertEquals(mapOf("only" to "survivor"), prefs.all)
  val reopened = SpaceContext(f.context, f.primary)
  try { assertEquals("survivor", reopened.getSharedPreferences("../../private-layout", 0).getString("only", null)) }
  finally { reopened.close() }
  f.root.walkTopDown().filter { it.isFile }.forEach { file ->
   val raw = file.readBytes().toString(Charsets.ISO_8859_1)
   assertFalse(raw.contains("survivor")); assertFalse(raw.contains("private-label")); assertFalse(raw.contains("secret-one"))
  }
 }
 @Test fun owner_and_preference_names_are_authenticated_and_corruption_is_preserved() = Fixture().use { f ->
  assertTrue(f.hidden.complete(f.hidden.beginAuthentication(), ByteArray(32) { 31 }))
  val hiddenAccess = EvidenceAccess.hidden(f.context, f.hidden) { true }
  val hidden = SpaceContext(f.context, hiddenAccess)
  try {
   val primary = f.space.getSharedPreferences("layout", 0)
   primary.edit().putString("private", "primary").apply()
   assertEquals("hidden", hidden.spaceId)
   assertFalse(hidden.getSharedPreferences("layout", 0).contains("private"))
   val original = File(f.primary.root, "home2").listFiles()!!.single { it.extension == "enc" }
   hidden.getSharedPreferences("layout", 0).edit().putString("private", "hidden").apply()
   val foreign = File(hiddenAccess.root, "home2").listFiles()!!.single { it.extension == "enc" }
   foreign.writeBytes(original.readBytes())
   try { hidden.getSharedPreferences("layout", 0).all; fail("Foreign owner accepted") } catch (_: Exception) {}
   original.writeBytes(original.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
   val corrupt = original.readBytes()
   try { primary.edit().clear().commit(); fail("Corrupt preferences overwritten") } catch (_: Exception) {}
   assertArrayEquals(corrupt, original.readBytes())
  } finally { hidden.close(); hiddenAccess.close() }
 }
 @Test fun stale_handles_and_editors_never_reactivate_after_unlock_and_wrapper_close() = Fixture().use { f ->
  val prefs = f.space.getSharedPreferences("layout", 0)
  prefs.edit().putString("value", "saved").apply()
  val editor = prefs.edit().putString("value", "stale")
  synchronized(f.gate) { f.identity = Any() }
  assertFalse(f.space.current())
  val replacementAccess = f.access()
  val replacement = SpaceContext(f.context, replacementAccess)
  try {
   refused { prefs.all }; refused { editor.commit() }; refused { prefs.edit() }
   assertEquals("saved", replacement.getSharedPreferences("layout", 0).getString("value", null))
   val replacementPrefs = replacement.getSharedPreferences("layout", 0)
   replacement.close(); refused { replacementPrefs.all }
  } finally { replacement.close(); replacementAccess.close() }
 }
 @Test fun wallpaper_uses_encrypted_bytes_and_rejects_foreign_or_retired_access() = Fixture().use { f ->
  val bytes = "PRIVATE-WALLPAPER-BYTES".encodeToByteArray()
  val id = f.space.writeWallpaper(bytes)
  assertArrayEquals(bytes, f.space.readWallpaper(id))
  val file = File(f.primary.root, "home2").listFiles()!!.single { it.name.startsWith("wallpaper-") }
  assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("PRIVATE-WALLPAPER-BYTES"))
  assertNull(f.space.readWallpaper(UUID.randomUUID().toString()))
  val old = f.space.writeWallpaper("old".encodeToByteArray())
  f.space.pruneWallpapers(setOf(id))
  assertNull(f.space.readWallpaper(old)); assertArrayEquals(bytes, f.space.readWallpaper(id))
  f.space.close(); refused { f.space.readWallpaper(id) }
 }
 @Test fun hidden_handles_remain_retired_when_the_same_vault_is_unlocked_again() = Fixture().use { f ->
  assertTrue(f.hidden.complete(f.hidden.beginAuthentication(), ByteArray(32) { 73 }))
  val oldAccess = EvidenceAccess.hidden(f.context, f.hidden) { true }
  val oldSpace = SpaceContext(f.context, oldAccess)
  val prefs = oldSpace.getSharedPreferences("layout", 0)
  prefs.edit().putString("saved", "hidden").apply()
  val pending = prefs.edit().putString("saved", "stale")
  f.hidden.lock()
  assertTrue(f.hidden.complete(f.hidden.beginAuthentication(), ByteArray(32) { 73 }))
  val freshAccess = EvidenceAccess.hidden(f.context, f.hidden) { true }
  val freshSpace = SpaceContext(f.context, freshAccess)
  try {
   assertFalse(oldSpace.current()); refused { prefs.all }; refused { pending.commit() }
   assertEquals("hidden", freshSpace.getSharedPreferences("layout", 0).getString("saved", null))
  } finally { oldSpace.close(); oldAccess.close(); freshSpace.close(); freshAccess.close() }
 }
}
