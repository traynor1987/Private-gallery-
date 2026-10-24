# Private Gallery 1.7.4 — signed owner acceptance candidate

Recommendation: **GO for owner physical acceptance testing only.** Full release/owner acceptance remains **NO-GO pending Galaxy Z Fold testing and acceptance of the partial scope below**. Do not uninstall or clear data. No main merge and no1.8 face recognition/visual search work.

## Identity and recovery

| Item | Verified value |
|---|---|
| Authoritative recovered1.7.3 branch SHA | `f1e056b05347a8c9a1edc83cd0a1366267fffbce` |
| Exact tested1.7.3/code36 upgrade source | `569b3ea2e7bf600980e331b3b634843d01d0042e` |
| Baseline application tree (both sources) | `f9871f6e3b42b44c935f09eb1ce09886ddb5b091` |
| Exact tested/signed1.7.4 source | `0af69436147731d70dbdcac7455a4f252f199b6e` |
| Tested complete source tree | `62a435b0bf0cdc36174cdbd69e894c2b5eaf224d` |
| Tested application tree | `a144055bad4384ed9b5b02273b6608361b9466ed` |
| Branch | `release/1.7.4-camera-metadata-privacy` |
| Package | `uk.co.traynor.privategallery` |
| Release | `1.7.4`, code37, non-debuggable |
| APK size | 62,726,484bytes |
| APK SHA-256 | `7ed4907ea80721da6d8bb1fd8a4c3210e91f55410f09a83b854de634a83ebca8` |
| Original production certificate SHA-256 | `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17` |

CI apksigner verified APKv2 signature with one original4096-bit RSA signer. Independently downloaded acceptance ZIP digest matched GitHub, extracted APK hash matched CI evidence, and certificate DER extracted from its APKv2 signing block matched the original digest above. Local extraction is certificate identification; cryptographic signature verification was performed by official apksigner in CI. No replacement signing key generated.

## Completed focused work

- Full-screen camera with compact controls, supported zoom/quality choices, centred shutter, photo/video modes, recent authenticated capture preview, timer/grid/vibration/default-mode preferences, and capture rotation updates. Existing encrypted capture/journal pipeline retained.
- Original local article-style Reading View, setup separated from reading surface, intentional capture controls and existing foreground permission/lock finalisation policy.
- Authenticated photo/video Details, available image EXIF and protected random-access video metadata, copyable information, date/time edit/restore in encrypted supplemental metadata. Original capture timestamp, encrypted media and integrity hash unchanged; no watermark. Adjusted dates drive Date Taken ordering.
- Actual file-size sorting for phone Gallery/vaults/collections; independent vault preferences and stable ties/missing-size policy.
- JPEG/non-animated PNG review-and-share clean copies, default GPS/all-optional-metadata removal, optional adjusted exported date, and explicit original-metadata choice. Original encrypted media untouched. Bounded internal staging, temporary read-only grants, per-open15-minute expiry and best-effort cleanup.
- Vault-scoped storage breakdown, largest files, duplicate candidates, available capacity, confirmed regenerable Primary preview-cache clearing and incomplete-capture recovery entry. No automatic deletion of originals; Hidden totals/names are not scanned from Primary.

## Verification

Exact-source full workflow [40085716021](https://github.com/traynor1987/Private-gallery-/actions/runs/40085716021): **SUCCESS**, no failed steps.

| Check | Result |
|---|---|
| JVM XML reports |349 passed;0failures/errors/skips |
| Focused professional/privacy UI |71 passed;0failures/errors/skips |
| Security/browser preflight |74 completed successfully |
| Unfiltered Android regression XML |353 passed;0failures/errors/skips |
| Separate short privacy UI lane40085715961 |6 passed;0failures/errors/skips |
| App/test compilation, lint, host policy/parser/release checks |Passed |
| Strict existing Tor/browser/camera/encrypted-media regression |Passed in unfiltered run; no assertions weakened |
| Synthetic code36→37 upgrade |Passed debug and original-key production package |
| Upgrade process restart |Both packages passed preservation fixture after restart |
| Production identity/package/non-debuggable/hash checks |Passed |
| Secret scan and whitespace validation |Passed |

The71/74/6 executions overlap the353 full suite; these are executions, not a claim of that many unique tests. Earlier compile, fixture and transient transport failures are documented chronologically in V1_7_4_RECOVERY.md; final frozen-source gates have no unresolved failures. A probabilistic ciphertext-tamper fixture was corrected to mutate its intended byte while retaining authenticated rejection. Supplemental-file upgrade assertions verify baseline bytes before deliberate preference edits and expected changed bytes after restart; original payload/index/credential expectations retained.

Synthetic camera-cover/landscape and article renders inspected from final diagnostics; high-contrast controls fit their constrained viewports. These are emulator/synthetic evidence, not owner-device acceptance.

## Independent security review

No outstanding concrete Critical/Important blockers reported. Corrected findings included Primary authenticated publication race, failed-write staging cleanup, expiry after process death, reserved JPEG extension rejection and PNG structural validation. Final narrow review covered actual-payload accounting, scoped camera roots, native picker screenshot-policy/lifecycle and rotation updates. See [REVIEW.md](REVIEW.md).

## Remaining limitations

- Privacy sanitisation supports JPEG and non-animated PNG only. Video/WebP/GIF clean sharing is unavailable pending reliable sanitisation. The new workflow does not sanitise existing restore-to-gallery, browser uploads or AI-provider egress; those retain prior behaviour.
- Selective keep-other-EXIF modes are not implemented: clean removes all optional metadata together. PNG colour-profile removal may affect appearance. Metadata removal cannot remove visual location clues or receiving-service records.
- Device-specific FPS/capture-aspect presets remain unavailable; video resolution choices use discovered CameraX capabilities. Fold lens/flash/rotation/preview behaviour still needs physical testing.
- Phone Gallery date information is read-only until import into a vault. Adjusted dates are editable display data, not proof of capture time; exported EXIF does not control messaging/cloud/filesystem timestamps. Ambiguous/nonexistent local DST times are rejected.
- Large Primary videos can lack a recent thumbnail under the bounded preview policy; API26 video thumbnails are unavailable.
- Shared app storage is excluded from per-vault totals to prevent Hidden disclosure. Cleanup scheduling can be delayed by Android, and already-open recipient descriptors cannot be recalled.
- Owner real media/PINs have not been accessed or tested by this session. Emulator upgrade fixtures cannot establish physical acceptance. VPN+Tor remains unavailable.

## Artifacts and owner checks

One signed deliverable: **Private-Gallery-1.7.4-code37-Acceptance.apk**. Acceptance artifact12558142407 ZIP digest `9fb81320c073c1435728bf6c0a3e5e47ece7dcb93b7c9f12a73526edd473ec0e`; diagnostics artifact12557878018 ZIP digest `bdbf9ca6a64baaaf07b095abaff8a2f8152679aa986e0116d8b7b78717fc70e6`. First diagnostic transfer was incomplete and rejected; fresh transfer matched and ZIP integrity verified. Short-lane artifact12557390747 digest `5143616e882725e4dc4440a46ee53d4b32a02de3ee7c39576c9193ef4a926abc`.

Follow the [Galaxy Z Fold owner checklist](OWNER_ACCEPTANCE.md). All boxes remain pending. Preserve branch and prior releases; do not merge main without approval. The final documentation checkpoint may have a newer SHA than the exact tested/signed source above; it changes documentation only.
