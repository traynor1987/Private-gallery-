# Final media and settings pass

Goal: implement the owner's 21-section final defect/media/settings brief on current main, without a release.
Baseline: Private Gallery 5a6c7ffd4a434da21edf943e067f4b13f2e5a987; Player-video 61a0840.

## Audit and design
- MainActivity.onStop unconditionally backgrounds LockSession. Session/key are Activity fields; onCreate unconditionally locks. Retain only volatile security state in a ViewModel, never Bundle; skip configuration stops, handle screen-off explicitly. Handle orientation/screen/posture changes in-place so video and Browser hosts survive. Portrait outside fullscreen playback.
- Gallery key lambda calls deviceMedia[index], issuing load hints while keys are calculated. Use Paging itemKey (peek), deterministic _ID tie-break sort, invalidate the existing PagingSource rather than replacing Pager on observer events, retain grid during refresh, cache platform-sized thumbnails.
- Vault tiles have only composition-local state. Each new composition decrypts original; video extraction is full-sized. Add byte-bounded memory cache plus AES-GCM private persistent previews with authenticated item/hash/edit keys, bounded generation, serialized work, deletion invalidation and lock purge of plaintext memory. Never public files.
- IPTV audit: Media3 1.x, decoder fallback, HTTP factory, HLS/DASH MIME hints, player-lifetime listener, buffering/retry, fit/fill, screen-awake and bars cleanup. Adapt concepts independently; omit EPG, channels, PiP, cross-protocol redirects and fallback guessing.
- Shared internal Media3 UI for memory/content/web sources; explicit Browser menu reads only top-document video currentSrc or direct visible URL, never hidden request scraping. Reject blob/DRM/credentials. No cookie/header transfer. Owned VPN gate checks every open/read; losing permission stops player.
- Settings categories reuse controls and styling. Security/screenshots, Browser/history/data, VPN profiles, AI, appearance, updates/about, separated diagnostics; existing grid controls linked by explanation.
- AI provenance and immutable per-item restriction in encrypted index v5. Default-on preference applies to new AI saves. Restrictions propagate through local derivatives. Repository resolves authoritative metadata before restore/export/share. Toggle-off never releases old restrictions.

## Execution tasks
- [ ] Lifecycle policy and retained security state; regression tests for rotation/config/Home/screen-off.
- [ ] Gallery paging/invalidation/key and thumbnail cache; Vault encrypted preview cache and invalidation tests.
- [ ] Unified Media3 player and Browser explicit handoff; classification, VPN and cleanup tests.
- [ ] Index provenance migration, inherited containment, repository egress enforcement and AI save wiring; tests.
- [ ] Settings category navigation and reachability instrumentation.
- [ ] Full unit/lint/build/instrumentation CI, review, fixes, main green, physical matrix.

## Verification and rulings
Local baseline ./gradlew testDebugUnitTest cannot download Gradle (Network is unreachable). Android toolchain is absent. CI is the executable gate; no local test success is claimed. User explicitly authorised continuous implementation and push to main and excluded release publication. New plan/spec approval gates are superseded by that instruction.
