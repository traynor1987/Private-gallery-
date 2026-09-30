# Phase 0 Primary session core evidence

## SOURCE FACT

At audited baseline `a19218479eb9b9bcdb35ff0035fc78522263c891`, `ProtectedSessionState` retained a mutable key and boolean-oriented `LockSession`; no immutable epoch or operation authorization existed. `LockSession` checked its deadline only on foreground delivery. `VaultVideoSession.close()` wiped only the session key and did not close existing `ChunkedVaultVideoStore.Reader` instances, their copied keys, cached plaintext, or descriptors.

## IMPLEMENTED PHASE 0 CHANGE

`PrimarySessionAuthority` owns the supplied authenticated Primary key, issues per-operation copied-key leases and random in-process immutable epochs/operation identifiers, and revokes admission before cancelling jobs/closing resources/wiping keys. Protected actions independently check a monotonic deadline. Commit and publication serialize authorization with revocation. Lease closure stops decrypt/commit/fork, while epoch-valid queued publication remains possible without retaining a key. ABA re-entry never validates an old epoch. `ScopedItemHandle` binds Primary, epoch, item ID and the existing `legacy` revision marker; it introduces no persisted revision or format migration.

Cleanup completion waits for job completion, not merely cancellation; resource-close failure prevents a completion claim. Opening while cleanup remains incomplete is denied and the rejected transferred key is wiped. `ProtectedSessionState` exposes the authority alongside legacy session/key integration fields, uses `SystemClock.elapsedRealtime`, owns its background timer in `viewModelScope`, and revokes on clearing. `LockSession.isValid(now)` independently enforces its policy deadline.

`VaultVideoSession` serializes source open/read/close with session close, tracks all open data sources, and actively closes every live Reader on session closure. PGVIDEO1 serialization, header authentication, chunk authentication and payload key derivation are unchanged.

## TESTED EVIDENCE

Test-first RED: added `PrimarySessionAuthorityTest` and a `LockSessionTest` deadline regression before implementation. An independent invocation of Gradle's bundled `K2JVMCompiler` against these test files and original LockSession failed with unresolved `PrimarySessionAuthority`, `ContainerId`, and `isValid` (missing new API, not a runtime behavior assertion). The compiler output is `/tmp/private-gallery-session-standalone-red.log`. The initially selected multiplatform coroutine jar was corrected to a JVM jar before this accepted RED run.

GREEN independent JVM smoke: compiled actual authority/LockSession sources and both test classes using bundled Kotlin 1.9.24, JUnit 4.13.2 and cached coroutine JVM 1.6.4, then invoked JUnitCore. Result: **OK (13 tests)**, exit 0, 0.191 seconds. Coverage includes closed-lease publication with key wipe, closed decrypt/commit/fork denial, ABA, stale commit/publication/fork, expired actions without timer delivery, unexpired foreground, same-epoch fork, close/cancellation cleanup with delayed asynchronous finalization, late resource/job registration, commit-vs-revoke ordering, incomplete-cleanup reopen denial/rejected key wipe, close failures, and stale scoped handles. This is a compatibility smoke run, not the project's configured Kotlin 2.0.21 Android build.

Gradle command attempted with serialized `/tmp/private-gallery-phase0-gradle.lock`:

```sh
ANDROID_HOME=/tmp/private-gallery-android-sdk GRADLE_USER_HOME=/tmp/private-gallery-phase0-gradle ./gradlew :app:testDebugUnitTest --tests '*PrimarySessionAuthorityTest' --tests '*LockSessionTest' --console=plain
```

That sandbox attempt failed before compilation with `Could not determine a usable wildcard IP for this machine`; it is not a test failure or passing result. Concurrent initial project prerequisite check failed because `/usr/lib/jvm/java-17-openjdk-amd64` lacked `JAVA_COMPILER` capability. Full configured suite and Android compilation are outstanding until prerequisites are corrected.

## UNRESOLVED / PHYSICAL ACCEPTANCE REQUIRED

`VaultVideoDataSourceTest.closingSessionActivelyClosesAllLiveDataSourcesWithoutAnotherRead` is written for Android instrumentation. It verifies two real open Reader descriptors close without another read, captured copied-key/cache arrays are wiped, source URIs clear, and further use fails. It has not yet executed on Android; no device/emulator reader-cleanup result is claimed. Existing seek/authenticated-chunk instrumentation remains intact. Actual process-death/configuration/provider/OEM acceptance and native/GPU copy zeroization are outside this unit evidence.

Activity/repository integration is separately owned and must capture these operations before the core becomes a production authorization boundary. No Hidden production key/root/UI, VDEK rotation, ciphertext migration, format change, external push or release is performed by this task.

## Subsequent configured verification and Activity integration

The JDK/IP/trust-store blockers above were resolved with full JDK17 `/tmp/private-gallery-jdk17`, the system Java trust store `/etc/ssl/certs/java/cacerts`, and authorized network execution. The initial Android compile identified the inferred nullable return from `VaultVideoSession.close`; declaring the overridden return `Unit` corrected that compiler error.

The owner extended this task to exclusive `MainActivity` integration. Protected Activity jobs now use captured `PrimaryOperation`s, registered before LAZY start, and queued UI deliveries validate their original epoch after the copied-key lease closes. Compose callbacks retain their original UI operation. Browser sessions/metadata callbacks are instantiated for one epoch; document, VPN and source-deletion results retain their original operation or authentication attempt instead of selecting the latest key. Recovery setup hides the one-time display before owner re-entry, confirms the pending envelope against the active Primary capability and guarded promotion, and preserves confirmed/legacy envelopes. Root-only interrupted archive installation exposes a LOCK-only resume link; it never offers fresh VDEK creation over ciphertext. The existing archive restore dialog is reused.

Authentication-only attempts are held in retained `ProtectedSessionState` and revoked on old Activity destruction, including configuration changes. The final archive promotions use the immutable admission attempt under its synchronization gate; they do not repeatedly re-evaluate the fresh-install predicate after the transaction creates its own root. Protected Job completion closes its key lease only after normal completion, avoiding self-cancellation that would incorrectly discard a normal queued preview.

Actual regression RED: an isolated copy with the operation epoch/lease guard removed compiled and ran **14 tests with 6 failures** (`/tmp/private-gallery-session-standalone-behavior-red.log`), including stale publication, expired commit, closed-lease decrypt denial and post-revoke registration. Unmodified sources then passed **14 tests**, exit 0 (`/tmp/private-gallery-session-standalone-green.log`). This mutation is a behavior check in addition to the initial missing-API RED.

Actual Job-completion RED: the added assertion that a successfully finished Activity job is not cancelled failed against the original `finally { operation.close() }` helper: **3 tests, 1 failure** (`/tmp/private-gallery-selfcancel-red.log`). Closing solely from `invokeOnCompletion` fixed the failure while retaining revoke/unstarted-job cleanup and queued publication validation.

Configured GREEN: the named session/LockSession/Activity helper tests passed in the Android project's configured Kotlin 2.0.21 build; the final targeted command also assembled the debug instrumentation APK successfully (`/tmp/private-gallery-owned-final.log`, **BUILD SUCCESSFUL in 8s**). These suites contain **17 tests**: 11 Primary authority, 3 LockSession and 3 Activity callback/auth-attempt tests. Earlier root-wide verification ran **375 tests, zero failures**, lintDebug, assembleDebug and assembleDebugAndroidTest successfully (`/tmp/private-gallery-phase0-final-local.log`); final root-wide verification after the last review fixes remains separately owned and must be reported against its exact ending commit.

Main-thread Browser/UI cleanup is a registered Job with a NonCancellable Main finalizer: an IO-detected expiry cancels this job, closes the WebViews on Main, clears Activity bookmark/VPN/preview/recovery-display state, and only then completes the cleanup job. `cleanupComplete` remains false until that registered finalizer actually finishes. Weak bitmap cleanup references prevent retaining every evicted decoded preview until lock.

Android instrumentation tests compiled into the debug test APK. Neither the actual Reader FD/key/cache cleanup test nor `MainActivityRestoreResumeTest.lockedRestartCanRequestArchiveResumeWithoutCreatingFreshVault` was executed on a device in this task. Configuration/process/OEM/biometric acceptance remains physical/runtime evidence, not a conclusion from JVM tests or compilation. Native/GPU copies and provider prediction-memory lifetime are not claimed zeroized.
