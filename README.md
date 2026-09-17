# Private Gallery

Local-first Android media vault. Media imported to the vault is encrypted using AES-256-GCM in app-private storage; the vault key is random and protected with a PIN-derived scrypt envelope. Biometric unlock wraps that same vault key with an Android Keystore key after biometric authentication.

The core safety invariant is non-negotiable: a source is never requested for deletion until its encrypted vault copy is authenticated, readable, and committed.

## Development

Requires Android SDK platform 36, build tools 36, and JDK 17. Run `./gradlew testDebugUnitTest lintDebug assembleDebug`.

## Status

V1 is under active construction. It currently includes PIN and biometric unlock, secure-window protection, an encrypted metadata ledger and payload store, Android Photo Picker import, verified-before-delete move requests, MediaStore restore, protected in-memory viewers, and image previews decoded only in memory after unlock. See [the security design](docs/superpowers/specs/2026-09-17-private-gallery-v1-design.md) for the threat model and limitations.

## Interface design

Private Gallery uses a deliberately small local design layer based on the current James OS, Domino's Shift Tracker and Gig Tracker interfaces: quiet uppercase context labels, strong title hierarchy, dark ink/surface layering, lime primary actions, restrained blue status surfaces, rounded 16/22/24dp components, and a labelled bottom navigation. The vault grid remains deliberately more restrained than a dashboard so protected media stays visually dominant. Large screens keep content at a readable maximum width while the vault grid uses adaptive columns.
