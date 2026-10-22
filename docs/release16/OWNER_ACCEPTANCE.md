# Galaxy Z Fold owner acceptance — Private Gallery 1.6

Current recommendation: NO-GO for production use. This is a restricted browser candidate; VPN+Tor is deliberately unavailable. No device acceptance or packet-level routing results have been recorded. A failed or unavailable item stays unticked.

CI635 passed all configured automated gates. Candidate: `Private-Gallery-1.6-restricted-candidate-a4cc3ee.apk`, source `a4cc3eecd5a2a1cf744ca0c23157d89254165632`, APK SHA256 `40d980568205719e9371459149653ff0939d9a37262d9cd2faf8b7e2905929e6`. Retain your existing encrypted-data backup. Do not uninstall, clear app data, replace the signing key, or use actual sensitive browsing for testing. A TEST ONLY package is separate and does not prove production upgrade compatibility.

Record APK SHA256, source SHA, package/versionCode, certificate SHA256, Fold model/Android version, WebView version, date and network for each run. Expected production package is `uk.co.traynor.privategallery`, versionCode30, certificate `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`.

- [ ] Primary opens original WireGuard browser; existing navigation and settings work, with no Tor controls or Hidden tabs.
- [ ] Hidden requires its own PIN and opens the separate browser; Primary PIN cannot unlock Hidden.
- [ ] Tor Only starts without a required WireGuard tunnel, blocks navigation during bootstrap, and shows TOR only after a circuit is established.
- [ ] VPN+Tor works and its route is independently verified. **BLOCKED in this candidate: selecting it must show unavailable and start no fallback.**
- [ ] VPN Only verifies the app-owned WireGuard route before navigation and accurately states no Tor anonymity.
- [ ] Every enabled mode fails closed under Wi-Fi/cellular switching, airplane mode, service failure and VPN revocation. The unavailable mode never opens a page.
- [ ] Mode changes destroy old tabs/requests/downloads before a new transport becomes available. Rapid changes never show a stale Connected indicator.
- [ ] Primary and Hidden cookies, history, tabs, cache and settings remain separate across locks, process death and upgrade.
- [ ] A test image/video download appears only inside encrypted Hidden Vault. Check public Downloads, Gallery and MediaStore; test authorised Restore to Primary/Phone separately.
- [ ] Hidden auto-lock closes browsing, clears sensitive content and leaves no sensitive recents preview. No traffic continues after lock/background.
- [ ] Switching bottom-navigation tabs preserves authorised Hidden Vault authentication according to its timeout. This candidate intentionally closes browser tabs when leaving Browser.
- [ ] Ordinary Settings has no Debug / Acceptance card.
- [ ] Settings → About → seven version taps prompts fresh owner authentication. Wrong PIN/cancel/background cannot open diagnostics. Exit returns to Settings; access is not retained.
- [ ] Primary diagnostics reveal no Hidden information. Hidden diagnostics require the independently authenticated Hidden context.
- [ ] All 1.5 media operations, Copy/Move, both Restore destinations, Photo Picker, favourites, image editing, AI providers, screenshot settings and bottom navigation still work.
- [ ] Verified permanent-key APK upgrades 1.5 in place without loss of either vault's PIN, photos, videos, collections, favourites or settings.
- [ ] Fold closed/open, portrait/landscape and keyboard layouts have no clipped controls or oversized panels.

## Routing verification, beyond a connection icon

Use synthetic URLs and a controlled test server/DNS zone. Capture packets at the Wi-Fi gateway and, for VPN, the WireGuard endpoint; use a device capture method that does not replace the VPNService under test. Compare destination connections and DNS queries with both transports' actual endpoints. An IP-check website alone is insufficient.

For Tor Only: verify remote server sees a Tor exit, DNS for unique test domains does not leave the device/gateway directly, and only Tor relay connections carry browsing traffic. Block Tor relay connectivity mid-page and during a large image download; no origin connection or direct DNS may appear. Check process death/background.

For VPN Only: verify the chosen WireGuard endpoint and egress, pinned DNS, and failure after revoking the actual VPN Network. Try repeated navigation during teardown/reconnect. A VPN icon or fresh handshake alone is not proof.

VPN+Tor remains unavailable until an implementation pins Tor native outbound connections to WireGuard across teardown and packet captures prove both layers. Do not tick it based on two status labels.

This restricted WebView disables JavaScript, forms, frames, workers and HTTP redirects. Many sites will not work. It does not provide official Tor Browser's fingerprinting protections. Capture results are evidence for tested conditions only.
