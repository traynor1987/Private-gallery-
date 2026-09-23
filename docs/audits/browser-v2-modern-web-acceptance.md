# Browser V2 modern-web compatibility investigation

Starting main: `fd30ee9d0a037eab12a487b2f15c0523a14b0e8f` (Android #269 green).
The owner physically confirmed that initial REAL hosting, Go, session save and ordinary HTTPS rendering now work. The persisted `Unable to save Browser session` fatal report belongs to an earlier process. None of those solved boundaries is reopened here.

## Evidence boundaries

The two anonymous affected applications are not available to CI. Their source, runtime trace at the failing interaction and exact previously working APK are not known. `v1.0.21` (`8543f2d`) remains the newest clear pre-regression **source candidate** identified by the completed forensic audit, not a proven physical known-good APK. The later audited V1 `7aa6bc8` already had the reported website regressions.

A real source regression is reproduced: V2 replaced WebView's provider user agent with fabricated, unversioned `Chrome Safari` strings. A deterministic asynchronous application fixture that requires a parseable modern Chromium version cannot initialize its content under that identity. Restoring the provider identity fixes that mechanism without changing security settings. This is a plausible blue-page/blank-content contributor, **not proof of either anonymous site's root cause**. The same malformed identity affects desktop mode.

No modal-specific root cause is established from the supplied device evidence. Both an ordinary DOM dialog with asynchronous content and a genuine `window.open` child document succeeded under unchanged production code in Android #270. Existing child transport is not rewritten. Missing native window-focus handling is corrected and callback diagnostics now make future classification possible.

## Required baseline/current comparison

| Setting or callback | Source baseline v1.0.21 | Starting V2 | Changed? / relevance | Evidence |
|---|---|---|---|---|
| User agent | Provider default | Fabricated Android 17 / unversioned Chrome Safari; desktop also unversioned | **Yes; reproduced application boot failure** | `PrivateBrowser.secureBrowserWebView`, `BrowserSecurityPolicy.userAgent`; #270 local fixture |
| JavaScript | Enabled | Enabled | No | Both factories |
| DOM storage | Enabled | Enabled | No | Both factories |
| WebSQL/database | Disabled | Disabled | No; does not disable IndexedDB | Both factories; Android WebSettings semantics |
| First-party / third-party cookies | true / false | true / false | No; no evidence to relax | Both factories |
| Mixed content | NEVER_ALLOW | NEVER_ALLOW (`1`) | No; numeric 1 is the restrictive mode | Both factories |
| File/content/file-URL universal access | Disabled | Disabled | No | Both factories |
| Multiple windows / automatic JS windows | false / false | true / true | Yes; V2 design explicitly requires tab-owned child browsing contexts | `docs/design/browser-v2-design.md`, V2 factory |
| onCreateWindow | Returns false | Fresh tab-owned WebView, valid WebViewTransport, sendToTarget | Yes; working local child fixture; not proof of affected modal | #270 child fixture |
| Window close / focus | No managed child tabs | Close implemented; request-focus omitted | Focus callback gap fixed; retained existing tab selected/focused | `BrowserV2Session`, factory; focus regression test |
| JS alert/confirm/prompt/before-unload | Framework defaults | Framework defaults | No; preserve default dialogs, add event-kind observation only | Both ChromeClients; Activity context in MainActivity |
| Permission callbacks | Default denied | Owner prompt plus runtime grants | Yes; unknown resources could be granted along with checked ones; cancellation omitted | V2 UI and Android PermissionRequest contract; bounded wrapper correction |
| File chooser | Default unhandled | Owner system picker | Yes; keep behavior, count requests/results; cleanup unresolved result on UI disposal | V2 UI/factory |
| Fullscreen/media | Custom view; gesture required | Custom view; gesture required | Media policy unchanged; observe requests/exits | Both factories |
| shouldOverrideUrlLoading | HTTP(S) allowed, other schemes rejected | HTTP(S) allowed through VPN gate; controlled external choice | Deliberate feature; no policy relaxation | Navigation policies/session |
| Redirects / subframes | Provider navigation callbacks | Provider navigation callbacks, main-frame gate | No custom redirect loader; subresource requests not intercepted | V2 client |
| shouldInterceptRequest | No override at v1.0.21; later V1 observed/delegated | No override | No V2 acquisition/resource interception | Factories/history |
| Service workers | Provider default, no app client/interceptor | Same | No evidence of disabled support; capability snapshot only | Factory/client absence of overrides |
| Resource / HTTP errors | Main-frame message; later V1 counters | Counters plus main-frame message | Keep provider codes; add fixed categories, no descriptions/addresses | Session error callbacks |
| TLS / safe browsing | Cancel errors / enabled | Cancel errors / enabled | No relaxation; add event observation | Factory; safe browsing default delegated |
| Renderer lifecycle | Earlier incomplete recovery | Per-tab renderer loss and recovery | Intentional V2 recovery; now records crash boolean | Session/factory |
| WebView attachment/visibility | Compose/Activity retained view | Session-owned WebView per tab, clipped AndroidView host | Physically accepted; unchanged | Owner acceptance; initial-presentation pixel suite |
| Focus | Provider default | Touch-focusable; explicit navigation posts focus; acceptance-only A/B | No evidence normal DOM modal needs new focus policy | Factories/session; snapshots |
| Viewport | Wide true, overview false, text zoom100, initial scale0 | Wide true, overview false; other defaults unchanged | No established mismatch; no viewport change | Factory diff |
| Console | Framework; later V1 diagnostic text sanitization | Severity/counts, no page message retained | Preserve privacy; explicit warning/error event categories | Acceptance console/session |
| Downloads / long press | Early unsupported; later acquisition | Existing Vault adapters | Deliberate feature; record request only; unchanged acquisition | Factory/session/UI |
| WebView state restore | Retained view, no V2 tabs | Cold encrypted metadata restore and retained live tabs | Intentional; no saved native document restored by V2 | Session/store |
| Session save | Old crash occurred later in development | Reentrancy/serialization fixes already physically accepted | Preserved | MainActivity and existing tests |

## Exact changes

- Mobile UA uses `WebSettings.getDefaultUserAgent`; desktop replaces only the platform/mobile tokens while preserving actual provider engine/version tokens. Returning to mobile restores provider identity.
- Child requests retain V2's existing multiwindow policy. Invalid transport/unavailable child returns false, with a result event. A closed VPN gate rejects before child allocation. `onRequestFocus` selects/focuses the existing owned WebView. No speculative popup-policy toggle.
- Permission requests complete once, grant only requested recognized camera/microphone resources approved by the existing UI, and ignore late results after cancellation. Pending UI permission/file/geolocation results are resolved when disposed. These are independent capability correctness/security fixes, not a claimed modal diagnosis.
- Acceptance-only runtime snapshots at page commit/finish, after page touches, and on explicit capture. Counts: bounded DOM scan, geometrically visible elements, scripts, stylesheets, frames, modal markers, canvas, viewport, API availability, document ready/visibility/focus, aggregate errors/rejections after probe installation. No JS native bridge or resource interception.
- Native callback events cover window request/create/result/close/focus, JS dialog kinds, permission request/result, file chooser request/result, fullscreen, console severity, renderer loss, HTTP/resource/TLS/safe-browsing errors, downloads and external navigation requests.
- Settings > Debug contains acceptance-only static-host and layout-colour switches. Both default OFF; state is transient, normal Browser uses REAL. Browser overflow no longer exposes host switching. Existing clipping/retained ownership remains unchanged.
- Console explicitly separates CURRENT SESSION from PREVIOUS PROCESS FATAL REPORT. Copy all includes both. Clear current session resets events/counters/page state while preserving provider configuration and historical fatal evidence; it does not erase the crash file.

## Diagnostic interpretation and limitations

`WINDOW_OPEN_REQUEST` identifies the native create-window callback, not an injected interception of every JavaScript call to `window.open`. A DOM modal is independent. `DOM_MODAL_COUNT_CHANGE` recognizes open `<dialog>`, role=dialog and aria-modal=true; arbitrary div overlays may not use those markers. Snapshots count only the top document; cross-origin frame contents are not inspected. Visibility counts indicate geometry/style, not screenshot pixel proof; ancestor occlusion/compositing can still matter. Scanning is capped at 5,000 elements and truncation is reported. WebGL availability uses one detached temporary context and releases it when the provider supports context loss. API availability is not proof that a site's API operation succeeded.

Error/rejection listeners start with the first probe, so earlier startup exceptions can be missed; native console severity is still observed. No exception/rejection messages, stacks, script paths, addresses or DOM text are captured. Turning verbose diagnostics off removes probe listeners and stops sampling; a reload with diagnostics off provides a clean A/B when necessary. The manual snapshot taken inside the console can legitimately show a focus change caused by that dialog; page-interaction snapshots precede it.

Android error `-2` is ERROR_HOST_LOOKUP (server/proxy hostname lookup failed). `-1` is ERROR_UNKNOWN (generic). Neither is automatically a cancellation or a causal application failure. The supplied totals alone cannot correlate six errors with missing primary content. Chronological categories, main-frame flags, callback outcomes and before/after structural snapshots are required; addresses/descriptions remain excluded.

## Verification evidence

Test-only Android #270 (`35923333517`, commit `5b01cd1`) ran unchanged main production code plus three new real-WebView tests. Existing 15 tests passed; DOM modal/storage/frame/canvas and child-window fixtures passed; asynchronous version-dependent boot failed (`expected 1 application button, actual 0`). This establishes the synthetic-UA regression without assuming the private applications use that exact check.

Android #273 (`35925843914`, commit `7427490`) passed all complete gates and all 27 instrumentation tests. The added native-dialog test required an explicit test-only Espresso dependency (#271 compile failure). #272 exposed test synchronization errors: a snapshot preceded the unhandled-rejection event and Espresso selected the Activity instead of the native dialog root. Tests now wait for the page-generated event and explicitly target the dialog; production fixes were retained. The independent source review found no blocking issue; its minor concurrent-capture completion finding was fixed and covered. Main is rerun through the same complete gates after integration.

The complete subsequent suite includes default REAL and opt-in Settings behavior, retained remount/pixel clipping, async boot, DOM modal, native JS confirm, child document/close/focus, structural snapshot privacy, permission grant/cancellation, historical report separation and existing unit/security/session-save coverage. CI uses deterministic in-memory HTTPS-origin documents, not an external server or a TLS-bypass fixture. BBC/device HTTPS remains the actual network/TLS acceptance check.

## One signed-device acceptance matrix after green main

Use the owner's normal signed acceptance workflow on the reported green main SHA. No production release and no signed build are dispatched by this investigation.

1. **Default foundation:** force-stop/reopen/unlock; Settings > Debug switches OFF. Open Browser: REAL by default, no coloured labels. Navigate BBC, Go once, back/reload/tab switch; chrome/content bounds stay usable; no current crash. Do not interpret the separately labelled historical fatal report as a new crash.
2. **Blue application A:** use mobile mode and a fresh tab; clear current trace immediately before navigation. Open the anonymous application, wait for its usual ready state. If content fails, capture page structure and Copy all; report A, visible result, provider version and trace only. No hostname, entered text or page screenshot is required.
3. **Modal application B:** clear current trace; load the base page, capture a baseline structure, close diagnostics, activate the failing control once. Capture again after the modal settles, Copy all, and report B plus whether the expected content/dismiss action worked. Compare window/JS-dialog/capability callbacks and modal/frame counts; absence of a native window callback now has evidential value.
4. **If either still fails:** in that same signed build, repeat only that application with verbose diagnostics OFF after reload, then ON for evidence. If unchanged, retain the pre-console interaction focus/visibility snapshots for diagnosis; a snapshot taken while the console is open is not proof that the page lacked focus during use. Keep cookies/TLS/mixed content/VPN policies unchanged. Record which single variable changed, not browsing data.
5. **Host controls:** deliberately turn colours/static ON in Settings, return to Browser and verify static host; turn static OFF and verify retained REAL page/chrome; turn colours OFF and verify clean UI. Force-stop/reopen: both OFF again.
6. **Diagnostic evidence:** confirm clear-current preserves the separate historical fatal report and Copy all includes both labelled sections. Normal production builds must hide these debug host controls.
7. **Preservation smoke:** tab/back/forward/reload/bookmark, VPN-required gate and connection, Download to Vault, Screenshot to Vault and long-press Save to Vault on owner's chosen safe content. Check no replacement of the current page or loss of tab state during acquisition.

Physical return result may be PASS or a narrow mechanism-bearing trace. CI does not establish either private site's visual success.

## Primary platform references

- Android WebSettings: https://developer.android.com/reference/android/webkit/WebSettings
- Android WebChromeClient: https://developer.android.com/reference/android/webkit/WebChromeClient
- Android WebViewClient error definitions: https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/webkit/WebViewClient.java
- Android PermissionRequest: https://developer.android.com/reference/android/webkit/PermissionRequest
- Chromium child transport: https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/how-does-on-create-window-work.md
