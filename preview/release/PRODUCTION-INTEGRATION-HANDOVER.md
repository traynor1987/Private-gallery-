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
