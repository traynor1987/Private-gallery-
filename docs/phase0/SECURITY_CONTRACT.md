# Private Gallery 2.0 Phase 0 security contract

Authority: owner Phase 0 instruction and `PRIVATE_GALLERY_2_0_ARCHITECTURE_AUDIT_2026-09-29.md` (136582 bytes, audited main a19218479eb9b9bcdb35ff0035fc78522263c891). Starting main on 29 September 2026 is that same SHA: no intervening changes. This document freezes the implementation contract; results/evidence are recorded separately, never inferred from a proposal.

## Scope

Primary-only hardening, synthetic immutable fixtures, fault tests, privacy/exclusion checks, nonproduction Browser/process experiments and specifications are authorized. Hidden production storage/keys/UI/discovery, transfers, Tor, VPS, aliases, owner migration, Primary VDEK rotation and releases are forbidden. Primary AES-GCM payload/index/PGVIDEO1 formats remain compatibility formats with unchanged authenticated contexts. No fixture contains owner data.

## Identity and least authority

`ContainerId` identifies an encrypted compartment. Production Phase 0 exposes only `PRIMARY`; test identities cannot open production roots. `SessionEpoch` is unique for every opening; it is in-process, never restored from Bundle/preferences. `OperationId` distinguishes issued operations. `ScopedItemHandle` binds container, epoch and immutable item identity/revision. Collection/reference/upload handles follow the same rule. An identifier is not authority.

`PrimarySessionAuthority` owns the live Primary key. `open(key)` transfers ownership of authenticated key material; `operationOrNull()` issues a revocable `PrimaryOperation` for that epoch, not a generic container selector. An operation owns its copied key and registered jobs/resources. A repository is fixed to the Primary root and that operation at construction. No callback may choose a repository/root/key from a mutable active-container selector.

Required operation API: `containerId`, `epoch`, `operationId`, `key` (internal compatibility bridge only), `isCurrent`, `checkValid()`, `commit(action)`, `publish(action)`, `own(AutoCloseable)`, `own(Job)`, `fork()`, `close()`. `key` cannot be public container-neutral authority. `fork` can only issue another lease for the same still-valid epoch; it must not acquire the latest epoch. Closing a key lease stops decrypt/commit but permits a previously queued publication to validate its immutable epoch without retaining a key. Publication still fails on revoke/expiry. Closed leases cannot be resurrected.

`commit` serializes final authorization/metadata promotion with revocation. A commit that linearizes before revoke can finish; a commit after revoke must not execute. Expensive preparation is outside that short authorization boundary. Ciphertext staging after cancellation must be quarantined/cleaned and never appear as committed media. Source deletion/public MediaStore publication require a valid operation and a verified durable destination receipt. No future Hidden-to-Primary API may accept a naked key or resolve a naked ID globally.

## Revocation, expiry and cleanup

State is LOCKED → AUTHENTICATING → OPEN(epoch, optional monotonic deadline) → REVOKING → LOCKED. Drawing the lock screen is distinct from cleanup completion. Revocation invalidates admission first; then cancels jobs, closes readers/descriptors, cancels editor/AI work, clears scoped UI/preview/index state and best-effort wipes owned mutable keys/buffers. Job completion/join determines asynchronous cleanup completion; no onDestroy/finally-only promise. Native/GPU/TLS copies are not guaranteed zeroized.

Use elapsed/monotonic time, never wall-clock time, for deadlines. Interactive background records a deadline; foreground may clear it only while still unexpired. Every protected check independently rejects expired sessions, even if Android delayed the timer. A ViewModel-owned timer supplies prompt revocation while alive. Configuration changes retain the intended session; process death always starts locked.

ABA example: epoch A → lock → epoch B → callback A. A fails forever even if Primary/Hidden is unlocked at B. Checking a global boolean is insufficient. Tests must cover both stale publication and durable commit, including callbacks retained after their IO job ends.

All decrypt, decode, durable writes, metadata promotions, UI publication, AI submission/results, upload preparation/handoff, download destination commits and deletion revalidate ownership. Authentication attempts bind purpose and attempt generation; enrollment/sensitive confirmation additionally bind the originating epoch. A late unlock result cancelled by lock cannot issue a session.

## Primary compatibility adapter

`AndroidVaultRepository(context, PrimaryOperation)` fixes legacy `filesDir/vault`, the existing VDEK and unchanged formats. Raw-key construction may remain only as a restricted test/legacy restore adapter, not a production Activity path. Low-level cipher stores remain format primitives, not navigation authority. Their final promotion callbacks receive authority checks where used from a live session. Read/parse failure is UNAVAILABLE/CORRUPT, never an empty writable snapshot or fresh setup.

UI may display a safe empty/loading presentation after lock, but cannot consume a corruption error as permission to create a Vault. Partially missing PIN slots or existing ciphertext block fresh setup. Metadata read failures must surface a fixed category rather than names/raw exceptions.

## Recovery ownership and lifecycle

Primary recovery wraps only the existing Primary VDEK. NOT_CONFIGURED → PENDING_CONFIRMATION → CONFIRMED. Persist only the encrypted pending envelope, never the secret. Owner re-entry must authenticate the pending envelope and compare its unwrapped key to the active Primary capability before atomic promotion. Lock/death loses the one-time display, not the Vault. Legitimate authenticated Primary access can restart a pending setup. A pending envelope is not exported as confirmed recovery.

An existing legacy envelope remains readable and valid; do not silently rotate or invalidate it. Legacy setup is explicitly distinguished from newly confirmed setup, and a successful re-entry can mark possession verified. Restored archives already prove possession by authenticated unwrap; install preserves their envelope bytes. Partial/corrupt preferences cannot masquerade as NOT_CONFIGURED. Failed setup/confirmation leaves old keys/ciphertext unchanged.

## Backup exclusions

Keep allowBackup=false and fullBackupContent=false. API31+ cloud and device-transfer rules explicitly exclude all applicable root/file/database/sharedpref/external and device-protected counterparts. Legacy rules remain explicit where applicable. noBackup/cache are excluded by platform contract. Under the owner's subsequent 1 October gate-closure instruction, exhaust supported application controls and document residual OEM transfer limitations; do not require an impossible universal vendor guarantee. See evidence/BACKUP_PLATFORM_ASSURANCE_2026-10-01.md. Verify merged manifest and packaged rules, not source XML alone. Narrow FileProvider cache paths remain unchanged; no broad root/files path is added.

## Browser experiment boundary

Only an isolated evidence build/package may expose synthetic profile probes. Never modify normal Browser's default profile as a test fallback. Feature-check MULTI_PROFILE/STARTUP_FEATURE_SET_DATA_DIRECTORY_SUFFIX before use; unsupported means NOT_SUPPORTED/CLOSED. Assign profile on a fresh WebView before navigation/JavaScript. Use synthetic local intercepted origins and reject external network requests. Record provider/API/process identity and individual storage results, not private URLs.

Named profiles/suffixes separate identity/storage; they do not prove Vault-key encryption of engine storage. Persistent and ephemeral residue must be measured after destruction/death/cold deletion/reboot as feasible. Supported APIs only; no live Chromium directory copy/WAL manipulation. Another ordinary process shares the app UID. A separate browser host is a containment candidate, not a hostile sandbox or measured performance claim.

## Admission and reporting

No GO until compatibility, stale/expired operation denial, resource cleanup, recovery confirmation, backup exclusions, immutable formats, corruption rejection, fault/restart invariants, future crypto/credential specification and green required CI are evidenced. Physical signer/upgrade evidence is supplied for exact signed #51; the focused ordinary Primary PIN/biometric/lifecycle/revocation checklist and private backup/matching-recovery possession have now been confirmed by the owner on 1 October, as recorded in PHYSICAL_ACCEPTANCE.md and the final closeout. This aggregate owner statement is not device telemetry, destructive testing or an owner-data restore. Synthetic clean-state restore and deterministic interruption evidence may satisfy application-level semantics under the owner's subsequent instruction. OEM/actual-power-loss/enrollment-invalidation limits must remain explicitly unmeasured residual risks, never relabeled physical PASS. Hidden Browser can remain separately gated without blocking Vault-only work, but that does not relax Primary safety gates.

Every result uses SOURCE FACT, IMPLEMENTED PHASE 0 CHANGE, TESTED EVIDENCE, PHYSICAL ACCEPTANCE REQUIRED, PROPOSED 2.0 DESIGN or UNRESOLVED. No implementation claim is based solely on documentation. Stop after Phase 0; no automatic Phase 1.
