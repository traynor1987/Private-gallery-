# Nova Backup, Arcade and Calculator — owner implementation specification

Owner directive 2026-11-06. Implement in existing integrated NovaOS 1.7.x; Android17/API37 only, Galaxy Z Fold acceptance, package `uk.co.traynor.privategallery`. Three independently tested and pushed checkpoints; one repository writer. No reset, force push, uninstall, clearing data, replacement signing key, engine replacement, Tor work, or launcher redesign.

Preserve Home2, Gallery/Vaults, Hidden PIN-only authentication and networking, Evidence Locker, Mind, Chat Lite, Store/apps/sessions, wallpapers/widgets/Quick Panel/navigation/status indicators, Black Screen and pinning. Final release uses next unused code, original production key, focused consolidated API37 suite, real original-key in-place synthetic upgrade. Physical owner acceptance must be labelled unperformed.

## A — Nova Backup 1.0

Built-in Home and Settings app. Last success, destination, size/date, verification, actions/history/storage usage and optional daily/weekly charging/Wi-Fi scheduling. Distinguish verified/failed/interrupted/unverified. SAF destinations are untrusted. Portable versioned encrypted archive with encrypted manifest, authenticated content/item integrity, completion marker, compatibility range, bounded streaming and atomic finalisation where supported. Verify actual destination bytes before success. Established AEAD and reviewed recovery passphrase/key derivation; no custom primitives, no export of Android Keystore keys. Replacement-device recovery must exercise actual key design.

Eligible categories: Primary encrypted media, independently authorised Hidden media, collections/settings, Evidence records/reports, Home layouts/wallpapers, Store catalogue, supported preferences, Notes/Mind, Arcade saves/calculator preferences and other safe supported encrypted data. Explicitly mark unsafe/unsupported categories, never silently claim complete backup. Exclude plaintext PINs/API keys/tokens/cookies/credentials by default. Primary archives/UI/history must disclose neither Hidden existence nor data. Separate Hidden flow from authenticated Hidden only; PIN remains mandatory, no biometrics, no retained unattended Hidden authority. Wipe working buffers where practical and cancel on lock/Panic/retirement.

Restore: select, authenticate/validate, category eligibility and compatibility, conflict preview, explicit confirmation before overwrites, safe staging/rollback and verification/report. Failure must preserve existing Vault. Existing device-bound ciphertext alone is insufficient for portable recovery. Scheduling uses Android APIs; locked/unavailable sensitive authority produces an honest interrupted/needs-unlock status, not success or retained Hidden authority. Modular provider interface: implemented local SAF and clearly disabled future WebDAV/S3/self-hosted server, encryption remains local; never upload silently. No fake online implementation.

Tests: both Spaces roundtrip; wrong credentials; corruption/truncation/tampering; interrupted backup/restore; insufficient storage; duplicate filenames; large media; fold/unfold; lock/Panic/restart; synthetic cross-device keys; original production upgrade. Do not claim unexecuted results.

## B — Nova Arcade 1.0

Offline built-in Arcade; no network/accounts/ads/trackers. Installed/recent games, continue, scores/achievements/settings, optional sound/haptics, existing themes/Fold layout. Optional individual Home shortcuts through existing catalogue/layout model, no new installation engine. Space-encrypted persistence and no cross-Space history/recents. Integrate saves with Backup.

2048: standard4x4 correct once-per-move merge, swipe/keyboard, undo/restart, best score, win/game-over, continue beyond2048, automatic save/resume, deterministic merge and random placement tests.
Sudoku: Easy/Medium/Hard/Expert, solver-validated uniquely solvable puzzles, pencil notes/erase/undo/hints/mistakes, timer/pause/save/resume, valid completion only. Validated solver for puzzle verification/generation.
Snake: touch direction/swipe, adjustable speed, pause/resume, score/best, collisions, safe food spawning, game-over/restart, smooth Fold scaling, no immediate illegal reversal including queued turns.
Chess: clearly deferred catalogue entry unless first3 fully verified and time remains; never call movement-only chess complete.

## C — Nova Calculator 1.0

Standard +,-,*,/,%,decimals,parentheses,sign,clear/delete, history/copy/memory/large-number formatting/errors. Predictable documented percentages/precedence; decimal arithmetic avoids practical floating display errors. Responsive portrait/unfolded layout. Scientific powers/sqrt/trig/log/constants, DEG/RAD, factorial with bounded limits; accessible labels.

Optional concealed entry disabled by default, configured only in authenticated Hidden settings. Secret calculator sequence only initiates existing Hidden authentication; never directly unlocks. Preserve version-number entry, mandatory PIN, lockout/rate limiting, Panic, authority on intents/restoration. Do not store PIN in calculator preferences, log sequence in history or advertise Hidden through normal UI. History independently encrypted per Space with clear/disable; copy result. Tests arithmetic/scientific/error bounds/history isolation, secret opt-in/sequence nonlogging/no auth bypass/stale callbacks.

## Recovery and delivery

Verified starting HEAD `641cd9c6cc5326f8998f5143d4b064dbdef62dd7`, integration/novaos-1.7-home2. Original signed61 frozen application source `2da2895e387839ec37d0590dfb713dc1bec99853`, version1.7.11.5, cert SHA256 `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`. Signing run42269170059 success verified through GitHub. Baseline APK `/tmp/nova61-production/NovaOS-1.7.11.5-home2-code61-owner-acceptance.apk`, SHA256 `90083faaeb8ebaa896451d9dc721c4d26ee170135b3a4e1aad126038bcd7ef3e`. Baseline local checkout clean; no local writer observed; isolated worktree at `/workspace/scratch/af8df543d984/novaos`, shared advisory ownership file in common .git. Recheck remote before each push. Do not change the original checkout.

Final report: starting/final SHA, feature commits, backup/restore scope and results, Arcade/calculator capabilities, concealed entry security, API37 tests, signature/package/hash, direct GitHub APK URL, issues and physical acceptance checklist. If mandatory verification fails, provide honest development checkpoint/blockers and no safe production claim.
