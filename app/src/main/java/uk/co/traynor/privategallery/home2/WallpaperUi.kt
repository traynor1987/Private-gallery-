package uk.co.traynor.privategallery.home2

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import java.io.File

@Composable internal fun WallpaperScreen(data:PreviewData,filesDir:File,onPickImage:()->Unit,onBack:()->Unit){
 val profile=data.profile
 val config=LocalConfiguration.current
 val ratio=config.screenWidthDp.toFloat()/config.screenHeightDp.coerceAtLeast(1)
 var draft by remember(data.state.current,profile.wallpaper.image,profile.wallpaper.imageRevision,File(filesDir,profile.wallpaper.image?:"none").lastModified()){mutableStateOf(profile.wallpaper)}
 Column(Modifier.fillMaxSize().safeDrawingPadding()){
  PreviewHeader("Wallpapers",data.profileLabel,onBack)
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   Box(Modifier.fillMaxWidth().height(250.dp),contentAlignment=Alignment.Center){
   Box(Modifier.heightIn(max=240.dp).aspectRatio(ratio,matchHeightConstraintsFirst=true).clip(RoundedCornerShape(22.dp)).testTag("wallpaper-preview")){
    WallpaperSurface(draft,filesDir,Modifier.fillMaxSize())
    Column(Modifier.align(Alignment.Center).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("12:45",fontSize=(if(ratio<.6f)24 else 38).sp,color=Color.White);Text("NovaOS",fontSize=9.sp,color=Color.White)}
   }
   }
   Text("Original Nova wallpapers",style=MaterialTheme.typography.titleMedium)
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Aurora","Midnight","Dusk").forEach{value->FilterChip(selected=draft.image==null&&draft.builtin==value,onClick={draft=draft.copy(builtin=value,image=null)},label={Text(value)})}}
   OutlinedButton(onClick=onPickImage,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.AddPhotoAlternate,null);Text("Choose from system photo picker",Modifier.padding(start=8.dp))}
   if(draft.image!=null){Text("Crop and reposition",style=MaterialTheme.typography.titleMedium);Text("Zoom · ${"%.1f".format(draft.zoom)}×");Slider(draft.zoom,{draft=draft.copy(zoom=it)},valueRange=1f..3f);Text("Horizontal position");Slider(draft.x,{draft=draft.copy(x=it)},valueRange=-1f..1f);Text("Vertical position");Slider(draft.y,{draft=draft.copy(y=it)},valueRange=-1f..1f)}
   Text("Blur · ${draft.blur.toInt()}");Slider(draft.blur,{draft=draft.copy(blur=it)},valueRange=0f..24f)
   Text("Dimming · ${(draft.dim*100).toInt()}%");Slider(draft.dim,{draft=draft.copy(dim=it)},valueRange=0f.. .8f)
   Button(onClick={data.change{it.copy(wallpaper=draft)};onBack()},modifier=Modifier.fillMaxWidth().testTag("apply-wallpaper")){Text("Apply to ${data.profileLabel}")}
   TextButton(onClick={draft=Wallpaper(builtin=if(data.state.current=="hidden")"Midnight"else "Aurora")},modifier=Modifier.fillMaxWidth()){Text("Restore default wallpaper")}
   Text("Wallpaper pictures are encrypted within the authenticated Space. Crop follows screen aspect ratio when folding.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   Spacer(Modifier.height(20.dp))
  }
 }
}
