package uk.co.traynor.privategallery.core.home2

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.function.UnaryOperator
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog
import uk.co.traynor.privategallery.core.privateapps.PrivateAppStore
import uk.co.traynor.privategallery.home2.IntegratedHome
import uk.co.traynor.privategallery.home2.NovaLockEntry
import uk.co.traynor.privategallery.home2.NovaClipboardLibrary
import uk.co.traynor.privategallery.home2.NovaFilesAccess
import uk.co.traynor.privategallery.home2.NovaFilesSelection
import uk.co.traynor.privategallery.home2.NovaSnippet
import uk.co.traynor.privategallery.home2.SnippetRetention
import uk.co.traynor.privategallery.home2.NovaCalendarEvent
import uk.co.traynor.privategallery.home2.NovaCalendarLibrary
import uk.co.traynor.privategallery.home2.NovaEventRepeat
import uk.co.traynor.privategallery.home2.NovaNote
import uk.co.traynor.privategallery.home2.NovaNotesLibrary
import uk.co.traynor.privategallery.home2.NovaTaskRepeat
import uk.co.traynor.privategallery.home2.NovaSubtask
import uk.co.traynor.privategallery.home2.completeNovaTask
import uk.co.traynor.privategallery.home2.NovaTask
import uk.co.traynor.privategallery.home2.NovaTaskLibrary
import uk.co.traynor.privategallery.home2.NovaTaskPriority
import uk.co.traynor.privategallery.home2.WidgetData

/** Exercises the integrated launcher, its real encrypted Space context and production app adapter.
 * The app catalogue is memory-backed; opening an app records the production callback, without
 * making network requests or granting the launcher a browser/session implementation.
 */
class Home2IntegrationTest {
 @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
 private var fixture: SpaceFixture? = null
 private var attached by mutableStateOf(true)
 private var generation by mutableIntStateOf(0)
 private val nativeLaunches = mutableListOf<String>()
 private val appLaunches = mutableListOf<String>()
 private val browsedUrls = mutableListOf<String>()

 private class MemoryAppStore(apps: List<PrivateAppCatalog.App>) : PrivateAppStore {
  private var catalog = PrivateAppCatalog(false, apps)
  override fun owner() = PrivateAppStore.Owner.PRIMARY
  override fun read(): PrivateAppCatalog = synchronized(this) { catalog }
  override fun update(change: UnaryOperator<PrivateAppCatalog>): PrivateAppCatalog = synchronized(this) {
   change.apply(catalog).also { catalog = it }
  }
 }
 private class SpaceFixture(val hidden: Boolean, private val base: Context) : AutoCloseable {
  private val root = File(base.cacheDir, "home2-integration-${UUID.randomUUID()}")
  val context: Context = object : ContextWrapper(base) {
   override fun getFilesDir() = File(root, "files").apply { mkdirs() }
   override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
   override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
   // IntegratedHome needs the real Application for its lifecycle-bound media models.
   override fun getApplicationContext(): Context = base.applicationContext
  }
  private val vaultKey = ByteArray(32) { if (hidden) 73 else 31 }
  private val identity = Any()
  private val gate = Any()
  private val session = HiddenSession { android.os.SystemClock.elapsedRealtime() }
  val access: EvidenceAccess = if (hidden) {
   check(session.complete(session.beginAuthentication(), vaultKey.copyOf()))
   EvidenceAccess.hidden(context, session) { true }
  } else EvidenceAccess.primary(context, identity, { identity }, { true }, { vaultKey.copyOf() }, gate)
  val spaceId get() = if (hidden) "hidden" else "primary"
  fun nativeCatalogue() = uk.co.traynor.privategallery.core.privateapps.PrimaryPrivateAppAccess(
   File(context.filesDir,"private-apps"),identity,{identity},access::current,{vaultKey.copyOf()},gate)
  fun catalogueFiles() = File(context.filesDir,"private-apps").walkTopDown().filter{it.isFile}.toList()
  fun widgets() = SpaceContext(context, access).use { WidgetData(it).slots(spaceId) }
  fun originalClockVisible() = SpaceContext(context, access).use { WidgetData(it).clockVisible(spaceId) }
  override fun close() { access.close(); session.lock(); vaultKey.fill(0); root.deleteRecursively() }
 }
 private fun showHome(hidden: Boolean = false, records: List<PrivateAppCatalog.App> = emptyList(), onSwitch:(()->Unit)?=null,
  followActivityLifecycle: Boolean = false): SpaceFixture {
  val f = SpaceFixture(hidden, compose.activity).also { fixture = it }
  val bridge = if (hidden) null else NativeAppBridge(MemoryAppStore(records), f.access::current,
   { appLaunches.add(it) }, {}, { browsedUrls.add(it) })
  compose.setContent {
   if (attached) key(generation) {
    CompositionLocalProvider(LocalContext provides f.context) {
     val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
     val lifecycleState = lifecycle.currentStateFlow.collectAsState()
     val visible = !followActivityLifecycle || lifecycleState.value.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
     IntegratedHome(f.access, visible, { nativeLaunches.add(it) }, {}, {}, onSwitch, bridge)
    }
   }
  }
  compose.onNodeWithTag("preview-home").assertIsDisplayed()
  return f
 }
 @After fun closeSpace() {
  if (fixture != null) {
   try {
    compose.runOnIdle { attached = false }
    compose.waitForIdle()
   } finally { fixture?.close(); fixture = null }
  }
 }
 private fun openDrawer() {
  // The native launch fixture deliberately retains Home instead of replacing its Activity.
  // Wait for the real previous search IME to close before targeting moving Home controls.
  compose.runOnIdle {
   val view=compose.activity.window.decorView
   compose.activity.currentFocus?.clearFocus()
   (compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
    .hideSoftInputFromWindow(view.windowToken,0)
  }
  compose.waitUntil(5_000) {
   androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
    ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())!=true
  }
  compose.waitForIdle()
  compose.onNodeWithTag("open-apps").performClick()
  compose.waitUntil(5_000) {runCatching{compose.onNodeWithTag("drawer-sheet").assertIsDisplayed();true}.getOrDefault(false)}
  compose.onNodeWithTag("drawer-sheet").assertIsDisplayed()
 }
 private fun pressBackFromFocusedOwnedModal() {
  fun focusedModal()=android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull {view ->
   // WindowInspector lists this process's native roots. Quick Panel owns a separate app
   // Dialog window; the underlying Activity deliberately does not have window focus.
   view!==compose.activity.window.decorView && view.isAttachedToWindow && view.hasWindowFocus() &&
    view.context.packageName==compose.activity.packageName &&
    (view.layoutParams as? android.view.WindowManager.LayoutParams)?.type==android.view.WindowManager.LayoutParams.TYPE_APPLICATION
  }
  compose.waitUntil(5_000){compose.runOnIdle {focusedModal()!=null}}
  compose.runOnIdle {
   val modal=checkNotNull(focusedModal())
   assertNotSame("Quick Panel must own a native window distinct from the Activity",compose.activity.window.decorView.windowToken,modal.windowToken)
   android.util.Log.i("PGHomeLayoutFixture","Back target: ${modal.javaClass.name} ${modal.width}x${modal.height}, focused=${modal.hasWindowFocus()}, activityFocused=${compose.activity.window.decorView.hasWindowFocus()}")
  }
  val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
  val down=android.os.SystemClock.uptimeMillis()
  for(action in listOf(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.ACTION_UP)) {
   val event=android.view.KeyEvent(down,android.os.SystemClock.uptimeMillis(),action,android.view.KeyEvent.KEYCODE_BACK,0)
   assertTrue("Android must accept the native Back event",automation.injectInputEvent(event,true))
  }
  compose.waitForIdle()
  compose.onNodeWithTag("nova-quick-panel").assertDoesNotExist()
  compose.waitUntil(5_000){compose.runOnIdle {compose.activity.window.decorView.hasWindowFocus()}}
 }





 @Test fun clipboard_privacy_setting_survives_reopen_and_retention_updates() {
  SpaceFixture(false, compose.activity).use { f ->
   val now = java.time.Instant.parse("2027-03-26T12:00:00Z")
   val old = now.minus(java.time.Duration.ofDays(2))
   val snippets = listOf(
    NovaSnippet("regular", "Regular", "Text", "", false, false, old),
    NovaSnippet("pinned", "Pinned", "Text", "", true, false, old),
   )
   SpaceContext(f.context, f.access).use { space ->
    val library = NovaClipboardLibrary(space)
    val persisted = library.save(library.read(now), SnippetRetention.DAY, snippets, now, protectAllCopies = true)
    assertEquals(listOf("pinned"), persisted?.snippets?.map { it.id })
   }
   SpaceContext(f.context, f.access).use { space ->
    val library = NovaClipboardLibrary(space)
    val saved = library.read(now)
    assertTrue(saved.protectAllCopies)
    val updated = library.save(saved, SnippetRetention.MANUAL, saved.snippets, now)
    assertTrue(checkNotNull(updated).protectAllCopies)
   }
  }
 }

 @Test fun clipboard_retention_is_local_and_preserves_pinned_snippets() {
  SpaceFixture(false, compose.activity).use { f ->
   val now = java.time.Instant.parse("2026-11-16T12:00:00Z")
   val old = now.minus(java.time.Duration.ofDays(8))
   val expired = NovaSnippet("old", "", "Expired", "", false, true, old)
   val pinned = NovaSnippet("pin", "", "Pinned", "Work", true, true, old)
   SpaceContext(f.context, f.access).use { space ->
    val clipboard = NovaClipboardLibrary(space)
    assertNotNull(clipboard.save(clipboard.read(now), SnippetRetention.MANUAL, listOf(expired, pinned), now))
    val retentionSaved = clipboard.save(clipboard.read(now), SnippetRetention.WEEK, listOf(expired, pinned), now)
    assertEquals(listOf(pinned), retentionSaved?.snippets)
    assertTrue(NovaNotesLibrary(space).read().notes.isEmpty())
   }
   SpaceContext(f.context, f.access).use { space ->
    val reopened = NovaClipboardLibrary(space).read(now)
    assertEquals(SnippetRetention.WEEK, reopened.retention)
    assertEquals(listOf(pinned), reopened.snippets)
   }
  }
 }



 @Test fun files_picker_returns_to_the_authenticated_workspace_after_activity_pause() {
  val f = showHome(followActivityLifecycle = true)
  openDrawer()
  compose.onNodeWithTag("drawer-search").performTextInput("Nova Files")
  Espresso.closeSoftKeyboard()
  compose.onNodeWithTag("app-nova-files").assertIsDisplayed().performClick()
  compose.waitUntil(10_000) {
   compose.onAllNodes(hasText("Choose folder") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
  }
  compose.onNodeWithText("Choose folder").performClick()
  uk.co.traynor.privategallery.core.backup.BackupDocumentsUi62.folder()
  compose.waitUntil(15_000) {
   compose.onAllNodesWithText("Nova62 synthetic documents").fetchSemanticsNodes().isNotEmpty()
  }
  compose.onNodeWithTag("nova-files").assertIsDisplayed()
  SpaceContext(f.context, f.access).use { space ->
   val selected = checkNotNull(NovaFilesSelection(space).read().tree)
   assertTrue(android.provider.DocumentsContract.isTreeUri(selected))
   assertTrue(compose.activity.contentResolver.persistedUriPermissions.any { it.uri == selected && it.isReadPermission })
  }
 }

 @Test fun files_read_only_the_picker_granted_fixture_and_stop_when_space_access_retires() {
  val documents = uk.co.traynor.privategallery.core.backup.BackupDocuments62
  val tree = documents.persistedTree()
  val created = documents.createPersisted()
  try {
   SpaceFixture(false, compose.activity).use { f ->
    val files = NovaFilesAccess(f.context.contentResolver, tree, f.access::current)
    assertTrue(files.root().directory)
    assertTrue(files.children(files.root()).any { it.id == android.provider.DocumentsContract.getDocumentId(created) })
   }
   val denied = NovaFilesAccess(compose.activity.contentResolver, tree) { false }
   assertTrue(runCatching { denied.root() }.isFailure)
  } finally {
   documents.delete(created)
  }
 }

 @Test fun files_history_and_favourites_are_encrypted_and_bound_to_the_selected_tree() {
  SpaceFixture(false, compose.activity).use { f ->
   val firstTree = android.net.Uri.parse("content://example.documents/tree/first")
   val secondTree = android.net.Uri.parse("content://example.documents/tree/second")
   SpaceContext(f.context, f.access).use { space ->
    val store = NovaFilesSelection(space)
    val first = checkNotNull(store.save(store.read(), firstTree))
    val viewed = checkNotNull(store.markViewed(first, "example-id"))
    assertNull(store.toggleFavourite(first, "stale-id"))
    assertNotNull(store.toggleFavourite(viewed, "example-id"))
   }
   SpaceContext(f.context, f.access).use { space ->
    val store = NovaFilesSelection(space)
    val reopened = store.read()
    assertEquals(setOf("example-id"), reopened.favourites)
    assertEquals(listOf("example-id"), reopened.recent)
    val changed = checkNotNull(store.save(reopened, secondTree))
    assertEquals(secondTree, changed.tree)
    assertTrue(changed.favourites.isEmpty() && changed.recent.isEmpty())
   }
  }
 }

 @Test fun files_remember_only_the_explicit_tree_and_reject_path_like_names() {
  SpaceFixture(false, compose.activity).use { f ->
   val selectorUri = android.net.Uri.parse("content://example.documents/tree/root")
   SpaceContext(f.context, f.access).use { space ->
    val files = NovaFilesSelection(space)
    assertNull(files.read().tree)
    assertNotNull(files.save(files.read(), selectorUri))
   }
   SpaceContext(f.context, f.access).use { space ->
    assertEquals(selectorUri, NovaFilesSelection(space).read().tree)
    assertEquals("Receipts", NovaFilesAccess.checkedName(" Receipts "))
    for (bad in listOf("..", ".", "../folder", "nested/folder", "")) {
     assertTrue(runCatching { NovaFilesAccess.checkedName(bad) }.isFailure)
    }
   }
  }
 }

 @Test fun recurring_calendar_event_keeps_one_skipped_occurrence_across_reopen() {
  SpaceFixture(false, compose.activity).use { f ->
   val first = java.time.LocalDate.of(2027, 3, 27)
   val zone = java.time.ZoneId.of("Europe/London")
   val skipped = first.plusDays(1)
   val event = NovaCalendarEvent("series", "Daily", "", first, java.time.LocalTime.NOON,
    zone, 30, NovaEventRepeat.DAILY, setOf(skipped))
   SpaceContext(f.context, f.access).use { space ->
    val calendar = NovaCalendarLibrary(space)
    assertNotNull(calendar.save(calendar.read(), listOf(event)))
   }
   SpaceContext(f.context, f.access).use { space ->
    val restored = NovaCalendarLibrary(space).read().events.single()
    assertEquals(setOf(skipped), restored.skippedDates)
    assertTrue(restored.occurrencesOn(skipped, zone).isEmpty())
    assertEquals(1, restored.occurrencesOn(first, zone).size)
    assertEquals(1, restored.occurrencesOn(skipped.plusDays(1), zone).size)
   }
  }
 }

 @Test fun calendar_preserves_recurring_wall_time_across_dst_and_reopens_events() {
  SpaceFixture(false, compose.activity).use { f ->
   val london = java.time.ZoneId.of("Europe/London")
   val before = java.time.LocalDate.of(2026, 3, 22)
   val after = before.plusWeeks(1)
   val event = NovaCalendarEvent("weekly", "Weekly check-in", "", before,
    java.time.LocalTime.of(9, 0), london, 45, NovaEventRepeat.WEEKLY)
   SpaceContext(f.context, f.access).use { space ->
    val calendar = NovaCalendarLibrary(space)
    assertNotNull(calendar.save(calendar.read(), listOf(event)))
   }
   SpaceContext(f.context, f.access).use { space ->
    val reopened = NovaCalendarLibrary(space).read().events.single()
    assertEquals(event, reopened)
    assertEquals(java.time.Instant.parse("2026-03-22T09:00:00Z"),
     reopened.occurrencesOn(before, london).single().start)
    assertEquals(java.time.Instant.parse("2026-03-29T08:00:00Z"),
     reopened.occurrencesOn(after, london).single().start)
    assertTrue(reopened.occurrencesOn(after.plusDays(1), london).isEmpty())
   }
  }
 }

 @Test fun task_drawer_entry_opens_the_private_tasks_screen() {
  showHome()
  openDrawer()
  compose.onNodeWithTag("drawer-search").performTextInput("Nova Tasks")
  Espresso.closeSoftKeyboard()
  compose.onNodeWithTag("app-nova-tasks").assertIsDisplayed().performClick()
  compose.onNodeWithTag("nova-tasks").assertIsDisplayed()
 }

 @Test fun recurring_tasks_keep_the_monthly_anchor_without_duplicate_successors() {
  SpaceFixture(false, compose.activity).use { f ->
   val date = java.time.LocalDate.of(2027, 1, 31)
   val first = NovaTask("first", "Monthly task", due = date, repeat = NovaTaskRepeat.MONTHLY, tags = "home",
    subtasks = listOf(NovaSubtask("check", "Check the meter", true)))
   SpaceContext(f.context, f.access).use { space ->
    val library = NovaTaskLibrary(space)
    assertNotNull(library.save(library.read(), completeNovaTask(listOf(first), first.id, true)))
   }
   SpaceContext(f.context, f.access).use { space ->
    val library = NovaTaskLibrary(space)
    val saved = library.read()
    val february = saved.tasks.single { !it.completed }
    assertEquals(java.time.LocalDate.of(2027, 2, 28), february.due)
    assertEquals("home", february.tags)
    assertEquals(listOf(NovaSubtask("check", "Check the meter")), february.subtasks)
    val reopened = completeNovaTask(saved.tasks, first.id, false)
    assertEquals(saved.tasks, completeNovaTask(reopened, first.id, true))
    val march = completeNovaTask(saved.tasks, february.id, true).single { !it.completed }
    assertEquals(java.time.LocalDate.of(2027, 3, 31), march.due)
   }
  }
 }

 @Test fun tasks_keep_due_date_completion_and_project_after_reopen() {
  SpaceFixture(false, compose.activity).use { f ->
   SpaceContext(f.context, f.access).use { space ->
    val tasks = NovaTaskLibrary(space)
    val initial = tasks.read()
    val task = NovaTask("prepare", "Prepare", "Review fixtures", "Code 81",
     java.time.LocalDate.of(2026, 11, 20), NovaTaskPriority.HIGH)
    val saved = checkNotNull(tasks.save(initial, listOf(task)))
    assertNull(tasks.save(initial, emptyList()))
    assertNotNull(tasks.save(saved, listOf(task.copy(completed = true))))
   }
   SpaceContext(f.context, f.access).use { space ->
    val saved = NovaTaskLibrary(space).read().tasks.single()
    assertTrue(saved.completed)
    assertEquals("Code 81", saved.project)
    assertEquals(java.time.LocalDate.of(2026, 11, 20), saved.due)
    assertEquals(NovaTaskPriority.HIGH, saved.priority)
   }
  }
 }

 @Test fun notes_reopen_draft_after_leaving_through_nova_home() {
  showHome()
  fun openNotes() {
   openDrawer()
   compose.onNodeWithTag("drawer-search").performTextInput("Nova Notes")
   Espresso.closeSoftKeyboard()
   compose.onNodeWithTag("app-nova-notes-draft").assertIsDisplayed().performClick()
  }
  openNotes()
  compose.waitUntil(10_000) { compose.onAllNodes(hasText("New note") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
  compose.onNodeWithText("New note").performClick()
  compose.onNodeWithTag("nova-note-title").performTextInput("Private draft")
  compose.onNodeWithText("Draft saved for recovery").assertIsDisplayed()
  Espresso.closeSoftKeyboard()
  compose.onNodeWithTag("nova-home").performClick()
  openNotes()
  compose.waitUntil(10_000) { compose.onAllNodesWithText("Recovered an unsaved draft").fetchSemanticsNodes().isNotEmpty() }
  compose.onNodeWithTag("nova-note-title").assertTextContains("Private draft")
 }

 @Test fun notes_attach_a_real_android_document_and_keep_the_copy_after_source_deletion() {
  val documents = uk.co.traynor.privategallery.core.backup.BackupDocuments62
  val source = documents.createPersisted()
  val sourceId = android.provider.DocumentsContract.getDocumentId(source)
  val contents = "Synthetic Notes picker contents".encodeToByteArray()
  var sourceDeleted = false
  try {
   checkNotNull(compose.activity.contentResolver.openOutputStream(source, "wt")).use { it.write(contents) }
   val f = showHome(followActivityLifecycle = true)
   openDrawer()
   compose.onNodeWithTag("drawer-search").performTextInput("Nova Notes")
   Espresso.closeSoftKeyboard()
   compose.onNodeWithTag("app-nova-notes-draft").assertIsDisplayed().performClick()
   compose.waitUntil(10_000) { compose.onAllNodes(hasText("New note") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
   compose.onNodeWithText("New note").performClick()
   compose.onNodeWithTag("nova-note-title").performTextInput("Picker document")
   Espresso.closeSoftKeyboard()
   compose.onNodeWithText("Attach file (up to 24 KiB)").performScrollTo().performClick()
   uk.co.traynor.privategallery.core.backup.BackupDocumentsUi62.open(sourceId)
   compose.waitUntil(15_000) {
    compose.onAllNodesWithText("Attachment saved in the encrypted draft").fetchSemanticsNodes().isNotEmpty()
   }
   documents.delete(source)
   sourceDeleted = true
   SpaceContext(f.context, f.access).use { space ->
    val attachment = checkNotNull(NovaNotesLibrary(space).readDraft().note).attachments.single()
    assertArrayEquals(contents, attachment.readBytes())
   }
  } finally {
   if (!sourceDeleted) documents.delete(source)
  }
 }

 @Test fun protected_notebook_hides_content_until_primary_pin_is_confirmed() {
  SpaceFixture(false, compose.activity).use { f ->
   SpaceContext(f.context, f.access).use { space ->
    val library = NovaNotesLibrary(space)
    val note = NovaNote("locked-note", "Protected title", "Protected body", notebook = "Work")
    val initial = checkNotNull(library.save(library.read(), listOf(note)))
    assertNotNull(library.setNotebookProtected(initial, "Work", true))
    compose.setContent {
     androidx.compose.material3.MaterialTheme {
      uk.co.traynor.privategallery.home2.NovaNotesDraft(space, onBack = {},
       verifyNotebookPin = { pin -> pin.contentEquals("123456".toCharArray()) })
     }
    }
    compose.waitUntil(10_000) { compose.onAllNodesWithText("Locked: Work").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithText("Protected title").assertDoesNotExist()
    compose.onNodeWithText("Locked: Work").performClick()
    compose.onNodeWithText("Unlock notebook").performClick()
    compose.onNodeWithText("Primary Space PIN").performTextInput("654321")
    compose.onNodeWithText("Confirm").performClick()
    compose.waitUntil(10_000) {
     compose.onAllNodesWithText("Could not confirm the PIN or update the notebook.").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithText("Protected title").assertDoesNotExist()
    compose.onNodeWithText("Primary Space PIN").performTextInput("123456")
    compose.onNodeWithText("Confirm").performClick()
    compose.waitUntil(10_000) { compose.onAllNodesWithText("Protected title").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithText("Protected title").assertIsDisplayed()
   }
  }
 }

 @Test fun notes_actions_remain_reachable_on_cover_and_inner_with_large_text() {
  SpaceFixture(false, compose.activity).use { f ->
   SpaceContext(f.context, f.access).use { space ->
    var unfolded by mutableStateOf(false)
    var exits = 0
    compose.setContent {
     BoxWithConstraints(Modifier.fillMaxSize()) {
      val width = if (unfolded) 840.dp else 320.dp
      val height = 720.dp
      val density = androidx.compose.ui.platform.LocalDensity.current
      check(maxWidth >= width && maxHeight >= height) { "Fold fixture requires a larger emulator viewport" }
      CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
       androidx.compose.ui.unit.Density(density.density, fontScale = 2f)) {
       Box(Modifier.requiredSize(width, height)) {
        androidx.compose.material3.MaterialTheme {
         uk.co.traynor.privategallery.home2.NovaNotesDraft(space, onBack = { exits++ })
        }
       }
      }
     }
    }
    compose.waitUntil(10_000) {
     compose.onAllNodes(hasText("New note") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
    }
    fun verifyActions() {
     val screen = compose.onNodeWithTag("nova-notes-draft").getUnclippedBoundsInRoot()
     listOf("New note", "Reload", "Done").forEach { label ->
      val bounds = compose.onNodeWithText(label).assertIsDisplayed().assertIsEnabled().getUnclippedBoundsInRoot()
      assertTrue("$label must fit inside the workspace", (bounds.right - bounds.left) >= 48.dp &&
       bounds.left >= screen.left && bounds.right <= screen.right)
     }
    }
    verifyActions()
    compose.onNodeWithText("Done").performClick()
    compose.runOnIdle { assertEquals(1, exits); unfolded = true }
    verifyActions()
   }
  }
 }

 @Test fun notes_recover_unsaved_text_and_reject_a_stale_editor() {
  SpaceFixture(false, compose.activity).use { f ->
   val original = NovaNote("note", "Original", "Saved text")
   val edited = original.copy(body = "Unsaved private text")
   SpaceContext(f.context, f.access).use { space ->
    val library = NovaNotesLibrary(space)
    val saved = checkNotNull(library.save(library.read(), listOf(original)))
    val draft = checkNotNull(library.writeDraft(library.readDraft(), original, edited))
    assertEquals(edited, draft.note)
    assertNotNull(library.save(saved, listOf(original.copy(body = "Newer saved content"))))
   }
   SpaceContext(f.context, f.access).use { space ->
    val library = NovaNotesLibrary(space)
    val recovered = library.readDraft()
    assertEquals(edited, recovered.note)
    assertNull(library.finishDraft(recovered, delete = false))
    assertEquals("Newer saved content", library.read().notes.single().body)
    assertEquals(edited, library.readDraft().note)
    assertNotNull(library.discardDraft(recovered))
    assertNull(library.writeDraft(recovered, original, edited))
    assertNull(library.readDraft().note)
   }
  }
 }

 @Test fun notes_publish_draft_and_clear_recovery_in_the_same_transaction() {
  SpaceFixture(false, compose.activity).use { f ->
   val note = NovaNote("new-note", "Recovered title", "**Recovered body**\n- [x] Ready",
    notebook = "Personal / Plans", tags = "plans")
   SpaceContext(f.context, f.access).use { space ->
    val library = NovaNotesLibrary(space)
    val draft = checkNotNull(library.writeDraft(library.readDraft(), null, note))
    assertEquals(listOf(note), library.finishDraft(draft, delete = false)?.notes)
   }
   SpaceContext(f.context, f.access).use { space ->
    val library = NovaNotesLibrary(space)
    assertNull(library.readDraft().note)
    assertEquals(listOf(note), library.read().notes)
   }
  }
 }

 @Test fun notes_keep_the_legacy_draft_and_persist_multiple_edits_without_overwriting_newer_changes() {
  SpaceFixture(false, compose.activity).use { f ->
   SpaceContext(f.context, f.access).use { space ->
    val legacy = org.json.JSONObject().put("title", "Existing draft").put("body", "Legacy body")
    assertNotNull(NovaCoreAppDocuments(space, NovaCoreApp.NOTES).save("draft", null, legacy.toString()))
    val library = NovaNotesLibrary(space)
    val initial = library.read()
    assertEquals("Existing draft", initial.notes.single().title)
    val first = initial.notes.single()
    val second = NovaNote("checklist", "Shopping", "Apples", true)
    val changed = checkNotNull(library.save(initial, listOf(first, second)))
    assertNull(library.save(initial, emptyList()))
    val reopened = NovaNotesLibrary(space)
    assertEquals(changed, reopened.read())
    assertNotNull(reopened.save(changed, listOf(second.copy(body = "Apples and milk"))))
   }
   SpaceContext(f.context, f.access).use { space ->
    val surviving = NovaNotesLibrary(space).read().notes.single()
    assertEquals("Apples and milk", surviving.body)
    assertTrue(surviving.pinned)
   }
  }
 }

 @Test fun protected_entry_never_opens_home_until_the_user_requests_native_authentication() {
  var authenticationRequests = 0
  compose.setContent { NovaLockEntry { authenticationRequests++ } }
  compose.onNodeWithTag("nova-protected-lock").assertIsDisplayed()
  compose.onNodeWithTag("enter-novaos").assertIsDisplayed()
  compose.onNodeWithTag("preview-home").assertDoesNotExist()
  compose.runOnIdle { assertEquals(0, authenticationRequests) }
  compose.mainClock.advanceTimeBy(2_000)
  compose.runOnIdle { assertEquals(0, authenticationRequests) }
  compose.onNodeWithTag("enter-novaos").performClick()
  compose.runOnIdle { assertEquals(1, authenticationRequests) }
  compose.onNodeWithTag("preview-home").assertDoesNotExist()
 }

 @Test fun default_home_has_the_original_clock_and_native_settings_launches_the_production_route() {
  showHome()
  compose.onNodeWithText("NOVAOS").assertIsDisplayed()
  compose.onNodeWithText("Primary Space").assertIsDisplayed()
  compose.onNodeWithTag("home-original-clock").assertIsDisplayed()
  listOf("vault", "camera", "evidence", "folder:studio", "photos", "wallpaper", "settings").forEach {
   compose.onNodeWithTag("app-$it").assertIsDisplayed()
  }
  compose.onNodeWithContentDescription("Home page 2").performClick()
  compose.onNodeWithTag("app-native-settings").assertIsDisplayed().performClick()
  compose.runOnIdle { assertEquals(listOf("native-settings"), nativeLaunches); assertTrue(appLaunches.isEmpty()) }
 }

 @Test fun hidden_search_web_is_unavailable_and_a_tap_leaves_local_search_and_home_usable() {
  showHome(hidden = true)
  compose.onNodeWithContentDescription("Open Nova Search").performClick()
  compose.onNodeWithTag("nova-search-input").performTextInput("unmatched-local-query-4821")
  Espresso.closeSoftKeyboard()
  compose.onNodeWithText("No local matches. Try an app or tool name.").assertIsDisplayed()
  compose.onNodeWithText("Search the web explicitly…").performScrollTo().assertIsNotEnabled()
   .performTouchInput { click() }
  compose.onNodeWithTag("nova-search").assertIsDisplayed()
  compose.runOnIdle { assertTrue(nativeLaunches.isEmpty()); assertTrue(appLaunches.isEmpty()); assertTrue(browsedUrls.isEmpty()) }
  compose.onNodeWithTag("nova-home").performClick()
  compose.onNodeWithTag("preview-home").assertIsDisplayed()
  compose.onNodeWithText("Hidden Space").assertIsDisplayed()
 }

 @Test fun calm_now_opens_real_breathing_through_the_space_context_and_can_pause_and_return_home() {
  showHome()
  compose.onNodeWithContentDescription("Calm Now").performClick()
  compose.onNodeWithTag("nova-mind").assertIsDisplayed()
  compose.onNodeWithTag("breathing-circle").assertIsDisplayed()
  compose.onNodeWithTag("breathing-pause").performScrollTo().performClick()
  compose.onNodeWithText("Paused").assertIsDisplayed()
  compose.onNodeWithTag("breathing-pause").assertTextContains("Resume").performClick()
  compose.onNodeWithTag("breathing-pause").assertTextContains("Pause")
  compose.onNodeWithTag("nova-home").performClick()
  compose.onNodeWithTag("preview-home").assertIsDisplayed()
  compose.runOnIdle { assertTrue(nativeLaunches.isEmpty()) }
 }

 @Test fun duplicate_addresses_and_existing_port_or_ip_apps_keep_each_uuid_and_launch_its_exact_record() {
  val urls = listOf("https://example.com/app", "https://example.com/app", "https://example.org:8443/native", "https://192.0.2.10/private")
  val names = listOf("Native Duplicate One", "Native Duplicate Two", "Native Port App", "Native IP App")
  val records = urls.mapIndexed { index, url ->
   PrivateAppCatalog.App("00000000-0000-4000-8000-${(index + 1).toString().padStart(12, '0')}", names[index], url, "", "browser", "", false, false, false)
  }
  showHome(records = records)
  records.forEachIndexed { index, app ->
   openDrawer()
   compose.onNodeWithTag("drawer-search").performTextInput("Native")
   // All records are visible together, including the two that share an identical address.
   records.forEach { compose.onNodeWithTag("app-web:${it.id()}").assertIsDisplayed() }
   compose.onNodeWithTag("app-web:${app.id()}").performClick()
   compose.onNodeWithTag("preview-home").assertIsDisplayed()
   compose.runOnIdle {
    assertEquals(records.take(index + 1).map { it.id() }, appLaunches)
    assertTrue(nativeLaunches.isEmpty()); assertTrue(browsedUrls.isEmpty())
   }
  }
 }

 @Test fun adding_a_clock_widget_preserves_the_original_clock_and_its_visibility_setting_survives_reopen() {
  val f = showHome()
  compose.onNodeWithTag("home-original-clock").assertIsDisplayed()
  compose.onNodeWithContentDescription("Home menu").performClick()
  compose.onNodeWithText("Widget Centre").performClick()
  compose.onNodeWithTag("widget-centre").assertIsDisplayed()
  compose.onNodeWithTag("add-widget-clock").performScrollTo().performClick()
  compose.onNodeWithTag("preview-home").assertIsDisplayed()
  val slot = compose.runOnIdle { f.widgets().single() }
  assertEquals("clock", slot.items.single().type)
  compose.onNodeWithTag("widget-${slot.id}").assertIsDisplayed()
  compose.onNodeWithTag("home-original-clock").assertIsDisplayed()
  compose.onNodeWithTag("app-settings").performClick()
  compose.onNodeWithTag("original-clock-visible").performScrollTo().assertIsOn().performClick().assertIsOff()
  compose.onNodeWithTag("nova-home").performClick()
  compose.onNodeWithTag("home-original-clock").assertDoesNotExist()
  compose.onNodeWithTag("widget-${slot.id}").assertIsDisplayed()
  compose.runOnIdle { assertFalse(f.originalClockVisible()); generation++ }
  compose.onNodeWithTag("preview-home").assertIsDisplayed()
  compose.onNodeWithTag("home-original-clock").assertDoesNotExist()
  compose.onNodeWithTag("widget-${slot.id}").assertIsDisplayed()
  compose.runOnIdle { assertEquals(listOf(slot), f.widgets()) }
 }
 @Test fun cover_inner_and_landscape_layouts_keep_the_clock_drawer_and_quick_panel_reachable() {
  val f=SpaceFixture(false, compose.activity).also{fixture=it}
  var width by mutableIntStateOf(360)
  var height by mutableIntStateOf(720)
  val bridge=NativeAppBridge(MemoryAppStore(emptyList()),f.access::current,{}, {}, {})
  compose.setContent {
   if(attached)CompositionLocalProvider(LocalContext provides f.context){
    Box(Modifier.requiredSize(width.dp,height.dp)){
     IntegratedHome(f.access,true,{nativeLaunches.add(it)},{},{},null,bridge)
    }
   }
  }
  for(size in listOf(360 to 720,840 to 720,840 to 360)){
   compose.runOnIdle{width=size.first;height=size.second}
   compose.onNodeWithTag("preview-home").assertIsDisplayed()
   compose.onNodeWithTag("home-original-clock").assertIsDisplayed()
   openDrawer()
   val drawer = compose.onNodeWithTag("drawer-sheet").fetchSemanticsNode().boundsInRoot
   val root = compose.onAllNodes(isRoot()).fetchSemanticsNodes().maxOf { it.size.height }
   assertTrue("Drawer should occupy the whole viewport", drawer.height >= root - 2)
   compose.onNodeWithTag("drawer-search").performTextInput("Vault")
   compose.onNode(hasTestTag("app-vault") and hasAnyAncestor(hasTestTag("drawer-sheet"))).assertIsDisplayed()
   compose.onNodeWithContentDescription("Close app drawer").performClick()
   compose.onNodeWithTag("drawer-sheet").assertDoesNotExist()
   compose.waitUntil(5_000){compose.runOnIdle {compose.activity.window.decorView.hasWindowFocus()}}
   compose.onNodeWithContentDescription("Open Quick Panel").performClick()
   compose.onNodeWithTag("nova-quick-panel").assertIsDisplayed()
   pressBackFromFocusedOwnedModal()
   compose.onNodeWithTag("preview-home").assertIsDisplayed()
   compose.onNodeWithTag("home-original-clock").assertIsDisplayed()
  }
 }

 @Test fun store_install_home_launch_my_apps_and_uninstall_use_the_real_encrypted_native_catalogue(){
  val f=SpaceFixture(false,compose.activity).also{fixture=it}
  val store=f.nativeCatalogue()
  val bridge=NativeAppBridge(store,f.access::current,{appLaunches.add(it)}, {}, {})
  compose.setContent {
   if(attached)CompositionLocalProvider(LocalContext provides f.context){
    IntegratedHome(f.access,true,{nativeLaunches.add(it)},{},{},null,bridge)
   }
  }
  compose.onAllNodesWithTag("app-store").onLast().performClick()
  compose.onNodeWithTag("nova-store").assertIsDisplayed()
  compose.onNodeWithTag("store-search").performScrollTo().performTextInput("Photopea")
  Espresso.closeSoftKeyboard()
  compose.onNode(hasText("Photopea") and hasClickAction() and !hasTestTag("store-search")).performScrollTo().performClick()
  compose.onNodeWithTag("install-app").performClick()
  compose.onNodeWithTag("confirm-install").performClick()
  compose.onNodeWithTag("open-installed").assertIsDisplayed()
  val installed=compose.runOnIdle{store.read().apps.single()}
  assertEquals("Photopea",installed.name());assertTrue(installed.appStyle());assertFalse(installed.addressBar())
  assertTrue(f.catalogueFiles().isNotEmpty())
  assertTrue("Private Apps names and addresses stay encrypted on disk",f.catalogueFiles().none{
   val bytes=it.readBytes().toString(Charsets.ISO_8859_1)
   bytes.contains("Photopea")||bytes.contains(installed.startUrl())
  })
  compose.onNodeWithText("Add to Home").performClick()
  compose.onNodeWithTag("preview-home").assertIsDisplayed()
  compose.onNodeWithTag("app-web:"+installed.id()).performClick()
  compose.runOnIdle{assertEquals(listOf(installed.id()),appLaunches);assertTrue(nativeLaunches.isEmpty())}
  compose.onAllNodesWithTag("app-store").onLast().performClick()
  compose.onNodeWithTag("store-my-apps").performScrollTo().performClick()
  compose.onNode(hasText("Photopea") and hasClickAction() and !hasTestTag("store-search")).performScrollTo().performClick()
  compose.onNodeWithTag("install-app").assertDoesNotExist()
  compose.onNodeWithTag("open-installed").assertIsDisplayed()
  compose.onNodeWithText("Uninstall").performClick()
  compose.onNodeWithText("Uninstall this shortcut?").assertIsDisplayed()
  compose.onNode(hasText("Uninstall") and hasAnyAncestor(hasAnyDescendant(hasText("Uninstall this shortcut?")))).performClick()
  compose.runOnIdle{assertTrue(store.read().apps.isEmpty())}
  compose.onNode(hasContentDescription("Back") and hasAnyAncestor(hasTestTag("nova-store"))).performClick()
  compose.onNodeWithTag("app-web:"+installed.id()).assertDoesNotExist()
 }

 @Test fun owner_shortcut_names_survive_catalogue_refresh_and_reopening_without_changing_native_settings(){
  val f=SpaceFixture(false, compose.activity).also{fixture=it}
  val original=PrivateAppCatalog.App("00000000-0000-4000-8000-000000000061","Native Original","https://example.test/","","standalone","",false,false,true)
  val store=MemoryAppStore(listOf(original));val bridge=NativeAppBridge(store,f.access::current,{},{},{})
  SpaceContext(f.context,f.access).use{context->
   val data=uk.co.traynor.privategallery.home2.PreviewData(context,bridge)
   data.rename("web:"+original.id(),"My quiet app")
   data.refreshInstalled()
   assertEquals("My quiet app",data.app("web:"+original.id())!!.name)
   assertEquals(original,store.read().apps.single())
   val reopened=uk.co.traynor.privategallery.home2.PreviewData(context,bridge)
   assertEquals("My quiet app",reopened.app("web:"+original.id())!!.name)
  }
 }

 @Test fun native_theme_migration_is_once_only_and_preserves_home_customisations(){
  val f=SpaceFixture(false,compose.activity).also{fixture=it}
  SpaceContext(f.context,f.access).use{context->
   val old=uk.co.traynor.privategallery.home2.PreviewData(context,null)
   old.setTheme("light")
   old.change{it.copy(columns=5,iconSize=68,wallpaper=it.wallpaper.copy(builtin="Dusk",dim=.35f))}
   val layout=old.profile
   check(context.getSharedPreferences("nova_preview_v1",Context.MODE_PRIVATE).edit().remove("native-theme-bridged").commit())
   val published=mutableListOf<String>()
   val migrated=uk.co.traynor.privategallery.home2.PreviewData(context,null,null,null,"dark",{published.add(it)})
   assertEquals("dark",migrated.state.theme);assertEquals(layout,migrated.profile)
   assertTrue(migrated.commitTheme("system"));assertEquals(listOf("system"),published)
   val reopened=uk.co.traynor.privategallery.home2.PreviewData(context,null,null,null,"light",null)
   assertEquals("system",reopened.state.theme);assertEquals(layout,reopened.profile)
  }
 }
 @Test fun hidden_theme_never_imports_primary_native_choice_and_cannot_write_after_retirement(){
  val f=SpaceFixture(true,compose.activity).also{fixture=it}
  SpaceContext(f.context,f.access).use{context->
   val data=uk.co.traynor.privategallery.home2.PreviewData(context,null,null,null,"light",null)
   assertEquals("dark",data.state.theme)
   assertTrue(data.commitTheme("system"))
   val reopened=uk.co.traynor.privategallery.home2.PreviewData(context,null,null,null,"dark",null)
   assertEquals("system",reopened.state.theme)
   f.access.close()
   assertTrue(runCatching{data.setTheme("light")}.isFailure)
  }
 }

 @Test fun primary_home_discards_stale_switch_actions_across_menu_quick_panel_search_and_recents(){
  var switched=0
  val f=showHome(onSwitch={switched++})
  SpaceContext(f.context,f.access).use{context->
   val data=uk.co.traynor.privategallery.home2.PreviewData(context,null,{switched++})
   assertNull(data.onSwitch);data.switchProfile();assertEquals(0,switched)
  }
  fun assertConcealed(){
   compose.onAllNodesWithText("Switch Space").assertCountEquals(0)
   compose.onAllNodesWithText("Return to Primary").assertCountEquals(0)
   compose.onAllNodes(hasText("Hidden",substring=true) and !hasSetTextAction()).assertCountEquals(0)
  }
  compose.onNodeWithContentDescription("Home menu").performClick();assertConcealed()
  compose.onNodeWithText("Edit Home").performClick()
  compose.onNodeWithTag("nova-home-settings").assertIsDisplayed();assertConcealed();Espresso.pressBack()
  compose.onNodeWithContentDescription("Home menu").performClick()
  compose.onNode(hasText("Apps") and hasAnyAncestor(isPopup())).performClick()
  compose.onNodeWithTag("drawer-sheet").assertIsDisplayed();assertConcealed()
  compose.onNodeWithTag("drawer-search").performTextInput("Hidden");assertConcealed()
  Espresso.closeSoftKeyboard();compose.onNodeWithContentDescription("Close app drawer").performClick()
  compose.onNodeWithContentDescription("Open Quick Panel").performClick()
  compose.onNodeWithTag("quick-overlay").assertIsDisplayed();assertConcealed();Espresso.pressBack()
  compose.onNodeWithContentDescription("Open Nova Search").performClick()
  compose.onNodeWithTag("nova-search-input").performTextInput("Hidden");assertConcealed()
  Espresso.closeSoftKeyboard();Espresso.pressBack()
  compose.onNodeWithTag("nova-recents").performClick()
  compose.onNodeWithTag("nova-recents-screen").assertIsDisplayed();assertConcealed()
  compose.runOnIdle{assertEquals(0,switched)}
 }
 @Test fun authenticated_hidden_home_names_its_exit_return_to_primary(){
  var returned=0
  showHome(hidden=true,onSwitch={returned++})
  compose.onNodeWithContentDescription("Home menu").performClick()
  compose.onNodeWithText("Switch Space").assertDoesNotExist()
  compose.onNodeWithText("Return to Primary").performClick()
  compose.runOnIdle{assertEquals(1,returned)}
 }

}
