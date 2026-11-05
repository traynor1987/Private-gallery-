# Google account navigation regression — 2026-11-05

Original owner-confirmed working preview source: a5add01b8c8fccf88be2fce8d7280d4b6e7672e2. Browser navigation was general HTTPS WebView browsing, no Google host rule. JavaScript, DOM storage, accepted browser cookies and CookieManager.flush retained the website session inside the separate preview WebView directory. No OAuth SDK/client/callback or cookie migration existed. Owner reports an authenticated personal library; that observation is accepted. Actual redirect URLs were not logged and cannot be reconstructed from source.

Regression: 05754a2b16a6208155e3e120738db8e9c9771b2d added a whole-host accounts.google.com policy in PreviewAppPolicy; PreviewBrowser.navigate and shouldOverrideUrlLoading sent it to requestExternal, which generated the owner-reported red warning. WebView configuration remained unchanged.

Repair: allow ordinary first-party website navigation while retaining explicit external confirmation for Google OAuth authorization endpoints/parameters/nested continue destinations. Preserve Google website rejections. No claim that all embedded Google account sign-in is officially supported; Google decides whether to accept the browser. User agent, SSL handling, cookie policies, directory and engine remain unchanged. Session continuity, App Mode, foreground Home audio and icon source are preserved.

Evidence: regression host test fails against the old policy at ServiceLogin (ordinary first-party browsing incorrectly intercepted), then passes with the narrower policy. Android fixtures test the real WebView callback, unchanged UA, OAuth external confirmation, owner identity and retained synthetic session/page/audio. Real personal-library sign-in and Home-return acceptance require owner validation; no credentials/tokens are requested or logged.

Authoritative references: https://developers.google.com/identity/protocols/oauth2/policies and https://support.google.com/accounts/answer/7675428 .
