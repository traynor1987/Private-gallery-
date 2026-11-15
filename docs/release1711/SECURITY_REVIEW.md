# NovaOS 1.7.38.7 Code 80 maintenance upgrade controls

Source: f9b0f9d280a645c734c024d2682542d91efd1e98
Authorization: authenticated owner push
Release scope: private disposable upgrade verification only
Physical Fold acceptance: pending
Upgrade fixture SHA-256: ce00d3df11a1304d2ef69d7edc0aebf760ca7787c337822d0f13b3c44dfa1dae
Upgrade controls SHA-256: 2819da810a12672064f63ccedc593df3e2b6771470d93f982186b33eccb7ce60
Runtime matrix SHA-256: 62b209459a34f97e06eb73a0ccb88d7f503b7260696b3607812120ac3e2d3967
Runtime workflow run: 43701096930
Signing workflow SHA-256: a8ec18334ea2a0941d097eb0bd167cb77a3915d97f75219fa1663c6528f8f60f

The signing job waits for the selected Android 17 regression run. The original
GitHub artifact ID and digest are recorded from that run's authenticated API
response and verified against the downloaded bytes and the reviewed test matrix.

The original Code 80 baseline is pinned to production run 43629517564. The rebuilt baseline must have the original production certificate and every
ZIP-entry name and uncompressed byte must match the delivered APK. The receipts
record whether the complete rebuilt APK hash also matches. A payload mismatch
blocks delivery. The emulator verifies the encrypted in-place update and process restart
before the candidate can be transferred. The public Actions artifact contains
only ciphertext under the existing private recipient's certificate.
