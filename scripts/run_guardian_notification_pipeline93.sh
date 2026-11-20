#!/usr/bin/env bash
# Install a synthetic notification source only on the disposable Android 17 CI emulator.
set -euo pipefail
[[ "$GITHUB_ACTIONS" == true ]]
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == 37 ]]
[[ -z "$(adb shell pm path com.whatsapp)" ]]
fixture=scripts/fixtures/guardian93
sdk="$ANDROID_HOME/platforms/android-37.1/android.jar"
if [[ ! -f "$sdk" ]]; then sdk="$ANDROID_HOME/platforms/android-37/android.jar"; fi
test -f "$sdk"
tools="$ANDROID_HOME/build-tools/36.0.0"
scratch=$(mktemp -d)
installed=false
cleanup() {
    local status=$?
    if [[ "$installed" == true ]]; then adb uninstall com.whatsapp || status=1; fi
    rm -rf "$scratch"
    exit "$status"
}
trap cleanup EXIT
mkdir "$scratch/classes" "$scratch/dex"
javac --release 17 -classpath "$sdk" -d "$scratch/classes" "$fixture/SendFixture.java"
"$tools/d8" --min-api 37 --lib "$sdk" --output "$scratch/dex" "$scratch/classes/com/whatsapp/SendFixture.class"
"$tools/aapt2" link -I "$sdk" --manifest "$fixture/AndroidManifest.xml" -o "$scratch/unsigned.apk"
jar --update --file "$scratch/unsigned.apk" -C "$scratch/dex" classes.dex
"$tools/zipalign" -f 4 "$scratch/unsigned.apk" "$scratch/aligned.apk"
keytool -genkeypair -keystore "$scratch/fixture.p12" -storetype PKCS12 -storepass android \
    -alias guardian93-fixture -keyalg RSA -keysize 2048 -validity 1 -dname 'CN=NovaOS Guardian CI Fixture'
"$tools/apksigner" sign --ks "$scratch/fixture.p12" --ks-key-alias guardian93-fixture \
    --ks-pass pass:android --out "$scratch/fixture.apk" "$scratch/aligned.apk"
"$tools/apksigner" verify "$scratch/fixture.apk"
adb install "$scratch/fixture.apk"
installed=true
adb shell pm grant com.whatsapp android.permission.POST_NOTIFICATIONS
bash scripts/run_visual_checkpoint.sh GuardianNotificationPipeline93Test guardian-notification-pipeline-93 uk.co.traynor.privategallery.core.guardian
