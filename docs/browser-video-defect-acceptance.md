# Browser/video defect repair

Base: 8b74dc20ae6bad4dcf96db74bbc64dfc17f445fa.

## Evidence and scope

The physical fatal report was not supplied and is not remotely accessible. Inspection found a deterministic crash path in current main: onTitle uses first() and onPageState uses a throwing map mutation after a popup tab has been closed/evicted. Delayed callbacks then throw NoSuchElementException/IllegalStateException. Regression tests exercise both removed-tab states and queued callbacks from a destroyed provider instance. This is a confirmed code defect, not proof that the owner's physical crash had the same exception. Preserve the next acceptance fatal report if the device still crashes.

Session callbacks now reject removed tabs; provider callbacks are invalidated before destroy. Fullscreen ownership completes the Chromium callback once and rejects background/inactive/VPN-blocked or duplicate requests. Window custom views attach once to the Activity decor and detach during cleanup; Back, tab changes, navigation, background and VPN loss exit correctly.

The previous handoff used the first video or a page address, paused all videos before Media3 preparation, and supplied no useful WebView-owned fallback. The new source selection uses a visible actual HTML5 video, preferring playing media, never the page address. Cookie-bound sources stay in WebView. No cookies, auth, referer or private headers are transferred. Media3 uses the existing HTTPS-only policy; it pauses webpage video only after READY. If independent playback fails, Continue in Browser video view preserves the existing WebView/session. No new URL persistence.

A DOM-only fullscreen affordance follows playing video. AndroidX WebKit's feature-checked document-start API installs it in frames; no native Javascript bridge or message channel is exposed. A real DOM click retains user activation for requestFullscreen. Chromium supplies its native custom view through WebChromeClient. Existing page controls remain available. On older providers the top-document fallback installs at page finish and scans already-playing video. Frame permission policy is respected, never bypassed. Browser menu Video view additionally provides immersive viewing of the existing WebView, including session/blob/DRM video, without extracting or reconstructing its media. Cross-origin frames may still require their own page fullscreen controls when their embedding policy forbids fullscreen.

Normal UI requests portrait; both dedicated paths request sensor orientation and restore portrait. Existing protected-session configuration retention is unchanged. Real background pauses Browser video and retains existing auto-lock semantics. VPN denial destroys live WebViews (onPause/detachment alone does not halt networking), retaining existing encrypted tab metadata for gated re-entry. Engine-state changes enforce this synchronously; Media3 retains per-open/read gating. Reconnection resumes only while foregrounded. Diagnostic VIDEO_PATH values are WEBVIEW_FULLSCREEN, MEDIA3_DIRECT, UNSUPPORTED; no source, DOM, cookies or credential values. Media3 logging is disabled because provider/source exceptions can contain private URLs.

References audited:
- https://developer.samsung.com/browser/android/overview.html (video assistant viewing modes; concepts only)
- https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/full-screen.md (custom view/window ownership)
- https://developer.android.com/reference/android/webkit/WebChromeClient
- https://developer.android.com/reference/androidx/webkit/WebViewCompat (document-start scripts; feature gating)

## Physical acceptance (no release)

| Action | Expected |
|---|---|
| Repeat browsing that crashed; open/close popups and exceed eight tabs | App stays alive. If it crashes, copy Previous process fatal report from Browser diagnostics after reopening. |
| Play ordinary HTML5 video; tap floating fullscreen | Same playing WebView instance enters immersive custom-view presentation. |
| Play blob/MSE/session/DRM video already working on page | Floating/page fullscreen or menu Video view keeps playback in WebView; no extraction or access bypass. |
| Play genuine public HTTPS MP4, HLS, DASH through Play in Private Gallery | Media3 where decodable; failed/session-dependent transfer offers Continue in Browser video view. |
| Open a page without video, including a URL ending in .mp4 | Page URL is not offered as a media source; unsupported status is explicit. |
| Rotate either dedicated path repeatedly; Fold outer/inner; exit/Back | No spurious Vault lock; portrait restored; no black orphan surface or duplicate sound. |
| Home/return below and above auto-lock timeout; screen off | Existing lock policy holds; no unintended background playback. |
| Require VPN; try disconnected; reconnect; lose tunnel during either video path | No ungated playback; live WebViews close, reconnect permits gated reopening. |
| Inspect diagnostics after each path | Only path enums/fixed metadata; no URL, cookies, tokens or private headers. |
