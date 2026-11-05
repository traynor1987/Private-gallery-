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
