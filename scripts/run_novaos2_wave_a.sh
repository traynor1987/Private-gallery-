#!/usr/bin/env bash
set -euo pipefail

[[ "${GITHUB_ACTIONS:-}" == true ]]
bash scripts/run_novaos2_api37.sh --wave-a

out=app/build/novaos2
app_id=uk.co.traynor.privategallery.hiddenprototype
adb exec-out run-as "$app_id" cat cache/novaos2-wave-a-timings.txt > "$out/wave-a-emulator-samples.txt"
for key in cover_swipe_to_control_ms cover_control_debug_proportional_kib inner_tap_to_control_ms inner_apply_theme_ms; do
    grep -Eq "^${key}=[1-9][0-9]*$" "$out/wave-a-emulator-samples.txt"
done

bash scripts/run_launcher70_restart.sh
bash scripts/run_guardian_notification_pipeline93.sh
adb shell wm size 1200x1800
adb shell wm density 160
bash scripts/run_guardian_notification_pipeline93.sh GuardianFoldNotificationTest guardian-fold-notification
