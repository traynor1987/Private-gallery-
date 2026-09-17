# Private Gallery release signing

Production releases use one permanent Android signing identity. It is created once by James, stored outside this repository, and supplied only to the trusted GitHub Actions release workflow through repository secrets:

- `PRIVATE_GALLERY_SIGNING_KEYSTORE_BASE64`
- `PRIVATE_GALLERY_SIGNING_STORE_PASSWORD`
- `PRIVATE_GALLERY_SIGNING_KEY_ALIAS`
- `PRIVATE_GALLERY_SIGNING_KEY_PASSWORD`

The repository contains no keystore, passwords, keys or tokens. `assembleRelease` refuses to run unless all four signing properties are present, preventing an accidental debug-signed release.

## Owner setup — do this once, before the first production install

Create the keystore on a machine James controls, never in this repository or a CI workspace. Use a strong, unique store password and key password; retain them in a password manager separate from the encrypted offline keystore backup.

```bash
keytool -genkeypair -v \
  -keystore private-gallery-release.jks \
  -storetype PKCS12 \
  -alias private-gallery \
  -keyalg RSA -keysize 4096 -validity 10000

keytool -list -v -keystore private-gallery-release.jks -alias private-gallery
base64 -w0 private-gallery-release.jks > private-gallery-release.jks.base64
```

Keep an encrypted offline copy of `private-gallery-release.jks`, the two passwords, and the alias. Store at least one recovery copy outside GitHub and outside this app. Do not send any of those values in chat, commit them, or include them in an APK artifact.

In **GitHub repository → Settings → Secrets and variables → Actions**, add these repository secrets:

- `PRIVATE_GALLERY_SIGNING_KEYSTORE_BASE64` — contents of `private-gallery-release.jks.base64`
- `PRIVATE_GALLERY_SIGNING_STORE_PASSWORD`
- `PRIVATE_GALLERY_SIGNING_KEY_ALIAS` — `private-gallery`
- `PRIVATE_GALLERY_SIGNING_KEY_PASSWORD`

Record the displayed `SHA-256` certificate fingerprint from the `keytool -list -v` command in James's private custody notes. The trusted release workflow also publishes it in `private-gallery-release.metadata.json` with the APK digest.

## Trusted release path

The release workflow is manually dispatched only from `main` in `traynor1987/Private-gallery-`; it cannot run for fork pull requests or arbitrary branches. It runs unit tests and lint, builds a non-debuggable signed release, verifies it using `apksigner`, calculates the APK SHA-256, and publishes three official GitHub Release assets:

- `private-gallery-release.apk`
- `private-gallery-release.apk.sha256`
- `private-gallery-release.metadata.json` — version, versionCode, APK SHA-256 and public signing-certificate SHA-256

The dispatch tag must be the package version in the form `vX.Y.Z`; the workflow rejects a tag that does not match the APK's actual `versionName`.

The app accepts only a non-draft, non-prerelease release from `traynor1987/Private-gallery-` containing exactly named `private-gallery-release.apk` and `private-gallery-release.apk.sha256` assets. It verifies the downloaded APK digest before handing it to Android's installer. Android then enforces the permanent package signing identity.

Key loss or replacement after installation prevents normal in-place updates. Keep an encrypted offline custody/recovery copy under James's control before any production release is installed.

## Debug-install migration

The existing CI artifact is built by `assembleDebug` and therefore uses the Android debug signer. A production APK signed with the permanent key cannot update that debug installation. Before storing real Vault data, remove the debug build and install the first permanent production build. This resets that debug installation's app data, so export/test only disposable data first. Once the production-signed build is installed, future releases using the same signer update in place and retain Vault payloads, database state, PIN/key envelopes and settings.
