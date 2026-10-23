# Private Gallery 1.7 — Galaxy Z Fold acceptance

Status: camera/regression emulator tests passed; corrected upgrade/signing rerun pending. Physical owner acceptance remains pending. This checklist is not evidence of acceptance.
Install only the final verified original-key production-package APK supplied in the handoff. Upgrade in place; never uninstall or clear data. Do not use a TEST ONLY/debug APK as a replacement for the production app.

## Start with harmless test recordings

- [ ] Existing Primary and Hidden PINs work independently.
- [ ] Existing 641-photo Hidden collection and subsequent additions remain accessible; compare your actual current count.
- [ ] Primary: Vault menu → Camera → Normal Camera takes a photo.
- [ ] Hidden: Vault menu → Camera → Normal Camera takes a photo.
- [ ] Both vaults record and play normal video.
- [ ] Switch front/rear, test supported zoom and flash; flash starts off.
- [ ] Photo/Video selection and supported quality choices work.
- [ ] Denying camera permission shows a useful message without capture.
- [ ] Audio Off records silent video without microphone permission.
- [ ] Audio On requests microphone permission and records audio only after consent.
- [ ] Audio preference stays fixed during a recording and survives re-opening the same vault.

## Black-screen capture

- [ ] Choose Photo or Video before entering Black-Screen Camera.
- [ ] Capture surface is black, with no live preview or captured-image preview. The display remains physically on; this is an active foreground camera, not a switched-off screen.
- [ ] Photo tap and Volume Down each capture one photo; rapid taps/repeated key presses do not duplicate.
- [ ] Video tap and Volume Down each start/stop; no artificial ten-second limit.
- [ ] Record for at least a minute, stop, and play it back in the correct vault.
- [ ] Optional vibrations correspond to start/save; Android camera/mic privacy indicators remain intact.
- [ ] Android Back and Close stop/exit reliably; navigation swipes do not trigger capture.
- [ ] Accessibility announces the capture action and recording state.

## Storage, lock and recovery

- [ ] No automatic copies appear in phone Gallery, MediaStore or Downloads.
- [ ] Locking the phone during photo/video stops capture and input immediately.
- [ ] Unlocking presents the neutral Primary entry; no automatic second Hidden PIN prompt or camera restoration.
- [ ] Hidden recordings are unavailable in Primary and require independent Hidden authentication.
- [ ] After re-entering the original vault, complete pending captures recover into that vault.
- [ ] Camera → Recover interrupted captures/Recording details distinguishes retained incomplete captures.
- [ ] Recording Details shows original date, duration, size and SHA-256; Verify integrity succeeds for new originals.
- [ ] Repeated identical captures remain separate originals with separate capture identities.
- [ ] Low-storage interruption reports recovery/finalisation rather than a false completed recording.
- [ ] Force-stop/restart only with a disposable test recording; inspect retained recovery state. Recovery of every interrupted MP4 is not promised.

## Browser and regression

- [ ] Hidden Browser controls are compact and consistent with Primary.
- [ ] Check Fold cover/inner, portrait/landscape, light/dark and keyboard open/closed with gesture navigation.
- [ ] Back/Forward, Refresh/Stop, tabs, new tab, encrypted downloads and settings work.
- [ ] Tor Only still loads ordinary and onion websites.
- [ ] VPN Only still loads ordinary websites.
- [ ] VPN + Tor remains unavailable; no direct-network fallback appears.
- [ ] Primary WireGuard browser remains unchanged.
- [ ] Collections, favourites, Gallery integration, Copy/Move/restore/export, AI editing/Seedream Combine Photos, auto-lock, screenshot preferences, settings and owner diagnostics still work.

## Limits to understand

- Existing encrypted media formats and keys are preserved.
- CameraX requires a private plaintext recording file while capturing. Verified encrypted staging removes it. Process death or encryption/storage failure can leave private plaintext until the owner authenticates for recovery. Deletion is not a promise of forensic erasure.
- Each video is capped just below the existing 2 GiB Hidden storage limit, with storage reserve for encryption; there is no fixed duration limit. Quality falls back to device support.
- After a lock, finalisation writes encrypted pending media using only that capture's key. Publication into the ordinary vault index requires authentication. Incomplete/unparseable recordings remain retained and may be unplayable; there is no destructive auto-repair.
- SHA-256 is an integrity check, not proof of identity or legal admissibility.
- No cloud uploads, covert background recording or face recognition are included.
