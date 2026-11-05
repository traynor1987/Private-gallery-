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
  runCatching{if(name!=sound||player==null){player?.release();player=null;val p=MediaPlayer();player=p;p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());getApplication<Application>().assets.openFd("calm/$name.wav").use{p.setDataSource(it.fileDescriptor,it.startOffset,it.length)};p.isLooping=true;p.prepare();sound=name};player?.setVolume(volume,volume);player?.start();playing=true;error=null}.onFailure{pause();error="Sound unavailable. Try another texture."}
 }
 fun pause(){player?.pause();playing=false}
 fun resume(){if(sound.isNotBlank())play(sound)}
 fun volume(value:Float){volume=value.coerceIn(0f,1f);player?.setVolume(volume,volume)}
 fun timer(minutes:Int){handler.removeCallbacks(expire);deadline=if(minutes>0)SystemClock.elapsedRealtime()+minutes*60000L else 0;if(deadline>0)handler.post(expire)}
 override fun onCleared(){handler.removeCallbacks(expire);player?.release();player=null}
}
