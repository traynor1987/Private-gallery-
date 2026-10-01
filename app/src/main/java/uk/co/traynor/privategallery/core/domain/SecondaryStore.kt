package uk.co.traynor.privategallery.core.domain

import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/** Fixed independent root. Only concrete Secondary capabilities can mutate authenticated state. */
class SecondaryStore private constructor(filesDir: File, private val storageIo: SecondaryStorageIo) {
  constructor(filesDir: File) : this(filesDir, DurableSecondaryIo)
  internal constructor(filesDir: File, storage: SecondaryStorageIo, @Suppress("UNUSED_PARAMETER") testAdapter: Unit) : this(filesDir, storage)
  private val parent = filesDir.toPath().toAbsolutePath().normalize()
  private val root = parent.resolve("domain-store")
  private val lock = locks.computeIfAbsent(root.toString()) { Any() }
  private var parentKey: Any? = null
  private var rootKey: Any? = null
  private val directoryKeys = linkedMapOf<Path, Any>()
  val containerId = uk.co.traynor.privategallery.core.security.ContainerId.SECONDARY

  private val io = object : SecondaryStorageIo {
    private fun check(path: Path) {
      bindParent(DomainInventory.stat(parent).key)
      bindDirectory(parent)
      if (path != root) bindRoot()
      var current = path.parent
      while (current != null && current.startsWith(root)) {
        bindDirectory(current); current = current.parent
      }
    }
    override fun mkdir(path: Path) { check(path); SecondaryDirectoryHandles.withPinned(parent, listOf(path.parent), directoryKeys.toMap()) { storageIo.mkdir(path) }; bindDirectory(path) }
    override fun writeNew(path: Path, bytes: ByteArray) { check(path); SecondaryDirectoryHandles.withPinned(parent, listOf(path.parent), directoryKeys.toMap()) { storageIo.writeNew(path, bytes) } }
    override fun syncDirectory(path: Path) { check(path); bindDirectory(path); SecondaryDirectoryHandles.withPinned(parent, listOf(path), directoryKeys.toMap()) { storageIo.syncDirectory(path) } }
    override fun atomicReplace(source: Path, target: Path) { check(source); check(target); SecondaryDirectoryHandles.withPinned(parent, listOf(source.parent, target.parent), directoryKeys.toMap()) { storageIo.atomicReplace(source, target) } }
  }

  fun preflight(): SecondaryPreflight = synchronized(lock) {
    try {
      val parentBefore = DomainInventory.stat(parent); storeCheck(parentBefore.directory); bindParent(parentBefore.key)
      if (DomainInventory.missingChild(parent, "domain-store")) {
        storeCheck(rootKey == null)
        storeCheck(parentBefore == DomainInventory.stat(parent)); SecondaryPreflight.FRESH
      } else {
        bindRoot()
        val entries = DomainInventory.children(root)
        if (entries.isEmpty()) {
          storeCheck(parentBefore == DomainInventory.stat(parent)); SecondaryPreflight.FRESH
        } else {
          val snapshot = load(); if (snapshot.confirmed) SecondaryPreflight.READY else SecondaryPreflight.PENDING
        }
      }
    } catch (_: Exception) { SecondaryPreflight.UNAVAILABLE }
  }

  fun create(pin: CharArray, attemptGuard: () -> Unit, commitGuard: (() -> Unit) -> Unit): PendingSetup = neutral {
    try {
      synchronized(lock) {
        attemptGuard(); storeCheck(preflight() == SecondaryPreflight.FRESH)
        val parentBefore = DomainInventory.stat(parent)
        if (DomainInventory.missingChild(parent, "domain-store")) { io.mkdir(root); io.syncDirectory(parent) }
        bindRoot(); storeCheck(parentBefore.key == DomainInventory.stat(parent).key && DomainInventory.children(root).isEmpty())
        namespaces.forEach { io.mkdir(root.resolve(it)); io.syncDirectory(root) }
        io.mkdir(root.resolve("transactions/usage")); io.syncDirectory(root.resolve("transactions"))
        val master = F1Crypto.random(32); val secret = try { F1Crypto.random(32) } catch (failure: Throwable) { master.fill(0); throw failure }
        try {
          val identity = DomainIdentity(F1Crypto.random(16), F1Crypto.random(16))
          val stage = reserve(1)
          val pinSlot = F1Slot.createPin(identity, pin, master, 1)
          val recovery = F1Slot.createRecovery(identity, secret, master, 1, false)
          val next = prepare(stage, identity, 1, false, master, pinSlot, recovery, null, null, StrongAuthInterval.DAY, SecondaryAutoLock.IMMEDIATE, null, 0)
          select(next, master, admissionCommit = commitGuard) { attemptGuard() }
          PendingSetup(this, identity, next.token, false, master, secret)
        } catch (failure: Throwable) { master.fill(0); secret.fill(0); throw failure }
      }
    } finally { pin.fill('\u0000') }
  }

  fun resumePending(pin: CharArray, attemptGuard: () -> Unit, commitGuard: (() -> Unit) -> Unit): PendingSetup = neutral {
    try { synchronized(lock) {
      attemptGuard(); val old = load(); storeCheck(!old.confirmed)
      val master = unwrapPin(old, pin)
      val secret = try { F1Crypto.random(32) } catch (failure: Throwable) { master.fill(0); throw failure }
      try {
        val verified = verify(old, master); val generation = increment(old.generation); val stage = reserve(generation)
        val recovery = F1Slot.createRecovery(old.identity, secret, master, generation, false)
        val next = prepare(stage, old.identity, generation, false, master, old.pin, recovery, null, null, verified.strong, verified.autoLock, null, 0)
        select(next, master, admissionCommit = commitGuard) { attemptGuard(); requireSelected(old) }
        PendingSetup(this, old.identity, next.token, false, master, secret)
      } catch (failure: Throwable) { master.fill(0); secret.fill(0); throw failure }
    } } finally { pin.fill('\u0000') }
  }

  fun confirm(pending: PendingSetup, secret: ByteArray, attemptGuard: () -> Unit, commitGuard: (() -> Unit) -> Unit): AuthenticatedDomain = neutral {
    synchronized(lock) {
      attemptGuard(); storeCheck(pending.owner === this && !pending.replacement)
      val old = load(); storeCheck(!old.confirmed && old.token == pending.selection)
      val master = pending.masterCopy(); val secretSnapshot = secret.copyOf()
      try {
        val verified = verify(old, master); confirmPossession(old, master, secretSnapshot, old.recovery)
        val generation = increment(old.generation); val stage = reserve(generation)
        val recovery = F1Slot.createRecovery(old.identity, secretSnapshot, master, generation, true)
        val next = prepare(stage, old.identity, generation, true, master, old.pin, recovery, null, null, verified.strong, verified.autoLock, null, 0)
        select(next, master, admissionCommit = commitGuard) { attemptGuard(); requireSelected(old) }
        pending.close(); authenticated(next, verified, master)
      } catch (failure: Throwable) { master.fill(0); throw failure } finally { secretSnapshot.fill(0) }
    }
  }

  fun authenticatePin(pin: CharArray): AuthenticatedDomain = neutral {
    try { synchronized(lock) {
      val snapshot = load(); val master = unwrapPin(snapshot, pin)
      try { authenticated(snapshot, verify(snapshot, master), master) } catch (failure: Throwable) { master.fill(0); throw failure }
    } } finally { pin.fill('\u0000') }
  }
  fun authenticateRecovery(secret: ByteArray): AuthenticatedDomain = neutral { synchronized(lock) {
    val snapshot = load(); storeCheck(snapshot.confirmed)
    val metadata = F1Slot.inspect(snapshot.identity, snapshot.recovery); storeCheck(metadata.recoveryState == 2)
    charge(snapshot.recovery, true)
    val master = F1Slot.unwrapRecovery(snapshot.identity, secret, snapshot.recovery)
    try { authenticated(snapshot, verify(snapshot, master), master) } catch (failure: Throwable) { master.fill(0); throw failure }
  } }
  fun validateAuthenticated(master: ByteArray): AuthenticatedDomain = neutral { synchronized(lock) {
    val snapshot = load(); authenticated(snapshot, verify(snapshot, master), master.copyOf())
  } }

  fun changePin(operation: SecondaryOperation, pin: CharArray) = neutral {
    try { mutate(operation, SecondaryScope.CREDENTIALS) { old, verified, generation, stage ->
      val slot = F1Slot.createPin(old.identity, pin, operation.key, generation)
      prepare(stage, old.identity, generation, old.confirmed, operation.key, slot, old.recovery, old.pendingRecovery, old.biometric, verified.strong, verified.autoLock, verified.biometricId, verified.biometricGeneration)
    } } finally { pin.fill('\u0000') }
  }
  fun updateSettings(operation: SecondaryOperation, strongAuthInterval: StrongAuthInterval, autoLock: SecondaryAutoLock) = neutral {
    mutate(operation, SecondaryScope.WRITE) { old, verified, generation, stage ->
      prepare(stage, old.identity, generation, old.confirmed, operation.key, old.pin, old.recovery, old.pendingRecovery, old.biometric, strongAuthInterval, autoLock, verified.biometricId, verified.biometricGeneration)
    }
  }
  fun replaceRecovery(operation: SecondaryOperation): PendingSetup = neutral { synchronized(lock) {
    operation.requireScope(SecondaryScope.RECOVERY); val old = load(); storeCheck(old.confirmed); val verified = verify(old, operation.key)
    val generation = increment(old.generation); val stage = reserve(generation); val secret = F1Crypto.random(32)
    try {
      val recovery = F1Slot.createRecovery(old.identity, secret, operation.key, generation, false)
      val next = prepare(stage, old.identity, generation, true, operation.key, old.pin, old.recovery, recovery, old.biometric, verified.strong, verified.autoLock, verified.biometricId, verified.biometricGeneration)
      select(next, operation.key, operation) { operation.checkValid(); requireSelected(old) }
      PendingSetup(this, old.identity, next.token, true, operation.key.copyOf(), secret).also { operation.ownForSession(it) }
    } catch (failure: Throwable) { secret.fill(0); throw failure }
  } }
  fun confirmReplacement(operation: SecondaryOperation, pending: PendingSetup, secret: ByteArray) = neutral { synchronized(lock) {
    operation.requireScope(SecondaryScope.RECOVERY); storeCheck(pending.owner === this && pending.replacement)
    val old = load(); storeCheck(old.confirmed && old.token == pending.selection)
    val verified = verify(old, operation.key); val pendingSlot = old.pendingRecovery ?: throw SecondaryStoreException()
    val secretSnapshot = secret.copyOf()
    try {
      confirmPossession(old, operation.key, secretSnapshot, pendingSlot)
      val generation = increment(old.generation); val stage = reserve(generation)
      val recovery = F1Slot.createRecovery(old.identity, secretSnapshot, operation.key, generation, true)
      val next = prepare(stage, old.identity, generation, true, operation.key, old.pin, recovery, null, null, verified.strong, verified.autoLock, null, 0)
      select(next, operation.key, operation) { operation.checkValid(); requireSelected(old) }; pending.close()
    } finally { secretSnapshot.fill(0) }
  } }
  fun installBiometric(operation: SecondaryOperation, slotId: ByteArray, generation: Long, envelope: ByteArray) = neutral {
    storeCheck(slotId.size == 16 && generation > 0 && envelope.size in 1..4096)
    val id = slotId.copyOf(); val copy = ByteBuffer.allocate(26 + envelope.size).putShort(1).put(id).putLong(generation).put(envelope.copyOf()).array()
    mutate(operation, SecondaryScope.CREDENTIALS) { old, verified, revision, stage ->
      storeCheck(old.confirmed)
      prepare(stage, old.identity, revision, true, operation.key, old.pin, old.recovery, old.pendingRecovery, copy, verified.strong, verified.autoLock, id, generation)
    }
  }
  fun removeBiometric(operation: SecondaryOperation) = neutral {
    mutate(operation, SecondaryScope.CREDENTIALS) { old, verified, revision, stage ->
      prepare(stage, old.identity, revision, old.confirmed, operation.key, old.pin, old.recovery, old.pendingRecovery, null, verified.strong, verified.autoLock, null, 0)
    }
  }
  /** Untrusted bootstrap only; caller MUST validateAuthenticated and compare active extension. */
  fun biometricRecord(): SecondaryBiometricRecord? = neutral { synchronized(lock) {
    val snapshot = load(); snapshot.biometric?.let { encoded ->
      val b = ByteBuffer.wrap(encoded); storeCheck(encoded.size >= 26 && b.getShort(0).toInt() == 1 && b.getLong(18) > 0)
      SecondaryBiometricRecord(snapshot.identity, encoded.copyOfRange(2, 18), b.getLong(18), encoded.copyOfRange(26, encoded.size))
    }
  } }
  fun validateBiometric(master: ByteArray, record: SecondaryBiometricRecord): AuthenticatedDomain = neutral { synchronized(lock) {
    val snapshot = load(); val verified = verify(snapshot, master)
    storeCheck(snapshot.identity.matches(record.identity.container, record.identity.master) && verified.biometricId != null && same(verified.biometricId, record.slotId) && verified.biometricGeneration == record.generation)
    val encoded = snapshot.biometric ?: throw SecondaryStoreException()
    storeCheck(same(encoded.copyOfRange(26, encoded.size), record.envelope))
    authenticated(snapshot, verified, master.copyOf())
  } }

  private fun mutate(operation: SecondaryOperation, scope: SecondaryScope, prepareNext: (DomainSnapshot, VerifiedDescriptor, Long, String) -> DomainSnapshot) = synchronized(lock) {
    operation.requireScope(scope); val old = load(); storeCheck(old.confirmed); val verified = verify(old, operation.key)
    val generation = increment(old.generation); val stage = reserve(generation)
    val next = prepareNext(old, verified, generation, stage)
    select(next, operation.key, operation) { operation.checkValid(); requireSelected(old) }
  }
  private fun authenticated(snapshot: DomainSnapshot, verified: VerifiedDescriptor, master: ByteArray) = AuthenticatedDomain(snapshot.identity, snapshot.confirmed, verified.strong, verified.autoLock, master)
  private fun unwrapPin(snapshot: DomainSnapshot, pin: CharArray): ByteArray {
    charge(snapshot.pin, true); return F1Slot.unwrapPin(snapshot.identity, pin, snapshot.pin)
  }
  private fun confirmPossession(snapshot: DomainSnapshot, master: ByteArray, secret: ByteArray, envelope: ByteArray) {
    storeCheck(F1Slot.inspect(snapshot.identity, envelope).recoveryState == 1)
    charge(envelope, true); val unwrapped = F1Slot.unwrapRecovery(snapshot.identity, secret, envelope)
    try { storeCheck(same(master, unwrapped)) } finally { unwrapped.fill(0) }
  }
  private fun increment(value: Long): Long { storeCheck(value < Long.MAX_VALUE); return value + 1 }

  /** An immutable attempt is synced BEFORE any encryption; retries never reuse an attempt/domain. */
  private fun reserve(generation: Long): String {
    val token = F1Crypto.random(16).hex(); val path = root.resolve("transactions").resolve(token)
    io.mkdir(path); io.writeNew(path.resolve("reservation"), ByteBuffer.allocate(10).putShort(1).putLong(generation).array())
    io.syncDirectory(path); io.syncDirectory(root.resolve("transactions")); return token
  }
  private fun prepare(token: String, identity: DomainIdentity, generation: Long, confirmed: Boolean, master: ByteArray, pin: ByteArray, recovery: ByteArray, pending: ByteArray?, biometric: ByteArray?, strong: StrongAuthInterval, auto: SecondaryAutoLock, bioId: ByteArray?, bioGeneration: Long): DomainSnapshot {
    val ids = List(3) { F1Crypto.random(16) }
    val bootstrap = DomainEncoding.bootstrap(identity, generation, confirmed, ids)
    val index = F1Record.encrypt(master, F1Context(identity, 1, ids[1], generation), DomainEncoding.emptyIndex)
    val slots = listOfNotNull(pin, recovery, pending)
    val catalog = F1Record.encrypt(master, F1Context(identity, 8, ids[2], generation), DomainEncoding.catalog(identity, generation, slots))
    // Copying an existing immutable device extension never invokes encryption again.
    val bio = if (biometric == null) null else {
      storeCheck(bioId != null && bioGeneration > 0 && biometric.size in 27..4122)
      storeCheck(ByteBuffer.wrap(biometric).getShort(0).toInt() == 1 && same(biometric.copyOfRange(2, 18), bioId!!) && ByteBuffer.wrap(biometric).getLong(18) == bioGeneration)
      biometric.copyOf()
    }
    val skeleton = DomainSnapshot(token, identity, generation, confirmed, ids[0], ids[1], ids[2], bootstrap, byteArrayOf(), index, catalog, pin.copyOf(), recovery.copyOf(), pending?.copyOf(), bio)
    val descriptor = F1Record.encrypt(master, skeleton.context(9), DomainEncoding.descriptor(skeleton, strong, auto, bioId, bioGeneration))
    val snapshot = DomainSnapshot(token, identity, generation, confirmed, ids[0], ids[1], ids[2], bootstrap, descriptor, index, catalog, pin.copyOf(), recovery.copyOf(), pending?.copyOf(), bio)
    listOf(descriptor, index, catalog).forEach { registerKey(it, true) }
    slots.forEach { registerKey(it, F1Slot.inspect(identity, it).generation == generation) }
    listOf("descriptor", "slots", "recovery", "index").forEach { io.mkdir(root.resolve(it).resolve(token)) }
    fun write(namespace: String, name: String, bytes: ByteArray) { io.writeNew(root.resolve(namespace).resolve(token).resolve(name), bytes) }
    write("descriptor", "bootstrap", bootstrap); write("descriptor", "descriptor", descriptor)
    bio?.let { write("descriptor", "biometric", it) }
    write("slots", F1Slot.inspect(identity, pin).slotId.hex(), pin)
    write("recovery", F1Slot.inspect(identity, recovery).slotId.hex(), recovery)
    pending?.let { write("recovery", F1Slot.inspect(identity, it).slotId.hex(), it) }
    write("index", "index", index); write("index", "catalog", catalog)
    listOf("descriptor", "slots", "recovery", "index").forEach { io.syncDirectory(root.resolve(it).resolve(token)); io.syncDirectory(root.resolve(it)) }
    io.writeNew(root.resolve("transactions").resolve(token).resolve("complete"), digest(bootstrap + descriptor + index + catalog))
    io.syncDirectory(root.resolve("transactions").resolve(token)); io.syncDirectory(root.resolve("transactions"))
    val readback = readGeneration(token); verify(readback, master); return readback
  }

  private fun select(next: DomainSnapshot, master: ByteArray, operation: SecondaryOperation? = null, admissionCommit: (((() -> Unit)) -> Unit)? = null, guard: () -> Unit) {
    verify(next, master)
    val pointer = pointer(next.token)
    val temporary = root.resolve("temporary").resolve(next.token)
    io.writeNew(temporary, pointer); io.syncDirectory(root.resolve("temporary"))
    // Bound and check the storage tree outside the short original-epoch promotion gate.
    inventoryChecked()
    val promote = { guard(); io.atomicReplace(temporary, root.resolve("selected")); Unit }
    if (operation != null) operation.commit(promote) else (admissionCommit ?: throw SecondaryStoreException()).invoke(promote)
    io.syncDirectory(root)
    val reopened = load(); storeCheck(reopened.token == next.token); verify(reopened, master)
  }
  private fun requireSelected(old: DomainSnapshot) { storeCheck(readPointer() == old.token) }
  private fun pointer(token: String): ByteArray = "PGDOMP01".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 1) + token.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
  private fun readPointer(): String {
    val p = DomainInventory.read(root.resolve("selected"), 26)
    storeCheck(p.size == 26 && p.copyOfRange(0, 8).contentEquals("PGDOMP01".toByteArray(Charsets.US_ASCII)) && ByteBuffer.wrap(p).getShort(8).toInt() == 1)
    return p.copyOfRange(10, 26).hex()
  }
  private fun load(): DomainSnapshot {
    val before = inventoryChecked(); val token = readPointer(); val snapshot = readGeneration(token)
    storeCheck(before == inventoryChecked()); return snapshot
  }
  private fun readGeneration(token: String): DomainSnapshot {
    storeCheck(idRegex.matches(token))
    val descriptorDir = root.resolve("descriptor").resolve(token)
    val bootstrap = DomainInventory.read(descriptorDir.resolve("bootstrap"), DomainEncoding.BOOTSTRAP_SIZE)
    val (identity, generation, confirmed) = DomainEncoding.parseBootstrap(bootstrap)
    val descriptor = DomainInventory.read(descriptorDir.resolve("descriptor"), 65536 + 172)
    val index = DomainInventory.read(root.resolve("index").resolve(token).resolve("index"), 16 * 1024 * 1024 + 172)
    val catalog = DomainInventory.read(root.resolve("index").resolve(token).resolve("catalog"), 16 * 1024 * 1024 + 172)
    val slots = DomainInventory.children(root.resolve("slots").resolve(token)); storeCheck(slots.size == 1)
    val pin = readSlot(identity, slots.single()); storeCheck(F1Slot.inspect(identity, pin).slotType == 1)
    val recoveries = DomainInventory.children(root.resolve("recovery").resolve(token)).map { readSlot(identity, it) }
    storeCheck(recoveries.size in 1..2)
    recoveries.forEach { storeCheck(F1Slot.inspect(identity, it).slotType == 2) }
    val active = recoveries.filter { F1Slot.inspect(identity, it).recoveryState == if (confirmed) 2 else 1 }; storeCheck(active.size == 1)
    val pending = if (confirmed) recoveries.singleOrNull { F1Slot.inspect(identity, it).recoveryState == 1 } else null
    if (!confirmed) storeCheck(recoveries.size == 1)
    val names = DomainInventory.children(descriptorDir).map { it.fileName.toString() }; storeCheck(names.toSet() == setOf("bootstrap", "descriptor") || names.toSet() == setOf("bootstrap", "descriptor", "biometric"))
    val bio = if ("biometric" in names) DomainInventory.read(descriptorDir.resolve("biometric"), 4122) else null
    val complete = DomainInventory.read(root.resolve("transactions").resolve(token).resolve("complete"), 32)
    storeCheck(same(complete, digest(bootstrap + descriptor + index + catalog)))
    val reservation = DomainInventory.read(root.resolve("transactions").resolve(token).resolve("reservation"), 10)
    storeCheck(reservation.size == 10 && ByteBuffer.wrap(reservation).getShort(0).toInt() == 1 && ByteBuffer.wrap(reservation).getLong(2) == generation)
    val snapshot = DomainSnapshot(token, identity, generation, confirmed, bootstrap.copyOfRange(52, 68), bootstrap.copyOfRange(68, 84), bootstrap.copyOfRange(84, 100), bootstrap, descriptor, index, catalog, pin, active.single(), pending, bio)
    checkPublicRecord(snapshot.descriptor, snapshot.context(9)); checkPublicRecord(snapshot.index, snapshot.context(1)); checkPublicRecord(snapshot.catalog, snapshot.context(8))
    bio?.let { storeCheck(it.size in 27..4122 && ByteBuffer.wrap(it).getShort(0).toInt() == 1 && ByteBuffer.wrap(it).getLong(18) > 0) }
    return snapshot
  }
  private fun checkPublicRecord(bytes: ByteArray, context: F1Context) {
    storeCheck(bytes.size >= 172); val b = ByteBuffer.wrap(bytes)
    storeCheck(bytes.copyOfRange(0, 8).contentEquals("PGFUTR01".toByteArray(Charsets.US_ASCII)))
    storeCheck(b.getShort(8).toInt() == 1 && b.getShort(10).toInt() == 156 && b.getShort(12).toInt() == 1 && b.getShort(14).toInt() == context.purpose)
    storeCheck(context.identity.matches(bytes.copyOfRange(16, 32), bytes.copyOfRange(32, 48)) && context.matchesObject(bytes.copyOfRange(48, 64)) && b.getLong(64) == context.generation)
    val length = b.getLong(116); storeCheck(length in 0..F1Record.maximumBodyLength(context.purpose) && b.getLong(124) == length + 16 && b.getLong(132) == length && bytes.size.toLong() == length + 172)
    storeCheck((140..152 step 4).all { b.getInt(it) == 0 })
  }
  private fun readSlot(identity: DomainIdentity, path: Path): ByteArray {
    storeCheck(idRegex.matches(path.fileName.toString())); val bytes = DomainInventory.read(path, 204)
    storeCheck(F1Slot.inspect(identity, bytes).slotId.hex() == path.fileName.toString()); return bytes
  }
  private fun verify(snapshot: DomainSnapshot, master: ByteArray): VerifiedDescriptor {
    var descriptor: ByteArray? = null; var catalog: ByteArray? = null; var index: ByteArray? = null
    try {
      charge(snapshot.descriptor, false); descriptor = F1Record.decrypt(master, snapshot.context(9), snapshot.descriptor)
      charge(snapshot.catalog, false); catalog = F1Record.decrypt(master, snapshot.context(8), snapshot.catalog)
      charge(snapshot.index, false); index = F1Record.decrypt(master, snapshot.context(1), snapshot.index)
      val verified = DomainEncoding.verifyDescriptor(snapshot, descriptor)
      storeCheck(same(index, DomainEncoding.emptyIndex))
      storeCheck(same(catalog, DomainEncoding.catalog(snapshot.identity, snapshot.generation, listOfNotNull(snapshot.pin, snapshot.recovery, snapshot.pendingRecovery))))
      return verified
    } finally { descriptor?.fill(0); catalog?.fill(0); index?.fill(0) }
  }

  /** Durable cap on ALL verification queries (stronger than the 2^20 failed-tag ceiling). */
  private fun keyId(bytes: ByteArray, slot: Boolean): String {
    storeCheck(bytes.size >= 156)
    return digest(if (slot) (bytes.copyOfRange(12, 70) + bytes.copyOfRange(72, 74) + bytes.copyOfRange(92, 124)) else bytes.copyOfRange(12, 104)).hex()
  }
  private fun registerKey(bytes: ByteArray, allowNew: Boolean) {
    val slot = bytes.copyOfRange(0, 8).contentEquals("PGSLOT01".toByteArray(Charsets.US_ASCII))
    val path = root.resolve("transactions").resolve("usage").resolve(keyId(bytes, slot))
    if (DomainInventory.missingChild(path.parent, path.fileName.toString())) {
      storeCheck(allowNew)
      // Every actual derived key performs exactly one encryption. GHASH charge includes padded AAD and length.
      val ciphertext = bytes.size - 156 - 16; val blocks = 10L + (ciphertext + 15L) / 16L + 1L
      storeCheck(blocks <= (1L shl 32))
      io.writeNew(path, ByteBuffer.allocate(26).putShort(1).putLong(1).putLong(blocks).putLong(0).array() + digest(bytes)); io.syncDirectory(path.parent)
    } else {
      // Existing immutable slot copy must be identical to its original digest, not another encryption.
      val existing = DomainInventory.read(path, 58); storeCheck(existing.size == 58 && same(existing.copyOfRange(26, 58), digest(bytes)))
    }
  }

  private fun charge(bytes: ByteArray, slot: Boolean) {
    val path = root.resolve("transactions").resolve("usage").resolve(keyId(bytes, slot))
    val ledger = DomainInventory.read(path, 58); storeCheck(ledger.size == 58)
    val b = ByteBuffer.wrap(ledger); storeCheck(b.getShort(0).toInt() == 1 && b.getLong(2) == 1L && b.getLong(10) in 11..(1L shl 32))
    val count = b.getLong(18); storeCheck(count in 0 until MAX_QUERIES)
    // Header mutation with same key tuple consumes the SAME ledger, even if tag/body differs.
    val updated = ledger.copyOf(); ByteBuffer.wrap(updated).putLong(18, count + 1)
    val temporary = path.parent.resolve("q" + F1Crypto.random(16).hex())
    io.writeNew(temporary, updated); io.atomicReplace(temporary, path); io.syncDirectory(path.parent)
  }

  private fun bindParent(key: Any) { parentKey?.let { storeCheck(it == key) }; parentKey = key; directoryKeys[parent]?.let { storeCheck(it == key) }; directoryKeys[parent] = key }
  private fun bindDirectory(path: Path) {
    val entry = DomainInventory.stat(path); storeCheck(entry.directory)
    directoryKeys[path]?.let { storeCheck(it == entry.key) }; directoryKeys[path] = entry.key
  }
  private fun bindRoot() { val key = DomainInventory.stat(root).key; rootKey?.let { storeCheck(it == key) }; rootKey = key; bindDirectory(root) }
  private fun inventoryChecked(): Map<String, DomainInventory.Entry> {
    val parentBefore = DomainInventory.stat(parent); storeCheck(parentBefore.directory); bindParent(parentBefore.key); bindRoot()
    val before = DomainInventory.snapshot(root)
    before.forEach { (relative, entry) -> if (entry.directory) bindDirectory(root.resolve(relative)) }
    storeCheck(before[""]?.directory == true)
    val rootNames = DomainInventory.children(root).map { it.fileName.toString() }.toSet()
    storeCheck(rootNames == namespaces.toSet() + "selected" || rootNames == namespaces.toSet())
    namespaces.forEach { storeCheck(DomainInventory.stat(root.resolve(it)).directory) }
    for ((relative, entry) in before) {
      val segments = relative.split(File.separatorChar)
      if (relative.isEmpty() || segments.size == 1) continue
      val namespace = segments[0]
      if (namespace in setOf("payloads", "previews", "deleted")) throw SecondaryStoreException()
      if (namespace == "temporary") { storeCheck(segments.size == 2 && !entry.directory && idRegex.matches(segments[1])); continue }
      if (namespace == "transactions" && segments[1] == "usage") {
        if (segments.size == 2) storeCheck(entry.directory)
        else storeCheck(segments.size == 3 && !entry.directory && (hashRegex.matches(segments[2]) || queryRegex.matches(segments[2])))
        continue
      }
      storeCheck(segments.size in 2..3 && idRegex.matches(segments[1]))
      if (segments.size == 2) {
        storeCheck(entry.directory)
        if (namespace != "transactions") storeCheck(!DomainInventory.stat(root.resolve("transactions").resolve(segments[1]).resolve("reservation")).directory)
      } else {
        storeCheck(!entry.directory)
        storeCheck(when (namespace) {
          "descriptor" -> segments[2] in setOf("bootstrap", "descriptor", "biometric")
          "slots", "recovery" -> idRegex.matches(segments[2])
          "index" -> segments[2] in setOf("index", "catalog")
          "transactions" -> segments[2] in setOf("reservation", "complete")
          else -> false
        })
      }
    }
    storeCheck(parentBefore == DomainInventory.stat(parent)); storeCheck(before == DomainInventory.snapshot(root)); return before
  }
  private inline fun <T> neutral(action: () -> T): T = try { action() } catch (_: Exception) { throw SecondaryStoreException() }
  companion object {
    private val locks = ConcurrentHashMap<String, Any>()
    private val namespaces = listOf("descriptor", "slots", "index", "payloads", "previews", "transactions", "recovery", "temporary", "deleted")
    private val idRegex = Regex("[0-9a-f]{32}")
    private val hashRegex = Regex("[0-9a-f]{64}")
    private val queryRegex = Regex("q[0-9a-f]{32}")
    internal const val MAX_QUERIES = 1L shl 20
  }
}
