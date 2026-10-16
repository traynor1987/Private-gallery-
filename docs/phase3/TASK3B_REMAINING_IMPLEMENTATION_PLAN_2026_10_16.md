# Task 3B remaining implementation execution

Spec: owner's Task3B recovery/completion mission, existing TASK3B_COMPLETION_CHECKPOINT_2026_10_16.md, RUNTIME_MIGRATION_LEDGER.md, CLEANUP_CAPACITY.md and LOCK_ORDER.md. This is execution bookkeeping, not replacement of the accepted security contracts or restart of verified work. Prior checkpoints and failures remain authoritative.

## Global Constraints

- Task3B only. Task3A remains preserved. No Task3C–H, Phase3 Tasks4–8, main/protected-branch or PR59 changes.
- Existing recovery branch only for GitHub checkpoints. Phase3 NO-GO; PR59 OPEN/DRAFT/UNMERGED. No release APK/signing/owner physical acceptance.
- Fixed existing physical cleanup capacity16; exact original precreation manifests, strong independent child accounting and supported actual cleanup acknowledgement. Failed/incomplete application cleanup stays charged and denies unsafe admission. No quotas, generic owner exemptions or late registration shortcuts.
- Only Android-internal Keystore operation retirement without supported public acknowledgement is platform-managed. No AndroidX/application/provider/worker/array exception; never claim internal retirement was observed.
- Detach bounded metadata under ranked gates; Native/provider/Keystore/request cleanup and actual waits outside prohibited gates. Preserve exact epoch/attempt/selected-vault final authorization and encrypted formats/media/credentials.
- One implementation owner and one Gradle execution at a time. Never edit tested source while Gradle is active. Controlled deterministic tests, retain failures and interrupted-run qualifications. Independent review before declaring a component accepted; full A–D acceptance remains separate.

## Task 1: Own replacement confirmation inputs and key snapshot

Implement a focused replacement-confirmation family in the existing Secondary authentication/store path. Inspect current source first. Reserve TWO independent IO children atomically before acquisition: an exact RECOVERY operation-owned32-byte master snapshot and the caller's exact32-byte confirmation secret. The extra master snapshot is necessary: SecondaryOperation.key is a copied lease buffer that revocation wipes; Native verification cannot borrow it unprotected.

Prefer existing PendingSetupBuffer + ReleaseReservation.beginPendingBuffer typed admission. Attach inert cells before production; copy operation.key only in a short valid original operation.commit. Run the synchronous store consumer outside resource/authority gates. No new worker/queue. Both cells retain active borrows through actual consumer return; revocation seals ingress without wiping admitted use. Explicitly release the same original and await terminal retirement/failure outside root and authority before success; separately recheck original operation afterward. Wipe supplied secret on all failed-admission/terminal paths without racing its legitimate synchronous consumer. Preserve original errors and interruption status.

Migrate SecondaryController's replacement branch to this family before store entry. SecondaryStore.confirmReplacement must use its explicitly borrowed stable master and secret for all Native verification/preparation/selection, eliminating secretSnapshot and direct operation.key Native reads in this method. Keep metadata scope/epoch checks, selected predecessor, pending's independent retirement wait, and final UI publication guards. Do not leave an unmanaged three-argument production overload: direct synthetic test callers can use fixture-only adapters retaining real ownership, as with existing pending fixtures. Do not expose raw-array getters or general ownership transfer.

Add deterministic focused tests: normal exact-buffer use/wiping; held consumer cancellation preserves both buffers until return; revoked original denies late success and fresh admission; partial/capacity2 denial before consumer/copy; unsupported secret length; exceptions; inherited authority gate denial; actual cleanup callback return vs stage completion; interrupted/failed mandatory retirement retaining accounting. Include relevant replacement/controller/credential regressions and preserve selected biometric aliases. Tests prove state/bytes/actual returns, not counts alone.

This completes only this bounded input family. Outer Executor/captured UI input ownership before worker entry, confirmPossession temporary unwrapped master and complete provider/store manifests remain mandatory Task3B dependencies, not later-phase work.

## Task 2: Complete prepared Primary PIN persistence transactions

Use actual source and recorded review to implement one process-wide bounded CAS mutation claim across ALL PIN writers, exact preowned prepared preference commands outside gates, consistent encrypted snapshot/KDF separation, and exact original admission. Include setup/change/recovery/restore/rollback. Restore must retain its SAME claim until explicit success or exact-owned rollback; no rollback-only detached lambda permitting replacement overlap. Original authentication preparation contexts must be exact, thread/lifetime sealed and one-use; a generic generation callback is insufficient. Fix recovery's remaining check-to-write/preparation gap. Preserve formats, truthful partial durable outcomes, secret wiping and sole-result final barriers. Refine task into coherent internal batches against source before code; never guess transaction semantics or silently skip a writer.

## Task 3: Finish authentication queue/coordinator ownership

Complete exact precreation ownership for SecondaryController outer workers/queued captures/recovery display and Main authentication orchestration. Retire nonresult obligations before final branded result admission; never exempt a still-running coordinator. Own actual Threads/Native returns independently from Jobs/futures. Preserve original epoch/route/vault and stale/replacement/ABA denial, Activity/background cancellation and fixed capacity. Base implementation on actual current resources, not conversation reconstruction.

## Task 4: Complete store/provider/directory Native ownership and lock separation

Complete preparation/selection/serialized store operations, secondary directory/channel/stream acquisition and provider/temporary-result manifests, with bounded detachment and supported cleanup outside prohibited ranked gates. Preserve verified selection/relative-publication contracts, independently charged cleanup and original acknowledgement. Remove remaining confirmation/unwrapped-key/display buffer escapes. Produce concrete whole-route capacity census; deny unsupported acquisition before creation rather than expand limits.

## Task 5: Restore an accepted supported biometric lifecycle

Implement and verify a supported production boundary controlling app-owned prompt/queued callback/provider references, original attempt/epoch, stale Cipher denial and independent actual retirement. Existing AndroidX graph lacks public retirement and remains DISABLED. Review the archived API35 private-process proposal (Revision3 supersedes unsafe callback revisions) against actual source/capacity before integration. A model/compile/fail-closed result cannot enable biometrics. No private APIs/reflection cleanup or extra platform exception. Obtain real supported Android runtime evidence through an authorized nonrelease validation environment before enabling; record unavailable evidence honestly.

## Task 6: Whole Task3B acceptance and checkpoint

After coherent implementation run full relevant Debug/Phase0 JVM, security/source integrity, both lints and Android test compilation, supported Android runtime checks, and independent final A–D review against exact final source. Resolve genuine production findings and missing required verification; do not rename unfinished3B as3C. Preserve source/results/reviews remotely, verify exact recovery SHA and unchanged main/protected refs/PR59, and report PASS only if all mandatory requirements are satisfied. Stop after3B; no automatic3C.
