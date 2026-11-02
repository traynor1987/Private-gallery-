# Independent NovaOS Browser/Tor Beta security review

Source: 9efd19540a1d75efa1b30f4e23c9cbb096f93aff
Decision: APPROVED
Reviewer: /root/beta_security_review
Date: 2026-11-02

Upgrade fixture SHA-256: 4e8aaabcaf4d34c018340ad24129904a6210c49a7b1ea7641bf8f2a63d3a13a5
Runtime matrix SHA-256: d9992fc962ef86be563ecb1075f5e234823091cf3bb0c502a9f23440ddaf1478

## Owner scope and production decision

The owner explicitly accepted evolving Browser/Tor Beta capability for this release, separate Browser — Beta and Tor — Beta launchers, a professional application shell, and an accurate one-time privacy notice. Memory-only or vault-key encrypted engine storage is deferred; no engine replacement is required in this release. This changes the earlier strict provider-storage requirement recorded in HIDDEN_STORAGE_REVIEW.md and SECURITY_REVIEW_CONDITIONAL.md. It does not waive routing, authentication, authority revocation, isolation, or fail-closed requirements.

Within that expressly accepted scope, existing app-private transient WebView profiles may be retained in production; disabling native Hidden browsing solely because these profiles are not vault-key encrypted is not required. This is explicit acceptance of a documented limitation, not a finding that storage is encrypted or immediately erased. Provider cookies/site/profile data can reach ordinary app-private disk during a session and remain after exit/crash until cleanup runs. Force-stop, power loss, scheduling delays, or I/O failures can delay cleanup. This release must not claim memory-only browsing, immediate erasure, forensic protection, or Tor Browser-equivalent anonymity.

The new versioned acknowledgement stores only a nonsecret notice revision. Both HiddenHome and the Primary production browser destination show the disclosure before their browser content/runtime effects. Failed acknowledgement persistence keeps the notice displayed. Existing owner Home, Lock and Panic actions remain available. Notice copy explicitly describes app-private disk without NovaOS vault-key encryption and delayed cleanup, and distinguishes Tor from Tor Browser anonymity protections. App-owned bookmarks/history encryption remains distinct from provider storage.

## Source review

Reviewed runtime, broker, native child activity, cleanup, loopback proxy and manifest boundaries at e923972e, then the final candidate diff from 2b67f751a91961c138b4290c165415931e7c8b89 to the Source above. No unresolved additional Critical/Important implementation finding was identified in this reviewed scope. The native Activity diff contains strings and a corrected library-storage comment only. Browser transport engines, broker authority, profile lifecycle and storage cleanup implementations are unchanged.

Retained controls include a nonexported single-use child process, same-UID/claimed-PID Binder checks and live originating authority; app-owned immutable VPN Network binding; authenticated loopback proxy with VPN-specific DNS/socket routing or Tor Unix-socket transport; certificate-error cancellation; disabled file access; and transport revocation/child termination on authority loss. Whole-root journal/parent-child lock cleanup remains eventual deletion, not encryption. The unused parent profile initialization is not evidence that child authentication cookies are copied into that parent profile.

## Evidence scope and release controls

Exact-source fast-development run 41368247494/job 125032936376 succeeded. This reviewer independently inspected the downloaded artifact 13108297290, archive SHA-256 fd845324525f578d82cee2f01f7d013407e14f43dd7d26aa02755d0b34ccf073. Its source receipt matches the Source above, original UI exit status is zero, original API37 XML contains all 12 distinct Beta matrix cases with zero failures/errors/skips, and the selected-matrix verifier passes. Original focused JVM XML contains seven distinct cases with zero failures/errors/skips. This is focused Beta UI/authentication/boundary evidence, not a fresh live routing run or a full 266-case run.

Approval authorizes activating the reviewed production gates for this frozen application source and the owner's accepted Beta scope, including original-key signing and original signed code53-to54 upgrade/restart verification. It does not assert that signing, upgrade/restart, final APK verification, physical Fold acceptance or delivery have already succeeded. The production job must complete its certificate, identity, fixture-compilation, in-place upgrade/restart and artifact gates before an acceptance APK is staged. App-private provider disk retention remains the expressly accepted limitation described above; no encrypted/memory-only engine claim is approved.

Retained evidence remains source-scoped: 25 Android closure cases and seven focused JVM cases from 2b67f751, artifact 13107287404, archive SHA-256 1794260bd5bbf24eb1fc5b8c5d9a06c616c8ce427086a7eb6d4f6e0ca8227056; three actual VPN/Tor/network-loss cases from 4f4dd4f1, artifact 13105328926, archive SHA-256 c83d50172d840c6139ffa63e9c38b03e954e29cd670bc25347616e1707af7025. These are distinct historical checkpoints, not a final 266-case single-source pass. Their source/archive/XML checks are pinned by verify_beta17102_retained.py; this reviewer did not independently rerun device routing.

Reviewed inactive acceptance17102 controls require frozen-source runtime success, this review's exact source/hash, reviewed matrix/fixture hashes, and retained archive verification before signer access. Original code53 application baseline is not rebuilt: source f923d62de7696e731c2ec7809d772081ab0e1204; distributed archive SHA-256 fcc70278aca63b71e5321fdbd1f2e16235ff5fec7f3a3cb27b731280ec5e1707; APK SHA-256 8a13404b42720e5962478345fad964cdcc923a3115b632beb412a2813547a3a3. Both application APKs must match the original certificate SHA-256 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17 and package/version identities. Baseline-compatible instrumentation compiles before signer access with narrowly checked package transformations; only a disposable copy is resigned.

The reviewed fixture seeds Primary/Hidden encrypted payloads and metadata, browser settings and synthetic Primary cookie state, Private Apps, Evidence and Home, then verifies in-place code53-to54 upgrade and restart without uninstall/clear. Ciphertext and decrypted identity checks remain; Home migration does not require eleven enum values during baseline seeding and requires eleven only on the candidate. Local verifier unit tests passed 4/4. Actual fixture compilation, signed upgrade/restart, final APK certificate/hash verification and physical Fold acceptance remain required future evidence; no pass is asserted here.
