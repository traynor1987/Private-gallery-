#!/usr/bin/env bash
set -euo pipefail
# No signer environment, application install, or emulator operations are used here.
control_root=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "${PRIVATE_GALLERY_1711_ROOT:-$control_root/..}"
for forbidden in KEYSTORE_BASE64 STORE_PASSWORD KEY_PASSWORD KEY_ALIAS ORG_GRADLE_PROJECT_PRIVATE_GALLERY_STORE_FILE ORG_GRADLE_PROJECT_PRIVATE_GALLERY_STORE_PASSWORD ORG_GRADLE_PROJECT_PRIVATE_GALLERY_KEY_ALIAS ORG_GRADLE_PROJECT_PRIVATE_GALLERY_KEY_PASSWORD; do
  [[ ! -v "$forbidden" ]] || { echo 'Fixture compilation must precede signer environment access' >&2; exit 1; }
done
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_EVENT_NAME:-}" == push && "${GITHUB_REPOSITORY:-}" == traynor1987/Private-gallery- && "${GITHUB_REF:-}" == refs/heads/integration/novaos-1.7-home2 ]] || { echo 'Unsupported fixture compilation invocation' >&2; exit 1; }
[[ "${FROZEN_SOURCE:-}" =~ ^[a-f0-9]{40}$ && "$FROZEN_SOURCE" != 0000000000000000000000000000000000000000 ]]
[[ "$(git rev-parse HEAD)" == "$FROZEN_SOURCE" ]]
fixture="${PRIVATE_GALLERY_1711_UPGRADE_FIXTURE:?Reviewed fixture required}"
[[ -s "$fixture" && "$(sha256sum "$fixture" | cut -d ' ' -f 1)" == "${PRIVATE_GALLERY_1711_UPGRADE_FIXTURE_SHA256:?Reviewed fixture hash required}" ]]
output="${PRIVATE_GALLERY_1711_COMPILED_DIR:-${RUNNER_TEMP:?}/upgrade1711-compiled}"
python3 - "$output" "$RUNNER_TEMP" <<'PYOUTPUT'
from pathlib import Path
import sys
output=Path(sys.argv[1]).resolve();temporary=Path(sys.argv[2]).resolve()
assert output != temporary and output.is_relative_to(temporary), 'Compilation output must remain under RUNNER_TEMP'
PYOUTPUT
# A fresh compilation is required; stale successful receipts may never be reused implicitly.
[[ ! -e "$output" ]]
mkdir -p "$output"
control_root=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
baseline=ab659cf3578d08cdc0deb643f887e561abb14c63
baseline_dir=$(mktemp -d "${RUNNER_TEMP:?}/upgrade1711-source.XXXXXX")
trap 'rm -rf "$baseline_dir"' EXIT
if ! git cat-file -e "$baseline^{commit}"; then git fetch --no-tags origin "$baseline"; fi
git archive "$baseline" | tar -x -C "$baseline_dir"
fixture_target="$baseline_dir/app/src/androidTest/java/uk/co/traynor/privategallery/upgrade/UpgradeFixtureTest.kt"
mkdir -p "$(dirname "$fixture_target")"
cp "$baseline_dir/app/build.gradle.kts" "$output/baseline-build.gradle.kts"
python3 - "$control_root" "$fixture" "$fixture_target" "$baseline_dir/app/build.gradle.kts" <<'PY'
import sys
from pathlib import Path
sys.path.insert(0,sys.argv[1]);from verify_compiled_upgrade1711 import transform
fixture,build=transform(Path(sys.argv[2]).read_bytes(),Path(sys.argv[4]).read_bytes())
Path(sys.argv[3]).write_bytes(fixture);Path(sys.argv[4]).write_bytes(build)
PY
(cd "$baseline_dir" && ./gradlew assembleDebugAndroidTest --stacktrace) 2>&1 | tee "$output/compile.log"
cp "$baseline_dir/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" "$output/instrumentation.apk"
"$ANDROID_HOME/build-tools/36.0.0/aapt" dump badging "$output/instrumentation.apk" > "$output/instrumentation-package.txt"
grep -Fq "package: name='uk.co.traynor.privategallery.test'" "$output/instrumentation-package.txt"

"$ANDROID_HOME/build-tools/36.0.0/aapt" dump xmltree "$output/instrumentation.apk" AndroidManifest.xml > "$output/instrumentation-manifest.txt"

python3 - "$control_root" "$fixture" "$fixture_target" "$baseline_dir/app/build.gradle.kts" "$output" "$FROZEN_SOURCE" <<'PY'
import json,sys
from pathlib import Path
sys.path.insert(0,sys.argv[1]);from verify_compiled_upgrade1711 import BASELINE,sha,validate
root=Path(sys.argv[5]);fixture=Path(sys.argv[2]).read_bytes();apk=(root/'instrumentation.apk').read_bytes();compiler=(Path(sys.argv[1])/'compile_upgrade1711_fixture.sh').read_bytes()
proof=dict(schema=1,decision='COMPILED',baseline_source_sha=BASELINE,candidate_source_sha=sys.argv[6],fixture_sha256=sha(fixture),transformed_fixture_sha256=sha(Path(sys.argv[3]).read_bytes()),baseline_build_sha256=sha((root/'baseline-build.gradle.kts').read_bytes()),transformed_build_sha256=sha(Path(sys.argv[4]).read_bytes()),compiler_sha256=sha(compiler),instrumentation_apk_sha256=sha(apk),instrumentation_apk_bytes=len(apk),instrumentation_package_sha256=sha((root/'instrumentation-package.txt').read_bytes()),instrumentation_manifest_sha256=sha((root/'instrumentation-manifest.txt').read_bytes()),transformations=['remove_debug_applicationIdSuffix','fixture_target_production_package'])
validate(proof,sys.argv[6],fixture,compiler,apk,(root/'baseline-build.gradle.kts').read_bytes())
(root/'compilation.json').write_text(json.dumps(proof,indent=2)+'\n')
PY
python3 "$control_root/verify_compiled_upgrade1711.py" --directory "$output" --source "$FROZEN_SOURCE" --fixture "$fixture" --compiler "$control_root/compile_upgrade1711_fixture.sh"
