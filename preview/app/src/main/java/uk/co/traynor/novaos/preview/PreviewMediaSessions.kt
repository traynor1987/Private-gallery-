package uk.co.traynor.novaos.preview

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.runtime.*

/** API37: only our package; never global media-key dispatch or notification access. */
data class NovaSession(val tabId:String,val controller:MediaController,val title:String,val artist:String,val artwork:Bitmap?,val state:Int,val actions:Long,val durationMs:Long?=null,val positionMs:Long?=null,val providerMatched:Boolean=false){
 val playing get()=state==PlaybackState.STATE_PLAYING
 fun supports(action:Long)=actions and action!=0L
}
class PreviewMediaSessions(private val context:Context){
 var capabilitySummary by mutableStateOf("No native media inspection yet.");private set
 var source by mutableStateOf<NovaSession?>(null);private set
 private val manager=context.getSystemService(MediaSessionManager::class.java)
 fun clear(){source=null;capabilitySummary="No attributed native media source."}
 fun refresh(tabs:List<PreviewTab>,profile:String,foreground:Boolean,excluded:MediaSession.Token?=null){
  if(!foreground||tabs.any{it.profile!=profile}){clear();capabilitySummary=if(!foreground)"NovaOS is not in the foreground."else "Other preview profiles have retained pages; controls are disabled for privacy.";return}
  source=runCatching{
   val controllers=manager.getActiveSessionsForPackage(context.packageName).filter{it!=excluded}.map{MediaController(context,it)}.filter{it.packageName==context.packageName}
   val eligible=controllers.filter{it.playbackState?.state in listOf(PlaybackState.STATE_PLAYING,PlaybackState.STATE_PAUSED,PlaybackState.STATE_BUFFERING)}
   val running=eligible.filter{it.playbackState?.state in listOf(PlaybackState.STATE_PLAYING,PlaybackState.STATE_BUFFERING)}
   val selected=(if(running.isNotEmpty())running else eligible).singleOrNull()
   val metadata=selected?.metadata;val state=selected?.playbackState
   fun supplied(key:String)=metadata?.containsKey(key)==true
   val actions=state?.actions?:0L
   capabilitySummary=buildString{
    append(if(eligible.isEmpty())"No retained website currently publishes a native media session."else "Native sessions: ${eligible.size}. Retained web pages: ${tabs.size}.")
    append("\nState: ${if(state!=null)"provided"else "unavailable"}; title: ${supplied(MediaMetadata.METADATA_KEY_TITLE)||supplied(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)}; artist: ${supplied(MediaMetadata.METADATA_KEY_ARTIST)}; artwork: ${supplied(MediaMetadata.METADATA_KEY_ALBUM_ART)||supplied(MediaMetadata.METADATA_KEY_ART)}.")
    append("\nDuration: ${supplied(MediaMetadata.METADATA_KEY_DURATION)}; position: ${state!=null&&state.position>=0}.")
    append("\nAdvertised play/pause: ${actions and (PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)!=0L}; previous: ${actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS!=0L}; next: ${actions and PlaybackState.ACTION_SKIP_TO_NEXT!=0L}.")
   }
   val matches=listOfNotNull(selected).mapNotNull{c->
    val meta=c.metadata;val title=(meta?.getString(MediaMetadata.METADATA_KEY_TITLE)?:meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)).orEmpty()
    val index=PolishRules.mediaOwner(title,tabs.map{it.mediaTitle});if(index<0)return@mapNotNull null
    val tab=tabs[index];if(tab.profile!=profile)return@mapNotNull null
    val playback=c.playbackState?:return@mapNotNull null
    if(playback.state !in listOf(PlaybackState.STATE_PLAYING,PlaybackState.STATE_PAUSED,PlaybackState.STATE_BUFFERING))return@mapNotNull null
    val bitmap=meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?:meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)?:meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    val art=bitmap?.let{if(maxOf(it.width,it.height)<=512)it else {val ratio=512f/maxOf(it.width,it.height);Bitmap.createScaledBitmap(it,(it.width*ratio).toInt().coerceAtLeast(1),(it.height*ratio).toInt().coerceAtLeast(1),true)}}
    NovaSession(tab.id,c,title.take(160),(meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)?:meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)).orEmpty().take(160),art,playback.state,playback.actions,meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf{it>0},playback.position.takeIf{it>=0}?.let{position->val elapsed=if(playback.state==PlaybackState.STATE_PLAYING&&playback.lastPositionUpdateTime>0)((android.os.SystemClock.elapsedRealtime()-playback.lastPositionUpdateTime)*playback.playbackSpeed).toLong().coerceAtLeast(0)else 0;position+elapsed},tab.mediaTitle.isNotBlank()&&tab.mediaTitle==title)
   }
   // Multiple sessions are ambiguous even if a website reuses its title. No guessed ownership.
   matches.singleOrNull().also{capabilitySummary+="\nSource ownership: "+if(it?.providerMatched==true)"website metadata matched."else if(it!=null)"package-local session only; the sole retained app is a return shortcut, not provider-verified ownership."else "unavailable or ambiguous; Return to Player only."}
  }.onFailure{capabilitySummary="Native media discovery is unavailable on this Android build. Return to Player remains available."}.getOrNull()
 }
 fun perform(tabId:String,action:Long,tabs:List<PreviewTab>,profile:String,foreground:Boolean,excluded:MediaSession.Token?=null){
  val token=source?.controller?.sessionToken?:return
  refresh(tabs,profile,foreground,excluded);val s=source?:return
  if(s.tabId!=tabId||s.controller.sessionToken!=token||!s.supports(action))return
  runCatching{when(action){PlaybackState.ACTION_PLAY->s.controller.transportControls.play();PlaybackState.ACTION_PAUSE->s.controller.transportControls.pause();PlaybackState.ACTION_PLAY_PAUSE->if(s.playing)s.controller.transportControls.pause()else s.controller.transportControls.play();PlaybackState.ACTION_SKIP_TO_NEXT->s.controller.transportControls.skipToNext();PlaybackState.ACTION_SKIP_TO_PREVIOUS->s.controller.transportControls.skipToPrevious()}}
 }
}
