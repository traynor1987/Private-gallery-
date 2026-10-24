package uk.co.traynor.privategallery.core.camera

/** Non-content camera preferences, encrypted independently within the originating vault's camera domain. */
internal data class CameraOptions(val photoQuality:Int=95,val videoQuality:Int=1,val grid:Boolean=false,val timer:Int=0,val vibration:Boolean=false,val defaultVideo:Boolean=false) {
 fun encode():ByteArray {
  require(photoQuality in listOf(85,95,100)&&videoQuality in 0..2&&timer in listOf(0,3,10))
  return byteArrayOf(1,photoQuality.toByte(),videoQuality.toByte(),if(grid)1 else 0,timer.toByte(),if(vibration)1 else 0,if(defaultVideo)1 else 0)
 }
 companion object {
  fun decode(bytes:ByteArray):CameraOptions {
   require(bytes.size==7&&bytes[0]==1.toByte()&&listOf(3,5,6).all {bytes[it].toInt() in 0..1})
   return CameraOptions(bytes[1].toInt(),bytes[2].toInt(),bytes[3]==1.toByte(),bytes[4].toInt(),bytes[5]==1.toByte(),bytes[6]==1.toByte()).also {it.encode().fill(0)}
  }
 }
}
