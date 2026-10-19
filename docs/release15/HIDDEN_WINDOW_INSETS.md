# Hidden full-screen dialog system insets

Owner screenshot received19October2026: Samsung gesture handle visibly crosses bottom navigation icons; labels fall beyond the bottom edge. The top title and media content remain visible. Previous missing screenshot blocker is resolved; physical correction acceptance remains pending.

Hidden media is a separate Dialog window with usePlatformDefaultWidth=false and default decorFitsSystemWindows=true, containing the normal V1 Scaffold/NavigationBar. Primary Activity window handling does not establish the Hidden dialog window's inset contract. Target SDK36 enforces modern edge-to-edge behavior. Correction explicitly sets decorFitsSystemWindows=false and applies WindowInsets.safeDrawing at the Hidden content Surface boundary. Android system/IME/cutout insets are consumed there; descendant Material navigation observes consumption rather than receiving a second spacer. No arbitrary padding, screen-height offset, Primary shell change or authentication/storage change.

Reference: https://developer.android.com/reference/kotlin/androidx/compose/ui/window/DialogProperties and https://developer.android.com/develop/ui/compose/system/insets-ui .

Added real Hidden dialog test compares all five visible navigation labels and un-clipped selectable touch-target bounds against actual platform system/IME bottom inset. Local Android runtime unavailable; CI execution required before reporting the new test passed. Pixel emulator success alone cannot prove Samsung gesture/OEM Fold acceptance. Owner should check complete bottom edge folded/unfolded, keyboard open/closed and gesture/three-button modes with disposable media. Screenshot preference, independent PIN, return to Primary and Hidden timeout behavior must remain intact.

Existing CI593169-test pass applies to its exact previous source, not this correction. No main/V2/PR59 modifications, production install or real owner media use.
