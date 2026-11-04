package uk.co.traynor.novaos.preview

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
  PreviewHeader("Wallpapers",data.profileLabel+" · demo profile",onBack)
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   Box(Modifier.fillMaxWidth().height(250.dp),contentAlignment=Alignment.Center){
   Box(Modifier.heightIn(max=240.dp).aspectRatio(ratio,matchHeightConstraintsFirst=true).clip(RoundedCornerShape(22.dp)).testTag("wallpaper-preview")){
    WallpaperSurface(draft,filesDir,Modifier.fillMaxSize())
    Column(Modifier.align(Alignment.Center).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("12:45",fontSize=(if(ratio<.6f)24 else 38).sp,color=Color.White);Text("NovaOS Preview",fontSize=9.sp,color=Color.White)}
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
   Text("Imported pictures are copied only into this preview app. Profiles are visual demonstrations, not encrypted vaults. Crop follows screen aspect ratio when folding.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   Spacer(Modifier.height(20.dp))
  }
 }
}
@Composable internal fun PhotosDemo(onBack:()->Unit,onWallpaper:()->Unit){
 Column(Modifier.fillMaxSize().safeDrawingPadding()){
  PreviewHeader("Photos demo","Synthetic gallery · no production media",onBack)
  Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
   Text("A space for inspiration",style=MaterialTheme.typography.headlineMedium)
   Text("These original colour studies are synthetic demo tiles. This screen never reads your production Gallery or vault.")
   listOf(listOf(Color(0xFF829DD3),Color(0xFF233252)),listOf(Color(0xFFC598B3),Color(0xFF6C607E)),listOf(Color(0xFF8AD5C3),Color(0xFF224F65))).forEachIndexed{i,colors->Box(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(colors)),contentAlignment=Alignment.BottomStart){Text(listOf("Blue hour","Soft horizon","Northern tide")[i],Modifier.padding(20.dp),color=Color.White)}}
   Button(onClick=onWallpaper,modifier=Modifier.fillMaxWidth()){Text("Make a wallpaper yours")}
  }
 }
}
@Composable internal fun GuideScreen(onBack:()->Unit){
 Column(Modifier.fillMaxSize().safeDrawingPadding()){
  PreviewHeader("NovaOS Preview","Home 2.0 + Nova Store",onBack)
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
   Text("Your new Home, ready to explore.",style=MaterialTheme.typography.headlineLarge)
   Text("Swipe up for apps, down for the Quick Panel, or sideways between Home pages. Long-press empty space to customise. Every gesture has a button or three-dot menu alternative.")
   Text("Install a destination in Nova Store, then Add to Home or Pin to Dock. Long-press an app to rename, reorder, add to a folder or remove it.")
   Text("Primary Preview and Hidden Preview keep separate layouts, installed shortcuts, folders, wallpaper and Dock. Both are demonstrations. There is no authentication, vault encryption, Hidden VPN, Tor or production data access.")
   Text("Installed apps open inside an ordinary preview WebView. Browser data belongs to this separate app package and does not access production NovaOS. Preview profiles share browser cookies. A supported external browser option is available with confirmation for incompatible pages or sign-in providers.")
   Text("This development-signed application installs alongside production NovaOS. Uninstalling it removes only preview-local data. Website accounts and externally opened browser sessions remain managed by their provider/browser.")
  }
 }
}
