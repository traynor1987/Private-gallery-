package uk.co.traynor.novaos.preview

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.ViewModelProvider
import java.io.File

class MainActivity:ComponentActivity() {
 val previewData by lazy { PreviewData(this) }
 val mindData by lazy { MindData(this) }
 val mindAudio by lazy { ViewModelProvider(this)[MindAudio::class.java] }
 val browserPool by lazy { ViewModelProvider(this)[PreviewBrowserModel::class.java] }
 private val pickFiles=registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->browserPool.upload?.onReceiveValue(uris.takeIf{it.isNotEmpty()}?.toTypedArray());browserPool.upload=null}
 private var photoProfile="primary"
 private val pickPhoto=registerForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->
  if(uri!=null)runCatching{
   val source=ImageDecoder.createSource(contentResolver,uri)
   val bitmap=ImageDecoder.decodeBitmap(source){decoder,info,_->
    val w=info.size.width;val h=info.size.height
    require(w>0&&h>0&&w.toLong()*h<=100_000_000L){"Image dimensions are too large"}
    val factor=minOf(1f,2048f/maxOf(w,h));decoder.setTargetSize(maxOf(1,(w*factor).toInt()),maxOf(1,(h*factor).toInt()));decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
   }
   val name="wallpaper-$photoProfile.jpg";val target=File(filesDir,name);val temp=File(filesDir,"$name.tmp")
   try {temp.outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.JPEG,92,it))};check(temp.renameTo(target));previewData.change(photoProfile){it.copy(wallpaper=it.wallpaper.copy(image=name,zoom=1f,x=0f,y=0f,imageRevision=System.currentTimeMillis()))};if(previewData.error==null)previewData.shell.record(photoProfile,"Wallpaper import completed","wallpaper")} finally {bitmap.recycle();temp.delete()}
  }.onFailure{Toast.makeText(this,"Unable to import image: ${it.message}",Toast.LENGTH_LONG).show()}
 }
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState);photoProfile=savedInstanceState?.getString("photoProfile")?:"primary";enableEdgeToEdge();browserPool.pickFiles={pickFiles.launch(arrayOf("*/*"))}
  setContent { NovaPreview(previewData,browserPool,mindData,mindAudio, onPickImage={photoProfile=previewData.state.current;pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},onOpen={openWebsite(it)},filesDir=filesDir) }
 }
 override fun onResume(){super.onResume();browserPool.library.refreshDownloads(previewData.shell);browserPool.foreground=true;browserPool.tabs.filter{it.view.parent!=null}.forEach{it.wrapper.baseContext=this};browserPool.syncLifecycle()}
 override fun onStop(){super.onStop();if(!isChangingConfigurations)mindAudio.pause();browserPool.detachContexts();android.webkit.CookieManager.getInstance().flush()}
 override fun onDestroy(){browserPool.pickFiles=null;if(!isChangingConfigurations){browserPool.upload?.onReceiveValue(null);browserPool.upload=null};super.onDestroy()}
 override fun onSaveInstanceState(outState:Bundle){outState.putString("photoProfile",photoProfile);super.onSaveInstanceState(outState)}
 private fun openWebsite(url:String){
  runCatching{
   val uri=Uri.parse(PreviewRules.https(url))
   val browser=CustomTabsClient.getPackageName(this,null)
   if(browser!=null){val tab=CustomTabsIntent.Builder().setShowTitle(true).build();tab.intent.setPackage(browser);tab.launchUrl(this,uri)}
   else startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW,uri).addCategory(Intent.CATEGORY_BROWSABLE),"Open website externally"))
  }.onFailure{Toast.makeText(this,"No compatible browser is available. Install a browser to open websites.",Toast.LENGTH_LONG).show()}
 }
}
