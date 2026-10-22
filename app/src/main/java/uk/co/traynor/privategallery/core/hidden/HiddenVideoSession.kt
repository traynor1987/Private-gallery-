@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package uk.co.traynor.privategallery.core.hidden

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import uk.co.traynor.privategallery.core.vault.ProtectedVideoSession
import java.io.IOException

/** Retains an epoch-bound repository, never a decrypted file, key or plaintext cache. */
internal class HiddenVideoSession(private val repository:HiddenRepository, private val id:String,
    private val cancelled:()->Boolean) : ProtectedVideoSession {
    @Volatile private var closed=false
    override val sourceFactory=DataSource.Factory {
        object:DataSource {
            private var position=0L
            private var remaining=0L
            private var uri:Uri?=null
            override fun addTransferListener(listener:TransferListener)=Unit
            private fun allowed(){if(closed || cancelled())throw IOException("Hidden video closed")}
            override fun open(spec:DataSpec):Long {
                allowed();val size=repository.mediaSize(id)
                require(spec.position in 0..size)
                position=spec.position
                remaining=if(spec.length==androidx.media3.common.C.LENGTH_UNSET.toLong())size-position else minOf(spec.length,size-position)
                require(remaining>=0);uri=spec.uri;return remaining
            }
            override fun read(buffer:ByteArray,offset:Int,length:Int):Int {
                allowed();if(length==0)return 0;if(remaining==0L)return -1
                val count=minOf(length.toLong(),remaining).toInt()
                try {val n=repository.readRange(id,position,buffer,offset,count);allowed()
                    if(n>0){position+=n;remaining-=n};return n
                }catch(failure:Throwable){buffer.fill(0,offset,offset+count);throw IOException("Hidden video unavailable",failure)}
            }
            override fun getUri()=uri
            override fun close(){uri=null;remaining=0}
        }
    }
    override fun close(){closed=true}
}
