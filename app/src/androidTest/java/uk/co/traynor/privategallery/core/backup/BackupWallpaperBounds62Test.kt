package uk.co.traynor.privategallery.core.backup
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

class BackupWallpaperBounds62Test {
 /** Valid, highly compressible 16384-square one-bit grayscale PNG. Never fully decode it. */
 private fun oversizedPng():ByteArray {
  val out=ByteArrayOutputStream();out.write(byteArrayOf(-119,80,78,71,13,10,26,10))
  fun chunk(type:String,bytes:ByteArray){val data=DataOutputStream(out);data.writeInt(bytes.size);val name=type.encodeToByteArray();data.write(name);data.write(bytes);val crc=CRC32();crc.update(name);crc.update(bytes);data.writeInt(crc.value.toInt())}
  val header=ByteArrayOutputStream();DataOutputStream(header).use{it.writeInt(16384);it.writeInt(16384);it.write(byteArrayOf(1,0,0,0,0))};chunk("IHDR",header.toByteArray())
  val compressed=ByteArrayOutputStream();DeflaterOutputStream(compressed).use{stream->val row=ByteArray(2049);repeat(16384){stream.write(row)}};chunk("IDAT",compressed.toByteArray());chunk("IEND",ByteArray(0));return out.toByteArray()
 }
 @Test fun oversizedAuthenticatedWallpaperAndInvalidImageAreRejectedBeforeSettingsOverwrite(){
  val huge=oversizedPng();assertTrue(huge.size<8*1024*1024)
  val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true};android.graphics.BitmapFactory.decodeByteArray(huge,0,huge.size,bounds);assertEquals(16384,bounds.outWidth);assertEquals(16384,bounds.outHeight)
  for(hidden in listOf(false,true)){val root=BackupFixture62.temporary("wallpaper-bound62")
   try{BackupFixture62(root,hidden).use{f->f.coordinator.space.getSharedPreferences("nova_widgets_v1",0).edit().putBoolean("clock:${f.coordinator.owner}",false).commit()
    val target=File(f.access.root,"home2");val before=target.listFiles()!!.associate{it.name to it.readBytes()}
    for(bytes in listOf(huge,"not an image".encodeToByteArray())){
     val values=mapOf("wallpaper:${UUID.randomUUID()}" to bytes)
     assertThrows(Exception::class.java){f.coordinator.space.restoreBackupValues(values)}
     assertEquals(before.keys,target.listFiles()!!.map{it.name}.toSet());before.forEach{(name,expected)->assertArrayEquals(expected,File(target,name).readBytes())}
    }
    val bitmap=android.graphics.Bitmap.createBitmap(32,24,android.graphics.Bitmap.Config.ARGB_8888);val output=ByteArrayOutputStream();assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output));bitmap.recycle()
    val valid=output.toByteArray();val id=UUID.randomUUID().toString();f.coordinator.space.restoreBackupValues(mapOf("wallpaper:$id" to valid));assertArrayEquals(valid,f.coordinator.space.readWallpaper(id))
    val decoded=checkNotNull(uk.co.traynor.privategallery.core.home2.StoredWallpaperImage.decodeStored(valid));assertEquals(32,decoded.width);assertEquals(24,decoded.height);decoded.eraseColor(0);decoded.recycle()
    assertNull(uk.co.traynor.privategallery.core.home2.StoredWallpaperImage.decodeStored(huge))
   }}finally{root.deleteRecursively()}
  }
 }
}
