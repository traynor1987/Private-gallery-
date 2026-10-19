# Hidden full-screen dialog system insets

Owner screenshot received19October2026: Samsung gesture handle visibly crosses bottom navigation icons; labels fall beyond the bottom edge. The top title and media content remain visible. Previous missing screenshot blocker is resolved; physical correction acceptance remains pending.

Hidden media is a separate Dialog window with usePlatformDefaultWidth=false and default decorFitsSystemWindows=true, containing the normal V1 Scaffold/NavigationBar. Primary Activity window handling does not establish the Hidden dialog window's inset contract. Target SDK36 enforces modern edge-to-edge behavior. Correction explicitly sets decorFitsSystemWindows=false and applies WindowInsets.safeDrawing at the Hidden content Surface boundary. Android system/IME/cutout insets are consumed there; descendant Material navigation observes consumption rather than receiving a second spacer. No arbitrary padding, screen-height offset, Primary shell change or authentication/storage change.

Reference: https://developer.android.com/reference/kotlin/androidx/compose/ui/window/DialogProperties and https://developer.android.com/develop/ui/compose/system/insets-ui .

Added real Hidden dialog test compares all five visible navigation labels and un-clipped selectable touch-target bounds against actual platform system/IME bottom inset. Local Android runtime unavailable; CI execution required before reporting the new test passed. Pixel emulator success alone cannot prove Samsung gesture/OEM Fold acceptance. Owner should check complete bottom edge folded/unfolded, keyboard open/closed and gesture/three-button modes with disposable media. Screenshot preference, independent PIN, return to Primary and Hidden timeout behavior must remain intact.

Existing CI593169-test pass applies to its exact previous source, not this correction. No main/V2/PR59 modifications, production install or real owner media use.


## CI594 verified candidate

Tested source `4bf464ee1a580f30a962fa8f9eb789b563c4cb40`. [Download APK/reports ZIP](https://github.com/traynor1987/Private-gallery-/actions/runs/39180954931/artifacts/12156702826). Extract `outputs/apk/debug/app-debug.apk`. CI594 SUCCESS:170Android tests,0failures/errors/skips; JVM/lint/build and source/network checks passed. The new actual-window inset/navigation test passed. Local-pending statements above describe the pre-CI checkpoint.

APK SHA-256 `2824a33853767a7f108dab9bc0e1e16983f68e5bfbe166c9f89e52f8eb1d98ec`. Package `uk.co.traynor.privategallery.hiddenprototype`,1.0.27-TEST-ONLY/code28, debuggable. Signature/content digest verified. Debug certificate SHA-256 `6aef06bf6450b3e0fc7cd9496a7890c6afba2f999bbe42e7f3f30fb0f33ee992`; differs from CI593. In-place update may fail; do not uninstall any app holding needed media or production V1. Hidden has no export/recovery.

Physical acceptance remains OPEN: folded/unfolded, keyboard closed/open, Samsung gesture/three-button navigation; confirm all5 labels/icons/touch targets above system bar without duplicate spacer. Check screenshot preference, separate PIN, manual lock, background timeout and Return to normal Vault. Use synthetic media only. No production acceptance declared.

Evidence: [CI594 summary](evidence/ci594-summary.txt), [APK inspection](evidence/ci594-apk-inspection.json).
