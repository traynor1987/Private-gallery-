# Private Gallery Forensic Audit — 2026-09-22

**Scope.** Read-only audit of the executable repository at `7aa6bc8fe3611df0d8295e309baea36b519cb808`. This is not a release approval and does not claim physical-device success from source or CI evidence.

**Method.** Production claims below were traced from Compose/UI call sites through `MainActivity`, repositories/controllers, and storage/network code. A class or isolated test is not credited as a product feature unless that chain is present. GitHub Actions run #192 (`35704929341`) was green for this SHA; local Gradle verification was not repeated because the environment cannot reach Gradle distribution services. Read-only scripts `scripts/no_secret_scan.sh` and `scripts/verify_production_vpn_path.sh` both passed during this audit.

## 1. Authoritative repository and build state

| Item | Evidence | Result |
|---|---|---|
| Current main | `7aa6bc8` / `origin/main` | Audit baseline |
| App identity | `app/build.gradle.kts:9-16` | `uk.co.traynor.privategallery`, `1.0.25` (code 26) |
| Android configuration | `app/build.gradle.kts` | min SDK 26; target/compile SDK 36; Compose enabled |
| Build types | `app/build.gradle.kts:27-40` | debug and release; verbose Browser diagnostics only when `PRIVATE_GALLERY_ACCEPTANCE_DIAGNOSTICS=true`, otherwise false |
| Production signing | `app/build.gradle.kts`, `docs/RELEASE_SIGNING.md`, release workflows | Secrets are referenced but not committed; release task refuses missing signer. Permanent identity cannot be cryptographically compared without workflow-secret access, but workflow uses the established secret names and validates the produced signer. |
| Active production dependencies | `app/build.gradle.kts:70-78` | AndroidX/Compose, Biometric, Media3, Paging, WireGuard tunnel `1.0.20230706`, Bouncy Castle 1.79, `org.json:20240303`, Room/KSP. Room is declared but no `@Database`, DAO, `RoomDatabase`, or `databaseBuilder` exists: unused dependency/build surface. |
| Manifest | `app/src/main/AndroidManifest.xml` | Internet, media reads and installer request; backup disabled; only launcher exported; FileProvider non-exported and restricted to cache `updates/`; task-removal VPN service non-exported. |
| Workflows | `.github/workflows/{android,release,signed-device-test}.yml` | CI source scan/WireGuard audit/unit/lint/debug+test APK; separate manual signed acceptance and manual release. |
| PR/branch state | Git refs at audit time | no open PR supplied by repository; audit branch is clean and local only. |

## 2. Production feature inventory

Status vocabulary: **IWT** = implemented, wired, tested; **IWU** = implemented, wired, untested on the real production path; **Partial** = meaningful production path but a stated requirement is missing; **Regressed/physical failure** = failed real-device acceptance; **Deferred** = intentionally outside this APK/release; **Dead** = unreachable code.

### Gallery and media

| Feature | Classification | Production chain | Evidence / test assessment |
|---|---|---|---|
| Device Gallery grid and permission request | IWT | `GalleryHome` → `MainActivity.deviceMediaPages()` → `DeviceGalleryRepository.pagedItems()` → MediaStore PagingSource | `DeviceGalleryRepository.kt`; policy tests. Real grid exists. |
| Long-press selection and COPY | IWU | `GalleryHome` selection → `MainActivity.importSelected()` → `AndroidVaultRepository.import()` → `VaultImportCoordinator` | Production wired; unit tests cover import coordinator/use-case policy, not an Android MediaStore end-to-end copy. |
| MOVE with verify-before-delete | IWU | selection → `moveSelected()` → verified import → mark `DELETE_PENDING` → platform delete request → `finishSourceDeletionRequest()` verifies absent URI | `MainActivity.kt:954-990`, `AndroidVaultRepository.kt`; state/use-case tests. No instrumentation fixture proves full platform deletion path. |
| Immediate Gallery refresh after own successful MOVE | IWT | delete completion calls `deviceMedia.refresh()` | `MainActivity.kt:247-257`; production-wired. No Android test. |
| External MediaStore change refresh | **Partial** | `DeviceGalleryRepository.pagedItems()` is a one-shot Pager | No `ContentObserver` registration exists. Return-to-Gallery refresh only sees some changes. |
| Photo Picker | IWU | `GalleryHome` overflow → activity result → import | UI is in overflow, not permanent header. No Picker instrumentation test. |
| Compact Gallery header | IWU | `GalleryHome` compact title/overflow layout | Source-wired; requires Fold physical check. |
| Gallery image/video viewer | IWU | tile → `ViewerRequest.Gallery` → `FullscreenMediaViewer` | Same viewer is used for Gallery and Vault; no provider-level playback test. |

### Vault, crypto, recovery and media lifecycle

| Feature | Classification | Production chain | Evidence / test assessment |
|---|---|---|---|
| PIN-wrapped VDEK, encrypted index/payload | IWT | setup/unlock → `PinVaultKeyStore`/`PinEnvelope` → session key → `EncryptedIndexStore`/`EncryptedPayloadStore` | crypto and encrypted store tests; AES-GCM and Scrypt code present. |
| Biometric envelope | IWT | settings/setup → `BiometricVaultKeyStore` → Android Keystore/BiometricPrompt → `MainActivity.unlockWithBiometrics()` | policy tests; platform biometric result still needs device acceptance. |
| Offline recovery key | IWT | setup → `RecoveryVaultKeyStore`; recovery route → new PIN | recovery envelope/policy tests; `MainActivity` UI route wired. |
| Session wipe/auto-lock/secure window | IWT | `LockSession` + `MainActivity.onStop/onStart/lock` → `sessionKey.fill(0)` / `FLAG_SECURE` | unit policy tests. Device lifecycle not fully instrumented. |
| Streamed encrypted ingest/verification | IWT | Gallery/Picker/Browser source → `AndroidVaultRepository.importVerified()` → `VaultImportCoordinator` → private staging/cipher verification → encrypted index | coordinator/payload tests. |
| Duplicate detection | IWT | import metadata/hash comparison in repository | repository tests. |
| Restore and restore-and-remove | IWU | viewer action → `AndroidVaultRepository.restore()` → pending MediaStore write → byte/query verification → publish | policy/use-case tests; Android provider path uninstrumented. |
| Delete/reconcile interrupted state | IWT | `reconcile()` removes incomplete ciphertext and resolves pending deletion state safely | repository/state tests. |
| Private previews / thumbnails | IWU | `loadPreview()` decrypts item and decodes memory bitmap | no public thumbnail folder is used in current code; no complete Browser-origin thumbnail instrumentation coverage. |
| Vault video playback | IWU | decrypted bytes → `ByteArrayDataSource` / Media3 | byte source/video specification tests, no full encrypted video player integration test. |

### Collections, Favourite and crop/viewer

| Feature | Classification | Production chain | Evidence / test assessment |
|---|---|---|---|
| Generic encrypted collections/membership | IWT | Vault collection UI → `MainActivity` callbacks → `AndroidVaultRepository` → encrypted v4 index | `VaultCollectionsIndexTest`; real collection screens wired. |
| Delete collection without deleting media | IWT | `CollectionManagerDialog` → `deleteCollection()` | dialog explicitly states membership-only delete; tested collection state. |
| One favourite invariant and Jenna migration | IWT | `migrateLegacyFavourite()` / `favouriteCollection()` → encrypted `favouriteCollectionId` | collection migration tests verify fresh install does not create Jenna and legacy value is preserved. |
| Live favourite bottom-nav label | **Regressed** | `PrivateGalleryApp` loads `favouriteLabel` only in `LaunchedEffect(route)`; collection callbacks only refresh Vault state | `MainActivity.kt:1253-1268,2117-2122`. Change/delete favourite while remaining on Vault leaves nav stale. |
| Auto Crop, manual crop, undo/original | IWT | viewer crop → `VaultImageEdits.kt` → `AndroidVaultRepository` writes `ImageEditState` to index | crop geometry/policy tests. |
| Photo pinch/pan/double tap/pager arbitration | IWU, **physical retest required** | `FullscreenMediaViewer.ViewerImage` → native `ScaleGestureDetector`/`GestureDetector` and `graphicsLayer`; pager disabled when zoomed | `MediaViewerPolicyTest` proves policy only. Real device previously failed acceptance, so not physically accepted. |
| Video unaffected by photo transforms | IWU | viewer branches image vs video | source branch exists; no interaction regression test. |

### Browser, acquisition and bookmarks

| Feature | Classification | Production chain | Evidence / test assessment |
|---|---|---|---|
| Browser ownership / address / navigation | IWU, **physical regression open** | Browser nav → `PrivateBrowser` retained `WebView` held by `MainActivity.browserWebView`; `secureBrowserWebView()` | browser policy/callback tests, but instrumentation test intentionally uses failing fake WebView and proves only unavailable UI. |
| JS/DOM/cookies/viewport baseline | IWU | WebView factory enables JS/DOM, first-party cookies, viewport; disables database, file/content access, mixed content, third-party cookies and popups | `PrivateBrowser.kt:793-821`; no physical compatibility acceptance. |
| TLS and unsafe-scheme protection | IWT | WebViewClient cancels SSL errors; `BrowserNavigationPolicy` accepts HTTP(S) only | policy tests. |
| Normal page resource path | IWU | `shouldInterceptRequest()` records diagnostics then delegates to super | no browser resource is sent to Vault; CI test does not execute provider. |
| Popup/new-window support | **Partial / deliberately disabled** | current policy disables multiple/automatic windows; dormant constrained child callback exists | current modal traces show no child-window event. Do not call it a fix for blank modal. |
| Acceptance diagnostics | IWT source/CI; **physical trace only** | acceptance gradle property enables `BrowserAcceptanceDebugConsole`, ring buffer, sanitiser, runtime probe | console tests cover redaction; `7aa6bc8` redacts URLs. Must test more thoroughly against `wss`, IP literals etc before trusting it with private sites. |
| Screenshot to Vault | IWU, physical retest required | More action → `captureViewportSource()` → `onSaveToVault()` → import; feedback separate from `pageError` | draws WebView into memory; no URL/history mutation path. No real WebView acceptance test. |
| Download to Vault | IWU | WebView DownloadListener → confirmation → HTTP(S) `HttpURLConnection` with current cookie/UA context → Vault source | validated 2xx / filename policy; no physical server/session/cancellation test. |
| Long-press resource Save to Vault | IWU | native `HitTestResult` image/image-link → safe HTTP(S) resource importer with UA/referer/cookie origin constraints | no bridge/no original URL discovery. Tests are policy only. |
| Displayed-image capture fallback | **Partial** | blob/non-resource hit → `captureViewportSource()` | captures entire visible viewport, not verified element bounds. It is secure but does not meet element-bounded capture requirement. |
| Browser acquisition page preservation | IWU, physical retest required | acquisition feedback is independent of `pageError`; capture does not call navigation/stopLoading | source supports requirement; former device failure prevents acceptance claim. |
| Bookmarks | IWT | toolbar/More → `EncryptedBookmarkStore` → encrypted private file; list/open/remove → Browser gated navigation | bookmark store tests and UI wiring; clear data does not invoke bookmark deletion. |
| Bookmark privacy | IWT | encrypted storage; clear browsing data clears WebView state only | source confirms no analytics/diagnostics path. Memory buffer wipe remains hardening gap below. |

### WireGuard, settings, update and navigation

| Feature | Classification | Production chain | Evidence / test assessment |
|---|---|---|---|
| WireGuard production engine | IWT source/CI; physically connected previously | `MainActivity` → `WireGuardVpnEngine(OfficialWireGuardBackend)` → official `GoBackend.setState` | `VpnModels.kt:64-145`; real owner evidence confirms one connection. |
| Protocol-neutral abstraction | IWT | `VpnEngine` interface used by `BrowserVpnController`; only `WIREGUARD` production protocol | future OpenVPN not shipped. |
| Encrypted profile store/import/validation/list | IWT | Settings import picker → `VpnProfileRepository` / `EncryptedVpnProfileStore`; official `Config.parse` validation | unit tests and production list. |
| Active select and guarded removal | IWT | Settings actions → select disconnect/reselect; active removal rejected | repository tests; UI shows profiles and active label. |
| Android permission + engine-confirmed Browser gate | IWT source/CI; physical recheck lifecycle | `VpnService.prepare()` → connect; `BrowserVpnController.browserNetworkingAllowed()` checks CONNECTED | controller tests; actual permission/engine previously physically accepted. |
| Grace/background/lock/task removal ownership | IWU, physical retest required | MainActivity leave/background schedules 30s; lock immediate disconnect; non-exported `VpnOwnershipService.onTaskRemoved` calls owned registry only | controller tests only; Android process/task lifecycle is inherently not universally guaranteed. |
| External VPN non-interference | IWT source/CI | only engine with `ownsTunnel` is disconnected | `OwnedVpnTunnelRegistry`; no external-vpn instrumentation. |
| Settings wiring | IWT | all visible settings callbacks passed by `MainActivity` to Settings UI: PIN, biometrics, recovery status, theme/screenshots, browser prefs, VPN profiles/settings, updates, licence summary | `MainActivity.kt:334,1718+`; stale favourite label is nav state, not Settings. |
| Update check/download/install | IWU | Settings → `GithubReleaseUpdateService` → cache `updates` APK → FileProvider installer | update policy tests; release/update continuity requires signed device validation. |
| Route coverage | IWT except dead helper | routes SETUP/LOCK/RECOVER/GALLERY/VAULT/FAVOURITE/BROWSER/SETTINGS are dispatched. `PlannedDestinationHome()` is private dead code, never called | `MainActivity.kt:1154-1368`. |

## 3. Data-format and migration audit

The app does **not** use Room despite declaring it. The authoritative persistent Vault schema is `EncryptedIndexStore`: legacy v1 unmarked item stream, v2 collections/memberships, v3 image edits, v4 favourite ID. Reader accepts v1–v4 and writer always serializes v4 (`EncryptedIndexStore.kt:83-153`). Item payload encryption is separate.

| Migration | Reader behavior | Test state | Finding |
|---|---|---|---|
| v1 → v4 | legacy items read, next save v4 | direct legacy coverage | source-supported |
| v2 → v4 | collections/memberships read; no crop/favourite | no mature encrypted v2 fixture found | **coverage gap** |
| v3 → v4 | image edits read; no favourite | no mature encrypted v3 fixture found | **coverage gap** |
| Jenna legacy → favourite | pinned legacy collection converted only when legacy data exists | dedicated collection tests | source/test verified |
| Browser-created items | ordinary `VaultItem` through same coordinator/index | no complete source-specific thumbnail/viewer fixture | **coverage gap** |
| VPN profiles/bookmarks | private encrypted files but no explicit version header/migration mechanism | store/repository tests only | acceptable while format remains v1; future evolution needs migration design |

No Room database exists, so there is no SQL migration path to audit. Declared Room/KSP should be removed or an actual use justified; it presently increases dependency and compilation surface with no product behavior.

## 4. Security and privacy audit

### Confirmed controls

- No `addJavascriptInterface`; JS does not receive a native Vault API.
- `allowFileAccess`, `allowContentAccess`, file-URL universal access are disabled; SSL errors are cancelled; mixed content is never allowed.
- Browser remote acquisitions validate HTTP(S), gate remote requests on engine-confirmed VPN when required, and stream to encrypted staging rather than public Downloads/Pictures.
- Vault metadata/payload files are private encrypted storage; restore is the intentional user-facing MediaStore plaintext export and is verified before publish.
- Secure window is default; user must deliberately enable screenshots.
- Backup/data extraction disabled; provider is non-exported and only exposes cache update files.
- `no_secret_scan.sh` passed. No tracked keystore, VPN profile, private key, bookmark, or browser data was found. `verify_production_vpn_path.sh` passed.
- Acceptance diagnostics are compilation-flagged and production build config sets `ACCEPTANCE_BROWSER_DIAGNOSTICS=false`; verbose console/runtime probe code is not enabled in the normal release path.

### Findings and risks

1. **Acceptance console sanitisation is not exhaustively proven.** Current patterns redact HTTP(S), hosts, query-like pairs, bearer/auth/cookie patterns and long tokens, and a test protects the previously leaked full URL. It should be fuzz-tested before sharing private-site traces; WebSocket URLs, IPv4/IPv6 literals, encoded credentials and opaque identifiers are not all demonstrably covered by the inspected test.
2. **Memory hygiene hardening.** `EncryptedBookmarkStore.read()` and profile load paths use plaintext byte buffers without an explicit final `fill(0)` on every copied/decoded buffer. This is not a public persistent leak, but it falls short of the project’s otherwise deliberate key/buffer wiping discipline.
3. **Browser uses `HttpURLConnection` for downloads/image resources.** It manually carries a restricted cookie/UA/referer request context. This is intended but lacks provider-level tests for redirects, cookies and VPN drop cancellation. It must not be mistaken for WebView interception; normal page subresources are delegated to WebView.
4. **`REQUEST_INSTALL_PACKAGES` and cache FileProvider are legitimate update attack surface.** Release checksum/certificate checks and installer intent should be exercised in an actual signed upgrade test.

## 5. Licensing and dependency audit

**Confirmed:** production Gradle/source has the official WireGuard Android tunnel dependency; the WireGuard-only audit script passes; no OpenVPN/OpenSSL/LZO/OpenVPN3/AGPL code appears in the Private Gallery production dependency/build path. The separate `private-gallery-openvpn2` worktree is not a dependency and was not altered.

**Gaps / owner decision:**

| Decision | Valid choices | Consequence |
|---|---|---|
| Private Gallery licence | (A) publish a clear proprietary/all-rights-reserved licence, (B) choose an OSI licence compatible with shipped dependencies, (C) make the repository private until a licence is chosen | Repository currently has no top-level `LICENSE`; legal permissions to recipients are ambiguous. This needs owner/legal choice. |
| Third-party notices in distribution | (A) package complete NOTICE/licence texts as APK asset and link it from Settings, (B) include them in release assets with an in-app durable link, with legal review | Root `NOTICE` and docs exist, but Gradle does not package root NOTICE into the APK. Settings gives a summary, not the actual notice text. |
| `org.json` | (A) remove unused dependency if confirmed unused, (B) document/package its exact applicable licence/notice after review | It is directly declared and not covered by the identified in-app notice. |
| Room/KSP | (A) remove unused declarations, (B) justify/add actual supported storage usage | No runtime Room code exists; unrelated licence/build surface remains. |

No release should be called legally prepared until the owner makes the Private Gallery licence decision and required distribution notices are placed in the release artifact path.

## 6. Tests and CI audit

| Gate | Present | What it proves | Limitation |
|---|---|---|---|
| no-secret scan | yes, Android workflow | tracked-source patterns | not build artifact/runtime memory analysis |
| WireGuard-only audit | yes, Android workflow | production text/dependency path excludes prohibited VPN fork strings | not a formal SBOM/licence scan |
| unit tests | yes | pure policies, crypto, storage format, controller behavior | extensive use of fakes; no Android provider/GoBackend execution |
| lint | yes | Android static lint | no device behavior proof |
| debug APK + Android test APK assembly | yes | compiles test APK | workflow does not execute instrumentation tests on an emulator/device |
| signed acceptance | manual | signer, release assembly, acceptance flag | does not run no-secret/WireGuard audit itself; physical testing separate |
| production release | manual | signs, validates certificate, version/tag, publishes APK/hash/metadata | does not run physical acceptance or migration upgrade test |

`BrowserHomeRenderTest` deliberately throws from a fake WebView factory and checks the “Browser unavailable” surface. It does not validate the actual Android System WebView. `CropCanvasGestureTest` and pure `MediaViewerPolicyTest` do not prove the real fullscreen pinch conflict is resolved. This explains why CI could be green while physical tests still fail.

## 7. Release/update audit

Release workflow is manual, main-only, requires an existing semantic tag, restores a temporary keystore from GitHub secrets, builds `assembleRelease`, verifies APK/certificate, checks APK `versionName` matches the tag sans `v`, then publishes APK, hash and metadata. Signed acceptance similarly uses the permanent signer and keeps artifact for seven days. `docs/RELEASE_SIGNING.md` correctly notes that permanent signer is required for in-place upgrades and first installation from a debug build resets data.

**Status:** workflow architecture is suitable, but this app is not release-ready: open source defects, outstanding physical acceptance, migration coverage gaps, and licence/notice decision remain.

## 8. Browser forensic history and regression analysis

### Timeline

| Revision | Change | Audit classification |
|---|---|---|
| `3985dbc` (18 Sep) | initial private Browser | baseline browser feature |
| `08d5aea`, `2a219f2` | defer WebView/navigation and responsive viewport | potential lifecycle/viewport changes, pre-VPN |
| `v1.0.21` / `8543f2d` | tagged pre-milestone Browser baseline | newest clear pre-VPN candidate; not proven to be the owner’s observed good APK |
| `ae38248`–`ee4b3c5` | download Vault path and require-VPN gate | candidate: new gate/lifecycle behavior |
| `f60f297`, `fe11288` | real WireGuard, profiles, Browser/VPN lifecycle | candidate: stopLoading/route lifecycle behavior |
| `433ba2d` | screenshot preservation/image save; temporarily enabled auto/multiple windows and added child path | confirmed behavioral change, later reverted |
| `9ae7783`, `7a8f913`, `adfc3f8` | gestures/device acceptance/bookmarks | compatibility additions |
| `86dbb48` | restores single-window policy to v1.0.21 values | current popup policy matches baseline |
| `52cdf1a`–`7aa6bc8` | acceptance-only diagnostics | diagnostic behavior; should not run in normal release |

### Last-known-good assessment

`v1.0.21` is the strongest **source** baseline: before VPN, acquisition, popup experiment and diagnostics, with the same core WebSettings (JS and DOM storage enabled; DB false; first-party cookies true; third-party cookies false; file/content false; mixed content never; safe browsing true; multiple/automatic windows false). It is not possible to assert it is the exact physically observed good build without the owner supplying that APK/version. The known physical statement establishes that *some earlier build* worked, not which commit.

### Current-to-baseline diff conclusions

1. **Not confirmed: WebView recreation.** Device trace reports `WEBVIEW_ATTACHED` / `WEBVIEW_REBOUND state=retained` for the same id. `MainActivity` retains the `WebView` reference. The blue-site trace which contained no navigation/resource events only proves a retained instance was attached/rebound; it cannot diagnose the site content path.
2. **Not confirmed: popup/new window.** Current traces show no `onCreateWindow`, child, permission or file-chooser event during the blank modal. Current settings also match baseline single-window policy following `86dbb48`. Adding a child WebView would be speculation.
3. **Not confirmed: network subresource interception.** `shouldInterceptRequest` records and delegates. Modal trace reports hundreds of subresource observations and no received resource/HTTP errors. This proves observation only, not success/execution.
4. **Focus is an evidence lead, not a cause.** Acceptance probes reported native/window focus capability but `document.hasFocus()`, `native_has_focus`, `root_has_focus` false. Existing repository history does not show an explicit focus-policy change, nor does source prove the site requires focus. A physical A/B is needed before changing focus behavior.
5. **Popup experiment was reverted.** `433ba2d` enabled automatic/multiple windows and child handling; `86dbb48` restored baseline false settings after traces failed to show child-window activity. That rollback is evidence-supported and should not be reversed blindly.
6. **Browser regression root cause is therefore unknown, but narrowed.** Candidate classes: Android System WebView/provider crash or behavior (owner saw Android’s WebView crash warning), focus/Compose attachment, or an unobserved JS/rendering compatibility condition. Cookie/storage settings, basic main navigation, retained instance, and explicit subresource interception are not proven root causes.

### Controlled physical A/B required

One acceptance-only build should expose **one selectable, reported variable at a time**, without changing release behavior:

1. **Focus A/B:** current versus explicit `requestFocus()` after actual attach/window-focus. Trace native/document focus and whether application UI renders. This tests the only outstanding source-visible abnormality.
2. **WebView provider A/B:** same APK and same site with recorded provider/version; reproduce with current provider and known stable provider after owner-controlled update rollback only if Android allows it. This tests the externally reported provider crash without altering Browser policy.
3. **Diagnostics-on/off A/B:** acceptance trace injection/probes on versus off with identical web settings. This rules out diagnostics themselves perturbing fragile apps.

Do **not** A/B third-party cookies, mixed content, TLS handling, unsafe scheme access, or popup policy first: repository/device evidence does not support those theories and weakening them conflicts with intentional security controls. Modal test must be repeated after each only if blue site result identifies a shared cause; it currently has a distinct resource-heavy existing-page signature.

## 9. Placeholder, dead and incomplete-path audit

| Item | Classification | Evidence | Required action |
|---|---|---|---|
| `PlannedDestinationHome` | Dead | private composable in `MainActivity.kt:1358`; no call sites | delete during remediation or explain retained test fixture |
| Room/KSP | Dead dependency | declared, no Room APIs/annotations anywhere | remove or justify |
| OpenVPN compatibility | Intentionally deferred | `VpnModels` has neutral interface; audit script forbids production OpenVPN paths | no product work in this release |
| Secondary popup code | Implemented but currently unreachable by policy | `onCreateWindow` path exists while multiple windows false | retain only if justified; do not call completed modal solution |
| ContentObserver refresh | Missing | none in `DeviceGalleryRepository` | implement after audit review |
| Element-bounded displayed capture | Partial | `captureViewportSource` captures page rectangle | implement safe bounds or explicitly present screenshot fallback |
| Full index migration fixtures | Missing test coverage | no v2/v3 fixtures | add encrypted fixtures before release |
| Browser provider instrumentation | Missing production-path test | only fake unavailable-WebView instrumentation | physical A/B and, where feasible, real WebView instrumentation tests |

## 10. Mandatory requirements matrix

| Area | Requirement | Current status | Production wiring | Test coverage | Real-device status | Evidence | Action required |
|---|---|---|---|---|---|---|---|
| Gallery | Browse/Picker/Copy/Move safely | IWT/IWU | Gallery → MainActivity → repository/coordinator | policy/use-case | MOVE refresh needs retest | `GalleryHome`, `DeviceGalleryRepository`, `moveSelected` | Android-provider tests |
| Gallery | External MediaStore refresh | Missing | no observer | none | not accepted | no `ContentObserver` | implement observer/invalidation |
| Vault | encrypted ingest/verify/delete safety | IWT | all importers → coordinator/payload/index | crypto/store/coordinator | source/CI only | vault core files | signed device data-path test |
| Viewer | Gallery/Vault fullscreen | IWU | ViewerRequest → FullscreenMediaViewer | pure policy | not fully accepted | viewer source | instrumented viewer test |
| Viewer | pinch/pan/double-tap | IWU | native gesture detectors/graphics layer | policy only | previously failed; retest | `FullscreenMediaViewer.kt` | physical regression test |
| Crop | auto/manual/undo/original | IWT | viewer → repo/index | crop tests | source/CI only | edits/repository | optional device retest |
| Collections | generic memberships | IWT | Vault UI → repo/index | index tests | source/CI only | collections core | none before remediation |
| Favourite | generic favourite/Jenna migration | IWT | repo/index | migration tests | source/CI only | v4 favourite id | none |
| Favourite | live nav label | Regressed | stale Compose state | no UI test | not accepted | `LaunchedEffect(route)` | remediate |
| Browser | ordinary modern HTTPS rendering | Regressed/unknown | retained secure WebView | policies/fake UI only | blue site fails | history/physical traces | controlled A/B |
| Browser | blank modal | Regressed/unknown | existing-page WebView | no real mechanism test | fails | resource-heavy no-child trace | diagnose after A/B |
| Browser acquisition | screenshot preserves page | IWU | memory capture → Vault | policy only | previously failed; retest | `captureViewportSource` | real WebView test |
| Browser acquisition | download/Vault | IWU | listener → HTTP source → coordinator | policy/coordinator | source only | `PrivateBrowser` | network/session/tunnel tests |
| Browser acquisition | long press image | IWU | hit-test → resource/capture | policy only | source only | `BrowserImagePolicy` | real WebView test |
| Browser acquisition | element-bounded fallback | Partial | viewport capture fallback | none | not accepted | capture source | implement/refine |
| Bookmarks | private add/open/remove | IWT | UI → encrypted store | store/policy | source/CI only | encrypted bookmark store | memory hardening |
| WireGuard | official real engine/profiles | IWT source/CI | activity → engine → GoBackend | repo/controller | connected once; lifecycle retest | VPN models | Android integration tests |
| WireGuard | fail-closed/lifecycle | IWU | controller + activity/service | fake controller | not fully accepted | BrowserVpnController | physical lifecycle test |
| Settings | visible options wired | IWT | activity callbacks | policy tests | source/CI only | SettingsHome | regression tests for state |
| Updates | signed check/download/install | IWU | GitHub service/cache/FileProvider | policy | update acceptance needs retained proof | update core/workflow | signed upgrade exercise |
| Recovery | PIN/biometric/offline key | IWT | activity + key stores | unit tests | source/CI only | security core | locked/unlock device test |
| Security | exported/intent/TLS/bridge | IWT source | manifest/WebView policies | unit/text audit | source/CI only | manifest/browser core | SBOM/security review |
| Privacy | no secret/public plaintext | IWT with hardening gaps | private ciphertext; explicit restore/update cache | source scan | source/CI only | scripts/core | wipe buffers, diagnostic fuzz |
| Database/migrations | v1-v4 read/current write | Partial | EncryptedIndexStore | v1/v4 coverage | source only | index store | add v2/v3 fixtures |
| CI | all release gates meaningful | Partial | Android/release workflows | run #192 green | CI only | workflows | execute instrumentation; gate signed workflow scans |
| Release | signer/tag/update compatible | Partial | release workflow | workflow validation | no new release acceptance | release docs/workflow | all blockers/owner decision |
| Licensing | notices and app licence clear | Missing decision | docs/root NOTICE only | WireGuard script | N/A | NOTICE/Gradle | owner licence + package notices |

## 11. Remediation analysis (not implemented by this audit)

Priority order after owner review:

1. Make favourite label state a single observable source refreshed in the set/delete callbacks; add Compose regression test.
2. Add MediaStore `ContentObserver`-backed Paging invalidation and Android tests for external removal plus own MOVE success/failure.
3. Add v2/v3 encrypted index fixtures and Browser-source image/video preview/viewer tests.
4. Resolve long-press fallback honestly: native safe element bounds and crop only when reliable, otherwise visibly offer Screenshot to Vault; do not claim viewport screenshot is element capture.
5. Conduct the controlled Browser physical A/B; implement no WebView security change until one hypothesis is confirmed.
6. Owner chooses own-code licence and distribution method; then package the notices/attributions and remove or account for unused dependencies.
7. Before tag: run all CI gates, full signed acceptance build, upgrade from currently installed production signer preserving encrypted data, and physical acceptance for Browser, VPN lifecycle, acquisition, zoom and MOVE refresh.

## 12. Final classification

This audit is complete as a repository/CI forensic record. It identifies both implemented production paths and the limits of their proof. It does **not** approve release. The blocking categories are:

- known source defects: stale favourite label, missing external MediaStore invalidation, incomplete element-bounded capture, v2/v3 fixture coverage;
- Browser blue-site and blank-modal real-device failures with no proven root cause;
- physical verification gaps for viewer gestures, VPN lifecycle and Browser acquisition;
- owner licensing/notice decision.

No production code or build behavior was changed while producing this audit.
