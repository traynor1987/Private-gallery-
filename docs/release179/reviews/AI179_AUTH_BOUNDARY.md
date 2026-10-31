# AI179 credential authentication-boundary adjudication

Date: 2026-10-31. **Important new finding confirmed — High, AI179-AUTH-1. Correction required before final security review.**

Scope: read-only trace of EvidenceCredentials backend operations, their captured lease/authentication monitors, and the immediate lock/revocation callers. No parser rereview, product edit, test execution, CI, delegation, commit or signing. The earlier R1/R2/R3 and test-follow-up dispositions are unchanged; this is an additional finding, not a reopening of their resolved causes.

## Finding and concrete path

`EvidenceCredentials.kt:29–30` implements `guarded` as `access.leases.acquire().use { lease.commit { action() } }`. The complete backend operation is passed as that action:

| Operation | Credential call site | Work performed while the authentication monitor is held |
| --- | --- | --- |
| Setup/replacement | `EvidenceCredentials.kt:46` | `AiCredentialStore.save`: Keystore access/possible key generation, AES-GCM encryption, AtomicFile staging/write/finalization (`AiCredentialStore.kt:25–33`). |
| Send-time key read | `EvidenceCredentials.kt:60` | File existence/length/read, Keystore access and authenticated decryption (`AiCredentialStore.kt:35–42`). |
| Remove | `EvidenceCredentials.kt:51` | AtomicFile deletion and filesystem existence check (`AiCredentialStore.kt:45–47`); active-key revocation occurs only afterward. |

This is the real authentication monitor, not merely a private credential-operation mutex. `EvidenceAccess.kt:99` delegates to the captured lease; `EvidenceAuthorities.java:27` holds Primary's commit gate throughout `action.run()`, while `:42–43` calls `HiddenSession.commit`, whose synchronized body invokes the entire action (`HiddenSession.java:142–145`). `EvidenceAccess.close()` requires the same retirement boundary (`EvidenceAccess.kt:119,134,140`). Hidden `lock`, `revokeOperations`, `onBackgroundAt`, and authority/current checks are synchronized on that same session (`HiddenSession.java:101,139,148,160,174`).

The exact Main caller confirms Primary uses `phoneTransferGate` (`MainActivity.kt:1746–1747`); transfer-generation retirement also requires it (`:978`). Main's lock path closes Evidence Locker before proceeding (`:1533–1543`); the owner/controller closure reaches `access.close`. Panic calls this lock path (`:733–738`). These call sites were inspected solely to establish impact, not to conduct another UI/startup review.

## Why this is load-bearing

A thread stalled in Keystore Binder/key generation, waiting for the shared alias `KEY_GATE`, or blocked on storage holds the authentication monitor for that duration. Concurrent Main-thread lock/retirement can therefore block before its authority-revocation work completes. Hidden background/lock notifications and session validity checks can also wait. The 50 ms route watcher is not a deadline when its guard needs a blocked authentication monitor. Coroutine cancellation does not interrupt Java monitor acquisition or necessarily abort synchronous Keystore/filesystem operations.

Calling setup/remove/analyze on `Dispatchers.IO` moves the initiating work off Main, but does not remove this lock contention. The process-wide alias fence correctly fixes R3 and must stay; nesting a wait for that fence inside the session monitor unnecessarily propagates unrelated provider/owner Keystore latency into authentication retirement.

The narrow demonstrated defect is an unbounded authentication-monitor hold and resulting potential UI/revocation delay. No device stall, ANR, deadlock cycle, post-revocation POST or data disclosure was reproduced or is claimed. Panic installs a cover before its lock call; this finding does not assert that the cover fails. It concerns completion of retirement, cancellation and key/session teardown.

CONTRACTS' stage/encrypt/verify-outside-monitor rule is stated for the encrypted catalogue store; it should not be misquoted as an explicit credential-backend API requirement. Nevertheless, the shared `HiddenSession.commit` itself expressly permits only short final promotion, and the actual credential call graph violates that boundary. The user's prompt lock/Panic and nonblocking-UI requirements make this a security-lifecycle issue, not merely a style preference. Moving UI resource cleanup outside the authentication monitor does not fix this separate backend hold.

## Minimal correction shape

Retain the public one-argument AiCredentialStore API, default alias `private-gallery-ai-provider-v1`, existing nonce+ciphertext format, provider IDs/AAD, canonical filenames, and owner-scoped generation/revocation semantics. Add a narrowly scoped staged backend capability for evidence rather than changing credential identity or removing authentication checks.

1. **Setup/replacement:** serialize same-owner/file operations with a dedicated operation mutex. Check captured authority and capture the credential generation; acquire/create the Keystore key, encrypt, write and sync a private ciphertext-only stage outside the authentication monitor. Reenter a short captured commit boundary to recheck original authority and expected generation, atomically promote the already-complete stage, and advance generation/collect affected keys. Revoke those keys/routes and clean up the stage outside authentication/state monitors. Do not place `AtomicFile.finishWrite` wholesale inside the gate if it still performs sync/write work. Preserve old ciphertext on pre-promotion failure; accurately report any post-promotion durability uncertainty instead of blindly replaying setup.
2. **Send read:** capture authority/generation in a short check, read and decrypt outside the authentication monitor, then recheck the same captured authority/generation before creating/registering or returning a usable send-key lease. Reject and wipe every read copy if retirement/removal/replacement won during I/O. Keep key acquisition after the one-shot consent claim. A stale read may finish in its worker, but must never become an admitted send key or trigger a POST.
3. **Removal:** keep same-file serialization and an authenticated short removal transition. Invalidate the owner generation and detach the canonical credential via a short atomic filesystem transition (for example, rename to an owner-private ciphertext tombstone), with stale staged replacements prevented from promoting. Cancel affected send keys/routes outside the authentication monitor and before potentially slow ciphertext cleanup. Perform deletion/cleanup/durability work outside that monitor; do not announce successful durable removal if the removal transition failed. Cover any AtomicFile recovery/staging companions so old credential material cannot be restored as current. Do not delete the shared Keystore alias.
4. **Lock ordering/state:** no authentication callback may wait on the long credential-operation mutex or Keystore gate. Do not retain the current state/generation gate across slow backend I/O if key validity/removal needs it. Use short state transitions (or atomic generation state) and one documented lock order; avoid a new `state -> auth` / `auth -> state` inversion. Authentication retirement callbacks should remain flag-and-enqueue operations, and key/route cleanup must remain outside those monitors.

Read precheck/recheck alone is appropriate for read admission; it is **not** sufficient for writing the canonical credential. A replacement performed outside a guarded final promotion would introduce a stale post-lock write. Conversely, simply moving `backend.save` to another dispatcher while leaving it inside `lease.commit` does not address this finding.

## Narrow integration consequence

`EvidenceAiDialog.kt:52` currently invokes `credentials.isConfigured()` on Main. That method takes the shared credential gate and checks the filesystem (`EvidenceCredentials.kt:37`); it can wait behind a slow credential operation even though it does not itself call `lease.commit`. Root should move this read to IO and publish the Boolean through the existing captured publication gate. This is the immediate call-site correction for the same blocking issue, not a broader UI review.

## Required verification after correction — not executed here

- Inject blocking backend barriers at key lookup/encryption/stage write, send read/decrypt and removal cleanup. While each is paused, prove Primary captured-gate retirement and Hidden `lock`/`revokeOperations` return without waiting for the barrier. Verify no admitted key/POST or stale promotion after releasing a retired operation.
- Race setup/read/remove/replacement across two accessor instances. Verify generation ordering, old-key cancellation, no resurrection from a stale stage, cross-owner isolation and wiping on all rejected paths. Keep existing R3 alias-first-use protection and disposable-alias tests intact.
- Verify pre-promotion failure preserves the existing credential and cancellation cleans only owned ciphertext stages; removal failure/durability behavior must be explicit. Keep credentials out of logs/catalogue/export and never test by deleting the real shared alias.
- Run grouped Android compilation and relevant instrumentation, including actual Keystore and UI lock/Panic responsiveness. No host source assertion can establish a timing bound for these operations.

No release/security approval is granted. Serialize the correction with the active implementation work, then re-review this boundary and its targeted evidence before final security review.
