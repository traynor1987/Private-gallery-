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

- [ ] Add failing focused tests for common row, app-only overflow and sheet-first Back.
- [ ] Reuse a shared row in Home/native roots and supported owned workflows; wire real actions.
- [ ] Move all twelve existing menu actions to protected bottom sheet, preserving disabled states.
- [ ] Verify native/app bounds, retention/audio, lifecycle/lock, Primary privacy, Fold/rotation.
- [ ] Commit coherent independently verified navigation patch.

## Task2 Status

- [ ] Add failing model/provider tests with simulated battery, charging, saver and connectivity.
- [ ] Implement callback-owned platform provider and compact native indicator; no polling/secrets.
- [ ] Wire Home header and immersive app chrome, only with hidden real status bar.
- [ ] Verify battery/charging, Wi-Fi/mobile/loss/validation, restricted info, layout and lifecycle.
- [ ] Commit independently verified status enhancement.

## Task3 Release

- [ ] Verify next unused version; pin actual signed58 archive/APK as preservation baseline.
- [ ] Run host/JVM/build before one focused API37 batch; retain original reports/matrix.
- [ ] Independent exact-source security review before original-key signing activation.
- [ ] Run58-to-next in-place encrypted-data preservation/restart, independently inspect APK.
- [ ] Publish direct asset and record exact source/certificate/version/hash, physical test limits.
