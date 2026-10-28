# Modal verification map — restored scope

Read-only source audit on 28 October 2026. This maps **all 93 rows** of `docs/release1762/MODAL_INVENTORY_CURRENT.md` in original order. Its stored line numbers are identifiers, not assertions that current lines have not moved. Main lifecycle and native Hidden transport/child sources are changing concurrently; child mappings are provisional WIP and require rerun against the final frozen SHA. No production or test changes were made by this audit.

**D** means a real component/path is exercised by an identified test method in source; it does **not** mean that method passed on the current source. **R** means related behavior/policy only. **U** means direct component verification is unestablished. No current Android execution artifacts were inspected here; no new passing run, screenshot review, physical device, keyboard/OEM, TalkBack or fold-transition proof is claimed. Historical 117-case evidence belongs to the earlier frozen source and cannot verify new child/modal work.

## Shared shells and findings

`A` = GalleryAlertDialog alias: selected Material3 surfaceContainerHigh/onSurface/onSurfaceVariant, outline, shape and tonal elevation; default body scrolling and pinned actions. `G` = raw Compose Dialog containing GalleryDialogSurface with matching selected-theme decoration. `M` = Material3 menu/sheet inheriting the selected theme. `S` = custom Material3 Surface/media canvas. `N` = app-created native date/time picker with explicit selected-theme style. Every alert importer in this inventory routes to A, except the underlying Material3 constructor in A itself. No Android AlertDialog.Builder or PopupWindow family was found in production search; a constructor count alone is not sufficient acceptance.

1. **Retained legacy-only popup close-action contrast/insets:** `ui/PrivateBrowser.kt` temporary popup WebView overlay (outside the 93 constructor rows) still places a bare IconButton over website content with no opaque theme backing and no safeDrawing inset. This is a concrete source contrast/inset risk. The composable is actually named `BrowserHome`; a complete `rg` for its calls finds only its declaration and `BrowserHomeRenderTest.browserChromeAndFailureStateRemainVisibleWhenWebViewCannotStart`. MainActivity Route.BROWSER selects HiddenBrowserHome for Hidden and BrowserV2ProductionDestination otherwise. It has no entry in the current production route graph, so this retained legacy risk is not an active-route release defect. The harness tests chrome/failure only, not the popup.
2. **New history control accessible naming — source remedied, Android pending:** parent changed HiddenBrowserHome to a labeled minimum-48dp toggleable Row with Role.Switch and a non-interactive inner Switch. Current native child adds an explicit history contentDescription and minimum-48dp Switch. The initially observed unlabeled sibling Switch/Text risk is therefore addressed in current source. Verify merged focus behavior/TalkBack on the final source; no executed accessibility result is claimed, and child sources remain WIP.
3. **Nested scroll boundaries:** External editor choices now use an ordinary Column inside A's default scroll body (no nested lazy viewport), and the dedicated chooser test targets this. Lazy collection/upload/source/history lists generally use scrollBody=false plus explicit bounded viewports; do not wrap their lazy body in another unbounded vertical scroll. Inspection did not establish a current remaining infinite-height lazy-scroll crash. This is source reasoning, not full runtime proof.
4. **Remaining layout candidates requiring direct tests:** AiSourceCards fixed title + bounded source grid; Create Image reference/grid footer; PhotoEditor enlarged-source fixed header/footer; TransferHistory's fixed message plus bounded list; legacy diagnostics action rows; long saved URLs in child library (now a bounded LazyColumn in WIP source); and native authentication fields under short windows/IME. They inherit theme but can still consume available height/width at extreme text sizes. Current restored prompt/upload/reference/transfer fixes address specific prior risks; they do not establish the whole viewport matrix.
5. **Decoration exceptions are explicit:** PhotoEditor enlarged-source Surface inherits selected Material3 colors but does not use G's outline/elevation. Enlarged result/video/fullscreen media intentionally use black media canvases with white controls. Hidden foundation/PrimaryCopy/Synthetic authentication use selected-theme fullscreen Surface. These are not evidence of unstyled white dialogs.

## Complete constructor mapping

Catalog references below expand to exact test class and method names. A family prefix such as T10 means its a/b/c catalog entries. Every U remains a release verification gap; shared-shell coverage cannot turn it into D.

| ID | Inventory source | Family / source styling | Direct test mapping and boundary |
|---|---|---|---|
| 01 | `MainActivity.kt:3233` | Album dropdown · M | U; themed source only; no identified direct runtime method for this component/state. |
| 02 | `MainActivity.kt:3436` | Encrypted backup restore · A | U; themed source only; no identified direct runtime method for this component/state. |
| 03 | `MainActivity.kt:4008` | Recovery-key export verification · A | U; themed source only; no identified direct runtime method for this component/state. |
| 04 | `MainActivity.kt:4028` | Third-party licences · A | U; themed source only; no identified direct runtime method for this component/state. |
| 05 | `MainActivity.kt:4054` | Owner identity confirmation · A | U; themed source only; no identified direct runtime method for this component/state. |
| 06 | `MainActivity.kt:4081` | Change PIN · A | U; themed source only; no identified direct runtime method for this component/state. |
| 07 | `MainActivity.kt:4383` | Copy/move selection to Hidden confirmation · A | U; themed source only; no identified direct runtime method for this component/state. |
| 08 | `MainActivity.kt:4394` | Transfer-to-Hidden progress · A | U; themed source only; no identified direct runtime method for this component/state. |
| 09 | `MainActivity.kt:4401` | Bulk Recently Deleted confirmation · A | U; themed source only; no identified direct runtime method for this component/state. |
| 10 | `MainActivity.kt:4411` | Bulk Gallery restore confirmation · A | U; themed source only; no identified direct runtime method for this component/state. |
| 11 | `MainActivity.kt:4421` | Permanent delete confirmation · A | U; themed source only; no identified direct runtime method for this component/state. |
| 12 | `MainActivity.kt:4454` | Collection cover picker · A | U; themed source only; no identified direct runtime method for this component/state. |
| 13 | `MainActivity.kt:4614` | New collection · A | U; related T03 tests list pickers, not New collection text entry/IME. |
| 14 | `MainActivity.kt:4628` | Manage collection · A | U; related T03 does not open management/delete/rename states. |
| 15 | `MainActivity.kt:4643` | Add to collection picker · A | D T03b; list scrolling/selection in light theme. |
| 16 | `MainActivity.kt:4713` | Add Vault media picker · A | D T03a; 300-item list scrolling/selection in dark theme. |
| 17 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:238` | Hidden VPN settings · A | D T21 (Settings open); other controls U; child source WIP. |
| 18 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:247` | Hidden VPN tabs · A | U; T21 does not open/manage full tab list; child WIP. |
| 19 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:248` | Hidden VPN bookmark/history library · A | D T21 (both libraries via actual child); maximal payload assertion, not full visual matrix; child WIP. |
| 20 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:249` | Hidden GET download confirmation · A | U; GET consent/download UI not exercised by T20 upload flow. |
| 21 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:250` | Hidden upload picker · A | D T20 eligible/restricted upload picker; live preview state. Theme/large text U. |
| 22 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:264` | Hidden upload consent · A | D T20 explicit Allow upload path; no contrary-theme/large-text matrix. |
| 23 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:274` | Hidden website HTTP authentication · A | U; native child HTTP-auth UI has no identified direct method. |
| 24 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:278` | Hidden form resubmission · A | U; native child resubmit dialog has no identified direct method. |
| 25 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:281` | Hidden website JS alert/confirm/prompt · A | U; T04c/T04e target Primary, not child website dialogs. |
| 26 | `ui/AiEditingSettings.kt:145` | Prompt template editor · G | D T02 narrow 320×640/2x footer and scrolling; IME/contrary OS theme U. |
| 27 | `ui/AiEditingSettings.kt:203` | Provider credential setup · G | D T07 provider entry/remove/failure/background; visual/IME matrix U. |
| 28 | `ui/AiModelPicker.kt:46` | AI model picker sheet · M | D T08 model/provider sheet interaction; complete theme/IME matrix U. |
| 29 | `ui/AiSourceCards.kt:93` | AI source-photo picker · A | D T09 source replacement and stale-load handling; all source-picker layout states U. |
| 30 | `ui/BrowserPanels.kt:45` | Browser full-screen panel shell · G | D T10/T11 panel/settings/library interactions; shell theme sourced; complete IME/focus matrix U. |
| 31 | `ui/BrowserPanels.kt:176` | Clear Primary history confirmation · A | D T10c explicit history-clear confirmation; large-text/error states U. |
| 32 | `ui/BrowserPanels.kt:244` | Clear Primary browsing-data confirmation · A | U; T10a opens settings but does not prove browsing-data confirmation. |
| 33 | `ui/BrowserV2Home.kt:499` | Browser internal media viewer · S | U; media canvas policy/tests are not direct modal layout coverage. |
| 34 | `ui/BrowserV2Home.kt:719` | Save browser video · A | U; save-video consent/progress modal not directly mapped. |
| 35 | `ui/BrowserV2Home.kt:812` | Upload source choice · A | D T04a upload choice/extension path; all theme/viewport states U. |
| 36 | `ui/BrowserV2Home.kt:821` | Primary Vault upload picker · G | D T04a/T04d/T12; actual picker/reopen/preview; 2x/landscape grid matrix U. |
| 37 | `ui/BrowserV2Home.kt:859` | Decrypted upload confirmation · A | D T04a explicit upload confirmation; cancel race T04b; visual matrix U. |
| 38 | `ui/BrowserV2Home.kt:889` | Find in page · A | U; no identified direct find/IME modal method. |
| 39 | `ui/BrowserV2Home.kt:896` | Primary browser diagnostics · A | U; diagnostics lifecycle policy is not complete dialog layout coverage. |
| 40 | `ui/BrowserV2Home.kt:929` | Website JS/leave-page dialog · A | D T04c/T04e; T05 related once-only/revocation policy. |
| 41 | `ui/BrowserV2Home.kt:942` | Browser message · A | U; no identified arbitrary long-message rendering method. |
| 42 | `ui/BrowserV2Home.kt:943` | Website camera/microphone permission · A | U; no identified actual app camera/microphone consent dialog layout method. |
| 43 | `ui/BrowserV2Home.kt:961` | Website geolocation permission · A | U; no identified actual geolocation dialog layout method. |
| 44 | `ui/BrowserV2Home.kt:972` | Save image resource · A | U; no identified image-resource consent dialog layout method. |
| 45 | `ui/BrowserV2Home.kt:984` | Screenshot fallback consent · A | U; no identified screenshot fallback dialog layout method. |
| 46 | `ui/BrowserV2Home.kt:996` | Open external app consent · A | U; external intent policy does not prove app-confirmation layout. |
| 47 | `ui/CreateImageSheet.kt:90` | Create Image sheet · M | R T13 cancellation; actual full sheet/IME layout U. |
| 48 | `ui/CreateImageSheet.kt:166` | Create Image remote consent · A | R T13; Create Image remote consent appearance U (T14 targets edit consent). |
| 49 | `ui/CreateImageSheet.kt:171` | Create Image diagnostics · A | U; long Create Image diagnostics state not directly mapped. |
| 50 | `ui/CreateImageSheet.kt:178` | Create Image reference grid · G | U; source fix moves introduction into lazy grid, but no dedicated reference-grid runtime layout test identified. |
| 51 | `ui/ExternalEditingDialog.kt:44` | External editing status/handoff · A | D T15a/T15b entry/prepare/import states; full theme/IME/large-text matrix U. |
| 52 | `ui/ExternalEditingDialog.kt:72` | External editor choice · A | D T16 multi-editor choice without nested-scroll crash. |
| 53 | `ui/ExternalEditingDialog.kt:76` | External editing plaintext-egress consent · A | D T15b explicit editor preparation; all visual states U. |
| 54 | `ui/ExternalEditingDialog.kt:80` | Public-copy/export consent · A | U; public fallback export consent not directly covered by editor handoff test. |
| 55 | `ui/ExternalEditingDialog.kt:84` | Replace encrypted original confirmation · A | D T15c explicit verified replacement confirmation; failure/large text U. |
| 56 | `ui/FullscreenMediaViewer.kt:322` | Viewer Recently Deleted confirmation · A | U; no identified direct viewer-trash modal appearance method. |
| 57 | `ui/FullscreenMediaViewer.kt:789` | Video opening error · A | U; decoder tests do not demonstrate this error dialog layout. |
| 58 | `ui/GalleryAlertDialog.kt:32` | Shared alert implementation · A | D T01 shared shell only; does not upgrade unverified call sites to verified. |
| 59 | `ui/HiddenBrowserHome.kt:139` | Hidden browser settings · A | D T17 settings/tabs at four synthetic viewports; disconnected combined-mode fixture; connected controls/IME semantics U. |
| 60 | `ui/HiddenBrowserHome.kt:171` | Hidden bookmark/history library · A | U; T18 covers encrypted persistence, not Tor library UI rendering. |
| 61 | `ui/HiddenBrowserHome.kt:187` | Hidden tab overview · A | D T17 empty/disconnected overview only; populated long list/focus U. |
| 62 | `ui/HiddenFoundationDialog.kt:129` | Hidden foundation authentication · S | D T19 setup/background; shared keypad T23; whole visual matrix U. |
| 63 | `ui/HiddenRestoreDialog.kt:42` | Hidden restore destination/authentication/progress · G | D T22 + T24 destination choices, wrong/correct/cancel/lock/expiry and move confirmation; 2x/IME non-auth form U. |
| 64 | `ui/HiddenVaultDialog.kt:274` | Hidden viewer collection chooser · A | U; Primary T03 picker is related but this Hidden collection chooser is a separate call site. |
| 65 | `ui/MediaChrome.kt:68` | Shared media More sheet · M | D T25a actual viewer More sheet; all options/large-text/landscape U. |
| 66 | `ui/MediaChrome.kt:175` | Media sort dropdown · M | U; media sort-dropdown theme inheritance is source-only. |
| 67 | `ui/MediaDetailsDialog.kt:124` | Native date picker · N | D T26a screenshot policy; complete selected-theme OEM appearance U. |
| 68 | `ui/MediaDetailsDialog.kt:125` | Native time picker · N | D T26a screenshot policy; complete selected-theme OEM appearance U. |
| 69 | `ui/MediaDetailsDialog.kt:135` | Media details · A | D T26a/T26b details/date path; all long metadata/large-text/IME U. |
| 70 | `ui/MediaSelectionDialog.kt:39` | Select by date/size · A | D T27 date/size selection scope; visual/focus matrix U. |
| 71 | `ui/PhotoEditor.kt:548` | Enlarged source preview · S | D T09 source enlargement flow; no full-height 2x/landscape guarantee. |
| 72 | `ui/PhotoEditor.kt:560` | Enlarged AI result · S | D T09 result preview/discard flow; intentional black media canvas. |
| 73 | `ui/PhotoEditor.kt:574` | Prompt diagnostics · A | U; prompt diagnostic long-content layout not directly mapped. |
| 74 | `ui/PhotoEditor.kt:582` | Prediction diagnostics · A | U; prediction diagnostic long-content layout not directly mapped. |
| 75 | `ui/PhotoEditor.kt:589` | AI operation progress · A | R T14 operation flow/cancellation; progress modal appearance U. |
| 76 | `ui/PhotoEditor.kt:601` | Saving-stage progress · A | R T14 saves encrypted copy; saving-stage layout U. |
| 77 | `ui/PhotoEditor.kt:605` | Remote AI consent · A | D T14a consent-before-edit flow; large-text/theme matrix U. |
| 78 | `ui/PhotoEditor.kt:610` | Discard edits · A | D T09 discard result flow; all exit/discard states U. |
| 79 | `ui/PrimaryCopyDialog.kt:50` | Primary-to-Hidden copy/authentication picker · S | D T28 wrong→correct PIN/source/copy plus T23 keypad; lock/expiry/full picker large-text U. |
| 80 | `ui/PrivacyShareDialog.kt:112` | Privacy share review · A | D T29 dark scroll/cancel without media read; light/IME/landscape U. |
| 81 | `ui/PrivacyShieldSettings.kt:56` | Panic Exit test confirmation · A | U; T30 concerns settings visibility/auth, not opening Panic Exit test dialog. |
| 82 | `ui/PrivateBrowser.kt:523` | Legacy browser dropdown · M | U legacy; retained constructor is themed M3, current production routes do not call legacy BrowserHome (test harness only). |
| 83 | `ui/PrivateBrowser.kt:617` | Legacy download consent · A | U legacy; direct download-consent layout not mapped. |
| 84 | `ui/PrivateBrowser.kt:627` | Legacy image/screenshot consent · A | U legacy; direct image/screenshot-consent layout not mapped. |
| 85 | `ui/PrivateBrowser.kt:641` | Legacy bookmarks · A | U legacy; T10b targets new BrowserBookmarksPanel, not this dialog. |
| 86 | `ui/PrivateBrowser.kt:663` | Legacy diagnostics · A | U legacy; no direct debug-console modal layout mapping. |
| 87 | `ui/PrivateVideoPlayer.kt:235` | Video playback-speed menu · M | U; player interaction tests do not prove speed-dropdown layout. |
| 88 | `ui/RetiredModelCleanup.kt:27` | Retired model cleanup · A | U; provider migration/storage checks do not open cleanup confirmation. |
| 89 | `ui/SyntheticHiddenGalleryDialog.kt:58` | Synthetic Hidden gallery harness · S | D T31 synthetic fixture import/view/reset/background; not real-vault acceptance. |
| 90 | `ui/TransferHistoryDialog.kt:33` | Encrypted transfer history · A | U; transfer storage/retry tests do not mount this actual history dialog; wrapping source fix only. |
| 91 | `ui/VaultCameraScreen.kt:404` | Camera recording details · A | U; camera controls/security tests do not open Recording details. |
| 92 | `ui/VaultStoragePanel.kt:29` | Vault storage summary · A | U; storage arithmetic/unit tests do not render summary. |
| 93 | `ui/VaultStoragePanel.kt:52` | Clear disposable preview cache · A | U; no identified actual clear-preview-confirmation layout method. |

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

## Overlay families beyond constructors

- **Privacy covers, secondary windows, panic shield and accessibility focus concealment:** MainActivity lifecycle/security path. `HiddenVpnHandoff1762Test.childHomeUsesIndependentHiddenGraceAndImmediatePrimaryPolicy`, `childScreenOffLocksBothEvenWhenMainIsAlreadyStopped`, `delayedLossAfterForegroundExpiresOnlyTheMatchingIndependentAuthentication`, `lateMainStartConsumesReturnBeforeOverdueTimerCanBeCancelled` cover synthetic handoff/lifecycle behavior. They are not physical recents/fold/window-stack/TalkBack screenshot proof. Lifecycle sources are WIP.
- **Fullscreen independent authentication:** T19/T22/T23/T24/T28 map Hidden setup and cross-vault PIN paths. Primary setup/change/recovery and numeric form IME/keyboard traversal still need direct actual-page coverage rather than inferring it from keypad geometry.
- **Media/editor/camera fullscreen canvases and controls:** T09/T25 and `PhotoEditorUiTest.foldSizedCanvasesKeepSaveAndToolsReachable`, `PhotoEditorUiTest.backgroundWipesLoadedSourceBeforeResume`; `VaultCameraUiTest.blackPhotoTapsVolumeAndNavigationGestures`, `readingPhotoTapsVolumeAndNavigationGestures`, `activityForegroundLossLocksBothVaultsAndNeverReopensHiddenCamera` are related active component tests. Recording-details popup is still U. Black media backgrounds are intentional.
- **Primary browser full-screen panels:** T10/T11 cover current panels and adaptive chrome. Their tests do not establish all nested confirmation/find/permissions/diagnostic states.
- **Legacy temporary popup WebView:** source risk described above; absent from constructor inventory and unreachable through current production routes. A test-only BrowserHome harness exists; popup close/back/keyboard restoration remains unverified for retained legacy code.
- **New native Tor child and transport:** currently shares/churns native child sources with VPN. Existing HiddenBrowserHome disconnected combined-mode layout fixture does not verify the eventual connected native Tor settings/tabs/library/upload/website-dialog UI. Carry rows 17–25 and relevant library settings into the final native-Tor matrix; mark WIP until final source and execution artifacts exist.
- **System UI:** Android permissions, biometrics, VPN consent, photo picker, SAF and external editors are externally owned. App egress/permission explanation dialogs remain covered by this inventory; external OEM appearance must not be counted as app wrapper verification.

## Required acceptance evidence

Freeze final source, run mapped component tests and add real-state tests for U entries, then inspect artifacts across cover/inner × portrait/landscape × selected light/dark (including contrary OS theme), 1.6x/2x and system maximum font scale. Show IME for every text-entry/password/search/API/prompt/date form; verify readable last content, nonzero useful grid/list viewport, reachable cancel/confirm, long errors/loading/disabled/busy states, focus order, keyboard Enter/Escape/Back and TalkBack labels. Test dismissal/revocation on background, lock, route loss and mode switch. Physical Fold transitions, Samsung keyboard/native picker/editor and real screen-reader acceptance remain owner/device verification. This map is explicit coverage accounting, not a claim of complete modal acceptance.
