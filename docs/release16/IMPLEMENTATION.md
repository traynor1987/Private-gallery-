# Private Gallery 1.6 implementation plan

Goal: authenticated independent Hidden browsing with explicit Tor Only (default), VPN + Tor and VPN Only choices; discreet owner diagnostics; preserve V1.5 storage and media behaviour.

Binding specification: owner execution request of 22 October 2026. No automatic transport fallback; unavailable modes stay blocked. No main merge, signer replacement, vault-format migration or V2 work. Execute inline with independent security review and verified remote checkpoints.

## Design decisions and review focus

- Retain Primary browser and its settings. Hidden must never compose the Primary browser or share its profile/session/downloads.
- Fresh diagnostics authentication is transient and never saved. Seven About-version taps discover the auth prompt, not authority. Existing Hidden entry sequence remains intact.
- Separate supported WebView profile or process/data directory; unavailable isolation blocks browser creation. Block direct browser networking until transport enforcement is demonstrated.
- Tor dependency candidate: Guardian Project tor-android; verify version, licence, source, JNI ABI and startup/control API before adding it.
- VPN+Tor requires proof of Tor socket confinement to WireGuard across loss; an active icon/handshake alone is insufficient. Disable mode rather than silently substituting.
- Potential restricted-browser path: network-disabled WebView with native mediated requests, scripting disabled where no enforced WebRTC containment exists. Document compatibility restrictions explicitly.
- Browser lock, mode switch and route leave must revoke pending requests and destroy obsolete sessions before reconnect. Session restoration never restores auth.
- Hidden downloads must stream directly to Hidden encryption; no DownloadManager/MediaStore or shared plaintext cache.
- Review stale auth callbacks, async connection completions, profile deletion/reuse, renderer lifecycle, network loss during download and existing hidden-entry gestures.

## Milestones

1. Diagnostics: `MainActivity.kt`, `core/ui/SettingsCategory.kt`, new diagnostics discovery policy and tests. Remove DEBUG from ordinary cards; seven version taps -> existing PIN/biometric dialog -> transient DEBUG access. Reset on background/disposal/navigation. Update UI tests.
2. Hidden connection model: new `core/browser/hidden` policy, generation-bound transport readiness and unit/host tests. Explicit mode defaults/persistence; no automatic fallback; unavailable reasons.
3. Hidden browser integration: independently owned browser profile/process and UI, launch only with current Hidden authority. Native transport adapters, lifecycle revocation, tabs, settings and bounded encrypted downloads. Add Android tests for storage/auth and mode teardown.
4. CI/release: branch-triggered tests, versionCode 30/name 1.6, existing permanent signer and synthetic upgrade gates, evidence capture. Never weaken existing failures to obtain an APK.
5. Independent review; correct blockers; retain actual automated results and limits in recovery document. Supply candidate only with accurate signing/test status. Real routing and owner acceptance are separate from mocked tests.

Each coherent milestone: tests -> recovery update -> GitHub commit -> ref update without force -> independently fetched SHA match. Before large changes/builds/review, publish all recoverable work. If Android tests cannot run locally, label pre-CI checkpoints WIP.
