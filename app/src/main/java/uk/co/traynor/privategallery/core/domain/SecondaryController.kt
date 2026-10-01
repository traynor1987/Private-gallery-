package uk.co.traynor.privategallery.core.domain

import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executor
import javax.crypto.Cipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Public route state contains no credentials, keys, recovery material, object metadata or paths. */
enum class SecondaryRoute { CLOSED, CHECKING, SETUP, PIN, RECOVERY_DISPLAY, RECOVERY_CONFIRM, RECOVERY_AUTH, RESET_PIN, READY, UNAVAILABLE }
enum class SecondaryUiError { UNAVAILABLE, AUTHENTICATION, PIN_POLICY, RETRY_LATER }
data class SecondaryUiState(
  val route: SecondaryRoute = SecondaryRoute.CLOSED,
  val busy: Boolean = false,
  val error: SecondaryUiError? = null,
  val retryMillis: Long = 0,
  val biometricAvailable: Boolean = false,
  val biometricEnabled: Boolean = false,
  val strongAuthInterval: StrongAuthInterval = StrongAuthInterval.DAY,
  val autoLock: SecondaryAutoLock = SecondaryAutoLock.IMMEDIATE,
  val biometricRequestId: UUID? = null,
)

/** Transient CryptoObject request; possession of this object is not authentication. */
class SecondaryBiometricRequest internal constructor(
  val cipher: Cipher,
  internal val attempt: SecondaryAuthAttempt?,
  internal val operation: SecondaryOperation?,
  internal val record: SecondaryBiometricRecord?,
  internal val enrollment: SecondaryBiometricSlot.PendingEnrollment?,
  internal val unlock: SecondaryBiometricSlot.PendingUnlock?,
  internal val slotId: ByteArray,
  internal val generation: Long,
) : AutoCloseable {
  val id: UUID = UUID.randomUUID()
  internal var delivered = false
  internal var submitted = false
  @Synchronized override fun close() { enrollment?.close(); unlock?.close(); operation?.close(); slotId.fill(0) }
}

/**
 * A fixed-domain, in-process controller. Supply a serial worker executor, never the UI executor.
 * Android must inject elapsedRealtime into authority/policy. No state here is serializable authority.
 * All mutable input arrays are consumed and wiped, including rejected/queued work.
 * Lifecycle invalidation is synchronous; workers retain original attempt/epoch guards.
 */
class SecondaryController(
  private val store: SecondaryStore,
  private val authority: SecondarySessionAuthority,
  private val policy: SecondaryAuthPolicy,
  private val biometric: SecondaryBiometricSlot,
  private val executor: Executor,
  private val isPrimaryPin: (CharArray) -> Boolean,
) : AutoCloseable {
  private val gate = Any()
  private val mutableState = MutableStateFlow(SecondaryUiState())
  val state: StateFlow<SecondaryUiState> = mutableState.asStateFlow()
  private var sequence = 0L
  private var attempt: SecondaryAuthAttempt? = null
  private var pending: PendingSetup? = null
  private var displayAvailable = false
  private var displayedRecovery: CharArray? = null
  private var request: SecondaryBiometricRequest? = null
  private var identity: DomainIdentity? = null
  private var disposed = false
  private var resumeShell = false
  private var resumeBiometricEnabled = false
  private var usedChallenge: DiscoveryChallenge? = null

  fun discover(challenge: DiscoveryChallenge) {
    val token = synchronized(gate) {
      if (disposed || usedChallenge === challenge || mutableState.value.route != SecondaryRoute.CLOSED) return
      usedChallenge = challenge
      invalidateLocked()
      attempt = authority.beginAuthentication()
      mutableState.value = SecondaryUiState(SecondaryRoute.CHECKING, busy = true)
      sequence
    }
    submit(token, SecondaryRoute.UNAVAILABLE) {
      val result = store.preflight()
      val eligible = result == SecondaryPreflight.READY && store.biometricRecord() != null && policy.canUseBiometric()
      publish(token) {
        val route = when (result) {
          SecondaryPreflight.FRESH -> SecondaryRoute.SETUP
          SecondaryPreflight.READY, SecondaryPreflight.PENDING -> SecondaryRoute.PIN
          SecondaryPreflight.UNAVAILABLE -> SecondaryRoute.UNAVAILABLE
        }
        mutableState.value = SecondaryUiState(route, biometricAvailable = eligible)
        if (eligible) prepareBiometricUnlock()
      }
    }
  }

  fun setup(pin: CharArray) = pinAction(pin, setOf(SecondaryRoute.SETUP)) { token, auth ->
    requireIndependentPin(pin)
    val created = store.create(pin, { authority.checkAuthentication(auth) }, { promotion -> guardedCommit(token, auth, promotion) })
    acceptPending(token, created)
  }

  fun unlock(pin: CharArray) = pinAction(pin, setOf(SecondaryRoute.PIN), enforceRate = true) { token, auth ->
    if (store.preflight() == SecondaryPreflight.PENDING) {
      val resumed = store.resumePending(pin, { authority.checkAuthentication(auth) }, { promotion -> guardedCommit(token, auth, promotion) })
      acceptPending(token, resumed)
    } else {
      store.authenticatePin(pin, { authority.checkAuthentication(auth) }, { action -> guardedCommit(token,auth,action) }).use { authenticated ->
        check(authenticated.confirmed)
        promote(token, auth, authenticated, pinSuccess = true)
      }
    }
  }

  fun beginRecovery() = synchronized(gate) {
    if (mutableState.value.route != SecondaryRoute.PIN || mutableState.value.busy) return@synchronized
    closeRequestLocked()
    mutableState.value = mutableState.value.copy(route = SecondaryRoute.RECOVERY_AUTH, error = null, biometricAvailable = false, biometricRequestId = null)
  }

  fun recover(secret: ByteArray) {
    val admitted = begin(setOf(SecondaryRoute.RECOVERY_AUTH), true)
    if (admitted == null) { secret.fill(0); return }
    val (token, auth) = admitted
    submit(token, SecondaryRoute.RECOVERY_AUTH, true, { secret.fill(0) }) {
      store.authenticateRecovery(secret, { authority.checkAuthentication(auth) }, { action -> guardedCommit(token,auth,action) }).use { authenticated ->
        promote(token, auth, authenticated, pinSuccess = false, route = SecondaryRoute.RESET_PIN)
      }
    }
  }

  /** A one-shot mutable display copy. Caller must wipe it on dismissal/background/disposal. */
  fun takeRecoveryDisplay(): CharArray? = synchronized(gate) {
    if (!displayAvailable || mutableState.value.route != SecondaryRoute.RECOVERY_DISPLAY) return@synchronized null
    displayAvailable = false
    val bytes = pending?.takeRecoverySecret() ?: return@synchronized null
    try {
      val digits = "0123456789abcdef"
      CharArray(bytes.size * 2) { index -> digits[if (index % 2 == 0) (bytes[index / 2].toInt() and 255) ushr 4 else bytes[index / 2].toInt() and 15] }.also { displayedRecovery = it }
    } finally { bytes.fill(0) }
  }

  fun acknowledgeRecoveryDisplay() = synchronized(gate) {
    if (mutableState.value.route == SecondaryRoute.RECOVERY_DISPLAY && !mutableState.value.busy) {
      clearRecoveryDisplayLocked()
      pending?.discardRecoverySecret()
      mutableState.value = mutableState.value.copy(route = SecondaryRoute.RECOVERY_CONFIRM, error = null)
    }
  }

  fun confirmRecovery(secret: ByteArray) {
    val captured = synchronized(gate) {
      if (disposed || mutableState.value.route != SecondaryRoute.RECOVERY_CONFIRM || mutableState.value.busy || !policy.canAttempt()) {
        secret.fill(0); return
      }
      val candidate = pending ?: run { secret.fill(0); return }
      mutableState.value = mutableState.value.copy(busy = true, error = null)
      Triple(sequence, candidate, attempt)
    }
    val (token, candidate, auth) = captured
    submit(token, SecondaryRoute.RECOVERY_CONFIRM, true, { secret.fill(0) }) {
      if (candidate.replacement) {
        val operation = originalOperation(token, setOf(SecondaryScope.RECOVERY))
        operation.use {
          store.confirmReplacement(it, candidate, secret)
          publish(token) {
            it.publish {
              pending = null; policy.onSecurityChanged()
              mutableState.value = readyState(biometricEnabled = false)
            }
          }
        }
      } else {
        checkNotNull(auth)
        store.confirm(candidate, secret, { authority.checkAuthentication(auth) }, { promotion -> guardedCommit(token, auth, promotion) }).use {
          promote(token, auth, it, pinSuccess = true)
        }
      }
    }
  }

  fun resetPin(pin: CharArray) = mutatePin(pin, setOf(SecondaryRoute.RESET_PIN), finishRecovery = true)
  fun changePin(pin: CharArray) = mutatePin(pin, setOf(SecondaryRoute.READY), finishRecovery = false)

  private fun mutatePin(pin: CharArray, routes: Set<SecondaryRoute>, finishRecovery: Boolean) {
    mutate(routes, setOf(SecondaryScope.CREDENTIALS), { pin.fill('\u0000') }) { token, operation ->
      requireIndependentPin(pin)
      store.changePin(operation, pin)
      publish(token) { operation.publish {
        policy.onSecurityChanged()
        // A recovery-authorized reset must return to discovery/PIN, never grant convenience recency.
        if (finishRecovery) { invalidateLocked(); mutableState.value = SecondaryUiState() }
        else mutableState.value = readyState(biometricEnabled = mutableState.value.biometricEnabled)
      } }
    }
  }

  fun replaceRecovery() = mutate(setOf(SecondaryRoute.READY), setOf(SecondaryScope.RECOVERY)) { token, operation ->
    val created = store.replaceRecovery(operation)
    acceptPending(token, created)
    policy.onSecurityChanged()
  }

  fun updateSettings(strong: StrongAuthInterval, autoLock: SecondaryAutoLock) =
    mutate(setOf(SecondaryRoute.READY), setOf(SecondaryScope.WRITE)) { token, operation ->
      store.updateSettings(operation, strong, autoLock)
      publish(token) { operation.publish {
        policy.setStrongAuthInterval(strong); policy.setAutoLock(autoLock); policy.onSecurityChanged()
        mutableState.value = readyState(biometricEnabled = mutableState.value.biometricEnabled)
      } }
    }

  fun disableBiometric() = mutate(setOf(SecondaryRoute.READY), setOf(SecondaryScope.CREDENTIALS)) { token, operation ->
    store.removeBiometric(operation)
    publish(token) { operation.publish {
      policy.onSecurityChanged(); mutableState.value = readyState(biometricEnabled = false)
    } }
  }

  fun prepareBiometricEnrollment() = mutate(setOf(SecondaryRoute.READY), setOf(SecondaryScope.CREDENTIALS)) { token, operation ->
    val domain = synchronized(gate) { checkCurrent(token); checkNotNull(identity) }
    val slot = F1Crypto.random(16)
    val enrollment = biometric.prepareEnrollment(domain, slot, 1)
    var prepared: SecondaryBiometricRequest? = null
    try {
      prepared = SecondaryBiometricRequest(enrollment.cipher, null, operation.fork(), null, enrollment, null, slot, 1)
      operation.ownForSession(prepared)
      installRequest(token, prepared)
      prepared = null
    } finally { prepared?.close() }
  }

  fun prepareBiometricUnlock() {
    val admitted = begin(setOf(SecondaryRoute.PIN), true) ?: return
    val (token, auth) = admitted
    submit(token, SecondaryRoute.PIN) {
      check(policy.canUseBiometric())
      val record = store.biometricRecord() ?: error("Unavailable")
      val unlock = biometric.prepareUnlock(record.identity, record.slotId, record.generation, record.envelope)
      var prepared: SecondaryBiometricRequest? = SecondaryBiometricRequest(unlock.cipher, auth, null, record, null, unlock, record.slotId, record.generation)
      try { installRequest(token, prepared!!); prepared = null } finally { prepared?.close() }
    }
  }

  fun takeBiometricRequest(): SecondaryBiometricRequest? = synchronized(gate) {
    request?.takeIf { !it.delivered }?.also { it.delivered = true }
  }

  fun completeBiometric(value: SecondaryBiometricRequest, authenticatedCipher: Cipher) {
    val token = synchronized(gate) {
      if (disposed || request !== value || value.submitted) { if (request !== value) value.close(); return }
      value.submitted = true
      mutableState.value = mutableState.value.copy(busy = true, biometricRequestId = null)
      sequence
    }
    val route = if (value.enrollment != null) SecondaryRoute.READY else SecondaryRoute.PIN
    submit(token, route, value.unlock != null, { finishRequest(value) }) {
      if (value.enrollment != null) {
        val operation = checkNotNull(value.operation)
        operation.checkValid()
        val envelope = biometric.finishEnrollment(value.enrollment, authenticatedCipher, operation.key)
        try {
          store.installBiometric(operation, value.slotId, value.generation, envelope, value.enrollment::markInstalled) { commit ->
            synchronized(gate) {
              checkCurrent(token); check(request === value)
              commit() // Lock order: controller -> authority -> enrollment.
            }
          }
          // Journal ownership and pointer promotion are serialized with request cancellation.
          publish(token) { operation.publish {
            policy.onSecurityChanged(); mutableState.value = readyState(biometricEnabled = true)
          } }
        } finally { envelope.fill(0) }
      } else {
        check(policy.canUseBiometric())
        val unlock = checkNotNull(value.unlock); val record = checkNotNull(value.record)
        val capturedDigest = unlock.selectionDigest
        try { check(MessageDigest.isEqual(capturedDigest, digest(record.envelope))) } finally { capturedDigest.fill(0) }
        val master = biometric.finishUnlock(unlock, authenticatedCipher)
        try { store.validateBiometric(master, record).use { authenticated ->
          promote(token, checkNotNull(value.attempt), authenticated, pinSuccess = false, biometricSuccess = true)
        } } finally { master.fill(0) }
      }
    }
  }

  fun cancelBiometric(value: SecondaryBiometricRequest) = synchronized(gate) {
    if (request === value) {
      // Unwrap may already have returned a master. Invalidate its original publication token
      // and authentication attempt before closing the request; closing a Cipher is insufficient.
      sequence++
      if (value.attempt != null) {
        attempt?.let(authority::cancelAuthentication)
        attempt = authority.beginAuthentication() // Independent PIN fallback remains usable.
      }
      closeRequestLocked()
      mutableState.value = mutableState.value.copy(busy = false, biometricRequestId = null, biometricAvailable = false)
    } else value.close()
  }

  /** Explicit exit and Primary lock revoke this domain; no operation touches Primary authority. */
  fun exit() = synchronized(gate) { invalidateLocked(); mutableState.value = SecondaryUiState() }
  fun onPrimaryLocked() = exit()
  fun onScreenOff() = exit()
  fun onBackgrounded() = synchronized(gate) {
    if (disposed) return@synchronized
    resumeShell = mutableState.value.route == SecondaryRoute.READY
    resumeBiometricEnabled = resumeShell && mutableState.value.biometricEnabled
    sequence++
    attempt?.let(authority::cancelAuthentication); attempt = null
    pending?.close(); pending = null; clearRecoveryDisplayLocked(); closeRequestLocked()
    if (resumeShell) authority.onBackgrounded(policy.autoLock.timeoutMillis) else authority.revoke()
    mutableState.value = SecondaryUiState()
  }
  fun onForegrounded() = synchronized(gate) {
    if (disposed) return@synchronized
    authority.onForegrounded()
    if (resumeShell && authority.bindingOrNull()?.use { it.isCurrent } == true) mutableState.value = readyState(biometricEnabled = resumeBiometricEnabled)
    else { authority.revoke(); identity = null; mutableState.value = SecondaryUiState() }
    resumeShell = false; resumeBiometricEnabled = false
  }
  /** Timer cleanup never makes a backgrounded shell visible or clears its original deadline. */
  fun checkBackgroundExpiry() = synchronized(gate) {
    if (disposed || !resumeShell) return@synchronized
    authority.expireIfNeeded()
    if (authority.bindingOrNull()?.use { it.isCurrent } != true) {
      invalidateLocked(); mutableState.value = SecondaryUiState()
    }
  }
  override fun close() = synchronized(gate) { if (!disposed) { invalidateLocked(); disposed = true; mutableState.value = SecondaryUiState() } }

  private fun begin(routes: Set<SecondaryRoute>, enforceRate: Boolean): Pair<Long, SecondaryAuthAttempt>? = synchronized(gate) {
    if (disposed || mutableState.value.route !in routes || mutableState.value.busy) return@synchronized null
    if (enforceRate && !policy.canAttempt()) {
      mutableState.value = mutableState.value.copy(error = SecondaryUiError.RETRY_LATER, retryMillis = policy.retryAfterMillis()); return@synchronized null
    }
    val auth = attempt ?: return@synchronized null
    mutableState.value = mutableState.value.copy(busy = true, error = null, retryMillis = 0)
    sequence to auth
  }

  private fun pinAction(pin: CharArray, routes: Set<SecondaryRoute>, enforceRate: Boolean = false, action: (Long, SecondaryAuthAttempt) -> Unit) {
    val admitted = begin(routes, enforceRate)
    if (admitted == null) { pin.fill('\u0000'); return }
    val (token, auth) = admitted
    val fallback = synchronized(gate) { mutableState.value.route }
    submit(token, fallback, enforceRate, { pin.fill('\u0000') }) { action(token, auth) }
  }

  private fun mutate(routes: Set<SecondaryRoute>, scopes: Set<SecondaryScope>, cleanup: () -> Unit = {}, action: (Long, SecondaryOperation) -> Unit) {
    val admitted = synchronized(gate) {
      if (disposed || mutableState.value.route !in routes || mutableState.value.busy) { cleanup(); return }
      val operation = authority.operationOrNull(scopes) ?: run { cleanup(); invalidateLocked(); mutableState.value = SecondaryUiState(); return }
      mutableState.value = mutableState.value.copy(busy = true, error = null)
      Triple(sequence, operation, mutableState.value.route)
    }
    val (token, operation, fallback) = admitted
    submit(token, fallback, false, { try { operation.close() } finally { cleanup() } }) { operation.checkValid(); action(token, operation) }
  }

  private fun originalOperation(token: Long, scopes: Set<SecondaryScope>): SecondaryOperation = synchronized(gate) {
    checkCurrent(token); authority.operationOrNull(scopes) ?: error("Unavailable")
  }

  private fun requireIndependentPin(pin: CharArray) {
    if (pin.size !in 12..64 || pin.any { it !in '0'..'9' }) throw PinPolicyFailure()
    val copy = pin.copyOf()
    try { if (isPrimaryPin(copy)) throw PinPolicyFailure() } finally { copy.fill('\u0000') }
  }

  private fun acceptPending(token: Long, value: PendingSetup) {
    var accepted = false
    try { publish(token) {
      clearRecoveryDisplayLocked(); pending?.close(); pending = value; displayAvailable = true
      mutableState.value = mutableState.value.copy(route = SecondaryRoute.RECOVERY_DISPLAY, busy = false, error = null, biometricAvailable = false)
      accepted = true
    } } finally { if (!accepted) value.close() }
  }

  private fun promote(token: Long, auth: SecondaryAuthAttempt, value: AuthenticatedDomain, pinSuccess: Boolean, biometricSuccess: Boolean = false, route: SecondaryRoute = SecondaryRoute.READY) {
    // IO and catalog validation happen outside gate. Only final policy/authority/UI promotion is gated.
    val enabled = store.biometricRecord() != null
    publish(token) {
      authority.checkAuthentication(auth)
      policy.setStrongAuthInterval(value.strongAuthInterval); policy.setAutoLock(value.autoLock)
      if (biometricSuccess) check(policy.canUseBiometric())
      check(policy.canAttempt())
      val key = value.takeMaster()
      check(authority.completeAuthentication(auth, key))
      attempt = null; pending?.close(); pending = null; clearRecoveryDisplayLocked()
      identity = value.identity
      if (pinSuccess) policy.recordPinSuccess()
      else if (biometricSuccess) policy.recordBiometricSuccess()
      else policy.onSecurityChanged()
      mutableState.value = readyState(enabled).copy(route = route)
    }
  }

  private fun installRequest(token: Long, value: SecondaryBiometricRequest) = publish(token) {
    closeRequestLocked(); request = value
    mutableState.value = mutableState.value.copy(busy = false, biometricRequestId = value.id)
  }
  private fun finishRequest(value: SecondaryBiometricRequest) = synchronized(gate) {
    if (request === value) request = null
    value.close()
  }
  private fun guardedCommit(token: Long, auth: SecondaryAuthAttempt, action: () -> Unit) = synchronized(gate) {
    checkCurrent(token); authority.commitAuthentication(auth, action)
  }
  private fun publish(token: Long, action: () -> Unit) = synchronized(gate) { checkCurrent(token); action() }
  private fun checkCurrent(token: Long) { check(!disposed && token == sequence) { "Unavailable" } }
  private fun readyState(biometricEnabled: Boolean = mutableState.value.biometricEnabled) = SecondaryUiState(
    route = SecondaryRoute.READY, biometricEnabled = biometricEnabled,
    strongAuthInterval = policy.strongAuthInterval, autoLock = policy.autoLock,
  )
  // Retain the same returned mutable array, no extra plaintext copy. Revocation must
  // wipe it synchronously even when Compose disposal is delayed by background/lifecycle.
  private fun clearRecoveryDisplayLocked() {
    displayAvailable = false; displayedRecovery?.fill('\u0000'); displayedRecovery = null
  }
  private fun closeRequestLocked() { val old = request; request = null; old?.close() }
  private fun invalidateLocked() {
    sequence++; resumeShell = false
    attempt?.let(authority::cancelAuthentication); attempt = null
    authority.revoke(); pending?.close(); pending = null; clearRecoveryDisplayLocked()
    closeRequestLocked(); identity = null
  }

  private fun submit(token: Long, fallback: SecondaryRoute, authFailure: Boolean = false, cleanup: () -> Unit = {}, action: () -> Unit) {
    val work = Runnable {
      try { synchronized(gate) { checkCurrent(token) }; action() }
      catch (failure: Exception) {
        synchronized(gate) {
          if (!disposed && token == sequence) {
            if (authFailure && failure !is PinPolicyFailure) policy.recordFailure()
            mutableState.value = mutableState.value.copy(route = fallback, busy = false, error = when {
              failure is PinPolicyFailure -> SecondaryUiError.PIN_POLICY
              authFailure -> SecondaryUiError.AUTHENTICATION
              else -> SecondaryUiError.UNAVAILABLE
            }, retryMillis = policy.retryAfterMillis(), biometricRequestId = null, biometricAvailable = false)
          }
        }
      } finally { cleanup() }
    }
    try { executor.execute(work) } catch (_: Exception) {
      cleanup()
      synchronized(gate) { if (!disposed && token == sequence) mutableState.value = mutableState.value.copy(busy = false, error = SecondaryUiError.UNAVAILABLE) }
    }
  }
  private class PinPolicyFailure : Exception()
}
