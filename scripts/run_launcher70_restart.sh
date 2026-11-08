#!/usr/bin/env bash
set -euo pipefail

package=uk.co.traynor.privategallery.hiddenprototype
runner=$package.test/androidx.test.runner.AndroidJUnitRunner
test_class=uk.co.traynor.privategallery.launcher.LauncherProcess70Test
launcher=$package/uk.co.traynor.privategallery.launcher.NovaLauncherActivity
output=app/build/home2-evidence/launcher-process-70
mkdir -p "$output"
api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
[[ "$api" == 37 && "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
prefs=shared_prefs/nova_android_home_v1.xml
backup=files/launcher-70-restart-backup.xml
adb shell am force-stop "$package"
adb shell "run-as $package sh -c 'set -e; mkdir -p files; test ! -e $backup; if test -f $prefs; then cp $prefs $backup; fi'"
restore() {
  adb shell am force-stop "$package"
  adb shell "run-as $package sh -c 'if test -f $backup; then mv $backup $prefs; else rm -f $prefs; fi'"
}
trap restore EXIT

run_method() {
  adb shell am instrument -w -r -e class "$test_class#$1" "$runner" | tee "$output/$2.txt"
  grep -Eq '^OK \(1 test\)[[:space:]]*$' "$output/$2.txt"
}
start_home() {
  adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n "$launcher" | tee "$output/$1.txt"
  grep -Eq '^Status: ok[[:space:]]*$' "$output/$1.txt"
}
process() {
  adb shell pidof "$package" | tr -d '\r'
}

run_method seedConfiguredLauncher seed
start_home before
old_pid=$(process)
[[ "$old_pid" =~ ^[1-9][0-9]*$ ]]
adb shell am force-stop "$package"
[[ -z "$(process || true)" ]]
start_home after
new_pid=$(process)
[[ "$new_pid" =~ ^[1-9][0-9]*$ && "$new_pid" != "$old_pid" ]]
run_method verifyConfiguredLauncherAfterProcessDeath verify
printf 'source=%s\napi=%s\nold_pid=%s\nnew_pid=%s\nresult=PASS\n' "$(git rev-parse HEAD)" "$api" "$old_pid" "$new_pid" > "$output/receipt.txt"
