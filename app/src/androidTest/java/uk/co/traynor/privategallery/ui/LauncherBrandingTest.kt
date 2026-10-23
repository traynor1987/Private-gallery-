package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.R
import java.io.File

/** Resource-only test: never opens, authenticates or modifies either vault. */
class LauncherBrandingTest {
    @Test fun packagedAdaptiveRoundAndThemedIconsRender() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals(R.mipmap.ic_launcher, info.icon)
        val destination = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: File(context.externalMediaDirs.first(), "additional_test_output").absolutePath
        val output = File(destination, "launcher-branding").apply { mkdirs() }
        for ((name, id) in listOf("launcher" to R.mipmap.ic_launcher, "round" to R.mipmap.ic_launcher_round)) {
            val icon = context.getDrawable(id) as AdaptiveIconDrawable
            val bitmap = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
            icon.setBounds(0, 0, 432, 432)
            icon.draw(Canvas(bitmap))
            File(output, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            // The electric-blue lock must survive resource loading and platform masking.
            var blue = 0
            for (y in 120 until 370) for (x in 80 until 350) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.blue(pixel) > 180 && Color.blue(pixel) > Color.red(pixel) * 1.5) blue++
            }
            assertTrue("Blue vault/lock missing from $name ($blue pixels)", blue > 100)
            val mono = requireNotNull(icon.monochrome)
            val themed = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
            mono.setBounds(0, 0, 432, 432)
            mono.draw(Canvas(themed))
            var visible = 0
            for (y in 0 until 432) for (x in 0 until 432) {
                val pixel = themed.getPixel(x, y)
                if (Color.alpha(pixel) > 0) {
                    visible++
                    assertEquals(Color.red(pixel), Color.green(pixel))
                    assertEquals(Color.green(pixel), Color.blue(pixel))
                }
            }
            assertTrue("Empty monochrome vault", visible > 1000)
            File(output, "$name-themed.png").outputStream().use { assertTrue(themed.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
        val launch = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        assertNotNull(context.packageManager.resolveActivity(launch, 0))
    }
}
