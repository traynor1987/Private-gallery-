package uk.co.traynor.privategallery.core.domain

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class MediaUsageFaultTest {
  @Test fun everyUpdateInterruptionGrantsZeroGcmAndPreservesVisibleChargeOnRestart() {
    LedgerFaultPoint.entries.forEach { point ->
      VideoFixture().use { f ->
        val ref = f.write(byteArrayOf(8))
        val header = Files.readAllBytes(f.payload()).copyOfRange(0,156)
        val canonical = f.usage.resolve(digest(header.copyOfRange(12,104)).hex())
        val store = MediaUsageStore.hiddenMedia(f.dir.toFile(),f.identity,f.operation,LedgerFaults { event -> if (event.point == point) throw IOException("synthetic interruption") })
        CountingGcm().use {
          rejects { store.openPayload(ref,f.valid).use { F1Video.open(f.master,f.context,it,store,f.valid) } }
          assertEquals("$point", 0, CountingGcm.initializations)
          assertEquals("$point", 0, CountingGcm.attempts)
        }
        val visible = MediaUsage.parse(Files.readAllBytes(canonical)).chargedQueries
        assertTrue(visible == 0L || visible == 1L)
        val restarted = MediaUsageStore.hiddenMedia(f.dir.toFile(),f.identity,f.operation)
        restarted.openPayload(ref,f.valid).use { F1Video.open(f.master,f.context,it,restarted,f.valid).close() }
        assertEquals(visible + 1, MediaUsage.parse(Files.readAllBytes(canonical)).chargedQueries)
      }
    }
  }

  @Test fun secondLedgerFailureSpendsFirstAndNeverIssuesPartialSweepLease() {
    VideoFixture().use { f ->
      val ref = f.write(byteArrayOf(8))
      val chunkKey = digest(Files.readAllBytes(f.payload()).copyOfRange(184,276)).hex()
      val store = MediaUsageStore.hiddenMedia(f.dir.toFile(),f.identity,f.operation,LedgerFaults { e -> if (e.keyId == chunkKey && e.point == LedgerFaultPoint.BEFORE_CREATE) throw IOException("second ledger failed") })
      CountingGcm().use {
        rejects { store.openPayload(ref,f.valid).use { F1Video.verify(f.master,f.context,it,1,digest(byteArrayOf(8)),store,f.valid) } }
        assertEquals(0, CountingGcm.initializations)
      }
      val states = Files.list(f.usage).use { it.toList() }.filter { !it.fileName.toString().startsWith("q") }.map { MediaUsage.parse(Files.readAllBytes(it)) }
      assertEquals(1L, states.single { it.ghashBlocks == 11L }.chargedQueries)
      assertEquals(0L, MediaUsage.parse(Files.readAllBytes(f.usage.resolve(chunkKey))).chargedQueries)
      val restarted = MediaUsageStore.hiddenMedia(f.dir.toFile(),f.identity,f.operation)
      restarted.openPayload(ref,f.valid).use { F1Video.verify(f.master,f.context,it,1,digest(byteArrayOf(8)),restarted,f.valid) }
      assertEquals(2L, MediaUsage.parse(Files.readAllBytes(f.usage.resolve(digest(Files.readAllBytes(f.payload()).copyOfRange(12,104)).hex()))).chargedQueries)
    }
  }

  @Test fun onlyOriginalUpdaterCanDiscardExactUninstalledStage() {
    VideoFixture().use { f ->
      val ref = f.write(byteArrayOf(8))
      val store = MediaUsageStore.hiddenMedia(f.dir.toFile(),f.identity,f.operation,LedgerFaults { e -> if (e.point == LedgerFaultPoint.DURING_WRITE) throw IOException("partial") })
      rejects { store.openPayload(ref,f.valid).use { F1Video.open(f.master,f.context,it,store,f.valid) } }
      val stage = Files.list(f.usage).use { it.filter { p -> p.fileName.toString().startsWith("q") }.toList() }.single()
      assertEquals(41L, Files.size(stage))
      val restarted = MediaUsageStore.hiddenMedia(f.dir.toFile(),f.identity,f.operation)
      assertEquals(0, restarted.discardOwnedUninstalledStages(f.valid))
      assertTrue(Files.exists(stage))
      assertEquals(1, store.discardOwnedUninstalledStages(f.valid))
      assertFalse(Files.exists(stage))
    }
  }

  @Test fun replacedOwnedStageNeverBecomesDisposableByNameOrEqualBytes() {
    VideoFixture().use { f ->
      val ref = f.write(byteArrayOf(8))
      val store = MediaUsageStore.hiddenMedia(f.dir.toFile(),f.identity,f.operation,LedgerFaults { e -> if (e.point == LedgerFaultPoint.AFTER_STAGE_REOPEN) throw IOException("stop") })
      rejects { store.openPayload(ref,f.valid).use { F1Video.open(f.master,f.context,it,store,f.valid) } }
      val stage = Files.list(f.usage).use { it.filter { p -> p.fileName.toString().startsWith("q") }.toList() }.single()
      val bytes = Files.readAllBytes(stage)
      Files.move(stage,stage.resolveSibling("saved"))
      Files.write(stage,bytes)
      rejects { store.discardOwnedUninstalledStages(f.valid) }
      assertArrayEquals(bytes,Files.readAllBytes(stage))
    }
  }

  @Test fun unusedSweepsAreSpentAndCannotTransferToRestartedStoreOrReader() {
    VideoFixture().use { f ->
      val ref = f.write(byteArrayOf(8))
      val bytes = Files.readAllBytes(f.payload())
      val headers = listOf(bytes.copyOfRange(0,156),bytes.copyOfRange(172,328))
      val owner = Any()
      val lease = f.store.precharge(headers,listOf(1L,1L),ref.hash,owner,f.valid)
      val restarted = MediaUsageStore.hiddenMedia(f.dir.toFile(),f.identity,f.operation)
      rejects { lease.consume(restarted,owner,f.valid,headers[0]) }
      rejects { lease.consume(f.store,Any(),f.valid,headers[0]) }
      lease.close()
      restarted.openPayload(ref,f.valid).use { F1Video.verify(f.master,f.context,it,1,digest(byteArrayOf(8)),restarted,f.valid) }
      headers.forEach { assertEquals(2L,MediaUsage.parse(Files.readAllBytes(f.usage.resolve(digest(it.copyOfRange(12,104)).hex()))).chargedQueries) }
    }
  }

  @Test fun negativePrechargeNeverRefundsAnAlreadySpentQuery() {
    VideoFixture().use { f ->
      val ref = f.write(byteArrayOf(8))
      val header = Files.readAllBytes(f.payload()).copyOfRange(0,156)
      f.store.precharge(listOf(header),listOf(1L),ref.hash,Any(),f.valid).close()
      rejects { f.store.precharge(listOf(header),listOf(-1L),ref.hash,Any(),f.valid) }
      assertEquals(1L,MediaUsage.parse(Files.readAllBytes(f.usage.resolve(digest(header.copyOfRange(12,104)).hex()))).chargedQueries)
    }
  }

  private fun rejects(action: () -> Unit) {
    try { action(); fail("accepted interrupted/foreign accounting") } catch (_: SecurityException) {} catch (_: IllegalStateException) {} catch (_: IllegalArgumentException) {} catch (_: IOException) {}
  }
}
