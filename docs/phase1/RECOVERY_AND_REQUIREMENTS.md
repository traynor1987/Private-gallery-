# Phase 1 recovery and acceptance contract

## Recovery evidence (2026-10-01)

SOURCE FACT: Phase 0 PR #56 merged as `0a582c6458e2899dde3b1b7be57eba3f4f1c2b20`; main CI `36867322599` succeeded. Its tree is identical to final Phase 0 head `3e492116a6a4add53f8320b59049b341f19e2b53` (`054f1d395b78afafde2f1a9788b57b8541f7e88c`).

Inspected both extant checkouts, status/staged/unstaged changes, stashes, reflogs, unreachable objects, local and remote branches, PRs and recent Actions runs. The interrupted attempt left an admission-blocked report, not Phase 1 implementation. No Phase 1 commit, branch, PR or uncommitted implementation was found. There is no implementation to rebase or duplicate. The historical report is not current admission evidence.

The isolated `phase1/primary-scoped-security` worktree starts at the authoritative merged main. Fresh baseline `:app:testDebugUnitTest` passed on that exact tree. Older test reports are historical only.

## Recovered original requirements

These requirements are recovered from the original user-authored Phase 1 instructions through personal context; this is a traceable requirements digest, not a claim to reproduce the original specification verbatim. The current resume instruction and original acceptance boundary remain authoritative.

- Production PRIMARY only. No Hidden container/key/slots/recovery/UI, Jenna Protocol, transfers, Hidden Browser, Social Hub, VPS, Tor, disguises, owner migration or Phase 2.
- Preserve legacy VDEK/envelopes, index v1–v6 readers/v6 writes, payload/PGVIDEO1, metadata and backup formats and immutable fixtures. No owner-data migration.
- Typed container identity, immutable per-unlock epoch and per-operation identity; identifiers are not authority. Scoped revocable capabilities and handles, fixed legacy Primary root adapter, registry-owned jobs/readers/plaintext/cache resources.
- Monotonic authorization at read/decrypt/egress/write/commit/publication, independent of timer scheduling. Revoke first; reject ABA callbacks forever. Serialize final promotion with revocation; staged ciphertext never becomes committed media after revocation.
- Imports, previews, images, video, editor/crop, AI Edit/Create/Replicate, Browser uploads/save/stream/download, backup/restore/recovery/biometric callbacks capture original container/epoch/operation/destination. Never select a mutable current Vault at completion.
- Container/item/revision cache identity; video readers deny after revocation; Primary-only recovery/key-slot mutation and backup inventory. Distinguish empty, locked, unavailable and corrupt; partial credentials/root must never permit fresh writable setup.
- Preserve all Phase 0 CI, compatibility, backup source/merged/packaged and mutation gates, future vectors, secret hygiene, WireGuard and notices checks. Review adversarially for ambient authority, raw IDs and stale callbacks.
- Exact candidate green CI and permanent-signer Signed Device Acceptance Build (artifact only, no release). If manual dispatch is required, stop with exact branch/SHA/workflow.
- Same-signer in-place physical acceptance, no uninstall/clear. Record candidate/APK hash/signer/device/OEM/Android/WebView. Test ordinary PIN/biometric/data/media, revocation/lifecycle, AI/Browser and recovery/backup. Never infer physical acceptance or collect recovery secrets.
- Do not merge solely for green CI. Stop at the original acceptance boundary. Result is GO FOR PHASE 2 REVIEW only when all mandatory gates pass; otherwise NO-GO FOR PHASE 2 with exact unresolved gates.

## Mandatory negative cases

1. Stale epoch cannot decrypt.
2. Stale epoch cannot publish a preview.
3. Stale epoch cannot commit metadata.
4. Stale epoch cannot import an AI result.
5. Stale epoch cannot complete a Browser download into active Primary.
6. Lock/unlock cannot revive old callbacks.
7. Expired deadline denies even if the timeout task has not run.
8. Registered video reader is denied/closed after revocation.
9. Foreign container handle is rejected before lookup.
10. Colliding foreign item ID cannot resolve Primary preview cache.
11. Corrupt index cannot become an empty writable Vault.
12. Partial key slots cannot permit fresh setup over an existing root.
13. PIN change uses only Primary slot abstractions.
14. Recovery uses only Primary slot abstractions.
15. Primary backup cannot enumerate a synthetic foreign container.
16. AI result remains bound to originating destination.
17. Browser upload remains bound to originating selected handle/request.
18. Browser download uses its captured destination.
19. Configuration recreation retains only intentionally valid in-process authority.
20. Process restart is locked.

## Report and evidence

`PRIVATE_GALLERY_2_0_PHASE_1_REPORT.md` must cover starting main, Phase 0 integration, final candidate, changed files, before/after architecture, container/epoch/capability/registry/legacy adapter/handles, deadline/revocation, async audit, AI, Browser, cache, video, recovery/slots, backup, corruption, faults, all negative cases, fixture hashes, CI, signed build, physical evidence, risks and Phase 2 recommendation. Label source facts, implementation, automated test evidence, signed build evidence, physical evidence, future design and unresolved items separately. No secret material.
