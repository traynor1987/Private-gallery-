package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import uk.co.traynor.privategallery.core.privateapps.*
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.ui.AppTheme

/** Production drawer against encrypted stores; no network or browser authority is fabricated. */
class PrivateAppsDrawer50Test {
 @get:Rule val compose=createComposeRule()
 private fun app(name:String,auth:Boolean=false)=PrivateAppCatalog.App(UUID.randomUUID().toString(),name,"https://example.test/${name.lowercase()}/","","standalone","",false,true,auth)
 private fun ready(id:String) {compose.waitUntil(5000){compose.onAllNodesWithTag("private-app-tile-$id").fetchSemanticsNodes().isNotEmpty()}}
 private fun windowRoots():List<android.view.View> {
  var roots=emptyList<android.view.View>()
  if(android.os.Build.VERSION.SDK_INT>=29)compose.runOnIdle{roots=android.view.inspector.WindowInspector.getGlobalWindowViews()}
  return roots
 }
 private fun assertProtectedDropdown(before:List<android.view.View>) {
  if(android.os.Build.VERSION.SDK_INT>=29)compose.runOnIdle {
   val popup=android.view.inspector.WindowInspector.getGlobalWindowViews().filter{view->before.none{it===view} && view.isAttachedToWindow}
   assertTrue("Opening actions creates a separate popup window",popup.isNotEmpty())
   popup.forEach {view ->val flags=(view.layoutParams as android.view.WindowManager.LayoutParams).flags
    assertTrue("App actions popup always protects its contents",flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)}
  }
 }
 private fun assertProtectedDrawer() {
  androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
   .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check {view,failure ->
    if(failure!=null)throw failure
    val flags=(view.rootView.layoutParams as android.view.WindowManager.LayoutParams).flags
    assertTrue(flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
   }
 }
 private fun action(name:String,action:String) {
  val before=windowRoots()
  compose.onNodeWithContentDescription("App actions for $name").performClick()
  compose.onNodeWithText(action).assertIsDisplayed()
  assertProtectedDropdown(before)
  compose.onNodeWithText(action).performClick()
 }
 @Test fun tiles_search_long_press_and_overflow_preserve_authenticated_actions() {
  val root=File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,"drawer-actions-${UUID.randomUUID()}")
  val key=ByteArray(32){50};var live=true;var opened="";var closed=0;var authentication:((Boolean)->Unit)?=null;var mounted by mutableStateOf(true)
  val store=PrimaryPrivateAppStore(root,key,{live},{check(live);it()})
  val alpha=app("Alpha",true);val beta=app("Beta")
  store.update{it.upsert(alpha).upsert(beta)}
  fun newController()=PrivateAppsController(store.owner(),{live},{a,b,id->PrivateAppCommands.execute(store,a,b,id)},{opened=it},authenticate={authentication=it})
  var controller by mutableStateOf(newController())
  try {
   compose.setContent{PrivateGalleryTheme{if(mounted)PrivateAppsLibrary(controller,{closed++;mounted=false})}}
   ready(alpha.id())
   assertProtectedDrawer()
   compose.onNodeWithText("Open").assertDoesNotExist()
   compose.onNodeWithContentDescription("Edit Alpha").assertDoesNotExist()
   compose.onNodeWithTag("private-app-tile-${alpha.id()}").performClick()
   compose.runOnIdle{assertEquals("",opened);checkNotNull(authentication).invoke(false);assertEquals("",opened)}
   compose.onNodeWithTag("private-app-tile-${alpha.id()}").performClick()
   compose.runOnIdle{checkNotNull(authentication).invoke(true);assertEquals(alpha.startUrl(),opened);assertEquals(1,closed);assertFalse(mounted)}
   compose.onNodeWithTag("private-app-search").assertDoesNotExist()
   compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
   compose.runOnIdle{controller=newController();mounted=true};ready(alpha.id())
   compose.onNodeWithTag("private-app-search").performTextInput("beta")
   compose.onNodeWithTag("private-app-tile-${alpha.id()}").assertDoesNotExist()
   compose.onNodeWithTag("private-app-tile-${beta.id()}").assertIsDisplayed()
   compose.onNodeWithTag("private-app-search").performTextClearance()
   val accessibleLongClick=compose.onNodeWithTag("private-app-tile-${alpha.id()}").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.OnLongClick]
   assertEquals("App actions",accessibleLongClick.label)
   val beforeLongPress=windowRoots()
   compose.onNodeWithTag("private-app-tile-${alpha.id()}").performTouchInput{longClick()}
   compose.onNodeWithText("Pin").assertIsDisplayed()
   assertProtectedDropdown(beforeLongPress)
   compose.onNodeWithText("Pin").performClick()
   compose.waitUntil(5000){store.read().apps.single{it.id()==alpha.id()}.pinned()}
   action("Alpha","Unpin")
   compose.waitUntil(5000){!store.read().apps.single{it.id()==alpha.id()}.pinned()}
   action("Alpha","Edit")
   compose.onNodeWithTag("private-app-name").performTextReplacement("Renamed")
   compose.onNodeWithText("Save").performClick()
   compose.waitUntil(5000){store.read().apps.any{it.name()=="Renamed"}}
   action("Renamed","App Settings")
   compose.onNodeWithText("Private app settings").assertIsDisplayed()
   compose.onNodeWithTag("private-app-address-toggle").performClick()
   compose.waitUntil(5000){!store.read().apps.single{it.id()==alpha.id()}.addressBar()}
   compose.onNodeWithText("Done").performClick()
   action("Renamed","Remove")
   compose.onNodeWithText("Remove app").performClick()
   compose.waitUntil(5000){store.read().apps.none{it.id()==alpha.id()}}
   assertEquals(listOf(beta.id()),store.read().apps.map{it.id()})
  }finally{live=false;compose.waitForIdle();key.fill(0);root.deleteRecursively()}
 }
 @Test fun filtered_reorder_uses_full_persisted_order_and_survives_reopen() {
  val root=File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,"drawer-order-${UUID.randomUUID()}")
  val key=ByteArray(32){51};var live=true;var mounted by mutableStateOf(true);var closes=0
  val apps=listOf(app("Alpha"),app("Beta"),app("Gamma"),app("Delta"))
  val store=PrimaryPrivateAppStore(root,key,{live},{it()});store.update{catalog->apps.fold(catalog){c,a->c.upsert(a)}}
  fun controller()=PrivateAppsController(store.owner(),{live},{a,b,id->PrivateAppCommands.execute(store,a,b,id)},{})
  var owner by mutableStateOf(controller())
  try {
   compose.setContent{PrivateGalleryTheme{if(mounted)PrivateAppsLibrary(owner,{closes++;mounted=false})}}
   ready(apps.first().id())
   compose.onNodeWithTag("private-app-search").performTextInput("Delta")
   action("Delta","Reorder")
   compose.onNodeWithText("Tap an app, then tap its new position.").assertIsDisplayed()
   compose.onNodeWithTag("private-app-search").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,androidx.compose.ui.text.AnnotatedString("")))
   ready(apps.first().id())
   compose.onNodeWithTag("private-app-tile-${apps.last().id()}").performClick()
   compose.onNodeWithTag("private-app-tile-${apps.first().id()}").performClick()
   val expected=listOf(apps[3],apps[0],apps[1],apps[2]).map{it.id()}
   compose.waitUntil(5000){store.read().apps.map{it.id()}==expected}
   compose.onNodeWithText("Done").performClick()
   compose.onNodeWithText("Close").performClick()
   compose.onNodeWithTag("private-app-grid").assertDoesNotExist()
   compose.onNodeWithTag("private-app-search").assertDoesNotExist()
   compose.runOnIdle{assertFalse(mounted);assertEquals(1,closes)}
   // A missing Dialog alone does not prove the enclosing composition has disposed.
   compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
   compose.runOnIdle{owner=controller();mounted=true}
   ready(apps.last().id())
   compose.runOnIdle{assertTrue(mounted);assertEquals(1,closes)}
   assertEquals(expected,PrimaryPrivateAppStore(root,key,{live},{it()}).read().apps.map{it.id()})
   val first=compose.onNodeWithTag("private-app-tile-${apps.last().id()}").fetchSemanticsNode().boundsInRoot
   val second=compose.onNodeWithTag("private-app-tile-${apps.first().id()}").fetchSemanticsNode().boundsInRoot
   assertTrue("Persisted first item renders before second",first.top<second.top || (first.top==second.top && first.left<second.left))
  }finally{live=false;compose.runOnIdle{mounted=false};compose.waitForIdle();key.fill(0);root.deleteRecursively()}
 }
 @Test fun drawer_adapts_themes_fold_and_large_text_and_revokes_each_encrypted_owner() {
  val root=File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,"drawer-owners-${UUID.randomUUID()}")
  val key=ByteArray(32){52};val hidden=HiddenSession{100L};assertTrue(hidden.complete(hidden.beginAuthentication(),key.copyOf()))
  var live=true;var closed=0;var width by mutableStateOf(360);var dark by mutableStateOf(false);var large by mutableStateOf(false);var layoutPhase by mutableStateOf(true)
  val primary=PrimaryPrivateAppStore(File(root,"primary"),key,{live},{check(live);it()})
  val vpn=HiddenPrivateAppStore(root,hidden,hidden.authority(),false)
  val tor=HiddenPrivateAppStore(root,hidden,hidden.authority(),true)
  val primaryApp=app("PrimarySecret");val hiddenApp=app("HiddenSecret");val torApp=app("TorSecret")
  val primaryApps=listOf(primaryApp)+List(7){app("Tile$it")}
  primary.update{catalog->primaryApps.fold(catalog){c,a->c.upsert(a)}};vpn.update{it.upsert(hiddenApp)};tor.update{it.upsert(torApp)}
  fun controller(store:PrivateAppStore)=PrivateAppsController(store.owner(),{live && (store.owner()==PrivateAppStore.Owner.PRIMARY || hidden.isUnlocked)},{a,b,id->PrivateAppCommands.execute(store,a,b,id)},{})
  var owner by mutableStateOf(controller(primary))
  try {
   var actualDensity=0f
   compose.setContent {
    val density=LocalDensity.current
    actualDensity=density.density
    CompositionLocalProvider(LocalDensity provides Density(density.density,if(large)1.6f else 1f)) {
     PrivateGalleryTheme(if(dark)AppTheme.DARK else AppTheme.LIGHT) {
      if(layoutPhase)Box(Modifier.width(width.dp).fillMaxHeight()) {
       PrivateAppsDrawerGrid(primaryApps,primaryApps,true,false,null,null,{_,_->},{},{},{_,_->})
      } else PrivateAppsLibrary(owner,{closed++})
     }
    }
   }
   for(theme in listOf(false,true))for(size in listOf(360,840,600,360))for(font in listOf(false,true)) {
    compose.runOnIdle{dark=theme;width=size;large=font}
    ready(primaryApp.id())
    val gridWidth=compose.onNodeWithTag("private-app-grid").fetchSemanticsNode().boundsInRoot.width/actualDensity
    assertEquals("Production grid receives the requested real dp viewport",size.toFloat(),gridWidth,1f)
    compose.onNodeWithContentDescription("App actions for PrimarySecret").assertIsDisplayed()
    if(size==360 && !font) {
     val bounds=primaryApps.take(5).map{compose.onNodeWithTag("private-app-tile-${it.id()}").fetchSemanticsNode().boundsInRoot}
     assertEquals("Cover drawer has four columns",bounds[0].top,bounds[3].top,1f)
     assertTrue("Fifth app starts next row",bounds[4].top>bounds[0].top)
    }
    if(size==360 && font) {
     val first=compose.onNodeWithTag("private-app-tile-${primaryApps[0].id()}").fetchSemanticsNode().boundsInRoot
     val third=compose.onNodeWithTag("private-app-tile-${primaryApps[2].id()}").fetchSemanticsNode().boundsInRoot
     assertTrue("Large text reduces cover drawer columns",third.top>first.top)
    }
   }
   compose.runOnIdle{layoutPhase=false}
   ready(primaryApp.id())
   compose.onNodeWithTag("private-app-search").assertIsDisplayed()
   compose.onNodeWithText("Add App").assertIsDisplayed()
   compose.onNodeWithText("HiddenSecret").assertDoesNotExist()
   compose.onNodeWithText("TorSecret").assertDoesNotExist()
   compose.runOnIdle{owner=controller(vpn)};ready(hiddenApp.id())
   compose.onNodeWithText("PrimarySecret").assertDoesNotExist();compose.onNodeWithText("TorSecret").assertDoesNotExist()
   compose.runOnIdle{owner=controller(tor)};ready(torApp.id())
   compose.onNodeWithText("PrimarySecret").assertDoesNotExist();compose.onNodeWithText("HiddenSecret").assertDoesNotExist()
   compose.runOnIdle{hidden.lock()}
   compose.waitUntil(5000){closed==1}
   compose.onNodeWithText("TorSecret").assertDoesNotExist()
   assertTrue(hidden.complete(hidden.beginAuthentication(),key.copyOf()))
   val freshTor=HiddenPrivateAppStore(root,hidden,hidden.authority(),true)
   compose.runOnIdle{owner=controller(freshTor)};ready(torApp.id())
   assertEquals(primaryApps,primary.read().apps)
   assertEquals(listOf(hiddenApp),HiddenPrivateAppStore(root,hidden,hidden.authority(),false).read().apps)
   assertEquals(listOf(torApp),freshTor.read().apps)
   root.walkTopDown().filter{it.isFile}.forEach {file ->val bytes=file.readBytes();try{val text=bytes.toString(Charsets.ISO_8859_1);listOf("PrimarySecret","HiddenSecret","TorSecret").forEach{assertFalse(text.contains(it))}}finally{bytes.fill(0)}}
  }finally{live=false;hidden.lock();compose.waitForIdle();key.fill(0);root.deleteRecursively()}
 }
 @Test fun closing_drawer_retires_pending_pin_before_fresh_presentation_can_launch() {
  val root=File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,"drawer-pin-${UUID.randomUUID()}")
  val key=ByteArray(32){53};var live=true;var mounted by mutableStateOf(true);var opened=0;var closed=0
  var authentication:((Boolean)->Unit)?=null
  val store=PrimaryPrivateAppStore(root,key,{live},{check(live);it()})
  val protectedApp=app("Protected",true);store.update{it.upsert(protectedApp)}
  val controller=PrivateAppsController(store.owner(),{live},{a,b,id->PrivateAppCommands.execute(store,a,b,id)},{opened++},authenticate={authentication=it})
  try {
   compose.setContent{PrivateGalleryTheme{if(mounted)PrivateAppsLibrary(controller,{closed++;mounted=false})}}
   ready(protectedApp.id())
   compose.onNodeWithTag("private-app-tile-${protectedApp.id()}").performClick()
   lateinit var retired:((Boolean)->Unit)
   compose.runOnIdle{retired=checkNotNull(authentication);assertEquals(0,opened)}
   assertProtectedDrawer()
   compose.onNodeWithText("Close").performClick()
   compose.onNodeWithTag("private-app-search").assertDoesNotExist()
   compose.onNodeWithTag("private-app-grid").assertDoesNotExist()
   compose.runOnIdle{assertEquals(1,closed);mounted=true}
   ready(protectedApp.id())
   compose.runOnIdle{retired(true);assertEquals("A retired drawer cannot launch after a later presentation",0,opened);assertEquals(1,closed);assertTrue(mounted)}
   compose.onNodeWithTag("private-app-tile-${protectedApp.id()}").assertIsDisplayed().performClick()
   compose.runOnIdle{assertNotSame(retired,authentication);checkNotNull(authentication).invoke(true);assertEquals(1,opened);assertEquals(2,closed);assertFalse(mounted)}
   compose.onNodeWithTag("private-app-search").assertDoesNotExist()
  }finally{live=false;compose.runOnIdle{mounted=false};compose.waitForIdle();key.fill(0);root.deleteRecursively()}
 }

}
