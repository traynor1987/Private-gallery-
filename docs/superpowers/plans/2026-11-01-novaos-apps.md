# NovaOS Internal Applications Implementation Plan

> Execute inline for browser/integration, with independent app-presentation domains dispatched according to dispatching-parallel-agents. One final independent security review. Owner explicitly authorizes continuous work and release.

**Goal:** cohesive NovaOS1.7.10.2/code54 across existing applications.
**Architecture:** existing engines and authenticated owners; shared Nova presentation, distinct browser launch profiles.
**Tech Stack:** Kotlin/Compose, Java host checks, existing API37 workflows.
**Spec:** docs/superpowers/specs/2026-11-01-novaos-apps-design.md

## Global Constraints
- Same branch; original package/certificate/encryption IDs; API37 only; no uninstall/data clear.
- No direct browser fallback; actual JS/form/subresource paths verified before capability admission.
- Hidden PIN-only and exact authority revocation; explicit Evidence/AI image transmission consent.
- Local host checks then batched affected runtime checks; original signed53 baseline.

## Review Focus
- Switching VPN/Tor may never inherit cookies, bookmarks, logins or late callbacks.
- Hidden selection and Home transitions must not grant Primary encrypted authority.
- Camera/editor revocation must discard late plaintext and finalize encrypted recording safely.
- Provider previews/settings must not transmit anything without exact explicit consent.
- Eligible screenshot transitions must honor owner preference while lock/Recents/credentials remain secure.

## Tasks
- [ ] Browser/launcher: host RED tests for11-app migration, Hidden-only Tor, fixed VPN/Tor launch modes and owner-isolated state; implement launch integration and coherent shell; verify routing with existing fixtures and mandatory transport assertions.
- [ ] Evidence: add dashboard/adaptive UI tests, replace text-heavy Home with counts/actions/recent/search and Information page; retain original import/group/AI/export tests and controllers.
- [ ] Camera: add adaptive control tests; reorganize existing camera preview/top/bottom settings without controller rewrite; retain capture/audio/black/reading/volume/revocation assertions.
- [ ] Editor: add workspace/large-preview/collapsible-AI tests; reorganize existing PhotoEditor controls; preserve crop geometry, save-copy, consent and external-edit boundaries.
- [ ] Integration: shared-theme/modal/screenshot/Private Apps audit; focused host/JVM and grouped API37 run using existing workflow; inspect raw XML/screenshots, fix demonstrated failures.
- [ ] Independent security review: whole diff and runtime gates; resolve Critical/Important and freeze exact source.
- [ ] Release: baseline-compatible53->54 fixture, existing production workflow, original signer, in-place upgrade/restart; download/hash/signature verification, save APK and final recovery.

## Ledger
Ruling: supplied detailed specification plus explicit autonomous execution supersedes routine design/plan approval questions. Existing scratch clone is isolated to this task; use same release branch, no additional branch/worktree. Browser integration shares Main/Home files; app agents own disjoint Evidence/Camera/Editor presentation files and tests. Common NovaChrome is root-owned. No agent may push or trigger CI.
Baseline: remote73e89605; latest signed53; all87 remote heads max53. Existing104-case green run41354819734 and production41355731517 retain authoritative baseline proof; do not rerun baseline Android suites.
