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
    internal fun authorize(key:ByteArray)=synchronized(monitor){checkActive();require(key.size==32);primaryKey?.fill(0);primaryKey=key.copyOf()}
    private fun journal()=TransferJournal(java.io.File(context.noBackupFilesDir,"hidden-media-v1/transfers-v1"),operation.key()) {commit -> publish {commit.run()}}
    fun resume(id:String,cancelled:()->Boolean,progress:(Int,Int)->Unit):Result=synchronized(ioGate) {
        val op=journal().load(id);require(op.direction=="HIDDEN_TO_PRIMARY" && op.status!="CANCELLED")
        transferRecorded(op,cancelled,progress)
    }
    fun transfer(ids:List<String>,destination:Destination,move:Boolean,progress:(Int,Int)->Unit):Result=synchronized(ioGate) {
        if(destination==Destination.PRIMARY)return@synchronized transferRecorded(journal().create("HIDDEN_TO_PRIMARY",move,ids),{false},progress)
        transferPhone(ids,destination,move,progress)
    }
    private fun transferRecorded(op:TransferJournal.Operation,cancelled:()->Boolean,progress:(Int,Int)->Unit):Result {
        checkActive();val owned=synchronized(monitor){checkActive();checkNotNull(primaryKey){"Fresh Primary authentication required"}.copyOf()}
        try {
            val source=HiddenRepository(context,hidden,publishIfAllowed={commit -> publish {check(!cancelled());commit()}})
            val j=journal();op.status="RUNNING";op.ended=0;j.save(op)
            val primary=AndroidVaultRepository(context,owned)
            for((index,record) in op.items.withIndex()) {
                if(cancelled()||!isActive())break
                progress(index,op.items.size)
                if(!isActive())return Result(op.verified(),op.items.count {it.phase=="MOVED"},op.failed(),op.items.size)
                try {
                    val marker="transfer176:${op.id}:${record.source}"
                    if(record.phase=="MOVED" || (!op.move && record.phase=="VERIFIED")) {
                        val copy=primary.items().first {it.id==record.destination};primary.verifyTransferDestination(copy){!isActive()}
                        progress(index+1,op.items.size);continue
                    }
                    if(op.move && record.destination.isNotEmpty()) {
                        val trashed=source.recentlyDeleted().firstOrNull {it.id==record.source}
                        if(trashed!=null) {
                            val copy=primary.items().first {it.id==record.destination}
                            check(copy.plaintextSize==trashed.plaintextSize && MessageDigest.isEqual(copy.plaintextSha256,trashed.plaintextSha256))
                            primary.verifyTransferDestination(copy){!isActive()};record.phase="MOVED";j.save(op);progress(index+1,op.items.size);continue
                        }
                    }
                    source.withTransferSource(record.source,{!isActive()}) {item,input ->
                        val existing=primary.items().singleOrNull {it.sourceUri==marker}
                        val copy=existing ?: when(val result=primary.importVerified(VaultImportSource(item.displayName,item.mimeType,{input},sourceReference=marker,
                            origin=item.origin,vaultOnly=item.vaultOnly,createDistinctCopy=true,capturedAtEpochMillis=item.capturedAtEpochMillis,
                            isCancelled={!isActive()},publishIfAllowed=::publish))) {
                            is ImportResult.Imported->result.item
                            is ImportResult.Duplicate->result.existing
                        }
                        if(existing!=null){val buffer=ByteArray(64*1024);try{while(true){checkActive();val n=input.read(buffer);if(n<0)break;check(n>0)}}finally{buffer.fill(0)}}
                        check(copy.plaintextSize==item.plaintextSize && MessageDigest.isEqual(copy.plaintextSha256,item.plaintextSha256))
                        primary.verifyTransferDestination(copy){!isActive()}
                        record.destination=copy.id;record.size=copy.plaintextSize;record.phase="VERIFIED";j.save(op)
                        if(op.move && !cancelled()){beforeSourceRemoval();source.trashVerifiedSource(item);record.phase="MOVED";j.save(op)}
                    }
                } catch(failure:Exception){
                    if(record.phase!="MOVED" && record.phase!="VERIFIED")record.phase="FAILED"
                    if(!isActive())return Result(op.verified(),op.items.count {it.phase=="MOVED"},op.failed(),op.items.size)
                    j.save(op)
                }
                progress(index+1,op.items.size)
            }
            checkActive();op.status=if(cancelled())"INTERRUPTED" else if(op.failed()>0 || (op.move&&op.items.any {it.phase!="MOVED"}))"PARTIAL" else "COMPLETE"
            op.ended=System.currentTimeMillis();j.save(op)
            return Result(op.verified(),op.items.count {it.phase=="MOVED"},op.failed(),op.items.size)
        } finally {owned.fill(0)}
    }
    private fun transferPhone(ids:List<String>,destination:Destination,move:Boolean,progress:(Int,Int)->Unit):Result=synchronized(ioGate) {
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
                            val result=VaultImportCoordinator(primary).acquire(VaultImportSource(item.displayName,item.mimeType,{input},capturedAtEpochMillis=item.capturedAtEpochMillis,
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
