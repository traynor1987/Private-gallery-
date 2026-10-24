# Private Gallery 1.7.4 — Galaxy Z Fold acceptance

Do not uninstall or clear data. Install only the original-certificate signed candidate after automated release gates pass. Physical acceptance is pending.

- [ ] Signed upgrade installs over1.7.3/code36 and existing Primary/Hidden media/PINs remain usable.
- [ ] Primary fingerprint works; Hidden remains separate PIN only.
- [ ] Normal camera preview uses space well on cover and inner screens, portrait and landscape.
- [ ] Fill/Fit framing is understood; original capture resolution unchanged.
- [ ] Photo/video mode, supported flash/zoom/front-rear switch, timer/grid/quality work.
- [ ] Recording timer/stop and phone-lock finalisation work; output returns to originating vault.
- [ ] Reading View looks like an original article; tapping designated area/Volume Down captures; scrolling does not.
- [ ] Details shows actual filename/size/dimensions/available EXIF and video metadata.
- [ ] Vault Date Taken can be edited; original date/hash/pixels unchanged; no watermark.
- [ ] Restore Original Date works; Date Taken order refreshes; timestamps/time zones sensible.
- [ ] Largest/Smallest work in phone Gallery, each vault and collections with independent preferences.
- [ ] Settings > Gallery > Storage shows only authenticated vault totals/files, largest and duplicate candidates.
- [ ] Disposable Primary preview-cache confirmation clears only regenerable previews.
- [ ] Interrupted-capture review opens setup/recovery rather than recording automatically.
- [ ] Share review identifies exact random exported filename, format, approximate size and privacy choices.
- [ ] JPEG/PNG clean copies contain no GPS/device/XMP/IPTC/comments; original vault media unchanged.
- [ ] Optional adjusted exported date works where supported. Receiving apps may show their own timestamps.
- [ ] Unsupported video/WebP/GIF privacy export is clearly unavailable; existing restore remains separate.
- [ ] Primary WireGuard and Hidden Tor Only/VPN Only still work; VPN+Tor remains unavailable.
- [ ] Closing/backgrounding/locking Details or sharing reveals no vault metadata on neutral entry.

Known partial features must be reviewed before accepting the release as complete: capability-filtered FPS/resolution, recent captured-media thumbnail, persistent default mode, non-JPEG/PNG sanitisation and selective EXIF-retention modes. PNG ICC profiles are removed and colours can differ. Clean metadata does not remove visual location clues or receiving-service records.
