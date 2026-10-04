package uk.co.traynor.privategallery.core.domain

import java.nio.ByteBuffer
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ProofUsageTest {
  @Test fun dedicatedProofProducerAndQueryLeaveExhaustedMediaLedgerUntouched() {
    ProofFixture().use { f ->
      val ref=f.video.write(byteArrayOf(8))
      val mediaKey=digest(Files.readAllBytes(f.video.payload()).copyOfRange(12,104)).hex()
      val media=f.video.usage.resolve(mediaKey)
      val exhausted=Files.readAllBytes(media); ByteBuffer.wrap(exhausted).putLong(18,1L shl 20); Files.write(media,exhausted)
      val fresh=f.store.reserveCredentialAttempt(1,f.video.valid)
      val body=f.prepare(fresh.token)
      val encoded=f.store.sealProof(f.video.master,fresh,body,f.video.valid)
      val key=digest(encoded.copyOfRange(12,104)).hex()
      assertArrayEquals(body.encode(),f.store.openProof(f.video.master,f.video.valid))
      val usage=MediaUsage.parse(Files.readAllBytes(f.proofUsage.resolve(key)))
      assertEquals(1L,usage.encryptionInvocations); assertEquals(2L,usage.chargedQueries)
      assertArrayEquals(digest(encoded),usage.ciphertextHash)
      assertArrayEquals(exhausted,Files.readAllBytes(media))
      assertEquals(1L,ref.context.generation)
    }
  }

  @Test fun normalProofRequiresCredentialsScope() {
    ProofFixture().use { f ->
      val op=f.video.authority.operationOrNull(setOf(SecondaryScope.READ,SecondaryScope.WRITE))!!
      val noCredentials=MediaUsageStore.hiddenProof(f.video.dir.toFile(),f.video.identity,op)
      rejects { noCredentials.reserveCredentialAttempt(1) {} }
      op.close()
    }
  }

  @Test fun normalProofPreservesLast64Queries() {
    ProofFixture().use { f ->
      val fresh=f.store.reserveCredentialAttempt(1,f.video.valid)
      val bytes=f.store.sealProof(f.video.master,fresh,f.prepare(fresh.token),f.video.valid)
      val path=f.proofUsage.resolve(digest(bytes.copyOfRange(12,104)).hex())
      val state=Files.readAllBytes(path); ByteBuffer.wrap(state).putLong(18,(1L shl 20)-64); Files.write(path,state)
      CountingGcm().use { rejects { f.store.openProof(f.video.master,f.video.valid) }; assertEquals(0,CountingGcm.initializations) }
      assertArrayEquals(state,Files.readAllBytes(path))
    }
  }

  @Test fun restartedFreshProofAttemptAndForeignMasterNeverGrantEncryption() {
    ProofFixture().use { f ->
      val fresh=f.store.reserveCredentialAttempt(1,f.video.valid)
      val body=f.prepare(fresh.token)
      val restart=MediaUsageStore.hiddenProof(f.video.dir.toFile(),f.video.identity,f.video.operation)
      CountingGcm().use {
        rejects { restart.sealProof(f.video.master,fresh,body,f.video.valid) }
        rejects { f.store.sealProof(ByteArray(32) { 99 },fresh,body) {} }
        assertEquals(0,CountingGcm.initializations)
      }
      assertFalse(Files.exists(f.video.root.resolve("transactions/proof/anchors/"+fresh.token.hex())))
    }
  }
  private fun rejects(action: () -> Unit) { try { action(); fail("accepted invalid proof accounting") } catch (_: SecurityException) {} catch (_: IllegalStateException) {} catch (_: IllegalArgumentException) {} }
}

internal class ProofFixture: AutoCloseable {
  val video=VideoFixture()
  val proofUsage=video.root.resolve("transactions/proof/usage")
  val store=MediaUsageStore.hiddenProof(video.dir.toFile(),video.identity,video.operation)
  fun prepare(token: ByteArray): CredentialProof {
    val root=video.root; val identity=video.identity
    listOf("descriptor","slots","recovery","index").forEach { Files.createDirectory(root.resolve(it).resolve(token.hex())) }
    val bootstrap=ByteBuffer.allocate(120).put("PGDOMB02".toByteArray()).putShort(2).put(identity.container).put(identity.master).putLong(1).putShort(2).put(ByteArray(16) { 1 }).put(ByteArray(16) { 2 }).put(ByteArray(16) { 3 }).putInt(0).put(ByteArray(16) { 4 }).array()
    Files.write(root.resolve("descriptor/"+token.hex()+"/bootstrap"),bootstrap)
    fun slot(id: Int,type: Int): ByteArray = ByteBuffer.allocate(204).put("PGSLOT01".toByteArray()).putShort(1).putShort(156).put(identity.container).put(identity.master).put(ByteArray(16) { id.toByte() }).putLong(1).putShort(type.toShort()).putShort(if(type==1) 0 else 2).putShort(1).putShort(if(type==1) 1 else 2).putInt(if(type==1) 131072 else 0).putInt(if(type==1) 8 else 0).putInt(if(type==1) 1 else 0).putShort(32).putShort(0).put(ByteArray(32) { 9 }).put(ByteArray(12) { 10 }).put(ByteArray(12)).putInt(48).putShort(1).putShort(0).put(ByteArray(48)).array()
    val pin=slot(7,1); val recovery=slot(8,2)
    Files.write(root.resolve("slots/"+token.hex()+"/"+ByteArray(16) { 7 }.hex()),pin)
    Files.write(root.resolve("recovery/"+token.hex()+"/"+ByteArray(16) { 8 }.hex()),recovery)
    val catalog=ByteBuffer.allocate(136).putShort(1).putLong(1).putShort(2).put(ByteArray(16) { 7 }).putLong(1).put(digest(pin)).putShort(1).putShort(0).putShort(1).put(ByteArray(16) { 8 }).putLong(1).put(digest(recovery)).putShort(2).putShort(2).putShort(1).array()
    val encrypted=F1Record.encrypt(video.master,F1Context(identity,8,ByteArray(16) { 3 },1),catalog)
    Files.write(root.resolve("index/"+token.hex()+"/catalog"),encrypted)
    Files.write(root.resolve("selected"),"PGDOMP01".toByteArray()+ByteBuffer.allocate(2).putShort(1).array()+token)
    return CredentialProof.parse(ByteBuffer.allocate(100+catalog.size).put("PGAUTH02".toByteArray()).putShort(2).put(token).put(digest(bootstrap)).put(digest(encrypted)).putShort(2).putShort(1).putShort(1).putInt(catalog.size).put(catalog).array())
  }
  override fun close() { video.close() }
}
