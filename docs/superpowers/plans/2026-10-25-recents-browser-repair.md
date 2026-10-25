# Private Gallery 1.7.5.2 Implementation Plan

Goal: correct selection-scoped preview concealment and any reproduced general Primary WebView modal defect without weakening privacy or routing.
Architecture: keep native curtains and capture protection intact; distinguish previously displayed selections from new authenticated user selection. Test the actual Primary BrowserV2ProductionDestination/SecureWebViewFactory, not the unused legacy BrowserHome path. No external service testing.
Tech stack: Android 36/37, Kotlin/Compose, System WebView, Gradle/GitHub Actions.
Spec: owner execution request of 25 October 2026, preserved in V1_7_5_2_RECOVERY.md as scope and gates.

Constraints: one writer; no main merge, force push, owner data access, replacement signing keys, routing changes, consent bypass, or release before all mandatory gates. Target40 only after availability verification. Android16+ only.

Review focus: newly selected versus retained Hidden media; Recents versus actual background ambiguity; stale decrypted callbacks; renderer pixels versus DOM presence; all WebView networking stays governed by existing VPN policy.

- [x] Recover SHA/tree and existing release evidence; isolate branch from untracked older-main files.
- [ ] Reproduce stop-on-grid/new-selection Hidden failure and retained-photo concealment using isolated storage in HiddenVaultIntegrationTest.
- [ ] Exercise delayed light/dark consent, long scrolling content, transformed ancestor, native touches, actual screenshots, same-origin storage and foreground restoration using BrowserConsent1752Test and local asset.
- [ ] Run one baseline focused workflow across API36/37. Read actual XML/provider evidence before changing production.
- [ ] Correct only demonstrated root causes. Keep Recents protection when reliable public evidence cannot distinguish overview from other external focus. Do not add timing-based auto-uncover.
- [ ] Run host security checks and independent security review, then one affected focused workflow. Extend real gesture/Fold/upload tests where evidence warrants, without owner media.
- [ ] After focused green, full unfiltered regression/security and original-key synthetic39->40 upgrades on both SDKs; sign exact tested source and verify package/certificate/digest. Preserve all previous gates. Deliver acceptance only, never physical acceptance claim.
