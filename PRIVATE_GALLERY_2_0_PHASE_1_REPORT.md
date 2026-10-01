# Private Gallery 2.0 — Phase 1 report

1 October 2026. PRIMARY-only scoped security architecture. Implementation and synthetic automated evidence are separate from signed-build and physical acceptance. **Replacement candidate signed and aggregate owner physical acceptance passed. Final admission evidence is recorded below. The former candidate `77c95d14339309ba5ec1dbab6e4c6db82276bb3f` is SUPERSEDED and must not be installed, accepted, merged or promoted.** No Phase 2 work is authorized by this report.

## Provenance and candidate identity

| Field | Independently verified evidence |
| --- | --- |
| Authoritative starting main / merged Phase 0 | `0a582c6458e2899dde3b1b7be57eba3f4f1c2b20`, PR #56 merged. |
| Main CI | Run `36867322599`, completed/success. Phase 0 closeout remains authoritative; satisfied gates were not reopened. |
| Recovered local Phase 1 | Worktree survived with commit `1e0a5eede6a2df14b09d86603322fa27814eb8e5` plus actual adapter diffs. Source was inspected and preserved, rather than inferred from previous messages. |
| Dedicated branch / review | `phase1/primary-scoped-security`; [PR #57](https://github.com/traynor1987/Private-gallery-/pull/57). Owner-authorized final review/integration; no public release or Phase 2. |
| Reviewed implementation milestone | `cd363624318bb6b03926bbb3ddbf4c2c7a92f2ae`; source-identical tree `efea63368fda4880ff8aac6c4a27827e95ad8bf0`. Later integrated video/Gallery callback coverage is recorded by this report's commit. |
| Accepted production candidate | `3d0edcd3cf563204beb33698486948bdaa809924`. Signed #53 `build-sha.txt` matches exactly. The subsequent closeout changes documentation only; all production, release, test, fixture and workflow inputs remain byte-identical. |
| Signed/device status | Signed #53 PASS; focused physical acceptance aggregate owner-reported PASS on 1 October 2026: exact replacement APK installed in place; owner reports "All working." No individual observations or device telemetry are inferred. |

Recovery details and preserved local/remote commit mappings are in [RECOVERY_AND_REQUIREMENTS.md](docs/phase1/RECOVERY_AND_REQUIREMENTS.md). The inherited admission-stop report is preserved as [historical evidence](docs/phase1/HISTORICAL_ADMISSION_REPORT.md), not the current status. The architecture audit dated 2026-09-29 was retrieved and consulted, including sections 26–28. The current user specification and merged Phase 0 contract govern this implementation.

Exact changed files are listed in [CHANGED_FILES.txt](docs/phase1/CHANGED_FILES.txt). The delta covers scoped authority/legacy adapters, Primary repository/media/UI callers, network ownership, synthetic tests, CI branch admission and documentation; frozen fixtures and crypto format specifications have no delta.

## Implemented architecture

The existing Primary root, VDEK and encodings remain behind a fixed legacy Primary adapter. `LegacyPrimaryContainer` rejects a foreign identity before consulting context/root state and has no second production root. `ContainerId.PRIMARY` is the only production factory. Synthetic identities exist only for isolation tests and have no production key/root configuration.

Each successful unlock opens a new immutable `SessionEpoch`. Each `PrimaryOperation` has its own `OperationId`, immutable scope set and revocable copied-key lease. `PrimarySessionBinding` lets UI callbacks retain their originating epoch without retaining a key. UI callbacks issue the concrete permission set they need inside the original binding; they cannot revive after lock/unlock. Default operations grant READ only. Forks attenuate permissions and reject escalation.

Permissions cover READ, WRITE, LOCAL_EDIT, REMOTE_AI_EGRESS, BROWSER_UPLOAD_EGRESS, CREDENTIALS, BACKUP and existing generic EGRESS for restore/backup policy. Production does not issue the full enum set. A synthetic fixture may explicitly grant all scopes to exercise legacy behavior; this does not establish a production god-capability.

Item handles include container, originating epoch, item ID and immutable payload SHA revision. The repository validates identity before lookup, resolves the authenticated current record, checks revision and rechecks live authority. Policy decisions use that record, not caller-supplied copies. Presentation revisions also include crop state. Collection handles include container, epoch, ID and creation generation; public collection APIs accept handles. Internal legacy Primary UI string adapters resolve only inside an already captured Primary repository/session. No handle is serialized into legacy indexes, equality, copies or saved-instance state.

Deadline authorization uses Android monotonic elapsed time. Read, decrypt, network, commit and publication entry points check expiry; authorization never relies on delivery of the timer task. Existing serialized final commit/revocation gates are retained. Crypto preparation and PIN KDF work happen before final promotion checks. Lock invalidates admission first; the same VDEK on a subsequent unlock never makes an old epoch current.

The inherited resource registry owns jobs, streams, descriptors and session resources. Operation completion closes copied-key leases. Intentionally delivered UI/video resources transfer to the original session registry. Revocation cancels work, closes resources, removes owned temporary plaintext and wipes mutable key/buffer/bitmap copies where controllable. Reopening refuses incomplete/failed cleanup. Publication after an operation's key lease closes is intentionally permitted only for its still-live original epoch; decrypt, commit and fork require an open lease. Native, immutable string, JVM, codec, GPU and Chromium erasure is not claimed.

## Protected asynchronous path audit

| Path | Origin and final boundary |
| --- | --- |
| Index/items/collections/Favourite/Recently Deleted | Original operation-backed repository; typed media/collection handles; guarded metadata promotion and epoch publication. Load failures set CORRUPT or UNAVAILABLE instead of manufacturing an empty result. |
| COPY/MOVE imports and source-deletion callbacks | Captured operation, guarded streams and durable index/payload promotion. Platform deletion completion retains original items and operation. Sole verified copy protections and Phase 0 fault infrastructure remain. |
| Previews and scrolling | Validate the supplied item before memory/disk cache admission; identity contains Primary namespace, item and revision/crop state. Original-epoch delivery and registered mutable pixels. No alternate root or cache fallback. |
| Fullscreen images and Gallery thumbnails | Original owned job, read capability and epoch publication. Protected buffers/pixels are session-owned; Gallery thumbnail cache is cleared on revocation. Gallery remains external device media, not a second encrypted container. |
| Video | Scoped item resolution before payload lookup; bounded authenticated PGVIDEO1 reader/session registered to the original operation/session. Future reads fail and live DataSources close on revocation. Post-read authorization denies and wipes plaintext if revocation occurs during a read. Legacy playback conversion retains existing behavior and now requires WRITE; chunked playback works with READ only. |
| Crop/editor/local copies | Original handles and LOCAL_EDIT/WRITE authority; mutable decoded resources and jobs owned. Source resolution and final metadata/copy commits cannot choose a new session at callback time. |
| AI Edit/Create Image/Replicate lifecycle | Scoped references, original operation, explicit REMOTE_AI_EGRESS transport guard, owned connections/body/result streams, bounded/sanitized output and originating encrypted destination. Old results cannot import or publish after lock/unlock. Provider/model/moderation choices are unchanged. |
| AI settings | CREDENTIALS required at token save/removal, REMOTE_AI_EGRESS for network verification. The removal job is owned and final credential mutation is guarded. Standalone fake configuration adapters are used only by tests; production supplies the captured guard. |
| Browser Vault upload | Immutable selected Primary handles plus chooser callback/origin identity. BROWSER_UPLOAD_EGRESS required before plaintext preparation. Owned staging files removed on completion/revocation. A stale request cannot hand off another chooser's selection. |
| Browser downloads, image/video/stream acquisition | Captured Primary repository destination; unscoped network opener refuses execution. Guarded network/result reads and durable encrypted import. Metadata probes capture their own operation, own jobs/connections and check after HEAD before fallback GET/redirect/manifest work. |
| Backup export | Primary-only root/snapshot and BACKUP/EGRESS authority. Missing-index initialization additionally requires WRITE and cannot occur over existing material. Synthetic foreign/browser plaintext canaries cannot enter the archive. |
| Fresh backup restore | Fixed Primary restore staging and original authentication-attempt token, not an unlocked-session/global destination. Attempt guard covers final install and rollback ownership. Existing backup archive behavior and fault tests remain. |
| PIN/recovery/biometric | Fixed `PrimaryKeySlots`; CREDENTIALS required for unlocked slot mutations. Recovery authentication and locked unlock use captured authentication/biometric attempt identities. PIN rewrap/recovery preserve the Primary VDEK and slot formats. Biometric enrollment commits through the Primary boundary. |
| WireGuard, settings and updates | Existing single Primary Browser/VPN behavior retained; Browser state callbacks retain their owner. Non-Vault update machinery and provider models are not redesigned. No update/release is published. |

Low-level format primitives remain compatibility implementations; their production callers carry scoped authority. Phase 1 does not make a second repository, root, key slot or browser profile selectable.

## Corruption and write behavior

Repository/UI state distinguishes EMPTY, LOCKED, UNAVAILABLE, CORRUPT and READY. Missing index plus durable or unrecognized partial root material fails closed. Authentication, parse, version and inventory failures propagate; they cannot become an empty writable Vault. Existing/partial PIN, recovery or biometric material blocks fresh setup even when the PIN slot is incomplete. Failed operations preserve verified ciphertext and use only their owned staging/rollback resources. No owner data was inspected, migrated or rewritten during development.

## Required negative test matrix

These are synthetic automated tests, not physical-device claims. All listed tests passed in the accepted candidate's required CI; the final closeout head also requires terminal green checks before merge. Integrated tests exercise actual adapters where appropriate; pure authority tests additionally prove denial before side effects.

| # | Requirement | Test evidence |
| --- | --- | --- |
| 1 | Stale epoch cannot decrypt | `PrimaryRepositorySessionTest.forgedAndOldItemsCannotReachPayloadOrMetadata`, authority ABA tests. |
| 2 | Stale preview cannot publish | `ProtectedMediaOwnershipTest.oldEpochCannotHandOffPixelsDuringNewSession`, `MainActivitySessionTest` queued delivery. |
| 3 | Stale metadata cannot commit | `PrimaryRepositorySessionTest.staleRepositoryCannotReadOrCommitAfterSameKeyReauthentication`. |
| 4 | Stale AI result cannot import | `Phase1AsyncDestinationTest.oldAiAndBrowserResultsCannotImportAfterReauthentication`; unchanged index bytes. |
| 5 | Stale Browser download cannot import | Same test rejects the original captured sink before the download opener runs. |
| 6 | Lock/unlock cannot revive callback | `PrimaryBindingTest`, queued Activity delivery and `BrowserUploadRequestTest` callback replacement. |
| 7 | Expiry without timer rejects work | `PrimarySessionAuthorityTest`, `ScopedIoGuardTest`, Browser acquisition and delayed PIN promotion tests. |
| 8 | Video denied/closed after revocation | `VaultVideoDataSourceTest` close/descriptor/wipe tests and integrated scoped-video expiry test. |
| 9 | Foreign handle denied before lookup | `PrimaryScopeTest` lookup counter; `Phase1PrimarySlotsTest.foreignIdentityRejectedBeforeAnyRootAccess`; repository foreign handles. |
| 10 | Foreign colliding preview denied | `PrimaryScopeTest` same ID/revision foreign identity denied before cache identity/lookup; preview entry point validates before cache access. |
| 11 | Corruption cannot become writable empty | `PrimaryRepositorySessionTest.corruptionNeverBecomesEmptyWritableVault`, frozen corrupt/version/index tests. |
| 12 | Partial slots/root block fresh setup | `Phase1PrimarySlotsTest.partialRecoveryOrBiometricSlotsNeverPermitFreshSetup`, `PrimaryVaultSetupGuardTest`, `PartialPrimaryRootTest`. |
| 13 | PIN mutation Primary-only | `Phase1PrimarySlotsTest.pinChangeAndRecoveryTouchOnlyFixedPrimarySlotsAndPreserveVdek`; foreign slot canary unchanged. |
| 14 | Recovery mutation Primary-only | Same test, READ-only denial and preserved Primary VDEK/recovery compatibility tests. |
| 15 | Backup excludes synthetic foreign | `Phase1AsyncDestinationTest.primaryBackupCannotEnumerateSyntheticForeignRootOrPlaintextCanaries`; archive inventory checked. |
| 16 | AI result retains original destination | Old captured repository result test plus original-epoch Create/Image/Edit source audit. |
| 17 | Browser upload retains selected handle | `BrowserUploadRequestTest`; immutable handles/origin/callback, authoritative item policy and epoch guard. |
| 18 | Browser download retains destination | `Phase1AsyncDestinationTest.deadlineDuringBrowserAcquisitionCannotPromoteIndexOrPayload`; original sink and guarded opener. |
| 19 | Recreation retains intentional authority only | `ConfigurationRetentionTest.explicitActivityRecreationRetainsOnlyInProcessUnlockedSession`, recovery recreation tests. |
| 20 | Process restart begins locked | `ConfigurationRetentionTest.destroyedActivityCannotRestoreAuthorityIntoNewViewModel`. This exercises cold authority reconstruction; actual process kill/reboot remains a physical checkpoint. |

Additional tests cover collection foreign/stale handles, scoped video foreign collisions and expired readers after re-unlock, read-only reconcile/backup denial, and delayed Browser HEAD cancellation. Existing seek/tamper/format/fault tests remain.

## Adversarial review and observed failures

An independent whole-branch reviewer inspected the recovered implementation at `570bc845897273de792e395eb2eada4fad177a4b`. It found unowned Browser metadata probes, video read-completion revocation, implicit write authority in reconciliation/legacy video/backup initialization, and AI credential-removal ownership. The fixes are in the reviewed milestone and its follow-up. The reviewer did not run tests or provide physical assurance.

An isolated test-only regression branch (`phase1/regression-probe`, `69a87547f7e700daeeaca577127e86ba7f515203`) tested unchanged pre-fix production source. [Run 36894047101](https://github.com/traynor1987/Private-gallery-/actions/runs/36894047101) observed exactly the two expected assertion failures: video returned after revocation, and READ-only reconcile was admitted. The RED harness succeeded because the product tests failed as predicted; this is not a passing product gate. Fixes were applied after those failures were independently observed. No claim is made that every added test had an observed pre-fix RED run.

Fresh adapter CI at `570bc84` compiled/unit-tested successfully but failed instrumentation: one PIN adapter reused an input wiped by the compatibility unwrap; three standalone AI UI tests supplied no session authority. The PIN adapter now preserves a temporary input copy and rechecks at final promotion. UI tests inject explicit synthetic authority rather than permit unscoped production execution. These failed runs are retained as evidence, not accepted as green.

## Compatibility and immutable evidence

The existing Primary VDEK, PIN/recovery/biometric envelopes, v1–v6 index readers, v6 writes, payload encryption, PGVIDEO1, collections/Favourite, edits/provenance, origin/vaultOnly, Recently Deleted and backup/restore formats are retained. No new production crypto format, root or owner migration exists. Existing bounded legacy playback conversion is not a new Phase 1 owner-data migration program.

All 27 immutable fixture objects verify against the unchanged manifest. Manifest SHA-256: `cd26479fe9b077d81cd52b0c674900f0c475508e7081324cee396512770cc7bc`. Both fixture directories have zero diff from authoritative main; instrumentation archive/expected copies are byte-identical. `Phase0FrozenLegacyTest` checks old envelopes/indexes/payload/PGVIDEO1/archive and current v6 output against frozen expectations. The frozen corpus was not regenerated.

Fresh local source checks passed: diff whitespace, frozen hashes/copies, backup exclusion source policy and mutation checks, future format vectors, Browser Node tests, no-secret scan and WireGuard-only production path. A packaged mutation test is intentionally skipped without a fresh APK; fresh CI must run the packaged verifier. Historical retained Gradle outputs are not current evidence.

## Performance review and limits

Ordinary operations copy an already authenticated scoped key lease; they perform no KDF or key unwrap per preview/media read. The UI epoch binding retains no key copy. Payload revision hex formatting is linear without per-byte formatting overhead. Previews retain bounded encrypted disk/memory caching; PGVIDEO1 retains authenticated bounded chunking and seek. AI/Browser preparations remain bounded and stream where supported; new checks are lightweight monotonic/identity guards. PIN KDF is only a credential flow and final authorization follows preparation.

No fresh physical latency, scrolling, startup/seek, AI preparation or upload preparation measurements are claimed. The signed device checklist records those against the owner's platform and retained baseline. Immutable/base64 strings, codec/GPU state, provider-side processing already submitted and OEM behavior remain bounded platform risks, not claims of perfect erasure or remote cancellation.

## Accepted candidate CI, signed packaging and owner physical evidence

Fresh remote verification on 1 October confirms candidate `3d0edcd3cf563204beb33698486948bdaa809924`, based on unchanged main `0a582c6458e2899dde3b1b7be57eba3f4f1c2b20`. [Push CI 36906894806](https://github.com/traynor1987/Private-gallery-/actions/runs/36906894806) and [PR CI 36906899844](https://github.com/traynor1987/Private-gallery-/actions/runs/36906899844) are completed/SUCCESS with every required step passing. Push checkout is the exact candidate; PR run metadata identifies that head while its checkout is GitHub's merge preview `714a3f72620a4cab7445a7aaf6dfb1de38e16a08`. Do not conflate those checkout identities.

Required evidence includes targeted setup JVM tests, complete JVM/lint/debug/test APK suites, source/merged/packaged backup policy and mutations, unchanged 27-object legacy corpus, identical instrumentation fixture copies, future vectors, Node, no-secret, WireGuard/notices, isolated Phase 0 evidence variant, targeted 10-case Primary-slot instrumentation and full 162-case API36 instrumentation. Logs confirm the previously RED permission/concurrency cases and genuine-empty control now pass. Historical earlier milestone CI is retained in Git history; it does not substitute for these replacement results.

[Signed Device Acceptance Build #53 / run 36912086850](https://github.com/traynor1987/Private-gallery-/actions/runs/36912086850) completed/SUCCESS on the exact replacement candidate. Checkout/build-sha identity, permanent-signer restoration, JVM/lint/tests, release packaging, source/merged/release-packaged backup exclusions, retired-runtime absence, APK signature and upload steps passed. Artifact-only workflow; no GitHub Release.

| Accepted identity | Verified value |
| --- | --- |
| Artifact | `private-gallery-signed-device-acceptance-apk`, ID `11186763627`, ZIP 22,824,135 bytes |
| Artifact ZIP SHA-256 | `e34114647fc6cae864c7284666598f0e42f71d93640cc35744654381601d0c8b` |
| `build-sha.txt` | `3d0edcd3cf563204beb33698486948bdaa809924` |
| APK SHA-256 | `d9f94a396a58e5e146b1dfa50a27660571c5cb1dafc14ea2e7d4848c03834913` |
| Public signer certificate SHA-256 | `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17` |
| Package/version | `uk.co.traynor.privategallery`, `1.0.27`, versionCode `28` |

Downloaded artifact bytes independently match GitHub's ZIP digest, APK checksum and build-sha record. SDK `apksigner verify --verbose --print-certs` verifies one permanent RSA4096 signer and APK v2 signature; certificate equals recorded accepted Phase 0 #51. Independent compiled-APK backup exclusion verification passes. Owner's subsequent in-place installation is owner-reported evidence, not an independently queried installed signer.

**Owner physical evidence — aggregate PASS.** On 1 October 2026 the owner explicitly reports that focused physical acceptance of this exact replacement candidate and Build #53 PASSED. The replacement signed APK was installed IN PLACE over the existing Private Gallery installation. Owner's report: **"All working."** Record this as aggregate owner-reported PASS for the requested [focused checklist](docs/phase1/PHYSICAL_ACCEPTANCE.md), as the owner expressly instructed. The existing owner Vault/data remains in use. No uninstall/data clear is authorized or reported. No individual test observations, device/OEM/Android/WebView versions, latency measurements, recovery/credential mutations, fault outcomes or destructive tests are invented. No recovery secret was requested, received or recorded.

The superseded `77c95d14339309ba5ec1dbab6e4c6db82276bb3f` and signed #52/run `36903070049` remain withdrawn historical evidence. Neither is the accepted production candidate.

### Documentation-only closeout and integration gate

This closeout changes only this report and the physical acceptance evidence document. Accepted production tree `app/src/main`: `b0ad0423db157c625fd0002ca53822c842b63822`; release Gradle blob: `59c6f973c73316a432f32bf3dd0ed760d6171967`; signed workflow blob: `2e47e07f35598431169cc3263321b8cdfb1cdf4c`. Compare the full candidate-to-closeout changed-file inventory before integration: all non-document tracked inputs must be byte-identical. This establishes accepted release-input equivalence; it does not claim a newly signed APK for the documentation descendant.

The final documentation head's fresh push/PR run identities and terminal conclusions are recorded in PR #57 before merge. Merge is allowed only with unchanged accepted release inputs, no genuine review blocker and terminal green final checks. Use expected-head guarded normal merge, then verify exact resulting main and its full post-merge Android CI. No Phase 2 or public release follows.

## Post-candidate setup admission correction

The owner paused physical acceptance after [RED probe 36902691921](https://github.com/traynor1987/Private-gallery-/actions/runs/36902691921), source `cc733b15c7a39f95e3ac1f59bbf118ae45012002`. Its successful harness observed three expected product assertion failures, including `inaccessibleNestedPrimaryMaterialCannotPermitFreshKeyCreation`; it is RED evidence, not a product pass. The guard blob `37f4aa3367b9727dade8e42bab6fb24075151de0` is identical to the superseded candidate. `walkTopDown` could skip inaccessible nested directories while only top-level listing failure was checked. `PinVaultKeyStore.isConfigured/create` consumed that result and could admit fresh key creation.

`PrimaryStorageInventory` replaces permissive traversal with checked NIO enumeration and NOFOLLOW attribute reads. Missing paths require a readable/searchable parent inventory and an explicit missing-path stat; files, unknown entries, special types, links, inaccessible/failed traversal and partial material deny. Existing empty directories are completely enumerated. Parent/child identity, modification time and names are rechecked; unavailable file identity denies. Restore-stage absence and Vault inventory share one parent snapshot. Traversal and listing are bounded to 1024 visited directories and 4096 entries per directory; excess inventory denies instead of consuming unbounded resources. The missing-index path uses the same check. No crypto formats or owner data are rewritten.

Related tests cover inaccessible nested material and empty directories, execute-permission failure, missing-index denial, unknown/unreadable files, missing/non-directory parents, dangling/root/nested links and cycles, unavailable filesystems, traversal bounds, and genuine initial key creation/unlock. A separate test-only [probe branch](https://github.com/traynor1987/Private-gallery-/tree/phase1/setup-admission-red), `cbc84d9b265a6376eb2eef15be8e8cc25239d0cf`, retains unchanged superseded production inputs and adds permission/concurrency probes plus a genuine-empty positive control. [Run 36905797503](https://github.com/traynor1987/Private-gallery-/actions/runs/36905797503) completed successfully as a RED harness: exactly five named admission assertions failed, with zero errors; genuine-empty setup/key creation passed. This independently reproduces the permission flaw and setup/restore concurrency gap on unchanged superseded production source.

Independent read-only adversarial review confirmed the traversal fix and identified a pre-existing setup/restore transaction race. The correction serializes initial setup admission and final credential promotion with the repository Primary I/O transaction, with KDF preparation outside that lock and consistent I/O-before-preferences ordering. The real restore adapter is paused before root/stage resolution in the concurrency test; fresh key creation must wait and cannot save credentials while restore owns the transaction. The RED run observed the old adapter saving before that transaction released. The fix pass addressed this review finding; at that correction milestone no second reviewer or physical assurance was claimed. The later final reviewer and aggregate physical acceptance are separately recorded below. Checked snapshots detect observed changes; arbitrary out-of-process mutation after validation is not represented as an atomic filesystem guarantee. Exact replacement CI proved the targeted regression suite, full Phase 1 matrix, frozen fixtures, Phase 0 evidence package and Android instrumentation, as recorded above. Local Gradle execution was blocked before compilation because its absent wrapper distribution could not be downloaded in this environment; no local JVM/Android pass is claimed. Source checks passed: 27 frozen hashes and unchanged fixture copies, backup source policy, Python tests (one SDK-dependent packaged-mutation skip locally), five format vectors, three Node tests, WireGuard-only audit and whitespace. Required CI executed packaged mutation checks with its SDK.

The corrected candidate has green push AND PR CI, a new permanent-signer build on its exact HEAD, and aggregate owner-reported physical PASS. The original report's historical CI did not validate this correction. No artifact from `77c95d1` may be offered for owner acceptance.

Standing owner authorization (1 October 2026) permits routine milestone branches/commits/pushes/PRs, CI/retries, internal signed builds/artifact inspection, legitimate scoped fixes, evidence updates, and safe merge/post-merge verification once every mandatory implementation/automated/signed/physical/security gate passes. It does not authorize data destruction, signer/recovery changes, skipped physical evidence, a public release, or automatically starting the next phase. The owner has now supplied the required aggregate physical PASS and authorized final review, merge and post-merge main verification; no further merge confirmation is required.

## Final adversarial/admission review

A fresh independent read-only reviewer inspected exact `3d0edcd3cf563204beb33698486948bdaa809924` against the recovered original Primary-only requirements, inherited `docs/phase0/SECURITY_CONTRACT.md`, audit sections 25–28, actual production call chains and relevant negative/compatibility tests. Verdict: **ready to merge**, no substantiated Critical or Important source blockers. The coordinator independently verified remote CI, RED harness evidence, signed artifact identity/signature/exclusions and owner-authored physical acceptance. The reviewer did not rerun Android/JVM suites, inspect the signed APK, measure performance or observe the device; it did not claim independent execution of the 20-case matrix.

The review confirmed checked NOFOLLOW inventory and bounded traversal; shared initial/final setup/restore transaction; immutable epoch/capability checks and serialized final commits; authoritative handles; scoped AI/Browser transports and original destinations; preview identity; revoked video read-completion denial; unchanged crypto primitives/frozen fixtures. Its only note was stale pre-acceptance documentation, corrected by this closeout.

Explicitly considered and set aside: closed-lease publication remains valid only for its original live epoch; internal string collection adapters resolve within a captured Primary repository; authorized known `.part` cleanup is interrupted staging rather than unknown/committed-material admission; bounded legacy video conversion is inherited and now WRITE-scoped; arbitrary external post-inventory mutation is not an atomic filesystem guarantee; synthetic cold reconstruction is distinct from actual process-kill observation. These judgments follow the inherited contract and retained residual risks, not a waiver of scoped ownership or fail-closed admission.

| Phase 1 exit gate | Accepted evidence |
| --- | --- |
| PRIMARY-only architecture / original-epoch operations / least authority | Production source review and negative matrix; no second production container/root/key |
| Revocation, deadline, read/commit/publication and async ownership | Exact candidate JVM/adapter/API36 checks plus call-chain review |
| Setup admission / corruption / partial state / serialization | Both observed RED probes, corrected real-adapter cases and passing genuine-empty control |
| Legacy formats and immutable fixture compatibility | No crypto/fixture diff, unchanged manifest, frozen/restore tests |
| Credential/recovery/backup/cache/video/AI/Browser scoping | Required matrix and final source review |
| Performance and platform limits | Architecture cost/bounds review; aggregate owner PASS, no invented latency/device telemetry |
| Required candidate push and PR CI | `36906894806` / `36906899844`, terminal SUCCESS |
| Permanent signer and exact signed candidate artifact | Build #53 / `36912086850`, verified APK/hash/source/signature/exclusions |
| Focused physical acceptance | Explicit aggregate owner-reported PASS, exact in-place upgrade; "All working." |
| Phase 1 report and PR evidence | This documentation closeout and PR #57; final head CI/integration identities recorded there |
| Scope boundary | No Phase 2 implementation, owner migration or public release |

## Exit recommendation

The accepted replacement production candidate satisfies Phase 1 implementation, automated, signing, aggregate owner physical and final admission-review gates. No genuine Phase 1 blocker remains. Preserve its source identity while updating documentation. Integrate only after final closeout checks pass, then verify exact resulting main and terminal green post-merge CI under the owner's standing authorization. PR #57 carries those final integration run identities without an endless self-referential report-commit/CI cycle.

This decision admits **Phase 2 review only**, not implementation. No Hidden container/master key/credentials/recovery/UI/media, Jenna Protocol, Primary↔Hidden transfer, Vault Camera, Hidden Browser, VPN→Tor or VPS backup is created or authorized. Stop after verified green main.

PHASE 1 RESULT: GO FOR PHASE 2 REVIEW
