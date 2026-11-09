#!/usr/bin/env bash
set -euo pipefail

[[ "${GITHUB_REPOSITORY:-}" == traynor1987/Private-gallery- ]]
[[ "${GITHUB_ACTIONS:-}" == true ]]
[[ "${GITHUB_REF:-}" == refs/heads/feature/novaos-sanctuary-integration ]]
[[ "$(git rev-parse HEAD)" == "${GITHUB_SHA:?Expected the tested commit}" ]]

apk=app/build/outputs/apk/debug/app-debug.apk
evidence=app/build/home2-evidence
sdk="${ANDROID_HOME:?}/build-tools/36.0.0"
output=app/build/fold-test-delivery
legacy_cert=8ddfe8b413812577c86d001d951219f6e3f79263f8771b856eba4bb633cab352
production_cert=94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17
expected_cert="${NOVA_FOLD_TEST_CERT_SHA256:?Configure the pinned test certificate fingerprint}"
[[ "$expected_cert" =~ ^[a-f0-9]{64}$ && "$expected_cert" != "$production_cert" && "$expected_cert" != "$legacy_cert" ]]
: "${NOVA_FOLD_TEST_KEYSTORE_BASE64:?Configure the dedicated test keystore secret}"
: "${NOVA_FOLD_TEST_KEYSTORE_PASSWORD:?Configure the dedicated test keystore password secret}"

[[ "$(< "$evidence/source-sha.txt")" == "$GITHUB_SHA" ]]
sha256sum -c "$evidence/SHA256SUMS.txt"
python3 - "$evidence/runtime.json" <<'PY'
import json
import sys

with open(sys.argv[1]) as report:
    runtime = json.load(report)
if runtime.get("result") != "PASS" or runtime.get("cases") != 263:
    raise SystemExit("The mandatory Android 17 regression matrix did not pass")
PY
grep -Fxq "source=$GITHUB_SHA" "$evidence/launcher-process-70/receipt.txt"
grep -Fxq 'api=37' "$evidence/launcher-process-70/receipt.txt"
grep -Fxq 'result=PASS' "$evidence/launcher-process-70/receipt.txt"

badging=$("$sdk/aapt" dump badging "$apk")
package_line=$(sed -n '/^package: /p' <<< "$badging")
pattern="^package: name='uk\.co\.traynor\.privategallery\.hiddenprototype' versionCode='([1-9][0-9]*)' versionName='[^']+-TEST-ONLY'"
[[ "$package_line" =~ $pattern ]]
version_code="${BASH_REMATCH[1]}"
(( version_code >= 71 ))
grep -Fxq "sdkVersion:'37'" <<< "$badging"
grep -Fxq "targetSdkVersion:'37'" <<< "$badging"

umask 077
private=$(mktemp -d "${RUNNER_TEMP:?}/nova-fold-test-sign.XXXXXXXX")
trap 'rm -rf -- "$private"' EXIT
printf '%s' "$NOVA_FOLD_TEST_KEYSTORE_BASE64" | base64 --decode > "$private/keystore.p12"
unset NOVA_FOLD_TEST_KEYSTORE_BASE64
keytool -exportcert -storetype PKCS12 -keystore "$private/keystore.p12" \
  -storepass:env NOVA_FOLD_TEST_KEYSTORE_PASSWORD -alias nova-fold-test \
  -file "$private/cert.der" >/dev/null 2>&1
read -r actual_cert _ < <(sha256sum "$private/cert.der")
[[ "$actual_cert" == "$expected_cert" ]] || { echo 'Dedicated Fold signing certificate differs from its pin.' >&2; exit 1; }

signed="$private/NovaOS-Fold-Test-code$version_code.apk"
"$sdk/apksigner" sign --ks "$private/keystore.p12" --ks-type PKCS12 \
  --ks-key-alias nova-fold-test --ks-pass env:NOVA_FOLD_TEST_KEYSTORE_PASSWORD \
  --key-pass env:NOVA_FOLD_TEST_KEYSTORE_PASSWORD --out "$signed" "$apk"
unset NOVA_FOLD_TEST_KEYSTORE_PASSWORD
rm -f -- "$private/keystore.p12"

"$sdk/apksigner" verify --verbose --print-certs "$signed" > "$private/signature.txt"
grep -Fxq 'Number of signers: 1' "$private/signature.txt"
grep -Fxq 'Verified using v2 scheme (APK Signature Scheme v2): true' "$private/signature.txt"
grep -Fxq "Signer #1 certificate SHA-256 digest: $expected_cert" "$private/signature.txt"
"$sdk/aapt" dump badging "$signed" > "$private/package.txt"
[[ "$(sed -n '/^package: /p' "$private/package.txt")" == "$package_line" ]]
"$sdk/zipalign" -c -P 16 4 "$signed"

# Verify that only APK signing metadata changed from the APK exercised by the test run.
python3 - "$apk" "$signed" <<'PY'
import hashlib
import re
import sys
import zipfile

signature_entry = re.compile(r"^META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))$", re.I)

def payload(path):
    with zipfile.ZipFile(path) as archive:
        entries = archive.namelist()
        if len(entries) != len(set(entries)):
            raise SystemExit("Unexpected duplicate APK entry")
        result = {}
        for entry in entries:
            if not entry.endswith("/") and not signature_entry.fullmatch(entry):
                with archive.open(entry) as contents:
                    result[entry] = hashlib.file_digest(contents, "sha256").hexdigest()
        return result

if payload(sys.argv[1]) != payload(sys.argv[2]):
    raise SystemExit("Signing changed the tested APK payload")
PY

mkdir -p "$output"
mv -- "$signed" "$private/signature.txt" "$private/package.txt" "$output/"
cp -- "$evidence/source-sha.txt" "$evidence/runtime.json" "$output/"
(cd "$output" && sha256sum "NovaOS-Fold-Test-code$version_code.apk" > SHA256SUMS.txt)
