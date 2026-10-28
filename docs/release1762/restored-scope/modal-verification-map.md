# Modal verification map — restored scope

Read-only source audit on 28 October 2026. This maps **all 93 rows** of `docs/release1762/MODAL_INVENTORY_CURRENT.md` in original order. Its stored line numbers are identifiers, not assertions that current lines have not moved. Main lifecycle and native Hidden transport/child sources are changing concurrently; child mappings are provisional WIP and require rerun against the final frozen SHA. The initial read-only mapping made no changes; the completion stage below adds targeted source and test changes, without implying an Android pass.

**D** means a real component/path is exercised by an identified test method in source; it does **not** mean that method passed on the current source. **R** means related behavior/policy only. **U** means direct component verification is unestablished. No current Android execution artifacts were inspected here; no new passing run, screenshot review, physical device, keyboard/OEM, TalkBack or fold-transition proof is claimed. Historical 117-case evidence belongs to the earlier frozen source and cannot verify new child/modal work.

## Shared shells and findings

`A` = GalleryAlertDialog alias: selected Material3 surfaceContainerHigh/onSurface/onSurfaceVariant, outline, shape and tonal elevation; default body scrolling and pinned actions. `G` = raw Compose Dialog containing GalleryDialogSurface with matching selected-theme decoration. `M` = Material3 menu/sheet inheriting the selected theme. `S` = custom Material3 Surface/media canvas. `N` was the original native date/time picker family; the completion stage replaces both app-owned native pickers with A. Android permission/biometric/document-provider UI remains system-owned. Every alert importer in this inventory routes to A, except the underlying Material3 constructor in A itself. No Android AlertDialog.Builder or PopupWindow family was found in production search; a constructor count alone is not sufficient acceptance.

1. **Retained legacy-only popup close-action contrast/insets:** `ui/PrivateBrowser.kt` temporary popup WebView overlay (outside the 93 constructor rows) still places a bare IconButton over website content with no opaque theme backing and no safeDrawing inset. This is a concrete source contrast/inset risk. The composable is actually named `BrowserHome`; a complete `rg` for its calls finds only its declaration and `BrowserHomeRenderTest.browserChromeAndFailureStateRemainVisibleWhenWebViewCannotStart`. MainActivity Route.BROWSER selects HiddenBrowserHome for Hidden and BrowserV2ProductionDestination otherwise. It has no entry in the current production route graph, so this retained legacy risk is not an active-route release defect. The harness tests chrome/failure only, not the popup.
2. **New history control accessible naming — source remedied, Android pending:** parent changed HiddenBrowserHome to a labeled minimum-48dp toggleable Row with Role.Switch and a non-interactive inner Switch. Current native child adds an explicit history contentDescription and minimum-48dp Switch. The initially observed unlabeled sibling Switch/Text risk is therefore addressed in current source. Verify merged focus behavior/TalkBack on the final source; no executed accessibility result is claimed, and child sources remain WIP.
3. **Nested scroll boundaries:** External editor choices now use an ordinary Column inside A's default scroll body (no nested lazy viewport), and the dedicated chooser test targets this. Lazy collection/upload/source/history lists generally use scrollBody=false plus explicit bounded viewports; do not wrap their lazy body in another unbounded vertical scroll. Inspection did not establish a current remaining infinite-height lazy-scroll crash. This is source reasoning, not full runtime proof.
4. **Remaining layout candidates requiring direct tests:** AiSourceCards fixed title + bounded source grid; Create Image reference/grid footer; PhotoEditor enlarged-source fixed header/footer; TransferHistory's fixed message plus bounded list; legacy diagnostics action rows; long saved URLs in child library (now a bounded LazyColumn in WIP source); and native authentication fields under short windows/IME. They inherit theme but can still consume available height/width at extreme text sizes. Current restored prompt/upload/reference/transfer fixes address specific prior risks; they do not establish the whole viewport matrix.
5. **Decoration exceptions are explicit:** PhotoEditor enlarged-source Surface inherits selected Material3 colors but does not use G's outline/elevation. Enlarged result/video/fullscreen media intentionally use black media canvases with white controls. Hidden foundation/PrimaryCopy/Synthetic authentication use selected-theme fullscreen Surface. These are not evidence of unstyled white dialogs.

## Complete constructor mapping

Catalog references below expand to exact test class and method names. A family prefix such as T10 means its a/b/c catalog entries. Every U remains a release verification gap; shared-shell coverage cannot turn it into D.

| ID | Inventory source | Family / source styling | Direct test mapping and boundary |
|---|---|---|---|
| 01 | `MainActivity.kt:3233` | Album dropdown · M | D T34a actual production album selector, last of100 albums and selected label at cover/light/2x. |
| 02 | `MainActivity.kt:3436` | Encrypted backup restore · A | D T34b actual restore form: blank validation, real keyboard, busy disabled actions, authenticated-failure display and Cancel; no archive/credential callback invoked before explicit submission. |
| 03 | `MainActivity.kt:4008` | Recovery-key export verification · A | D T34c actual export form: empty-key rejection, masked entry/real keyboard and one explicit export boundary. |
| 04 | `MainActivity.kt:4028` | Third-party licences · A | D T34d actual long licence body reaches scroll maximum while Close stays displayed in short landscape/dark/2x. |
| 05 | `MainActivity.kt:4054` | Owner identity confirmation · A | D T34e actual owner form: masked real-keyboard entry, wrong PIN then correct PIN; biometric eligibility remains caller-owned. |
| 06 | `MainActivity.kt:4081` | Change PIN · A | D T34f actual Change PIN form validation, wrong-current callback error and Cancel at cover/light/2x; no credential store is mutated by this UI fixture. |
| 07 | `MainActivity.kt:4383` | Copy/move selection to Hidden confirmation · A | D T34g/h actual Copy and Move variants: source/destination explanation, Cancel before callback, explicit confirmation only; related encrypted integrity tests remain separate. |
| 08 | `MainActivity.kt:4394` | Transfer-to-Hidden progress · A | D T34i actual progress: body can scroll, safe-stop callback once, action disables while stopping and dialog stays present. |
| 09 | `MainActivity.kt:4401` | Bulk Recently Deleted confirmation · A | D T34j actual Recently Deleted variant: Cancel invokes no action, explicit Move invokes once with pinned choices. |
| 10 | `MainActivity.kt:4411` | Bulk Gallery restore confirmation · A | D T34k actual Gallery restore variant in short landscape/dark/2x: restriction explanation and explicit choice. |
| 11 | `MainActivity.kt:4421` | Permanent delete confirmation · A | D T34l actual permanent-delete variant: cancellation and distinct explicit confirmation, no storage mutations in fixture. |
| 12 | `MainActivity.kt:4454` | Collection cover picker · A | D T34m actual bounded cover list with current selection and last of300 items; actual production callback receives selected identity. |
| 13 | `MainActivity.kt:4614` | New collection · A | D T34n actual New collection field: blank rejection, real keyboard and explicit Create at cover/dark/2x. |
| 14 | `MainActivity.kt:4628` | Manage collection · A | D T32i: actual large-text management actions scroll; Cancel/Save stay pinned; delete requires separate confirmation. Rename/IME states still need broader coverage. |
| 15 | `MainActivity.kt:4643` | Add to collection picker · A | D T03b; list scrolling/selection in light theme. |
| 16 | `MainActivity.kt:4713` | Add Vault media picker · A | D T03a; 300-item list scrolling/selection in dark theme. |
| 17 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:238` | Hidden VPN settings · A | D T35 settings choices, save-history labels, desktop controls and long body scrolling in selected light/dark; live native Settings and library paths also covered. Runtime pending. |
| 18 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:247` | Hidden VPN tabs · A | D T35 actual native tab body, bounded long list, selection and close; live tab creation/selected-close/address restoration added. Runtime pending. |
| 19 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:248` | Hidden VPN bookmark/history library · A | D T21 (both libraries via actual child); maximal payload assertion, not full visual matrix; child WIP. |
| 20 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:249` | Hidden GET download confirmation · A | D T20 now includes explicit GET confirmation, encrypted import verification and decrypted fixture SHA-256; final child source remains WIP. |
| 21 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:250` | Hidden upload picker · A | D T20 real eligible/restricted transfer/upload/preview path; T35 renders the actual shared native grid at cover/dark and short-landscape/light2x with loading/disabled actions and item501 selection. Runtime pending. |
| 22 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:264` | Hidden upload consent · A | D T20 explicit Allow upload path; no contrary-theme/large-text matrix. |
| 23 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:274` | Hidden website HTTP authentication · A | D T35 actual username/password fields, Password/Next/Done IME options and explicit choices in light/dark; live HTTPS Basic-auth fixture added. Runtime pending. |
| 24 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:278` | Hidden form resubmission · A | D T35 actual resend warning and explicit Cancel/Resend; real native POST reload fixture added. Runtime pending. |
| 25 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:281` | Hidden website JS alert/confirm/prompt · A | D T35 actual website message/confirm/prompt body and input at2x; live iframe alert origin and explicit confirm-cancel/prompt-confirm callbacks added. Runtime pending. |
| 26 | `ui/AiEditingSettings.kt:145` | Prompt template editor · G | D T02 narrow 320×640/2x footer and scrolling; T32h adds actual keyboard-window visibility and unsaved Cancel. Contrary OS-theme matrix U. |
| 27 | `ui/AiEditingSettings.kt:203` | Provider credential setup · G | D T07 provider entry/remove/failure/background; visual/IME matrix U. |
| 28 | `ui/AiModelPicker.kt:46` | AI model picker sheet · M | D T08 model/provider sheet interaction; complete theme/IME matrix U. |
| 29 | `ui/AiSourceCards.kt:93` | AI source-photo picker · A | D T09 source replacement and stale-load handling; T32b adds real short landscape/2x lazy-grid final-item selection. Complete matrix remains U. |
| 30 | `ui/BrowserPanels.kt:45` | Browser full-screen panel shell · S (selected-theme background, safeDrawing/IME insets) | D T10/T11 panel/settings/library interactions; shell theme sourced; complete IME/focus matrix U. |
| 31 | `ui/BrowserPanels.kt:176` | Clear Primary history confirmation · A | D T10c explicit history-clear confirmation; large-text/error states U. |
| 32 | `ui/BrowserPanels.kt:244` | Clear Primary browsing-data confirmation · A | D T32f actual browsing-data confirmation at cover/2x; Cancel invokes no cleanup, explicit Clear invokes once. |
| 33 | `ui/BrowserV2Home.kt:499` | Browser internal media viewer · S | D T36 actual production modal component at cover/short landscape2x selected light/dark; explicit Cancel/confirm/back and long-content/permission/resource states. See browser-modal-completion.md for exact case mapping. Runtime pending. |
| 34 | `ui/BrowserV2Home.kt:719` | Save browser video · A | D T36 actual production modal component at cover/short landscape2x selected light/dark; explicit Cancel/confirm/back and long-content/permission/resource states. See browser-modal-completion.md for exact case mapping. Runtime pending. |
| 35 | `ui/BrowserV2Home.kt:812` | Upload source choice · A | D T04a upload choice/extension path; all theme/viewport states U. |
| 36 | `ui/BrowserV2Home.kt:821` | Primary Vault upload picker · G | D T04a/T04d/T12; actual picker/reopen/preview; 2x/landscape grid matrix U. |
| 37 | `ui/BrowserV2Home.kt:859` | Decrypted upload confirmation · A | D T04a explicit upload confirmation; cancel race T04b; visual matrix U. |
| 38 | `ui/BrowserV2Home.kt:889` | Find in page · A | D T36 actual production modal component at cover/short landscape2x selected light/dark; explicit Cancel/confirm/back and long-content/permission/resource states. See browser-modal-completion.md for exact case mapping. Runtime pending. |
| 39 | `ui/BrowserV2Home.kt:896` | Primary browser diagnostics · A | D T36 actual production modal component at cover/short landscape2x selected light/dark; explicit Cancel/confirm/back and long-content/permission/resource states. See browser-modal-completion.md for exact case mapping. Runtime pending. |
| 40 | `ui/BrowserV2Home.kt:929` | Website JS/leave-page dialog · A | D T04c/T04e; T05 related once-only/revocation policy. |
| 41 | `ui/BrowserV2Home.kt:942` | Browser message · A | D T36 actual production modal component at cover/short landscape2x selected light/dark; explicit Cancel/confirm/back and long-content/permission/resource states. See browser-modal-completion.md for exact case mapping. Runtime pending. |
| 42 | `ui/BrowserV2Home.kt:943` | Website camera/microphone permission · A | D T36 actual production modal component at cover/short landscape2x selected light/dark; explicit Cancel/confirm/back and long-content/permission/resource states. See browser-modal-completion.md for exact case mapping. Runtime pending. |
| 43 | `ui/BrowserV2Home.kt:961` | Website geolocation permission · A | D T36 actual production modal component at cover/short landscape2x selected light/dark; explicit Cancel/confirm/back and long-content/permission/resource states. See browser-modal-completion.md for exact case mapping. Runtime pending. |
| 44 | `ui/BrowserV2Home.kt:972` | Save image resource · A | D T36 actual production modal component at cover/short landscape2x selected light/dark; explicit Cancel/confirm/back and long-content/permission/resource states. See browser-modal-completion.md for exact case mapping. Runtime pending. |
| 45 | `ui/BrowserV2Home.kt:984` | Screenshot fallback consent · A | D T36 actual production modal component at cover/short landscape2x selected light/dark; explicit Cancel/confirm/back and long-content/permission/resource states. See browser-modal-completion.md for exact case mapping. Runtime pending. |
| 46 | `ui/BrowserV2Home.kt:996` | Open external app consent · A | D T36 actual production modal component at cover/short landscape2x selected light/dark; explicit Cancel/confirm/back and long-content/permission/resource states. See browser-modal-completion.md for exact case mapping. Runtime pending. |
| 47 | `ui/CreateImageSheet.kt:90` | Create Image sheet · M | D T32g actual cover/2x sheet controls and all aspect choices; T13 cancellation. Complete IME matrix U. |
| 48 | `ui/CreateImageSheet.kt:166` | Create Image remote consent · A | D T32g actual remote-create consent Cancel before generator invocation; T14 remains a separate edit-consent path. |
| 49 | `ui/CreateImageSheet.kt:171` | Create Image diagnostics · A | D T32g actual original/effective prompt diagnostic; unusually long/error diagnostics remain U. |
| 50 | `ui/CreateImageSheet.kt:178` | Create Image reference grid · G | D T32g actual 300-item reference grid, final-item selection and Done at cover/2x. No provider is called. |
| 51 | `ui/ExternalEditingDialog.kt:44` | External editing status/handoff · A | D T15a/T15b entry/prepare/import states; full theme/IME/large-text matrix U. |
| 52 | `ui/ExternalEditingDialog.kt:72` | External editor choice · A | D T16 multi-editor choice without nested-scroll crash. |
| 53 | `ui/ExternalEditingDialog.kt:76` | External editing plaintext-egress consent · A | D T15b explicit editor preparation; all visual states U. |
| 54 | `ui/ExternalEditingDialog.kt:80` | Public-copy/export consent · A | D T33 actual extracted production component, or existing production sort button, in selected light/dark at 2x with narrow/short-landscape windows, long-data scroll, explicit actions and 48dp checks. Android execution pending; engine/physical acceptance not inferred. |
| 55 | `ui/ExternalEditingDialog.kt:84` | Replace encrypted original confirmation · A | D T15c explicit verified replacement confirmation; failure/large text U. |
| 56 | `ui/FullscreenMediaViewer.kt:322` | Viewer Recently Deleted confirmation · A | D T33 actual extracted production component, or existing production sort button, in selected light/dark at 2x with narrow/short-landscape windows, long-data scroll, explicit actions and 48dp checks. Android execution pending; engine/physical acceptance not inferred. |
| 57 | `ui/FullscreenMediaViewer.kt:789` | Video opening error · A | D T33 actual extracted production component, or existing production sort button, in selected light/dark at 2x with narrow/short-landscape windows, long-data scroll, explicit actions and 48dp checks. Android execution pending; engine/physical acceptance not inferred. |
| 58 | `ui/GalleryAlertDialog.kt:32` | Shared alert implementation · A | D T01 shared shell only; does not upgrade unverified call sites to verified. |
| 59 | `ui/HiddenBrowserHome.kt:139` | Hidden browser settings · A | D T17 settings/tabs at four synthetic viewports; disconnected combined-mode fixture; connected controls/IME semantics U. |
| 60 | `ui/HiddenBrowserHome.kt:171` | Hidden bookmark/history library · A | D T35 shared actual HiddenNativeLibraryContent now used by both Main handoff shell and native child: empty-state plus long lazy list, selection and bookmarks removal; encrypted persistence remains T18. Runtime pending. |
| 61 | `ui/HiddenBrowserHome.kt:187` | Hidden tab overview · A | D T17 empty/disconnected overview only; populated long list/focus U. |
| 62 | `ui/HiddenFoundationDialog.kt:129` | Hidden foundation authentication · S | D T19 setup/background; shared keypad T23; whole visual matrix U. |
| 63 | `ui/HiddenRestoreDialog.kt:42` | Hidden restore destination/authentication/progress · G | D T22 + T24 destination choices, wrong/correct/cancel/lock/expiry and move confirmation; 2x/IME non-auth form U. |
| 64 | `ui/HiddenVaultDialog.kt:274` | Hidden viewer collection chooser · A | D T33 actual extracted production component, or existing production sort button, in selected light/dark at 2x with narrow/short-landscape windows, long-data scroll, explicit actions and 48dp checks. Android execution pending; engine/physical acceptance not inferred. |
| 65 | `ui/MediaChrome.kt:68` | Shared media More sheet · M | D T25a actual viewer More sheet; all options/large-text/landscape U. |
| 66 | `ui/MediaChrome.kt:175` | Media sort dropdown · M | D T33 actual extracted production component, or existing production sort button, in selected light/dark at 2x with narrow/short-landscape windows, long-data scroll, explicit actions and 48dp checks. Android execution pending; engine/physical acceptance not inferred. |
| 67 | `ui/MediaDetailsDialog.kt:124` | App-owned date input stage · A, M3 DatePicker Input mode | D T26a migrated actual-window screenshot policy/exact selection/cancel; T32k/l narrow/short large-text and DST preservation. Android execution pending. |
| 68 | `ui/MediaDetailsDialog.kt:125` | App-owned numeric time stage · A | D T26a migrated actual-window policy/metadata-only round trip; T32k range validation/IME Done once, T32l invalid DST rejection. Android execution pending. |
| 69 | `ui/MediaDetailsDialog.kt:135` | Media details · A | D T26a/T26b details/date path; all long metadata/large-text/IME U. |
| 70 | `ui/MediaSelectionDialog.kt:39` | Select by date/size · A | D T27 date/size selection scope; T32j cover/2x preset geometry, all choices and last-preset result. Date-form IME matrix U. |
| 71 | `ui/PhotoEditor.kt:548` | Enlarged source preview · S | D T09 source enlargement flow; no full-height 2x/landscape guarantee. |
| 72 | `ui/PhotoEditor.kt:560` | Enlarged AI result · S | D T09 result preview/discard flow; intentional black media canvas. |
| 73 | `ui/PhotoEditor.kt:574` | Prompt diagnostics · A | D T33 actual extracted production component, or existing production sort button, in selected light/dark at 2x with narrow/short-landscape windows, long-data scroll, explicit actions and 48dp checks. Android execution pending; engine/physical acceptance not inferred. |
| 74 | `ui/PhotoEditor.kt:582` | Prediction diagnostics · A | D T33 actual extracted production component, or existing production sort button, in selected light/dark at 2x with narrow/short-landscape windows, long-data scroll, explicit actions and 48dp checks. Android execution pending; engine/physical acceptance not inferred. |
| 75 | `ui/PhotoEditor.kt:589` | AI operation progress · A | D T33 actual extracted production component, or existing production sort button, in selected light/dark at 2x with narrow/short-landscape windows, long-data scroll, explicit actions and 48dp checks. Android execution pending; engine/physical acceptance not inferred. |
| 76 | `ui/PhotoEditor.kt:601` | Saving-stage progress · A | D T33 actual extracted production component, or existing production sort button, in selected light/dark at 2x with narrow/short-landscape windows, long-data scroll, explicit actions and 48dp checks. Android execution pending; engine/physical acceptance not inferred. |
| 77 | `ui/PhotoEditor.kt:605` | Remote AI consent · A | D T14a consent-before-edit flow; large-text/theme matrix U. |
| 78 | `ui/PhotoEditor.kt:610` | Discard edits · A | D T09 discard result flow; all exit/discard states U. |
| 79 | `ui/PrimaryCopyDialog.kt:50` | Primary-to-Hidden copy/authentication picker · S | D T28 wrong→correct PIN/source/copy plus T23 keypad; lock/expiry/full picker large-text U. |
| 80 | `ui/PrivacyShareDialog.kt:112` | Privacy share review · A | D T29 dark scroll/cancel without media read; light/IME/landscape U. |
| 81 | `ui/PrivacyShieldSettings.kt:56` | Panic Exit test confirmation · A | D T32c actual Panic Exit test confirmation Cancel without settings changes; T30 remains settings/auth related. This does not assert an actual Main lock. |
| 82 | `ui/PrivateBrowser.kt:523` | Legacy browser dropdown · M | U legacy; retained constructor is themed M3, current production routes do not call legacy BrowserHome (test harness only). |
| 83 | `ui/PrivateBrowser.kt:617` | Legacy download consent · A | U legacy; direct download-consent layout not mapped. |
| 84 | `ui/PrivateBrowser.kt:627` | Legacy image/screenshot consent · A | U legacy; direct image/screenshot-consent layout not mapped. |
| 85 | `ui/PrivateBrowser.kt:641` | Legacy bookmarks · A | U legacy; T10b targets new BrowserBookmarksPanel, not this dialog. |
| 86 | `ui/PrivateBrowser.kt:663` | Legacy diagnostics · A | U legacy; no direct debug-console modal layout mapping. |
| 87 | `ui/PrivateVideoPlayer.kt:235` | Video playback-speed menu · M | D T33 actual extracted production component, or existing production sort button, in selected light/dark at 2x with narrow/short-landscape windows, long-data scroll, explicit actions and 48dp checks. Android execution pending; engine/physical acceptance not inferred. |
| 88 | `ui/RetiredModelCleanup.kt:27` | Retired model cleanup · A | D T32d actual confirmation, Cancel then explicit cleanup in isolated synthetic storage; unrelated vault sentinel retained. |
| 89 | `ui/SyntheticHiddenGalleryDialog.kt:58` | Synthetic Hidden gallery harness · S | D T31 synthetic fixture import/view/reset/background; not real-vault acceptance. |
| 90 | `ui/TransferHistoryDialog.kt:33` | Encrypted transfer history · A | D T32e actual encrypted journal with 30 operations, lazy scroll, pending-operation cancel and retained-media sentinel; retry execution remains separate security coverage. |
| 91 | `ui/VaultCameraScreen.kt:404` | Camera recording details · A | D T33 actual extracted production component, or existing production sort button, in selected light/dark at 2x with narrow/short-landscape windows, long-data scroll, explicit actions and 48dp checks. Android execution pending; engine/physical acceptance not inferred. |
| 92 | `ui/VaultStoragePanel.kt:29` | Vault storage summary · A | D T32a actual long summary, last explanation scrolling and pinned Close at cover/2x. |
| 93 | `ui/VaultStoragePanel.kt:52` | Clear disposable preview cache · A | D T32a actual cache confirmation Cancel/no callback then explicit Clear/callback once. |

## Exact runtime-test source catalog

Class names are relative to `uk.co.traynor.privategallery`; all files are under `app/src/androidTest/java`. Method existence was checked against current source. These are test definitions, not executed results. T20/T21's live cases need the opted-in owned-VPN fixture and remain unexecuted here.

| Ref | Test class | Exact method(s) |
|---|---|---|
| T01 | `ui.GalleryDialog1762Test` | `foldCoverDark`, `foldCoverLight`, `foldInnerDark`, `foldLandscapeLight` |
| T02 | `ui.PromptTemplateLayout1762Test` | `narrowLargeTextKeepsActionsReachable` |
| T03a | `CollectionDialogs1762Test` | `largeMediaListCanScrollAndSelectInDarkTheme` |
| T03b | `CollectionDialogs1762Test` | `largeCollectionListCanChooseLastInLightTheme` |
| T04a | `ui.BrowserConsent1752Test` | `extensionBasedUploadUsesThemedVaultPickerWithoutPrematureEgress` |
| T04b | `ui.BrowserConsent1752Test` | `cancelledPreparationCannotClearNewerChooserRequest` |
| T04c | `ui.BrowserConsent1752Test` | `websiteDialogsUseThemedShellAndRevokeOnBackground` |
| T04d | `ui.BrowserConsent1752Test` | `movedEligiblePhotoAppearsWhenPickerReopensButRestrictedPhotoDoesNot` |
| T04e | `ui.BrowserConsent1752Test` | `realWebsiteAlertAndBeforeUnloadRequireExplicitChoice` |
| T05 | `core.browser.v2.BrowserScriptDialogs1762Test` | `backgroundAndLateReplyCannotApproveOldWebsiteDialog`, `networkRevocationAndInactiveTabRequestsFailClosed`, `explicitReplyIsOnceOnlyAndCloseCancels` |
| T07 | `ui.AiProviderSetupTest` | `ownerCanOpenProviderSetupWithoutAConfiguredCredential`, `verifiedTokenIsNeverPrefilledAndRemovalDisablesProvider`, `failedTokenIsNotSavedAndRetryRemainsAvailable`, `backgroundingCancelsVerificationAndDiscardsEntry` |
| T08 | `ui.AiModelPickerTest` | `compactSelectionOpensSheetAndUpdatesWithoutHorizontalClipping`, `providerUsesCompactSelectionAndBottomSheet` |
| T09 | `ui.AiEditorProfessionalTest` | `bothSourcesCanBeReplacedWithoutChangingExactPromptAndResultCanBeDiscarded`, `lateAuthenticatedPickerLoadsAreWipedAfterBackgroundAndCannotRestorePreviews`, `sourceCardsAndActionsRemainReachableAcrossFoldViewports`, `promptAndGenerateRemainReachableWithKeyboardOpen` |
| T10a | `ui.BrowserPolishTest` | `browserSettingsOpenLocallyAndReturnToSamePageAndView` |
| T10b | `ui.BrowserPolishTest` | `bookmarksFilterAndRemoveWithoutOpeningAnotherPage` |
| T10c | `ui.BrowserPolishTest` | `historyClearNeedsConfirmationAndRetainsDatesAndTitles` |
| T11 | `ui.BrowserProfessionalUiTest` | `coverLight`, `innerDark`, `coverLandscapeLargeText`, `innerLandscapeLight` |
| T12 | `ui.BrowserUploadThumbnail1762Test` | `closeAndReopenDoesNotRecycleSharedThumbnail`, `latePreviewDoesNotRecycleBorrowedCacheEntry` |
| T13 | `ui.CreateImageCancellationTest` | `lateCancelledGenerationCannotPublishOrReplaceNewRequestState` |
| T14a | `ui.AiEditorFlowTest` | `consentPrecedesRemoteEditAndResultWaitsForSaveCopy` |
| T14b | `ui.AiEditorFlowTest` | `backgroundLockBoundaryCancelsFutureOnDeviceProviderAndWipesInput` |
| T15a | `ui.ExternalEditingUi176Test` | `compactEntryKeepsHandoffBehindExplicitConsentAndCanCancel` |
| T15b | `ui.ExternalEditingUi176Test` | `confirmedEditorChoicePreparesOnceAndImportIsExplicit` |
| T15c | `ui.ExternalEditingUi176Test` | `replaceRequiresExplicitConfirmationAfterPreview` |
| T16 | `ui.ExternalEditorChooser1762Test` | `multipleEditorsRenderAndChooseWithoutNestedScrollCrash` |
| T17 | `ui.HiddenBrowser17LayoutTest` | `foldCoverLight`, `foldInnerDark`, `foldCoverLandscapeDark`, `foldInnerLandscapeLight` |
| T18 | `core.browser.hidden.HiddenSessionLibrary1762Test` | `encryptedLibrarySurvivesReauthenticationAndKeepsModesIndependent` |
| T19 | `ui.HiddenFoundationDialogTest` | `setupOwnPinUnlocksAndHiddenLockDoesNotReplacePrimaryCredentials`, `backgroundClearsUnsubmittedPinWithoutCredentialCreation` |
| T20 | `core.browser.hidden.HiddenVpnLive1762Test` | `nativeChildPostsFormsUploadsAndRevokesOnOwnedVpn` |
| T21 | `core.browser.hidden.HiddenLibraryParcelPressure1762Test` | `maximalResponseFitsParcelAndAuthenticationFailuresPreserveCiphertext`, `maximalLibraryTraversesActualClaimedChildBinderWithoutTransactionTooLarge` |
| T22 | `ui.HiddenRestoreAuthentication1762Test` | `wrongThenCorrectKeypadPinCopiesVerifiedMediaAndKeepsHiddenOriginal`, `correctKeypadPinMovesOnlyAfterSeparateConfirmation`, `cancellationAtKeypadDoesNotPublishOrDelete`, `lockWhileEnteringPrimaryPinRevokesWithoutPublishing`, `expiredHiddenGraceRevokesPrimaryAuthenticationDialog` |
| T23 | `ui.PasscodeKeypadTest` | `digitsAreMaskedBackspaceWorksAndVariablePinRequiresConfirmation`, `keypadControlsFitFoldCoverInnerAndLandscapeViewports`, `knownFixedLengthSubmitsOnceAtBoundary` |
| T24 | `ui.HiddenRestoreDialogTest` | `choicesAreExplicitAndPrimaryRequiresFreshPinWithoutChangingHidden`, `moveRequiresSeparateConfirmationAndLockRevokesDialogOnce` |
| T25a | `ui.PhotoEditorUiTest` | `chromeHidesTogetherAndMoreUsesSheet` |
| T26a | `ui.MediaDetails174Test` | `nativeDateAndTimePickersFollowOwnerScreenshotPolicy` |
| T26b | `ui.MediaDetails174Test` | `restoreOriginalDateRefreshesDetailsWithoutChangingMediaHash` |
| T27 | `ui.MediaSelection176Test` | `dateSelectionIncludesBothLocalBoundariesAndNeverAddsOutsideScope`, `sizeSelectionUsesMetadataAndStrictPresetBoundary` |
| T28 | `ui.PrimaryCopyDialogTest` | `sourceChoiceFreshPinSelectionCopyAndReturnStayInHidden` |
| T29 | `ui.PrivacyShareDialog1762Test` | `darkShareOptionsScrollAndCancelWithoutReadingMedia` |
| T30 | `ui.SettingsPolish1761Test` | `panicConfigurationIsAbsentFromSecretWhileHideContentAccessRemains`, `primaryScreenshotEnableStillRequiresFreshAuthenticationInSecurity` |
| T31 | `ui.SyntheticHiddenGalleryTest` | `syntheticPhotoImportViewingFavouritesCollectionsAndReset`, `backgroundClosesAndReopeningHasNoDisposableState` |
| T33 | `ui.RemainingMediaModals1762Test` | `narrowDarkTwoTimesText`, `narrowLightTwoTimesText`, `shortLandscapeDarkTwoTimesText`, `shortLandscapeLightTwoTimesText` |


## Completion-stage component definitions (all Android execution pending)

The `ui.ModalCompletion1762Test` class adds these real component tests. T32 labels are additions to the catalog, not passing-run claims:

| Ref | Exact method | Actual boundary |
|---|---|---|
| T32a | `coverDarkStorageLongContentAndCacheConsentRemainReachable` | VaultStoragePanel long data + cache confirmation; no cache work on Cancel. |
| T32b | `landscapeDarkSourcePickerCanReachLastOfLargeVaultAtLargeText` | AiSourcePicker 300-item lazy grid; encrypted-loader interface with synthetic PNG copies. |
| T32c | `coverLightPanicTestConfirmationCancelsWithoutChangingSettings` | Actual confirmation Cancel; no preference mutation; no Main lock claim. |
| T32d | `landscapeLightRetiredModelConsentCancelsBeforeIsolatedCleanup` | Actual cleanup consent with isolated retired-model file and retained sentinel. |
| T32e | `innerDarkEncryptedTransferHistoryScrollsAndCancelsOnlyRecordedOperation` | Actual encrypted journal/component, 30 operations and one pending cancellation. |
| T32f | `coverLightBrowsingDataConfirmationRequiresExplicitChoiceAtLargeText` | Shared browser settings confirmation, cancel then one explicit callback. |
| T32g | `coverDarkCreateImageChoicesReferencesDiagnosticsAndConsentFitAtLargeText` | All aspect chips, last reference, prompt diagnostics and remote consent; zero generator calls. |
| T32h | `coverDarkPromptEditorKeepsActionsVisibleWithActualKeyboard` | Real IME window observed, pinned actions and unsaved cancellation; keyboard setting restored. |
| T32i | `coverLightCollectionActionsScrollWhileFooterStaysReachable` | Actual manager tertiary actions/confirmation; no deletion on Cancel. |
| T32j | `coverDarkSizeSelectionPresetsWrapAndKeepLastChoiceUsable` | Preset widths/selection at2x; strict final scope result. |
| T32k | `coverDarkDateTimeFieldsScaleAndKeyboardDoneCommitsOnce` | Date/time stages, invalid hour rejection and single keyboard-Done submission. |
| T32l | `landscapeLightDstGapCannotChangeOriginalMetadataOrMediaHash` | Actual MediaDetails route rejects Europe/London spring DST gap before any write. |

Production remedies from this stage:

- Create Image aspect chips and size-selection presets now wrap; increased text cannot squeeze later actions into the remaining width of a fixed row. Size fields request the numeric keyboard.
- Collection management tertiary actions move into the existing scrolling body; only Cancel and Save/confirmed Delete remain pinned (parent-owned MainActivity change).
- App-created DatePickerDialog/TimePickerDialog no longer inherit platform-default grey surfaces/corners. Both stages use GalleryAlertDialog. The M3 date input encodes selected calendar dates in UTC before applying the original zone; the bounded numeric time form uses scalable app typography, valid24-hour ranges and Next/Done focus handling. Final DisplayDateTime resolution still rejects ambiguous/missing DST times before the original metadata callback. Cancellation at either stage performs no update. The owner window's screenshot flag is explicitly applied to each app-owned dialog.
- `MediaDetails174Test.nativeDateAndTimePickersFollowOwnerScreenshotPolicy` keeps its historical evidence method name, but now drives the actual Compose stages and checks the actual focused dialog WindowManager flags. Its exact2024-06-06 13:42 round trip, independent cancellation at each stage, unchanged capture timestamp and unchanged media hash assertions remain. No security assertion is waived because the widget changed.

The original93 source entries have each been inspected against their A/G/M/S implementation family. Repeated alert aliases inherit the shared palette/shape/type/scroll/action contract; bounded lazy bodies were checked for scrollBody opt-out. Fullscreen authentication/media panels intentionally use full-window surfaces/canvases, with black playback canvases preserving white media controls. This source review does **not** verify actual measured layout/focus in each state. Remaining U entries in the table still require direct-state runtime evidence; no broad completion, Fold acceptance or final source approval follows from shared-shell coverage.

Rows 54/56/57/64/73–76/91 now call actual internal production components from `ui/MediaOperationDialogs.kt`; row 87 uses its extracted production speed menu, and row 66 retains MediaSortButton with explicit minimum targets. T33 mounts those exact components, not a duplicated shell. It covers public export/open variants; trash confirmation; retryable long video error; 300-item/empty Hidden collection lists; long prompt/prediction; cancellable AI operation/save and saved state; recording-details verification/busy state; sort/speed selection. UI callbacks remain owned by original call sites; no media/camera/AI/auth work is initialized by the fixture. Its screenshots are intended runtime evidence, not already captured results.


## Main application surface completion definitions (Android pending)

The actual Main call sites now delegate their UI to internal components, keeping the existing owner/authentication/storage/transfer callbacks in the original callers. This is testability extraction, not a second mirrored widget implementation. Backup forms retain request-URI-keyed, memory-only state and clear submitted strings; numeric PIN fields now request NumberPassword. Cover selection is a bounded lazy list with an explicit current radio state. Licence text uses a maximum-height scrollable body rather than forcing360dp into a short window.

All entries below are in `MainModalInventory1762Test` and use2x app text. Fixture callbacks accept synthetic strings only and wipe received CharArrays; no credential, backup archive or vault media store is changed. Form cases explicitly observe an actual IME window and restore the keyboard setting/service flags afterward.

| Ref | Exact method |
|---|---|
| T34a | `coverLightAlbumMenuScrollsAndSelectsBeyondFirstViewport` |
| T34b | `coverDarkBackupRestoreValidatesThenDisablesActionsUntilCompletion` |
| T34c | `coverLightBackupExportRequiresEnteredRecoveryKeyAndExplicitExport` |
| T34d | `landscapeDarkLicencesLongContentScrollsWhileCloseStaysVisible` |
| T34e | `coverDarkOwnerConfirmationMasksInputAndRejectsWrongPinBeforeCorrectPin` |
| T34f | `coverLightChangePinValidatesMismatchAndPreservesCallbackBoundary` |
| T34g | `coverDarkCopyConfirmationRequiresExplicitAcceptance` |
| T34h | `landscapeLightMoveConfirmationRequiresExplicitAcceptance` |
| T34i | `coverDarkTransferProgressCanRequestSafeStopOnceWithoutDismissing` |
| T34j | `coverLightRecentlyDeletedConfirmationKeepsCancelAndMoveReachable` |
| T34k | `landscapeDarkRestoreConfirmationKeepsRestrictionExplanationAndChoice` |
| T34l | `coverDarkPermanentDeleteRequiresSeparateExplicitChoice` |
| T34m | `coverLightCollectionCoverShowsSelectionAndReachesLastOfLargeList` |
| T34n | `coverDarkNewCollectionUsesActualFieldAndExplicitCreateWithKeyboard` |

These definitions close the prior wholly-unmapped Main surface IDs01–13 at the component-definition level. Run their exact final source before claiming measured verification. Existing encrypted transfer, backup/credential and owner lifetime suites remain required for the corresponding storage/security behavior; these layout fixtures do not replace them. No physical acceptance is inferred.

## Overlay families beyond constructors

- **Privacy covers, secondary windows, panic shield and accessibility focus concealment:** MainActivity lifecycle/security path. `HiddenVpnHandoff1762Test.childHomeUsesIndependentHiddenGraceAndImmediatePrimaryPolicy`, `childScreenOffLocksBothEvenWhenMainIsAlreadyStopped`, `delayedLossAfterForegroundExpiresOnlyTheMatchingIndependentAuthentication`, `lateMainStartConsumesReturnBeforeOverdueTimerCanBeCancelled` cover synthetic handoff/lifecycle behavior. They are not physical recents/fold/window-stack/TalkBack screenshot proof. Lifecycle sources are WIP.
- **Fullscreen independent authentication:** T19/T22/T23/T24/T28 map Hidden setup and cross-vault PIN paths. Primary setup/change/recovery and numeric form IME/keyboard traversal still need direct actual-page coverage rather than inferring it from keypad geometry.
- **Media/editor/camera fullscreen canvases and controls:** T09/T25 and `PhotoEditorUiTest.foldSizedCanvasesKeepSaveAndToolsReachable`, `PhotoEditorUiTest.backgroundWipesLoadedSourceBeforeResume`; `VaultCameraUiTest.blackPhotoTapsVolumeAndNavigationGestures`, `readingPhotoTapsVolumeAndNavigationGestures`, `activityForegroundLossLocksBothVaultsAndNeverReopensHiddenCamera` are related active component tests. Recording-details actual modal is now mapped to T33, pending execution. Black media backgrounds are intentional.
- **Primary browser full-screen panels:** T10/T11 cover current panels and adaptive chrome. Their tests do not establish all nested confirmation/find/permissions/diagnostic states.
- **Legacy temporary popup WebView:** source risk described above; absent from constructor inventory and unreachable through current production routes. A test-only BrowserHome harness exists; popup close/back/keyboard restoration remains unverified for retained legacy code.
- **New native Tor child and transport:** currently shares/churns native child sources with VPN. Existing HiddenBrowserHome disconnected combined-mode layout fixture does not verify the eventual connected native Tor settings/tabs/library/upload/website-dialog UI. Carry rows 17–25 and relevant library settings into the final native-Tor matrix; mark WIP until final source and execution artifacts exist.
- **System UI:** Android permissions, biometrics, VPN consent, photo picker, SAF and external editors are externally owned. App egress/permission explanation dialogs remain covered by this inventory; external OEM appearance must not be counted as app wrapper verification.

## Required acceptance evidence

Freeze final source, run mapped component tests and add real-state tests for U entries, then inspect artifacts across cover/inner × portrait/landscape × selected light/dark (including contrary OS theme), 1.6x/2x and system maximum font scale. Show IME for every text-entry/password/search/API/prompt/date form; verify readable last content, nonzero useful grid/list viewport, reachable cancel/confirm, long errors/loading/disabled/busy states, focus order, keyboard Enter/Escape/Back and TalkBack labels. Test dismissal/revocation on background, lock, route loss and mode switch. Physical Fold transitions, Samsung keyboard/native picker/editor and real screen-reader acceptance remain owner/device verification. This map is explicit coverage accounting, not a claim of complete modal acceptance.

## Native and Primary browser completion case catalogs (execution pending)

T35 is `ui.HiddenNativeBrowserDialogs1762Test`, all22 current named methods (expanded by parity completion): actual production auth, website message/prompt, settings, bounded tabs/library, upload intro/consent and resubmit bodies. Source list and viewport/action mappings are in `native-child-modal-inventory.md`. Native live protocol cases separately verify callbacks and origin/authority fencing. A direct body test does not establish real network behavior.

T36 is `ui.BrowserModalSurfaces1762Test`, all10 named methods, exact case mappings in `browser-modal-completion.md`. BrowserV2Home calls the tested components for internal viewer, media-save progress/consent, Find, diagnostics, arbitrary message, camera/microphone, location, image resource, screenshot fallback and external-app consent. Owner callbacks, permission and egress policy remain in the original session.

## Focused execution selection for existing mapped UI cases

The focused runner now explicitly selects the 27 existing modal interaction methods in T07–T11, T13–T14, T19, T25a, T27 and T31. Each exact class/method pair is required by `scripts/verify_focused1762.py`; a different passing method in the same class cannot satisfy the gate. Existing whole-class selectors are retained, and these additions do not select unrelated AI/media engine suites. Method names were checked against their annotated source declarations. This is selection/gate coverage only: Android execution and physical Fold acceptance remain pending.

## Restored-scope parity/recovery variants added after2781

These rows extend the preserved original93 inventory; they never replace an original requirement. Android execution of this new source is pending.

| Variant | Actual shared production surface | Direct selected coverage |
|---|---|---|
| R94 External editing recovery acknowledgement | ExternalEditingDialog / GalleryAlertDialog; distinct Keep receipt and Acknowledge and forget; public/cache/cloud limits | ExternalEditingUi176Test.forgetRecoveryReceiptAtDoubleFontRequiresExplicitAcknowledgement; ExternalEditorRecovery1762Test persistent receipt/reopen/recreation cases |
| R95 Native tabs/settings/bookmarks/history panels | BrowserTabsPanel / BrowserPanel / BrowserBookmarksPanel / BrowserHistoryPanel, shared with Primary; selected card, bounded scrolling/search/keyboard/clear confirmation | HiddenNativeBrowserDialogs1762Test current complete class; BrowserPolishTest; BrowserProfessionalUiTest; HiddenLibraryParcelPressure1762Test actual child Binder |
| R96 Native Find and download status/return | Actual production `HiddenFindBar` and `HiddenDownloadsDialog`; bounded result scroll, keyboard Done, Previous/Next/Close; downloads explicit return/cancel-transfer explanation | `HiddenNativeBrowserDialogs1762Test.findLightCoverKeyboardAndActions`, `findDarkLandscapeKeyboardAndActions`, `downloadsLightCoverLongStatusAndExplicitActions`, `downloadsDarkLandscapeLongStatusAndExplicitActions` (2x font, actual production components); `HiddenVpnLive1762Test` actual WebView Find; `HiddenVpnHandoff1762Test` parent foreground/auth/generation |
| R97 Browser failure overlay | BrowserPageError shared Primary/native, selected theme, scroll/retry/settings; TLS errors still cancelled | HiddenNativeBrowserDialogs1762Test.sharedPageFailureOffersRealRetryAndSettingsCallbacks; HiddenVpnLive1762Test real HTTP failure/retry |

Current constructor reconciliation was regenerated from actual source (89 sites). Reuse removes obsolete native wrapper constructors while adding variants, so equal counts do not prove equal scope. The complete current test classes and exact-source screenshots must pass; earlier85/88 does not approve these new bytes. Physical Fold/OEM/keyboard/assistive acceptance remains separate.
