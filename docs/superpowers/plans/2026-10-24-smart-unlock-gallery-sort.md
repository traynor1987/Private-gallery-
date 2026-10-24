# Smart unlock and Gallery sorting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans inline and fresh independent review.

**Goal:** Code36 original-key acceptance with numeric unlock and six independent sort modes.
**Architecture:** Compose presentation over existing PIN/bio crypto, existing MediaStore paging, encrypted supplemental vault timeline/preferences without index migration.
**Tech Stack:** Kotlin/Compose, AndroidX BiometricPrompt, existing vault crypto, JVM/Android CI.
**Spec:** docs/superpowers/specs/2026-10-24-smart-unlock-gallery-sort-design.md and owner mission.

## Global constraints
Package uk.co.traynor.privategallery;1.7.3/code36, original certificate only. No real user data operations or credential/index-format reset. Hidden PIN-only; Primary biometric cryptographic unlock only. Preserve earlier branches, camera/AI/browser/icon and no1.8 work.

## Review focus
- Late Primary biometric callback after discreet PIN entry cannot override Hidden intent (Task1).
- Unknown legacy PIN length must not trigger prefix guessing or lose compatibility (Task1).
- Lifecycle recreation clears entered secret/Hidden route (Task1).
- New destination dates do not replace original capture timestamps (Task2).
- Hidden preference/metadata cannot be read or reused through Primary (Task2).

### Task1 — keypad and authentication
Files: new ui/PasscodeKeypad.kt, MainActivity.kt, HiddenFoundationDialog.kt; existing biometric attempt/policy code; new unit/UI/security tests.
- [ ] Test keypad digits/backspace/dots, known-length auto-submit, unknown-length explicit submission, failed clearing, background clearing and Fold sizes.
- [ ] Implement keypad without changing crypto/PIN formats; integrate neutral and Hidden routes.
- [ ] Test Primary-only crypto prompt, cancellation/generation invalidation, discreet Hidden PIN-only routing, lock/recreation and existing media decryption.
- [ ] Push milestone and record limitations/results.

### Task2 — sorting and authenticated timeline
Files: core/ui/VaultBrowsePolicy.kt, core/gallery/DeviceGalleryRepository.kt, repositories/timeline helper, MediaChrome.kt/MainActivity.kt, focused tests.
Interface: MediaSort six enums retaining legacy names; DeviceGalleryRepository.pagedItems accepts sort with default date-added newest; VaultItem supplemental added/capture values read only through authenticated repositories.
- [ ] Tests: six orders, invalid dates/fallback, duplicate ties, imported-old-date/new-addition, copy/move destination date and capture preservation.
- [ ] Implement allowlisted SQL paging and compact toolbar sort controls; persist per context with Hidden encrypted auth.
- [ ] Test round-trip isolation, corrupt/revoked metadata access, permission-limited Gallery and large deterministic collections.
- [ ] Push milestone and record actual results.

### Task3 — review, regression and signed upgrade
Files: release config/workflows/upgrade guard+fixture, V1_7_3_RECOVERY.md, docs/release173/REVIEW.md.
- [ ] Independent authentication/data-boundary review and focused regressions for findings.
- [ ] Configure1.7.3/code36 baseline signed1.7.2 sourcee0cc1f0d8ddb8482910f01a9de5abf1e9472332b, add exact35→36 fixture pair.
- [ ] Fresh JVM/build/lint, focused UI, full Android/security/browser/camera/AI and process restart checks.
- [ ] Original signer, package, synthetic production35→36 both-vault preservation/restart, independent APK hash/certificate and one saved acceptance APK.
- [ ] Final GitHub checkpoint/full remote SHA, physical Fold checklist; production GO only after owner testing.

## Execution ledger
Pre-flight: Task1 uses existing callbacks; Task2 keeps index/payload compatibility; Task3 consumes exact tested source. No shared-interface conflict. Ruling: owner explicitly directed autonomous execution; proceed with documented routine choices, no repeated design approval.
