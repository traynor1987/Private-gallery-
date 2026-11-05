# NovaOS Preview 0.3 — Google navigation regression repair (EXPERIMENTAL)

Android 17/API37, version code3, package `uk.co.traynor.novaos.preview`. Same stable development certificate as Preview0.2, so 0.2 can update without clearing preview data. Production NovaOS, Primary V1 Browser, Hidden VPN and Tor are untouched.

## Exact regression

Commit `05754a2b16a6208155e3e120738db8e9c9771b2d` introduced `PreviewAppPolicy.googleAccount()` and called it from both `navigate()` and `shouldOverrideUrlLoading()`. It matched the entire `accounts.google.com` host. `requestExternal()` generated the red NovaOS warning. The first preview source (`a5add01b…`) had no such host interception.

## Correction and authentication behaviour

The policy now distinguishes ordinary Google website navigation (for example YouTube's first-party ServiceLogin links) from OAuth authorization requests. Ordinary HTTPS pages reach the unchanged WebView. OAuth authorization endpoints, grant parameters and wrapped OAuth continue URLs still require a confirmed supported external browser. No user-agent spoofing, injected login scripts, cookie import, certificate bypass, provider-error bypass or fabricated callback has been added. Google's website remains authoritative about accepting or rejecting an embedded browser.

The original source used ordinary WebView JavaScript/DOM storage and CookieManager acceptance/flush in its separate `nova_preview_v1` directory. There was no native Google SDK, registered OAuth client, cookie migration or special callback. This explains how website-issued browser cookies could retain a session. The owner confirms the original APK displayed their authenticated library, but the actual account redirect chain was not recorded, so its precise server-side authentication protocol cannot be reconstructed from source alone.

Google OAuth policy prohibits authorization requests in controlled embedded user-agents. Google Account Help also says embedded browsers may be rejected. This repair removes Nova's overbroad interception; it does not certify general Google WebView sign-in as supported. If Google rejects a flow, use the confirmed external option; signing in there does not sign in the WebView.

Sources: https://developers.google.com/identity/protocols/oauth2/policies ; https://support.google.com/accounts/answer/7675428

## Preserved

Full-height App Mode and menu, retained WebView/tabs, audio under foreground Nova Home, all icon assets/cache, Store entries, profiles and layout schema. Cookies stay in ordinary preview-only Android app storage, shared within demo profiles; no encrypted-vault claim. Full Android background/screen-off audio remains unverified/unsupported.

## Verification limits and owner acceptance

Host policy regressions distinguish ordinary first-party navigation from OAuth and preserve external-request ownership. Focused Android fixtures use synthetic pages, cookies and audio, not personal accounts. Runtime outcome is appended below by CI. No automated test can certify the owner's personal YouTube Music library without their device/account; reaching a login page is not counted as sign-in success.

Owner acceptance: update Preview0.2, open YouTube Music, sign in only if Google accepts the unchanged browser, confirm your actual personal library appears, return Nova Home, reopen Music and check that the library/account and audio remain. Report any Google-owned rejection separately from Nova's OAuth compatibility prompt. Do not share credentials, tokens or account URLs.

Older Preview0.1 from GitHub used an unsaved runner key and cannot update directly to this certificate without reinstalling that preview; do not remove it if its local data is needed. Production NovaOS must remain installed.

Publication marker: preview-auth-regression-code3
