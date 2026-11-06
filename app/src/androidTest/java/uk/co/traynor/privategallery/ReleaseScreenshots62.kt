package uk.co.traynor.privategallery
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Synthetic fixture content only; no credential/recovery-input screenshots. */
internal object ReleaseScreenshots62 {
 fun capture(node:SemanticsNodeInteraction,name:String){
  require(name.matches(Regex("[a-z0-9-]+")))
  val instrumentation=InstrumentationRegistry.getInstrumentation();val path=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
  val root=File(path?:instrumentation.targetContext.getExternalFilesDir(null)!!.path,"nova62-visuals").apply{mkdirs()}
  val image=node.captureToImage().asAndroidBitmap();File(root,"$name.png").outputStream().use{check(image.compress(Bitmap.CompressFormat.PNG,100,it))}
 }
}
