# Home 2.0 Preparation Implementation Plan

Goal: preserve useful Home implementation without activating an unfinished release.
Architecture: immutable Java layout + inactive Compose view; retain existing launcher and engines.
Tech stack: Java17, existing Compose/Material3, existing Nova icons.
Spec: ../specs/2026-11-04-home2-preparation-design.md

Global constraints: same release branch; 1.7.10.3/code55 unchanged; API37; no signing activation, new CI or transport changes. Preparation only until security baseline passes.

## Task 1 — desktop model
- [x] Write host behavioral checks for pages, folders, dock, drawer filtering, independent owners, immutable edits, bounded codec and malformed input.
- [x] Observe missing implementation failure.
- [x] Implement HomeDesktop in core/home/desktop and a host runner using the existing Java compiler invocation.
- [x] Run host checks and existing launcher/security control checks.

## Task 2 — inactive presentation
- [x] Add HomeDesktopScreen consuming caller-owned state/actions; no MainActivity references.
- [x] Build wallpaper-visible Home pages, dock, searchable drawer, folders, clock/date, editing controls and internal Quick Panel.
- [x] Compile if Android dependencies can be provisioned. Otherwise preserve Kotlin outside app source sets and explicitly report uncompiled preparation.

## Task 3 — review and recovery
- [x] Independent review model and preparation boundaries; fix material findings with behavioral regression checks.
- [x] Update recovery with exact progress, verification and limitations.
- [x] Commit coherent work with skip-ci; lease-check remote HEAD and push only if unchanged; independently read back SHA.

Review focus: malformed persisted state must not bypass owner/disabled-app checks; edits must not mutate earlier snapshots; hidden apps disappear from every launch surface; display changes never rewrite logical arrangements; no view or codec confers authentication.

Execution ledger: owner explicitly authorised autonomous implementation and isolated UI preparation; no additional design approval was required. Independent reviews are scoped as recorded in PREPARATION_STATUS.md. Home source is compiled but deliberately inactive. No release gate was waived.
