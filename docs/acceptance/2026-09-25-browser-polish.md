# Browser presentation pass

Starting main: 2bc7210e953e5879d6b9d6fec25411d432b80b76.

## Audit and changes

The app shell hid its bottom navigation only for fullscreen video, stacking the
app navigation directly below the Browser toolbar in ordinary browsing.
Browser now owns that space. Its menu retains Gallery, Vault, Favourite and App
settings destinations. System navigation insets remain managed by the shell.

The Browser settings menu previously navigated to generic app Settings. It now
opens a focused in-app page above the retained Browser, sharing the exact
settings component/callbacks with Settings → Browser. Search, encrypted history,
VPN requirement, automatic VPN connection, clear-on-lock and clearing site data
remain reachable. Diagnostics remain acceptance-only. Clear data requires
confirmation; it preserves Vault and bookmarks.

Tabs, bookmarks and history now use full-size in-app surfaces with back controls,
consistent typography and spacing. Tabs show a responsive card grid, active state,
title/domain, close and new-tab controls. They deliberately do not capture page
screenshots or download favicons. Bookmarks/history have ephemeral local search;
history groups visits by local date and confirms clear. Existing encrypted stores
and the maximum eight-tab session policy are unchanged.

Chrome hides after 48dp of actual vertical touch scrolling into the page and
returns on upward scrolling or at the top. It stays available while editing the
address, using a Browser surface, in VPN recovery or with touch exploration.
The session observes but never consumes WebView touches. Scripted scrolling,
flings without a new touch and viewport changes do not initiate hiding. Existing
acceptance touch diagnostics remain attached. Changing visibility does not create
or reparent the WebView. A single layout change occurs at each threshold, avoiding
continuous resize animation. CSS-only inner scrollers keep the toolbar visible.

No Browser configuration/security, VPN gates, media transfer, encryption or
orientation policy is relaxed. Dialogs inherit the protected window's secure flag.
No release is published.

## Automated checks

- Chrome distance/direction/top/reset policy unit tests.
- App navigation absent on Browser; unchanged for Gallery/Vault/Settings.
- Actual WebView touch scrolling collapses/restores toolbar, while programmatic
  scrolling does not; same WebView and parent retained.
- Local Browser settings preference mutation and return to retained WebView.
- Bookmark filtering/deletion and history clear confirmation.
- Existing Browser rendering, provider replacement, popup, VPN, fullscreen,
  Media3, security and lifecycle suite remains required.

## Physical acceptance (not claimed from CI)

1. Fold outer and inner: Browser has one bottom toolbar, with correct Android
   gesture inset; menu can reach Gallery/Vault/Favourite/App settings.
2. Long page: swipe upward to read lower content → chrome hides; swipe downward
   → chrome returns. Top, address typing, short page, zoom and horizontal controls
   remain usable; no page reload or blank/blue regression.
3. Tabs: create, switch, close and back; current indicator and layout work on both
   displays. Existing page/session survives opening and closing the tabs screen.
4. Bookmarks: save, search, open, remove. History: grouped visits, search, cancel
   clear, confirm clear. Browser settings: toggle history, reopen, verify both
   settings entry points agree; Cancel on clear leaves login state unchanged.
5. VPN required: disconnect during browsing/video, confirm blocking; reconnect.
   HTML5 fullscreen and internal player still work; exit returns portrait.
6. Rotate/fold during playback stays unlocked; Home and screen off still follow
   lock policy. TalkBack retains Browser controls while scrolling.

## CI follow-up

Run #318 compiled and passed unit/lint/build gates, but ART rejected the generated
BrowserV2Home method at mount with a VerifyError (`copy-cat1`, Composer register
v277). This was a runtime bytecode failure, not a website/network failure. The
existing and new Browser mount tests caught it. Grouping its ephemeral presentation
state in BrowserUiState reduces generated method/register pressure without changing
WebView/session ownership or persisting UI data. The scroll callback also uses the
latest density-specific policy after a Fold display change. Full instrumentation
must pass on the replacement commit; compilation alone is insufficient.

Run #319 passed 92/93 instrumentation tests, including all Browser mount,
compatibility, video and settings tests. The remaining scroll fixture used
Compose's batched whole-gesture injection (no intermediate recomposition/frames),
while WebView scroll offsets arrive asynchronously from Chromium. The fixture now
uses Espresso native WebView swipes on the actual window viewport and additionally
waits for real page displacement; hide/reveal and retained-parent assertions remain.
Reference: https://developer.android.com/reference/kotlin/androidx/compose/ui/test/TouchInjectionScope
