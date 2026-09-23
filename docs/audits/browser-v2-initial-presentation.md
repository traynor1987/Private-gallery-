# Browser V2 initial REAL presentation

Starting main: `867ea9f3be34c0ca97b7a5654e6205b9bb45dfaf`, Android #265 green.

## Narrow boundary

Owner evidence: STATIC paints the complete route and chrome; initial REAL paints white over
chrome while its controls remain interactive; first navigation renders both page and chrome
with correct final content-host bounds. Go/session-save no longer crashes.

## Source findings

- `BrowserV2Home` mounts the session-owned WebView directly in `AndroidView`. Its modifier
  sets size but no drawing clip. The initial tab has no loaded document.
- `SecureWebViewFactory` does not change initial alpha, visibility, background, elevation or
  layout parameters. Those are native defaults. No initial load is performed.
- `navigate` loads the user-selected destination and posts focus. `onPageStarted` and
  `onPageFinished` update tab metadata/chrome state, with a temporary loading indicator.
  None reparents the WebView or repairs layout with requestLayout/invalidate. There was no
  commit-visible callback in V2.
- The old `WEBVIEW_ATTACHED` marker was emitted from `AndroidView.update`, not actual window
  attachment. Its later posted measurement could not explain the initial drawing boundary.
- Navigation cleared the console's earlier structural events, losing the before/after trace.

## Drawing mechanism

AndroidX documents that AndroidView does not clip to layout bounds. Chromium's AwContents
draws its effective background colour onto the canvas before it has a rendered frame and
also when native drawing fails. Canvas colour fill obeys the canvas clip, not the WebView's
measured rectangle. Consequently correct layout geometry does not guarantee confined pixels.
The first rendered document changes Chromium's draw path; an app-level attachment repair
is not required to explain the observed recovery.

Primary source references:

- https://android.googlesource.com/platform/frameworks/support/+/2fb1702c1c62a6059e73d757d9e4eda1044ce21d/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/viewinterop/AndroidView.android.kt
- https://chromium.googlesource.com/chromium/src/+/refs/heads/main/android_webview/java/src/org/chromium/android_webview/AwContents.java (`onDrawInner`)
- https://chromium.googlesource.com/chromium/src/+/4ed92d7d028d523518484d447669a2f3ab823f8b/android_webview/browser/gfx/browser_view_renderer.cc (`NeedToDrawBackgroundColor`)

## Regression method

Use the real production destination, session and secure WebView factory with remote networking
gated off. Compare PixelCopy-backed chrome images across STATIC, initial REAL, and a deterministic
in-memory document. Assert native/Compose bounds, visibility and alpha, unchanged chrome geometry,
and retained view/parent identity across navigation. Also test direct REAL mount before any
interaction and retained STATIC/REAL remount. No external page is required.

Existing assertIsDisplayed/semantics checks are insufficient: white overdraw does not remove
semantics or hit targets. The new pixel check must fail on the unchanged starting implementation.

## Fix and diagnostics

The production drawing fix is `clipToBounds()` on the existing WebView AndroidView modifier.
No measurement, document initialisation, loading, focus, session persistence or security change
is required. The retained WebView is still created once per tab by the session.

The acceptance probe observes actual attach/detach, reparenting and layout listeners, and the
first pre-draw after attachment. Host creation/update/release have distinct labels. Page start,
commit-visible, finish and navigation submission snapshot the same structural fields: native
parent size/clipping, layout parameters, bounds, visibility/alpha, elevation/translations,
layer/background presence and document-presence boolean. It never reads or records page text.
V2 navigation preserves the bounded pre-navigation event timeline; page console text is omitted
entirely. Capture remains acceptance-build-only. No callback changes rendering or navigation.

## Validation

Test-only commit `bd96e58283785b93d7a3c4bf87e85f81e2441acb`, Android #266:
https://github.com/traynor1987/Private-gallery-/actions/runs/35916370019

- Unit tests, lint and both APK builds passed.
- Both new pixel tests failed on the unchanged production code. Changed chrome pixel fraction
  was `0.9985929938985904` in both direct REAL mount and STATIC → REAL.
- The direct-mount test's native bounds, alpha, visibility and empty-document checks passed
  before the pixel assertion failed. The existing geometry/typing/submit tests also passed.
- Only these two new regressions failed. This establishes drawing overrun with correct geometry.

Fix validation and final-main CI pending. No physical device result is claimed by automated
validation. No signed acceptance build is dispatched here.
