package uk.co.traynor.privategallery.core.domain

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import javax.crypto.Cipher
import org.junit.Assert.*
import org.junit.Test

// The first two tests and middle negatives are semantic reconstruction after workspace loss;
// retained original helper/known tests and fresh validation provide the execution evidence.
class F1VideoTest {
  @Test fun chargedTwoChunkVideoRoundTripsAndTracksSeparateActualKeys() {
    VideoFixture().use { f ->
      val plain=ByteArray(F1Video.CHUNK_SIZE+1) { (it%251).toByte() }
      val ref=f.write(plain)
      assertEquals(1049093L,ref.ciphertextLength)
      f.store.openPayload(ref,f.valid).use { source -> F1Video.open(f.master,f.context,source,f.store,f.valid).use { reader ->
        assertEquals(plain.size.toLong(),reader.plaintextLength)
        assertArrayEquals(plain.copyOfRange(0,F1Video.CHUNK_SIZE),reader.readChunk(0))
        assertArrayEquals(plain.copyOfRange(F1Video.CHUNK_SIZE,plain.size),reader.readChunk(1))
      } }
      val verified=f.store.openPayload(ref,f.valid).use { F1Video.verify(f.master,f.context,it,plain.size.toLong(),digest(plain),f.store,f.valid) }
      assertArrayEquals(digest(plain),verified.plaintextHash)
      assertArrayEquals(ref.hash,verified.ciphertextHash)
      val bytes=Files.readAllBytes(f.payload())
      val header=MediaUsage.parse(Files.readAllBytes(f.usage.resolve(digest(bytes.copyOfRange(12,104)).hex())))
      val chunks=MediaUsage.parse(Files.readAllBytes(f.usage.resolve(digest(bytes.copyOfRange(184,276)).hex())))
      assertEquals(1L,header.encryptionInvocations); assertEquals(11L,header.ghashBlocks); assertEquals(2L,header.chargedQueries)
      assertEquals(2L,chunks.encryptionInvocations); assertEquals(65559L,chunks.ghashBlocks); assertEquals(4L,chunks.chargedQueries)
      assertArrayEquals(ref.hash,header.ciphertextHash); assertArrayEquals(ref.hash,chunks.ciphertextHash)
    }
  }

  @Test fun wholeRecordSealPersistsExactOriginalOutputAndChargesVerification() {
    VideoFixture().use { f ->
      val ctx=MediaContext(1,ByteArray(16) { 5 },1)
      val attempt=f.reserve(listOf(ctx))
      val bytes=F1ChargedRecord.seal(f.master,ctx,byteArrayOf(8),f.store,attempt,f.valid)
      val path=f.root.resolve("transactions/media/attempts/"+attempt.hex()+"/files/0001-"+ctx.objectId.hex()+"-0000000000000001")
      assertArrayEquals(bytes,Files.readAllBytes(path))
      val ledger=f.usage.resolve(digest(bytes.copyOfRange(12,104)).hex())
      assertEquals(0L,MediaUsage.parse(Files.readAllBytes(ledger)).chargedQueries)
      assertArrayEquals(byteArrayOf(8),F1ChargedRecord.open(f.master,ctx,bytes,digest(bytes),f.store,f.valid))
      assertEquals(1L,MediaUsage.parse(Files.readAllBytes(ledger)).chargedQueries)
    }
  }

  @Test fun changingInputDigestCannotCompleteTheVideo() {
    VideoFixture().use { f ->
      val attempt = f.reserve(listOf(f.context))
      rejects { f.store.openOutput(f.master,f.context,attempt,f.valid).use { F1Video.write(f.master, f.context, ByteArrayInputStream(byteArrayOf(9)), 1, digest(byteArrayOf(8)), it, f.store, attempt, f.valid) } }
    }
  }

  @Test fun extraProviderEofCannotCompleteTheVideo() {
    VideoFixture().use { f ->
      val attempt = f.reserve(listOf(f.context))
      rejects { f.store.openOutput(f.master,f.context,attempt,f.valid).use { F1Video.write(f.master, f.context, ByteArrayInputStream(byteArrayOf(8, 9)), 1, digest(byteArrayOf(8)), it, f.store, attempt, f.valid) } }
    }
  }

  @Test fun independentPlaintextDigestIsRequiredForFullVerification() {
    VideoFixture().use { f ->
      val ref=f.write(byteArrayOf(8))
      rejects { f.store.openPayload(ref,f.valid).use { F1Video.verify(f.master,f.context,it,1,digest(byteArrayOf(9)),f.store,f.valid) } }
    }
  }

  @Test fun authenticatedWholeSubstitutionStillFailsPinnedCiphertextDigest() {
    VideoFixture().use { f ->
      val ctx=MediaContext(1,ByteArray(16) { 5 },1)
      val attempt=f.reserve(listOf(ctx))
      val original=F1ChargedRecord.seal(f.master,ctx,byteArrayOf(8),f.store,attempt,f.valid)
      val h=original.copyOfRange(0,156)
      val key=F1Record.deriveKey(f.master,h.copyOfRange(72,104),F1Context(f.identity,1,ctx.objectId,1))
      val replaced=try { h+F1Crypto.aead(Cipher.ENCRYPT_MODE,key,h.copyOfRange(104,116),h,byteArrayOf(9)) } finally { key.fill(0) }
      rejects { F1ChargedRecord.open(f.master,ctx,replaced,digest(original),f.store,f.valid) }
      assertEquals(1L,MediaUsage.parse(Files.readAllBytes(f.usage.resolve(digest(h.copyOfRange(12,104)).hex()))).chargedQueries)
    }
  }

  @Test fun authenticatedNoncanonicalVideoHeaderIsDenied() {
    VideoFixture().use { f ->
      val ref=f.write(byteArrayOf(8))
      val bytes=Files.readAllBytes(f.payload())
      ByteBuffer.wrap(bytes).putLong(116,1)
      f.retag(bytes,0,byteArrayOf())
      f.installWithMatchingLedgerHash(bytes,ref)
      val changed=MediaReference(f.context,bytes.size.toLong(),digest(bytes))
      rejects { f.store.openPayload(changed,f.valid).use { F1Video.open(f.master,f.context,it,f.store,f.valid).close() } }
    }
  }

  @Test fun authenticatedNoncanonicalChunkIsDenied() {
    VideoFixture().use { f ->
      val ref=f.write(byteArrayOf(8))
      val bytes=Files.readAllBytes(f.payload())
      ByteBuffer.wrap(bytes).putInt(172+148,F1Video.CHUNK_SIZE+1)
      f.retag(bytes,172,byteArrayOf(8))
      f.installWithMatchingLedgerHash(bytes,ref)
      val changed=MediaReference(f.context,bytes.size.toLong(),digest(bytes))
      rejects { f.store.openPayload(changed,f.valid).use { F1Video.verify(f.master,f.context,it,1,digest(byteArrayOf(8)),f.store,f.valid) } }
    }
  }

  @Test fun everyHeaderChunkCiphertextAndTagByteTamperIsDenied() {
    VideoFixture().use { f ->
      val ref=f.write(byteArrayOf(8))
      val original=Files.readAllBytes(f.payload())
      for(index in original.indices) {
        val bytes=original.copyOf(); bytes[index]=(bytes[index].toInt() xor 1).toByte()
        Files.write(f.payload(),bytes)
        rejects { f.store.openPayload(ref,f.valid).use { F1Video.verify(f.master,f.context,it,1,digest(byteArrayOf(8)),f.store,f.valid) } }
      }
      Files.write(f.payload(),original)
    }
  }

  @Test fun badUnreadSecondChunkDoesNotBecomeVerifiedByOpeningOrReadingFirst() {
    VideoFixture().use { f ->
      val plain=ByteArray(F1Video.CHUNK_SIZE+1) { 8 }
      val ref=f.write(plain)
      val bytes=Files.readAllBytes(f.payload()); bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte(); Files.write(f.payload(),bytes)
      f.store.openPayload(ref,f.valid).use { F1Video.open(f.master,f.context,it,f.store,f.valid).use { reader ->
        assertArrayEquals(plain.copyOfRange(0,F1Video.CHUNK_SIZE),reader.readChunk(0))
        rejects { reader.readChunk(1) }
      } }
      rejects { f.store.openPayload(ref,f.valid).use { F1Video.verify(f.master,f.context,it,plain.size.toLong(),digest(plain),f.store,f.valid) } }
    }
  }

  @Test fun emptyPartialMaximumAndOverflowArithmeticUseFrozenFraming() {
    assertEquals(F1Video.Plan(0,0,172),F1Video.plan(0))
    assertEquals(F1Video.Plan(2,65559,1049093),F1Video.plan(1048577))
    assertEquals(F1Video.Plan(8192,536961024,8591343788),F1Video.plan(F1Video.MAX_LENGTH))
    listOf(-1L,F1Video.MAX_LENGTH+1,Long.MAX_VALUE).forEach { rejects { F1Video.plan(it) } }
    VideoFixture().use { f ->
      val ref=f.write(byteArrayOf())
      assertEquals(172L,ref.ciphertextLength)
      f.store.openPayload(ref,f.valid).use { F1Video.open(f.master,f.context,it,f.store,f.valid).close() }
      f.store.openPayload(ref,f.valid).use { F1Video.verify(f.master,f.context,it,0,digest(byteArrayOf()),f.store,f.valid) }
    }
  }

  @Test fun closedOriginalAndReaderCannotBeRevivedWithNoopCallback() {
    VideoFixture().use { f ->
      val ref=f.write(byteArrayOf(8))
      val reader=F1Video.open(f.master,f.context,f.store.openPayload(ref,f.valid),f.store) {}
      reader.close()
      rejects { reader.readChunk(0) }
      f.operation.close()
      rejects { f.store.openPayload(ref) {} }
    }
  }

  @Test fun revokedAndAbaOriginalCannotBeRevivedWithNoopCallback() {
    VideoFixture().use { f ->
      val ref=f.write(byteArrayOf(8))
      val reader=F1Video.open(f.master,f.context,f.store.openPayload(ref,f.valid),f.store) {}
      val attempt=f.authority.beginAuthentication(); check(f.authority.completeAuthentication(attempt,f.master.copyOf()))
      rejects { reader.readChunk(0) }
      reader.close()
    }
  }

  @Test fun pinnedCiphertextReplacementWithEqualBytesIsDenied() {
    VideoFixture().use { f ->
      val ref=f.write(byteArrayOf(8))
      val reader=F1Video.open(f.master,f.context,f.store.openPayload(ref,f.valid),f.store,f.valid)
      val path=f.payload(); val bytes=Files.readAllBytes(path)
      Files.move(path,path.resolveSibling("saved")); Files.write(path,bytes)
      CountingGcm().use { rejects { reader.readChunk(0) }; assertEquals(0,CountingGcm.initializations) }
      reader.close()
    }
  }

  @Test fun genericOutputCannotEstablishPhysicalCompletion() {
    VideoFixture().use { f ->
      val attempt=f.reserve(listOf(f.context))
      rejects { F1Video.write(f.master,f.context,ByteArrayInputStream(byteArrayOf(8)),1,digest(byteArrayOf(8)),ByteArrayOutputStream(),f.store,attempt,f.valid) }
    }
  }
  @Test fun callerMasterMutationCannotSubstituteMaterialAfterOriginalAdmission() {
    VideoFixture().use { f ->
      val ctx=MediaContext(1,ByteArray(16) { 5 },1)
      val attempt=f.reserve(listOf(ctx)); val bytes=F1ChargedRecord.seal(f.master,ctx,byteArrayOf(8),f.store,attempt,f.valid)
      val supplied=f.master.copyOf()
      val mutating: () -> Unit = { f.operation.checkValid(); supplied.fill(99) }
      assertArrayEquals(byteArrayOf(8),F1ChargedRecord.open(supplied,ctx,bytes,digest(bytes),f.store,mutating))
    }
  }

  @Test fun mutableExpectedCiphertextHashCannotAuthorizeAuthenticatedSubstitution() {
    VideoFixture().use { f ->
      val ctx=MediaContext(1,ByteArray(16) { 5 },1)
      val attempt=f.reserve(listOf(ctx)); val original=F1ChargedRecord.seal(f.master,ctx,byteArrayOf(8),f.store,attempt,f.valid)
      val h=original.copyOfRange(0,156); val key=F1Record.deriveKey(f.master,h.copyOfRange(72,104),F1Context(f.identity,1,ctx.objectId,1))
      val substitute=try { h+F1Crypto.aead(Cipher.ENCRYPT_MODE,key,h.copyOfRange(104,116),h,byteArrayOf(9)) } finally { key.fill(0) }
      val expected=digest(original); var checks=0
      val mutating: () -> Unit = { f.operation.checkValid(); checks++; if(checks==2) digest(substitute).copyInto(expected) }
      rejects { F1ChargedRecord.open(f.master,ctx,substitute,expected,f.store,mutating) }
    }
  }

  @Test fun callerExpectedPlaintextHashMutationCannotCompleteChangingInput() {
    VideoFixture().use { f ->
      val attempt=f.reserve(listOf(f.context)); val expected=digest(byteArrayOf(8))
      val mutating: () -> Unit = { f.operation.checkValid(); digest(byteArrayOf(9)).copyInto(expected) }
      rejects { f.store.openOutput(f.master,f.context,attempt,f.valid).use { out -> F1Video.write(f.master,f.context,ByteArrayInputStream(byteArrayOf(9)),1,expected,out,f.store,attempt,mutating) } }
    }
  }

  @Test fun revocationAfterChunkAuthenticationWipesPlaintextBeforeReturning() {
    VideoFixture().use { f ->
      val ref=f.write(byteArrayOf(8))
      val reader=F1Video.open(f.master,f.context,f.store.openPayload(ref,f.valid),f.store,f.valid)
      var observed: ByteArray? = null
      CountingGcm().use {
        CountingGcm.afterDecrypt = { bytes -> observed=bytes; f.authority.revoke() }
        rejects { reader.readChunk(0) }
        assertArrayEquals(byteArrayOf(0),observed)
      }
      reader.close()
    }
  }

  @Test fun finalWholeCallbackRevocationDeniesAndWipesAuthenticatedPlaintext() {
    VideoFixture().use { f ->
      val ctx=MediaContext(1,ByteArray(16) { 5 },1)
      val attempt=f.reserve(listOf(ctx)); val bytes=F1ChargedRecord.seal(f.master,ctx,byteArrayOf(8),f.store,attempt,f.valid)
      var armed=false; var observed: ByteArray?=null
      val callback: () -> Unit = { if(armed) f.authority.revoke() }
      CountingGcm().use {
        CountingGcm.afterDecrypt={ plain -> observed=plain; armed=true }
        rejects { F1ChargedRecord.open(f.master,ctx,bytes,digest(bytes),f.store,callback) }
        assertArrayEquals(byteArrayOf(0),observed)
        assertEquals(1,CountingGcm.initializations)
      }
    }
  }

  @Test fun directFullReaderVerificationPinsExpectedPlaintextHashBeforeCallback() {
    VideoFixture().use { f ->
      val ref=f.write(byteArrayOf(8)); val expected=digest(byteArrayOf(9)); var mutate=false
      val callback: () -> Unit = { f.operation.checkValid(); if(mutate) digest(byteArrayOf(8)).copyInto(expected) }
      F1Video.open(f.master,f.context,f.store.openPayload(ref,f.valid),f.store,callback).use { reader ->
        mutate=true
        rejects { reader.verifyAll(1,expected) }
      }
    }
  }

  @Test fun directReaderConstructionCannotBindForeignMaster() {
    VideoFixture().use { f ->
      val ref=f.write(byteArrayOf(8)); val encoded=Files.readAllBytes(f.payload())
      f.store.openPayload(ref,f.valid).use { input -> CountingGcm().use {
        rejects { F1Video.Reader(ByteArray(32) { 99 },f.context,input,f.store,f.valid,encoded.copyOfRange(0,156),1,F1Video.plan(1)).close() }
        assertEquals(0,CountingGcm.initializations)
      } }
    }
  }

  @Test fun secondVideoKeyDerivationFailureDisposesRegisteredAllowanceBeforeCallerClosesOutput() {
    VideoFixture().use { f ->
      val attempt=f.reserve(listOf(f.context)); val output=f.store.openOutput(f.master,f.context,attempt,f.valid)
      try {
        CountingGcm(5).use {
          try {
            F1Video.write(f.master,f.context,ByteArrayInputStream(byteArrayOf()),0,digest(byteArrayOf()),output,f.store,attempt,f.valid)
            fail("provider interruption not reached")
          } catch (_: java.security.ProviderException) {}
          assertEquals(5,CountingGcm.hmacFinals)
          // Observe the actual live allowance registry only; no test seam supplies authority or crypto.
          val field=MediaUsageStore::class.java.getDeclaredField("livePermits").apply { isAccessible=true }
          assertEquals(0,(field.get(f.store) as Map<*,*>).size)
          assertEquals(1,CountingGcm.initializations) // Owner query only; video allowance never consumed.
        }
      } finally { output.close() }
    }
  }

  @Test fun wholeKeyDerivationFailureDisposesRegisteredAllowanceBeforeCallerClosesOutput() {
    VideoFixture().use { f ->
      val ctx=MediaContext(1,ByteArray(16) { 5 },1); val attempt=f.reserve(listOf(ctx))
      val output=f.store.openOutput(f.master,ctx,attempt,f.valid)
      try {
        CountingGcm(1).use {
          try {
            F1ChargedRecord.sealOwned(f.master,ctx,byteArrayOf(8),f.store,attempt,output,f.valid)
            fail("provider interruption not reached")
          } catch (_: java.security.ProviderException) {}
          val field=MediaUsageStore::class.java.getDeclaredField("livePermits").apply { isAccessible=true }
          assertEquals(0,(field.get(f.store) as Map<*,*>).size)
          assertEquals(0,CountingGcm.initializations)
        }
      } finally { output.close() }
    }
  }

  @Test fun directReaderNeverReturnsValidChunkBeforeExactHeaderAuthentication() {
    VideoFixture().use { f ->
      val ref=f.write(byteArrayOf(8)); val bytes=Files.readAllBytes(f.payload())
      bytes[156]=(bytes[156].toInt() xor 1).toByte(); Files.write(f.payload(),bytes)
      val input=f.store.openPayload(ref,f.valid)
      F1Video.Reader(f.master,f.context,input,f.store,f.valid,bytes.copyOfRange(0,156),1,F1Video.plan(1)).use { reader ->
        rejects { reader.readChunk(0) }
      }
    }
  }

  @Test fun directReaderSnapshotsHeaderBytesUsedForItsSuccessfulAuthentication() {
    VideoFixture().use { f ->
      val ref=f.write(byteArrayOf(8)); val header=Files.readAllBytes(f.payload()).copyOfRange(0,156)
      val input=f.store.openPayload(ref,f.valid)
      F1Video.Reader(f.master,f.context,input,f.store,f.valid,header,1,F1Video.plan(1)).use { reader ->
        f.store.precharge(listOf(header),listOf(1L),ref.hash,reader,f.valid).use { reader.authenticateHeader(it) }
        header.fill(0)
        assertArrayEquals(byteArrayOf(8),reader.readChunk(0))
      }
    }
  }

  @Test fun directReaderRejectsFramingWhichDisagreesWithOriginalOwnedCiphertext() {
    VideoFixture().use { f ->
      val ref=f.write(byteArrayOf(8)); val header=Files.readAllBytes(f.payload()).copyOfRange(0,156)
      f.store.openPayload(ref,f.valid).use { input ->
        rejects { F1Video.Reader(f.master,f.context,input,f.store,f.valid,header,0,F1Video.plan(0)).close() }
      }
    }
  }

  @Test fun retainedVideoOutputCannotRetryFirstRegistrationFailure() { failedVideoOutputCannotRetry(1) }
  @Test fun retainedVideoOutputCannotRetrySecondRegistrationFailure() { failedVideoOutputCannotRetry(2) }
  private fun failedVideoOutputCannotRetry(failingRegistration: Int) {
    VideoFixture().use { f ->
      val attempt=f.reserve(listOf(f.context)); var registrations=0
      val store=MediaUsageStore.hiddenMedia(f.dir.toFile(),f.identity,f.operation,LedgerFaults { e ->
        if(e.phase==LedgerPhase.REGISTRATION && e.point==LedgerFaultPoint.DURING_WRITE && ++registrations==failingRegistration) throw java.io.IOException("registration stop")
      })
      store.openOutput(f.master,f.context,attempt,f.valid).use { output -> CountingGcm().use {
        fun write() { F1Video.write(f.master,f.context,ByteArrayInputStream(byteArrayOf(8)),1,digest(byteArrayOf(8)),output,store,attempt,f.valid) }
        try { write(); fail("registration fault not reached") } catch (_: java.io.IOException) {}
        val before=Files.list(f.usage).use { paths -> paths.toList().associateWith(Files::readAllBytes) }
        val invocations=CountingGcm.initializations
        rejects { write() }
        assertEquals(failingRegistration,registrations); assertEquals(invocations,CountingGcm.initializations)
        assertEquals(before.keys,Files.list(f.usage).use { it.toList().toSet() })
        before.forEach { (path,bytes) -> assertArrayEquals(bytes,Files.readAllBytes(path)) }
      } }
    }
  }

  @Test fun retainedWholeU82OutputCannotRetryRegistrationFailure() {
    VideoFixture().use { f ->
      val ctx=MediaContext(1,ByteArray(16) { 5 },1); val attempt=f.reserve(listOf(ctx)); var registrations=0
      val store=MediaUsageStore.hiddenMedia(f.dir.toFile(),f.identity,f.operation,LedgerFaults { e ->
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

  @Test fun registrationCallbackCannotReenterTheSameOriginalProducer() {
    VideoFixture().use { f ->
      val ctx=MediaContext(1,ByteArray(16) { 5 },1); val attempt=f.reserve(listOf(ctx)); var reentered=false
      lateinit var store: MediaUsageStore; lateinit var output: OwnedMediaOutput
      store=MediaUsageStore.hiddenMedia(f.dir.toFile(),f.identity,f.operation,LedgerFaults { e ->
        if(!reentered && e.phase==LedgerPhase.REGISTRATION && e.point==LedgerFaultPoint.BEFORE_CREATE) {
          reentered=true
          rejects { F1ChargedRecord.sealOwned(f.master,ctx,byteArrayOf(9),store,attempt,output,f.valid) }
        }
      })
      output=store.openOutput(f.master,ctx,attempt,f.valid)
      output.use { CountingGcm().use {
        val bytes=F1ChargedRecord.sealOwned(f.master,ctx,byteArrayOf(8),store,attempt,output,f.valid)
        assertTrue(reentered); assertEquals(1,CountingGcm.initializations)
        assertArrayEquals(byteArrayOf(8),F1Record.decrypt(f.master,F1Context(f.identity,1,ctx.objectId,1),bytes))
      } }
    }
  }

  @Test fun outputBindingCallbackAbortDeniesProtectedWrite() {
    VideoFixture().use { f ->
      val ctx=MediaContext(1,ByteArray(16) { 5 },1); val attempt=f.reserve(listOf(ctx)); var armed=false
      lateinit var output: OwnedMediaOutput
      val callback: () -> Unit = { f.operation.checkValid(); if(armed) output.invalidate() }
      output=f.store.openOutput(f.master,ctx,attempt,callback)
      output.use {
        val header=F1Record.wholeHeader(F1Context(f.identity,1,ctx.objectId,1),ByteArray(32) { 7 },ByteArray(12) { 9 },1)
        f.store.reserveEncryption(listOf(header),listOf(1L),listOf(12L),attempt,output,f.valid).use { permit ->
          output.claim(f.store,ctx,attempt,permit,173,header); permit.consume(f.store,output,f.valid,header)
          armed=true
          rejects { output.write(byteArrayOf(8)) }
          assertEquals(0L,Files.size(f.root.resolve("transactions/media/attempts/"+attempt.hex()+"/files/0001-"+ctx.objectId.hex()+"-0000000000000001")))
        }
      }
    }
  }

  @Test fun outputBindingCallbackCannotReturnUnclaimedAdmissionAfterStartingRegistration() {
    VideoFixture().use { f ->
      val ctx=MediaContext(1,ByteArray(16) { 5 },1); val attempt=f.reserve(listOf(ctx)); var armed=false
      lateinit var output: OwnedMediaOutput
      val callback: () -> Unit = { f.operation.checkValid(); if(armed) { armed=false; output.beginRegistration(f.store,ctx,attempt) } }
      output=f.store.openOutput(f.master,ctx,attempt,callback)
      output.use { armed=true; rejects { output.checkForWrite(f.store,ctx,attempt) } }
    }
  }

  private fun rejects(action: () -> Unit) { try { action(); fail("accepted invalid F1 video") } catch (_: SecurityException) {} catch (_: IllegalStateException) {} catch (_: IllegalArgumentException) {} }
}

internal class VideoFixture : AutoCloseable {
  val dir = Files.createTempDirectory("phase3-video-")
  val master = ByteArray(32) { it.toByte() }
  val identity = DomainIdentity(ByteArray(16) { 1 }, ByteArray(16) { 2 })
  val context = MediaContext(10, ByteArray(16) { 3 }, 1)
  val authority = SecondarySessionAuthority { 0L }
  val operation: SecondaryOperation
  val valid: () -> Unit
  val store: MediaUsageStore
  val root = dir.resolve("domain-store")
  val usage = root.resolve("transactions/media/usage")
  init {
    listOf("descriptor", "slots", "index", "payloads", "previews", "transactions/media/usage", "transactions/media/attempts", "transactions/media/evidence", "transactions/proof/usage", "transactions/proof/anchors", "transactions/usage", "recovery", "temporary", "deleted").forEach { Files.createDirectories(root.resolve(it)) }
    val auth = authority.beginAuthentication()
    check(authority.completeAuthentication(auth, master.copyOf()))
    operation = authority.operationOrNull(SecondaryScope.entries.toSet())!!
    valid = operation::checkValid
    store = MediaUsageStore.hiddenMedia(dir.toFile(), identity, operation)
  }
  fun reserve(contexts: List<MediaContext>): ByteArray {
    val fresh = store.reserveAttempt(1, valid)
    val reservation = MediaAttemptReservation.parse(ByteBuffer.allocate(26).putShort(2).put(fresh.attemptId).putLong(1).array())
    val owner = MediaAttemptOwner.parse(ByteBuffer.allocate(55 + 26 * contexts.size).putShort(2).put(digest(reservation.encode())).putShort(3).put(ByteArray(16) { 4 }).put(0).putShort(contexts.size.toShort()).apply { contexts.forEach { put(it.encode()) } }.array())
    store.sealOwner(master, fresh, owner, valid)
    return fresh.attemptId
  }
  fun payload() = root.resolve("payloads/" + context.objectId.hex() + "/0000000000000001")
  fun retag(bytes: ByteArray, offset: Int, plaintext: ByteArray) {
    val h = bytes.copyOfRange(offset, offset + 156)
    val purpose = ByteBuffer.wrap(h).getShort(14).toInt()
    val key = F1Record.deriveKey(master, h.copyOfRange(72,104), F1Context(identity, purpose, context.objectId, 1))
    try { F1Crypto.aead(Cipher.ENCRYPT_MODE, key, h.copyOfRange(104,116), h, plaintext).copyInto(bytes, offset + 156) } finally { key.fill(0) }
  }
  fun installWithMatchingLedgerHash(bytes: ByteArray, original: MediaReference) {
    Files.write(payload(), bytes)
    val keys = listOf(bytes.copyOfRange(0,156), bytes.copyOfRange(172,328)).map { digest(it.copyOfRange(12,104)).hex() }
    keys.forEach { id -> val path = usage.resolve(id); val ledger = Files.readAllBytes(path); digest(bytes).copyInto(ledger,34); Files.write(path,ledger) }
  }
  fun write(plain: ByteArray): MediaReference {
    val attempt = reserve(listOf(context))
    val out = store.openOutput(master,context,attempt,valid)
    val ref = out.use { F1Video.write(master, context, ByteArrayInputStream(plain), plain.size.toLong(), digest(plain), it, store, attempt, valid) }
    val payload = root.resolve("payloads/" + context.objectId.hex() + "/0000000000000001")
    Files.createDirectories(payload.parent)
    Files.copy(root.resolve("transactions/media/attempts/"+attempt.hex()+"/files/000a-"+context.objectId.hex()+"-0000000000000001"),payload)
    return ref
  }
  override fun close() { operation.close(); authority.revoke(); dir.toFile().deleteRecursively() }
}
