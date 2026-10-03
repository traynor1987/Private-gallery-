package uk.co.traynor.privategallery.core.domain

import java.nio.ByteBuffer
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.PrimaryScope
import uk.co.traynor.privategallery.core.security.PrimarySessionAuthority

class PrimaryUsageTest {
  @Test fun retainedPrimary58OutputCannotRetryRegistrationFailure() {
    PrimaryUsageFixture().use { f ->
      val ctx=MediaContext(1,ByteArray(16) { 5 },1); val attempt=f.reserve(ctx); var registrations=0
      val store=MediaUsageStore.primaryTransfer(f.dir.toFile(),f.identity,f.operation,LedgerFaults { e ->
        if(e.phase==LedgerPhase.REGISTRATION && e.point==LedgerFaultPoint.DURING_WRITE && ++registrations==1) throw java.io.IOException("registration stop")
      })
      store.openOutput(f.master,ctx,attempt,f.valid).use { output -> CountingGcm().use {
        try { F1ChargedRecord.sealOwned(f.master,ctx,byteArrayOf(8),store,attempt,output,f.valid); fail("registration fault not reached") } catch (_: java.io.IOException) {}
        val before=Files.list(f.usage).use { paths -> paths.toList().associateWith(Files::readAllBytes) }
        rejects { F1ChargedRecord.sealOwned(f.master,ctx,byteArrayOf(8),store,attempt,output,f.valid) }
        assertEquals(1,registrations); assertEquals(0,CountingGcm.initializations)
        assertEquals(before.keys,Files.list(f.usage).use { it.toList().toSet() }); before.forEach { (path,bytes) -> assertArrayEquals(bytes,Files.readAllBytes(path)) }
      } }
    }
  }

  @Test fun primaryFreshOwnerAndWholeRecordKeepFrozen58PhysicalCompletion() {
    PrimaryUsageFixture().use { f ->
      val ctx = MediaContext(1,ByteArray(16) { 5 },1)
      val attempt = f.reserve(ctx)
      val bytes = F1ChargedRecord.seal(f.master,ctx,byteArrayOf(8),f.store,attempt,f.valid)
      val id = digest(bytes.copyOfRange(12,104)).hex()
      val ledger = Files.readAllBytes(f.usage.resolve(id))
      assertEquals(58,ledger.size)
      val b = ByteBuffer.wrap(ledger)
      assertEquals(1,b.getShort(0).toInt()); assertEquals(1L,b.getLong(2)); assertEquals(12L,b.getLong(10)); assertEquals(0L,b.getLong(18))
      assertArrayEquals(digest(bytes),ledger.copyOfRange(26,58))
      assertArrayEquals(bytes,Files.readAllBytes(f.root.resolve("attempts/"+attempt.hex()+"/files/0001-"+ctx.objectId.hex()+"-0000000000000001")))
      assertArrayEquals(byteArrayOf(8),F1ChargedRecord.open(f.master,ctx,bytes,digest(bytes),f.store,f.valid))
      assertEquals(1L,ByteBuffer.wrap(Files.readAllBytes(f.usage.resolve(id))).getLong(18))
    }
  }

  @Test fun pending58NeverFundsQueryAfterRestartOrFoundCiphertext() {
    PrimaryUsageFixture().use { f ->
      val ctx = MediaContext(1,ByteArray(16) { 5 },1)
      val bytes = F1Record.encrypt(f.master,F1Context(f.identity,1,ctx.objectId,1),byteArrayOf(8))
      val id = digest(bytes.copyOfRange(12,104)).hex()
      val pending = ByteBuffer.allocate(58).putShort(1).putLong(1).putLong(12).putLong(0).put(ByteArray(32)).array()
      Files.write(f.usage.resolve(id),pending)
      val restarted = MediaUsageStore.primaryTransfer(f.dir.toFile(),f.identity,f.operation)
      CountingGcm().use { rejects { F1ChargedRecord.open(f.master,ctx,bytes,digest(bytes),restarted,f.valid) }; assertEquals(0,CountingGcm.initializations) }
      assertArrayEquals(pending,Files.readAllBytes(f.usage.resolve(id)))
    }
  }

  @Test fun existing58QuarantineUsesExactInheritedBoundsAndQueries() {
    PrimaryUsageFixture().use { f ->
      val ctx = MediaContext(1,ByteArray(16) { 5 },1)
      val bytes = F1Record.encrypt(f.master,F1Context(f.identity,1,ctx.objectId,1),byteArrayOf(8))
      val id = digest(bytes.copyOfRange(12,104)).hex()
      Files.write(f.usage.resolve(id),ByteBuffer.allocate(58).putShort(1).putLong(1).putLong(12).putLong(0).put(digest(bytes)).array())
      listOf(0,1,29,58).forEachIndexed { i,size -> Files.write(f.usage.resolve("q"+(i+1).toString(16).padStart(32,'0')),ByteArray(size)) }
      assertArrayEquals(byteArrayOf(8),F1ChargedRecord.open(f.master,ctx,bytes,digest(bytes),f.store,f.valid))
      assertEquals(1L,ByteBuffer.wrap(Files.readAllBytes(f.usage.resolve(id))).getLong(18))
      Files.write(f.usage.resolve("q"+"a".repeat(32)),ByteArray(59))
      rejects { F1ChargedRecord.open(f.master,ctx,bytes,digest(bytes),f.store,f.valid) }
    }
  }

  @Test fun freshRegistrationFailuresNeverReachGcmOrCompleteAHash() {
    val points = LedgerFaultPoint.entries.filter { it !in listOf(LedgerFaultPoint.BEFORE_REPLACE,LedgerFaultPoint.AFTER_REPLACE) }
    points.forEach { point -> PrimaryUsageFixture().use { f ->
      val ctx = MediaContext(1,ByteArray(16) { 5 },1)
      val attempt = f.reserve(ctx)
      val store = MediaUsageStore.primaryTransfer(f.dir.toFile(),f.identity,f.operation,LedgerFaults { e -> if (e.phase == LedgerPhase.REGISTRATION && e.point == point) throw java.io.IOException("registration stop") })
      CountingGcm().use {
        rejects { store.openOutput(f.master,ctx,attempt,f.valid).use { F1ChargedRecord.sealOwned(f.master,ctx,byteArrayOf(8),store,attempt,it,f.valid) } }
        assertEquals("$point",1,CountingGcm.initializations)
      }
      Files.list(f.usage).use { entries -> entries.filter { it.fileName.toString().matches(Regex("[0-9a-f]{64}")) }.forEach { path ->
        val bytes = Files.readAllBytes(path)
        if (bytes.size == 58 && ByteBuffer.wrap(bytes).getLong(10) == 12L) assertTrue(bytes.copyOfRange(26,58).all { it == 0.toByte() })
      } }
    } }
  }

  @Test fun producerSyncAndReopenFailuresNeverCompletePending58() {
    val points = listOf(LedgerFaultPoint.BEFORE_FILE_SYNC,LedgerFaultPoint.AFTER_FILE_SYNC,LedgerFaultPoint.BEFORE_PARENT_SYNC,LedgerFaultPoint.AFTER_PARENT_SYNC,LedgerFaultPoint.BEFORE_CANONICAL_REOPEN,LedgerFaultPoint.AFTER_CANONICAL_REOPEN)
    points.forEach { point -> PrimaryUsageFixture().use { f ->
      val ctx = MediaContext(1,ByteArray(16) { 5 },1)
      val attempt = f.reserve(ctx)
      val store = MediaUsageStore.primaryTransfer(f.dir.toFile(),f.identity,f.operation,LedgerFaults { e -> if (e.phase == LedgerPhase.OUTPUT && e.point == point) throw java.io.IOException("producer stop") })
      rejects { store.openOutput(f.master,ctx,attempt,f.valid).use { F1ChargedRecord.sealOwned(f.master,ctx,byteArrayOf(8),store,attempt,it,f.valid) } }
      val pending = Files.list(f.usage).use { it.filter { p -> p.fileName.toString().matches(Regex("[0-9a-f]{64}")) }.toList() }.map { Files.readAllBytes(it) }.single { ByteBuffer.wrap(it).getLong(10) == 12L }
      assertTrue("$point",pending.copyOfRange(26,58).all { it == 0.toByte() })
      assertEquals(0L,ByteBuffer.wrap(pending).getLong(18))
    } }
  }

  @Test fun freshBudgetCannotBeMintedForAnArbitraryOwnerOrInconsistentCost() {
    PrimaryUsageFixture().use { f ->
      val ctx = MediaContext(1,ByteArray(16) { 5 },1)
      val h = F1Record.wholeHeader(F1Context(f.identity,1,ctx.objectId,1),ByteArray(32) { 8 },ByteArray(12) { 9 },1)
      rejects { f.store.reserveEncryption(listOf(h),listOf(1L),listOf(11L),ByteArray(16) { 3 },Any(),f.valid) }
      assertEquals(0L,Files.list(f.usage).use { it.count() })
    }
  }

  @Test fun changedOriginalOutputAfterProducerReopenCannotCompleteItsLedger() {
    PrimaryUsageFixture().use { f ->
      val ctx=MediaContext(1,ByteArray(16) { 5 },1)
      val attempt=f.reserve(ctx)
      val path=f.root.resolve("attempts/"+attempt.hex()+"/files/0001-"+ctx.objectId.hex()+"-0000000000000001")
      val store=MediaUsageStore.primaryTransfer(f.dir.toFile(),f.identity,f.operation,LedgerFaults { e ->
        if(e.phase==LedgerPhase.COMPLETION && e.point==LedgerFaultPoint.BEFORE_REPLACE) { val b=Files.readAllBytes(path); b[b.lastIndex]=(b.last().toInt() xor 1).toByte(); Files.write(path,b) }
      })
      rejects { store.openOutput(f.master,ctx,attempt,f.valid).use { F1ChargedRecord.sealOwned(f.master,ctx,byteArrayOf(8),store,attempt,it,f.valid) } }
      val b=Files.list(f.usage).use { it.filter { p -> p.fileName.toString().matches(Regex("[0-9a-f]{64}")) }.toList() }.map { Files.readAllBytes(it) }.single { it.size==58 && ByteBuffer.wrap(it).getLong(10)==12L }
      assertTrue(b.copyOfRange(26,58).all { it==0.toByte() })
    }
  }

  @Test fun everyCompletionInterruptionRecognizesInstalledWinnerWithoutReconstructingFinalizer() {
    LedgerFaultPoint.entries.forEach { point -> PrimaryUsageFixture().use { f ->
      val ctx=MediaContext(1,ByteArray(16) { 5 },1); val attempt=f.reserve(ctx)
      val store=MediaUsageStore.primaryTransfer(f.dir.toFile(),f.identity,f.operation,LedgerFaults { e ->
        if(e.phase==LedgerPhase.COMPLETION && e.point==point) throw java.io.IOException("completion interruption")
      })
      rejects { store.openOutput(f.master,ctx,attempt,f.valid).use { F1ChargedRecord.sealOwned(f.master,ctx,byteArrayOf(8),store,attempt,it,f.valid) } }
      val path=f.root.resolve("attempts/"+attempt.hex()+"/files/0001-"+ctx.objectId.hex()+"-0000000000000001")
      val ciphertext=Files.readAllBytes(path); val id=digest(ciphertext.copyOfRange(12,104)).hex()
      val canonical=f.usage.resolve(id); val ledger=Files.readAllBytes(canonical)
      assertEquals(58,ledger.size); assertEquals(0L,ByteBuffer.wrap(ledger).getLong(18))
      val completed=ledger.copyOfRange(26,58).any { it != 0.toByte() }
      val restarted=MediaUsageStore.primaryTransfer(f.dir.toFile(),f.identity,f.operation)
      CountingGcm().use {
        if(completed) {
          assertArrayEquals(digest(ciphertext),ledger.copyOfRange(26,58))
          assertArrayEquals(byteArrayOf(8),F1ChargedRecord.open(f.master,ctx,ciphertext,digest(ciphertext),restarted,f.valid))
          assertEquals(1,CountingGcm.initializations)
          assertEquals(1L,ByteBuffer.wrap(Files.readAllBytes(canonical)).getLong(18))
        } else {
          rejects { F1ChargedRecord.open(f.master,ctx,ciphertext,digest(ciphertext),restarted,f.valid) }
          assertEquals(0,CountingGcm.initializations)
          assertArrayEquals(ledger,Files.readAllBytes(canonical))
        }
      }
      if(point==LedgerFaultPoint.DURING_WRITE) {
        val stages=Files.list(f.usage).use { it.filter { p -> p.fileName.toString().startsWith("q") }.toList() }
        assertEquals(29L,Files.size(stages.single()))
      }
    } }
  }

  private fun rejects(action: () -> Unit) { try { action(); fail("accepted pending Primary accounting") } catch (_: SecurityException) {} catch (_: IllegalStateException) {} catch (_: IllegalArgumentException) {} catch (_: java.io.IOException) {} }
}

internal class PrimaryUsageFixture : AutoCloseable {
  val dir = Files.createTempDirectory("phase3-primary-usage-")
  val root = dir.resolve("vault/transfer-v1")
  val usage = root.resolve("usage")
  val master = ByteArray(32) { (it+11).toByte() }
  val identity = DomainIdentity(ByteArray(16) { 11 },ByteArray(16) { 12 })
  val authority = PrimarySessionAuthority { 0L }
  val operation: uk.co.traynor.privategallery.core.security.PrimaryOperation
  val valid: () -> Unit
  val store: MediaUsageStore
  init {
    listOf("usage","attempts","evidence","gens").forEach { Files.createDirectories(root.resolve(it)) }
    authority.open(master.copyOf())
    operation = authority.operationOrNull(setOf(PrimaryScope.READ,PrimaryScope.WRITE))!!
    valid = operation::checkValid
    store = MediaUsageStore.primaryTransfer(dir.toFile(),identity,operation)
  }
  fun reserve(context: MediaContext): ByteArray {
    val fresh = store.reserveAttempt(1,valid)
    val source = ByteBuffer.allocate(56).put(ByteArray(16) { 13 }).put(ByteArray(16) { 14 }).put(ByteArray(16) { 15 }).putLong(1).array()
    val owner = MediaAttemptOwner.parse(ByteBuffer.allocate(137).putShort(2).put(digest(fresh.reservation.encode())).putShort(1).put(ByteArray(16) { 4 }).put(1).put(source).putShort(1).put(context.encode()).array())
    store.sealOwner(master,fresh,owner,valid)
    return fresh.attemptId
  }
  override fun close() { operation.close(); authority.revoke(); dir.toFile().deleteRecursively() }
}
