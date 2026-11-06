package uk.co.traynor.privategallery.home2

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** One draft editor for Home and the encrypted image viewer. */
@Composable internal fun WallpaperScreen(
 draft:Wallpaper,preview:Bitmap?,ownerLabel:String,filesDir:File,busy:Boolean,error:String?,
 onChange:(Wallpaper)->Unit,onBuiltin:(String)->Unit,onChooseVault:()->Unit,onPickSystem:()->Unit,
 onApply:()->Unit,onBack:()->Unit,allowSources:Boolean=true,
) {
 val config=LocalConfiguration.current
 val ratio=config.screenWidthDp.toFloat()/config.screenHeightDp.coerceAtLeast(1)
 Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("wallpaper-editor")) {
  PreviewHeader("Wallpapers",ownerLabel,onBack)
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
   Box(Modifier.fillMaxWidth().height(250.dp),contentAlignment=Alignment.Center) {
    Box(Modifier.heightIn(max=240.dp).aspectRatio(ratio,matchHeightConstraintsFirst=true).clip(RoundedCornerShape(22.dp)).testTag("wallpaper-preview")) {
     WallpaperSurface(draft,filesDir,Modifier.fillMaxSize(),preview)
     Column(Modifier.align(Alignment.Center).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
      Text("12:45",fontSize=(if(ratio<.6f)24 else 38).sp,color=androidx.compose.ui.graphics.Color.White)
      Text("NovaOS",fontSize=9.sp,color=androidx.compose.ui.graphics.Color.White)
     }
    }
   }
   Text("Original Nova wallpapers",style=MaterialTheme.typography.titleMedium)
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    listOf("Aurora","Midnight","Dusk").forEach{value->FilterChip(enabled=!busy,selected=preview==null&&draft.image==null&&draft.builtin==value,onClick={onBuiltin(value)},label={Text(value)},modifier=Modifier.testTag("wallpaper-builtin-$value"))}
   }
   if(allowSources) {
    OutlinedButton(onClick=onChooseVault,enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("choose-vault-wallpaper")){Icon(Icons.Default.PhotoLibrary,null);Text("Choose from NovaOS Vault",Modifier.padding(start=8.dp))}
    OutlinedButton(onClick=onPickSystem,enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("choose-system-wallpaper")){Icon(Icons.Default.AddPhotoAlternate,null);Text("Choose from system photo picker",Modifier.padding(start=8.dp))}
   }
   if(preview!=null||draft.image!=null) {
    Text("Crop and reposition",style=MaterialTheme.typography.titleMedium)
    Text("Zoom · ${"%.1f".format(draft.zoom)}×");Slider(draft.zoom,{onChange(draft.copy(zoom=it))},enabled=!busy,valueRange=1f..3f,modifier=Modifier.testTag("wallpaper-zoom"))
    Text("Horizontal position");Slider(draft.x,{onChange(draft.copy(x=it))},enabled=!busy,valueRange=-1f..1f)
    Text("Vertical position");Slider(draft.y,{onChange(draft.copy(y=it))},enabled=!busy,valueRange=-1f..1f)
   }
   Text("Blur · ${draft.blur.toInt()}");Slider(draft.blur,{onChange(draft.copy(blur=it))},enabled=!busy,valueRange=0f..24f)
   Text("Dimming · ${(draft.dim*100).toInt()}%");Slider(draft.dim,{onChange(draft.copy(dim=it))},enabled=!busy,valueRange=0f.. .8f)
   error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("wallpaper-error"))}
   if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
   Button(onClick=onApply,enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("apply-wallpaper")){Text("Apply to $ownerLabel")}
   TextButton(onClick={onBuiltin(if(ownerLabel=="Hidden Space")"Midnight"else "Aurora")},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("restore-wallpaper-default")){Text("Restore default wallpaper")}
   TextButton(onClick=onBack,modifier=Modifier.fillMaxWidth().testTag("cancel-wallpaper")){Text("Cancel")}
   Text("Wallpaper pictures are encrypted within the authenticated Space. Crop follows screen aspect ratio when folding.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   Spacer(Modifier.height(20.dp))
  }
 }
}
