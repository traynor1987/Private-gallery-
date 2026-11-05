# Production integration handover — separate authorization required

Preview components ready for separate integration assessment: shell navigation and inset handling, safe-placeholder Recents, Edge controls, event model, Games catalogue and display preferences, Mind exercise UI and timing policy, generated CC0 sound assets, Sanctuary visual shell.

Do not copy preview persistence as secure production storage. Mood/journal entries, drafts, preferences containing sensitive choices and any future private notifications must use authenticated encrypted Space storage. Define an explicit owner-approved migration; never silently import preview notes. Replace Bundle/saveable plaintext drafts with Space-scoped protected state. No production vault keys or database were used in this preview.

Primary and Hidden must own separate navigation history, recent-app sessions, browser profiles/cookies, notification events, journal/mood stores, media state and shortcuts. Preview demonstration profiles share ordinary WebView cookies and are not a security implementation. Hidden requires its own PIN; never add Hidden biometric unlock. Lock, timeout and Panic Exit must revoke callbacks, clear sensitive drafts/previews and close/pause associated media.

Recents: safe app placeholders only for protected content; preserve secure Android Recents flags throughout transitions and Fold recreation. Review process-death restoration, background snapshots and cross-Space app ownership. Home retains apps; Close destroys screen sessions; Lock/Panic are separate security operations.

Keep real production Primary Browser V1 and its security/lifecycle policies. Integrate App Mode as a separate presentation, not an engine replacement. Review history/bookmarks/download storage and cookie handling independently before production. Preview DownloadManager support handles public HTTPS only; it is not a Hidden download implementation.

Hidden VPN, Tor, production code55 work, routing and OAuth session redesign are excluded. Nothing here should replace or reopen those implementations.

Production must retain uk.co.traynor.privategallery and the original production signing certificate, choose the next unused production version code, and run real encrypted-data in-place upgrade/security checks. Never ship preview package/development key as a production update. Physical Galaxy Z Fold Android17 acceptance and independent production security review remain mandatory.

## Preview0.6 additions

WidgetData/WidgetRules/WidgetUi provide a preview-local grid and stacks; NovaSearch indexes metadata only; HomeBackup/HomeBackupCodec/LayoutTransaction provide an allowlisted layout format with recovery snapshots; HomeExperienceUi provides the demonstration lock and future NovaUnlockGate boundary. NovaMedia owns explicit local video/miniplayer/PiP; MindAudio publishes only its own generated-sound MediaSession. Browser V1 merely adds standard onShowCustomView callbacks; engine, cookie/OAuth restrictions and production code are unchanged. New source milestone is recorded in DELIVERY.json after publication.

Production must scope widget/search/recents/media ownership per authenticated Primary/Hidden Space. Quick Notes/configuration, search history and restore recovery snapshots require encrypted Space storage. Layout backup must preserve the explicit credential/journal/note exclusions, validate before mutation and obtain a target authenticated Space; demonstration profile identifiers are not authorization. Preview wallpaper-image references resolve only within its app directory and do not migrate image bytes.

Replace NovaUnlockGate's unconditional demonstration interaction with production authentication; retain screenshot/Recents protection throughout transitions and forbid internal callbacks or PiP while locked. Review app backgrounding, Fold recreation, media callbacks and native VideoView ownership. Do not copy the preview's lack of secure boundary into production. Hidden VPN teardown DNS/UDP fail-closed work remains excluded and unresolved by this preview. Tor stays deferred. Production package, original key and in-place encrypted-data upgrade verification are mandatory and separately authorized.

## Foreground media continuity boundary

PreviewBrowserModel owns retained WebView lifecycle independently of Compose attachment and Media Session metadata. While NovaOS is foreground, current-profile pages remain resumed even if not selected; WebView retains platform audio-focus handling. Home/app/Browser navigation does not force pause, recreate or play. Activity configuration teardown only drops Activity context references; true stop pauses the registry. Close App/Close All destroy owned sessions. MindAudio requests/releases native media focus; NovaVideo uses VideoView focus. Source milestone:74048a6bca3f6c25d516c84d3c8ae986748426e1.

Production integration must replace demonstration-profile selection with authenticated Space ownership before reusing this policy. Lock/timeout/Panic Exit must explicitly suspend/revoke all associated sessions and callbacks, independently of view attachment. Do not infer playback from absent website metadata; never scrape/inject transport commands or bypass provider restrictions. Android Home background audio is deferred. Physical YouTube Music and genuine Fold posture transitions require owner verification.

## Delivered source and dependencies

Code7/0.6.1 source:9ff024f5dd00104a1d93bb37fa1646c898499e1e. Widget engine:WidgetData.kt, WidgetRules.java, WidgetUi.kt. Media:NovaMedia.kt, MindAudio.kt, PreviewAudioFocus.kt, PreviewBrowser.kt, MainActivity.kt. Search:NovaSearch.kt. Backup:HomeBackup.kt, HomeBackupCodec.java, LayoutTransaction.java. Lock:HomeExperienceUi.kt. All reside under preview/app/src/main/java/uk/co/traynor/novaos/preview. Shell integration is PreviewUi.kt.

Dependencies remain Android WebView/MediaSession/MediaPlayer/VideoView/PiP, existing AndroidX Compose/Activity/Lifecycle/Browser and Android JSON; no new website engine or privileged media access. Espresso3.7.0 is test-only for API37 compatibility. The separate preview development signing identity is not a production dependency. See VERIFICATION-0.6.1.md and receipts/0.6.1 for precise runtime/publication evidence.

## Preview0.7 polish boundary

PolishedShellUi.kt provides the internal top panel and empty-Home gesture helper; PreviewUi.kt routes advanced Home/navigation settings as full pages. No accepted Home palette/wallpaper/icons/Dock redesign. PreviewShellData displayDim is an app-only visual overlay, not Android brightness. WidgetData.moveLayout/moveWidget and PolishRules.relocate stage collision-free whole-layout changes before saving; WidgetUi uses explicit editing, live snapped feedback, page-edge target selection and immediately committed note edits. No widget schema reset.

PreviewMediaSessions.kt uses API37 getActiveSessionsForPackage(context.packageName), excluding the native Mind token. Only a unique playing controller (or sole paused controller) with proven current-profile ownership receives advertised controls: a complete bounded title match when metadata exists, or sole retained web source after excluding known native players. Multiple unknown web sources are rejected. Register/exclude all future native player tokens before enabling ownership by elimination. Mixed-profile retained tabs disable that bridge. No global session queries, notification-listener permission, media-key routing or website playback injection. Return remains the fallback. API reference:https://developer.android.com/reference/android/media/session/MediaSessionManager#getActiveSessionsForPackage(java.lang.String).

This metadata association is preview presentation logic, not an authentication identity. Production must bind native session tokens to authenticated Space-owned browser instances and revoke them on lock/timeout/Panic; do not copy title matching as a production security boundary. Protect note text, search history, widgets/configuration and callback ownership with encrypted authenticated Space storage. Keep existing production Browser V1/VPN/Tor requirements unchanged.

Delivered0.7 source:3de66dd289713f003c1e8f6a064f97071ffb1709. Final native-session/media run42026621706 passed4 cases + upgrade; earlier42023130870 supplies17 unchanged UI/widget/polish passes. WebView navigator.mediaSession is unavailable on the API37 emulator: native-only sole-owner fallback was implemented without a website polyfill, and actual native callbacks passed. Real-provider publication still needs physical acceptance. See VERIFICATION-0.7.md and receipts/0.7.

## Preview 0.7.1 correction boundary

Desktop Site is scoped by preview profile and canonical host in BrowserLibrary; it changes only the selected WebView and preserves existing provider restrictions. Production must map preferences to authenticated Space identity and verify redirects/session isolation independently. No production browser changed.

The original clock had been explicitly hidden whenever page zero contained a widget. It is now a protected separate Home element with an explicit visibility preference. Widget geometry remains metadata only; strict collisions, clipped snapped previews, cancel/undo geometry and conservative recovery do not export or overwrite note contents. Recovery retains the original invalid raw layout privately. Migrate layout metadata separately from encrypted notes; review recovery snapshot retention/encryption before production.

Native MediaSession capabilities are inspected through the package-scoped Android 17 API. Package-local controls do not prove a website owns that token; unmatched sessions are labelled NovaOS native media. Production needs authenticated Space/session revocation and privacy review; no website playback polyfill, DOM scraping or broad notification access added.

No Friends Mode allowlist/filter exists in this repository. The unsupported compatibility guard conceals web content when requested and persists that choice; it is not an authenticated chat adapter or approved-friends filter. Do not integrate it as such. WhatsApp linking and chat/session persistence require separately authorised owner acceptance.

Production signing, encrypted storage, Primary/Hidden authentication and Recents isolation remain separate gates. Hidden VPN teardown security blockers and Tor exclusions are unchanged and outside this correction.

Delivered correction source:1b1f24c5d76c4791d2bf07280c2d60b6cc1afc7b (code9). Files:CorrectionRules.java, PreviewBrowser.kt, WidgetData.kt, WidgetUi.kt, PreviewUi.kt, PolishedShellUi.kt, PreviewMediaSessions.kt and NovaMedia.kt. Final focused run42039524262 passed13 cases +code8→9 upgrade. The pinned editing toolbar fixes the diagnosed offscreen Done/disabled stack interaction. See VERIFICATION-0.7.1.md and receipts/0.7.1 for complete evidence and limitations. No new external dependency or broad media permission. Production integration remains separately authorised.
