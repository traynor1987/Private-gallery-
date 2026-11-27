#!/usr/bin/env bash
set -euo pipefail

out=app/build/novaos2
debug=app/build/outputs/apk/debug/app-debug.apk
tests=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
namespace=uk.co.traynor.privategallery
app_id="$namespace.hiddenprototype"
fixture_root="app/src/androidTest/java/${namespace//./\/}/novaos2"
mkdir -p "$out"
bash scripts/verify_android17_device.sh "$out"
sha256sum "$debug" "$tests" > "$out/debug-inputs.sha256"

# Wait for the package manager and compositor as well as sys.boot_completed.
stable=0
previous=''
for attempt in {1..30}; do
    compositor=$(adb shell pidof surfaceflinger | tr -d '\r' || true)
    service=$(adb shell service check package | tr -d '\r' || true)
    if [[ -n "$compositor" && "$service" == 'Service package: found' ]] &&
        adb shell cmd package path android | grep -q '^package:'; then
        if [[ "$compositor" == "$previous" ]]; then ((stable+=1)); else stable=1; fi
        if ((stable >= 5)); then break; fi
    else
        stable=0
    fi
    previous=$compositor
    sleep 3
done
((stable >= 5))
adb shell wm dismiss-keyguard
adb install --no-streaming -r "$debug" | tee "$out/install-app.txt"
adb install --no-streaming -r "$tests" | tee "$out/install-tests.txt"
grep -Fxq Success "$out/install-app.txt"
grep -Fxq Success "$out/install-tests.txt"
adb shell run-as "$app_id" rm -rf cache/novaos2-captures

classes=()
expected=0
while IFS= read -r test; do
    class=${test#app/src/androidTest/java/}
    class=${class%.kt}
    count=$(grep -Ec '@Test([[:space:]]|$)' "$test")
    classes+=("${class//\//.}")
    expected=$((expected + count))
done < <(
    find "$fixture_root" -type f -name '*Test.kt' | sort
    theme_test="app/src/androidTest/java/${namespace//./\/}/launcher/LauncherThemesStudioUiTest.kt"
    if [[ -f "$theme_test" ]]; then printf '%s\n' "$theme_test"; fi
)
# Verify the actual encrypted utilities reused by this branch alongside its new entry points.
if [[ -f "$fixture_root/NovaUtilitiesApi37Test.kt" ]]; then
    core="$namespace.core.home2"
    for method in \
        clipboard_privacy_setting_survives_reopen_and_retention_updates \
        files_history_and_favourites_are_encrypted_and_bound_to_the_selected_tree \
        files_remember_only_the_explicit_tree_and_reject_path_like_names \
        recurring_calendar_event_keeps_one_skipped_occurrence_across_reopen \
        calendar_preserves_recurring_wall_time_across_dst_and_reopens_events \
        recurring_tasks_keep_the_monthly_anchor_without_duplicate_successors \
        tasks_keep_due_date_completion_and_project_after_reopen \
        notes_publish_draft_and_clear_recovery_in_the_same_transaction; do
        classes+=("$core.Home2IntegrationTest#$method")
        ((expected+=1))
    done
    classes+=("$core.SpacePreferencesTest#reminders_persist_reschedule_and_cancel_through_the_android_alarm")
    ((expected+=1))
fi
((expected > 0))
selection=$(IFS=,; printf '%s' "${classes[*]}")
printf '%s\n' "${classes[@]}" > "$out/test-selection.txt"
adb shell am instrument -w -r -e class "$selection" "$app_id.test/androidx.test.runner.AndroidJUnitRunner" | tee "$out/instrumentation.txt"

if adb shell run-as "$app_id" test -d cache/novaos2-captures; then
    mkdir -p "$out/screenshots"
    while IFS= read -r name; do
        [[ "$name" =~ ^[a-z0-9-]+\.png$ ]]
        adb exec-out run-as "$app_id" cat "cache/novaos2-captures/$name" > "$out/screenshots/$name"
    done < <(adb shell run-as "$app_id" ls cache/novaos2-captures | tr -d '\r')
fi

grep -Eq "^OK \(${expected} tests?\)[[:space:]]*$" "$out/instrumentation.txt"
! grep -Eq '^INSTRUMENTATION_STATUS_CODE: -[1-4][[:space:]]*$' "$out/instrumentation.txt"
sha256sum --check "$out/debug-inputs.sha256"

if [[ -f "$fixture_root/NovaUtilitiesApi37Test.kt" ]]; then
    [[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
    permission=android.permission.POST_NOTIFICATIONS
    # This debug app was freshly installed on this disposable emulator above.
    # Keep grant/deny in separate instrumentation processes; Android kills an app on revocation.
    for state in granted denied; do
        if [[ "$state" == granted ]]; then
            adb shell pm grant "$app_id" "$permission"
        else
            adb shell pm revoke "$app_id" "$permission"
        fi
        result="$out/reminder-$state.txt"
        adb shell am instrument -w -r -e novaReminderPermission "$state" \
            -e class "$namespace.core.home2.SpacePreferencesTest#due_task_dispatch_honours_android_permission_and_posts_only_once" \
            "$app_id.test/androidx.test.runner.AndroidJUnitRunner" | tee "$result"
        grep -Eq '^OK \(1 test\)[[:space:]]*$' "$result"
        ! grep -Eq '^INSTRUMENTATION_STATUS_CODE: -[1-4][[:space:]]*$' "$result"
    done
fi
