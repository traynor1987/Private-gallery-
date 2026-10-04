package uk.co.traynor.privategallery.core.domain

import java.nio.ByteBuffer
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class MediaUsageStoreTest {
  @Test fun unknownStageNameFailsAccountingAdmissionWithoutDeletion() {
    VideoFixture().use { f ->
      val ref = f.write(byteArrayOf(8))
      val bad = f.usage.resolve("unexpected")
      Files.write(bad, byteArrayOf())
      rejects { f.store.openPayload(ref, f.valid).use { F1Video.open(f.master, f.context, it, f.store, f.valid) } }
      assertTrue(Files.isRegularFile(bad))
    }
  }

  @Test fun malformedStageTypesAndSizeFailClosed() {
    listOf("oversize", "directory", "link", "unreadable").forEach { kind ->
      VideoFixture().use { f ->
        val ref = f.write(byteArrayOf(8))
        val bad = f.usage.resolve("q" + "a".repeat(32))
        when (kind) {
          "oversize" -> Files.write(bad, ByteArray(83))
          "directory" -> Files.createDirectory(bad)
          "link" -> Files.createSymbolicLink(bad, f.usage.resolve(digest(Files.readAllBytes(f.payload()).copyOfRange(12,104)).hex()))
          "unreadable" -> { Files.write(bad, byteArrayOf()); Files.setPosixFilePermissions(bad, emptySet()) }
        }
        rejects { f.store.openPayload(ref, f.valid).use { F1Video.open(f.master, f.context, it, f.store, f.valid) } }
        assertTrue(Files.exists(bad, java.nio.file.LinkOption.NOFOLLOW_LINKS))
      }
    }
  }

  @Test fun sixteenQuarantinedStagesDenyNewUpdateWithoutGc() {
    VideoFixture().use { f ->
      val ref = f.write(byteArrayOf(8))
      repeat(16) { Files.write(f.usage.resolve("q" + (it + 1).toString(16).padStart(32,'0')), ByteArray(it % 3)) }
      val before = Files.list(f.usage).use { it.toList() }.associate { it.fileName.toString() to Files.readAllBytes(it).toList() }
      rejects { f.store.openPayload(ref, f.valid).use { F1Video.open(f.master, f.context, it, f.store, f.valid) } }
      assertEquals(before, Files.list(f.usage).use { it.toList() }.associate { it.fileName.toString() to Files.readAllBytes(it).toList() })
    }
  }

  @Test fun validBoundedStagesNeverReplayOrReconstructMissingCanonical() {
    VideoFixture().use { f ->
      val ref = f.write(byteArrayOf(8))
      val bytes = Files.readAllBytes(f.payload())
      val canonical = f.usage.resolve(digest(bytes.copyOfRange(12,104)).hex())
      val state = Files.readAllBytes(canonical)
      listOf(0,1,41,82).forEachIndexed { i, size -> Files.write(f.usage.resolve("q"+(i+1).toString(16).padStart(32,'0')),state.copyOf(size)) }
      f.store.openPayload(ref, f.valid).use { F1Video.open(f.master,f.context,it,f.store,f.valid).close() }
      assertEquals(1L, ByteBuffer.wrap(Files.readAllBytes(canonical)).getLong(18))
      Files.delete(canonical)
      rejects { f.store.openPayload(ref,f.valid).use { F1Video.open(f.master,f.context,it,f.store,f.valid) } }
      assertFalse(Files.exists(canonical))
      assertEquals(4, Files.list(f.usage).use { it.filter { p -> p.fileName.toString().startsWith("q") }.count() }.toInt())
    }
  }

  @Test fun detectablyInconsistentEncryptionCostDeniesCrypto() {
    VideoFixture().use { f ->
      val ref = f.write(byteArrayOf(8))
      val bytes = Files.readAllBytes(f.payload())
      val canonical = f.usage.resolve(digest(bytes.copyOfRange(12,104)).hex())
      val state = Files.readAllBytes(canonical)
      ByteBuffer.wrap(state).putLong(2, 0).putLong(10, 0)
      Files.write(canonical,state)
      rejects { f.store.openPayload(ref,f.valid).use { F1Video.open(f.master,f.context,it,f.store,f.valid) } }
    }
  }

  @Test fun foreignMasterDeniedBeforeAnyLedgerUpdate() {
    VideoFixture().use { f ->
      val ref = f.write(byteArrayOf(8))
      val before = Files.list(f.usage).use { it.toList() }.associate { it.fileName.toString() to Files.readAllBytes(it).toList() }
      rejects { f.store.openPayload(ref,f.valid).use { F1Video.open(ByteArray(32) { 99 },f.context,it,f.store) {} } }
      assertEquals(before, Files.list(f.usage).use { it.toList() }.associate { it.fileName.toString() to Files.readAllBytes(it).toList() })
    }
  }

  @Test fun fabricatedInProcessPermitCannotGrantAnAttempt() {
    VideoFixture().use { f ->
      val ref = f.write(byteArrayOf(8))
      val header = Files.readAllBytes(f.payload()).copyOfRange(0,156)
      val owner = Any()
      val key = digest(header.copyOfRange(12,104)).hex()
      val forged = MediaUsageStore.Permit.create(f.store,owner,f.valid,mapOf(key to (1L to 1L)),ref.hash)
      rejects { forged.consume(f.store,owner,f.valid,header) }
      forged.close()
    }
  }

  @Test fun restartedOrFabricatedFreshReservationCannotResumeOwnerEncryption() {
    VideoFixture().use { f ->
      val fresh = f.store.reserveAttempt(1,f.valid)
      val restarted = MediaUsageStore.hiddenMedia(f.dir.toFile(),f.identity,f.operation)
      val forged = MediaUsageStore.ReservedAttempt.create(restarted,fresh.attemptId,fresh.reservation)
      val owner = MediaAttemptOwner.parse(ByteBuffer.allocate(81).putShort(2).put(digest(fresh.reservation.encode())).putShort(3).put(ByteArray(16) { 4 }).put(0).putShort(1).put(f.context.encode()).array())
      rejects { restarted.sealOwner(f.master,forged,owner,f.valid) }
      assertFalse(Files.exists(f.root.resolve("transactions/media/attempts/"+fresh.attemptId.hex()+"/owner")))
    }
  }

  @Test fun originalAbaAndWrongScopeNeverReviveWrites() {
    VideoFixture().use { f ->
      val old = f.store
      val attempt = f.authority.beginAuthentication()
      check(f.authority.completeAuthentication(attempt,f.master.copyOf()))
      rejects { old.reserveAttempt(1) {} }
      val readOnly = f.authority.operationOrNull(setOf(SecondaryScope.READ))!!
      val store = MediaUsageStore.hiddenMedia(f.dir.toFile(),f.identity,readOnly)
      rejects { store.reserveAttempt(1) {} }
      readOnly.close()
    }
  }

  @Test fun internalOutputFactoriesCannotBypassFreshOwnerOrChargedManifestAdmission() {
    VideoFixture().use { f ->
      val fresh=f.store.reserveAttempt(1,f.valid)
      rejects { OwnedMediaOutput.openOwner(f.store,MediaContext(9,fresh.attemptId,1),fresh.attemptId,f.valid).close() }
      val attempt=f.reserve(listOf(f.context))
      rejects { OwnedMediaOutput.openListed(f.store,f.context,attempt,f.valid).close() }
    }
  }

  @Test fun maximumVideoSweepsPreflightBothKeysAndLoseUnusedAllowances() {
    VideoFixture().use { f ->
      val ref=f.write(byteArrayOf(8))
      val h=Files.readAllBytes(f.payload()).copyOfRange(0,156)
      val plan=F1Video.plan(F1Video.MAX_LENGTH)
      ByteBuffer.wrap(h).putLong(132,F1Video.MAX_LENGTH).putInt(144,plan.chunks)
      val chunks=h.copyOf().also { ByteBuffer.wrap(it).putShort(14,11) }
      val ids=listOf(h,chunks).map { digest(it.copyOfRange(12,104)).hex() }
      fun seed(chunkQueries: Long, sequence: Long=1) {
        listOf(1L to 11L,8192L to plan.chunkBlocks).forEachIndexed { i,cost -> Files.write(f.usage.resolve(ids[i]),ByteBuffer.allocate(82).putShort(2).putLong(cost.first).putLong(cost.second).putLong(if(i==0) 0 else chunkQueries).putLong(sequence).put(ref.hash).put(ByteArray(16) { 3 }).array()) }
      }
      seed((1L shl 20)-8192+1)
      val before=ids.map { Files.readAllBytes(f.usage.resolve(it)).toList() }
      CountingGcm().use { rejects { f.store.precharge(listOf(h,chunks),listOf(1L,8192L),ref.hash,Any(),f.valid) }; assertEquals(0,CountingGcm.initializations) }
      assertEquals(before,ids.map { Files.readAllBytes(f.usage.resolve(it)).toList() })
      seed(0)
      repeat(128) { f.store.precharge(listOf(h,chunks),listOf(1L,8192L),ref.hash,Any(),f.valid).close() }
      assertEquals(128L,MediaUsage.parse(Files.readAllBytes(f.usage.resolve(ids[0]))).chargedQueries)
      assertEquals(1L shl 20,MediaUsage.parse(Files.readAllBytes(f.usage.resolve(ids[1]))).chargedQueries)
      rejects { f.store.precharge(listOf(h,chunks),listOf(1L,8192L),ref.hash,Any(),f.valid) }
      assertEquals(128L,MediaUsage.parse(Files.readAllBytes(f.usage.resolve(ids[0]))).chargedQueries)
      seed(0,Long.MAX_VALUE)
      rejects { f.store.precharge(listOf(h,chunks),listOf(1L,8192L),ref.hash,Any(),f.valid) }
      assertEquals(0L,MediaUsage.parse(Files.readAllBytes(f.usage.resolve(ids[0]))).chargedQueries)
    }
  }

  @Test fun freshAttemptsEnforceSixteenLimitBeforeCreatingAnything() {
    VideoFixture().use { f ->
      val attempts=f.root.resolve("transactions/media/attempts")
      repeat(16) { i -> val dir=attempts.resolve((i+1).toString(16).padStart(32,'0')); Files.createDirectory(dir); Files.createDirectory(dir.resolve("files")); Files.write(dir.resolve("reservation"),ByteBuffer.allocate(26).putShort(2).put(ByteArray(16) { (i+1).toByte() }).putLong(1).array()) }
      rejects { f.store.reserveAttempt(1,f.valid) }
      assertEquals(16L,Files.list(attempts).use { it.count() })
    }
  }

  @Test fun freshAttemptsReserveEntireThreeEntryFootprintBeforeCreatingAnything() {
    VideoFixture().use { f ->
      val count=Files.walk(f.root).use { it.count() }.toInt()
      repeat(8190-count) { Files.write(f.usage.resolve((it+1).toString(16).padStart(64,'0')),ByteArray(82)) }
      rejects { f.store.reserveAttempt(1,f.valid) }
      assertEquals(8190L,Files.walk(f.root).use { it.count() })
    }
  }

  @Test fun equalBytesCannotReplaceOriginalFreshReservationOwnership() {
    VideoFixture().use { f ->
      val fresh=f.store.reserveAttempt(1,f.valid)
      val path=f.root.resolve("transactions/media/attempts/"+fresh.attemptId.hex()+"/reservation")
      val bytes=Files.readAllBytes(path); Files.move(path,path.resolveSibling("saved")); Files.write(path,bytes)
      val owner=MediaAttemptOwner.parse(ByteBuffer.allocate(81).putShort(2).put(digest(bytes)).putShort(3).put(ByteArray(16) { 4 }).put(0).putShort(1).put(f.context.encode()).array())
      rejects { f.store.sealOwner(f.master,fresh,owner,f.valid) }
    }
  }

  @Test fun aMediaOperationCannotManufactureARestrictedCounterService() {
    VideoFixture().use { f -> rejects { MediaUsageStore.CounterService.create(f.store,null,true) } }
  }

  @Test fun schema2ImageAndPreviewBudgetsRejectOversizeWithoutAllocatingBodies() {
    listOf(2 to (48L*1024*1024+1),3 to (58L+2L*1024*1024+1)).forEach { (purpose,length) -> VideoFixture().use { f ->
      val ctx=MediaContext(purpose,ByteArray(16) { 5 },1)
      val attempt=f.reserve(listOf(ctx))
      val h=F1Record.wholeHeader(F1Context(f.identity,purpose,ctx.objectId,1),ByteArray(32) { 8 },ByteArray(12) { 9 },length)
      f.store.openOutput(f.master,ctx,attempt,f.valid).use { out -> rejects { f.store.reserveEncryption(listOf(h),listOf(1L),listOf(11+(length+15)/16),attempt,out,f.valid).close() } }
    } }
  }

  @Test fun encryptionGrantCannotBeClaimedByAnotherConcreteOwnedOutput() {
    VideoFixture().use { f ->
      val a=MediaContext(1,ByteArray(16) { 5 },1); val b=MediaContext(1,ByteArray(16) { 6 },1)
      val attempt=f.reserve(listOf(a,b))
      val h=F1Record.wholeHeader(F1Context(f.identity,1,a.objectId,1),ByteArray(32) { 8 },ByteArray(12) { 9 },1)
      f.store.openOutput(f.master,a,attempt,f.valid).use { outputA ->
        f.store.openOutput(f.master,b,attempt,f.valid).use { outputB ->
          f.store.reserveEncryption(listOf(h),listOf(1L),listOf(12L),attempt,outputA,f.valid).use { grant ->
            rejects { outputB.claim(f.store,b,attempt,grant,173,h) }
          }
        }
      }
    }
  }

  @Test fun freshOwnerRejectsInitiallyForeignMasterBeforeAnyCallbackCanReplaceIt() {
    VideoFixture().use { f ->
      val fresh=f.store.reserveAttempt(1,f.valid)
      val owner=MediaAttemptOwner.parse(ByteBuffer.allocate(81).putShort(2).put(digest(fresh.reservation.encode())).putShort(3).put(ByteArray(16) { 4 }).put(0).putShort(1).put(f.context.encode()).array())
      val supplied=ByteArray(32) { 99 }
      val callback: () -> Unit = { f.operation.checkValid(); f.master.copyInto(supplied) }
      CountingGcm().use { rejects { f.store.sealOwner(supplied,fresh,owner,callback) }; assertEquals(0,CountingGcm.initializations) }
      assertEquals(0L,Files.list(f.usage).use { it.count() })
    }
  }

  @Test fun authenticatedOwnerAllowsExactListedContextsWithDifferentObjectGenerations() {
    VideoFixture().use { f ->
      val index=MediaContext(1,ByteArray(16) { 5 },2); val receipt=MediaContext(7,ByteArray(16) { 6 },1)
      val fresh=f.store.reserveAttempt(2,f.valid)
      val source=ByteBuffer.allocate(56).put(ByteArray(16) { 7 }).put(ByteArray(16) { 8 }).put(ByteArray(16) { 9 }).putLong(1).array()
      val owner=MediaAttemptOwner.parse(ByteBuffer.allocate(163).putShort(2).put(digest(fresh.reservation.encode())).putShort(1).put(ByteArray(16) { 4 }).put(1).put(source).putShort(2).put(index.encode()).put(receipt.encode()).array())
      f.store.sealOwner(f.master,fresh,owner,f.valid)
      listOf(index,receipt).forEach { context ->
        val bytes=F1ChargedRecord.seal(f.master,context,byteArrayOf(8),f.store,fresh.attemptId,f.valid)
        assertArrayEquals(byteArrayOf(8),F1ChargedRecord.open(f.master,context,bytes,digest(bytes),f.store,f.valid))
      }
      val unlisted=MediaContext(7,receipt.objectId,2)
      rejects { f.store.openOutput(f.master,unlisted,fresh.attemptId,f.valid).close() }
      assertFalse(Files.exists(f.root.resolve("transactions/media/attempts/"+fresh.attemptId.hex()+"/files/0007-"+receipt.objectId.hex()+"-0000000000000002")))
    }
  }

  private fun rejects(action: () -> Unit) {
    try { action(); fail("accepted invalid accounting") } catch (_: SecurityException) {} catch (_: IllegalStateException) {} catch (_: IllegalArgumentException) {}
  }
}
