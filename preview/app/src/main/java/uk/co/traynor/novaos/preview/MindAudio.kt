package uk.co.traynor.novaos.preview

import android.app.Application
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel

/** Generated, bundled noncommercial-source-free textures. No background service or network. */
class MindAudio(app:Application):AndroidViewModel(app){
 private var profile:String?=null
 val profileOwner get()=profile
 var position by mutableIntStateOf(0);private set
 var duration by mutableIntStateOf(0);private set
 private val session=android.media.session.MediaSession(app,"Nova calm sounds").apply{setCallback(object:android.media.session.MediaSession.Callback(){override fun onPlay(){resume()};override fun onPause(){pause()};override fun onSkipToNext(){next()};override fun onSkipToPrevious(){previous()}})}
 fun poll(){runCatching{position=player?.currentPosition?:0;duration=player?.duration?:0}}
 private fun publish(){session.isActive=playing;session.setMetadata(android.media.MediaMetadata.Builder().putString(android.media.MediaMetadata.METADATA_KEY_TITLE,sound.replaceFirstChar{it.uppercase()}+" texture").putString(android.media.MediaMetadata.METADATA_KEY_ARTIST,"Nova Mind").putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,duration.toLong()).build());session.setPlaybackState(android.media.session.PlaybackState.Builder().setActions(android.media.session.PlaybackState.ACTION_PLAY or android.media.session.PlaybackState.ACTION_PAUSE or android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT or android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS).setState(if(playing)android.media.session.PlaybackState.STATE_PLAYING else android.media.session.PlaybackState.STATE_PAUSED,position.toLong(),if(playing)1f else 0f).build())}
 fun next(){val names=listOf("rain","ocean","wind","fireplace","white","ambient");play(names[(names.indexOf(sound)+1).mod(names.size)])}
 fun previous(){val names=listOf("rain","ocean","wind","fireplace","white","ambient");play(names[(names.indexOf(sound)-1).mod(names.size)])}
 fun switchProfile(next:String){if(profile!=null&&profile!=next)pause();profile=next}
 private var player:MediaPlayer?=null
 private val handler=Handler(Looper.getMainLooper())
 var sound by mutableStateOf("");private set
 var playing by mutableStateOf(false);private set
 var volume by mutableFloatStateOf(.35f);private set
 var deadline by mutableLongStateOf(0);private set
 var error by mutableStateOf<String?>(null);private set
 private val expire=object:Runnable{override fun run(){if(deadline>0&&SystemClock.elapsedRealtime()>=deadline){pause();deadline=0}else if(deadline>0)handler.postDelayed(this,500)}}
 fun play(name:String){
  require(name in listOf("rain","ocean","wind","fireplace","white","ambient"))
  runCatching{if(name!=sound||player==null){player?.release();player=null;val p=MediaPlayer();player=p;p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());getApplication<Application>().assets.openFd("calm/$name.wav").use{p.setDataSource(it.fileDescriptor,it.startOffset,it.length)};p.isLooping=true;p.prepare();sound=name};player?.setVolume(volume,volume);player?.start();playing=true;poll();publish();error=null}.onFailure{pause();error="Sound unavailable. Try another texture."}
 }
 fun pause(){player?.pause();playing=false;poll();publish()}
 fun resume(){if(sound.isNotBlank())play(sound)}
 fun volume(value:Float){volume=value.coerceIn(0f,1f);player?.setVolume(volume,volume)}
 fun timer(minutes:Int){handler.removeCallbacks(expire);deadline=if(minutes>0)SystemClock.elapsedRealtime()+minutes*60000L else 0;if(deadline>0)handler.post(expire)}
 override fun onCleared(){session.release();handler.removeCallbacks(expire);player?.release();player=null}
}
