# NovaOS 1.7.38.7 Code 80 maintenance upgrade controls

Source: f2dbfbcce068ee5f999fe8b0533471ea96c3aa96
Authorization: authenticated owner push
Release scope: private disposable upgrade verification only
Physical Fold acceptance: pending
Upgrade fixture SHA-256: eb3b3348d598d63967ba019fa3d21e1c528fb65f14a7914162ece9af07d768af
Upgrade controls SHA-256: ac928060942ad1aa9f3c68ddf6866638150423ae0cd6bb31f0fe863f726ffdd2
Runtime matrix SHA-256: 62b209459a34f97e06eb73a0ccb88d7f503b7260696b3607812120ac3e2d3967
Runtime workflow run: 43661668897
Signing workflow SHA-256: e84acea7e12710404baf7ce8b39823d19d138de21b1a075417aa15a260900318

The signing job waits for the selected Android 17 regression run. The original
GitHub artifact ID and digest are recorded from that run's authenticated API
response and verified against the downloaded bytes and the reviewed test matrix.

The original Code 80 baseline is pinned to production run 43629517564. Its
reconstruction must match the original APK byte for byte. A mismatch blocks
delivery. The emulator verifies the encrypted in-place update and process restart
before the candidate can be transferred. The public Actions artifact contains
only ciphertext under the existing private recipient's certificate.
