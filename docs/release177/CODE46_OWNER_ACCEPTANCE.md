# Private Gallery1.7.7 — code46 owner-acceptance build

Production-signed acceptance APK; physical Samsung Galaxy Z Fold acceptance is still required. Code45's earlier physical acceptance remains OWNER_REQUIRED.

| Identity | Verified value |
|---|---|
| Filename | Private-Gallery-1.7.7-code46-owner-acceptance.apk |
| Package | uk.co.traynor.privategallery |
| Version |1.7.7 / code46 |
| Platform | Android17, min/targetAPI37 |
| APK bytes |104979026 |
| APK SHA-256 |696e80071497501d8da6cd8dd3e2c9728a63b0d4abc9e88c4ab46338fef97a2e |
| Original certificate SHA-256 |94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17 |
| Frozen application source |25aae6d59f79c455e47acc73a5c99f9c8f15242a |
| Release control |48a788a546375fca9c0c8bd5b32fd9b79aa0a7bb |

[Focused verification run](https://github.com/traynor1987/Private-gallery-/actions/runs/40896458172): host201 checks, compilation/JVM/lint,23exact Android cases, zero failures/skips, independent VPN packet/body/revocation and Tor/sink verification. Artifact12906529709, SHA-25693e197a109026af32d2558806b2994698ca065344e27854787e13d5966b9f64b.

[Production signing and upgrade run](https://github.com/traynor1987/Private-gallery-/actions/runs/40899438953): original signer verified; exact frozen APK built and identity verified; original-key code45→46 in-place upgrade plus restart passed. Baseline source97e94452ceb9c5262521d7552f0c25eeb07374eb. Seed, post-upgrade and post-restart each report OK(1test). Existing encrypted media, keys/credentials, collections, favourites, dates, settings and explicit encrypted browser records were preserved. No app-data clearing or uninstall occurred between seed and verification.

Production artifact12908558722, archiveSHA-25616873a3b24abcb311d21ca11e93e1af1707be42946a817770e4b9d629e790869. It contains one APK and evidence, including the reviewed upgrade fixture, signing validator, their hashes, release-control SHA, certificate/package reports and upgrade outputs.

Independent security review approved exact source25aae6d with zero open Critical/Important findings. Parent and reviewer separately checked the downloaded APK hash and release/upgrade evidence. Historical failed runs remain documented in V1_7_7_RECOVERY.md; none were waived into a pass.

Install this APK over the existing application. Do not uninstall or clear its data. Follow [the physical acceptance checklist](OWNER_ACCEPTANCE.md) for the carried-forward navigation, thumbnails, VPN/Tor browser, upload picker, Samsung editing and privacy controls, plus independent encrypted Recently Deleted.

Retention defaults to30 days independently per vault, with7/15/30/60/Never choices. Locked vaults reconcile expiry after authentication. Permanent deletion removes app-managed encrypted items and references; physical flash erasure and removal of external copies are not guaranteed. Previously destroyed files cannot be recovered retroactively.
