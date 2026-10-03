package uk.co.traynor.privategallery.core.domain

import java.nio.ByteBuffer
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.PrimaryScope
import uk.co.traynor.privategallery.core.security.PrimarySessionAuthority

class RestrictedUsageTest {
  @Test fun projectionChargesCanonicalSlotsAndProofWithoutLookingInsideOpaqueMedia() {
    CounterFixture().use { f ->
      val opaque = f.root.resolve("transactions/media")
      Files.createDirectories(opaque)
      repeat(8200) { Files.write(opaque.resolve("x$it"),byteArrayOf()) }
      Files.setPosixFilePermissions(opaque,emptySet())
      CountingGcm().use {
        f.counters.chargeSelectedSlot(f.pinId,false,f.valid)
        f.counters.chargeSelectedProof(f.valid)
        assertEquals(0,CountingGcm.initializations)
      }
      assertEquals(1L,ByteBuffer.wrap(Files.readAllBytes(f.slotLedger)).getLong(18))
      assertEquals(1L,MediaUsage.parse(Files.readAllBytes(f.proofLedger)).chargedQueries)
      assertTrue(f.counters.checkedProjectionEntries(f.valid) < 64)
      Files.setPosixFilePermissions(opaque,setOf(java.nio.file.attribute.PosixFilePermission.OWNER_READ,java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE))
    }
  }

  @Test fun projectionQuotaAndProofStageQuotaFailWithoutMutatingOrDiscarding() {
    CounterFixture().use { f ->
      repeat(16) { Files.write(f.proofUsage.resolve("q"+(it+1).toString(16).padStart(32,'0')),ByteArray(it%3)) }
      val before = Files.readAllBytes(f.proofLedger)
      rejects { f.counters.chargeSelectedProof(f.valid) }
      assertArrayEquals(before,Files.readAllBytes(f.proofLedger))
      assertEquals(16L,Files.list(f.proofUsage).use { it.filter { p -> p.fileName.toString().startsWith("q") }.count() })
      repeat(17) { Files.write(f.credentialUsage.resolve("q"+(it+20).toString(16).padStart(32,'0')),ByteArray(58)) }
      f.counters.chargeSelectedSlot(f.pinId,false,f.valid)
      assertEquals(1L,ByteBuffer.wrap(Files.readAllBytes(f.slotLedger)).getLong(18))
      val existing = f.counters.checkedProjectionEntries(f.valid)
      repeat(8192-existing) { Files.write(f.credentialUsage.resolve((it+100).toString(16).padStart(64,'0')),ByteArray(58)) }
      assertEquals(8192,f.counters.checkedProjectionEntries(f.valid))
      val spent = Files.readAllBytes(f.slotLedger)
      rejects { f.counters.chargeSelectedSlot(f.pinId,false,f.valid) }
      assertArrayEquals(spent,Files.readAllBytes(f.slotLedger))
    }
  }

  @Test fun projectionRejectsUnknownReplacedMissingMalformedAndPendingMaterial() {
    listOf("unknown-root","unknown-usage","oversize","missing","pending","selector-replacement","wrong-bootstrap").forEach { kind -> CounterFixture().use { f ->
      f.counters.checkedProjectionEntries(f.valid)
      when(kind) {
        "unknown-root" -> Files.createDirectory(f.root.resolve("unexpected"))
        "unknown-usage" -> Files.write(f.credentialUsage.resolve("unknown"),byteArrayOf())
        "oversize" -> Files.write(f.proofUsage.resolve("q"+"a".repeat(32)),ByteArray(83))
        "missing" -> Files.delete(f.proofLedger)
        "pending" -> { val b=Files.readAllBytes(f.proofLedger); b.fill(0,34,66); Files.write(f.proofLedger,b) }
        "selector-replacement" -> { val p=f.root.resolve("selected"); val b=Files.readAllBytes(p); Files.move(p,p.resolveSibling("old")); Files.write(p,b); Files.delete(p.resolveSibling("old")) }
        "wrong-bootstrap" -> { val p=f.root.resolve("descriptor/"+f.token.hex()+"/bootstrap"); val b=Files.readAllBytes(p); b[10]=99; Files.write(p,b) }
      }
      rejects { f.counters.chargeSelectedProof(f.valid) }
    } }
  }

  @Test fun interruptedRestrictedChargesLeaveStagesAndRequireOriginalLiveGates() {
    CounterFixture().use { f ->
      val counters = f.service(LedgerFaults { e -> if(e.point==LedgerFaultPoint.DURING_WRITE) throw java.io.IOException("stop") })
      rejects { counters.chargeSelectedProof(f.valid) }
      val stage=Files.list(f.proofUsage).use { it.filter { p -> p.fileName.toString().startsWith("q") }.toList() }.single()
      assertEquals(41L,Files.size(stage))
      f.counters.chargeSelectedProof(f.valid)
      assertTrue(Files.exists(stage))
      f.authority.beginAuthentication()
      rejects { f.counters.chargeSelectedSlot(f.pinId,false) {} }
      f.primary.close()
      rejects { f.counters.chargeSelectedProof {} }
    }
  }

  @Test fun normalCredentialServiceReservesLast64AndUsesOriginalAuthenticationAttempt() {
    CounterFixture().use { f ->
      val normal=MediaUsageStore.normalCredentialCounters(f.dir.toFile(),f.identity,f.authority,f.attempt)
      val b=Files.readAllBytes(f.slotLedger); ByteBuffer.wrap(b).putLong(18,(1L shl 20)-64); Files.write(f.slotLedger,b)
      rejects { normal.chargeSelectedSlot(f.pinId,false,f.valid) }
      f.counters.chargeSelectedSlot(f.pinId,false,f.valid)
      assertEquals((1L shl 20)-63,ByteBuffer.wrap(Files.readAllBytes(f.slotLedger)).getLong(18))
      f.authority.beginAuthentication()
      rejects { normal.chargeSelectedSlot(f.pinId,false) {} }
    }
  }
  private fun rejects(action: () -> Unit) { try { action(); fail("accepted invalid restricted accounting") } catch (_: SecurityException) {} catch (_: IllegalStateException) {} catch (_: IllegalArgumentException) {} catch (_: java.io.IOException) {} }
}

internal class CounterFixture: AutoCloseable {
  val dir=Files.createTempDirectory("phase3-counter-")
  val root=dir.resolve("domain-store")
  val token=ByteArray(16) { 6 }
  val pinId=ByteArray(16) { 7 }
  val identity=DomainIdentity(ByteArray(16) { 11 },ByteArray(16) { 12 })
  val master=ByteArray(32) { 13 }
  val credentialUsage=root.resolve("transactions/usage")
  val proofUsage=root.resolve("transactions/proof/usage")
  val authority=SecondarySessionAuthority { 0L }
  val attempt=authority.beginAuthentication()
  val primaryAuthority=PrimarySessionAuthority { 0L }
  val primary: uk.co.traynor.privategallery.core.security.PrimaryOperation
  val valid: () -> Unit = { authority.checkAuthentication(attempt); primary.checkValid() }
  val slotLedger: java.nio.file.Path
  val proofLedger: java.nio.file.Path
  val counters: MediaUsageStore.CounterService
  init {
    listOf("descriptor","slots","recovery","index","payloads","previews","transactions","temporary","deleted").forEach { Files.createDirectories(root.resolve(it)) }
    listOf("descriptor","slots","recovery","index").forEach { Files.createDirectory(root.resolve(it).resolve(token.hex())) }
    Files.createDirectories(proofUsage); Files.createDirectories(root.resolve("transactions/proof/anchors")); Files.createDirectory(credentialUsage)
    Files.write(root.resolve("selected"),"PGDOMP01".toByteArray()+ByteBuffer.allocate(2).putShort(1).array()+token)
    val bootstrap=ByteBuffer.allocate(120).put("PGDOMB02".toByteArray()).putShort(2).put(identity.container).put(identity.master).putLong(1).putShort(2).put(ByteArray(16) { 1 }).put(ByteArray(16) { 2 }).put(ByteArray(16) { 3 }).putInt(0).put(ByteArray(16) { 4 }).array()
    Files.write(root.resolve("descriptor/"+token.hex()+"/bootstrap"),bootstrap)
    Files.write(root.resolve("index/"+token.hex()+"/catalog"),F1Record.encrypt(master,F1Context(identity,8,ByteArray(16) { 3 },1),ByteArray(12)))
    val slot=ByteBuffer.allocate(204).put("PGSLOT01".toByteArray()).putShort(1).putShort(156).put(identity.container).put(identity.master).put(pinId).putLong(1).putShort(1).putShort(0).putShort(1).putShort(1).putInt(131072).putInt(8).putInt(1).putShort(32).putShort(0).put(ByteArray(32) { 9 }).put(ByteArray(12) { 10 }).put(ByteArray(12)).putInt(48).putShort(1).putShort(0).put(ByteArray(48)).array()
    Files.write(root.resolve("slots/"+token.hex()+"/"+pinId.hex()),slot)
    val id=digest(slot.copyOfRange(12,70)+slot.copyOfRange(72,74)+slot.copyOfRange(92,124)).hex()
    slotLedger=credentialUsage.resolve(id); Files.write(slotLedger,ByteBuffer.allocate(58).putShort(1).putLong(1).putLong(13).putLong(0).put(digest(slot)).array())
    val proof=F1Record.encrypt(master,F1Context(identity,9,ByteArray(16) { 4 },1),byteArrayOf(8))
    Files.write(root.resolve("transactions/proof/anchors/"+token.hex()),proof)
    proofLedger=proofUsage.resolve(digest(proof.copyOfRange(12,104)).hex()); Files.write(proofLedger,ByteBuffer.allocate(82).putShort(2).putLong(1).putLong(12).putLong(0).putLong(1).put(digest(proof)).put(token).array())
    primaryAuthority.open(ByteArray(32) { 15 }); primary=primaryAuthority.operationOrNull(setOf(PrimaryScope.HOLD_RESTORE))!!
    counters=service()
  }
  fun service(faults: LedgerFaults=LedgerFaults.NONE)=MediaUsageStore.restrictedCounters(dir.toFile(),identity,primary,authority,attempt,faults)
  override fun close() { primary.close(); primaryAuthority.revoke(); authority.revoke(); dir.toFile().deleteRecursively() }
}
