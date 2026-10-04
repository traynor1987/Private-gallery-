package uk.co.traynor.privategallery.core.editor

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Public fixtures against the app's actual private encoder stream, not a model implementation.
 * writeTo exposes its actual backing-array argument through a public Java/Android API.
 * This is Java obsolete-buffer/scalar-bound evidence, not renderer/Native ownership acceptance. */
@RunWith(AndroidJUnit4::class)
class Phase3PhotoEncoderBufferAndroidTest {
    @Test fun bulkGrowthWipesObsoleteStorageWithoutChangingEncodedBytesOrSource() {
        val out=writer();val source=ByteArray(4096){(it%251).toByte()}
        try{out.write(byteArrayOf(7,8,9));val old=backing(out)
            out.write(source,17,3000);assertNotSame(old,backing(out))
            assertTrue("Obsolete encoded buffer retained payload",old.all{it==0.toByte()})
            assertArrayEquals(byteArrayOf(7,8,9)+source.copyOfRange(17,3017),out.toByteArray())
            assertTrue(source.indices.all{source[it]==(it%251).toByte()})
            out.close();assertTrue(old.all{it==0.toByte()});assertTrue(backing(out).all{it==0.toByte()});assertEquals(0,out.size())
        }finally{out.close()}
    }
    @Test fun scalarGrowthClearsOldStorageAndPreservesLowEightBits() {
        val out=writer()
        try{out.write(7);val old=backing(out);while(out.size()<old.size)out.write(0x108)
            val expected=out.toByteArray()+byteArrayOf((-1).toByte())
            out.write(-1);assertNotSame(old,backing(out));assertArrayEquals(expected,out.toByteArray())
            assertTrue("Scalar growth retained old secret buffer",old.all{it==0.toByte()})
        }finally{out.close()}
    }
    @Test fun backingArraySelfAppendSurvivesGrowthBeforeOldStorageIsWiped() {
        val out=writer()
        try{out.write(byteArrayOf(7,8,9));val old=backing(out)
            while(out.size()<old.size)out.write(11)
            val prefix=out.toByteArray();out.write(old,0,old.size)
            assertArrayEquals(prefix+prefix,out.toByteArray());assertNotSame(old,backing(out))
            assertTrue("Self-append old storage retained bytes",old.all{it==0.toByte()})
        }finally{out.close()}
    }
    @Test fun inPlaceOverlapAndZeroLengthPreserveByteArrayOutputSemantics() {
        val out=writer()
        try{out.write(byteArrayOf(7,8,9,10));val original=backing(out)
            out.write(original,3,4);out.write(original,original.size,0)
            assertSame(original,backing(out));assertArrayEquals(byteArrayOf(7,8,9,10,10,0,0,0),out.toByteArray())
            out.close();out.close();assertTrue(original.all{it==0.toByte()});assertEquals(0,out.size())
        }finally{out.close()}
    }
    @Test fun invalidSlicesDenyBeforeMutationAndDoNotWipeCallerOrCurrentPayload() {
        val out=writer();val source=byteArrayOf(17,18)
        try{out.write(byteArrayOf(7,8));val original=backing(out)
            for((off,len)in listOf(-1 to 1,0 to -1,3 to 0,0 to Int.MAX_VALUE,Int.MAX_VALUE to 1)) {
                assertThrows(IndexOutOfBoundsException::class.java){out.write(source,off,len)}
                assertSame(original,backing(out));assertArrayEquals(byteArrayOf(7,8),out.toByteArray());assertArrayEquals(byteArrayOf(17,18),source)
            }
        }finally{out.close()}
    }
    @Test fun actualStorageCapacityCannotExceedExistingFortyEightMiBLimit() {
        val out=writer();val chunk=ByteArray(64*1024){7}
        try{repeat(PhotoRenderer.MAX_SOURCE_BYTES/chunk.size){out.write(chunk,0,chunk.size)}
            assertEquals(PhotoRenderer.MAX_SOURCE_BYTES,out.size())
            val actual=backing(out);assertTrue("Actual encoder capacity exceeds declared limit",actual.size<=PhotoRenderer.MAX_SOURCE_BYTES)
            assertTrue(chunk.all{it==7.toByte()})
        }finally{out.close()}
    }
    @Test fun scalarWriteCannotBypassExistingFortyEightMiBPayloadLimit() {
        val out=writer();val chunk=ByteArray(64*1024){7}
        try{repeat(PhotoRenderer.MAX_SOURCE_BYTES/chunk.size){out.write(chunk,0,chunk.size)}
            assertEquals(PhotoRenderer.MAX_SOURCE_BYTES,out.size())
            assertThrows(IllegalStateException::class.java){out.write(7)}
            assertThrows(IllegalStateException::class.java){out.write(chunk,0,1)}
            assertTrue(chunk.all{it==7.toByte()})
        }finally{out.close()}
    }
    @Test fun admittedSizeFailureWipesCurrentBytesAndCannotReturnAPartialEncodedImage() {
        val out=writer();val source=ByteArray(PhotoRenderer.MAX_SOURCE_BYTES){17}
        try{out.write(byteArrayOf(7,8));val actual=backing(out)
            assertThrows(IllegalStateException::class.java){out.write(source,0,source.size)}
            assertTrue("Failed stream retains partial private output",actual.all{it==0.toByte()})
            assertEquals(0,out.size())
            assertThrows(IllegalStateException::class.java){out.toByteArray()}
            assertThrows(IllegalStateException::class.java){out.write(1)}
            assertThrows(IllegalStateException::class.java){out.write(byteArrayOf(9),0,1)}
            out.reset()
            assertThrows(IllegalStateException::class.java){out.toByteArray()}
            assertThrows(IllegalStateException::class.java){out.write(1)}
            assertTrue(source.all{it==17.toByte()})
        }finally{out.close()}
    }
    @Test fun eachSuccessiveGrowthWipesItsOwnObsoleteArrayAndPreservesTheFullPrefix() {
        val out=writer();var expected=byteArrayOf(7,8);val obsolete=mutableListOf<ByteArray>()
        try{out.write(expected)
            repeat(3){val old=backing(out);obsolete.add(old);val suffix=ByteArray(old.size+37){(19+it%29).toByte()}
                out.write(suffix,0,suffix.size);expected+=suffix
                assertNotSame(old,backing(out));assertTrue("A later growth retained its obsolete payload",old.all{it==0.toByte()})
                assertArrayEquals(expected,out.toByteArray());assertTrue(obsolete.all{b->b.all{it==0.toByte()}})
            }
        }finally{out.close()}
        assertTrue(obsolete.all{b->b.all{it==0.toByte()}})
    }
    @Test fun successfulCloseCanBeReusedWithoutRevivingOldPayload() {
        val out=writer()
        try{out.write(byteArrayOf(7,8));val original=backing(out);out.close();out.close()
            assertTrue(original.all{it==0.toByte()});assertEquals(0,out.size());assertArrayEquals(ByteArray(0),out.toByteArray())
            out.write(byteArrayOf(17,18,19));assertArrayEquals(byteArrayOf(17,18,19),out.toByteArray())
            out.close();assertTrue(backing(out).all{it==0.toByte()});assertEquals(0,out.size())
        }finally{out.close()}
    }
    @Test fun actualNativePngCompressionMatchesPublicReferenceAndDecodesAllPixels() {
        val pixels=IntArray(64*40){index->android.graphics.Color.rgb(index%251,(index*7)%251,(index*13)%251)}
        val reference=ByteArrayOutputStream()
        var encoded:ByteArray?=null
        var decoded:android.graphics.Bitmap?=null
        val bitmap=android.graphics.Bitmap.createBitmap(64,40,android.graphics.Bitmap.Config.ARGB_8888)
        try{bitmap.setPixels(pixels,0,64,0,0,64,40)
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,reference))
            val bytes=PhotoRenderer.encode(bitmap);encoded=bytes
            assertArrayEquals(reference.toByteArray(),bytes)
            val image=checkNotNull(android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size));decoded=image
            assertEquals(64,image.width);assertEquals(40,image.height)
            val actual=IntArray(pixels.size);image.getPixels(actual,0,64,0,0,64,40);assertArrayEquals(pixels,actual)
        }finally{
            try{decoded?.recycle()}finally{
                try{bitmap.recycle()}finally{
                    try{encoded?.fill(0)}finally{reference.reset()}
                }
            }
        }
    }
    private fun writer():ByteArrayOutputStream = Class.forName("uk.co.traynor.privategallery.core.editor.PhotoRenderer\$WipingOutput")
        .getDeclaredConstructor().apply{isAccessible=true}.newInstance() as ByteArrayOutputStream
    private fun backing(out:ByteArrayOutputStream):ByteArray {
        var actual:ByteArray?=null
        out.writeTo(object:OutputStream(){override fun write(value:Int){error("Expected public bulk writeTo")}
            override fun write(bytes:ByteArray,off:Int,len:Int){assertEquals(0,off);assertEquals(out.size(),len);actual=bytes}})
        return checkNotNull(actual)
    }
}
