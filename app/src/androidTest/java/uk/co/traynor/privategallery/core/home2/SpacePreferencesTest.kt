package uk.co.traynor.privategallery.core.home2

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.home2.NovaNote
import uk.co.traynor.privategallery.home2.NovaNotesLibrary
import uk.co.traynor.privategallery.home2.NovaReminders
import uk.co.traynor.privategallery.home2.NovaTask
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.hidden.HiddenCredentialStore
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
 @Test fun core_app_documents_are_encrypted_namespaced_and_reject_stale_saves() = Fixture().use { f ->
  val marker = "private-code81-draft-value"
  val notes = NovaCoreAppDocuments(f.space, NovaCoreApp.NOTES)
  assertNull(notes.read("draft"))
  val first = checkNotNull(notes.save("draft", null, """{"body":"$marker"}"""))
  assertNull(NovaCoreAppDocuments(f.space, NovaCoreApp.TASKS).read("draft"))
  SpaceContext(f.context, f.primary).use { reopened ->
   val writer = NovaCoreAppDocuments(reopened, NovaCoreApp.NOTES)
   assertEquals(first, writer.read("draft"))
   val second = checkNotNull(writer.save("draft", first.revision, """{"body":"$marker updated"}"""))
   assertNull(notes.save("draft", first.revision, """{"body":"stale"}"""))
   assertEquals(second, notes.read("draft"))
  }
  assertTrue(f.root.walkTopDown().any { it.isFile && it.extension == "enc" })
  assertFalse(f.root.walkTopDown().filter { it.isFile }.any {
   it.readBytes().toString(Charsets.ISO_8859_1).contains(marker)
  })
  f.space.close()
  refused { notes.read("draft") }
 }

 @Test fun attachment_is_recovered_after_reauthentication_and_portable_without_source_access() = Fixture().use { f ->
  val notes = NovaNotesLibrary(f.space)
  val note = NovaNote("attachment-note", "Private", "Draft")
  assertNotNull(notes.writeDraft(notes.readDraft(), null, note))
  val source = File(f.root, "example.txt").apply { writeText("PRIVATE_ATTACHMENT_TEST") }
  val attachment = NovaNoteAttachment("file-example", "example.txt", "text/plain",
   java.util.Base64.getEncoder().encodeToString(source.readBytes()))
  f.space.close()
  f.identity = Any()
  f.access().use { reopened ->
   SpaceContext(f.context, reopened).use { space ->
    val writer = NovaNotesLibrary(space)
    assertFalse(writer.attachToDraft("another-note", attachment))
    assertTrue(writer.attachToDraft(note.id, attachment))
    assertTrue(writer.attachToDraft(note.id, attachment))
    assertTrue(source.delete())
    val draft = writer.readDraft()
    assertEquals(listOf(attachment), draft.note?.attachments)
    assertNotNull(writer.finishDraft(draft, false))
    val values = space.backupValues()
    try {
     writer.save(writer.read(), emptyList())
     space.restoreBackupValues(values)
     val recovered = writer.read().notes.single().attachments.single()
     assertArrayEquals("PRIVATE_ATTACHMENT_TEST".toByteArray(), recovered.readBytes())
    } finally {
     values.values.forEach { it.fill(0) }
    }
   }
  }
 }

 @Test fun reminders_persist_reschedule_and_cancel_through_the_android_alarm() = Fixture().use { f ->
  val context = object : ContextWrapper(f.context) {
   override fun getApplicationContext(): Context = this
  }
  val zone = java.time.ZoneId.systemDefault()
  val date = java.time.LocalDate.now(zone).plusDays(2)
  val first = NovaTask("reminder-task", "Private task", due = date, remindAt = java.time.LocalTime.of(10, 0))
  val edited = first.copy(remindAt = java.time.LocalTime.of(11, 0))
  fun next(time: java.time.LocalTime) = date.atTime(time).atZone(zone).toInstant()
  fun registered() = android.app.PendingIntent.getBroadcast(context, 0,
   android.content.Intent(context, uk.co.traynor.privategallery.home2.NovaReminderReceiver::class.java).setAction(NovaReminders.ALARM),
   android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE)
  SpaceContext(context, f.primary).use { store ->
   try {
    NovaReminders.syncTasks(store, listOf(first))
    assertNotNull(registered())
    assertEquals(next(checkNotNull(first.remindAt)), NovaReminders.nextScheduledAt(context, zone))
    NovaReminders.dispatch(context, shouldNotify = false)
    assertEquals(next(checkNotNull(first.remindAt)), NovaReminders.nextScheduledAt(context, zone))
    NovaReminders.syncTasks(store, listOf(edited))
    NovaReminders.syncTasks(store, listOf(edited))
    assertEquals(next(checkNotNull(edited.remindAt)), NovaReminders.nextScheduledAt(context, zone))
   } finally {
    NovaReminders.syncTasks(store, emptyList())
   }
  }
  assertNull(NovaReminders.nextScheduledAt(context, zone))
  assertNull(registered())
 }

 @Test fun due_task_dispatch_honours_android_permission_and_posts_only_once() = Fixture().use { f ->
  val context = f.context
  check(uk.co.traynor.privategallery.BuildConfig.DEBUG && context.packageName.endsWith(".hiddenprototype"))
  val zone = java.time.ZoneId.systemDefault()
  val day = java.time.LocalDate.now(zone).plusDays(2)
  val task = NovaTask("utility-delivery", "Synthetic private reminder title", due = day,
   remindAt = java.time.LocalTime.NOON)
  val clock = java.time.Clock.fixed(day.atTime(java.time.LocalTime.NOON).atZone(zone).toInstant(), zone)
  val manager = context.getSystemService(android.app.NotificationManager::class.java)
  val expected = when (InstrumentationRegistry.getArguments().getString("novaReminderPermission")) {
   "granted" -> true
   "denied" -> false
   null -> NovaReminders.canNotify(context)
   else -> error("Unknown reminder permission fixture")
  }
  assertEquals(expected, NovaReminders.canNotify(context))
  fun own() = manager.activeNotifications.firstOrNull { it.id == 81001 && it.notification.channelId == "nova-core-reminders" }
  fun awaitNotification(present: Boolean) {
   val deadline = android.os.SystemClock.elapsedRealtime() + 3_000L
   while ((own() != null) != present && android.os.SystemClock.elapsedRealtime() < deadline) {
    android.os.SystemClock.sleep(40)
   }
   assertEquals(present, own() != null)
  }
  try {
   manager.cancel(81001)
   awaitNotification(false)
   NovaReminders.syncTasks(f.space, listOf(task))
   NovaReminders.dispatch(context, shouldNotify = true, clock = clock)
   awaitNotification(expected)
   if (expected) {
    val notice = checkNotNull(own()).notification
    assertEquals("NovaOS reminder", notice.extras.getCharSequence(android.app.Notification.EXTRA_TITLE))
    assertEquals("Open NovaOS to check your tasks and calendar.",
     notice.extras.getCharSequence(android.app.Notification.EXTRA_TEXT))
   }
   manager.cancel(81001)
   awaitNotification(false)
   NovaReminders.dispatch(context, shouldNotify = true, clock = clock)
   assertNull(own())
   assertNull(NovaReminders.nextScheduledAt(context, zone))
  } finally {
   NovaReminders.syncTasks(f.space, emptyList())
   manager.cancel(81001)
  }
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
  verifyHiddenNotesAuthentication(f)
 }

 private fun verifyHiddenNotesAuthentication(f: Fixture) {
  val namespace = "notes-${UUID.randomUUID()}"
  val credentials = HiddenCredentialStore(f.context, namespace)
  val pin = "728491"
  var clock = 0L
  val session = HiddenSession { clock }
  val primaryNotes = NovaNotesLibrary(f.space)
  val primaryNote = NovaNote("same-id", "Primary example", "Primary notes fixture")
  val hiddenNote = NovaNote("same-id", "Hidden example", "Hidden notes fixture")
  try {
   credentials.create(pin.toCharArray()).fill(0)
   refused { EvidenceAccess.hidden(f.context, session) { true }.close() }
   assertNull(credentials.unlockForComparison("123456".toCharArray()))
   assertFalse(session.isUnlocked)
   assertTrue(session.complete(session.beginAuthentication(), credentials.unlock(pin.toCharArray())))

   EvidenceAccess.hidden(f.context, session) { true }.use { access ->
    SpaceContext(f.context, access).use { hidden ->
     val notes = NovaNotesLibrary(hidden)
     val hiddenBefore = notes.read()
     assertTrue(hiddenBefore.notes.isEmpty())
     val saved = checkNotNull(notes.save(hiddenBefore, listOf(hiddenNote)))
     assertEquals(listOf(hiddenNote), notes.read().notes)
     assertTrue("Primary cannot see the Hidden note", primaryNotes.read().notes.isEmpty())
     assertNotNull(primaryNotes.save(primaryNotes.read(), listOf(primaryNote)))
     assertEquals(listOf(hiddenNote), notes.read().notes)
     val hiddenFiles = access.root.walkTopDown().filter { it.isFile }.toList()
     assertTrue(hiddenFiles.any { it.extension == "enc" })
     assertFalse(hiddenFiles.any { it.readBytes().toString(Charsets.ISO_8859_1).contains(hiddenNote.body) })

     session.onBackgroundAt(30_000L, clock)
     clock = 30_001L
     session.onForeground()
     assertFalse(session.isUnlocked)
     assertFalse(hidden.current())
     refused { notes.read() }
     refused { notes.save(saved, listOf(hiddenNote.copy(body = "expired write"))) }
     assertEquals(listOf(primaryNote), primaryNotes.read().notes)

     assertTrue(session.complete(session.beginAuthentication(), credentials.unlock(pin.toCharArray())))
     refused { notes.read() }
     EvidenceAccess.hidden(f.context, session) { true }.use { reopened ->
      SpaceContext(f.context, reopened).use { fresh ->
       assertEquals(listOf(hiddenNote), NovaNotesLibrary(fresh).read().notes)
      }
     }
    }
   }
  } finally {
   session.lock()
   val alias = "${f.context.packageName}.hidden.v1.$namespace"
   KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
  }
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
   refused { f.space.commitAuthorizedWrite { fail("A retired space committed a write") } }
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
