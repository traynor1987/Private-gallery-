#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_REF:-}" == refs/heads/release/1.7.8-private-apps ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
python3 scripts/debug_apk_identity1762.py app/build/outputs/apk/debug/app-debug.apk
adb push "$RUNNER_TEMP/private-gallery-vpn1762/client.conf" /data/local/tmp/hidden-vpn1762.conf
adb shell chmod 600 /data/local/tmp/hidden-vpn1762.conf
adb shell wm size 1200x1800
adb shell wm density 160
classes='uk.co.traynor.privategallery.ui.PrivateAppsUi178Test,uk.co.traynor.privategallery.ui.PrivateAppControls178Test,uk.co.traynor.privategallery.PrivateAppsAuthority178Test,uk.co.traynor.privategallery.core.browser.hidden.HiddenPrivateAppsLive178Test'
./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$classes" \
 -Pandroid.testInstrumentationRunnerArguments.hiddenVpnConfig=/data/local/tmp/hidden-vpn1762.conf \
 -Pandroid.testInstrumentationRunnerArguments.hiddenVpnOrigin=http://fixture.invalid \
 "-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeUrl=${HIDDEN_ONION_PROBE_URL:?}" \
 "-Pandroid.testInstrumentationRunnerArguments.hiddenOnionProbeExpected=${HIDDEN_ONION_PROBE_EXPECTED:?}" --stacktrace
python3 scripts/verify_selected_android1762.py app/build/outputs/androidTest-results/connected/debug "$classes"
