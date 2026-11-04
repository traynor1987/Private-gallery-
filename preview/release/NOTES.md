# NovaOS Home 2.0 + Nova Store Preview 0.2 — EXPERIMENTAL

Android 17 (API37), package `uk.co.traynor.novaos.preview`, code 2. Development signed. Separate from production NovaOS, with no production browser, vault, VPN or Tor components.

- Full-height App Mode by default. Discreet top-right menu for Home, Back, Refresh, Tabs, browser view and compatibility settings.
- Selected WebView retained attached underneath Nova Home while this Activity stays foregrounded, removing Nova's forced pause on internal Home switching. Tabs, DOM and supported sessions retained. Android Activity stop and profile/tab changes still pause the inactive app. Website restrictions may still pause media; no playback-policy bypass, media extraction or visibility spoofing.
- Google account navigation requests a confirmed external browser. External sign-in does NOT log in the preview WebView. No supported third-party YouTube cookie/OAuth return integration was found; no fabricated callback or browser identity spoofing. Real-account Google login and YouTube Premium playback have not been verified.
- Refreshed official website/manifest icon assets with bounded HTTPS retrieval, image decoding and hash checks; crisp NovaOS fallbacks replace tiny favicons. Shared artwork across Store, Home, Dock, folders and drawer; no per-icon ellipses. Long-press, accessibility Options action and drawer-wide three-dot app selector remain available.
- Existing 50 catalogue destinations and layout schema preserved. All classified conservatively as Website Shortcuts; no new PWA claims.

## Installation and existing preview data

The first GitHub preview APK used an unsaved ephemeral runner-generated development key (`a5398358…`). Android cannot update that installation with this build's stable development key (`81cda5b7…`). **Do not uninstall the old preview if you need its data preserved.** This build preserves the layout/storage schema but cannot overcome Android's signature requirement. A fresh install would require manually removing ONLY NovaOS Preview and would erase its local layouts/cookies. Production NovaOS must remain installed and is unaffected. Earlier local/Library preview builds signed with `81cda5b7…` can update normally. The public test key is now versioned for consistent future preview updates; it is never a production signing key.

## Compatibility

Ordinary sandboxed browser storage, shared within the preview's demo profiles; not an encrypted vault. No Chrome cookie import or production credentials. Supported browser sessions persist via WebView storage; renderer/process death can discard live page state. No guarantee of audio when the screen locks or another Android app is foregrounded. Android's native MediaSessionService model owns a native Player; it does not provide a supported adapter for arbitrary third-party WebView streams. No fake foreground service was added. Downloads and camera/microphone permissions remain unavailable.

Runtime results are appended by the publishing workflow. Real Galaxy Z Fold cover/inner-display, Google account login, Premium and screen-off behavior still require owner acceptance. This prerelease is not production security approval.

Publication marker: preview-appmode-code2
