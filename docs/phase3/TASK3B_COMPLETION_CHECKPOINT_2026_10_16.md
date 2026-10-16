# Task3B completion execution — PARTIAL, not accepted

Recovery branch: `recovery/phase3-task3-wip-2026-10-06`.
Recovered remote parent: `f1204c6f097afeaa7a4534e6b87259d529a88fc0`.
Task3A ancestor: `5124de138a3bcb9f3e5fec186a8442297459e654`.

## Implementation actually preserved

| Blocker | Implemented | Still mandatory |
| --- | --- | --- |
| A | Opaque original owner adapters, exact Cipher identity checks, isolated Activity/model and bounded launch slot, controlled callback/borrow/destruction-return lifecycle, stale/replacement regressions. | Actual AndroidX app-owned queued graph retirement is unproven. Production acquisition is fixed unavailable. An audited controlled adapter is required before biometrics can operate. |
| B | Precreation request provider/fork/slot ownership; branded actual-Thread key handoff; independently owned Primary/Hidden comparison/enrollment snapshots; actual borrower return and wiping acknowledgement. | Outer executor/coroutine queues, PendingSetup/recovery/display, KDF/provider partial construction, stream/channel and output manifests; complete fixed16 capacity fit. |
| C | Native input consumption outside authority gates; request retirement outside controller; prepared pinned relative rename narrows final controller/authority publication to the atomic move. | Root4 stream/channel cleanup and Primary gated preference persistence remain unresolved. |
| D | Store-bound original selected token/generation/identity proof; original sequence/epoch/route checks; actual worker-return result handoff; Hidden enrollment waits request retirement before UI success. | Whole-path mandatory cleanup and final admission cannot be accepted until remaining B/C obligations are implemented. |

Consumers migrated include Primary biometric unlock/enrollment/sensitive confirmation,
Hidden biometric unlock/enrollment and Hidden PIN/recovery result handoffs. Existing
synchronous compatibility APIs remain historical test seams, not complete ownership proof.

Independent review established and verified fixes for S1 (Hidden enrollment success
preceded actual provider retirement) and S2 (Primary Native input could be wiped by
revocation during its admitted borrow). Hidden enrollment now also preowns the exact
borrowed snapshot, avoiding a second unowned copy. A cancellation regression holds
actual Hidden provider retirement, cancels, and verifies no late success while the
selected installed alias survives.

## Demonstrated AndroidX blocker

Official pinned androidx.biometric1.1.0 source JAR SHA256:
`a34841717766cd97c36f708763ead26c79a9721129ffd87c57c99fea10a8f9e0`.

BiometricFragment.java447–454 and704–711 queue anonymous delayed tasks retaining
Fragment → ViewModel → CryptoObject/Cipher. Activity destruction and ViewModelStore.clear
do not retire those queues. PromptExecutor127–136 permits later framework callback
posts; a maximum-delay barrier does not establish sealed ingress. No supported
public close/quiescence/crypto-detach API was identified.

This is an app-owned dependency lifetime blocker, NOT the approved Keystore-internal
exception. AndroidXBiometricRetirementSupport.available is fixed false, not configurable.
Main entrypoints, Primary Cipher preparation, Hidden Android key preparation, coordinator
and backend deny before actual provider/prompt/Activity acquisition. Controlled JVM
backends explicitly opt into their observable retirement protocol. They do not establish
Android acceptance. Existing encrypted biometric records/aliases remain preserved;
supported alias cleanup is still callable. PIN paths remain independent.

**Biometric unlock, enrollment and sensitive biometric confirmation are intentionally
unavailable in this partial recovery checkpoint.** The experimental Android host must
remain unreachable until app-owned queue ingress, callback return and reference retirement
are controlled and independently verified. Containment does not complete A.

## Actual verification and evidence qualification

Observed completed commands before workspace loss:

- Final focused Gradle selection: **152 tests, 0 failures/errors/skips, 18 classes**, exit0.
  Includes Primary/Hidden authority, biometric lifecycle/slot/request/borrow, actual Thread
  handoff, selected-credential rotation, prepared move/Store and selected Task3A plaintext tests.
- Earlier coherent focused command: 95 tests, 0 failures/errors/skips, exit0.
- Source/security Python suite with working SDK: 25 tests passed.
- Format vectors/design contracts: 35 tests passed.
- Browser helper: 3 tests passed.
- Frozen legacy corpus hashes and identical instrumentation fixtures passed.
- Backup-source policy, retired-model distribution and WireGuard-only source checks passed.
- Unsupported-backend regression genuinely failed before its guard, then passed in the152 run.
- Hidden exact Native input ownership regression genuinely failed before its independent
  snapshot original, then passed in subsequent focused runs.
- Earlier normal-Primary ordering, Hidden provider-retirement and Primary borrowed-key
  failures, plus fixture/configuration failures, were preserved locally during execution;
  none was counted as a passing command.

Broad verification first failed task selection before tests (incompatible instrumentation
variant task settings). Corrected broad execution returned exit1. While it was running,
the observed failure was SecondaryControllerBiometricTest.
cancellationDuringPointerPromotionCannotDeleteTheSelectedBiometricAlias at line286,
before its interleaving barrier. Its completed stack and final totals were not recovered.
A fixture cleanup-isolation explanation remains a hypothesis, not an accepted fix.
Do not weaken production admission checks or claim this broad run passed.

The repository, toolchain, current raw XML/logs and local review files unexpectedly
disappeared during execution. A later read showed an empty repository directory
containing only .gradle and no .git/app/toolchain; the active process was no longer present.
The pending Gradle session subsequently returned exit1. Thus complete JVM/lint/
instrumentation compilation acceptance is **unverified/failed**, and runtime/device
verification is unavailable (ADB had no attached device/emulator).

Before that loss, all31 changed app/test/script files had been uploaded as immutable Git
blobs and their returned SHAs checked against the staged Git blobs. The surviving
orchestrator manifest reconstructs the exact tree without recreating source from memory.
See the machine-readable source Git-blob manifest alongside this document.

Session raw evidence was not recovered and is not fabricated here. This is a new
recovery notice recording observed command outcomes, not a reconstruction of lost logs.
Previously committed historical evidence remains unchanged in the parent tree.

## Independent review disposition

The independent reviewer verified all30 app-file SHA256 hashes before the broad run,
with zero mismatches. That manifest's observed SHA256 was
`c7952a6dd819688d8061cdc5e2d7e560111f567e41efba4ec1a7f929b8558a85`.
The local manifest/report disappeared; the exact Git-blob manifest below survives.
Reviewer conclusions were delivered before loss: S1 and both input-borrow fixes sound;
S3 contained by fixed production denial; no additional introduced defect established;
full Task3B remains BLOCKED/PARTIAL. This paragraph records those delivered findings,
not a fabricated copy of the missing original review.

## Remaining blocking source locations

- A: OriginalBiometricActivity.kt and core/security/AndroidXBiometricRetirementSupport.kt:
  real AndroidX app-owned retirement remains unsupported; current acquisition denial is containment.
- B/D: core/domain/SecondaryController.kt:560 submit queue; :488 pending publication;
  :155 recovery display; core/domain/DomainSnapshot.kt:39 PendingSetup.
- B/C: core/domain/SecondaryDirectoryHandles.kt:162 close and open/channel paths;
  SecondaryStore.kt:258,289 prepare/select and root-serialized consumers.
- C/D: core/security/PrimaryKeySlots.kt:45–49 preference persistence inside operation.commit;
  the unused legacy enrollment method retains gated Native work.
- B/C/D: core/security/BiometricVaultKeyStore.kt and core/domain/SecondaryBiometricSlot.kt:
  complete provider partial-construction/output manifests remain unfinished.
- Main outer coordinator coroutine ownership is unfinished; production biometric denial
  prevents those paths from executing, not complete ownership acceptance.
- Verification: the observed broad failure requires its real stack or deterministic reproduction,
  then focused correction and complete JVM/lint/instrumentation compilation checks.

None is reassigned to Task3C–3H. Task3B is not PASS.

## Preserved invariants and boundaries

Only inaccessible Android-internal Keystore retirement is platform-managed under the
owner-approved exception. No prompt cancel, callback, Cipher disposal, abort, queued work
or future completion is claimed to prove internal retirement. Residual platform lifetime/
OEM risk remains unobserved. App-owned incomplete/failed cleanup stays charged; no physical
capacity increase, format change or rank exception was introduced.

Task3A source/format implementations remain unchanged, with selected regressions passing;
complete broad regression acceptance is not claimed. PIN wrapping, F1 format and
verification-before-delete remain intact. No original media deletion was introduced.

Protected main head: `93ed56fbfcd6cb04731a87452dbcbf2e6d6bcb10`.
Protected Phase3 head: `79bb1928f3fbe16e935e587a3fe594158cb3f529`.
PR59 remained OPEN/DRAFT/UNMERGED, updated_at2026-10-04T05:20:31Z.
No release APK, signing, release workflow or owner physical acceptance.
Phase3 remains NO-GO. **Task3C is not safe to begin and has not been started.**
