# Phase 2 independent container design

## Authority and admission

The owner's 1 October 2026 Phase 2 instruction authorizes implementation, incremental commits/pushes, draft PR, CI and internal signed acceptance builds. Physical acceptance is mandatory before merge. No Phase 3, owner migration, public release, Hidden media/import/transfer/camera/browser, Tor or VPS work is authorized.

SOURCE FACT: GitHub PR #57 merged at f34db23445bd16fd45ae100474c19a2de8669695. Remote main equals that SHA. Post-merge Android run 36918293917 completed SUCCESS, verified through GitHub on 1 October 2026. Starting tree d1b4d307cb7bd480aee5cfb3e5b1a9d77fe0a861 matches recovered source. Existing worktree is clean. Local Gradle cannot obtain its missing distribution because network access is unavailable; Android execution must be evidenced by CI, never inferred from source review.

Read together: docs/phase0/SECURITY_CONTRACT.md, FUTURE_CRYPTO_FORMAT.md (F1-r2), HIDDEN_CREDENTIAL_POLICY.md (H1-r2), Phase 0 closeout, Phase 1 report, architecture audit 2026-09-29. The current owner instruction supersedes H1-r2's mandatory secret-plus-biometric ceremony and moves discovery into Phase 2. Record explicit format revisions instead of silently changing the frozen vectors.

## Boundaries and production naming

Retain the concrete Primary adapter, root, key slots, sessions, formats and fixtures. Add ContainerId.SECONDARY and concrete SecondarySessionAuthority/SecondaryOperation/SecondaryRepository types; never allow an active-container selector. Production strings use "Private space", "Unlock", "PIN", "Recovery key", "Lock and exit". Do not use the project codename in UI. The existing Hide Content preference remains presentation-only and must never mount or mutate the new container.

Secondary storage is filesDir/domain-store, a sibling of vault. Namespaces: descriptor, slots, index, payloads, previews, transactions, recovery, temporary, deleted. Only Secondary adapters receive this root. Primary backup remains its existing explicit allowlist and cannot include the sibling. Startup does not query Secondary to populate Primary UI.

## Cryptography and format

Implement reviewed F1-r2 whole records exactly: 156-byte canonical headers, AES-256-GCM, HKDF-SHA256, full-header AAD, independent random 32-byte master and 16-byte opaque container/master/object IDs, positive generation, fresh salt/nonce per immutable write, exact expected-context matching, purpose-specific bounds and strict parsing. Preserve the frozen known-answer vector file unchanged. No fallback to Primary/legacy on errors. No video implementation is authorized in this phase.

PIN and recovery slots use the reviewed 156-byte PGSLOT01 header and catalog binding. PIN policy: 12–64 ASCII digits, distinct from Primary's current credential at enrollment; explain that a long randomly selected PIN is stronger than a memorable pattern and a numeric PIN is not a high-entropy recovery key. Exact scrypt N=131072/r=8/p=1/32 bytes, no weakening fallback. Recovery: independent 32 random bytes, hex encoding, separate portable slot. Both use F1 domain-separated HKDF wrapping. Pending recovery is not complete configuration. Confirm possession by unwrap and constant-time comparison to this container's master.

Optional biometric convenience is an explicit H2 policy revision: a separate per-use BIOMETRIC_STRONG Keystore AES-GCM key unwraps this container's master through a separately versioned envelope and authenticated context. It is not represented as the old F1 secret-plus-device slot. Alias ownership includes opaque container/master/slot IDs. Missing or invalid aliases fail without key regeneration during decrypt. Actual enrollment authorization, ciphertext and active-slot digest must match; callback success alone is insufficient. No Primary alias, preferences or device credential fallback. Any Android-enrolled biometric may satisfy it; distinct aliases do not distinguish people.

## Per-item keys and deletion decision

Do not add per-item content keys to the production foundation. F1-r2 derives immutable per-object-generation keys from the random master, purpose and salt. Random item-key wrapping would add a new catalog/key-loss/backup/transfer lifecycle and change the reviewed format, while old encrypted index/slot backups could still preserve keys. Phase 2 has no media deletion or backups to validate that expanded lifecycle. Freeze F1-r2 for this foundation; Phase 3 must honestly offer logical item deletion with removal of app-controlled references, previews, temporaries and ciphertext, NOT per-item cryptographic erasure. Old snapshots and flash remnants may remain decryptable while the master exists. Only destruction of every usable copy of the container master/wrappers can provide container-wide cryptographic erasure, subject to external copies. A future per-item key format would require separately versioned review before writing media; no fake overwrite guarantees.

## Transaction and setup admission

Fresh admission uses checked NOFOLLOW inventory, parent checks, explicit file identities, readable/searchable directories and bounded traversal. Unknown files OR directories, links, partial structures, inaccessible nested material, inventory change and existing key material block setup. Missing root or a genuinely empty root is allowed only under a Secondary-only storage transaction lock. Do not reuse Primary's root selector or lock. Recheck before durable promotion.

Creation stages a journal and immutable generation containing independent descriptor, PIN slot, pending recovery slot and encrypted empty index/catalog. Sync files and parent directories. A single authoritative generation selection commits configuration only after recovery possession confirmation and authenticated readback. Interrupted setup retains its stage; authenticated PIN may resume pending recovery, but unknown material never becomes fresh setup. Journal/selection corruption is unavailable. Fault hooks remain test-only through storage abstractions. Credential/recovery changes create a new verified generation and atomically select it, retaining the prior valid generation until selection is durable; no Primary mutation. Errors use neutral fixed categories.

## Sessions and authentication

Use a concrete Secondary authority with immutable ContainerId, random SessionEpoch and OperationId, explicit scopes, short serialized commit/publication, original-epoch key leases, registry-owned resources/jobs and monotonic deadlines. Primary and Secondary may remain independently unlocked. Secondary revocation wipes controllable buffers, closes resources, cancels jobs and clears UI; it never revokes Primary merely for exit. Primary locking removes any Secondary visible route and revokes Secondary for safe product navigation. Process death begins with no Secondary authority. No saved-state serialization of keys/attempts/routes. Configuration changes may retain only in-process ViewModel authority, with protection applied before rendering.

Strong authentication default every day; choices every time/day/3 days/7 days. Last PIN authentication is monotonic in-process state only; a cold process requires PIN. Recovery/security changes reset biometric eligibility. Backoff starts after repeated failures, grows exponentially, caps at 60 seconds, never permanently locks out. No wall-clock-only authority. Independent auto-lock default immediately on background; options immediate/30 seconds/1 minute/5 minutes. Screen-off and explicit exit revoke immediately. Authentication attempt IDs/generations prevent delayed callbacks from creating authority after cancellation/background/lock.

## Discovery and UI

Reuse app-owned About Installed x5, Private Gallery version x1, Installed x4 mechanics; replace persistent discovery of the new domain with a bounded monotonic state machine. Wrong order, leaving About, background, restart and exit reset. Successful discovery issues only a transient authentication route. No key/root creation on discovery. Authentication/setup and shell force FLAG_SECURE irrespective of Primary screenshot preference. Ordinary Primary screens never show a row/badge/count/error/recovery/backup hint for the new domain.

Keep Hide Content settings independent: ordinary Primary privacy-presentation controls remain protected by Primary authentication and never become the Secondary credential. Existing persisted discovery state must not become a Secondary route. The new shell is minimal: ready state, lock/exit, independent PIN/biometric/strong-auth/auto-lock/recovery settings. No media enumeration/import, browser, network or transfer capability. No normal notifications or diagnostics describe the domain. After exit only Primary/neutral route remains.

## Verification and acceptance

Implement the owner's 42-case negative matrix with real adapters and synthetic data, including cross-key/credential/recovery/biometric rejection, slot mutation isolation, root/index/cache identity, stale sessions, setup faults/permissions/links/races, recovery lifecycle, disclosure semantics, backup exclusion and immutable fixtures. Real F1 synthetic payload round-trip must match a digest and reject every bound-context mismatch, corruption and unknown version. New assumptions get meaningful RED probes where pre-fix behavior exists; absent production features are not fabricated regression failures.

Retain complete Phase 0/1 CI. Add Phase 2 branch to push admission and targeted tests. Review actual production call chains before acceptance. Every exact candidate needs green push/PR CI, same-signer signed build, build-sha/APK digest/package verification, packaged exclusions, then non-destructive owner in-place acceptance. Report all source/implementation/automated/signed/physical/platform/proposed/unresolved evidence separately. Stop for physical acceptance; do not call Phase 2 complete prematurely. After owner PASS, standing authorization permits final report/merge/post-merge CI only, then stop.
