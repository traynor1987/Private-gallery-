package uk.co.traynor.privategallery.core.domain

import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.attribute.BasicFileAttributeView
import java.util.concurrent.ConcurrentHashMap

/** One rank4 gate for every service targeting the fixed normalized Hidden root. */
internal object HiddenRootLocks {
  private val locks = ConcurrentHashMap<Path, Any>()
  fun forFilesDir(filesDir: File): Any = locks.computeIfAbsent(filesDir.toPath().toAbsolutePath().normalize().resolve("domain-store")) { Any() }
}

/** Concrete fixed-root accounting. No public root/key selector or optional accounting provider. */
internal class MediaUsageStore private constructor(
  filesDir: File,
  internal val identity: DomainIdentity,
  private val original: OriginalGate,
  private val kind: Kind,
  private val faults: LedgerFaults,
) {
  private enum class Kind { MEDIA, PRIMARY, PROOF, CREDENTIAL }
  private val primary58 = kind == Kind.PRIMARY
  private val counterOnly = original is OriginalGate.Authentication
  private var allowCredentialShorts = false
  private var projectionCount: (() -> Int)? = null
  private val anchor = filesDir.toPath().toAbsolutePath().normalize()
  private val root = anchor.resolve(if (primary58) "vault/transfer-v1" else "domain-store")
  private val usage = root.resolve(when (kind) { Kind.PRIMARY -> "usage"; Kind.MEDIA -> "transactions/media/usage"; Kind.PROOF -> "transactions/proof/usage"; Kind.CREDENTIAL -> "transactions/usage" })
  private val attempts = root.resolve(if (primary58) "attempts" else "transactions/media/attempts")
  private val lock = if (primary58) uk.co.traynor.privategallery.core.security.PrimaryVaultSetupGuard.storageLock else HiddenRootLocks.forFilesDir(filesDir)
  private val ledgerSize = if (kind in setOf(Kind.PRIMARY,Kind.CREDENTIAL)) 58 else 82
  private val directories = linkedMapOf<Path, Any>()
  private val freshCredentials = mutableSetOf<ReservedCredential>()
  private val freshAttempts = mutableSetOf<ReservedAttempt>()
  private val claimedAttempts = mutableSetOf<ReservedAttempt>()
  private val liveAdmissions = ConcurrentHashMap<Any, Boolean>()
  private val liveOutputs = ConcurrentHashMap<OwnedMediaOutput, Boolean>()
  private val liveCompletions = ConcurrentHashMap<OwnedMediaOutput.Completion, OwnedMediaOutput>()
  private val livePermits = ConcurrentHashMap<Permit, Boolean>()
  private val quarantine = linkedMapOf<Path, DomainInventory.Entry>()
  private val shortCanonicals = linkedMapOf<Path,DomainInventory.Entry>()
  private val ownedFailedStages = mutableListOf<OwnedStage>()
  private class OwnedStage(val path: Path, val key: Any, var entry: DomainInventory.Entry) {
    var replacementAttempted = false
    var bytes = byteArrayOf()
  }

  init { bind(usage); if (!counterOnly && kind == Kind.PROOF) bind(root.resolve("transactions/proof/anchors")); if (!counterOnly && kind in setOf(Kind.MEDIA,Kind.PRIMARY)) bind(attempts) }

  internal fun valid(extra: () -> Unit, write: Boolean = false) {
    if(kind == Kind.PROOF && !counterOnly) original.credentials()
    original.valid(write)
    extra()
    if(kind == Kind.PROOF && !counterOnly) original.credentials()
    original.valid(write)
  }

  internal fun validMaster(master: ByteArray) { original.master(master).fill(0) }
  internal fun <T> withMaster(master: ByteArray, action: (ByteArray) -> T): T {
    val owned = original.master(master)
    return try { action(owned) } finally { owned.fill(0) }
  }
  internal fun maximumBodyLength(purpose: Int): Long = when {
    kind == Kind.MEDIA && purpose == 2 -> 48L * 1024 * 1024
    kind == Kind.MEDIA && purpose == 3 -> 58L + 2L * 1024 * 1024
    kind == Kind.PROOF && purpose == 9 -> 2096L
    else -> F1Record.maximumBodyLength(purpose)
  }
  internal fun context(context: MediaContext): F1Context = F1Context(identity, context.purpose, context.objectId, context.generation)

  private fun bind(path: Path) {
    var p: Path? = path
    while (p != null && p.startsWith(anchor)) {
      val entry = DomainInventory.stat(p)
      storeCheck(entry.directory)
      val prior = directories.putIfAbsent(p, entry.key)
      storeCheck(prior == null || prior == entry.key)
      if (p == anchor) break
      p = p.parent
    }
  }

  private fun <T> pinned(path: Path, action: () -> T): T {
    bind(path)
    return SecondaryDirectoryHandles.withPinned(anchor, listOf(path), directories.toMap(), action)
  }

  private fun read(path: Path, bound: Int): ByteArray = try { bind(path.parent); DomainInventory.read(path, bound) } catch (_: java.io.IOException) { throw SecondaryStoreException() }
  private fun writeNew(path: Path, bytes: ByteArray, check: () -> Unit): DomainInventory.Entry = pinned(path.parent) {
    fun hit(point: LedgerFaultPoint) { faults.hit(LedgerEvent(point,path.parent.fileName.toString(),LedgerPhase.RESERVATION)); valid(check,true) }
    hit(LedgerFaultPoint.BEFORE_CREATE)
    val parent=SecondaryDirectoryHandles.current().parent(path)
    var installed: DomainInventory.Entry? = null
    parent.newByteChannel(path.fileName,setOf(java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE,NOFOLLOW_LINKS)).use { opened ->
      val file=opened as? java.nio.channels.FileChannel ?: throw SecondaryStoreException()
      val creator=DomainInventory.stat(path)
      storeCheck(!creator.directory && creator.size==0L && file.size()==0L)
      hit(LedgerFaultPoint.AFTER_CREATE)
      val first=ByteBuffer.wrap(bytes,0,bytes.size/2)
      while(first.hasRemaining()) storeCheck(file.write(first)>0)
      storeCheck(DomainInventory.stat(path).key==creator.key)
      hit(LedgerFaultPoint.DURING_WRITE)
      val rest=ByteBuffer.wrap(bytes,bytes.size/2,bytes.size-bytes.size/2)
      while(rest.hasRemaining()) storeCheck(file.write(rest)>0)
      hit(LedgerFaultPoint.AFTER_WRITE)
      hit(LedgerFaultPoint.BEFORE_FILE_SYNC)
      file.force(true)
      hit(LedgerFaultPoint.AFTER_FILE_SYNC)
      installed=DomainInventory.stat(path)
      storeCheck(installed!!.key==creator.key && installed!!.size==bytes.size.toLong() && file.size()==bytes.size.toLong())
    }
    val entry=installed ?: throw SecondaryStoreException()
    hit(LedgerFaultPoint.BEFORE_STAGE_DIR_SYNC)
    DurableSecondaryIo.syncDirectory(path.parent)
    hit(LedgerFaultPoint.AFTER_STAGE_DIR_SYNC)
    hit(LedgerFaultPoint.BEFORE_STAGE_REOPEN)
    storeCheck(DomainInventory.stat(path)==entry && read(path,bytes.size).contentEquals(bytes))
    hit(LedgerFaultPoint.AFTER_STAGE_REOPEN)
    hit(LedgerFaultPoint.BEFORE_PARENT_SYNC)
    DurableSecondaryIo.syncDirectory(path.parent)
    hit(LedgerFaultPoint.AFTER_PARENT_SYNC)
    hit(LedgerFaultPoint.BEFORE_CANONICAL_REOPEN)
    storeCheck(DomainInventory.stat(path)==entry && read(path,bytes.size).contentEquals(bytes))
    hit(LedgerFaultPoint.AFTER_CANONICAL_REOPEN)
    storeCheck(DomainInventory.stat(path)==entry && read(path,bytes.size).contentEquals(bytes))
    entry
  }

  /** Exclusive installed pre-GCM charge; a failed partial canonical is never credit. */
  private fun register(id: String, state: LedgerState, check: () -> Unit): DomainInventory.Entry = pinned(usage) {
    fun hit(point: LedgerFaultPoint) { faults.hit(LedgerEvent(point,id,LedgerPhase.REGISTRATION)); valid(check,true) }
    val path = usage.resolve(id)
    val bytes = state.encode()
    hit(LedgerFaultPoint.BEFORE_CREATE)
    val parent = SecondaryDirectoryHandles.current().parent(path)
    var entry: DomainInventory.Entry? = null
    parent.newByteChannel(path.fileName,setOf(java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE,NOFOLLOW_LINKS)).use { opened ->
      val file = opened as? java.nio.channels.FileChannel ?: throw SecondaryStoreException()
      val created = DomainInventory.stat(path)
      storeCheck(!created.directory && created.size == 0L)
      hit(LedgerFaultPoint.AFTER_CREATE)
      val first = ByteBuffer.wrap(bytes,0,bytes.size/2)
      while (first.hasRemaining()) storeCheck(file.write(first) > 0)
      hit(LedgerFaultPoint.DURING_WRITE)
      val rest = ByteBuffer.wrap(bytes,bytes.size/2,bytes.size-bytes.size/2)
      while (rest.hasRemaining()) storeCheck(file.write(rest) > 0)
      hit(LedgerFaultPoint.AFTER_WRITE)
      hit(LedgerFaultPoint.BEFORE_FILE_SYNC)
      file.force(true)
      hit(LedgerFaultPoint.AFTER_FILE_SYNC)
      entry = DomainInventory.stat(path)
      storeCheck(entry!!.key == created.key && entry!!.size == bytes.size.toLong() && file.size() == bytes.size.toLong())
    }
    hit(LedgerFaultPoint.BEFORE_STAGE_DIR_SYNC)
    DurableSecondaryIo.syncDirectory(usage)
    hit(LedgerFaultPoint.AFTER_STAGE_DIR_SYNC)
    hit(LedgerFaultPoint.BEFORE_STAGE_REOPEN)
    storeCheck(DomainInventory.stat(path) == entry && read(path,ledgerSize).contentEquals(bytes))
    hit(LedgerFaultPoint.AFTER_STAGE_REOPEN)
    hit(LedgerFaultPoint.BEFORE_PARENT_SYNC)
    DurableSecondaryIo.syncDirectory(usage)
    hit(LedgerFaultPoint.AFTER_PARENT_SYNC)
    hit(LedgerFaultPoint.BEFORE_CANONICAL_REOPEN)
    storeCheck(DomainInventory.stat(path) == entry && read(path,ledgerSize).contentEquals(bytes))
    hit(LedgerFaultPoint.AFTER_CANONICAL_REOPEN)
    val installed=entry ?: throw SecondaryStoreException()
    storeCheck(DomainInventory.stat(path) == installed && read(path,ledgerSize).contentEquals(bytes))
    installed
  }

  internal class ReservedAttempt private constructor(
    private val store: MediaUsageStore,
    private val id: ByteArray,
    internal val reservation: MediaAttemptReservation,
    private val reservationEntry: DomainInventory.Entry?,
  ) {
    private var used = false
    val attemptId: ByteArray get() = id.copyOf()
    internal fun consume(owner: MediaUsageStore) {
      val path=owner.attempts.resolve(id.hex()).resolve("reservation")
      storeCheck(store === owner && !used && reservationEntry != null && DomainInventory.stat(path) == reservationEntry && owner.read(path,26).contentEquals(reservation.encode()) && owner.freshAttempts.remove(this))
      used = true; owner.claimedAttempts.add(this)
    }
    companion object {
      internal fun create(store: MediaUsageStore, id: ByteArray, reservation: MediaAttemptReservation, entry: DomainInventory.Entry? = null) = ReservedAttempt(store, id.copyOf(), reservation, entry)
    }
  }

  fun reserveAttempt(generation: Long, checkValid: () -> Unit): ReservedAttempt = synchronized(lock) {
    valid(checkValid, true)
    storeCheck(!counterOnly && kind in setOf(Kind.MEDIA,Kind.PRIMARY) && generation > 0)
    val currentAttempts=DomainInventory.children(attempts,16)
    currentAttempts.forEach { storeCheck(it.fileName.toString().matches(Regex("[0-9a-f]{32}")) && DomainInventory.stat(it).directory) }
    storeCheck(currentAttempts.size < 16 && ordinaryCount()+3 <= 8192)
    val id = F1Crypto.random(16)
    val path = attempts.resolve(id.hex())
    pinned(attempts) { DurableSecondaryIo.mkdir(path); DurableSecondaryIo.syncDirectory(attempts) }
    bind(path)
    pinned(path) { DurableSecondaryIo.mkdir(path.resolve("files")); DurableSecondaryIo.syncDirectory(path) }
    val reservation = MediaAttemptReservation.parse(ByteBuffer.allocate(26).putShort(2).put(id).putLong(generation).array())
    val installed=writeNew(path.resolve("reservation"), reservation.encode(), checkValid)
    valid(checkValid, true)
    storeCheck(DomainInventory.stat(path.resolve("reservation")) == installed)
    ReservedAttempt.create(this, id, reservation, installed).also { freshAttempts.add(it) }
  }

  fun sealOwner(master: ByteArray, fresh: ReservedAttempt, owner: MediaAttemptOwner, checkValid: () -> Unit) = withMaster(master) { owned -> sealOwnerBound(owned,fresh,owner,checkValid) }
  private fun sealOwnerBound(master: ByteArray, fresh: ReservedAttempt, owner: MediaAttemptOwner, checkValid: () -> Unit) {
    val ctx = MediaContext(9, fresh.attemptId, 1)
    val output = synchronized(lock) {
      valid(checkValid, true)
      validMaster(master)
      fresh.consume(this)
      owner.validateBinding(fresh.reservation, ctx)
      OwnedMediaOutput.openOwner(this, ctx, fresh.attemptId, checkValid, fresh)
    }
    val bytes = output.use { F1ChargedRecord.sealOwned(master, ctx, owner.encode(), this, fresh.attemptId, output, checkValid) }
    synchronized(lock) {
      val path = attempts.resolve(fresh.attemptId.hex()).resolve("owner")
      val reopened = read(path, 65536 + 172)
      val plaintext = F1ChargedRecord.open(master, ctx, reopened, digest(bytes), this, checkValid)
      try { MediaAttemptOwner.parse(plaintext).validateBinding(fresh.reservation, ctx) } finally { plaintext.fill(0) }
      valid(checkValid, true)
    }
  }

  internal class ReservedCredential private constructor(private val store: MediaUsageStore, token: ByteArray, val generation: Long, internal val reservationEntry: DomainInventory.Entry) {
    private val id=token.copyOf()
    val token: ByteArray get()=id.copyOf()
    internal fun consume(actual: MediaUsageStore) { storeCheck(actual === store && actual.freshCredentials.remove(this)) }
    companion object { internal fun create(store: MediaUsageStore, token: ByteArray, generation: Long, entry: DomainInventory.Entry)=ReservedCredential(store,token,generation,entry) }
  }
  fun reserveCredentialAttempt(generation: Long, check: () -> Unit): ReservedCredential = synchronized(lock) {
    valid(check,true)
    storeCheck(kind == Kind.PROOF && !counterOnly && generation > 0 && ordinaryCount()+2 <= 8192)
    val token=F1Crypto.random(16)
    val parent=root.resolve("transactions"); val dir=parent.resolve(token.hex())
    pinned(parent) { DurableSecondaryIo.mkdir(dir); DurableSecondaryIo.syncDirectory(parent) }
    val reservation=dir.resolve("reservation")
    val installed=writeNew(reservation,ByteBuffer.allocate(10).putShort(1).putLong(generation).array(), check)
    valid(check,true)
    storeCheck(DomainInventory.stat(reservation)==installed)
    ReservedCredential.create(this,token,generation,installed).also { freshCredentials.add(it) }
  }
  private class ProofInputs(val bootstrap: HiddenBootstrap, val catalog: ByteArray, val envelopes: List<ByteArray>, val ownership: List<Path>)
  private fun proofInputs(token: ByteArray): ProofInputs {
    val t=Phase3Bytes(token,16,true).copy().hex()
    val bootstrapPath=root.resolve("descriptor/$t/bootstrap")
    val bootstrap=HiddenBootstrap.parse(read(bootstrapPath,120))
    storeCheck(identity.matches(bootstrap.containerId,bootstrap.masterId) && bootstrap.state==2)
    val catalogPath=root.resolve("index/$t/catalog")
    val catalog=read(catalogPath,12+32*62+172)
    val ownership=mutableListOf(bootstrapPath,catalogPath)
    val envelopes=mutableListOf<ByteArray>()
    listOf("slots/$t","recovery/$t").forEach { relative ->
      val dir=root.resolve(relative); bind(dir)
      DomainInventory.children(dir,32-envelopes.size).forEach { path ->
        storeCheck(path.fileName.toString().matches(Regex("[0-9a-f]{32}")))
        envelopes.add(read(path,204)); ownership.add(path)
      }
    }
    return ProofInputs(bootstrap,catalog,envelopes,ownership)
  }
  fun sealProof(master: ByteArray, fresh: ReservedCredential, body: CredentialProof, check: () -> Unit): ByteArray = withMaster(master) { owned -> sealProofBound(owned,fresh,body,check) }
  private fun sealProofBound(master: ByteArray, fresh: ReservedCredential, body: CredentialProof, check: () -> Unit): ByteArray {
    validMaster(master)
    val prepared=synchronized(lock) {
      valid(check,true); storeCheck(kind == Kind.PROOF && !counterOnly)
      fresh.consume(this)
      val reservation=root.resolve("transactions/"+fresh.token.hex()+"/reservation")
      storeCheck(DomainInventory.stat(reservation)==fresh.reservationEntry && read(reservation,10).contentEquals(ByteBuffer.allocate(10).putShort(1).putLong(fresh.generation).array()))
      val inputs=proofInputs(fresh.token)
      storeCheck(inputs.bootstrap.generation==fresh.generation)
      body.validateBindings(inputs.bootstrap,fresh.token,inputs.catalog,inputs.envelopes)
      val ctx=MediaContext(9,inputs.bootstrap.credentialProofObjectId,fresh.generation)
      val admission=Any().also { liveAdmissions[it]=true }
      ctx to OwnedMediaOutput.openProof(this,ctx,fresh.token,check,admission,inputs.ownership+listOf(reservation))
    }
    val bytes=prepared.second.use { F1ChargedRecord.sealOwned(master,prepared.first,body.encode(),this,fresh.token,it,check) }
    val reopened=synchronized(lock) { readProof(master,fresh.token,check) }
    try { storeCheck(reopened.contentEquals(body.encode())) } finally { reopened.fill(0) }
    return bytes
  }
  private fun readProof(master: ByteArray, token: ByteArray, check: () -> Unit): ByteArray {
    valid(check); validMaster(master); storeCheck(kind == Kind.PROOF && !counterOnly)
    val inputs=proofInputs(token)
    val ctx=MediaContext(9,inputs.bootstrap.credentialProofObjectId,inputs.bootstrap.generation)
    val bytes=read(root.resolve("transactions/proof/anchors/"+token.hex()),2268)
    val state=load(key(bytes))
    storeCheck(state.attemptId.contentEquals(token))
    val plaintext=F1ChargedRecord.open(master,ctx,bytes,state.ciphertextHash,this,check)
    try { CredentialProof.parse(plaintext).validateBindings(inputs.bootstrap,token,inputs.catalog,inputs.envelopes); valid(check); return plaintext }
    catch(failure: Throwable) { plaintext.fill(0); throw failure }
  }
  fun openProof(master: ByteArray, check: () -> Unit): ByteArray = withMaster(master) { owned -> openProofBound(owned,check) }
  private fun openProofBound(master: ByteArray, check: () -> Unit): ByteArray = synchronized(lock) {
    validMaster(master); valid(check)
    val selected=read(root.resolve("selected"),26)
    val token=HiddenMediaSelector.parse(selected).attemptId
    val plaintext=readProof(master,token,check)
    try { storeCheck(read(root.resolve("selected"),26).contentEquals(selected)); plaintext }
    catch(failure: Throwable) { plaintext.fill(0); throw failure }
  }

  internal fun requireOwner(master: ByteArray, attemptId: ByteArray, context: MediaContext, checkValid: () -> Unit): Unit = withMaster(master) { owned -> requireOwnerBound(owned,attemptId,context,checkValid) }
  private fun requireOwnerBound(master: ByteArray, attemptId: ByteArray, context: MediaContext, checkValid: () -> Unit) = synchronized(lock) {
    valid(checkValid, true)
    validMaster(master)
    val path = attempts.resolve(Phase3Bytes(attemptId, 16, true).copy().hex())
    val reservation = MediaAttemptReservation.parse(read(path.resolve("reservation"), 26))
    // The authenticated reservation hash binds targetGeneration; each listed C has its own generation.
    storeCheck(reservation.attemptId.contentEquals(attemptId))
    val bytes = read(path.resolve("owner"), 65536 + 172)
    val ownerContext = MediaContext(9, attemptId, 1)
    val ledger = load(key(bytes))
    val plaintext = F1ChargedRecord.open(master, ownerContext, bytes, ledger.ciphertextHash, this, checkValid)
    try {
      val owner = MediaAttemptOwner.parse(plaintext)
      owner.validateBinding(reservation, ownerContext)
      storeCheck(context in owner.contexts)
    } finally { plaintext.fill(0) }
  }

  internal fun key(header: ByteArray): String {
    storeCheck(header.size >= 156)
    storeCheck(identity.matches(header.copyOfRange(16, 32), header.copyOfRange(32, 48)))
    return digest(header.copyOfRange(12, 104)).hex()
  }

  private fun admitUsage(): Int {
    bind(usage)
    shortCanonicals.forEach { (path,entry) -> storeCheck(DomainInventory.stat(path) == entry) }
    val entries = DomainInventory.children(usage)
    var stages = 0
    val present = mutableSetOf<Path>()
    for (path in entries) {
      val name = path.fileName.toString()
      val entry = DomainInventory.stat(path)
      storeCheck(!entry.directory)
      when {
        Regex("[0-9a-f]{64}").matches(name) -> {
          storeCheck(entry.size in (if(kind == Kind.CREDENTIAL && !allowCredentialShorts) ledgerSize.toLong() else 0L)..ledgerSize.toLong())
          if(entry.size < ledgerSize) {
            val prior=shortCanonicals.putIfAbsent(path,entry)
            storeCheck(prior == null || prior == entry)
          }
        }
        Regex("q[0-9a-f]{32}").matches(name) -> {
          storeCheck(entry.size in 0..ledgerSize.toLong())
          stages++
          val previous = quarantine.putIfAbsent(path, entry)
          storeCheck(previous == null || previous == entry)
          present.add(path)
        }
        else -> throw SecondaryStoreException()
      }
    }
    storeCheck(kind == Kind.CREDENTIAL || stages <= 16)
    quarantine.keys.retainAll(present)
    return stages
  }

  private fun ordinaryCount(): Int {
    var count = 0
    fun visit(path: Path, depth: Int) {
      storeCheck(depth <= 6 && count < 8192)
      count++
      val before = DomainInventory.stat(path)
      if (before.directory) DomainInventory.children(path, 8192 - count).forEach { visit(it, depth + 1) }
      storeCheck(before == DomainInventory.stat(path))
    }
    visit(root, 0)
    return count
  }

  private fun reserveEntry(stage: Boolean) {
    val stages = admitUsage()
    storeCheck(!stage || kind == Kind.CREDENTIAL || stages < 16)
    storeCheck((projectionCount?.invoke() ?: ordinaryCount()) + 1 <= 8192)
  }

  private fun exactCost(header: ByteArray, state: LedgerState) {
    val b = ByteBuffer.wrap(header)
    val purpose = F1Crypto.u16(b, 14)
    val invocation: Long
    val blocks: Long
    when (purpose) {
      10 -> { invocation = 1; blocks = 11 }
      11 -> { val p = F1Video.plan(b.getLong(132)); invocation = p.chunks.toLong(); blocks = p.chunkBlocks }
      else -> { val n = b.getLong(116); storeCheck(n in 0..maximumBodyLength(purpose)); invocation = 1; blocks = 11 + (n + 15) / 16 }
    }
    storeCheck(state.encryptionInvocations == invocation && state.ghashBlocks == blocks)
  }

  private fun chargedAdd(before: Long, cost: Long): Long = try { Math.addExact(before,cost) } catch (_: ArithmeticException) { throw SecondaryStoreException() }
  private fun newPermit(owner: Any, check: () -> Unit, keys: Map<String, Pair<Long, Long>>, hash: ByteArray, encryption: Boolean, pending: Map<String,ByteArray> = emptyMap(), header: ByteArray? = null, physicalLength: Long = 0, entries: Map<String,DomainInventory.Entry> = emptyMap()): Permit =
    Permit.create(this, owner, check, keys, hash, pending, header, physicalLength, entries).also { livePermits[it] = encryption; it.requirePending() }

  private class LedgerState(val encryptionInvocations: Long, val ghashBlocks: Long, val chargedQueries: Long, val chargeSequence: Long, val ciphertextHash: ByteArray, val attemptId: ByteArray, val encoded: ByteArray) {
    fun encode() = encoded.copyOf()
  }
  private fun load(id: String): LedgerState {
    val bytes = read(usage.resolve(id), ledgerSize)
    if (ledgerSize == 82) {
      val parsed = MediaUsage.parse(bytes)
      return LedgerState(parsed.encryptionInvocations, parsed.ghashBlocks, parsed.chargedQueries, parsed.chargeSequence, parsed.ciphertextHash, parsed.attemptId, bytes)
    }
    storeCheck(bytes.size == 58)
    val b = ByteBuffer.wrap(bytes)
    storeCheck(b.getShort(0).toInt() == 1 && b.getLong(2) == 1L && b.getLong(10) in 11..(1L shl 32) && b.getLong(18) in 0..(1L shl 20))
    val hash = bytes.copyOfRange(26,58)
    storeCheck(hash.any { it != 0.toByte() } || (primary58 && b.getLong(18) == 0L))
    return LedgerState(1,b.getLong(10),b.getLong(18),b.getLong(18),hash,ByteArray(16),bytes)
  }
  private fun usage(i: Long, b: Long, q: Long, seq: Long, hash: ByteArray, attempt: ByteArray): LedgerState {
    if (ledgerSize == 82) {
      val parsed = MediaUsage.parse(ByteBuffer.allocate(82).putShort(2).putLong(i).putLong(b).putLong(q).putLong(seq).put(hash).put(attempt).array())
      return LedgerState(i,b,q,seq,hash.copyOf(),attempt.copyOf(),parsed.encode())
    }
    storeCheck(i == 1L && b in 11..(1L shl 32) && q in 0..(1L shl 20) && hash.size == 32 && (hash.any { it != 0.toByte() } || q == 0L))
    return LedgerState(i,b,q,q,hash.copyOf(),attempt.copyOf(),ByteBuffer.allocate(58).putShort(1).putLong(i).putLong(b).putLong(q).put(hash).array())
  }

  private fun replace(id: String, before: LedgerState, after: LedgerState, checkValid: () -> Unit) {
    reserveEntry(true)
    val canonical = usage.resolve(id)
    val targetEntry = DomainInventory.stat(canonical)
    storeCheck(read(canonical, ledgerSize).contentEquals(before.encode()))
    val stagePath = usage.resolve("q" + F1Crypto.random(16).hex())
    var owned: OwnedStage? = null
    var success = false
    fun hit(point: LedgerFaultPoint) { faults.hit(LedgerEvent(point, id, if (before.ciphertextHash.all { it == 0.toByte() }) LedgerPhase.COMPLETION else LedgerPhase.QUERY)); valid(checkValid, before.ciphertextHash.all { it == 0.toByte() }) }
    fun capture(stage: OwnedStage, bytes: ByteArray) {
      val current = DomainInventory.stat(stage.path)
      storeCheck(current.key == stage.key && !current.directory && current.size == bytes.size.toLong())
      stage.entry = current
      stage.bytes = bytes.copyOf()
    }
    try {
      pinned(usage) {
        hit(LedgerFaultPoint.BEFORE_CREATE)
        val parent = SecondaryDirectoryHandles.current().parent(stagePath)
        parent.newByteChannel(stagePath.fileName, setOf(java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE, NOFOLLOW_LINKS)).use { channel ->
          val file = channel as? java.nio.channels.FileChannel ?: throw SecondaryStoreException()
          val e = DomainInventory.stat(stagePath)
          val stage = OwnedStage(stagePath, e.key, e)
          owned = stage
          hit(LedgerFaultPoint.AFTER_CREATE)
          val bytes = after.encode()
          val first = ByteBuffer.wrap(bytes, 0, bytes.size / 2)
          while (first.hasRemaining()) storeCheck(file.write(first) > 0)
          capture(stage, bytes.copyOf(bytes.size / 2))
          hit(LedgerFaultPoint.DURING_WRITE)
          val rest = ByteBuffer.wrap(bytes, bytes.size / 2, bytes.size - bytes.size / 2)
          while (rest.hasRemaining()) storeCheck(file.write(rest) > 0)
          capture(stage, bytes)
          hit(LedgerFaultPoint.AFTER_WRITE)
          hit(LedgerFaultPoint.BEFORE_FILE_SYNC)
          file.force(true)
          hit(LedgerFaultPoint.AFTER_FILE_SYNC)
        }
        hit(LedgerFaultPoint.BEFORE_STAGE_DIR_SYNC)
        DurableSecondaryIo.syncDirectory(usage)
        hit(LedgerFaultPoint.AFTER_STAGE_DIR_SYNC)
        val stage = owned ?: throw SecondaryStoreException()
        hit(LedgerFaultPoint.BEFORE_STAGE_REOPEN)
        storeCheck(DomainInventory.stat(stagePath) == stage.entry && read(stagePath, ledgerSize).contentEquals(after.encode()))
        hit(LedgerFaultPoint.AFTER_STAGE_REOPEN)
        hit(LedgerFaultPoint.BEFORE_REPLACE)
        storeCheck(DomainInventory.stat(stagePath) == stage.entry && DomainInventory.stat(canonical) == targetEntry && read(canonical,ledgerSize).contentEquals(before.encode()))
        stage.replacementAttempted = true
        DurableSecondaryIo.atomicReplace(stagePath, canonical)
        hit(LedgerFaultPoint.AFTER_REPLACE)
        hit(LedgerFaultPoint.BEFORE_PARENT_SYNC)
        DurableSecondaryIo.syncDirectory(usage)
        hit(LedgerFaultPoint.AFTER_PARENT_SYNC)
        hit(LedgerFaultPoint.BEFORE_CANONICAL_REOPEN)
        storeCheck(DomainInventory.stat(canonical).key == stage.key && read(canonical,ledgerSize).contentEquals(after.encode()))
        hit(LedgerFaultPoint.AFTER_CANONICAL_REOPEN)
        success = true
      }
    } finally {
      owned?.let { if (!success && !it.replacementAttempted) ownedFailedStages.add(it) }
    }
  }

  fun discardOwnedUninstalledStages(checkValid: () -> Unit): Int = synchronized(lock) {
    valid(checkValid, true)
    storeCheck(!counterOnly)
    var discarded = 0
    for (stage in ownedFailedStages.toList()) {
      storeCheck(!stage.replacementAttempted && DomainInventory.stat(stage.path) == stage.entry && read(stage.path,ledgerSize).contentEquals(stage.bytes))
      pinned(usage) {
        valid(checkValid, true)
        storeCheck(DomainInventory.stat(stage.path) == stage.entry)
        DurableSecondaryIo.remove(stage.path, false)
        DurableSecondaryIo.syncDirectory(usage)
      }
      quarantine.remove(stage.path)
      ownedFailedStages.remove(stage)
      discarded++
    }
    discarded
  }

  internal class Permit private constructor(
    private val store: MediaUsageStore,
    private val owner: Any,
    private val check: () -> Unit,
    private val keys: Map<String, Pair<Long, Long>>,
    private val hash: ByteArray,
    pending: Map<String,ByteArray>,
    firstHeader: ByteArray?,
    private val physicalLength: Long,
    entries: Map<String,DomainInventory.Entry>,
  ) : AutoCloseable {
    private val remaining = keys.mapValues { it.value.first }.toMutableMap()
    private var closed = false
    private val operationId = F1Crypto.random(16)
    private val pendingStates = pending.mapValues { it.value.copyOf() }
    private val pendingEntries = entries.toMap()
    private val pendingDirectories = if(entries.isEmpty()) emptyMap() else store.directories.filterKeys { store.usage.startsWith(it) }.toMap()
    private val firstHeader = firstHeader?.copyOf()
    internal fun requirePending(id: String? = null) {
      if(pendingEntries.isEmpty()) return // Read leases do no root/storage IO under reader5.
      try {
        fun directories() { pendingDirectories.forEach { (path,key) -> val e=DomainInventory.stat(path); storeCheck(e.directory && e.key==key) } }
        directories()
        val checked=if(id==null) pendingEntries else mapOf(id to (pendingEntries[id] ?: throw SecondaryStoreException()))
        checked.forEach { (key,entry) ->
          val path=store.usage.resolve(key)
          storeCheck(DomainInventory.stat(path)==entry && DomainInventory.read(path,store.ledgerSize).contentEquals(pendingStates[key]))
        }
        directories()
      } catch(failure: Throwable) {
        close(); (owner as? OwnedMediaOutput)?.invalidate()
        if(failure is java.io.IOException) throw SecondaryStoreException()
        throw failure
      }
    }
    internal fun requireOwner(actualStore: MediaUsageStore, actualOwner: Any, actualCheck: () -> Unit) {
      storeCheck(!closed && actualStore === store && actualOwner === owner && actualCheck === check && store.livePermits[this] == true)
      requirePending()
    }
    internal fun requireClaim(actualStore: MediaUsageStore, actualOwner: OwnedMediaOutput, length: Long, header: ByteArray) {
      requireOwner(actualStore,actualOwner,check)
      storeCheck(length == physicalLength && firstHeader != null && firstHeader.contentEquals(header))
      val b=ByteBuffer.wrap(header)
      storeCheck(b.getShort(14).toInt() == actualOwner.context.purpose && header.copyOfRange(48,64).contentEquals(actualOwner.context.objectId) && b.getLong(64) == actualOwner.context.generation)
    }
    internal fun matchesPending(ids: Set<String>): Boolean = pendingStates.keys == ids
    internal fun matchesPending(id: String, bytes: ByteArray): Boolean = pendingStates[id]?.contentEquals(bytes) == true
    fun consume(actualStore: MediaUsageStore, actualOwner: Any, actualCheck: () -> Unit, header: ByteArray) {
      storeCheck(!closed && actualStore === store && actualOwner === owner && actualCheck === check)
      storeCheck(store.livePermits.containsKey(this))
      store.valid(check, store.livePermits[this] == true)
      requirePending()
      val key = store.key(header)
      val left = remaining[key] ?: throw SecondaryStoreException()
      storeCheck(left > 0)
      remaining[key] = left - 1
    }
    fun exhausted(): Boolean = !closed && store.livePermits[this] == true && remaining.values.all { it == 0L }
    override fun close() { closed = true; store.livePermits.remove(this); remaining.clear(); pendingStates.values.forEach { it.fill(0) }; firstHeader?.fill(0); hash.fill(0); operationId.fill(0) }
    companion object {
      internal fun create(store: MediaUsageStore, owner: Any, check: () -> Unit, keys: Map<String, Pair<Long, Long>>, hash: ByteArray, pending: Map<String,ByteArray> = emptyMap(), header: ByteArray? = null, physicalLength: Long = 0, entries: Map<String,DomainInventory.Entry> = emptyMap()) = Permit(store, owner, check, keys.toMap(), hash.copyOf(), pending, header, physicalLength,entries)
    }
  }

  internal fun reserveEncryption(headers: List<ByteArray>, invocations: List<Long>, blocks: List<Long>, attempt: ByteArray, owner: Any, checkValid: () -> Unit): Permit = synchronized(lock) {
    valid(checkValid, true)
    storeCheck(headers.isNotEmpty() && headers.size == invocations.size && headers.size == blocks.size)
    val output = owner as? OwnedMediaOutput ?: throw SecondaryStoreException()
    storeCheck(liveOutputs[output] == true)
    output.checkForWrite(this,output.context,attempt)
    val ids = headers.map(::key)
    storeCheck(ids.distinct().size == ids.size)
    val states = ids.indices.map { usage(invocations[it], blocks[it], 0, 1, ByteArray(32), attempt) }
    states.indices.forEach {
      storeCheck(!primary58 || ByteBuffer.wrap(headers[it]).getShort(14).toInt() in 1..9)
      exactCost(headers[it],states[it])
      val h = headers[it]
      storeCheck(h.copyOfRange(48,64).contentEquals(output.context.objectId) && ByteBuffer.wrap(h).getLong(64) == output.context.generation)
    }
    output.beginRegistration(this,output.context,attempt)
    try {
      val entries=ids.indices.associate { reserveEntry(false); ids[it] to register(ids[it], states[it], checkValid) }
      valid(checkValid, true)
      val first=headers.first()
      val plannedLength=if(ByteBuffer.wrap(first).getShort(14).toInt()==10) F1Video.plan(ByteBuffer.wrap(first).getLong(132)).ciphertextLength else 172L+ByteBuffer.wrap(first).getLong(116)
      newPermit(owner, checkValid, ids.indices.associate { ids[it] to (invocations[it] to states[it].chargeSequence) }, ByteArray(32), true, ids.indices.associate { ids[it] to states[it].encode() }, first, plannedLength,entries)
    } catch(failure: Throwable) { output.invalidate(); throw failure }
  }

  internal fun complete(headers: List<ByteArray>, completion: OwnedMediaOutput.Completion, permit: Permit, checkValid: () -> Unit) = synchronized(lock) {
    valid(checkValid, true)
    val output = liveCompletions.remove(completion) ?: throw SecondaryStoreException()
    storeCheck(completion.permit === permit && liveOutputs[output] == true && completion.output === output && output.context == completion.context)
    permit.requireOwner(this,output,checkValid)
    output.confirmCompletion(completion)
    val hash = completion.hash
    storeCheck(hash.size == 32 && hash.any { it != 0.toByte() } && permit.exhausted())
    storeCheck(permit.matchesPending(headers.map(::key).toSet()))
    headers.forEach { header ->
      val id = key(header)
      permit.requirePending(id)
      val old = load(id)
      storeCheck(old.ciphertextHash.all { it == 0.toByte() } && old.chargedQueries == 0L && permit.matchesPending(id,old.encode()))
      exactCost(header,old)
      replace(id, old, usage(old.encryptionInvocations, old.ghashBlocks, old.chargedQueries, chargedAdd(old.chargeSequence, 1), hash, old.attemptId), { checkValid(); output.confirmCompletion(completion) })
    }
    permit.close()
  }

  internal fun precharge(headers: List<ByteArray>, costs: List<Long>, hash: ByteArray, owner: Any, checkValid: () -> Unit): Permit = synchronized(lock) {
    valid(checkValid)
    storeCheck(headers.isNotEmpty() && headers.size == costs.size && costs.all { it >= 0 } && costs.any { it > 0 })
    val ids = headers.map(::key)
    storeCheck(ids.distinct().size == ids.size)
    admitUsage()
    val old = ids.map(::load)
    old.indices.forEach { exactCost(headers[it], old[it]) }
    val next = old.indices.map { i ->
      storeCheck(old[i].ciphertextHash.contentEquals(hash) && hash.any { it != 0.toByte() })
      val queries=chargedAdd(old[i].chargedQueries,costs[i])
      storeCheck(kind != Kind.PROOF || counterOnly || queries <= (1L shl 20)-64)
      usage(old[i].encryptionInvocations, old[i].ghashBlocks, queries, chargedAdd(old[i].chargeSequence, 1), hash, old[i].attemptId)
    }
    ids.indices.forEach { replace(ids[it], old[it], next[it], checkValid) }
    valid(checkValid)
    newPermit(owner, checkValid, ids.indices.associate { ids[it] to (costs[it] to next[it].chargeSequence) }, hash, false)
  }

  fun openPayload(reference: MediaReference, checkValid: () -> Unit): OwnedMediaCiphertext = synchronized(lock) {
    valid(checkValid)
    storeCheck(kind == Kind.MEDIA && !counterOnly && reference.context.purpose == 10)
    val path = root.resolve("payloads").resolve(reference.context.objectId.hex()).resolve(reference.context.generation.toString(16).padStart(16, '0'))
    bind(path.parent)
    OwnedMediaCiphertext.openSelected(this, reference, checkValid)
  }

  fun openOutput(master: ByteArray, context: MediaContext, attemptID: ByteArray, checkValid: () -> Unit): OwnedMediaOutput = withMaster(master) { owned -> openOutputBound(owned,context,attemptID,checkValid) }
  private fun openOutputBound(master: ByteArray, context: MediaContext, attemptID: ByteArray, checkValid: () -> Unit): OwnedMediaOutput = synchronized(lock) {
    requireOwner(master, attemptID, context, checkValid)
    val admission=Any().also { liveAdmissions[it]=true }
    OwnedMediaOutput.openListed(this, context, attemptID, checkValid, admission)
  }

  private fun outputName(context: MediaContext) = context.purpose.toString(16).padStart(4,'0') + "-" + context.objectId.hex() + "-" + context.generation.toString(16).padStart(16,'0')

  /** Owns a fixed-root opened ciphertext and its persistent no-follow directory handles. */
  internal class OwnedMediaCiphertext private constructor(
    private val store: MediaUsageStore,
    private val boundCheck: () -> Unit,
    private val pinned: SecondaryDirectoryHandles.PinnedDirectories,
    private val path: Path,
    private val entry: DomainInventory.Entry,
    private val channel: SeekableByteChannel,
    val reference: MediaReference,
  ) : AutoCloseable {
    private var closed = false
    internal fun belongsTo(owner: MediaUsageStore): Boolean = store === owner
    internal fun read(offset: Long, length: Int): ByteArray = synchronized(this) {
      storeCheck(!closed && offset >= 0 && length >= 0 && offset <= entry.size - length)
      checkBinding()
      val bytes = ByteArray(length)
      try {
        channel.position(offset)
        val b = ByteBuffer.wrap(bytes)
        while (b.hasRemaining()) storeCheck(channel.read(b) > 0)
        checkBinding()
        bytes
      } catch (failure: Throwable) { bytes.fill(0); throw failure }
    }
    internal fun checkBinding() {
      storeCheck(!closed)
      store.valid(boundCheck)
      pinned.checkBindings()
      val attrs = pinned.open(path.parent).getFileAttributeView(path.fileName, BasicFileAttributeView::class.java, NOFOLLOW_LINKS).readAttributes()
      storeCheck(attrs.isRegularFile && !attrs.isSymbolicLink && attrs.fileKey() == entry.key && attrs.size() == entry.size && attrs.lastModifiedTime().toMillis() == entry.modified)
      storeCheck(DomainInventory.stat(path) == entry && channel.size() == entry.size)
    }
    override fun close() = synchronized(this) { if (!closed) { closed = true; try { channel.close() } finally { pinned.close() } } }
    companion object {
      internal fun openSelected(store: MediaUsageStore, reference: MediaReference, check: () -> Unit): OwnedMediaCiphertext {
        val path = store.root.resolve("payloads").resolve(reference.context.objectId.hex()).resolve(reference.context.generation.toString(16).padStart(16,'0'))
        return openFixed(store, path, reference, check)
      }
      private fun openFixed(store: MediaUsageStore, path: Path, reference: MediaReference, check: () -> Unit): OwnedMediaCiphertext {
        store.valid(check)
        store.bind(path.parent)
        val anchor = store.anchor
        val directories = store.directories.toMap()
        val pinned = SecondaryDirectoryHandles.PinnedDirectories(anchor, directories)
        var channel: SeekableByteChannel? = null
        try {
          val parent = pinned.open(path.parent)
          val before = DomainInventory.stat(path)
          storeCheck(!before.directory && before.size == reference.ciphertextLength)
          channel = parent.newByteChannel(path.fileName, setOf(READ, NOFOLLOW_LINKS))
          val source = OwnedMediaCiphertext(store, check, pinned, path, before, channel, reference)
          source.checkBinding()
          return source
        } catch (failure: Throwable) { channel?.close(); pinned.close(); throw failure }
      }
    }
  }
  /** Only a fixed admitted attempt's newly created, pinned original producer output. */
  internal class OwnedMediaOutput private constructor(
    private val store: MediaUsageStore,
    val context: MediaContext,
    private val attempt: ByteArray,
    private val boundCheck: () -> Unit,
    private val pinned: SecondaryDirectoryHandles.PinnedDirectories,
    private val path: Path,
    private val key: Any,
    private val channel: java.nio.channels.FileChannel,
    private val ownership: Map<Path, DomainInventory.Entry>,
  ) : java.io.OutputStream(), AutoCloseable {
    private var closed = false
    private var aborted = false
    private var registrationStarted = false
    private var claimed = false
    private var finished = false
    private var finishedEntry: DomainInventory.Entry? = null
    private var finishedModified: java.nio.file.attribute.FileTime? = null
    private var expectedLength = 0L
    private var written = 0L
    private var permit: Permit? = null
    private var header = byteArrayOf()
    internal fun checkForWrite(owner: MediaUsageStore, expected: MediaContext, attemptID: ByteArray) {
      storeCheck(!registrationStarted)
      unclaimed(owner,expected,attemptID)
      binding()
      storeCheck(!registrationStarted)
      unclaimed(owner,expected,attemptID)
    }
    private fun unclaimed(owner: MediaUsageStore, expected: MediaContext, attemptID: ByteArray) {
      storeCheck(owner === store && context == expected && attempt.contentEquals(attemptID) && !closed && !aborted && !claimed && written == 0L && store.liveOutputs[this] == true)
    }
    internal fun beginRegistration(owner: MediaUsageStore, expected: MediaContext, attemptID: ByteArray) = synchronized(this) {
      storeCheck(!registrationStarted)
      unclaimed(owner,expected,attemptID)
      registrationStarted=true
      try { binding() } catch(failure: Throwable) { invalidate(); throw failure }
    }
    internal fun claim(owner: MediaUsageStore, expected: MediaContext, attemptID: ByteArray, grant: Permit, physicalLength: Long, firstHeader: ByteArray) = synchronized(this) {
      storeCheck(registrationStarted)
      unclaimed(owner,expected,attemptID)
      binding()
      grant.requireClaim(store,this,physicalLength,firstHeader)
      claimed = true
      permit = grant
      expectedLength = physicalLength
      header = firstHeader.copyOf()
    }
    private fun binding() {
      storeCheck(!closed && !aborted)
      store.valid(boundCheck, true)
      storeCheck(!closed && !aborted)
      pinned.checkBindings()
      ownership.forEach { (file,e) -> storeCheck(DomainInventory.stat(file) == e) }
      val e = DomainInventory.stat(path)
      storeCheck(!e.directory && e.key == key && e.size == written && channel.size() == written)
      if(finished) {
        storeCheck(e == finishedEntry && java.nio.file.Files.getLastModifiedTime(path,NOFOLLOW_LINKS) == finishedModified)
      }
    }
    override fun write(value: Int) = write(byteArrayOf(value.toByte()),0,1)
    override fun write(bytes: ByteArray, offset: Int, length: Int) = synchronized(this) {
      storeCheck(claimed && !finished && offset >= 0 && length >= 0 && offset <= bytes.size - length && written <= expectedLength - length)
      binding()
      val buffer = ByteBuffer.wrap(bytes,offset,length)
      while (buffer.hasRemaining()) storeCheck(channel.write(buffer) > 0)
      written += length
      binding()
    }
    internal fun finish(expectedHash: ByteArray): Completion = synchronized(this) {
      binding()
      val grant = permit ?: throw SecondaryStoreException()
      storeCheck(claimed && !finished && grant.exhausted() && written == expectedLength)
      fun hit(point: LedgerFaultPoint) { store.faults.hit(LedgerEvent(point,store.key(header),LedgerPhase.OUTPUT)); binding() }
      hit(LedgerFaultPoint.BEFORE_FILE_SYNC)
      channel.force(true)
      hit(LedgerFaultPoint.AFTER_FILE_SYNC)
      hit(LedgerFaultPoint.BEFORE_PARENT_SYNC)
      pinned.syncDirectory(path.parent)
      hit(LedgerFaultPoint.AFTER_PARENT_SYNC)
      hit(LedgerFaultPoint.BEFORE_CANONICAL_REOPEN)
      binding()
      val before = DomainInventory.stat(path)
      val sha = java.security.MessageDigest.getInstance("SHA-256")
      pinned.open(path.parent).newByteChannel(path.fileName,setOf(READ,NOFOLLOW_LINKS)).use { reopened ->
        storeCheck(DomainInventory.stat(path) == before && before.key == key && reopened.size() == expectedLength)
        val chunk = ByteArray(65536)
        try {
          var read = 0L
          while (read < expectedLength) {
            val count = minOf(chunk.size.toLong(), expectedLength - read).toInt()
            val b = ByteBuffer.wrap(chunk,0,count)
            while (b.hasRemaining()) storeCheck(reopened.read(b) > 0)
            if (read == 0L) storeCheck(chunk.copyOfRange(0,156).contentEquals(header))
            sha.update(chunk,0,count)
            read += count
          }
          storeCheck(reopened.read(ByteBuffer.allocate(1)) == -1)
        } finally { chunk.fill(0) }
      }
      binding()
      storeCheck(DomainInventory.stat(path) == before)
      val hash = sha.digest()
      storeCheck(java.security.MessageDigest.isEqual(hash,expectedHash))
      hit(LedgerFaultPoint.AFTER_CANONICAL_REOPEN)
      finishedEntry=before
      finishedModified=java.nio.file.Files.getLastModifiedTime(path,NOFOLLOW_LINKS)
      finished = true
      Completion.create(this,context,hash,grant).also { store.liveCompletions[it] = this }
    }
    internal fun confirmCompletion(completion: Completion) = synchronized(this) { storeCheck(finished && completion.output === this); binding() }
    internal class Completion private constructor(internal val output: OwnedMediaOutput, internal val context: MediaContext, hash: ByteArray, internal val permit: Permit) {
      private val sha = hash.copyOf()
      internal val hash: ByteArray get() = sha.copyOf()
      companion object { internal fun create(output: OwnedMediaOutput, context: MediaContext, hash: ByteArray, permit: Permit) = Completion(output,context,hash,permit) }
    }
    /** Admission-only abort under storage; original caller still owns physical close outside gates. */
    internal fun invalidate() = synchronized(this) {
      if(!aborted) {
        aborted=true
        store.liveOutputs.remove(this)
        store.liveCompletions.entries.removeIf { it.value === this }
        header.fill(0)
        permit?.close()
      }
    }
    override fun close() = synchronized(this) {
      if (!closed) {
        closed = true
        invalidate()
        try { channel.close() } finally { pinned.close() }
      }
    }
    companion object {
      internal fun openListed(store: MediaUsageStore, context: MediaContext, attemptID: ByteArray, check: () -> Unit, admission: Any? = null): OwnedMediaOutput {
        storeCheck(admission != null && store.liveAdmissions.remove(admission) == true)
        val attempt = store.attempts.resolve(Phase3Bytes(attemptID,16,true).copy().hex())
        val path = attempt.resolve("files").resolve(store.outputName(context))
        return openFixed(store,context,attemptID,check,path,listOf(attempt.resolve("reservation"),attempt.resolve("owner")))
      }
      internal fun openOwner(store: MediaUsageStore, context: MediaContext, attemptID: ByteArray, check: () -> Unit, fresh: ReservedAttempt? = null): OwnedMediaOutput {
        storeCheck(fresh != null && store.claimedAttempts.remove(fresh) && fresh.attemptId.contentEquals(attemptID))
        val attempt = store.attempts.resolve(Phase3Bytes(attemptID,16,true).copy().hex())
        storeCheck(context.purpose == 9 && context.generation == 1L && context.objectId.contentEquals(attemptID))
        return openFixed(store,context,attemptID,check,attempt.resolve("owner"),listOf(attempt.resolve("reservation")))
      }
      internal fun openProof(store: MediaUsageStore, context: MediaContext, token: ByteArray, check: () -> Unit, admission: Any?, ownership: List<Path>): OwnedMediaOutput {
        storeCheck(store.kind == Kind.PROOF && admission != null && store.liveAdmissions.remove(admission)==true)
        return openFixed(store,context,token,check,store.root.resolve("transactions/proof/anchors/"+token.hex()),ownership)
      }
      private fun openFixed(store: MediaUsageStore, context: MediaContext, attemptID: ByteArray, check: () -> Unit, path: Path, ownershipPaths: List<Path>): OwnedMediaOutput {
        store.valid(check,true)
        storeCheck(store.ordinaryCount() + 1 <= 8192)
        store.bind(path.parent)
        val owned = ownershipPaths.associateWith(DomainInventory::stat)
        val pinned = SecondaryDirectoryHandles.PinnedDirectories(store.anchor,store.directories.toMap())
        var channel: java.nio.channels.FileChannel? = null
        try {
          val parent = pinned.open(path.parent)
          channel = parent.newByteChannel(path.fileName,setOf(java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE,NOFOLLOW_LINKS)) as? java.nio.channels.FileChannel ?: throw SecondaryStoreException()
          val e = DomainInventory.stat(path)
          storeCheck(!e.directory && e.size == 0L && channel.size() == 0L)
          return OwnedMediaOutput(store,context,attemptID.copyOf(),check,pinned,path,e.key,channel,owned).also { store.liveOutputs[it] = true }
        } catch (failure: Throwable) { channel?.close(); pinned.close(); throw failure }
      }
    }
  }

  /** Charges fixed selected counters only. No crypto, lease, cleanup or authentication authority. */
  internal class CounterService private constructor(private val credentials: MediaUsageStore, private val proof: MediaUsageStore?, private val restricted: Boolean) {
    private val root = credentials.root
    private val identities = mutableMapOf<Path,Any>()
    private fun token(): ByteArray = HiddenMediaSelector.parse(credentials.read(root.resolve("selected"),26)).attemptId
    fun checkedProjectionEntries(check: () -> Unit): Int = synchronized(credentials.lock) { credentials.valid(check); projection() }
    private val shortEntries=linkedMapOf<Path,DomainInventory.Entry>()
    private val fixedFiles = mutableMapOf<Path,DomainInventory.Entry>()
    private fun projection(): Int {
      shortEntries.forEach { (path,entry) -> storeCheck(DomainInventory.stat(path) == entry) }
      val selected=token()
      val t=selected.hex()
      val entries=linkedMapOf<Path,DomainInventory.Entry>()
      val opaque=mutableSetOf<Path>()
      fun opaqueStat(path: Path): DomainInventory.Entry {
        val attrs=java.nio.file.Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes::class.java,NOFOLLOW_LINKS)
        storeCheck(!attrs.isSymbolicLink && (attrs.isDirectory || attrs.isRegularFile))
        return DomainInventory.Entry(attrs.fileKey() ?: throw SecondaryStoreException(),attrs.isDirectory,attrs.size(),attrs.lastModifiedTime().toMillis())
      }
      fun add(path: Path, isOpaque: Boolean=false, fixed: Boolean=false) {
        storeCheck(path.startsWith(root) && root.relativize(path).nameCount <= 6)
        if(path !in entries) {
          storeCheck(entries.size < 8192)
          val entry=if(isOpaque) opaqueStat(path) else DomainInventory.stat(path)
          entries[path]=entry
          if(isOpaque) opaque.add(path)
          if(entry.directory || fixed) {
            val prior=identities.putIfAbsent(path,entry.key)
            storeCheck(prior == null || prior == entry.key)
            if(!isOpaque && entry.directory) credentials.bind(path)
          }
          if(fixed && !entry.directory) { val prior=fixedFiles.putIfAbsent(path,entry); storeCheck(prior == null || prior == entry) }
        }
      }
      fun ancestors(path: Path) {
        val chain=mutableListOf<Path>(); var p=path.parent
        while(p != null && p.startsWith(root)) { chain.add(p); p=p.parent }
        chain.asReversed().forEach { add(it); storeCheck(entries[it]!!.directory) }
      }
      add(root); storeCheck(entries[root]!!.directory)
      val rootChildren=DomainInventory.children(root,8192-entries.size)
      val known=setOf("descriptor","slots","recovery","index","payloads","previews","transactions","temporary","deleted")
      val names=rootChildren.map { it.fileName.toString() }.toSet()
      storeCheck(names-setOf("selected","retirement") == known && "selected" in names)
      rootChildren.forEach { path ->
        val name=path.fileName.toString()
        add(path,name in setOf("payloads","previews","temporary","deleted"),true)
        storeCheck(entries[path]!!.directory == (name in known))
      }
      val selectedFiles=listOf("descriptor/$t/bootstrap" to 120,"index/$t/catalog" to (12+32*62+172),"transactions/proof/anchors/$t" to 2268)
      selectedFiles.forEach { (relative,bound) ->
        val path=root.resolve(relative); ancestors(path); add(path,fixed=true)
        val entry=entries[path]!!
        storeCheck(!entry.directory && entry.size in (if(bound==120) 120L else 172L)..bound.toLong())
      }
      val bootstrap=HiddenBootstrap.parse(credentials.read(root.resolve("descriptor/$t/bootstrap"),120))
      storeCheck(bootstrap.state == 2 && credentials.identity.matches(bootstrap.containerId,bootstrap.masterId))
      var envelopes=0
      listOf("slots/$t","recovery/$t").forEach { relative ->
        val dir=root.resolve(relative); ancestors(dir); add(dir); storeCheck(entries[dir]!!.directory)
        DomainInventory.children(dir,minOf(32-envelopes,8192-entries.size)).forEach { path ->
          storeCheck(path.fileName.toString().matches(Regex("[0-9a-f]{32}")))
          add(path,fixed=true); storeCheck(!entries[path]!!.directory && entries[path]!!.size == 204L)
          envelopes++
        }
      }
      listOf("transactions/usage" to 58,"transactions/proof/usage" to 82).forEach { (relative,size) ->
        val dir=root.resolve(relative); ancestors(dir); add(dir); storeCheck(entries[dir]!!.directory)
        var stages=0
        DomainInventory.children(dir,8192-entries.size).forEach { path ->
          add(path); val entry=entries[path]!!; storeCheck(!entry.directory)
          when {
            path.fileName.toString().matches(Regex("[0-9a-f]{64}")) -> {
              storeCheck(entry.size in 0..size.toLong())
              if(entry.size < size) {
                val prior=shortEntries.putIfAbsent(path,entry)
                storeCheck(prior == null || prior == entry)
              }
            }
            path.fileName.toString().matches(Regex("q[0-9a-f]{32}")) -> {
              storeCheck(entry.size in 0..size.toLong()); stages++
              val prior=identities.putIfAbsent(path,entry.key); storeCheck(prior == null || prior == entry.key)
            }
            else -> throw SecondaryStoreException()
          }
        }
        storeCheck(size == 58 || stages <= 16)
      }
      val proofPath=root.resolve("transactions/proof/anchors/$t")
      val proofBytes=credentials.read(proofPath,2268)
      storeCheck(proofBytes.size >= 172)
      val b=ByteBuffer.wrap(proofBytes)
      storeCheck(b.getShort(14).toInt()==9 && proofBytes.copyOfRange(48,64).contentEquals(bootstrap.credentialProofObjectId) && b.getLong(64)==bootstrap.generation)
      entries.forEach { (path,entry) ->
        val after=if(path in opaque) opaqueStat(path) else DomainInventory.stat(path)
        storeCheck(if(path in opaque) after.key == entry.key && after.directory == entry.directory else after == entry)
      }
      storeCheck(token().contentEquals(selected))
      return entries.size
    }

    fun chargeSelectedSlot(slotId: ByteArray, recovery: Boolean, check: () -> Unit): Unit = synchronized(credentials.lock) {
      credentials.valid(check)
      if(restricted) projection()
      val selected=token()
      val path=root.resolve(if (recovery) "recovery" else "slots").resolve(selected.hex()).resolve(Phase3Bytes(slotId,16,true).copy().hex())
      val bytes=credentials.read(path,204)
      if(!restricted) {
        val bootstrap=credentials.read(root.resolve("descriptor/"+selected.hex()+"/bootstrap"),120)
        credentials.allowCredentialShorts=bootstrap.size==120
        val identity=if(bootstrap.size==120) HiddenBootstrap.parse(bootstrap).let { DomainIdentity(it.containerId,it.masterId) } else DomainEncoding.parseBootstrap(bootstrap).first
        storeCheck(credentials.identity.matches(identity.container,identity.master))
      }
      val metadata=F1Slot.inspect(credentials.identity,bytes)
      storeCheck(metadata.slotId.contentEquals(slotId) && metadata.slotType == if(recovery) 2 else 1)
      storeCheck(!recovery || metadata.recoveryState == 2)
      val id=digest(bytes.copyOfRange(12,70)+bytes.copyOfRange(72,74)+bytes.copyOfRange(92,124)).hex()
      charge(credentials,id,bytes,selected,check,13L)
    }
    fun chargeSelectedProof(check: () -> Unit): Unit = synchronized(credentials.lock) {
      credentials.valid(check)
      val store=proof ?: throw SecondaryStoreException()
      if(restricted) projection()
      val selected=token()
      val bytes=store.read(root.resolve("transactions/proof/anchors").resolve(selected.hex()),2268)
      val header=bytes.copyOfRange(0,156)
      val state=store.load(store.key(header))
      store.exactCost(header,state)
      storeCheck(state.attemptId.contentEquals(selected))
      charge(store,store.key(header),bytes,selected,check,state.ghashBlocks)
    }
    private fun charge(store: MediaUsageStore, id: String, bytes: ByteArray, selected: ByteArray, check: () -> Unit, blocks: Long) {
      store.valid(check)
      val old=store.load(id)
      storeCheck(old.encryptionInvocations==1L && old.ghashBlocks==blocks && old.ciphertextHash.contentEquals(digest(bytes)))
      storeCheck(old.chargedQueries < (1L shl 20) - if(restricted) 0 else 64)
      val next=store.usage(1,blocks,old.chargedQueries+1,store.chargedAdd(old.chargeSequence,1),old.ciphertextHash,old.attemptId)
      store.replace(id,old,next,check)
      store.valid(check)
      storeCheck(token().contentEquals(selected))
    }
    companion object {
      internal fun create(credentials: MediaUsageStore, proof: MediaUsageStore?, restricted: Boolean): CounterService {
        storeCheck(credentials.kind == Kind.CREDENTIAL && credentials.counterOnly)
        storeCheck(proof == null || (proof.kind == Kind.PROOF && proof.counterOnly && proof.original === credentials.original))
        storeCheck(!restricted || (proof != null && (credentials.original as? OriginalGate.Authentication)?.restricted == true))
        return CounterService(credentials,proof,restricted).also { service ->
        if(restricted) { credentials.allowCredentialShorts=true; credentials.projectionCount=service::projection; proof?.projectionCount=service::projection }
        }
      }
    }
  }

  private sealed interface OriginalGate {
    fun valid(write: Boolean)
    fun master(bytes: ByteArray): ByteArray
    fun credentials() { throw SecondaryStoreException() }
    class Hidden(private val op: SecondaryOperation): OriginalGate {
      override fun credentials() { op.requireScope(SecondaryScope.CREDENTIALS) }
      override fun valid(write: Boolean) { op.requireScope(if (write) SecondaryScope.WRITE else SecondaryScope.READ); op.checkValid() }
      override fun master(bytes: ByteArray): ByteArray { return op.commit { storeCheck(bytes.size == 32 && java.security.MessageDigest.isEqual(bytes,op.key)); op.key.copyOf() } }
    }
    class Authentication(private val authority: SecondarySessionAuthority, private val attempt: SecondaryAuthAttempt, private val primary: uk.co.traynor.privategallery.core.security.PrimaryOperation?): OriginalGate {
      val restricted: Boolean get() = primary != null
      override fun valid(write: Boolean) {
        storeCheck(!write)
        primary?.requireScope(uk.co.traynor.privategallery.core.security.PrimaryScope.HOLD_RESTORE)
        authority.checkAuthentication(attempt)
      }
      override fun master(bytes: ByteArray): ByteArray { throw SecondaryStoreException() }
    }
    class Primary(private val op: uk.co.traynor.privategallery.core.security.PrimaryOperation): OriginalGate {
      override fun valid(write: Boolean) { op.requireScope(if (write) uk.co.traynor.privategallery.core.security.PrimaryScope.WRITE else uk.co.traynor.privategallery.core.security.PrimaryScope.READ); op.checkValid() }
      override fun master(bytes: ByteArray): ByteArray { return op.commit { storeCheck(bytes.size == 32 && java.security.MessageDigest.isEqual(bytes,op.key)); op.key.copyOf() } }
    }
  }

  companion object {
    fun hiddenMedia(filesDir: File, identity: DomainIdentity, original: SecondaryOperation, faults: LedgerFaults = LedgerFaults.NONE): MediaUsageStore = MediaUsageStore(filesDir, identity, OriginalGate.Hidden(original), Kind.MEDIA, faults)
    fun hiddenProof(filesDir: File, identity: DomainIdentity, original: SecondaryOperation, faults: LedgerFaults = LedgerFaults.NONE): MediaUsageStore = MediaUsageStore(filesDir,identity,OriginalGate.Hidden(original),Kind.PROOF,faults)
    fun restrictedCounters(filesDir: File, identity: DomainIdentity, primary: uk.co.traynor.privategallery.core.security.PrimaryOperation, authority: SecondarySessionAuthority, attempt: SecondaryAuthAttempt, faults: LedgerFaults = LedgerFaults.NONE): CounterService {
      val gate=OriginalGate.Authentication(authority,attempt,primary)
      return CounterService.create(MediaUsageStore(filesDir,identity,gate,Kind.CREDENTIAL,faults),MediaUsageStore(filesDir,identity,gate,Kind.PROOF,faults),true)
    }
    fun normalCredentialCounters(filesDir: File, identity: DomainIdentity, authority: SecondarySessionAuthority, attempt: SecondaryAuthAttempt, faults: LedgerFaults = LedgerFaults.NONE): CounterService = CounterService.create(MediaUsageStore(filesDir,identity,OriginalGate.Authentication(authority,attempt,null),Kind.CREDENTIAL,faults),null,false)
    fun primaryTransfer(filesDir: File, identity: DomainIdentity, original: uk.co.traynor.privategallery.core.security.PrimaryOperation, faults: LedgerFaults = LedgerFaults.NONE): MediaUsageStore = MediaUsageStore(filesDir, identity, OriginalGate.Primary(original), Kind.PRIMARY, faults)
  }
}

/** Internal faults surround actual pinned IO, never replace accounting or cryptography. */
internal enum class LedgerFaultPoint {
  BEFORE_CREATE, AFTER_CREATE, DURING_WRITE, AFTER_WRITE, BEFORE_FILE_SYNC, AFTER_FILE_SYNC,
  BEFORE_STAGE_DIR_SYNC, AFTER_STAGE_DIR_SYNC, BEFORE_STAGE_REOPEN, AFTER_STAGE_REOPEN,
  BEFORE_REPLACE, AFTER_REPLACE, BEFORE_PARENT_SYNC, AFTER_PARENT_SYNC,
  BEFORE_CANONICAL_REOPEN, AFTER_CANONICAL_REOPEN,
}
internal enum class LedgerPhase { QUERY, COMPLETION, REGISTRATION, OUTPUT, RESERVATION }
internal data class LedgerEvent(val point: LedgerFaultPoint, val keyId: String, val phase: LedgerPhase = LedgerPhase.QUERY)
internal fun interface LedgerFaults {
  fun hit(event: LedgerEvent)
  companion object { val NONE = LedgerFaults {} }
}

internal typealias OwnedMediaCiphertext = MediaUsageStore.OwnedMediaCiphertext
internal typealias OwnedMediaOutput = MediaUsageStore.OwnedMediaOutput
