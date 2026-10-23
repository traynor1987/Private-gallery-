# Private Gallery 1.7.1 Implementation Plan

> **For agentic workers:** Use executing-plans for each bounded implementation domain; dispatching-parallel-agents for independent files. User authorised autonomous execution and independent review. Root owns commits and GitHub checkpoints.

**Goal:** Consistent professional Compose UI with actual authenticated AI source thumbnails and preserved secure behavior.
**Architecture:** Refine existing UI and callback boundaries; shared theme/tokens/components without storage or transport rewrites.
**Tech Stack:** Existing Kotlin, Compose Material3, CameraX and GitHub Android CI; no new dependencies.
**Spec:** docs/superpowers/specs/2026-10-23-professional-ui-design.md

## Global constraints
Baseline23b4b1681736e7c9b6635a9c16184abfcc95b507. Version1.7.1/code34, packageuk.co.traynor.privategallery, original signing key only. No main merge, owner media operations, key changes, transport rewrite, face recognition or V2 import. Preserve full-screen black capture surface. Root alone edits MainActivity/shared design/release workflow; other domains have disjoint file ownership. Existing component signatures remain compatible.

## Review focus
- Lock or disposal during asynchronous thumbnail completion must clear and never restore sensitive previews.
- Changing either combine source must preserve the custom prompt and multi-image consent boundary.
- Cover portrait and landscape with large fonts/keyboard must retain reachable controls without overlap.
- Collection and media identity/membership must not change when styling or switching vaults.
- Camera input/lock finalisation and Tor/VPN transport policy must remain unchanged.

## Tasks
- [ ] 1. Shared design/media/settings: PrivateGalleryDesign.kt, PrivateGalleryTheme.kt, MediaChrome.kt, MainActivity.kt; retain signatures and callbacks, refine tokens and reusable empty/status/selection surfaces, compact settings, accessible collection and grid cards. Add Compose viewport/interaction tests in ProfessionalUiTest.kt. Cosmetic-only changes need visual checks rather than mirrored unit tests.
- [ ] 2. AI editing: PhotoEditor.kt and dedicated UI-only helpers/tests. Test real thumbnail selection/replacement for both sources, exact prompt preservation, result preview/discard and lock disposal using synthetic images/provider; implement bounded in-memory authenticated preview cards and source picker without provider/storage changes. Preserve public PhotoEditor signature if possible.
- [ ] 3. Camera presentation: VaultCameraScreen.kt only plus focused tests. Refine entry/settings, accessible shutter/recording/zoom/status; retain black capture subtree and lifecycle calls. Verify existing VaultCameraUiTest and CameraHardwareTest.
- [ ] 4. Browser presentation: BrowserChromeBar.kt, BrowserPanels.kt, HiddenBrowserHome.kt and BrowserV2Home.kt only as necessary; compact consistent toolbar/settings, accessible controls/insets, no transport edits. Extend browser viewport tests without changing Tor assertions.
- [ ] 5. Release verification: app/build.gradle.kts, CI and release scripts; fresh full regressions and targeted UI screenshot suite, original-key code33→34 synthetic upgrades, release signing/package/hash checks. Keep prior1.7 evidence gate scoped to its old branch; never reuse1.7 app tests for changed1.7.1 source.
- [ ] 6. Independent review, resolve concrete blockers, visual screenshot inspection; final GitHub checkpoint and verified SHA, single signed APK and owner checklist. Record actual failures, physical limits and GO/NO-GO.

## Verification commands
Host: scripts/check_camera.sh, check_hidden_foundation.sh, check_hidden_browser.sh, check_video_container.sh, no_secret_scan.sh, node --test scripts/tests/*.test.cjs, git diff --check.
Android CI: ./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest; focused instrumentation and full connectedDebugAndroidTest; lintRelease assembleRelease; original signing/upgrade verifier scripts adapted narrowly to1.7.1. Local SDK availability must be checked; report CI-only results accurately.

## Execution ledger
Pre-flight: shared tokens retain existing public names/signatures. AI/camera/browser tasks consume theme only; root will not edit their files. User instructed autonomous execution, so no additional design approval stop. New branch remote baseline independently verified. Existing1.7 physical acceptance pending; owner has reconfirmed working1.6 Tor/VPN. No recorded outstanding1.7 critical data/security blocker; live Tor probes flaky.
