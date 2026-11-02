# NovaOS UI and Private App repair checkpoint

Base: authoritative release/1.7.10-private-home HEAD 53da3f286a11f1431e2b465af78995f222688bd9. Same branch; no reset, replacement signing key, release activation or version increment. Last delivered production APK remains 1.7.10.2/code 54 from source 9efd19540a1d75efa1b30f4e23c9cbb096f93aff.

## Approved scope and implementation

Owner's detailed specification authorizes continuing the existing implementation. Work is batched before one focused existing-workflow run. This directory is a workstream name, not an activated Android version.

- Hidden popup tabs inherit initiating Private App identity and retain an opener graph. Closing a popup restores its live opener; Close App retires its whole popup group. Return Home, Lock, Panic and background revocation remain distinct existing operations.
- Retired WebView membership is fenced before cancellation, listener detachment and destruction. Late page, renderer, download, find, auth, dialog and upload callbacks cannot mutate another active app. TLS/client certificates remain denied. Debug diagnostics contain event/reason/scheme classification and booleans, never URLs, tokens, cookies or raw app IDs.
- Shared Nova connection panel replaces duplicated Primary/Hidden/Tor connection bodies without changing the owner's WebView composition or routing. Existing address/header/status/navigation remain owned by their shells.
- Shared full-page surface supports safe-drawing/IME insets and Fold width limits. Hidden Restore, bounded media preview and transfer progress use it. Shared transfer summary/progress preserve direction-specific authentication and existing cancellation/recovery engines.
- Shared media tiles add bounded thumbnail preview, information, selection feedback, count and sort controls to browser upload and AI reference pickers. AI reference selection commits only on Confirm. Collection covers now use thumbnail selection with Confirm/Cancel.
- Evidence pre-registration review uses image/PDF thumbnail grids. Exact source ownership/fingerprint is verified before reading; bytes are wiped by the existing verified-read engine. Tile-owned bitmaps are bounded, unpublished after retirement, wiped/recycled on retirement/disposal. No new plaintext files or network requests.

## Google authentication: unresolved release blocker

This is not diagnosed as merely a cookie problem. Source inspection proves popup ownership/close handling was missing; it does not prove that defect is the owner's exact Google return trigger. Existing onStop terminates the Hidden child and revokes broker authority. Main explicitly navigates Home only for RETURN, not every BACKGROUND termination. No external OAuth callback receiver, registered client/redirect, state/PKCE handoff or Custom Tabs integration exists. Only safe public HTTP(S) navigation is admitted.

An external browser does not inherit NovaOS's process-bound VPN or loopback WebView proxy; Tor isolation only permits the NovaOS package. Launching arbitrary external Google authorization would bypass these controls. Google provider policy also disallows directing OAuth to a developer-controlled embedded user agent. A third-party site's supported external authorization/one-time website handoff and owned redirect must be established before adding a callback; they cannot be invented or replaced by copying external cookies.

The exact Private App site/redirect and success/cancellation runtime trace have not been supplied. The unanswered clarification did not authorize relaxing routing or authentication. Relevant owner screenshots show only empty Browser Beta connection/connected shells, no website or OAuth redirect. Therefore popup repairs are not labelled a complete Google OAuth fix. No external browser launch, new exported callback, persistent cookie or routing exception is added.

## Verification plan and limits

Local Java popup ownership/retired-callback checks and duration-label checks pass; editor presentation 4/4 and evidence presentation 3/3 pass. Android SDK/Gradle distribution is unavailable locally. Existing fast-development.yml gains only a focused selection flag, not a new workflow. It compiles all app/test sources, retains existing host/JVM checks, and selects 28 Android 17 tests covering changed ownership, previews, selection, source isolation, transfer auth, connection lifecycle, large text and Fold-themed modal presentation. Android callback fixtures use real WebView identities and retained callbacks; they are not an actual Google transaction or complete Activity integration test.

Unchanged production code54 evidence remains source-scoped in docs/release17102. New focused UI evidence cannot substitute for Google success/cancellation, fresh live route verification if transport changes, or original-key upgrade evidence for a newly signed build. Signing remains blocked pending a supported and verified Google return flow. Existing code54 APK must not be presented as this request's completed update.

## Remaining UI limitations

Bounded fullscreen thumbnail preview is not original-resolution preview or video playback. VaultItem has no duration field: known-duration labels are supported by the shared tile but current browser Vault sources display an explicit unavailable label. Some already-professional pickers (AI source, Primary copy) retain their existing workflows; this checkpoint does not claim every picker requirement or universal modal migration is complete. Android-required system pickers and permissions remain unchanged.
