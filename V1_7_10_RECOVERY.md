# Private Gallery 1.7.10 recovery

Status: baseline recovered; implementation has not started. No 1.7.10 APK or security approval exists.

Branch `release/1.7.10-private-home` was created from release-control commit `3fc699ae5ecfbc7678e835240c594e38daf4e1da`, preserving the controls and evidence after signed application source `19e3275ef17746e780d64e6a684d94bab2cabdc3`. The application and Gradle build source trees are identical between those commits.

GitHub Actions production run 41301316948, job 124832650609, succeeded, including original-key code 50 to 51 in-place upgrade, restart, and APK validation. Artifact 13081837273 archive SHA-256 `a7ad5e4d6c243e195a5d35db1f4a12711de1842bd6260aa318203e53ac691cb7` was independently downloaded and hashed. Its APK SHA-256 is `9f99f0e8df4dbdc6219aa147525866f0ace699b1b4ae9bab1f6b647ac4d7a178`; the APK bytes were independently hashed. The artifact reports package `uk.co.traynor.privategallery`, version 1.7.9/code 51, original certificate SHA-256 `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`, and successful synthetic upgrade/restart. Physical Fold acceptance remains owner-required. Focused run 41295673256, job 124815899187, succeeded at source 19e; its published artifact is 13079244254.

Do not rely on the older failure status at the top of V1_7_9_RECOVERY.md: the later successful production run supersedes it.

Next: verify remote branch and no competing writer; audit all issued version codes before assigning the next unused code above 51. Implement the full owner-approved 1.7.10 specification, including Primary/Hidden navigation isolation, startup migration, Home, launcher, evidence pages and guided import, screenshot lifecycle policy, and focused tests. Keep existing encryption and routing boundaries. Review CI trigger conditions before pushing implementation; batch focused API 37 checks. Perform independent security review and verify original-key in-place upgrade from code 51 before release. Never uninstall or clear data. No production signing or owner delivery is authorised until all applicable gates actually pass.

Environment observation: local Android SDK, emulator and Gradle distribution were unavailable; Gradle bootstrap failed with `Network is unreachable`. GitHub Actions provided the successful prior build and may be used for batched 1.7.10 verification after its workflow and access are reviewed. This observation does not establish that CI is unavailable.

## Launcher rules checkpoint

A small standalone immutable `HomeLayout` model now defines the eight shortcut IDs, ordered visible items, hide/show, pinning, reorder, reset and label filtering. `tests/home1710/HomeLayoutChecks.java` exercises those rules on the host using the JDK compiler entry point. This is an unintegrated foundation only: it does not persist, choose a vault, authenticate, render Home, or implement navigation. It must not be described as a completed launcher. No Android build or security test has been run on it. All remaining requirements above remain open.

## 2026-11-01 integrated development pass (not release-approved)

Remote HEAD independently recovered at 817b28998cecc2ee8f88d98aff0afd1cd1cf8bde. Audited app/build.gradle.kts at 87 fetched remote branch heads: highest issued version code 51; code 52 was unused and is now assigned to 1.7.10. Original baseline acceptance179 job 124832650609 rechecked successful.

Added actual Compose Home and activity routing for eight shortcuts, owner-bound encrypted Home preferences using existing revocable vault leases, layout serialization checks, vault-scoped metadata search, Home return controls, and activity-owned Evidence workflow pages. Sensitive AI/export credential pages retain capture protection. These changes require compilation, instrumentation, independent security review and further completion. No APK exists for 1.7.10 and no release gate is being asserted.

Local Java compiler is available via `java com.sun.tools.javac.Main`; layout and persistence host checks passed. Gradle bootstrap failed with Network is unreachable to services.gradle.org. Existing fast-development workflow now has one branch-specific grouped Home compilation job, excluding the unrelated default job for this branch.

Outstanding: complete guided evidence import review before registration, direct Startup Screen preference controls in Settings, Hidden child-process Private Apps/Home routing, robust Fold/session restoration, instrumented Home encryption/navigation/search/screenshot tests, review findings, complete API37 runtime gates, original-key code51->52 upgrade and signing. Do not treat this checkpoint as finished or deliver its APK as accepted.

### Continuation after independent review

Independent review identified stale Hidden Home authority after outside() and an overriding Evidence page Back handler. Home access now refreshes on explicit Home requests; the Home return control does not depend on a retired preference capability. Evidence pages use the hierarchical Back lambda. Home visibility/authentication startup state now remains in the existing in-process ViewModel across configuration changes. Added direct per-owner Startup Screen options, Hidden child Private Apps entry flag (UI-only; existing Binder authentication unchanged), and Home return on authenticated Hidden child return.

Added an in-memory guided import review: select/remove encrypted originals, verify bytes and run existing offline OCR, review/correct transcripts and timestamp provenance, edit group labels to split/merge, then one explicit catalogue commit. External multi-document imports preserve encrypted originals and defer evidence registration to review. Added Android Home storage isolation/encryption/startup/revocation test source; runtime execution remains pending.

First workflow attempt 41324315361 failed before any jobs started. actionlint identified a duplicate job-level `if` introduced while excluding the default fast job. Combined the branch predicate into the existing job `if`; actionlint now passes. No Android compilation or security gate passed in that failed run. Use the corrected grouped compilation next; do not repeatedly rerun the invalid source.
