package uk.co.traynor.privategallery.core.domain

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import javax.crypto.Cipher
import org.junit.Assert.*
import org.junit.Test

class PendingLedgerIdentityTest {
  @Test fun pendingPrimary58ReplacementAtFinalRegistrationNeverFundsGcm() { finalRegistrationSwap(true) }
  @Test fun pendingU82ReplacementAtFinalRegistrationNeverFundsGcm() { finalRegistrationSwap(false) }

  private fun finalRegistrationSwap(primary: Boolean) {
    PendingFixture(primary).use { f ->
      var path: Path?=null; var pending: ByteArray?=null
      val store=f.store(LedgerFaults { e ->
        if(e.phase==LedgerPhase.REGISTRATION && e.point==LedgerFaultPoint.AFTER_CANONICAL_REOPEN) {
          path=f.usage.resolve(e.keyId); pending=Files.readAllBytes(path!!); substituteSameBytes(path!!)
        }
      })
      store.openOutput(f.master,f.context,f.attempt,f.valid).use { output -> CountingGcm().use {
        rejects { F1ChargedRecord.sealOwned(f.master,f.context,byteArrayOf(8),store,f.attempt,output,f.valid) }
        assertNotNull(path); assertEquals(0,CountingGcm.initializations)
        assertArrayEquals(pending,Files.readAllBytes(path!!))
      } }
    }
  }

  @Test fun pendingVideoFirstKeyReplacementDuringSecondRegistrationNeverFundsVideoGcm() {
    VideoFixture().use { f ->
      val attempt=f.reserve(listOf(f.context)); var first: Path?=null; var pending: ByteArray?=null; var registered=0
      val store=MediaUsageStore.hiddenMedia(f.dir.toFile(),f.identity,f.operation,LedgerFaults { e ->
        if(e.phase==LedgerPhase.REGISTRATION && e.point==LedgerFaultPoint.AFTER_CANONICAL_REOPEN) {
          registered++
          if(registered==1) { first=f.usage.resolve(e.keyId); pending=Files.readAllBytes(first!!) }
          if(registered==2) substituteSameBytes(first!!)
        }
      })
      store.openOutput(f.master,f.context,attempt,f.valid).use { output -> CountingGcm().use {
        rejects { F1Video.write(f.master,f.context,ByteArrayInputStream(byteArrayOf(8)),1,digest(byteArrayOf(8)),output,store,attempt,f.valid) }
        assertEquals(2,registered); assertEquals(1,CountingGcm.initializations) // Charged owner query only.
        assertArrayEquals(pending,Files.readAllBytes(first!!))
      } }
    }
  }

  @Test fun pendingPrimary58ReplacementBeforeConsumptionDenies() { permitBoundarySwap(true,false) }
  @Test fun pendingU82ReplacementBeforeConsumptionDenies() { permitBoundarySwap(false,false) }
  @Test fun pendingPrimary58ReplacementBeforeCompletionDenies() { permitBoundarySwap(true,true) }
  @Test fun pendingU82ReplacementBeforeCompletionDenies() { permitBoundarySwap(false,true) }

  private fun permitBoundarySwap(primary: Boolean, completing: Boolean) {
    PendingFixture(primary).use { f ->
      val store=f.store(); val header=F1Record.wholeHeader(F1Context(f.identity,1,f.context.objectId,1),ByteArray(32) { 7 },ByteArray(12) { 9 },1)
      val path=f.usage.resolve(digest(header.copyOfRange(12,104)).hex())
      store.openOutput(f.master,f.context,f.attempt,f.valid).use { output ->
        store.reserveEncryption(listOf(header),listOf(1L),listOf(12L),f.attempt,output,f.valid).use { permit ->
          output.claim(store,f.context,f.attempt,permit,173,header)
          val pending=Files.readAllBytes(path)
          CountingGcm().use {
            if(completing) {
              permit.consume(store,output,f.valid,header)
              val key=F1Record.deriveKey(f.master,header.copyOfRange(72,104),F1Context(f.identity,1,f.context.objectId,1))
              val bytes=try { header+F1Crypto.aead(Cipher.ENCRYPT_MODE,key,header.copyOfRange(104,116),header,byteArrayOf(8)) } finally { key.fill(0) }
              output.write(bytes); val completion=output.finish(digest(bytes))
              substituteSameBytes(path)
              rejects { store.complete(listOf(header),completion,permit,f.valid) }
              assertEquals(1,CountingGcm.initializations)
            } else {
              substituteSameBytes(path)
              rejects { permit.consume(store,output,f.valid,header) }
              assertEquals(0,CountingGcm.initializations)
            }
          }
          assertArrayEquals(pending,Files.readAllBytes(path))
        }
      }
    }
  }

  private fun substituteSameBytes(path: Path) {
    val before=DomainInventory.stat(path); val bytes=Files.readAllBytes(path)
    val replacement=Files.createTempFile(path.parent,"substitution-","")
    Files.write(replacement,bytes); Files.move(replacement,path,ATOMIC_MOVE,REPLACE_EXISTING)
    assertNotEquals(before.key,DomainInventory.stat(path).key)
    assertArrayEquals(bytes,Files.readAllBytes(path))
  }
  private fun rejects(action: () -> Unit) { try { action(); fail("substituted pending ledger granted authority") } catch (_: SecurityException) {} catch (_: IllegalStateException) {} catch (_: IllegalArgumentException) {} }

  private class PendingFixture(primary: Boolean): AutoCloseable {
    private val p=if(primary) PrimaryUsageFixture() else null
    private val v=if(primary) null else VideoFixture()
    val master=p?.master ?: v!!.master
    val identity=p?.identity ?: v!!.identity
    val context=MediaContext(1,ByteArray(16) { 5 },1)
    val valid=p?.valid ?: v!!.valid
    val usage=p?.usage ?: v!!.usage
    val attempt=p?.reserve(context) ?: v!!.reserve(listOf(context))
    fun store(faults: LedgerFaults=LedgerFaults.NONE)=if(p!=null) MediaUsageStore.primaryTransfer(p.dir.toFile(),identity,p.operation,faults) else MediaUsageStore.hiddenMedia(v!!.dir.toFile(),identity,v.operation,faults)
    override fun close() { p?.close(); v?.close() }
  }
}
