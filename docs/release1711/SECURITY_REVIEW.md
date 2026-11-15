# NovaOS 1.7.38.7 Code 80 maintenance upgrade controls

Source: 13e45eae02837470e7585f8b643651da884f502b
Authorization: authenticated owner push
Release scope: private disposable upgrade verification only
Physical Fold acceptance: pending
Upgrade fixture SHA-256: 1ba2169f2674b138c7e8ef08c9bd46c16f73aa73f2f4b03a7cd74a2607f68235
Upgrade controls SHA-256: 202f76d9c7c87d400cd20924595f4b991a9a75828cbca81b813eeb3239c9a7e5
Runtime matrix SHA-256: 62b209459a34f97e06eb73a0ccb88d7f503b7260696b3607812120ac3e2d3967
Runtime workflow run: 43740944953
Signing workflow SHA-256: 890527cd27981ef8e00866802011560ad8015cacb9a71a0f9a4bcc75993c852b

The signing job waits for the selected Android 17 regression run. The original
GitHub artifact ID and digest are recorded from that run's authenticated API
response and verified against the downloaded bytes and the reviewed test matrix.

Build 82 is pinned to production run 43702829697. The baseline must match the original production certificate and the exact APK SHA-256 recorded in its upgrade receipts. A rebuild mismatch blocks the upgrade gate. The emulator verifies the encrypted in-place update and process restart
before the candidate can be transferred. The public Actions artifact contains
only ciphertext under the existing private recipient's certificate.
