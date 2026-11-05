# NovaOS Preview 0.7.1 — Focused corrections (EXPERIMENTAL)

Android 17/API 37 only, code 9, package uk.co.traynor.novaos.preview. Same development signing identity. Install over the existing preview without uninstalling or clearing data. Production remains untouched.

- Per-site/profile Desktop Site in Browser V1 and installed app settings, explicit desktop reload and mobile return; unrelated sites retain their user agent. Provider authentication restrictions remain in place.
- Original Home clock/date remain visible independently of widgets. Protected clock area, clipped snapped drag previews, strict collision rejection, pinned Done toolbar, cancel/undo movement, and conservative invalid-layout recovery preserving notes/configuration.
- Quick Panel has distinct rounded icon tiles with explicit row/column spacing and animated dismissal. Native media capability details distinguish actual supported transport/metadata from unavailable website information.
- Native progress is shown only when provided. Controls require actual advertised native MediaSession actions; package-only association is labelled NovaOS native media. Return to app remains the safe fallback. No arbitrary website playback injection.

WhatsApp authenticated chats, linking and session persistence require owner acceptance; the public login probe does not prove them. No Friends Mode allowlist adapter exists in this source: requesting Friends Mode displays a persisted compatibility guard that conceals website content, rather than promising filtering. Normal browsing is unfiltered. No personal messages or linked accounts are collected in CI.

Physical Galaxy Z Fold behaviour and real YouTube Music media-session publication require owner verification. Android-level background playback remains deferred. Preview storage is not production encrypted. No production merge or security approval.

Publication marker: preview-correction-code9
