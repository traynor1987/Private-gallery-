# Preview App Mode implementation plan

Goal: improve the existing preview without replacing its browser or altering production.

1. Retain the selected WebView attached under opaque Home while the Activity is foregrounded. Pause tabs on tab/profile changes and Activity stop; never inject media scripts, fake visibility, or autoplay. Test retained DOM, attachment, synthetic user-started media and stop/resume.
2. Default to full-height App Mode; overlay a 48dp accessible menu with Home, Back, Refresh, Tabs, browser view and settings. Preserve the optional address bar and all existing navigation.
3. Recognise Google's account host, block embedded OAuth navigation and offer a confirmed external browser. Preserve the initiating tab. No client registration/callback contract exists for third-party YouTube login: do not invent a cookie-return mechanism. Explain shared preview cookie storage and provider/background limits.
4. Remove per-tile ellipses; expose Compose accessibility custom actions and a drawer-wide options selector. Keep context menus, labels and layouts. Use reviewed official high-resolution icon assets where retrievable; bound size/dimensions and cache verified PNG assets; fallback rather than upscale tiny favicons.
5. Run host/JVM checks, build code2/0.2-preview with the previous APK's development key, run focused synthetic Android17 tests once, and publish a new experimental prerelease asset. Disclose unavailable real-account/device checks.

Constraints: same branch and preview ID; no production/VPN/Tor edits, credential logs, user-agent changes, cookie import, native media interception or production signing. No background playback guarantee outside the foreground Activity; a foreground service cannot grant a third-party WebView a supported media API.


Implementation findings and rulings:
- Previous CI development key was ephemeral and not persisted. Recovering a signing private key from the APK is impossible. Version a public preview-only debug key for future continuity; Android will reject an update from the old GitHub certificate. Never auto-uninstall or clear preview data. Source layout schema stays unchanged; existing local previews with the matching development key can upgrade. This is a delivery limitation, not a bypass.
- Independent read-only review found two auth gaps: initial/address loadUrl bypasses shouldOverrideUrlLoading, and URL-only external requests lose tab ownership. Fixed with a common navigate method and an owned external request checked against active tab/profile/foreground; 13 policy host checks, synthetic Android fixtures added. No further review blockers reported.
- Background media means retaining the foreground Activity's attached WebView underneath Home. True screen-off/native background playback cannot be promised for arbitrary provider streams, so no fake MediaSession/foreground service, extraction or JS playback injection was added.
- Official metadata yielded 30 validated icons (24 at least192px); 20 use fallback. One interrupted PNG write was detected by hashes, re-downloaded and validated; maintenance helper now replaces staged PNG/metadata atomically.
