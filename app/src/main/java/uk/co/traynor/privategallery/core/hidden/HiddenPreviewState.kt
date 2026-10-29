package uk.co.traynor.privategallery.core.hidden

import android.graphics.Bitmap
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Process-local preview ownership only: no keys, paths or persisted media metadata. */
internal object HiddenPreviewState {
    private class State {
        val revision=MutableStateFlow(0L)
        val bitmaps=mutableListOf<WeakReference<Bitmap>>()
    }
    private val states=WeakHashMap<HiddenSession,State>()
    private val gate=Any()
    private fun state(session:HiddenSession)=states.getOrPut(session){State()}
    fun revisions(session:HiddenSession):StateFlow<Long> = synchronized(gate){state(session).revision}
    fun epoch(session:HiddenSession):Long = synchronized(gate){state(session).revision.value}
    fun track(session:HiddenSession,epoch:Long,bitmap:Bitmap):Boolean = synchronized(gate) {
        val state=state(session)
        if(state.revision.value!=epoch)return@synchronized false
        state.bitmaps.removeAll {it.get()?.isRecycled!=false}
        state.bitmaps.add(WeakReference(bitmap));true
    }
    /** Callback only updates UI state; it must not acquire session/repository authority. */
    fun deliver(session:HiddenSession,epoch:Long,callback:()->Unit):Boolean = synchronized(gate) {
        if(state(session).revision.value!=epoch)return@synchronized false
        callback();true
    }
    /** Called before a media-state mutation, outside the authentication commit monitor. */
    fun invalidate(session:HiddenSession) = synchronized(gate) {
        val state=state(session)
        val retired=state.bitmaps.toList()
        state.bitmaps.clear()
        state.revision.value=state.revision.value+1
        var failure:RuntimeException?=null
        retired.forEach {reference ->reference.get()?.let {bitmap ->
            try {if(!bitmap.isRecycled && bitmap.isMutable)bitmap.eraseColor(android.graphics.Color.TRANSPARENT)}
            catch(error:RuntimeException) {if(!bitmap.isRecycled && failure==null)failure=error}
        }}
        // Concurrent tile disposal is already retired; a genuine wipe failure blocks mutation.
        failure?.let {throw it}
    }
}
