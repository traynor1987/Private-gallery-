# V1.5 final TEST ONLY owner acceptance — CI596

Candidate source: `fef455091cf759b0f932753f43502c7cfddfb038`.
[CI596 / APK and reports](https://github.com/traynor1987/Private-gallery-/actions/runs/39615390388).
Automated: 171 Android tests, zero failures/errors/skips; JVM/lint/build passed.
This is not production approval. Use disposable synthetic media only.

## Physical evidence received

On 21 October 2026 the owner confirmed the latest test APK displays the restored
five-tab navigation and Hidden Vault screen. This confirms those visible elements
only. It does not establish all folded/unfolded, keyboard, playback, timeout or
screenshot checks below. Earlier CI594 clipping failure is superseded by the
CI595 Activity viewport implementation, retained in CI596; full device acceptance
of that correction is still to be recorded.

## Installation

Package `uk.co.traynor.privategallery.hiddenprototype` installs alongside production
`uk.co.traynor.privategallery`; never uninstall production V1. CI596's debug signer
differs from CI595, so it cannot update CI595 in place. Do not uninstall an existing
test app containing needed media. If CI596 is already installed, keep it for this
acceptance pass. Hidden has no export/backup/recovery facility in this candidate.

## Record PASS / FAIL for each check

1. **Layout:** folded and unfolded, Hidden Vault and Favourite. Check every bottom
   icon, label and touch target stays above the Samsung gesture area. Check media
   rows, collection controls and menus remain reachable. No extra bottom banner.
2. **PIN:** existing shared full-screen design labelled Hidden. Test independent
   setup, wrong PIN/retry, unlock and Close/Back, keyboard open/closed. Primary PIN
   must not unlock Hidden; cancelling must not unlock either context.
3. **Navigation:** Gallery shows device media. Vault/Favourite show only selected
   vault data. Gallery, Browser and Settings hide Hidden media while retaining
   its valid session; returning to Vault/Favourite returns to Hidden. Settings
   retains original categories and separate Hidden settings.
4. **Switching:** Return to normal Vault revokes Hidden and opens Primary subject
   to its authentication. Reopening Hidden requires its PIN. No cross-vault media,
   collections or favourite labels. Original Primary media remains unchanged.
5. **Timeout:** test Immediately, 30 seconds, 1 minute and 5 minutes independently
   of Primary. Background hides media/stops playback immediately. Return before
   grace expiry can retain authentication; return at/after expiry requires PIN.
   Active foreground use and tab changes must not trigger a fixed-minute expiry.
   Screen-off/manual lock/explicit Primary lock revoke Hidden immediately.
6. **Screenshots:** with the existing preference OFF, Hidden grid/viewer capture
   is blocked; with owner consent ON, it is allowed. Hidden PIN/setup remains
   protected regardless. Check changing tabs, return to Primary, lock/unlock and
   background/foreground. Use only synthetic content in captures.
7. **Imports/playback:** import disposable PNG/JPEG and MP4 below 16 MiB via Photo
   Picker; reauthenticate as requested. Originals remain in Gallery. Check
   thumbnails, image viewing and video playback/seek/audio. Lock/background must
   stop playback. Hidden imports must not appear in Primary.
8. **Collections/deletion:** create/rename collections; add/remove items; select a
   favourite; reopen Favourite. Trash, restore and permanently delete disposable
   items. Other media and original device files must remain unchanged.
9. **Restart:** force-stop TEST ONLY, relaunch and authenticate independently.
   Imported media, thumbnails, collections and favourites persist. Wrong PIN and
   cancelled picker must not import or expose data.
10. **V1 preservation:** check original Gallery, Settings, Primary AI and existing
    WireGuard browser configuration. Do not weaken VPN gating to get a page to
    load. Record network/browser failures separately from Hidden acceptance.

## Known separate maintenance issue and candidate limits

- Owner previously observed a blank page in the existing WireGuard browser.
  This remains an open, separately tracked maintenance issue; no successful live
  browsing claim is made. VPN requirements and fail-closed enforcement remain.
- Hidden import limit is 16 MiB per media item. Oversized files must fail with
  originals retained. This is an existing candidate limitation, not a new change.
- Hidden backup/export/recovery, browser vault uploads/imports while Hidden is
  selected, and Hidden move-originals are unavailable. Primary features remain.
- Real Fold behaviour, live VPN/network/provider calls and real owner data cannot
  be certified by emulator tests. No owner-data migration or production promotion.

Record device/Android version, folded/unfolded state, each check's result and a
synthetic-content screenshot for any failure. Fix confirmed defects only.
