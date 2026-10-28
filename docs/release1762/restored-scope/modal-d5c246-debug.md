# Modal failures from focused d5c246 Android 17 run

Status: changes prepared; Android runtime verification PENDING. The measured run remains RED (40755407244: 273 cases, 255 pass, 18 fail). These changes do not authorize acceptance or claim the six owned failures now pass.

## Evidence reviewed

- Tested base: `d5c246a6c1a366e71cee47751dd9062cec23b199`; working HEAD when assigned: `9f6d031679e311c551f6d865d80a6f26340d18fd`.
- Extracted artifact: `/tmp/focused-d5c246`, supplied verified checksum `4701aa33b739a55ad86f49fe9c90f318e1eaa6a6b368f9acdb39631a96137bef`; full failure stacks: `/tmp/focused-d5c246-failures.txt`.
- Four `RemainingMediaModals1762Test` variants fail at line 63 before speed-menu checks: `Menu sample must be opaque, not an isolated transparent glyph`. The helper captured the `DropdownMenu` modifier's tagged content viewport, not its ancestor painted Surface. Moving a tag off glyphs did not include that ancestor. No menu PNG was saved by the previous helper, so the actual screen palette cannot be adjudicated from those failures.
- `BrowserModalSurfaces1762Test.coverDarkBusyVideoKeepsExplicitCancelWhenBackCannotDismissProgress` fails in Espresso `pressBack()` at line 115, with `RootViewWithoutFocusException`. The reported root is explicitly `BASE_APPLICATION`, 320×640, `has-window-focus=false`; this is the Activity behind the Dialog. No Dialog Back behavior assertion ran. The already-corrected permission Back test in the same class provides an owned-focused-window example.
- `ModalCompletion1762Test.landscapeDarkSourcePickerCanReachLastOfLargeVaultAtLargeText` reaches its 10-second thumbnail wait at line 142 and times out. Its per-test logcat contains the timeout but no image decode error; the decode path catches errors without logging. There is no source-picker failure screenshot or semantic dump in this artifact. Therefore a zero-height grid or failed decode is not directly proven by the old artifact.
- Visually inspected `remaining-media-modals1762/DARK-840x392-4.png`: the landscape Dialog image is only 320 px wide and 392 px tall; title/footer remain visible while long collection content scrolls. Actual font-scale 2 configuration is independently proven by the passing probe and is retained.

## Changes and root-cause limits

1. Menu sampling now captures the actual `isPopup()` root (which includes the ancestor Surface) and maps the same 4-pixel viewport padding point into that image. It first checks that the mapped sample is inside the root. Original alpha ≥250 and light >180 / dark <100 luminance requirements remain unchanged. Both menus now save their popup PNG, semantics, bounds and sampled ARGB before asserting. No production menu color was changed: the previous evidence measures an isolated content layer, not a demonstrated wrong screen color.
2. Busy-video Back waits for an attached, focused, non-Activity root owned by the test Activity, then requests actual Android global Back. It checks the request succeeded, cancellation stays zero, the Activity survives, and explicit visible Cancel still invokes cancellation once. No direct dismiss callback replaces Back; production behavior is unchanged.
3. Source picker: a fixed `Photos from ...` header precedes the grid inside the constrained AlertDialog body. At font scale 2 and a short, default-width Dialog, this header can consume the remaining body height independently of the grid. This is a source-supported layout hypothesis for the timeout, not a measured proof from the incomplete old artifacts. Move the introduction into a full-span lazy-grid item so it scrolls away and the grid receives the whole bounded body viewport. The fixed title and Cancel footer remain in the existing AlertDialog slots. Maximum grid height stays 360 dp; adaptive cells, stable candidate keys, two-decode semaphore, authenticated access, byte wiping, bitmap recycling and cancellation remain unchanged.
4. Source test now targets the named grid, requires a visible ≥48 dp viewport and candidate, scrolls to index 300 (introduction at zero; still selects synthetic-299), rechecks ≥48 dp visible Cancel after scrolling, and saves real foreground-window screenshots plus grid semantics initially and after the thumbnail wait, including on timeout. These artifacts distinguish grid geometry from decode failure if the hypothesis is incomplete.

## Checks and outstanding runtime

- `git diff --check`: PASS after these changes.
- Reviewed the four owned Kotlin diffs, existing Dialog shell, renderer/thumbnail lifecycle, failing full stacks and owned per-test logcat.
- No Android SDK/adb is available locally; no Kotlin compilation or device execution is claimed. The unrelated prior 193 local test passes are not rerun or repurposed as UI evidence.
- Required next check: compile and execute these three modal suites on Android 17 at current OS font scale 2. Retain all four palette variants and both menus, actual system Back plus explicit Cancel, and last-of-300 real thumbnail selection. Inspect newly emitted menu and source-picker evidence even if assertions pass.
- If source picker still fails, use the new measured grid bounds/semantics and actual foreground screenshot to separate layout from decode; do not lower opacity, color, timeout, or 48 dp action requirements.

Only `RemainingMediaModals1762Test`, `BrowserModalSurfaces1762Test`, `ModalCompletion1762Test`, `AiSourceCards`, and this report are owned by this change. No commit/push, core browser, BrowserV2Home, device-picker, OS-font configuration, or Main14 modal edits were made.
