# Nova Home 2.0 — isolated preparation

Authority: the owner's 2026-11-04 recovery and redesign directive explicitly permits isolated, recoverable Home UI preparation while code55 remains blocked. No release/version activation is authorised by this document.

The existing Classic Home, authenticated launch callbacks, encrypted HomePreferences and all transport/session engines stay intact. New Home components are unreferenced by MainActivity until code55 containment, integration review and Android verification pass.

## Approach

Use immutable host-testable Java desktop state, then a Compose presentation consuming that state and caller-owned authenticated callbacks. This follows the current Java HomeLayout / Kotlin PrivateHome split. Replacing the existing launcher immediately would mix unverified presentation with the unfinished security baseline; creating a separate release branch would compete with the authoritative branch. Neither is used.

Desktop state contains per-Space pages, shortcuts/folders, configurable dock, hidden drawer apps, grid/icon preferences, built-in wallpaper appearance and widget placement. Folder names and media identifiers are potentially sensitive; no plaintext persistence is introduced. A bounded binary codec binds decoded data to the expected Space. The codec is NOT encryption: later persistence must reuse captured EvidenceAccess leases and authenticated encryption, never SharedPreferences.

A new presentation uses original procedural Nova gradients, visible wallpaper, clock/date, pages, dock, app drawer/search, folder sheets and a Quick Panel. It accepts only available app shortcuts and caller-supplied actions. It must not read vault content, acquire sessions, modify screenshot policy, or synthesise storage/evidence/download data. Built-in clock data only until authorised widget providers are integrated. Media wallpaper decoding/cropping/encrypted persistence remain explicit follow-up work.

## Security and limitations

Primary and Hidden states have distinct owner identity; cross-owner decoding fails. Tor remains filtered through NovaNavigation.available. Model changes cannot authorise launching; Main remains authority. New composables are inactive preparation, not a shipped feature. Session-keyed composition and immediate sensitive-state disposal on revocation are integration gates, not guarantees inferred from a model or preview.
