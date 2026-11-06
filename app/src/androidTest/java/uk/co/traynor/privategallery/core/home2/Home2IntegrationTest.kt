package uk.co.traynor.privategallery.core.home2

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
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
  private val session = HiddenSession { android.os.SystemClock.elapsedRealtime() }
  val access: EvidenceAccess = if (hidden) {
   check(session.complete(session.beginAuthentication(), vaultKey.copyOf()))
   EvidenceAccess.hidden(context, session) { true }
  } else EvidenceAccess.primary(context, identity, { identity }, { true }, { vaultKey.copyOf() }, Any())
  val spaceId get() = if (hidden) "hidden" else "primary"
  fun widgets() = SpaceContext(context, access).use { WidgetData(it).slots(spaceId) }
  fun originalClockVisible() = SpaceContext(context, access).use { WidgetData(it).clockVisible(spaceId) }
  override fun close() { access.close(); session.lock(); vaultKey.fill(0); root.deleteRecursively() }
 }
 private fun showHome(hidden: Boolean = false, records: List<PrivateAppCatalog.App> = emptyList()): SpaceFixture {
  val f = SpaceFixture(hidden, compose.activity).also { fixture = it }
  val bridge = if (hidden) null else NativeAppBridge(MemoryAppStore(records), f.access::current,
   { appLaunches.add(it) }, {}, { browsedUrls.add(it) })
  compose.setContent {
   if (attached) key(generation) {
    CompositionLocalProvider(LocalContext provides f.context) {
     IntegratedHome(f.access, true, { nativeLaunches.add(it) }, {}, {}, null, bridge)
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
  compose.onNodeWithTag("open-apps").performClick()
  compose.onNodeWithTag("drawer-sheet").assertIsDisplayed()
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
   compose.onNodeWithContentDescription("Close app drawer").performClick()
   compose.onNodeWithContentDescription("Open Quick Panel").performClick()
   compose.onNodeWithTag("nova-quick-panel").assertIsDisplayed()
   Espresso.pressBack()
   compose.onNodeWithTag("preview-home").assertIsDisplayed()
  }
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

}
