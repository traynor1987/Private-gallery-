# Browser parity completion source and evidence map

28 October 2026. Scope: required browser parity omissions in `browser-parity-audit-2781.md`, for 1.7.6.2/code44. This document records implementation and pending verification; it is not activation or runtime acceptance. No flags, version, permissions, keys, PIN or route policy changed.

| Requirement | Production implementation | Verification and limits |
| --- | --- | --- |
| Actual shared address/navigation/error interface | `BrowserSharedChrome.kt` supplies the same address bar (IME Go and accessible Go, reload/stop), six-icon navigation toolbar and retry/settings error presentation to `BrowserV2Home` and `HiddenVpnBrowserActivity`. Hidden keeps its mode, Done and quick-lock header. | `HiddenNativeBrowserDialogs1762Test` invokes shared production callbacks at large text. Existing toolbar viewport cases remain. Actual screenshots and Fold acceptance pending. |
| Tabs, bookmarks, history, settings | Native uses actual `BrowserTabsPanel`, `BrowserBookmarksPanel`, `BrowserHistoryPanel`, and full-screen `BrowserPanel` settings shell. Library rows expose full URLs; search matches title and URL. Settings retain Hidden-specific mandatory-route and permissions explanations. | Shared library callback/search test added; parcel pressure fixture retains actual-child library reachability and full URL assertions. Persisted metadata and legacy migration have separate host tests. |
| Address focus and Back | IME/accessibility Go clear focus/hide keyboard before native navigation. System Back clears address focus, cancels active website/upload interactions, closes Find, traverses actual WebView history, then retires the session at its root. Explicit Done/lock still retire. | Live owned-VPN test adds two fixture navigations followed by actual OS Back and verifies restored address and unretired owner. Runtime result pending. |
| Loading, HTTP/DNS/TLS failure and recovery | Per-WebView loading/error state, active progress updates, explicit Stop clearing, shared Retry/settings overlay. Background callbacks update their own state without replacing selected-page presentation. TLS cancellation remains unconditional. | Live fixture adds main-frame HTTP 503, actual Retry and server evidence of repeated request. DNS/TLS, stalled Stop, background-tab loading and recovery device acceptance remain required; not claimed executed. |
| Find and independent search | Native Find calls real `findAllAsync`, next/previous and clearMatches; reports WebView match counts. Search choice is session-owned and uses `BrowserAddressPolicy`; onion shorthand retains Tor-only route validation. No Primary preference/session access. | Live test enters Find text through accessibility and requires actual WebView match count. Runtime result pending. |
| Desktop/mobile per tab | Each native WebView owns desktop state; selecting restores its setting and UA/viewport, toggling reloads that tab; new tabs start mobile. | Live test toggles production UI, checks server-observed desktop UA, opens a new tab and checks mobile UA, closes it and checks restored desktop setting. Runtime result pending. |
| Downloads management | Native Downloads shows current broker status and an explicit Manage in Hidden Vault action. Bounded optional END integer requests parent navigation only for authenticated valid graceful RETURN. Parent callback must queue the authenticated Hidden Vault route. UI warns return closes tabs and cancels unfinished transfers. | Existing Live supported GET encrypted-import and exact-byte digest assertions retained. Parent callback integration and fresh security review are owned by root. No generic URL-retention download manager created. |
| Isolation and permissions | All native callbacks still use child-owned WebViews/profile, immutable owned route and revocable broker. VPN JavaScript remains on; Tor remains off. Camera/microphone/location denial and upload confirmation remain intact. | Existing protocol/body/upload/auth/modal assertions retained. No permission or route relaxation. |

## Download API facts

Supported reusable GET image/video media still requires explicit consent, broker validation, encrypted import and verification. Android's DownloadListener supplies a URL and metadata, not the original response body or POST body. POST responses, blob URLs, segmented media and one-use responses cannot be faithfully captured through that API; the UI states these limits and never replays an unsupported POST as GET. Broker CHECK exposes textual download status, not byte progress or a per-transfer cancel operation. Returning through the existing session teardown cancels outstanding broker calls. No percentage or unsupported individual cancellation is promised.

## Exact local verification

- `bash scripts/check_hidden_browser.sh`: PASS, 77 policy checks and >500-item stable upload paging/exclusion/metadata checks.
- Metadata owner's encrypted-library host suite: reported PASS separately; root must reconcile the final exact source snapshot.
- `python3 -m py_compile scripts/hidden_vpn_fixture1762.py`: PASS.
- `git diff --check`: PASS at the source-review checkpoint.
- Attempted Gradle connected test: blocked before compilation while wrapper attempted to download Gradle 9.5.0 (`java.net.SocketException: Network is unreachable`). No APK build, compilation, device test, screenshot or physical Fold result is claimed. The newly added tests have not produced a red/green runtime result locally.

Required next evidence: compile all production/test call sites, run actual Main entry across both activation paths and all selected native suites; cover/inner portrait/landscape, light/dark, large text, keyboard/focus/scroll, loading/errors/Stop, dialogs, and mode-transition isolation; reconcile independent security review before claiming complete acceptance or enabling flags.

## Earlier CI Live failure: tab close/dismiss boundary

The earlier `6c7b945` focused run failed the preserved `Closing selected tab restores actual page address` assertion at 21:28:58.916 (`HiddenVpnLive1762Test.kt:174`). Its source was the old native resizable tabs AlertDialog, not the shared-panel implementation above. Logcat records the tabs Dialog registration at 21:28:28.150 and no matching dismissal before the 30-second assertion timeout/fixture teardown. No tab UI hierarchy or failure screenshot was captured, so the artifacts establish an undismissed modal, not whether the Close action or the immediately following Done tap missed. They do not prove a WebView navigation or transport failure.

The updated Live test preserves the restored-address assertion, selects Close through the actual selected shared tab card's accessibility ancestry (rather than assuming index 1), waits for the production count to become one before dismissing, and verifies actual panel dismissal before examining the WebView address. This gives distinct failures for close, dismiss and restored page. Shared BrowserPanel has a fixed full-screen header, avoiding the old resizable-body footer moving while a row is removed. This is source-based remediation with stronger diagnostics; success still requires the next actual-child run. No additional production change was made after the parity freeze for this investigation.

## Native overlay inventory closure

`HiddenDownloadsDialog(status,onManage,onClose)` and `HiddenFindBar(query,result,onChange,onNext,onClose)` are the actual production components invoked by the native Activity. Their extraction keeps owner WebView calls, teardown and authenticated return unchanged. Find's explicit IME Done clears focus/hides the keyboard; its result area scrolls within 72dp so long status cannot displace Previous/Next/Close.

Four new 2x-font cases in `HiddenNativeBrowserDialogs1762Test` map directly to inventory R96:

- `downloadsLightCoverLongStatusAndExplicitActions`
- `downloadsDarkLandscapeLongStatusAndExplicitActions`
- `findLightCoverKeyboardAndActions`
- `findDarkLandscapeKeyboardAndActions`

Downloads cases scroll a long production body to the transfer-cancellation warning, exercise Keep browsing without management, and assert exactly one management callback. Find cases enter text, use actual IME Done, exercise both match directions and Close, and assert the production callback values. Parent authorization remains tested separately by handoff cases. These four Android cases are authored, not locally executed; host browser checks and diff checking pass at this checkpoint.

## IR-B78 explicit-address privacy fix and compile correction

Review found that shared address normalization could turn rejected private or malformed URI text into a public search. Native navigation now calls production `HiddenConnectionPolicy.resolveAddress` before any search resolver. Explicit URI syntax is validated as supplied; rejected file/content/unknown schemes, malformed HTTP(S), user-info, local/private addresses and disallowed ports return null without invoking search. Bare dotted addresses are also validated directly; valid bare v3 onion hosts retain HTTP in Tor and are rejected in VPN. Intentional ordinary queries alone invoke the session-selected shared search resolver, and its result still passes mode-specific validation. Primary behavior is unchanged.

Executable `HiddenBrowserPolicyChecks` uses a counting resolver at this exact production boundary. Seventeen rejected inputs across all three modes must yield null with zero search calls; direct public HTTP(S) and bare public host resolution skip search; onion cases retain mode behavior; an ordinary query invokes search once. Initial extraction reproducing search-before-validation failed at check54; the corrected helper passes. `bash scripts/check_hidden_browser.sh`: **186 policy checks PASS**, plus >500-item upload paging/exclusion checks PASS; `git diff --check`: PASS.

The b78 CI build separately exposed the Activity JVM accessor collision `setProgress(I)V`. Native presentation state is renamed to `pageLoadProgress`; WebView.progress and the Material progress argument remain unchanged. The b78 build failed before Android tests; no 151-case execution or acceptance is claimed. This accessor correction still requires the next Android compilation.

The address guard also preserves unambiguous dotted `host:numeric-port` input before URI-scheme classification. `example.org:443/path` resolves directly to HTTPS; bare v3 onion `:80/path` resolves directly to HTTP only in Tor. The narrow authority grammar does not admit credentials, and mode-specific `safeUrl` still rejects private hosts and unsupported ports. Five executable regression cases first failed at check160, then passed after this bounded correction. Final local browser policy count: **191 PASS**, with zero search-resolver calls for the negative/private/credential cases; upload paging and diff checks also PASS.

The existing Live method additionally enters fixed synthetic `file:///private-test.jpg`, `content://private-test/item` and malformed `https://` through the actual address field and Go control after rendering the fixture page. Each must show the exact production rejection notice while the fixture remains visibly rendered and broker capability stays valid, consistently for 500ms. The test compares fixture-observed search-provider DNS event counts before/after rejected entry, then continues valid parity navigation and all original transport/body assertions. This complements host resolver-spy proof with actual Activity wiring; Android execution remains pending and no additional test method is introduced.
