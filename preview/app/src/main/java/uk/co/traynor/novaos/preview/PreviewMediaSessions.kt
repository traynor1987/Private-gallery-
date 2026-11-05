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
data class NovaSession(val tabId:String,val controller:MediaController,val title:String,val artist:String,val artwork:Bitmap?,val state:Int,val actions:Long){
 val playing get()=state==PlaybackState.STATE_PLAYING
 fun supports(action:Long)=actions and action!=0L
}
class PreviewMediaSessions(private val context:Context){
 var source by mutableStateOf<NovaSession?>(null);private set
 private val manager=context.getSystemService(MediaSessionManager::class.java)
 fun clear(){source=null}
 fun refresh(tabs:List<PreviewTab>,profile:String,foreground:Boolean,excluded:MediaSession.Token?=null){
  if(!foreground||tabs.any{it.profile!=profile}){clear();return}
  source=runCatching{
   val controllers=manager.getActiveSessionsForPackage(context.packageName).filter{it!=excluded}.map{MediaController(context,it)}.filter{it.packageName==context.packageName}
   val eligible=controllers.filter{it.playbackState?.state in listOf(PlaybackState.STATE_PLAYING,PlaybackState.STATE_PAUSED,PlaybackState.STATE_BUFFERING)}
   val running=eligible.filter{it.playbackState?.state in listOf(PlaybackState.STATE_PLAYING,PlaybackState.STATE_BUFFERING)}
   val selected=(if(running.isNotEmpty())running else eligible).singleOrNull()
   val matches=listOfNotNull(selected).mapNotNull{c->
    val meta=c.metadata?:return@mapNotNull null;val title=(meta.getString(MediaMetadata.METADATA_KEY_TITLE)?:meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)).orEmpty()
    val index=PolishRules.mediaOwner(title,tabs.map{it.mediaTitle});if(index<0)return@mapNotNull null
    val tab=tabs[index];if(tab.profile!=profile)return@mapNotNull null
    val playback=c.playbackState?:return@mapNotNull null
    if(playback.state !in listOf(PlaybackState.STATE_PLAYING,PlaybackState.STATE_PAUSED,PlaybackState.STATE_BUFFERING))return@mapNotNull null
    val bitmap=meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?:meta.getBitmap(MediaMetadata.METADATA_KEY_ART)?:meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    val art=bitmap?.let{if(maxOf(it.width,it.height)<=512)it else {val ratio=512f/maxOf(it.width,it.height);Bitmap.createScaledBitmap(it,(it.width*ratio).toInt().coerceAtLeast(1),(it.height*ratio).toInt().coerceAtLeast(1),true)}}
    NovaSession(tab.id,c,title.take(160),(meta.getString(MediaMetadata.METADATA_KEY_ARTIST)?:meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)).orEmpty().take(160),art,playback.state,playback.actions)
   }
   // Multiple sessions are ambiguous even if a website reuses its title. No guessed ownership.
   matches.singleOrNull()
  }.getOrNull()
 }
 fun perform(tabId:String,action:Long,tabs:List<PreviewTab>,profile:String,foreground:Boolean,excluded:MediaSession.Token?=null){
  val token=source?.controller?.sessionToken?:return
  refresh(tabs,profile,foreground,excluded);val s=source?:return
  if(s.tabId!=tabId||s.controller.sessionToken!=token||!s.supports(action))return
  runCatching{when(action){PlaybackState.ACTION_PLAY->s.controller.transportControls.play();PlaybackState.ACTION_PAUSE->s.controller.transportControls.pause();PlaybackState.ACTION_PLAY_PAUSE->if(s.playing)s.controller.transportControls.pause()else s.controller.transportControls.play();PlaybackState.ACTION_SKIP_TO_NEXT->s.controller.transportControls.skipToNext();PlaybackState.ACTION_SKIP_TO_PREVIOUS->s.controller.transportControls.skipToPrevious()}}
 }
}
