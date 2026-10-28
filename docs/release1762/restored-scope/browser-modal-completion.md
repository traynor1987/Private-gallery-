# Primary browser modal completion stage

Source work on 28 October 2026. This is a direct component coverage definition, not an Android pass, physical Fold acceptance, keyboard/OEM or TalkBack claim. No local Android SDK is available. Parent must compile and execute the following full class in focused CI:

`uk.co.traynor.privategallery.ui.BrowserModalSurfaces1762Test`

## Production change

The exact UI formerly embedded in `BrowserV2Home.kt` now calls internal production components in `BrowserModalSurfaces.kt`. Session ownership, permission checks, Android permission contracts, denial callbacks, media save source validation, cancellation tokens, external navigation plans and explicit egress confirmation remain in `BrowserV2Home.kt`. Tests invoke those same components; there is no duplicated test dialog implementation or network grant in a fixture.

All alerts use `GalleryAlertDialog`, selected app theme and minimum-48dp action targets. The diagnostics Copy/Clear controls moved from a potentially tall fixed footer into the bounded scroll body, with Close pinned. Diagnostics focus controls and Find Previous/Next wrap. Find now has a labeled text field and Search IME callback. The internal player preserves its network gate and black media canvas and uses safe drawing insets for its exit/control chrome.

## Exact inventory mapping

These original inventory IDs remain stable even as source line numbers move. D below means actual component test definitions; every execution remains pending.

| Inventory ID | Actual component | Exact test method(s) |
|---|---|---|
| 33 | `BrowserInternalMediaDialog` + actual `PrivateVideoPlayer` with network denied | `landscapeDarkInternalMediaKeepsExitUsableWhenNetworkingIsDenied` |
| 34 | `BrowserSaveVideoDialog` | `landscapeLightVideoConsentProgressAndCancellationDoNotImplicitlySave`, `coverDarkBusyVideoKeepsExplicitCancelWhenBackCannotDismissProgress` |
| 38 | `BrowserFindDialog` | `coverLightFindRetainsQueryAndKeyboardSearchUntilExplicitClose` |
| 39 | `BrowserDiagnosticsDialog` | `landscapeDarkDiagnosticsLongReportKeepsActionsReachableAndIndependent` |
| 41 | `BrowserMessageDialog` | `coverDarkLongBrowserMessageScrollsWithoutHidingAcknowledgement` |
| 42 | `BrowserConsentDialog(CAPABILITY)` | `coverDarkEveryCapabilityAndSaveConsentRequiresExplicitChoice`, `landscapeLightEveryCapabilityAndSaveConsentDispatchesOnlyChosenAction`, `coverDarkPermissionBackDismissDeniesWithoutGrant` |
| 43 | `BrowserConsentDialog(LOCATION)` | same two full-enum consent methods above |
| 44 | `BrowserConsentDialog(SAVE_IMAGE)` | same two full-enum consent methods above |
| 45 | `BrowserConsentDialog(SCREENSHOT)` | same two full-enum consent methods above |
| 46 | `BrowserExternalNavigationDialog` actual APP/WEBSITE/UNSUPPORTED choices | `coverLightExternalNavigationPlansKeepCancelSeparateFromOpen` |

The class uses real 320×640 and 840×392 windows at 2× text in selected light/dark themes. Its assertions exercise the decisions/actions, progress cancellation, scrolling, long structured reports, Search IME action, external choice dispatch, and exit while actual media networking is denied. Permission/save enum loops cover all four real production definitions. These tests do not grant Android permissions, start an external app, send media, run provider AI or contact a synthetic video origin.

## Verification performed locally

`git diff --check` passes. Source call-site review confirms all ten inventory surfaces use the extracted components. No Android compilation/runtime result is claimed, and no final signing approval is provided.
