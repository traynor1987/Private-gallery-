# Transfer and modal presentation audit

Scope: authoritative local versionCode 54 source; presentation only. Transfer engines, authentication direction, recovery and retention remain unchanged. Shared presentation is integrated at the activity owner callsites.

## Implemented

`GalleryFullPageDialog` is the shared window shell for complex workflows: app theme surface/content colors, full window, safe drawing and IME insets, scrolling body, and a centered body capped at 840 dp on wider windows. Lazy/independently scrolling content opts out of shell scrolling. It forwards dismissal and window security properties to the owner rather than inventing operation policy.

`HiddenRestoreDialog` now uses that shell instead of a compact 600 dp rounded dialog. Destination choices, separate copy/move confirmation and progress share one scrolling page; Primary authentication retains its existing keypad and SecureOn window. Authentication still requires fresh Primary credentials for Hidden → Primary. Phone export remains distinct, with visibility text. Primary → Hidden remains independently authenticated through its existing owner.

No `HiddenRestoreAccess` calls changed: close, authenticate, char-array wiping, lifecycle ON_STOP revocation, terminal compare-and-set, transfer arguments, progress updates and completion/recovery strings are preserved. Cancel retains its existing finish/close behavior; this change does not promise a new stop-after-item engine capability.

## Inventory and remaining integrations

| Existing surface | Classification / result |
| --- | --- |
| GalleryAlertDialog | Already shared themed compact confirmation/message shell; scrolling body and separately placed actions. Keep simple confirmations compact. |
| GalleryMenuSheet / SheetAction | Already shared themed scrolling menu, skip partial expansion, 52 dp minimum action rows, heading semantics. |
| GalleryDialogSurface | Existing bounded rounded surface; suitable for bounded content, not automatically a full-page workflow. |
| HiddenRestoreDialog | Previously bounded destination/auth/confirmation/progress workflow; migrated here. |
| PrimaryCopyDialog | Already secure full-screen authentication/thumbnail selection/progress; left untouched because selection belongs to picker workstream. |
| MainActivity ConfirmHiddenTransferDialog | Compact GalleryAlertDialog retained; shared NovaTransferSummary supplies matching direction/count/copy/move presentation. |
| MainActivity HiddenTransferProgressDialog | Integrated by activity owner onto GalleryFullPageDialog and NovaTransferProgress. Back/Cancel uses the existing safe-stop callback; stopping disables repeat cancellation. |
| TransferHistoryDialog | Already GalleryAlertDialog alias, but complex lazy history/resume/cancel/clear workflow remains bounded (420 dp). Full-page candidate; engines deliberately untouched. |
| AiEditingSettings guidance editors | Secure dialogs use GalleryDialogSurface at 90% height; complex form remains bounded. Full-page candidate for form owner. |
| MediaSelectionDialog | Already GalleryAlertDialog alias; complex date/size form remains compact. Picker owner controls migration. |
| MediaDetailsDialog / date/time | Already GalleryAlertDialog alias. Date/time dialogs are deliberate bounded choices; lengthy details are a full-page candidate. |
| ExternalEditingDialog | Already GalleryAlertDialog alias; main recovery/action body bounded, secondary export/replace/forget consent dialogs are appropriate compact confirmations. |
| PhotoEditor, CreateImageSheet, PrivacyShareDialog, BrowserPanels, PrivateBrowser, BrowserV2Home, VaultStoragePanel, PrivacyShieldSettings, RetiredModelCleanup | Imports alias GalleryAlertDialog; occurrences of AlertDialog are not duplicate Material3 shells. |
| HiddenFoundationDialog / SyntheticHiddenGalleryDialog | Already secure full-window entry/gallery workflows. |
| BrowserModalSurfaces internal media | Already full-window media; browser owner retains lifecycle and security policy. |
| Android biometric/permission/document-provider UI | Platform-owned; excluded from app shell styling. |

## Validation and limits

- Added instrumentation assertion `HiddenRestoreDialogTest.restoreUsesFullPageSurfaceWithReachableCancel`; existing tests cover explicit destinations, fresh Primary PIN, cancellation and lock revocation exactly once.
- Attempted `./gradlew :app:compileDebugAndroidTestKotlin --offline`; blocked before compilation because Gradle 9.5.0 is absent and wrapper download fails with `Network is unreachable`. No Android tests or rendered Fold/large-font/IME checks ran locally; the added test has not been observed red or green.
- Focused host inspection passed: migration retains credential wiping, authentication, transfer arguments, lifecycle/terminal delivery and recovery strings; shell includes theme/inset handling.
- `git diff --check` passed. Host source checks do not establish runtime layout correctness.
- Required device follow-up: folded and unfolded windows, 200% font, landscape/short height, keyboard visible, all theme modes; scroll every destination and Cancel into view, authenticate both directions, copy/move bulk selection, stop/cancel during transfer, lock/background and recovery. No runtime claims are made by this audit.

Final focused evidence: compilation/host checks and 7 JVM cases pass; 25 original API37 cases pass at 79f6aaa and three test-only corrected retries pass at b2d0239. See STATUS.md for exact source/artifact accounting and remaining limitations. No physical Fold or Google transaction approval.
