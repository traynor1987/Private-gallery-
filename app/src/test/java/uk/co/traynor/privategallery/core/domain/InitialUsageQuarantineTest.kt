package uk.co.traynor.privategallery.core.domain

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class InitialUsageQuarantineTest {
  @Test fun everyLaterInitialProofRegistrationInterruptionLeavesOldSelectedProofUsable() {
    LedgerFaultPoint.entries.filter { it !in listOf(LedgerFaultPoint.BEFORE_REPLACE,LedgerFaultPoint.AFTER_REPLACE) }.forEach { point -> ProofFixture().use { f ->
      val fresh=f.store.reserveCredentialAttempt(1,f.video.valid); val body=f.prepare(fresh.token)
      f.store.sealProof(f.video.master,fresh,body,f.video.valid)
      val selected=Files.readAllBytes(f.video.root.resolve("selected")); var failedKey: String? = null
      val failed=MediaUsageStore.hiddenProof(f.video.dir.toFile(),f.video.identity,f.video.operation,LedgerFaults { e ->
        if(e.phase==LedgerPhase.REGISTRATION && e.point==point) { failedKey=e.keyId; throw IOException("later registration interruption") }
      })
      val later=failed.reserveCredentialAttempt(1,f.video.valid); val laterBody=f.prepare(later.token)
      Files.write(f.video.root.resolve("selected"),selected)
      CountingGcm().use {
        try { failed.sealProof(f.video.master,later,laterBody,f.video.valid); fail("interruption not reached") } catch (_: IOException) {}
        assertEquals("$point",0,CountingGcm.initializations)
        rejects { failed.sealProof(f.video.master,later,laterBody,f.video.valid) }
        assertEquals("$point",0,CountingGcm.initializations)
      }
      val path=f.proofUsage.resolve(failedKey!!)
      val before=if(Files.exists(path)) Files.readAllBytes(path) else null
      if(point==LedgerFaultPoint.AFTER_CREATE) assertEquals(0,before!!.size)
      if(point==LedgerFaultPoint.DURING_WRITE) assertEquals(41,before!!.size)
      val restart=MediaUsageStore.hiddenProof(f.video.dir.toFile(),f.video.identity,f.video.operation)
      assertArrayEquals(body.encode(),restart.openProof(f.video.master,f.video.valid))
      if(before != null) assertArrayEquals(before,Files.readAllBytes(path)) else assertFalse(Files.exists(path))
    } }
  }

  @Test fun restrictedSelectedProofSurvivesUnrelatedShortProofAndCredentialKeys() {
    CounterFixture().use { f ->
      listOf(0,1,41,81).forEachIndexed { i,size -> Files.write(f.proofUsage.resolve((100+i).toString(16).padStart(64,'0')),ByteArray(size)) }
      listOf(0,1,29,57).forEachIndexed { i,size -> Files.write(f.credentialUsage.resolve((100+i).toString(16).padStart(64,'0')),ByteArray(size)) }
      CountingGcm().use {
        f.counters.chargeSelectedProof(f.valid)
        f.counters.chargeSelectedSlot(f.pinId,false,f.valid)
        assertEquals(0,CountingGcm.initializations)
      }
      assertEquals(1L,MediaUsage.parse(Files.readAllBytes(f.proofLedger)).chargedQueries)
      assertEquals(1L,ByteBuffer.wrap(Files.readAllBytes(f.slotLedger)).getLong(18))
      listOf(0,1,41,81).forEachIndexed { i,size -> assertEquals(size.toLong(),Files.size(f.proofUsage.resolve((100+i).toString(16).padStart(64,'0')))) }
    }
  }

  @Test fun mediaIntactKeySurvivesBoundedUnrelatedShortsWithoutAdoptionOrDiscard() {
    VideoFixture().use { f ->
      val ref=f.write(byteArrayOf(8))
      listOf(0,1,41,81).forEachIndexed { i,size -> Files.write(f.usage.resolve((100+i).toString(16).padStart(64,'0')),ByteArray(size)) }
      f.store.openPayload(ref,f.valid).use { F1Video.open(f.master,f.context,it,f.store,f.valid).close() }
      assertEquals(0,f.store.discardOwnedUninstalledStages(f.valid))
    }
  }

  @Test fun primaryIntactKeySurvivesBoundedUnrelatedShortsWithoutAdoptionOrDiscard() {
    PrimaryUsageFixture().use { f ->
      val ctx=MediaContext(1,ByteArray(16) { 5 },1)
      val bytes=F1Record.encrypt(f.master,F1Context(f.identity,1,ctx.objectId,1),byteArrayOf(8))
      Files.write(f.usage.resolve(digest(bytes.copyOfRange(12,104)).hex()),ByteBuffer.allocate(58).putShort(1).putLong(1).putLong(12).putLong(0).put(digest(bytes)).array())
      listOf(0,1,29,57).forEachIndexed { i,size -> Files.write(f.usage.resolve((100+i).toString(16).padStart(64,'0')),ByteArray(size)) }
      assertArrayEquals(byteArrayOf(8),F1ChargedRecord.open(f.master,ctx,bytes,digest(bytes),f.store,f.valid))
      assertEquals(0,f.store.discardOwnedUninstalledStages(f.valid))
    }
  }

  @Test fun requiredShortKeyNeverGrantsCryptoOrChangesItsBytes() {
    listOf(0,1,41,81).forEach { size -> VideoFixture().use { f ->
      val ctx=MediaContext(1,ByteArray(16) { 5 },1)
      val bytes=F1Record.encrypt(f.master,F1Context(f.identity,1,ctx.objectId,1),byteArrayOf(8))
      val path=f.usage.resolve(digest(bytes.copyOfRange(12,104)).hex()); val short=ByteArray(size)
      Files.write(path,short)
      CountingGcm().use { rejects { F1ChargedRecord.open(f.master,ctx,bytes,digest(bytes),f.store,f.valid) }; assertEquals(0,CountingGcm.initializations) }
      assertArrayEquals(short,Files.readAllBytes(path))
    } }
  }

  @Test fun previouslyCheckedShortReplacementAndGrowthCannotBecomeCredit() {
    listOf("replace","grow","remove").forEach { change -> CounterFixture().use { f ->
      val path=f.proofUsage.resolve("a".repeat(64)); Files.write(path,ByteArray(1))
      f.counters.checkedProjectionEntries(f.valid)
      when(change) {
        "replace" -> { Files.move(path,path.resolveSibling("saved")); Files.write(path,ByteArray(1)); Files.delete(path.resolveSibling("saved")) }
        "grow" -> Files.write(path,ByteArray(82))
        "remove" -> Files.delete(path)
      }
      val before=Files.readAllBytes(f.proofLedger)
      rejects { f.counters.chargeSelectedProof(f.valid) }
      assertArrayEquals(before,Files.readAllBytes(f.proofLedger))
    } }
  }

  @Test fun normalCredentialShortExceptionIsSchema2Only() {
    CounterFixture().use { f ->
      val bootstrapPath=f.root.resolve("descriptor/"+f.token.hex()+"/bootstrap")
      val schema2=Files.readAllBytes(bootstrapPath)
      Files.write(bootstrapPath,DomainEncoding.bootstrap(f.identity,1,true,List(3) { ByteArray(16) { (it+1).toByte() } }))
      val short=f.credentialUsage.resolve("a".repeat(64)); Files.write(short,ByteArray(29))
      val schema1=MediaUsageStore.normalCredentialCounters(f.dir.toFile(),f.identity,f.authority,f.attempt)
      val before=Files.readAllBytes(f.slotLedger)
      rejects { schema1.chargeSelectedSlot(f.pinId,false,f.valid) }
      assertArrayEquals(before,Files.readAllBytes(f.slotLedger))
      Files.write(bootstrapPath,schema2)
      val normal2=MediaUsageStore.normalCredentialCounters(f.dir.toFile(),f.identity,f.authority,f.attempt)
      normal2.chargeSelectedSlot(f.pinId,false,f.valid)
      assertEquals(1L,ByteBuffer.wrap(Files.readAllBytes(f.slotLedger)).getLong(18))
      assertEquals(29L,Files.size(short))
    }
  }

  @Test fun reservationReplacementAtActualFileSyncCannotMintFreshOwnerCapability() {
    VideoFixture().use { f ->
      val store=MediaUsageStore.hiddenMedia(f.dir.toFile(),f.identity,f.operation,LedgerFaults { e ->
        if(e.phase==LedgerPhase.RESERVATION && e.point==LedgerFaultPoint.AFTER_FILE_SYNC) {
          val path=f.root.resolve("transactions/media/attempts/"+e.keyId+"/reservation")
          val bytes=Files.readAllBytes(path); Files.move(path,path.resolveSibling("saved")); Files.write(path,bytes)
        }
      })
      CountingGcm().use { rejects { store.reserveAttempt(1,f.valid) }; assertEquals(0,CountingGcm.initializations) }
    }
  }

  @Test fun everyFreshReservationInterruptionGrantsNoCapabilityOrGcm() {
    LedgerFaultPoint.entries.filter { it !in listOf(LedgerFaultPoint.BEFORE_REPLACE,LedgerFaultPoint.AFTER_REPLACE) }.forEach { point ->
      VideoFixture().use { f ->
        val store=MediaUsageStore.hiddenMedia(f.dir.toFile(),f.identity,f.operation,LedgerFaults { e ->
          if(e.phase==LedgerPhase.RESERVATION && e.point==point) throw IOException("reservation interruption")
        })
        CountingGcm().use { rejects { store.reserveAttempt(1,f.valid) }; assertEquals("$point",0,CountingGcm.initializations) }
        assertEquals(0L,Files.list(f.usage).use { it.count() })
      }
    }
  }

  @Test fun shortKeyUnknownNamesTypesOversizeAndUnreadabilityAlwaysDenySelectedCharge() {
    listOf("uppercase","directory","link","oversize","unreadable").forEach { kind -> CounterFixture().use { f ->
      val path=f.proofUsage.resolve(if(kind=="uppercase") "A".repeat(64) else "a".repeat(64))
      when(kind) {
        "directory" -> Files.createDirectory(path)
        "link" -> Files.createSymbolicLink(path,f.proofLedger)
        "oversize" -> Files.write(path,ByteArray(83))
        "unreadable" -> { Files.write(path,ByteArray(1)); Files.setPosixFilePermissions(path,emptySet()) }
        else -> Files.write(path,ByteArray(1))
      }
      val before=Files.readAllBytes(f.proofLedger)
      CountingGcm().use { rejects { f.counters.chargeSelectedProof(f.valid) }; assertEquals(0,CountingGcm.initializations) }
      assertArrayEquals(before,Files.readAllBytes(f.proofLedger))
      assertTrue(Files.exists(path,java.nio.file.LinkOption.NOFOLLOW_LINKS))
    } }
  }

  @Test fun unrelatedShortsConsumeProjectionCapacityBeforeAnyNewStage() {
    CounterFixture().use { f ->
      val existing=f.counters.checkedProjectionEntries(f.valid)
      repeat(8192-existing) { Files.write(f.credentialUsage.resolve((100+it).toString(16).padStart(64,'0')),ByteArray(it%58)) }
      assertEquals(8192,f.counters.checkedProjectionEntries(f.valid))
      val before=Files.readAllBytes(f.slotLedger)
      rejects { f.counters.chargeSelectedSlot(f.pinId,false,f.valid) }
      assertArrayEquals(before,Files.readAllBytes(f.slotLedger))
      assertEquals(0L,Files.list(f.credentialUsage).use { it.filter { p -> p.fileName.toString().startsWith("q") }.count() })
    }
  }

  private fun rejects(action: () -> Unit) { try { action(); fail("short canonical granted service") } catch (_: SecurityException) {} catch (_: IllegalStateException) {} catch (_: IllegalArgumentException) {} catch (_: IOException) {} }
}
