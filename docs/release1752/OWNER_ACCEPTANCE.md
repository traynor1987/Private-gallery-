# Galaxy Z Fold acceptance checklist — 1.7.5.2

READY FOR OWNER ACCEPTANCE ONLY. The automated release gates and independent review passed. Physical acceptance is pending; every box below remains unchecked. See [the acceptance report](ACCEPTANCE_REPORT.md) for exact evidence and limitations.

Signed source: `ec05b23ec28a44730439bd1090c1e64afff8f140`. Package `uk.co.traynor.privategallery`, version 1.7.5.2/code 40, Android 16+. Original certificate SHA-256: `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`. APK SHA-256: `deddec834271291704806a25cb986aacd285b89f4346f10bad12f0e450577990`.

[Signed acceptance APK](https://chatgpt.com/library?file_id=libfile_0ab33b1f9eb08191a56e3ca961ea2978&account_id=personal). Install only as an in-place upgrade over code 39; never uninstall, clear data, reset PINs or replace keys.

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
