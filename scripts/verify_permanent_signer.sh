#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_REPOSITORY:-}" == traynor1987/Private-gallery- ]]
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_REF:-}" == refs/heads/release/1.7-encrypted-camera ]]
: "${KEYSTORE_BASE64:?Permanent signing identity unavailable}"
: "${STORE_PASSWORD:?}" "${KEY_ALIAS:?}" "${KEY_PASSWORD:?}"
keystore="$RUNNER_TEMP/private-gallery-release.jks"
printf '%s' "$KEYSTORE_BASE64" | base64 --decode > "$keystore"
chmod 600 "$keystore"
probe=$(mktemp -d)
trap 'rm -rf "$probe"' EXIT
keytool -exportcert -keystore "$keystore" -storepass:env STORE_PASSWORD -alias "$KEY_ALIAS" -file "$probe/cert.der" >/dev/null 2>&1
expected=94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17
[[ "$(sha256sum "$probe/cert.der" | cut -d' ' -f1)" == "$expected" ]]
printf 'Private Gallery signer availability probe\n' > "$probe/probe.txt"
jar cf "$probe/probe.jar" -C "$probe" probe.txt
jarsigner -keystore "$keystore" -storepass:env STORE_PASSWORD -keypass:env KEY_PASSWORD "$probe/probe.jar" "$KEY_ALIAS" >/dev/null 2>&1
jarsigner -verify "$probe/probe.jar" > "$probe/verification.txt"
grep -q 'jar verified' "$probe/verification.txt"
mkdir -p app/build/acceptance-diagnostics
printf 'PASS: original permanent private key available; certificate SHA256 %s\n' "$expected" > app/build/acceptance-diagnostics/permanent-signer.txt
