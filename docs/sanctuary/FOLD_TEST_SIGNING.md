# NovaOS Fold test signing

The isolated application ID is `uk.co.traynor.privategallery.hiddenprototype`. Its permanent test signer has no authority over the production application. The first APK under the permanent signer is a fresh installation for anyone using the runner-signed Code 70 APK. Export any ordinary test launcher layout you want to keep before uninstalling that test app. Android deletes its local data when it is uninstalled.

## One-time provisioning

Run these commands on a trusted machine with a JDK, OpenSSL, and an authenticated GitHub CLI that can administer this repository's Actions secrets. Choose a private directory outside the checkout on encrypted storage. Run with shell tracing disabled.

```bash
set +x
set -euo pipefail
umask 077
repo=traynor1987/Private-gallery-
cd /path/to/private-signing-directory
test ! -e fold-test.p12 && test ! -e fold-test.password || exit 1

openssl rand -hex 32 > fold-test.password
keytool -genkeypair -storetype PKCS12 -keystore fold-test.p12 \
  -storepass:file fold-test.password -keypass:file fold-test.password -alias nova-fold-test \
  -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=NovaOS Fold Test'
keytool -exportcert -storetype PKCS12 -keystore fold-test.p12 \
  -storepass:file fold-test.password -alias nova-fold-test -file fold-test-cert.der
fingerprint=$(sha256sum fold-test-cert.der | cut -d ' ' -f 1)

base64 < fold-test.p12 | gh secret set NOVA_FOLD_TEST_KEYSTORE_BASE64 --repo "$repo"
gh secret set NOVA_FOLD_TEST_KEYSTORE_PASSWORD --repo "$repo" < fold-test.password
gh variable set NOVA_FOLD_TEST_CERT_SHA256 --repo "$repo" --body "$fingerprint"
printf 'Fold test certificate SHA-256 %s\n' "$fingerprint"
```

Save the public fingerprint with the build records. Keep the encrypted PKCS#12 file and its password in the approved private backup store, with independently recoverable copies and access restricted to the owner. Record the alias `nova-fold-test` with the fingerprint. Keep the keystore and password out of chat, Git, build artifacts and logs.

## Build and recovery

Run **Private Gallery fast development** on `feature/novaos-sanctuary-integration` using **Run workflow**. If manual dispatch is unavailable on that branch, a signing push touching a workflow-triggered path can use commit markers `[home2-runtime] [visual-full] [fold-test-sign] [verification-only]`. Signing is gated on the Android 17 checks and the tested APK digest. The **novaos-fold-test-signed** artifact contains the APK, signer and package reports, checksum, source SHA and signed fresh-install smoke evidence.

If the GitHub signing configuration is lost, retrieve the original encrypted keystore and password from the private backup. Export its public certificate again and compare its SHA-256 with the recorded fingerprint and a previously published test APK. Restore the two Actions secrets and the original fingerprint variable only after they match. Re-run signing and compare the emitted APK certificate to the recorded fingerprint. A replacement private key starts a new test signing identity and requires a fresh test installation. The production signer is separate.

Record actual upgrade compatibility after a later build with a higher version code has been installed over a prior permanently signed test APK and its test data has been verified.
