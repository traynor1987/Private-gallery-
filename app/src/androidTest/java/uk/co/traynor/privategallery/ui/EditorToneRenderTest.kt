package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.editor.*

class EditorToneRenderTest {
    private fun source(colour: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(colour) }
        return try { PhotoRenderer.encode(bitmap) } finally { bitmap.recycle() }
    }
    private fun pixel(bytes: ByteArray, edit: PhotoEdit): Int {
        val bitmap = PhotoRenderer.render(bytes, edit, false)
        return try { bitmap.getPixel(0, 0) } finally { bitmap.recycle() }
    }
    @Test fun neutralMatchesOriginalAndExposureChangesSavedPixelsWithChannelClamping() {
        val bytes = source(Color.rgb(64, 100, 180))
        try {
            assertEquals(Color.rgb(64,100,180), pixel(bytes, PhotoEdit()))
            assertEquals(pixel(bytes,PhotoEdit()), pixel(bytes,PhotoEdit(exposure=0f,temperature=0f)))
            val brighter = pixel(bytes, PhotoEdit(exposure=1f))
            assertEquals(128,Color.red(brighter)); assertEquals(200,Color.green(brighter)); assertEquals(255,Color.blue(brighter))
            val darker = pixel(bytes, PhotoEdit(exposure=-1f))
            assertEquals(32,Color.red(darker));assertEquals(50,Color.green(darker));assertEquals(90,Color.blue(darker))
            val saved=PhotoRenderer.output(bytes,PhotoEdit(exposure=1f))
            try { assertEquals(brighter,pixel(saved,PhotoEdit())) } finally {saved.fill(0)}
        } finally { bytes.fill(0) }
    }
    @Test fun temperatureMovesRedAndBlueInOppositeDirectionsAndKeepsGreenAndAlpha() {
        val bytes=source(Color.rgb(128,128,128))
        try {
            val warm=pixel(bytes,PhotoEdit(temperature=1f));val cool=pixel(bytes,PhotoEdit(temperature=-1f))
            assertTrue(Color.red(warm)>128 && Color.blue(warm)<128)
            assertTrue(Color.red(cool)<128 && Color.blue(cool)>128)
            assertEquals(128,Color.green(warm));assertEquals(128,Color.green(cool));assertEquals(255,Color.alpha(warm))
        } finally {bytes.fill(0)}
        val translucent=source(Color.argb(96,128,128,128))
        try {assertEquals(96,Color.alpha(pixel(translucent,PhotoEdit(exposure=1f,temperature=.5f))))}
        finally {translucent.fill(0)}
    }
    @Test fun rotatedPresetRendersRequestedFinalAspectRatio() {
        val bitmap=Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.BLUE)}
        val bytes=try {PhotoRenderer.encode(bitmap)}finally{bitmap.recycle()}
        try {
            for(turns in 0..3)for(ratio in listOf(1f,4f/3f,16f/9f)) {
                val bounds=EditorCropPresets.orientedCentered(400,300,1,turns,ratio)
                val crop=uk.co.traynor.privategallery.core.vault.NormalizedCrop(bounds[0],bounds[1],bounds[2],bounds[3])
                val rendered=PhotoRenderer.render(bytes,PhotoEdit(crop=crop,quarterTurns=turns),false)
                try {assertEquals("Final output ratio at rotation $turns",ratio,rendered.width.toFloat()/rendered.height,.015f)}
                finally {rendered.recycle()}
            }
        } finally {bytes.fill(0)}
    }

}
