# Galaxy Z Fold acceptance checklist — 1.7.5.2

NOT READY FOR OWNER TESTING. No1752signed acceptance APK has been built. Targetcode40 remains unreserved. This checklist records the required physical gates; no box is pre-approved by emulator tests.

Once a single original-key APK is admitted, record source SHA, APK SHA256, certificate and package/version here. Install only as an in-place upgrade; never uninstall, clear data, reset PINs or replace keys.

- [ ] Brief Recents visit preserves normal authenticated photo viewing (check Primary, Hidden and device Gallery separately).
- [ ] Android Recents never exposes sensitive photos, including held/cancelled gestures.
- [ ] Switching apps conceals previously visible photos.
- [ ] Home/background privacy remains intact, including screen lock/unlock and expired auto-lock.
- [ ] Panic Exit revokes both sessions, returns to neutral locked entry and never reopens Hidden.
- [ ] Screenshot enabled/disabled preferences work independently; PIN entry remains protected.
- [ ] Primary browser displays the full consent dialog, including long scrollable text, in both themes.
- [ ] Consent checkbox and accept/decline buttons work only through manual user action.
- [ ] Normal webpage interaction resumes after consent.
- [ ] User-initiated authenticated image upload still works with disposable media.
- [ ] WireGuard remains fail-closed when disconnected; no direct-network fallback.
- [ ] Hidden Tor Only and VPN Only remain functional. VPN+Tor remains unavailable.
- [ ] Existing encrypted photos/videos, PINs, Primary fingerprint and Hidden PIN-only access remain intact.
- [ ] Signed in-place upgrade preserves all data.
- [ ] Fold open/close and screen resizing preserve the same privacy/authentication rules.
- [ ] Continuous encrypted video playback, seeking/speed, camera modes, metadata/export, collections/favourites, AI Edit/Combine and metallic-blue launcher icon remain functional.

Known evidence limit: public lifecycle/focus callbacks do not uniquely distinguish overview from Home/another app. The native curtain/epoch protections have not been weakened. Previously displayed sensitive content may therefore remain deliberately concealed where classification is ambiguous. A fresh explicitly selected Hidden photo must not inherit an unrelated stale concealment flag. This distinction must be verified on the actual device rather than inferred from test names.
