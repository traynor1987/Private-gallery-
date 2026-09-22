# Private Gallery Browser V2 Design

## Purpose and scope

Browser V2 replaces the V1 embedded Browser subsystem only.  It preserves the
existing application, encrypted Vault formats, collection/favourite migration,
permanent signing identity, and audited WireGuard GoBackend/profile lifecycle.
It is a compact private browser for ordinary modern HTTP(S) sites, not a tab
sync service, password manager, downloader, or external-app launcher.

The production route changes only after automated verification and owner
physical acceptance.  V1 remains in Git history for comparison.

## Ownership model

`BrowserSessionManager` owns a bounded collection of `BrowserTab`s.  Each tab
owns exactly one WebView supplied by `SecureWebViewFactory`; the session is the
only layer allowed to destroy it. Compose displays an existing tab WebView via
`AndroidView` and sends commands to the session manager. Recomposition, UI
state, VPN state changes, bookmarks, acquisition feedback, and Fold resizing
never recreate a tab WebView.

Tab destruction is limited to explicit close, bounded-session eviction, or a
renderer-process failure. A renderer failure records recoverable tab metadata,
destroys the invalid view, and exposes Reload; it never claims JavaScript state
survived. Browser session metadata is encrypted and restored only after Vault
authentication; it contains selected tab, URL and title, never page contents.

## Components

| Component | Responsibility |
|---|---|
| `BrowserSessionManager` | tabs, selected tab, lifecycle, bounded resources, session restoration |
| `BrowserTab` | stable ID, WebView reference, title/loading/navigation/error state, desktop flag |
| `SecureWebViewFactory` | configure every main or popup WebView identically and attach clients |
| `BrowserSecurityPolicy` | HTTP(S), TLS cancellation, scheme rejection, first/third-party cookie and permission policy |
| `BrowserVpnGate` | adapter over existing `BrowserVpnController`; permits remote work only when policy allows |
| `BrowserVaultAcquisition` | user-requested download/image/screenshot paths into existing `VaultImportCoordinator` |
| `BrowserDataStore` | encrypted bookmarks, history, and session metadata; clear categories independently |
| `BrowserUiState` | Compose-only toolbar, panels, transient feedback and permission prompts |

## WebView and security policy

V2 deliberately enables JavaScript, DOM storage, responsive viewport, normal
first-party cookies, HTTP(S) subresources, fetch/XHR, fonts/media, IndexedDB
and service-worker capability where provided by WebView. It keeps file/content
access, file-URL universal access, JavaScript interfaces, TLS bypass and
unapproved custom schemes disabled. Mixed content remains never-allow.

Third-party cookies default off. A private Browser setting may enable them only
after an explicit, documented owner/user action; the state is private and never
diagnosed. This avoids silently weakening the default while providing a
deliberate compatibility control if evidence later justifies it.

`WebChromeClient.onCreateWindow` opens a new constrained tab using the same
factory and passes the WebView through `WebViewTransport`, preserving normal
opener semantics as Android WebView permits. Ordinary iframe content remains
delegated to WebView. Popups never escape the app without an explicit user
action.

Permissions (camera, microphone, geolocation) require a private in-app prompt,
then Android runtime permission where needed; only the approved requested
resource is granted for that request. File input uses a system picker and only
the user-selected URI. Fullscreen custom view is tab-scoped and restores the
parent page on exit.

## Navigation, UI and Fold behavior

The omnibox resolves HTTP(S) or the selected existing search engine. Each tab
retains back/forward/loading/title state. Back, forward, reload/stop, home/new
tab, close tab, compact switcher, history, bookmarks, find-in-page and a
per-tab desktop-user-agent flag are session commands—not WebView recreation.
Desktop mode reloads the active tab after switching the intentional UA policy.

The outer display uses compact controls. On the inner display, tabs and
history/bookmarks use a wider sheet/pane while WebView receives the complete
remaining canvas. Window size changes remeasure the same Android View.

## VPN and acquisition

`BrowserVpnGate` uses the existing engine-confirmed WireGuard state. Require
VPN blocks new remote navigation, popup navigation, downloads and remote image
acquisition until CONNECTED; it cancels remote acquisition when the tunnel
drops. It does not intercept or selectively block normal permitted WebView
subresources after the gate is open. Existing owned-tunnel grace, background,
lock and task-removal policy stays in `BrowserVpnController`/Activity.

All user-requested remote downloads and image-resource saves stream through
`VaultImportCoordinator` with private encrypted staging and verification.
Screenshots and reliable displayed-element captures are in-memory local input.
If native hit-test/layout cannot supply validated element bounds, V2 explicitly
offers Screenshot to Vault rather than calling a viewport capture an image
capture. Acquisition feedback is independent of tab navigation/error state.

## Data and privacy

Existing encrypted bookmarks migrate without loss. New history (title, URL,
timestamp), save-history preference, and tab-session metadata are encrypted in
private app storage. Clear History, Clear Cookies/Site Data and Clear Cache are
separate actions. Bookmarks are never removed by ordinary browsing-data clear.
No Browser URLs, titles, cookies, history, session data, page content or media
enter logs, crash reports or diagnostics.

## Acceptance diagnostics and recovery

The signed-device acceptance build alone exposes the sanitised diagnostic
console and focus/verbose controls. It reports structural categories, provider
version, lifecycle, VPN state, renderer status and sanitised console/runtime
events; it records no URL/host/token/cookie/page or media data. Normal release
builds compile verbose probes out.

Automated local fixtures cover SPA bootstrap, storage capability, iframe,
modal, popup, file input, fullscreen, download and image actions. Physical
acceptance still tests two unnamed complex modern sites, Fold layouts, VPN
lifecycle, acquisition preservation and viewer gestures.

## Delivery sequence

1. Reconcile current main with approved post-audit shared remediation.
2. Add pure session/security/data policies and failing tests.
3. Implement stable session-owned tabs and secure WebView factory.
4. Wire V2 UI, navigation, popup/fullscreen/permission/file chooser controls.
5. Reuse VPN and Vault acquisition boundaries; migrate bookmarks and add
   encrypted history/session stores.
6. Add renderer recovery, diagnostics boundary and local fixtures.
7. Run all CI gates, prepare one signed-device acceptance build, and require
   physical acceptance before removing obsolete V1 production wiring or
   releasing.
