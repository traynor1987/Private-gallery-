# Browser / Private App ownership diagnosis

Baseline: release/1.7.10-private-home, HEAD e772b50a. No production edits at diagnosis time.

## Confirmed root cause

1. MainActivity.launchHomeShortcut sends BROWSER, TOR_BROWSER and PRIVATE_APPS through openBrowser(). On Primary, openBrowser changes Route.BROWSER and VPN state but never selects an ordinary-browser tab. BrowserV2Home renders browserV2Session.tabs.activeTab. Opening a Private App calls BrowserV2Session.openPrivateApp(), selecting its tab; Home leaves that selected ID intact. Thus Private App -> Home -> Browser deterministically renders the saved app's tab and app controls.
2. BrowserV2Session stores every tab in one BrowserSessionManager. privateAppTabs is only an ephemeral ID->App map. Ordinary Browser tab panels expose the full tab list. metadataSnapshot serializes the full list; destroyAll and restoreMetadata discard privateAppTabs, so an app URL can later be restored as ordinary Browser metadata. openPrivateApp currently calls newTab(url) before assigning its owner, exposing intermediate ownerless metadata through changed(). Popup tabs also do not inherit their parent's saved-app owner. onWindowFocus selects arbitrary retained tabs, allowing a background window callback to redirect the selected app.
3. Hidden cold launches are broker-capability based and transport mode comes from CLAIM, not URL. However Home and app Exit call HiddenVpnBrowserActivity.terminate(), which destroys every view/profile. MainActivity.leaveBrowserIfOpen and HiddenBrowserHome disposal close the runtime too. Existing denied-transport continuation is for background/connection loss, not Home. Full authorized Home retention needs coordinated presentation/lifecycle changes; a simple tab-selection patch cannot preserve these sessions safely.
4. Hidden Activity ordinary tab picker also exposes all views, including saved-app roots/popups, and chooses arbitrary last views on close. Saved-app launch lookup can select an existing popup instead of the root.

## Minimal safe repair

- Reuse BrowserV2Session and existing engines. Give ordinary Browser a remembered ordinary tab ID. Explicit Browser launch selects that tab, another ordinary tab, or an empty tab; never choose by last URL.
- Assign app owner before navigation/metadata notifications. Inherit owner for popups. Ordinary picker and encrypted browser metadata include ordinary tabs only. Keep app ownership during authorized runtime; when destroyAll ends it, remove app metadata rather than orphaning it.
- Ignore window-focus callbacks from background owners; stale callbacks cannot change which app the user opened.
- Scope local Hidden ordinary picker to ordinary views; saved-app reopen selects only matching roots. Do not alter Binder authority, process routing, cookies, transport, or continuation security.
- Parent must coordinate Hidden Home retention and MainActivity edits because these overlap the VPN security repair. No destruction-based workaround or most-recent-URL fallback is acceptable.

## Proposed files

- app/src/main/java/uk/co/traynor/privategallery/core/browser/v2/BrowserV2Session.kt
- app/src/main/java/uk/co/traynor/privategallery/core/browser/v2/BrowserLaunchOwnership.java (small pure owner/selection helper, host-tested)
- app/src/main/java/uk/co/traynor/privategallery/ui/BrowserV2Home.kt
- app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt (parent-coordinated explicit Primary ordinary launch call only)
- app/src/main/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenVpnBrowserActivity.kt (local UI ownership only, coordinated)
- focused host ownership regression checks and runner

No code/version bump, new branch, CI dispatch, push, transport/security edits, or immersive UI work.

## Verification constraints

Host owner transition tests can prove Primary ordinary/app ID isolation, popup inheritance, stale focus rejection, and no URL fallback. Existing Private Apps checks and Kotlin/Android compilation should run where local dependencies allow. Physical Home/Exit retention and routing/cookie isolation remain device acceptance requirements, and must not be claimed from static checks alone.

## Implemented draft and verification

Primary uses BrowserLaunchOwnership (opaque IDs only) integrated into actual BrowserV2Session lifecycle. Owner assignment now precedes renderer creation/navigation and metadata publication. Browser ordinary selection survives saved-app selection; focus callbacks cannot cross saved-app/ordinary owner boundaries. Browser picker/count and encrypted metadata projection exclude saved-app roots/popups. Runtime termination removes app tab metadata before dropping the owner registry, so it cannot be restored as ordinary browsing. Closing an ordinary tab also cannot reveal a saved-app tab as its fallback.

MainActivity modification is exactly one line in the Primary openBrowser destination clause. Hidden changes are local ordinary-picker/count filtering, stale ordinary-picker ID validation, ordinary-only close fallback, and saved-app root selection rather than selecting a popup.

Executed:
- scripts/check_browser_launch_ownership.sh: passed, after test-first missing-helper red.
- scripts/check_private_apps178.sh: passed all policy/catalog/store/command/icon/screenshot/DNS/fixture checks.
- scripts/check_hidden_browser.sh: passed 227 policy checks plus paged upload query checks.
- git diff --check: clean.

Android production-session regressions added in BrowserLaunchOwnershipTest.kt cover independent ordinary/app IDs and page-history metadata, stale focus rejection, app-free metadata notifications, encrypted ordinary-only round-trip, and app metadata removal on destroyAll. These were NOT executed. ./gradlew :app:compileDebugKotlin --offline --no-daemon failed before compilation because Gradle 9.5.0 is not cached and services.gradle.org is unreachable.

Residual limitations:
- Hidden Home/Exit retention remains parent/security-integration scope and unchanged by this draft.
- Physical WebView/backstack/cookie/routing behavior has not been device-tested here.
- Historical browser-session-v2.enc snapshots carry no app owner marker. Previously orphaned saved-app URLs in those snapshots are indistinguishable from genuine ordinary Browser URLs. New snapshots are owner-filtered; no URL heuristic or destructive historical-data migration was introduced.
- Existing bounded eight-tab eviction remains as shipped.

## Review follow-up: deferred restore fencing

Read /tmp/nova-navigation-review.md and confirmed its Important finding. MainActivity.restoreBrowserPresentation previously used persisted metadata equality as a concurrency fence. Since ordinary-only metadata deliberately omits app mutations, that comparison could accept a delayed disk read after live app ownership changed.

Implemented separate monotonic BrowserV2Session runtimeMutationRevision, advanced before each changed() publication (including renderer creation, app opening/selecting, page metadata/navigation, ordinary changes, reset and restore). Added restoreMetadataIfUnchanged(snapshot, expectedRevision) and made Main's authenticated/generation-checked callback use that guarded entry point. Persisted metadata remains ordinary-only. A null disk session still applies bookmarks only when the runtime revision is unchanged.

Added production-session deferred restore regression with real WebViews and a closed remote-networking gate. It holds an ordinary snapshot after the ordinary renderer exists, opens/navigates app A and switches B, delivers the old snapshot, and asserts rejection plus identical A/B/ordinary view objects, IDs, owner bindings, active B and A backstack metadata. It separately checks that an already-retained app selection and app-only navigation each invalidate a later pending restore. Existing encrypted round-trip test now verifies successful guarded restore into an unchanged fresh session.

Re-executed ownership host suite, Private Apps suite, and git diff --check: all pass. Attempt to invoke Android instrumentation again failed at Gradle wrapper acquisition before compilation/task execution (services.gradle.org unreachable); the new real-renderer regression is unexecuted. No CI, commit, push, transport/auth/lifetime edits or MainActivity changes beyond the approved Primary launch clause and exact asynchronous restore guard.

## Review addendum follow-up: trusted provenance and live-before-capture eligibility

This section supersedes the earlier historical-v2 limitation: ownerless legacy data is now actively quarantined from automatic presentation.

Implemented with parent-approved migration/API scope:
- New saves use browser-session-v3.enc and v3-specific authenticated AAD plus an authenticated version/ordinary-browser owner marker. The BrowserSessionSnapshot constructor/data shape is unchanged.
- loadForPresentation() reads only trusted v3. MainActivity's sole production automatic session load uses this API. No legacy URL or selection can be automatically adopted by that path.
- Historical load() keeps its binary signature and compatibility semantics (v3 when present, otherwise old v2). This preserves the old compiled upgrade fixture API; production automatic presentation never calls it.
- legacyRecoveryEntries() reads the original browser-session-v2.enc read-only under historical AAD. v3 writes/staging never replace, rename, mutate, or delete v2 ciphertext.
- Existing Browser menu now offers Recover previous tabs. Its small panel explains that old records lack app identity and requires an explicit record choice. Primary-authorized recovery handles bind listing/opening to the originating unlock/presentation; chosen IDs are reread from the encrypted file and runtime revision is checked after IO. The session then creates a fresh ordinary ID and retains all other owner contexts. Existing URL policy and network gate apply. No URL/catalogue inference or credential/profile changes.
- Guarded non-null restore now refuses any established saved-app binding even when the read captured its current revision. Null disk result may still allow bookmark-only display when revision/current-owner checks pass.

Added tests (unexecuted Kotlin/Android):
- Exact historical v2 encrypted serialization with three records, duplicate URLs under different IDs, and ambiguous selected app record. v3-only automatic load returns no snapshot, compatibility load returns old records, manual recovery lists all records, a trusted new ordinary snapshot using the same URL round-trips, and original v2 ciphertext stays byte-for-byte identical after v3 save. Mislabeled old ciphertext under a v3 filename is rejected.
- Real-WebView test captures the restore revision after app A already exists, rejects destructive restore, preserves exact view/ID/owner/selection, and accepts null bookmark-only result.
- Explicit recovered legacy choice gets exactly one fresh ordinary tab ID and does not inherit the legacy app-like ID/owner.

Fresh verification:
- Ownership host checks: pass.
- Existing Private Apps host suite: pass.
- Existing Hidden browser suite: 227 policy checks and paged upload-query checks pass.
- git diff --check: clean.
- ./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --offline --no-daemon: fails in wrapper acquisition before task execution/compilation; Gradle 9.5.0 is uncached and services.gradle.org is unreachable. Migration Kotlin tests, real-WebView tests, and ABI/device upgrade verification therefore remain unexecuted. No CI was dispatched.

No version change, push, commit, transport/routing/authentication/Hidden-lifetime changes, destructive old-data migration, or UI cosmetic work.
