package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.privateapps.*
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.io.File
import androidx.test.core.app.ApplicationProvider
import uk.co.traynor.privategallery.core.hidden.HiddenSession

class PrivateAppCustomIconsTest {
 @get:Rule val compose=createComposeRule()
 private fun png(width:Int=80,height:Int=60,color:Int=Color.BLUE):ByteArray {
  val image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).apply{eraseColor(color)}
  return try {ByteArrayOutputStream().use{check(image.compress(Bitmap.CompressFormat.PNG,100,it));it.toByteArray()}}finally{image.recycle()}
 }
 @Test fun selected_photos_are_normalized_and_oversized_dimensions_rejected() {
  val bytes=png(2000,1000)
  val icon=PrivateAppIcons.normalizeSelected(bytes)
  val bitmap=checkNotNull(PrivateAppIcons.decode(icon))
  assertEquals(64,bitmap.width);assertEquals(32,bitmap.height);bitmap.recycle();bytes.fill(0)
  val bomb=png(8193,1)
  try {assertThrows(IllegalArgumentException::class.java){PrivateAppIcons.normalizeSelected(bomb)}}finally{bomb.fill(0)}
  assertThrows(IllegalArgumentException::class.java){PrivateAppIcons.normalizeSelected("not an image".toByteArray())}
  val tooManyPixels=png().also {data ->
   // Change only the PNG header, so a decompression bomb test never allocates its declared image.
   java.nio.ByteBuffer.wrap(data,16,8).putInt(8192).putInt(4097)
   val crc=java.util.zip.CRC32().apply{update(data,12,17)}
   java.nio.ByteBuffer.wrap(data,29,4).putInt(crc.value.toInt())
  }
  try {assertThrows(IllegalArgumentException::class.java){PrivateAppIcons.normalizeSelected(tooManyPixels)}}finally{tooManyPixels.fill(0)}
 }
 @Test fun sampled_icons_preserve_original_aspect_in_portrait_and_odd_sized_photos() {
  for((width,height,expectedWidth,expectedHeight) in listOf(listOf(1000,2000,32,64),listOf(2001,1000,64,32),listOf(1000,2001,32,64))) {
   val bytes=png(width,height)
   try {
    val icon=checkNotNull(PrivateAppIcons.decode(PrivateAppIcons.normalizeSelected(bytes)))
    try {assertEquals(expectedWidth,icon.width);assertEquals(expectedHeight,icon.height)}finally{icon.recycle()}
   }finally{bytes.fill(0)}
  }
 }
 @Test fun hidden_icon_chooser_selects_only_scoped_preview_without_export() {
  var selected:ByteArray?=null;var requests=0
  val source=png()
  compose.setContent {HiddenPrivateAppIconPicker(7,{true},{query,offset ->
   assertEquals("",query);assertEquals(0,offset)
   """{"generation":7,"query":"fixture-query","items":[{"id":"image-id","name":"Vault photo","mime":"image/png"}],"next":1,"total":1}"""
  },{query,id ->assertEquals("fixture-query",query);assertEquals("image-id",id);requests++;source.copyOf()},{selected=it})}
  compose.waitUntil(5000){compose.onAllNodesWithContentDescription("Use Vault photo as icon").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithContentDescription("Use Vault photo as icon").performClick()
  compose.waitUntil(5000){selected!=null}
  compose.runOnIdle{assertArrayEquals(source,selected);assertTrue(requests>=1);selected?.fill(0);source.fill(0)}
 }
 @Test fun revoked_editor_discards_late_icon_result() {
  var live=true;var selection:((ByteArray?)->Unit)?=null;var writes=0
  val controller=PrivateAppsController(PrivateAppStore.Owner.PRIMARY,{live},{action,_,_ ->
   if(action==PrivateAppCommands.UPSERT)writes++
   PrivateAppCatalog(false,emptyList())
  },{},requestIcon={selection=it})
  compose.setContent{PrivateAppsLibrary(controller,{})}
  compose.waitUntil(5000){compose.onAllNodesWithText("No private apps yet").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("Add App").performClick()
  compose.onNodeWithText("Choose icon").performClick()
  val bytes=png()
  compose.runOnIdle{live=false;checkNotNull(selection).invoke(bytes);assertTrue(bytes.all{it==0.toByte()});assertEquals(0,writes)}
 }
 @Test fun selected_icon_survives_encrypted_restart_in_each_owner_without_source_uri() {
  val context=ApplicationProvider.getApplicationContext<android.content.Context>()
  val root=File(context.cacheDir,"custom-icon-storage-${UUID.randomUUID()}").apply{mkdirs()}
  val key=ByteArray(32){17};val hidden=HiddenSession{100L}
  assertTrue(hidden.complete(hidden.beginAuthentication(),key.copyOf()))
  val source=png();val icon=try{PrivateAppIcons.normalizeSelected(source)}finally{source.fill(0)}
  try {
   for(owner in PrivateAppStore.Owner.values()) {
    val store:PrivateAppStore=when(owner) {
     PrivateAppStore.Owner.PRIMARY->PrimaryPrivateAppStore(File(root,"primary"),key,{true},{it()})
     else->HiddenPrivateAppStore(root,hidden,hidden.authority(),owner==PrivateAppStore.Owner.HIDDEN_TOR)
    }
    val app=PrivateAppCatalog.App(UUID.randomUUID().toString(),"Icon fixture","https://example.test/","","browser",icon,false,true,false)
    store.update{it.upsert(app)}
   }
   hidden.lock();assertTrue(hidden.complete(hidden.beginAuthentication(),key.copyOf()))
   for(owner in PrivateAppStore.Owner.values()) {
    val store:PrivateAppStore=when(owner) {
     PrivateAppStore.Owner.PRIMARY->PrimaryPrivateAppStore(File(root,"primary"),key,{true},{it()})
     else->HiddenPrivateAppStore(root,hidden,hidden.authority(),owner==PrivateAppStore.Owner.HIDDEN_TOR)
    }
    assertEquals(icon,store.read().apps.single().icon())
   }
   root.walkTopDown().filter{it.isFile}.forEach {file ->
    val data=file.readBytes()
    try {val text=data.toString(Charsets.ISO_8859_1);assertFalse(text.contains(icon));assertFalse(text.contains("content://"));assertFalse(text.contains("Icon fixture"))}finally{data.fill(0)}
   }
  }finally{hidden.lock();key.fill(0);root.deleteRecursively()}
 }
 @Test fun replace_clear_cancel_and_late_selection_preserve_saved_catalogue() {
  var catalog=PrivateAppCatalog(false,listOf(PrivateAppCatalog.App(UUID.randomUUID().toString(),"Fixture","https://example.test/","","browser","",false,true,false)))
  var selection:((ByteArray?)->Unit)?=null
  val controller=PrivateAppsController(PrivateAppStore.Owner.PRIMARY,{true},{action,bytes,_ ->
   if(action==PrivateAppCommands.UPSERT)catalog=catalog.upsert(PrivateAppCatalog.decode(checkNotNull(bytes),false).apps.single())
   catalog
  },{},requestIcon={selection=it})
  compose.setContent {PrivateAppsLibrary(controller,{})}
  fun openFixtureEditor() {
   compose.waitUntil(5000) {
    compose.onAllNodesWithText("Edit Private App").fetchSemanticsNodes().isEmpty() &&
     compose.onAllNodes(hasContentDescription("App actions for Fixture") and isEnabled()).fetchSemanticsNodes().size==1
   }
   compose.onNodeWithContentDescription("App actions for Fixture").performClick()
   compose.onNodeWithText("Edit").performClick()
   compose.waitUntil(5000){compose.onAllNodesWithText("Edit Private App").fetchSemanticsNodes().size==1}
  }
  compose.waitUntil(5000){compose.onAllNodesWithContentDescription("App actions for Fixture").fetchSemanticsNodes().isNotEmpty()}
  openFixtureEditor()
  compose.onNodeWithText("Choose icon").performClick()
  compose.runOnIdle{checkNotNull(selection).invoke(png())}
  compose.waitUntil(5000){compose.onAllNodesWithText("Replace icon").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("Save").performClick()
  compose.waitUntil(5000){catalog.apps.single().icon().isNotEmpty()}
  val first=catalog.apps.single().icon()
  openFixtureEditor()
  compose.waitUntil(5000){compose.onAllNodesWithText("Replace icon").fetchSemanticsNodes().size==1}
  compose.onNodeWithText("Replace icon").performClick()
  compose.runOnIdle{checkNotNull(selection).invoke(png(color=Color.RED))}
  compose.waitUntil(5000){compose.onAllNodesWithText("Icon ready. Save to apply.").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("Cancel").performClick()
  compose.runOnIdle{assertEquals(first,catalog.apps.single().icon())}
  openFixtureEditor()
  compose.waitUntil(5000){compose.onAllNodesWithText("Replace icon").fetchSemanticsNodes().size==1}
  compose.onNodeWithText("Replace icon").performClick()
  val late=checkNotNull(selection)
  compose.onNodeWithText("Clear icon").performClick()
  val stale=png()
  compose.runOnIdle{late(stale)}
  compose.onNodeWithText("Save").performClick()
  compose.waitUntil(5000){catalog.apps.single().icon().isEmpty()}
  compose.runOnIdle{assertTrue(stale.all{it==0.toByte()})}
 }
}
