# Phase 1 recovery and acceptance contract

## Independently verified recovery (new Work session, 2026-10-01)

Phase 0 PR #56 is merged at `0a582c6458e2899dde3b1b7be57eba3f4f1c2b20`. Main CI `36867322599` is completed/success. The original main checkout still had local HEAD `a192184`; its remote-tracking main and independent GitHub state established the authoritative baseline. It was not reset or reverted.

The previous isolated worktree **survived** at `/workspace/scratch/25515c66d81b/phase1-private-gallery`. Its interrupted Phase 1 head was `1e0a5eede6a2df14b09d86603322fa27814eb8e5`, based on merged main, with 25 modified tracked files and nine untracked source/test additions. Actual diffs were inspected and preserved. Remote inspection initially found no Phase 1 branch, PR or run. The earlier recovery paragraph in this document described the start of that previous attempt; it did not describe this new session.

The original local commits remain under `recovered/phase1-*` references. Git push authentication was unavailable, so the connected GitHub API created source-identical trees and fast-forward commits. Local versus remote mappings:

| Local preserved commit | Remote source-identical commit | Tree |
| --- | --- | --- |
| `1e0a5ee` | `c60ac008b5631fe3c2d43dddb1ba7e0e8d068753` | `1ee68f2c2115063db8c79c7223fcdbcd918401a2` |
| `28d5b27` | `570bc845897273de792e395eb2eada4fad177a4b` | `640e89f2654ff3f9d718f72c34968bfb73d440e1` |
| `743ac75` | `cd363624318bb6b03926bbb3ddbf4c2c7a92f2ae` | `efea63368fda4880ff8aac6c4a27827e95ad8bf0` |

The branch is `phase1/primary-scoped-security`, draft PR #57. Retained Gradle/XML output (392 JVM tests, no failures/errors/skips) is historical evidence only. Fresh local Gradle failed before compilation because the Gradle distribution cache did not survive and the distribution endpoint was unreachable. Fresh GitHub CI supplies build/test evidence; no old build output is accepted as current validation.

The superseded admission report is preserved in `docs/phase1/HISTORICAL_ADMISSION_REPORT.md`. Satisfied Phase 0 owner/admission gates remain closed. Phase 1 requires its own exact signed artifact and physical acceptance.

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
