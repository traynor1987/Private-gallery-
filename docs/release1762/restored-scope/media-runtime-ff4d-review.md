# Media/UI runtime failures — ff4d correction stage

Source reviewed: `ff4d323ffb5948c79c8056070a92cdfde5103df3`, focused run `40745377742`, artifact `12842407311`, ZIP SHA-256 `06033b433a41db80c460b8d761f2cf18c98d5e0e21374aaa3eb140ee890f935a`.

These are corrections based on the failed Android run, not passing Android results.

## Picker test fixture

`HiddenUploadPickerPaging1762Test.actualTransferredLastItemBeyondFiveHundredSurvivesCancelReopenAndTypedPages` failed before VPN startup at its `video/mp4` import. The fixture passed PNG bytes to that import. The actual encrypted importer reached `HiddenRepository.thumbnail` and Android's `MediaMetadataRetriever.setDataSource` correctly rejected the invalid MP4.

The nonmatching-type fixture now uses the existing `androidTest/assets/browser-media/valid.mp4`, already used by production-import/transfer tests. Local `ffprobe` confirms MPEG-4 video, 64×64, 2 seconds. The actual authenticated Primary-to-Hidden transfer, 501 later encrypted photos, restricted item, MIME exclusion count, bounded pages, cancel/reopen, thumbnail and explicit egress assertions remain intact. No production media validation was relaxed.

## External editor tests

Two `ExternalEditingUi176Test` cases searched for the retired Samsung-only introduction/button. Production now says “Choose a compatible photo editor…” and “Open photo editor”, because discovery prioritises Samsung but accepts compatible image editors and retains its explicit editor chooser.

The tests now target those real strings and scroll to the entry action. Their original assertions remain: mounting and opening the consent does not prepare plaintext; Cancel keeps preparation at zero; explicit continuation prepares exactly once; edited-photo import remains a separate explicit action. The existing replacement and stale-preview assertions are unchanged.

## Menu colour measurement

The light menu cases sampled `captureToImage` of a **Text label** at its last two horizontal pixels and treated that glyph region as the menu background. That does not measure the named menu surface and can sample text ink. The production sort/speed menus now expose nonvisual test tags on their menu viewports. The assertions capture that actual viewport and sample its left background padding outside text/icons; they also require an opaque sample and include measured ARGB in failures.

Light (>180 mean channel), dark (<100) and 48dp action assertions are unchanged. There is no speculative production palette change. Android execution must verify the corrected measurement; no menu colour pass is claimed.

## Checks and limits

- `git diff --check`: passed.
- Fixture `ffprobe`: valid 64×64, 2-second MPEG-4.
- Reviewed changed production entries: only nonvisual menu test tags.
- No local Android SDK/emulator: compilation and Android execution remain pending.
- The downloaded artifact contains no PNG files despite test screenshot writes. Collect AGP `connected_android_test_additional_output` or the exact synthetic test-output directory in the next run before claiming screenshot inspection.

## Browser permission Back test

The exact failure dump for `BrowserModalSurfaces1762Test.coverDarkPermissionBackDismissDeniesWithoutGrant` shows Espresso selecting the `BASE_APPLICATION` (type1) Activity root with `has-window-focus=false`, then waiting10 seconds. A modal Dialog is expected to own focus while that Activity root is unfocused; the failure occurred in Espresso root selection before sending Back.

The test now waits for a focused attached secondary window belonging to the actual fixture Activity, sends Android's `GLOBAL_ACTION_BACK`, and waits for the real dialog state/window content to disappear. It still requires denial exactly once, no acceptance, and an Activity that has not finished. It does not invoke the onDismiss callback directly or alter production dialog behavior. Android execution is pending.
