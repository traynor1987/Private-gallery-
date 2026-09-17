# Private Gallery release signing

Production releases use one permanent Android signing identity. It is created once by James, stored outside this repository, and supplied only to the trusted GitHub Actions release workflow through repository secrets:

- `PRIVATE_GALLERY_SIGNING_KEYSTORE_BASE64`
- `PRIVATE_GALLERY_SIGNING_STORE_PASSWORD`
- `PRIVATE_GALLERY_SIGNING_KEY_ALIAS`
- `PRIVATE_GALLERY_SIGNING_KEY_PASSWORD`

The repository contains no keystore, passwords, keys or tokens. The release workflow is manually dispatched from `main`, rejects other branches and fork PRs, runs tests/lint, creates a signed release APK, writes its SHA-256 companion asset, and publishes both to the official GitHub Release.

The app accepts only a non-draft, non-prerelease release from `traynor1987/Private-gallery-` containing exactly named `private-gallery-release.apk` and `private-gallery-release.apk.sha256` assets. It verifies the downloaded APK digest before handing it to Android's installer. Android then enforces the permanent package signing identity.

Key loss or replacement after installation prevents normal in-place updates. Keep an encrypted offline custody/recovery copy under James's control before any production release is installed.
