package uk.co.traynor.privategallery.home2
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
/** Native sounds request focus only on an explicit play action. WebView keeps its own platform focus integration. */
class PreviewAudioFocus(context:Context,onLoss:()->Unit){
 private val manager=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
 private val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setOnAudioFocusChangeListener{change->if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)onLoss()}.build()
 fun acquire()=manager.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED
 fun release(){manager.abandonAudioFocusRequest(request)}
}
