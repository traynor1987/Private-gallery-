package uk.co.traynor.privategallery.home2

import android.app.Application
import android.media.session.PlaybackState
import androidx.compose.ui.graphics.asImageBitmap
import android.content.MutableContextWrapper
import android.net.Uri
import android.widget.VideoView
import android.view.ViewGroup
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay

class NovaVideoModel(app:Application):AndroidViewModel(app){
 val wrapper=MutableContextWrapper(app)
 private var wantsPlay=false
 private var pendingSeek=0
 fun detachSurface(){if(prepared){pendingSeek=maxOf(position,runCatching{view?.currentPosition?:position}.getOrDefault(position));prepared=false}}
 var view:VideoView?=null;private set
 var uri by mutableStateOf<Uri?>(null);private set
 var title by mutableStateOf("Local video");private set
 var prepared by mutableStateOf(false);private set
 var playing by mutableStateOf(false);private set
 var duration by mutableIntStateOf(0);private set
 var position by mutableIntStateOf(0);private set
 var width by mutableIntStateOf(16);private set
 var height by mutableIntStateOf(9);private set
 var mini by mutableStateOf(false)
 var pip by mutableStateOf(false)
 var locked=false
 var profile:String?=null;private set
 var error by mutableStateOf<String?>(null)
 var x by mutableFloatStateOf(.95f)
 var y by mutableFloatStateOf(.85f)
 var scale by mutableFloatStateOf(.6f)
 var pick:(()->Unit)?=null
 var enterPip:(()->Unit)?=null
 fun open(uri:Uri,name:String,owner:String){
  close();wantsPlay=true;pendingSeek=0;profile=owner;this.uri=uri;title=name.take(100).ifBlank{"Local video"};val v=VideoView(wrapper);v.setAudioFocusRequest(android.media.AudioManager.AUDIOFOCUS_GAIN);view=v
  v.setOnPreparedListener{p->if(view!==v)return@setOnPreparedListener;prepared=true;duration=p.duration.coerceAtLeast(0);width=p.videoWidth.coerceAtLeast(1);height=p.videoHeight.coerceAtLeast(1);error=null;if(pendingSeek>0){v.seekTo(pendingSeek);position=pendingSeek;pendingSeek=0};if(wantsPlay)v.start();playing=wantsPlay}
  v.setOnCompletionListener{if(view===v){wantsPlay=false;playing=false;position=duration}}
  v.setOnErrorListener{_,_,_->if(view!==v)return@setOnErrorListener true;error="This local video could not play. Its format may be unsupported.";playing=false;prepared=false;true}
  v.setVideoURI(uri)
 }
 fun poll(){val v=view?:return;if(prepared)runCatching{position=v.currentPosition.coerceAtLeast(0);playing=v.isPlaying}}
 fun pause(){wantsPlay=false;if(prepared)runCatching{view?.pause()};playing=false}
 fun play(){wantsPlay=true;if(prepared){view?.start();playing=true}}
 fun seek(ms:Int){val target=ms.coerceIn(0,duration);position=target;pendingSeek=target;if(prepared){view?.seekTo(target);pendingSeek=0}}
 fun close(){(view?.parent as? ViewGroup)?.removeView(view);view?.stopPlayback();view=null;uri=null;wantsPlay=false;pendingSeek=0;prepared=false;playing=false;position=0;duration=0;mini=false;error=null;profile=null}
 override fun onCleared(){close();pick=null;enterPip=null;wrapper.baseContext=getApplication<Application>()}
}

@Composable internal fun NowPlaying(pool:PreviewBrowserModel,audio:MindAudio,video:NovaVideoModel,profile:String,onResume:(PreviewTab)->Unit,onVideo:()->Unit,onAudio:()->Unit,compact:Boolean=false,privacy:Boolean=false){
 val web=pool.player(profile)
 val fallback=pool.recents(profile).firstOrNull{it.app.category=="Entertainment"}
 val nativeVideo=video.profile==profile&&video.uri!=null&&(video.playing||(!audio.playing&&web?.mediaState!="playing"))
 val nativeAudio=audio.sound.isNotBlank()&&audio.profileOwner==profile
 Column(Modifier.fillMaxWidth().testTag("now-playing"),verticalArrangement=Arrangement.spacedBy(3.dp)){
  if(privacy){Text("Now Playing",style=MaterialTheme.typography.labelMedium);Text(if(nativeVideo||nativeAudio||web!=null)"Media available after unlock"else "No active media",style=MaterialTheme.typography.bodySmall);return@Column}
  when{
   nativeVideo->{Text("Local video",style=MaterialTheme.typography.labelSmall);Text(video.title,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis);if(!compact&&video.duration>0)Slider(video.position.toFloat().coerceIn(0f,video.duration.toFloat()),{video.seek(it.toInt())},valueRange=0f..video.duration.toFloat(),enabled=video.prepared);Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick={if(video.playing)video.pause()else video.play()},enabled=video.prepared){Icon(if(video.playing)Icons.Default.Pause else Icons.Default.PlayArrow,"Video play or pause")};TextButton(onClick=onVideo){Text("Return")};if(!compact)Text("${video.position/1000}s / ${video.duration/1000}s",style=MaterialTheme.typography.labelSmall)}}
   nativeAudio&&(audio.playing||web?.mediaState!="playing")->{Text("Nova Mind · ${if(audio.playing)"playing"else "paused"}",style=MaterialTheme.typography.labelSmall);Text(audio.sound.replaceFirstChar{it.uppercase()}+" texture",style=MaterialTheme.typography.titleSmall,maxLines=1);if(!compact&&audio.duration>0)LinearProgressIndicator(progress={audio.position.toFloat()/audio.duration},modifier=Modifier.fillMaxWidth());Row{if(!compact)IconButton(onClick={audio.previous()}){Icon(Icons.Default.SkipPrevious,"Previous calm sound")};IconButton(onClick={if(audio.playing)audio.pause()else audio.resume()}){Icon(if(audio.playing)Icons.Default.Pause else Icons.Default.PlayArrow,"Calm sound play or pause")};if(!compact)IconButton(onClick={audio.next()}){Icon(Icons.Default.SkipNext,"Next calm sound")};IconButton(onClick=onAudio){Icon(Icons.Default.OpenInNew,"Return to calm sounds")}}}
   web!=null->{Text(web.app.name,style=MaterialTheme.typography.labelSmall);Text(web.mediaTitle.ifBlank{web.app.name},maxLines=if(compact)1 else 2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall);if(!compact)Text("Website reports ${web.mediaState}",style=MaterialTheme.typography.labelSmall);Row{IconButton(onClick={},enabled=false){Icon(Icons.Default.PlayArrow,"Website transport unavailable")};TextButton(onClick={onResume(web)}){Text(if(compact)"Open"else "Return to App")}}}
   fallback!=null->{Text(fallback.app.name,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis);Text("Playback details unavailable",style=MaterialTheme.typography.bodySmall);TextButton(onClick={onResume(fallback)}){Text("Return to Player")}}
   else->{Text("Now Playing",style=MaterialTheme.typography.labelMedium);Text("Open a player to begin.",style=MaterialTheme.typography.bodySmall)}
  }
 }
}

@Composable internal fun NativeVideoSurface(video:NovaVideoModel,modifier:Modifier=Modifier){
 val context=LocalContext.current
 val view=video.view?:return
 key(view){AndroidView(factory={if(view.parent!=null)video.detachSurface();(view.parent as? ViewGroup)?.removeView(view);video.wrapper.baseContext=context;view},modifier=modifier,update={video.wrapper.baseContext=context},onReset=null,onRelease={video.detachSurface();(it.parent as? ViewGroup)?.removeView(it);video.wrapper.baseContext=context.applicationContext})}
}
@Composable internal fun VideoScreen(video:NovaVideoModel,onHome:()->Unit){
 LaunchedEffect(video){while(true){video.poll();delay(500)}}
 Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("nova-video")){
  PreviewHeader("Nova Video","User-selected local video · no website extraction",onHome)
  if(video.uri==null)Column(Modifier.padding(24.dp)){Text("Choose a video you can access on this device. Your selection is not included in Home backups.");Button(onClick={video.pick?.invoke()},Modifier.padding(top=18.dp)){Text("Choose local video")}}
  else if(!video.mini){
   Box(Modifier.weight(1f).fillMaxWidth().background(androidx.compose.ui.graphics.Color.Black),contentAlignment=Alignment.Center){NativeVideoSurface(video,Modifier.fillMaxWidth().aspectRatio(video.width.toFloat()/video.height))}
   Column(Modifier.fillMaxWidth().padding(16.dp)){Text(video.title,maxLines=1);if(video.duration>0)Slider(video.position.toFloat().coerceIn(0f,video.duration.toFloat()),{video.seek(it.toInt())},valueRange=0f..video.duration.toFloat());Row(Modifier.horizontalScroll(rememberScrollState())){TextButton(onClick={if(video.playing)video.pause()else video.play()},enabled=video.prepared){Text(if(video.playing)"Pause"else "Play")};TextButton(onClick={video.mini=true;onHome()},enabled=video.prepared){Text("Mini player")};TextButton(onClick={video.enterPip?.invoke()},enabled=WidgetRules.pipEligible(true,video.prepared,video.locked)){Text("Android PiP")};TextButton(onClick={video.close()}){Text("Close")}}}
  }else TextButton(onClick={video.mini=false}){Text("Expand video")}
  video.error?.let{Text(it,Modifier.padding(20.dp),color=MaterialTheme.colorScheme.error)}
 }
}
@Composable internal fun VideoMiniPlayer(video:NovaVideoModel,onExpand:()->Unit){
 if(!video.mini||video.uri==null||video.locked)return
 BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()){
  val maxW=maxWidth;val maxH=maxHeight;val w=(maxW*video.scale).coerceIn(minOf(180.dp,maxW),minOf(460.dp,maxW));val aspect=video.width.toFloat()/video.height;val h=(w/aspect+84.dp).coerceAtMost(maxH)
  val density=androidx.compose.ui.platform.LocalDensity.current
  Surface(Modifier.offset((maxW-w)*video.x,(maxH-h)*video.y).width(w).height(h).testTag("video-mini-player"),shape=RoundedCornerShape(20.dp),shadowElevation=10.dp){Column{
   Row(Modifier.fillMaxWidth().height(36.dp).pointerInput(maxW,maxH,w,h){detectDragGestures{change,drag->change.consume();video.x=(video.x+drag.x/with(density){(maxW-w).toPx().coerceAtLeast(1f)}).coerceIn(0f,1f);video.y=(video.y+drag.y/with(density){(maxH-h).toPx().coerceAtLeast(1f)}).coerceIn(0f,1f)}},verticalAlignment=Alignment.CenterVertically){Text("Local video",Modifier.weight(1f).padding(start=12.dp),style=MaterialTheme.typography.labelSmall);IconButton(onClick={video.close()},Modifier.size(34.dp)){Icon(Icons.Default.Close,"Close mini video",Modifier.size(18.dp))}}
   Box(Modifier.weight(1f).fillMaxWidth().background(androidx.compose.ui.graphics.Color.Black),contentAlignment=Alignment.Center){NativeVideoSurface(video,Modifier.fillMaxWidth().aspectRatio(aspect))}
   Row(Modifier.fillMaxWidth().height(48.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick={if(video.playing)video.pause()else video.play()}){Icon(if(video.playing)Icons.Default.Pause else Icons.Default.PlayArrow,"Mini video play or pause")};IconButton(onClick={video.mini=false;onExpand()}){Icon(Icons.Default.OpenInFull,"Expand video")};Spacer(Modifier.weight(1f));Icon(Icons.Default.DragHandle,"Drag to resize video",Modifier.size(32.dp).pointerInput(maxW){detectDragGestures{change,drag->change.consume();video.scale=(video.scale+drag.x/with(density){maxW.toPx()}).coerceIn(.35f,.9f)}})}}}
 }
}

