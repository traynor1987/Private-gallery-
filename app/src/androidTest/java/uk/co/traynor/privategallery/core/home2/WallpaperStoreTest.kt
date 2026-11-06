package uk.co.traynor.privategallery.core.home2

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.hidden.HiddenRepository
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.vault.*
import uk.co.traynor.privategallery.home2.PreviewData
import uk.co.traynor.privategallery.home2.Wallpaper

class WallpaperStoreTest {
 private class Fixture:AutoCloseable {
  val base=ApplicationProvider.getApplicationContext<Context>()
  val root=File(base.cacheDir,"wallpaper-${UUID.randomUUID()}")
  val context=object:ContextWrapper(base) {
   override fun getFilesDir()=File(root,"files").apply{mkdirs()}
   override fun getNoBackupFilesDir()=File(root,"hidden").apply{mkdirs()}
   override fun getApplicationContext():Context=this
  }
  val key=ByteArray(32){42};val identity=Any();val gate=Any()
  val hidden=HiddenSession{100L}.also{check(it.complete(it.beginAuthentication(),ByteArray(32){71}))}
  fun access(secret:Boolean)=if(secret)EvidenceAccess.hidden(context,hidden){true}else EvidenceAccess.primary(context,identity,{identity},{true},{key.copyOf()},gate)
  override fun close(){hidden.lock();key.fill(0);root.deleteRecursively()}
 }
 @Test fun deliberate_apply_is_owner_encrypted_and_stale_home_save_preserves_it()=Fixture().use{f->
  f.access(false).use{access->SpaceContext(f.context,access).use{space->
   val stale=PreviewData(space,null)
   val bytes="PRIVATE WALLPAPER PIXELS".encodeToByteArray()
   val saved=WallpaperStore(space).apply(Wallpaper(zoom=1.7f,x=.3f,dim=.25f),bytes)
   assertNotNull(saved.image);assertArrayEquals(bytes,space.readWallpaper(saved.image!!))
   stale.change{it.copy(columns=5)}
   assertEquals(saved,PreviewData(space,null).profile.wallpaper)
   assertTrue(f.root.walkTopDown().filter{it.isFile}.none{it.readBytes().toString(Charsets.ISO_8859_1).contains("PRIVATE WALLPAPER PIXELS")})
   f.access(true).use{hiddenAccess->SpaceContext(f.context,hiddenAccess).use{secret->assertNull(WallpaperStore(secret).read().image);assertNull(secret.readWallpaper(saved.image!!))}}
   bytes.fill(0)
  }}
 }
 @Test fun both_owners_read_originals_without_changing_gallery_and_cancel_does_not_commit()=Fixture().use{f->
  val image=Bitmap.createBitmap(32,24,Bitmap.Config.ARGB_8888)
  val bytes=ByteArrayOutputStream().use{out->image.compress(Bitmap.CompressFormat.PNG,100,out);out.toByteArray()};image.recycle()
  val primary=AndroidVaultRepository(f.context,f.key)
  val first=(primary.importVerified(VaultImportSource("primary.png","image/png",{ByteArrayInputStream(bytes)}))as ImportResult.Imported).item
  val secret=HiddenRepository(f.context,f.hidden)
  val second=secret.importMedia("hidden.png","image/png",ByteArrayInputStream(bytes))
  for(hidden in listOf(false,true))f.access(hidden).use{access->
   val coordinator=WallpaperCoordinator(f.context,access)
   try {
    val id=if(hidden)second.id else first.id
    assertEquals(listOf(id),coordinator.images().map{it.id})
    val draft=coordinator.image(id)
    try{assertEquals(32,draft.bitmap.width);assertEquals(24,draft.bitmap.height)}finally{draft.close()}
    assertNull(coordinator.wallpaper().image)
   }finally{coordinator.close()}
  }
  assertArrayEquals(first.plaintextSha256,primary.items().single().plaintextSha256)
  assertArrayEquals(second.plaintextSha256,secret.items().single().plaintextSha256)
  bytes.fill(0)
 }
 @Test fun retired_coordinator_cannot_apply_or_read_after_same_hidden_authentication_returns()=Fixture().use{f->
  val access=f.access(true);val coordinator=WallpaperCoordinator(f.context,access)
  f.hidden.revokeOperations()
  try{coordinator.apply(Wallpaper(),null);fail("Retired Apply accepted")}catch(_:IllegalStateException){}
  try{coordinator.images();fail("Retired grid accepted")}catch(_:IllegalStateException){}
  coordinator.close();access.close()
  f.access(true).use{fresh->SpaceContext(f.context,fresh).use{assertNull(WallpaperStore(it).read().image)}}
 }
 @Test fun primary_visible_crop_is_used_and_apply_never_changes_original_ciphertext()=Fixture().use{f->
  val bitmap=Bitmap.createBitmap(80,40,Bitmap.Config.ARGB_8888)
  val bytes=ByteArrayOutputStream().use{out->bitmap.compress(Bitmap.CompressFormat.PNG,100,out);out.toByteArray()};bitmap.recycle()
  val repo=AndroidVaultRepository(f.context,f.key)
  val original=(repo.importVerified(VaultImportSource("cropped.png","image/png",{ByteArrayInputStream(bytes)}))as ImportResult.Imported).item
  val crop=NormalizedCrop(.25f,0f,.75f,1f);repo.applyImageCrop(original.id,crop)
  val payload=File(f.context.filesDir,"vault/payloads/${original.id}.vault");val ciphertext=payload.readBytes()
  f.access(false).use{access->WallpaperCoordinator(f.context,access).use{coordinator->
   coordinator.image(original.id).use{draft->
    assertEquals(40,draft.bitmap.width);assertEquals(40,draft.bitmap.height)
    val applied=coordinator.apply(Wallpaper(zoom=1.5f,x=.4f,y=-.3f,blur=7f,dim=.4f),draft)
    assertNotNull(applied.image);assertEquals(1.5f,applied.zoom,0f)
   }
   assertEquals(crop,repo.imageEdit(original.id)?.crop)
   assertArrayEquals(ciphertext,payload.readBytes());assertArrayEquals(original.plaintextSha256,repo.items().single().plaintextSha256)
  }}
  bytes.fill(0);ciphertext.fill(0)
 }
 @Test fun damaged_encrypted_preferences_refuse_apply_without_replacing_existing_ciphertext()=Fixture().use{f->
  f.access(false).use{access->SpaceContext(f.context,access).use{space->
   val store=WallpaperStore(space);val previous=store.apply(Wallpaper(),"old-wallpaper".encodeToByteArray())
   val root=File(access.root,"home2")
   val preferences=root.listFiles()!!.single{it.name.startsWith("prefs-")}
   preferences.writeBytes(preferences.readBytes().also{it[it.lastIndex]=(it.last().toInt() xor 1).toByte()})
   val corrupted=preferences.readBytes();val files=root.listFiles()!!.map{it.name}.toSet()
   try{store.apply(Wallpaper(builtin="Dusk"),"new-wallpaper".encodeToByteArray());fail("Damaged record overwritten")}catch(_:Exception){}
   assertArrayEquals(corrupted,preferences.readBytes());assertEquals(files,root.listFiles()!!.map{it.name}.toSet())
   assertArrayEquals("old-wallpaper".encodeToByteArray(),space.readWallpaper(previous.image!!))
  }}
 }
 @Test fun retired_owner_ticket_never_inherits_returned_hidden_authentication()=Fixture().use{f->
  val authentication=f.hidden.authentication();var selected=true
  val owner=WallpaperOwner(uk.co.traynor.privategallery.core.evidence.EvidenceCatalog.Owner.HIDDEN,
   {selected&&f.hidden.isAuthenticationCurrent(authentication)},{f.access(true)},true)
  owner.open(f.context)!!.close()
  selected=false;assertFalse(owner.current());owner.close()
  selected=true;assertTrue(f.hidden.isUnlocked);assertNull(owner.open(f.context))
 }
 @Test fun retiring_a_stalled_system_provider_closes_read_and_wipes_accumulated_plaintext()=Fixture().use{f->
  val file=File(f.root,"provider-fixture").apply{parentFile!!.mkdirs();writeBytes(byteArrayOf(1))}
  val entered=java.util.concurrent.CountDownLatch(1);val closed=java.util.concurrent.CountDownLatch(1)
  val borrowed=java.util.concurrent.atomic.AtomicReference<ByteArray>()
  val stream=object:java.io.FileInputStream(file) {
   var first=true
   override fun read(bytes:ByteArray,offset:Int,length:Int):Int {
    if(first){first=false;"sensitive-provider-prefix".encodeToByteArray().copyInto(bytes,offset);return 25}
    borrowed.set(bytes);entered.countDown()
    check(closed.await(5,java.util.concurrent.TimeUnit.SECONDS)){"Retirement did not close the provider read"}
    throw java.io.IOException("Closed by retirement")
   }
   override fun close(){closed.countDown();super.close()}
  }
  val provider=object:android.content.ContentProvider() {
   override fun onCreate()=true
   override fun getType(uri:android.net.Uri)="image/png"
   override fun query(uri:android.net.Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):android.database.Cursor?=null
   override fun insert(uri:android.net.Uri,values:android.content.ContentValues?):android.net.Uri?=error("read only")
   override fun delete(uri:android.net.Uri,selection:String?,selectionArgs:Array<out String>?):Int=error("read only")
   override fun update(uri:android.net.Uri,values:android.content.ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int=error("read only")
   override fun openAssetFile(uri:android.net.Uri,mode:String)=object:android.content.res.AssetFileDescriptor(android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY),0,android.content.res.AssetFileDescriptor.UNKNOWN_LENGTH) {
    override fun createInputStream():java.io.FileInputStream=stream
   }
  }
  provider.attachInfo(f.context,android.content.pm.ProviderInfo().apply{authority="wallpaper.fixture";exported=false})
  val resolver=android.content.ContentResolver.wrap(provider)
  val context=object:ContextWrapper(f.context){override fun getApplicationContext():Context=this;override fun getContentResolver()=resolver}
  val access=f.access(false);val coordinator=WallpaperCoordinator(context,access)
  val worker=java.util.concurrent.Executors.newSingleThreadExecutor()
  try {
   val result=worker.submit<Boolean>{try{coordinator.systemImage(android.net.Uri.parse("content://wallpaper.fixture/stalled")).close();false}catch(_:Exception){true}}
   assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));access.close()
   assertTrue(closed.await(5,java.util.concurrent.TimeUnit.SECONDS));assertTrue(result.get(5,java.util.concurrent.TimeUnit.SECONDS))
   assertNotNull(borrowed.get());assertTrue(borrowed.get().all{it==0.toByte()})
   f.access(false).use{fresh->SpaceContext(f.context,fresh).use{assertNull(WallpaperStore(it).read().image)}}
  }finally{closed.countDown();stream.close();worker.shutdownNow();coordinator.close();access.close()}
 }
}
