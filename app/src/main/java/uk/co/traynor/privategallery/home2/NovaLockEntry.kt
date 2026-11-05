package uk.co.traynor.privategallery.home2

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Decorative entry only; Enter invokes the existing production authentication path. */
@Composable internal fun NovaLockEntry(onEnter:()->Unit){
 var now by remember{mutableStateOf(LocalDateTime.now())}
 LaunchedEffect(Unit){while(true){now=LocalDateTime.now();delay(1000)}}
 Box(Modifier.fillMaxSize().testTag("nova-protected-lock")){
  // Always bundled Aurora. No Space wallpaper or content is decrypted before authentication.
  WallpaperSurface(Wallpaper(),LocalContext.current.filesDir,Modifier.fillMaxSize())
  Column(Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
   Icon(Icons.Default.Lock,null,Modifier.size(32.dp),tint=Color.White)
   Text("NOVAOS",Modifier.padding(top=24.dp),color=Color.White,letterSpacing=4.sp)
   Text(now.format(DateTimeFormatter.ofPattern("HH:mm")),fontSize=64.sp,color=Color.White)
   Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),color=Color.White)
   Spacer(Modifier.height(30.dp))
   Button(onClick=onEnter,modifier=Modifier.testTag("enter-novaos")){Text("Enter NovaOS")}
  }
 }
}
