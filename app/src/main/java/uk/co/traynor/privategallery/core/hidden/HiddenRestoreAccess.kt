package uk.co.traynor.privategallery.core.hidden

import android.content.Context
import java.security.MessageDigest
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore
import uk.co.traynor.privategallery.core.vault.*

/** One foreground restore request. Primary destination auth never changes active vault selection.
 * Lock order: Hidden repository -> Primary metadata -> monitor -> Hidden session.
 * Never acquire a repository gate from inside the monitor. */
internal class HiddenRestoreAccess(private val context:Context,private val hidden:HiddenSession,
    private val beforeSourceRemoval:()->Unit = {},
) : AutoCloseable {
    enum class Destination(val label:String) { PHONE("Phone Gallery"), PRIMARY("Primary Vault") }
    data class Result(val verified:Int,val moved:Int,val failed:Int,val total:Int)
    private val monitor=Any()
    private val ioGate=Any()
    private val operation=hidden.beginOperation()
    private var closed=false
    private var primaryKey:ByteArray?=null
    fun isActive():Boolean=synchronized(monitor){!closed && hidden.isCurrent(operation)}
    private fun checkActive(){check(isActive()) {"Restore ended. Authenticate again."}}
    private fun publish(block:()->Unit)=synchronized(monitor){checkActive();hidden.commit(operation,block)}
    fun authenticate(pin:CharArray) {
        var candidate:ByteArray?=null
        try {
            checkActive();val unlocked=PinVaultKeyStore(context).unlock(pin);candidate=unlocked
            synchronized(monitor){checkActive();primaryKey?.fill(0);primaryKey=unlocked.copyOf()}
        } finally {pin.fill('\u0000');candidate?.fill(0)}
    }
    fun transfer(ids:List<String>,destination:Destination,move:Boolean,progress:(Int,Int)->Unit):Result=synchronized(ioGate) {
        checkActive();val selected=ids.distinct();require(selected.size in 1..100)
        val owned=if(destination==Destination.PRIMARY)synchronized(monitor){checkActive();checkNotNull(primaryKey){"Fresh Primary authentication required"}.copyOf()} else null
        val source=HiddenRepository(context,hidden,publishIfAllowed=::publish)
        var verified=0;var moved=0;var failed=0
        try {
            val primary=owned?.let {AndroidVaultRepository(context,it)}
            selected.forEachIndexed {index,id ->
                if(!isActive())return@forEachIndexed
                progress(index,selected.size)
                try {
                    source.withTransferSource(id,{!isActive()}) {item,input ->
                        if(primary!=null) {
                            // Distinct identities preserve restrictions/provenance even if identical bytes
                            // already exist in Primary with different egress policy. Never merge metadata.
                            val result=VaultImportCoordinator(primary).acquire(VaultImportSource(item.displayName,item.mimeType,{input},
                                origin=item.origin,vaultOnly=item.vaultOnly,createDistinctCopy=true,
                                isCancelled={!isActive()},publishIfAllowed=::publish))
                            val copy=when(result){is ImportResult.Imported->result.item;is ImportResult.Duplicate->result.existing}
                            check(copy.plaintextSize==item.plaintextSize && MessageDigest.isEqual(copy.plaintextSha256,item.plaintextSha256))
                            primary.verifyTransferDestination(copy){!isActive()}
                        } else HiddenPhoneExport(context).export(item,input,::checkActive,::publish)
                        verified++
                        if(move) runCatching {beforeSourceRemoval();source.trashVerifiedSource(item);moved++}
                    }
                } catch(_:Exception) {failed++}
                progress(index+1,selected.size)
            }
            Result(verified,moved,failed,selected.size)
        } finally {owned?.fill(0)}
    }
    override fun close()=synchronized(monitor){closed=true;primaryKey?.fill(0);primaryKey=null;operation.close()}
}
