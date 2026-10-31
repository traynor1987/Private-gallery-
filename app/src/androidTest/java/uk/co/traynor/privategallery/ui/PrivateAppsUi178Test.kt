package uk.co.traynor.privategallery.ui

import androidx.compose.ui.test.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import uk.co.traynor.privategallery.core.privateapps.*
import uk.co.traynor.privategallery.core.ui.AppTheme

class PrivateAppsUi178Test {
 @get:Rule val compose=createComposeRule()
 @Test fun libraryCrudLaunchAndRevocation() {
  val context=ApplicationProvider.getApplicationContext<android.content.Context>()
  val root=File(context.cacheDir,"privateapps-ui-test").apply{deleteRecursively();mkdirs()}
  val key=ByteArray(32){3};var live=true;var closed=0;var launched="";var mounted by mutableStateOf(true)
  val store=PrimaryPrivateAppStore(root,key,{live},{check(live);it()})
  val controller=PrivateAppsController(store.owner(),{live},{action,bytes,id->PrivateAppCommands.execute(store,action,bytes,id)},{launched=it})
  compose.setContent {PrivateGalleryTheme(AppTheme.DARK){if(mounted)PrivateAppsLibrary(controller,{closed++;mounted=false})}}
  compose.waitUntil(5000){compose.onAllNodesWithText("No private apps yet").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("Add App").performClick()
  compose.onNodeWithTag("private-app-name").performTextInput("Fixture")
  compose.onNodeWithTag("private-app-url").performTextInput("https://example.test/")
  compose.waitUntil(5000){compose.onAllNodesWithText("Add as Private Website").fetchSemanticsNodes().any{it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not()}}
  compose.onNodeWithText("Add as Private Website").performClick()
  compose.waitUntil(5000){compose.onAllNodesWithText("Fixture").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithContentDescription("App actions for Fixture").performClick()
  compose.onNodeWithText("Edit").performClick()
  compose.onNodeWithTag("private-app-name").performTextReplacement("Renamed")
  compose.onNodeWithText("Save").performClick()
  compose.waitUntil(5000){compose.onAllNodesWithText("Renamed").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("Renamed").performClick()
  compose.runOnIdle{assertEquals("https://example.test/",launched);assertEquals(1,closed);assertFalse(mounted)}
  compose.onNodeWithTag("private-app-search").assertDoesNotExist()
  compose.runOnIdle{mounted=true}
  compose.waitUntil(5000){compose.onAllNodesWithContentDescription("App actions for Renamed").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithContentDescription("App actions for Renamed").performClick()
  compose.onNodeWithText("Remove").performClick()
  compose.onNodeWithText("Remove app").performClick()
  compose.waitUntil(5000){compose.onAllNodesWithText("No private apps yet").fetchSemanticsNodes().isNotEmpty()}
  compose.runOnIdle{live=false}
  compose.waitUntil(5000){closed==2}
  compose.onNodeWithText("No private apps yet").assertDoesNotExist()
  key.fill(0);root.deleteRecursively()
 }
 @Test fun discovers_synthetic_pwa_previews_icon_and_persists_metadata() {
  val context=ApplicationProvider.getApplicationContext<android.content.Context>()
  val root=File(context.cacheDir,"privateapps-pwa-ui").apply{deleteRecursively();mkdirs()}
  val key=ByteArray(32){7};val store=PrimaryPrivateAppStore(root,key,{true},{it()})
  val bitmap=android.graphics.Bitmap.createBitmap(96,96,android.graphics.Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.BLUE)}
  val png=java.io.ByteArrayOutputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it);it.toByteArray()};bitmap.recycle()
  val controller=PrivateAppsController(store.owner(),{true},{a,b,id->PrivateAppCommands.execute(store,a,b,id)},{},{page ->
   PrivateAppDiscovery.discover(page,false,{true},{url ->
    when {
     url.endsWith("manifest.json")->PrivateAppDiscovery.Resource("""{"short_name":"Synthetic PWA","start_url":"/app/home","scope":"/app/","display":"standalone","icons":[{"src":"/icon.png","type":"image/png"}]}""".toByteArray(),"application/manifest+json")
     url.endsWith("icon.png")->PrivateAppDiscovery.Resource(png.copyOf(),"image/png")
     else->PrivateAppDiscovery.Resource("<link rel='manifest' href='/manifest.json'>".toByteArray(),"text/html")
    }
   },PrivateAppIcons::normalize)
  })
  try {
   compose.setContent{PrivateGalleryTheme(AppTheme.LIGHT){PrivateAppsLibrary(controller,{},initialUrl="https://example.test/app/",initialName="Current fixture")}}
   compose.waitUntil(5000){compose.onAllNodesWithTag("private-app-url").fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithTag("private-app-url").assert(SemanticsMatcher.expectValue(
    androidx.compose.ui.semantics.SemanticsProperties.EditableText,androidx.compose.ui.text.AnnotatedString("https://example.test/app/")))
   compose.waitUntil(5000){compose.onAllNodesWithText("Install as Private Web App").fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithText("Install as Private Web App").performClick()
   compose.waitUntil(5000){compose.onAllNodesWithText("Synthetic PWA").fetchSemanticsNodes().isNotEmpty()}
   val app=store.read().apps.single();assertEquals("https://example.test/app/home",app.startUrl());assertEquals("standalone",app.display());assertTrue(app.icon().isNotEmpty())
   assertEquals(app,PrimaryPrivateAppStore(root,key,{true},{it()}).read().apps.single())
   val decoded=checkNotNull(PrivateAppIcons.decode(app.icon()));assertTrue(decoded.width<=64 && decoded.height<=64);decoded.recycle()
  }finally{png.fill(0);key.fill(0);root.deleteRecursively()}
 }
 @Test fun icon_decoder_rejects_large_and_malformed_images() {
  assertNull(PrivateAppIcons.decode(java.util.Base64.getEncoder().encodeToString("not an image".toByteArray())))
  val bitmap=android.graphics.Bitmap.createBitmap(1025,1,android.graphics.Bitmap.Config.ARGB_8888)
  val bytes=java.io.ByteArrayOutputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it);it.toByteArray()};bitmap.recycle()
  try{assertThrows(IllegalArgumentException::class.java){PrivateAppIcons.normalize(bytes)}}finally{bytes.fill(0)}
 }

}
