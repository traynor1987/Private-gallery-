# Private Gallery 1.7 — Galaxy Z Fold acceptance

Status: signed 1.7/code33 acceptance APK verified; synthetic debug and original-key production upgrades passed. Physical acceptance remains pending. These boxes are deliberately unchecked. Live Tor/onion CI probes have intermittent failures despite a prior full pass on identical app source; recheck both working modes on the Fold.

Install only the final verified original-key production APK supplied in the handoff, as an update over 1.6. Never uninstall or clear app data. Start with harmless test media. The owner’s existing 641-photo Hidden collection and subsequent additions have not been accessed by this work; compare the actual current collection and independent PINs yourself.

- [ ] Primary normal camera takes photos.
- [ ] Hidden normal camera takes photos.
- [ ] Primary normal camera records video.
- [ ] Hidden normal camera records video.
- [ ] Black-screen photo capture works.
- [ ] Black-screen video capture works.
- [ ] Volume Down controls work.
- [ ] Tap controls work; rapid repeats and navigation swipes do not cause unwanted captures.
- [ ] Audio On works after microphone permission is granted.
- [ ] Audio Off works without microphone permission.
- [ ] Photos save encrypted to the correct vault.
- [ ] Videos save encrypted to the correct vault.
- [ ] No automatic public Gallery, MediaStore or Downloads copies appear.
- [ ] Locking the phone stops capture and further input.
- [ ] Completed Hidden recordings remain in Hidden and need independent Hidden authentication.
- [ ] Unlocking returns to neutral Primary entry without automatically reopening Hidden Camera or another Hidden PIN prompt.
- [ ] Existing media, collections, favourites and independent PINs remain intact.
- [ ] Hidden Browser looks consistent with Primary.
- [ ] Tor Only still loads websites.
- [ ] VPN Only still loads websites.
- [ ] Existing V1.6 functionality remains intact: Primary WireGuard, Copy/Move, restore/export, Gallery integration, AI editing/Seedream Combine Photos, downloads, auto-lock, screenshots, settings, diagnostics and bottom navigation.

## How to exercise the controls

Open each authenticated vault’s menu → Camera. Choose Normal or Black-Screen, Photo or Video, and Audio On/Off before entry. Try front/rear switching, supported flash/zoom and quality choices; deny permission once and check the message. Record at least one minute, stop and play it back. Check optional vibration, accessible capture/exit and Android privacy indicators. The black capture surface has no preview; the physical display remains on.

Lock during a harmless Hidden recording, then unlock Primary and independently authenticate Hidden. Complete pending captures should recover into their original vault. Camera → Recording details shows dates, duration, size and SHA-256, with an integrity verification action. Retained incomplete captures are distinguished from originals. Use disposable test recordings only for force-stop/restart or low-storage experiments; not every interrupted MP4 can be recovered.

Check the browser on Fold inner/cover screens, portrait/landscape, light/dark, keyboard open/closed and gesture navigation. Exercise Back/Forward, Refresh/Stop, new tab, tab overview, downloads and compact settings. VPN+Tor must remain unavailable.

## Limits

- CameraX requires private plaintext while recording. Verified encrypted staging removes it; a crash or storage/encryption failure may leave private plaintext until authenticated recovery. Deletion is not a promise of forensic erasure.
- Each recording is capped just below the existing 2 GiB vault limit, with encryption space reserved. There is no fixed duration limit; device capabilities and storage govern recording length. Incomplete data may be retained but unplayable.
- After lock, capture-only keys can finalise encrypted pending data; publishing it into the vault requires authentication. SHA-256 checks integrity, not recorder identity or legal admissibility.
- No cloud uploads, off-device backup, covert background recording or automatic face recognition are included.
