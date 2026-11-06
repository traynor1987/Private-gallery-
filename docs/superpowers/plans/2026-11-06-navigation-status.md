# Navigation and Status Implementation Plan

> Execute authorized owner specification in this session, with disjoint navigation/status work
> and independent final review. Parent alone owns Git, builds, CI and release activation.

**Goal:** Shared NovaOS navigation, contextual bottom App Mode menu, accurate live status.
**Architecture:** Existing authenticated callbacks; shared composables, lifecycle-owned status
provider; existing retained WebViews and upgrade lane. No new engines, crypto or routing.
**Tech Stack:** Kotlin/Compose/Android17 API37, existing Gradle and GitHub workflows.
**Spec:** docs/superpowers/specs/2026-11-06-navigation-status-design.md

## Constraints and review focus

- Preserve production package uk.co.traynor.privategallery, original signer, data and Home2.0.
- No Hidden entry/disclosure through public actions; lock/Panic reject stale menu callbacks.
- No duplicate NovaOS rows, system bar/status indicators or broken IME/gesture insets.
- No WebView recreation/reload or session/audio loss when opening/dismissing chrome.
- Default-network handover/loss races must never publish stale connectivity or invented5G.
- Lifecycle/authority retirement unregisters callbacks, closes sheets and prevents state revival.

## Task1 Navigation/menu

- [x] Add failing focused tests for common row, app-only overflow and sheet-first Back.
- [x] Reuse a shared row in Home/native roots and supported owned workflows; wire real actions.
- [x] Move all twelve existing menu actions to protected bottom sheet, preserving disabled states.
- [x] Verify native/app bounds, retention/audio, lifecycle/lock and Primary privacy; automate layout bounds. Physical Fold/rotation remains owner acceptance.
- [x] Commit coherent independently verified navigation patch.

## Task2 Status

- [x] Add failing model/provider tests with simulated battery, charging, saver and connectivity.
- [x] Implement callback-owned platform provider and compact native indicator; no polling/secrets.
- [x] Wire Home header and immersive app chrome, only with hidden real status bar.
- [x] Verify battery/charging, Wi-Fi/mobile/loss/validation, restricted info, layout and lifecycle.
- [x] Commit independently verified status enhancement.

## Task3 Release

- [x] Verify next unused version; pin actual signed58 archive/APK as preservation baseline.
- [x] Run host/JVM/build and focused API37 verification; retain original failed and passing reports/matrix.
- [x] Independent exact-source security review before original-key signing activation.
- [x] Run58-to-next in-place encrypted-data preservation/restart, independently inspect APK.
- [x] Publish direct asset and record exact source/certificate/version/hash, physical test limits.

- [ ] Physical Galaxy Z Fold acceptance: actual posture/rotation, Samsung fingerprint, live radio/VPN transitions and personal website sessions. See docs/release1711/CODE59_DELIVERY.md.
