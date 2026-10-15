# Task3B authentication lifecycle — partial checkpoint — 2026-10-15

**Task3B: PARTIAL. Full acceptance BLOCKED. Task3C cannot safely begin.**
Phase3 remains NO-GO; PR59 stays draft, open and unmerged.

## Recovery and scope

Actual remote recovery head was verified as
`5124de138a3bcb9f3e5fec186a8442297459e654`, the completed Task3A checkpoint.
Continues the existing Work worktree and recovery branch, without reset or loss
of source/evidence. No active Gradle execution was replaced. One Gradle execution
at a time was used throughout this phase. Main remains
`93ed56fbfcd6cb04731a87452dbcbf2e6d6bcb10`; PR59's branch remains
`79bb1928f3fbe16e935e587a3fe594158cb3f529`.
No Task3C–3H, Tasks4–8, encryption/schema change, release APK, signing or workflow
change, new PR or owner physical acceptance. Existing Task3A paths/tests/evidence
are retained. This checkpoint is recoverable implementation, not security acceptance.

## Production changes

- `PrimarySessionAuthority.kt`: exact opaque Primary attempts, bounded precreation
  manifest admission, cleanup/capacity denial, 60-second attempt expiry, replacement/
  background invalidation and a separately checked final key admission. Native
  factory invocation and phase waiting occur outside the authority gate.
- `OwnedAuthenticationKey.kt`: exact-authority/attempt, factory-branded result
  original; three independently charged result/invocation/input children funded
  before the synchronous leaf factory. The invocation and input retire physically
  before the key-only result handoff. Borrowed input is not wiped during a live
  factory. Cancellation preserves actual-return and failed-retirement accounting.
- `ReleaseReservation.kt`: atomic original validation plus typed key consumption;
  synchronous bounded result invalidation on release without completing an
  acknowledgement, running Native work, or recycling physical capacity.
- `MainActivity.kt`: PIN setup/unlock consume the owned result via short authority
  admission; PIN wiping covers route/configuration/outer-attempt/clock failures.
- `SecondaryBiometricSlot.kt`: synchronous Cipher invocation is outside pending
  enrollment/unlock monitors. Disposal waits for actual admitted use to return
  outside those monitors; stale output is wiped after Native return. Duplicate
  enrollment close retains the first deletion result; self-wait and inherited
  pending-monitor entry are rejected. This does not claim cleanup outside every
  caller lock or retirement of the initialized Keystore operation.

Fixed process pool sizes, independent child accounting, Primary/Hidden boundaries,
PIN wrapping, vault formats, existing encrypted media and verification-before-delete
are unchanged. No new weak registration, unbounded buffer or raw UI key getter.
The three-child helper covers only a synchronous leaf; it does **not** account for
implicit KDF, store, provider, Native handle or queued worker children.

## Remaining Task3B obligations and hard blocker

1. Complete precreation manifests for every Primary/Hidden auth path, including
   prompt/request, worker, provider, store/IO/KDF temporaries and independent cleanup/
   unblocking resources. SecondaryStore/SecondarySessionAuthority/SecondaryController
   have not been migrated by this checkpoint.
2. SecondaryController still closes requests beneath its controller gate, submits
   unfunded executor work and promotes raw `AuthenticatedDomain.takeMaster()` from
   a worker before full request/worker retirement. Bounded detachment and separately
   funded releases outside ranked gates remain required. Pending close can wait for
   provider return; callers under controller gates remain unsafe and are not accepted.
3. Main primary biometric, Hidden biometric, sensitive authentication, recovery and
   restore paths remain unmigrated. Exact prompt replacement/ABA isolation and
   cancellation-before-start ownership are not established end-to-end.
4. **Supported Native retirement contract is missing.** Public `Cipher` exposes no
   exact-operation close/abort acknowledgement. `BiometricPrompt.cancelAuthentication()`
   cancels authentication, but does not document positive retirement of its
   initialized Cipher operation. Pending unlock close before finish currently wipes
   known arrays; enrollment alias deletion also does not establish exact-operation
   abort return. This is an implementation/support-contract blocker, not merely
   missing device test evidence. A supported provider/platform adapter must prove
   exact-operation abort, actual return and failure retention, including failed
   initialization and cancellation before prompt startup. No hidden API, dummy
   Cipher reinitialization, dropped reference, finalizer or terminal UI callback is
   accepted as that acknowledgement.

Primary sources informing the blocker: [Cipher API](https://developer.android.com/reference/javax/crypto/Cipher),
[BiometricPrompt API](https://developer.android.com/reference/androidx/biometric/BiometricPrompt),
and [pinned AOSP Keystore Cipher implementation](https://android.googlesource.com/platform/frameworks/base.git/+/1406600d75c1a30ebdf45312b7fa0f4a4355194b/keystore/java/android/security/keystore2/AndroidKeyStoreCipherSpiBase.java).
The provider conclusion is an inference from the supported API and implementation,
not a claim that all devices behave identically. No security contract was relaxed.

## Verification

Evidence: `evidence/task3b-auth-2026-10-15/`. Fresh focused selection: **98 passed,
0 failures/errors/skips**, including 19 new Primary handoff tests, 19 biometric-slot
tests, controller/secondary/Primary authority regressions and Task3A OwnedPlaintext.
Android instrumentation Kotlin/Java compilation passed. Static checks: 21 Python,
35 vector/design and 3 Node tests passed; secret/model/VPN/backup/corpus checks passed.
The initial Python run without ANDROID_HOME skipped the packaged-backup fixture;
the SDK-configured rerun executed all21 with no skips. Both runs are retained.

Broader JVM execution completed successfully (11m28s): **1245 debug and1252 Phase0
passed,0 failures/errors/skips**. Both generated result directories were removed
before that invocation; all cases were freshly executed. All586 frozen inputs
remained unchanged. Both lints passed (4m42s),0 Fatal/Error findings. Debug:34 warnings/4 information;
Phase0:54 warnings/4 information, matching the Task3A counts. Full XML findings
and logs are retained. CI/release workflows were not changed or requested.
No Android runtime, Keystore/OEM, emulator or physical-device execution is claimed:
this environment has no `/dev/kvm`, emulator or system image. Compilation and host
provider test doubles do not establish Android Native operation retirement.

Independent read-only review found three Important implementation defects. Actual
RED evidence and the single fix pass are retained: original-validation/consume race,
delayed result wiping and early-failure PIN wiping. Final focused tests pass after
those fixes. The independent review's remaining scope/provider findings above stay
open. Earlier genuine failures and the premature-wipe test-fixture qualification
are preserved rather than relabeled as passes.


## Infrastructure loss qualification — checkpoint upload

After every final verification completed, the execution service lost the original
/workspace/scratch/0881340dd615 directory during blob upload. Shell execution from
/ was available, but the original worktree, build outputs and unuploaded logs were
no longer present. The recovery branch had not been advanced.

All nine changed production/Kotlin-test files had already been uploaded with exact
original staged Git blob hashes. The Python guard was recovered byte-for-byte and
its GitHub blob SHA matched the original staged hash. All ten changed source/test
files therefore remain exactly those tested. The original baseline is unchanged.
Documentation/evidence-only recovery changes do not replace production source.

Some raw Task3B logs, lint XML and per-class full-suite XML summaries could not be
uploaded. References above to their retention are superseded by this notice. No
missing raw file was recreated or represented as available. See
CHECKPOINT_RECOVERY_NOTICE.md and original-stage-inventory.json in the evidence
folder for the exact available/lost inventory and observed verification results.
The observed completed test/build results remain real; the raw archive is incomplete
and independent replay/reverification remains an additional acceptance obligation.
Task3B stays PARTIAL, full acceptance BLOCKED; Task3C must not begin.
