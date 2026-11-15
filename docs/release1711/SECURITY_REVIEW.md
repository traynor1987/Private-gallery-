# NovaOS 1.7.38.7 Code 80 maintenance upgrade controls

Source: f2dbfbcce068ee5f999fe8b0533471ea96c3aa96
Authorization: authenticated owner push
Release scope: private disposable upgrade verification only
Physical Fold acceptance: pending
Upgrade fixture SHA-256: eb3b3348d598d63967ba019fa3d21e1c528fb65f14a7914162ece9af07d768af
Upgrade controls SHA-256: 1810fd90a86196187ec2468c0544ead3f6e0902eb87817befe73bbab48c13e9e
Runtime matrix SHA-256: 62b209459a34f97e06eb73a0ccb88d7f503b7260696b3607812120ac3e2d3967
Runtime workflow run: 43661668897
Signing workflow SHA-256: 62ccd8e0168cda56917a148f9f4245d0cc0d8beb3e9d6847cfbbb3164b3f73c7

The signing job waits for the selected Android 17 regression run. The original
GitHub artifact ID and digest are recorded from that run's authenticated API
response and verified against the downloaded bytes and the reviewed test matrix.

The original Code 80 baseline is pinned to production run 43629517564. The rebuilt baseline must have the original production certificate and every
ZIP-entry name and uncompressed byte must match the delivered APK. The receipts
record whether the complete rebuilt APK hash also matches. A payload mismatch
blocks delivery. The emulator verifies the encrypted in-place update and process restart
before the candidate can be transferred. The public Actions artifact contains
only ciphertext under the existing private recipient's certificate.
