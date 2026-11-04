#!/usr/bin/env bash
set -euo pipefail
baseline=a5add01b8c8fccf88be2fce8d7280d4b6e7672e2
# Reuse the exact successful development APK build; no application inputs changed.
git diff --exit-code "$baseline" HEAD -- preview/app preview/build.gradle.kts preview/settings.gradle.kts preview/gradle.properties
mkdir -p preview/release-out
gh api repos/traynor1987/Private-gallery-/actions/artifacts/13358999836/zip > preview/release-out/source-artifact.zip
python3 - <<'PY'
import hashlib,pathlib,zipfile
root=pathlib.Path('preview/release-out');archive=root/'source-artifact.zip'
assert hashlib.sha256(archive.read_bytes()).hexdigest()=='8e3156c986538c895a7237caac9deac767db87f78b38ef25808840074828277a'
with zipfile.ZipFile(archive) as z:
 source=z.read('artifacts/SOURCE_SHA.txt').decode().strip()
 assert source=='a5add01b8c8fccf88be2fce8d7280d4b6e7672e2'
 apk=z.read('artifacts/NovaOS-Home2-Store-Preview.apk')
 expected=z.read('artifacts/SHA256SUMS.txt').decode().split()[0]
 assert hashlib.sha256(apk).hexdigest()==expected
 (root/'NovaOS-Home2-Store-Preview.apk').write_bytes(apk)
print('Verified original artifact digest, source and APK checksum')
PY
python3 preview/release/verify_apk.py \
 --apk preview/release-out/NovaOS-Home2-Store-Preview.apk \
 --aapt "$ANDROID_HOME/build-tools/36.0.0/aapt" \
 --apksigner "$ANDROID_HOME/build-tools/36.0.0/apksigner" \
 --zipalign "$ANDROID_HOME/build-tools/36.0.0/zipalign" \
 --source "$GITHUB_SHA" --output preview/release-out
