# NovaOS 1.7.11 with Nova Home 2.0 — owner acceptance

The integrated application is built, signed with the original production certificate and published as an owner-acceptance prerelease. Software verification and the original-key upgrade/restart passed. Physical Galaxy Z Fold and live-account acceptance remain outstanding.

[Download the signed NovaOS APK](https://github.com/traynor1987/Private-gallery-/releases/download/novaos-1.7.11-home2-code56/NovaOS-1.7.11-home2-code56-owner-acceptance.apk)

Install over the existing NovaOS application. Do not uninstall it or clear its data.

| Identity | Verified value |
|---|---|
| Production baseline | `9efd19540a1d75efa1b30f4e23c9cbb096f93aff` — signed 1.7.10.2/code54 |
| Preview baseline | `39e8e9068c203380858d304c7845aece7fcb7e00` |
| Published Preview 0.9 feature source | `ff3f7683d16276a134183d4d2812354d89ede52f` |
| Integration branch | `integration/novaos-1.7-home2` |
| Frozen integrated APK source | `0e7f7c07608a6b439a4471e613531a758028c849` |
| Signing activation | `8803742e02d0e58e639687ff30b4b473109c0d47` |
| Application version | NovaOS **1.7.11**, code **56** |
| Launcher component | Nova Home **2.0** |
| Package | `uk.co.traynor.privategallery` |
| Platform | Android 17; minimum/target API37 |
| APK bytes | `144418414` |
| APK SHA-256 | `2efbe7a29a08a2677b04052eb268e908bb89f5a03fc23677b1791a1c11ea2141` |
| Original certificate SHA-256 | `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17` |

The original production and Preview branches were preserved. Integration started from the verified signed54 source; unfinished code55 VPN transport changes were not imported. Local checkpoint histories and the integration worktree were preserved without reset or force-push. This is NovaOS 1.7.x; the future NovaOS 2.0 Secure release remains separate.

## Completed integration

- Reused the approved Home design, wallpapers, clock/date, pages, dock, folders, drawer, themes, search, editing and responsive layouts.
- Connected the protected entrance to existing Primary PIN/fingerprint policy and concealed Hidden PIN entry. Home and sensitive content require authenticated Space authority; Hidden fingerprint unlocking remains prohibited.
- Connected existing Gallery/Vault, Camera, Evidence Locker, Collections, Favourites, Recently Deleted, Settings, Private Apps and Browser. Photo/AI Editor shortcuts use the existing photo-selection/editor path.
- Reused Nova Store, its curated catalogue/games, search/details, install/open/uninstall, My Apps and Home/dock placement, with existing encrypted Primary Private Apps records and UUIDs.
- Reused widgets, stacks, placement/resize policies, original-clock preservation, Quick Notes, media/Recent Apps/Mind widgets and Quick Panel.
- Reused Nova Mind breathing, grounding, bundled calm sounds, check-ins, journal, Calm Now and Sanctuary. New sensitive state uses captured owner-encrypted native storage; no Preview journal or credential import occurs.
- Added retained Private App presentation through the existing browser renderer registry. Gesture-started audio survives ordinary foreground Home/app switching; background/lock retirement remains enforced. Metadata-unavailable controls use Return to Player.
- Extended the existing three-finger Panic dispatcher to Home dialog and popup windows, including cancellation, immediate concealment, authority revocation, callback retirement and safe entry.

## Browser and encrypted-data preservation

The Primary V1 constructor, settings, policies, data stores and ordinary tab operations remain pinned to signed54. Private Apps use additive lifecycle entry points in that same engine. Native callback liveness, view identity and permission fences were tightened. No competing engine or browser-data directory was introduced.

The upgrade fixture verified synthetic persistent/HttpOnly cookies, encrypted browser tabs/history/bookmarks, all Private Apps fields/order, Primary/Hidden encrypted media and credentials, trash, collections, metadata/configuration and encrypted Evidence Locker records across original54-to56 and a force-stop/restart. The media/storage schemas and original keys remain unchanged. These are synthetic preservation checks; existing owner logins and media still require physical acceptance.

## Verification and delivery evidence

| Check | Result |
|---|---|
| JVM suite | 399 cases; zero failures/errors/skips |
| Focused API37 matrix | 57 cases/16 classes, exactly once; zero failures/errors/skips |
| Home policy/encrypted-adapter host checks | PASS |
| Delivery/upgrade-control host regressions | 10 PASS |
| Independent source/signing review | APPROVED for frozen source and gated upgrade |
| Real system three-finger Quick Panel/popup and synchronous drawer tests | PASS; both authorities revoked, neutral lock/PIN entry, no popup child click |
| Native encrypted Store UI install/Home/UUID launch/My Apps/uninstall | PASS |
| Retained real user-started HTML audio and Mind foreground retirement | PASS |
| Release build/lint, package, original certificate, native/OCR distribution and alignment | PASS |
| Original-key code54 → 56 install in place, verify, force-stop, verify again | PASS on fresh enforcing API37 emulator |
| Downloaded APK signature/package/hash versus published asset digest | PASS |
| Physical Fold, hardware fingerprint, live accounts | OWNER REQUIRED — not performed remotely |

- [Original 57-case run](https://github.com/traynor1987/Private-gallery-/actions/runs/42141848244), artifact `13467919132`; ZIP SHA-256 `7329a7a3dee3589ded9cf7b00aadf2bc90589482e54c72b2d58c814e571b6cf9`.
- [Original-key production build/upgrade run](https://github.com/traynor1987/Private-gallery-/actions/runs/42143438856), artifact `13469068696`; ZIP SHA-256 `aa42e6a06abd80710767cebe1b4fe58380fe68361222a7dfd2856dc7883221e6`.
- [Release and assets](https://github.com/traynor1987/Private-gallery-/releases/tag/novaos-1.7.11-home2-code56).
- [Upgrade evidence ZIP](https://github.com/traynor1987/Private-gallery-/releases/download/novaos-1.7.11-home2-code56/NovaOS-1.7.11-home2-code56-evidence.zip), SHA-256 `26e7c7810b6c9e6343612ba7ff75db8e7dbb3a225df78c1f72c51c536c7236d1`.
- [Independent review](../release1711/SECURITY_REVIEW.md).

Published evidence contains receipts, manifests, signatures, hashes and reviewed controls. It excludes test instrumentation APKs and signing material. The production certificate has one signer and a verified APKv2 signature. No owner device was connected, uninstalled or cleared.

## Deferred and known limitations

- Hidden networking, Hidden web apps/Store operations, VPN/Tor development and Hidden remote analysis remain unavailable. There is no Hidden direct-internet fallback. Continue security transport work separately for 1.8.x.
- Nova Chat Lite/Favourites remains disabled Beta. Normal website compatibility and embedded authentication are provider dependent; no strict friends-only isolation or unofficial messaging protocol is claimed.
- Home backup remains unavailable. Personal layouts/wallpapers/notes from the separately installed Preview are not automatically imported; existing production Home ordering is migrated where supported. Re-select personal wallpaper in the integrated application as needed.
- Android-level background playback is deferred; verified continuity is foreground switching within NovaOS. Editor shortcuts first ask the user to choose a photo in the existing engine.
- Synthetic responsive checks cover cover/inner/landscape dimensions. They do not establish physical Fold posture, real website login, hardware fingerprint or owner-data acceptance.

## Galaxy Z Fold acceptance

- [ ] Upgrade in place; confirm 1.7.11/code56, existing media, collections, trash and Evidence Locker are present.
- [ ] Primary PIN and enabled fingerprint work only after Enter NovaOS. Concealed Hidden entry requires its separate PIN and never fingerprint.
- [ ] Open Gallery/Camera, Photo/AI Editor, Browser V1 and Private Apps. Confirm actual browser history, tabs, downloads, bookmarks and existing website logins.
- [ ] Install/open/uninstall a Store app; add Home/dock shortcuts; verify wallpapers, pages, folders, search and widget move/resize/stack/clock behaviour.
- [ ] Switch Home/apps/Recents with supported audio playing; verify safe previews, metadata/fallback and app close behaviour.
- [ ] In Hidden, test three-finger Panic from content, Quick Panel, drawer and menus. Confirm immediate concealment and fresh PIN requirements; test Lock and configured timeout.
- [ ] Confirm Primary never reveals Hidden wallpaper, notes, apps, search, widgets, recents or notifications; verify configured screenshot restrictions and Android Recents.
- [ ] Fold/unfold and rotate while using Home, media and native apps. Check navigation and layout on both screens.

For continuation, use this integration branch and frozen signed source, not the unfinished code55 development branch. After installing56, use a greater production version code for a subsequent update; do not replace this verified release asset with a different APK.
