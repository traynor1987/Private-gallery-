# Private Gallery

Local-first Android media vault. Media imported to the vault is encrypted using AES-256-GCM in app-private storage; the vault key is random and protected with a PIN-derived scrypt envelope, with biometric wrapping planned for the setup flow.

The core safety invariant is non-negotiable: a source is never requested for deletion until its encrypted vault copy is authenticated, readable, and committed.

## Development

Requires Android SDK platform 36, build tools 36, and JDK 17. Run `./gradlew testDebugUnitTest lintDebug assembleDebug`.

## Status

V1 is under active construction. The committed crypto core has tests; media selection, vault persistence, restoration, lock UI, biometric unlock, and safe deletion confirmation are not yet released as complete.
