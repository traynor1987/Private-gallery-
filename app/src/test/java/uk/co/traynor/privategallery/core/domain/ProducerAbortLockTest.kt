package uk.co.traynor.privategallery.core.domain

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import org.junit.Assert.*
import org.junit.Test

class ProducerAbortLockTest {
  @Test fun primaryRegistrationAbortClosesPhysicalOutputAfterStorageGate() { abort(false,true) }
  @Test fun hiddenRegistrationAbortClosesPhysicalOutputAfterStorageGate() { abort(false,false) }
  @Test fun primaryPendingIdentityAbortClosesPhysicalOutputAfterStorageGate() { abort(true,true) }
  @Test fun hiddenPendingIdentityAbortClosesPhysicalOutputAfterStorageGate() { abort(true,false) }

  private fun abort(identityFailure: Boolean, primary: Boolean) {
    val p=if(primary) PrimaryUsageFixture() else null; val v=if(primary) null else VideoFixture()
    try {
      val dir=p?.dir ?: v!!.dir; val identity=p?.identity ?: v!!.identity; val master=p?.master ?: v!!.master
      val usage=p?.usage ?: v!!.usage; val valid=p?.valid ?: v!!.valid
      val ctx=MediaContext(1,ByteArray(16) { 5 },1); val attempt=p?.reserve(ctx) ?: v!!.reserve(listOf(ctx))
      val faults=LedgerFaults { e ->
        if(!identityFailure && e.phase==LedgerPhase.REGISTRATION && e.point==LedgerFaultPoint.DURING_WRITE) throw IOException("registration stop")
        if(identityFailure && e.phase==LedgerPhase.OUTPUT && e.point==LedgerFaultPoint.AFTER_CANONICAL_REOPEN) {
          val path=usage.resolve(e.keyId); val replacement=Files.createTempFile(usage,"substitution-","")
          Files.write(replacement,Files.readAllBytes(path)); Files.move(replacement,path,ATOMIC_MOVE,REPLACE_EXISTING)
        }
      }
      val store=if(p!=null) MediaUsageStore.primaryTransfer(dir.toFile(),identity,p.operation,faults) else MediaUsageStore.hiddenMedia(dir.toFile(),identity,v!!.operation,faults)
      val output=store.openOutput(master,ctx,attempt,valid)
      val field=OwnedMediaOutput::class.java.getDeclaredField("channel").apply { isAccessible=true }
      val channel=field.get(output) as FileChannel
      val storage=if(primary) uk.co.traynor.privategallery.core.security.PrimaryVaultSetupGuard.storageLock else HiddenRootLocks.forFilesDir(dir.toFile())
      var closedUnderStorage=false; var closes=0
      field.set(output,ObservedChannel(channel) { closes++; closedUnderStorage=Thread.holdsLock(storage) })
      try {
        try { F1ChargedRecord.sealOwned(master,ctx,byteArrayOf(8),store,attempt,output,valid); fail("abort not reached") } catch (_: IOException) {} catch (_: SecurityException) {}
        assertEquals("failure must invalidate admission while caller retains physical resource",0,closes)
        assertTrue(channel.isOpen)
        output.close()
        assertEquals(1,closes); assertFalse(channel.isOpen)
        assertFalse("physical owned output close ran under storage gate",closedUnderStorage)
      } finally { output.close() }
    } finally { p?.close(); v?.close() }
  }

  /** Observes actual physical close while preserving the original real channel's IO. */
  private class ObservedChannel(private val real: FileChannel, private val closing: () -> Unit): FileChannel() {
    override fun read(dst: ByteBuffer)=real.read(dst)
    override fun read(dsts: Array<out ByteBuffer>,offset: Int,length: Int)=real.read(dsts,offset,length)
    override fun read(dst: ByteBuffer,position: Long)=real.read(dst,position)
    override fun write(src: ByteBuffer)=real.write(src)
    override fun write(srcs: Array<out ByteBuffer>,offset: Int,length: Int)=real.write(srcs,offset,length)
    override fun write(src: ByteBuffer,position: Long)=real.write(src,position)
    override fun position()=real.position()
    override fun position(position: Long): FileChannel { real.position(position); return this }
    override fun size()=real.size()
    override fun truncate(size: Long): FileChannel { real.truncate(size); return this }
    override fun force(metadata: Boolean)=real.force(metadata)
    override fun transferTo(position: Long,count: Long,target: WritableByteChannel)=real.transferTo(position,count,target)
    override fun transferFrom(src: ReadableByteChannel,position: Long,count: Long)=real.transferFrom(src,position,count)
    override fun map(mode: MapMode,position: Long,size: Long): MappedByteBuffer=real.map(mode,position,size)
    override fun lock(position: Long,size: Long,shared: Boolean): FileLock=real.lock(position,size,shared)
    override fun tryLock(position: Long,size: Long,shared: Boolean): FileLock?=real.tryLock(position,size,shared)
    override fun implCloseChannel() { closing(); real.close() }
  }
}
