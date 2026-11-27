# Build 99 Android 17 failure inventory

Run [46043469375](https://github.com/traynor1987/Private-gallery-/actions/runs/46043469375), job 140656768912, source `2cd669b2cbff6d04ef3d5dda6c555843208ab8e0`. Last progress line showed 986 of 1,151 broad tests completed, 83 failed, zero skipped. The job ended before the full release verifier. Native WireGuard and Tor suites ran separately. No passing full-security receipt exists for this run.

The first failure was Hidden encrypted video continuity. The activity reported one focus loss, regained focus, a concealment epoch of 1, a resumed lifecycle and no attached player. The log does not identify the window that took focus. The focused comparison records native window ownership and compares the original main and candidate under identical emulator settings.

The exception groups below describe observations. Several can be symptoms of the same upstream fault. A test files directory that has never been created cannot satisfy the existing storage inventory. A second ordering fault allows an encrypted presentation preference to become the first file in an otherwise empty Primary vault. The repair initializes the index through the existing guarded inventory path before writing those preferences.

| Observation | Cases |
|---|---:|
| Player never ready | 5 |
| Other assertion or state | 15 |
| Compose timeout | 12 |
| UI visibility, capture or scrolling | 27 |
| Missing Primary index | 8 |
| Input dispatch | 16 |

| Order | Test | First reported error |
|---:|---|---|
| 1 | `EncryptedVideoContinuity1751Test#hiddenEncryptedSessionSurvivesBufferingControlsAndRecomposition` | java.lang.AssertionError: Real player must prepare before Panic and advance: focusLosses=1 focus=true cover=false epoch=1 lifecycle=RESUMED state=null error=null |
| 2 | `EncryptedVideoContinuity1751Test#primaryEncryptedSessionSurvivesBufferingControlsAndRecomposition` | java.lang.AssertionError: Real player must prepare before Panic and advance: focusLosses=1 focus=true cover=false epoch=1 lifecycle=RESUMED state=null error=null |
| 3 | `EncryptedVideoContinuity1751Test#shortEncryptedVideoAdvancesBeforePanic` | java.lang.AssertionError: Real player must prepare before Panic and advance: focusLosses=1 focus=true cover=false epoch=1 lifecycle=RESUMED state=null error=null |
| 4 | `EncryptedVideoContinuity1751Test#boundedMemoryFallbackKeepsOnePlayerAcrossRecomposition` | java.lang.AssertionError: Real player must prepare before Panic and advance: focusLosses=1 focus=true cover=false epoch=1 lifecycle=RESUMED state=null error=null |
| 5 | `HiddenMainBrowserLifecycle1762Test#realMainVpnTorVpnSwitchRetiresOldProcessAndDoesNotRestoreCookies` | java.lang.AssertionError: Production VPN child |
| 6 | `HiddenMainBrowserLifecycle1762Test#realMainToNativeChildThenHomeAppliesIndependentLockPolicies` | java.lang.AssertionError: Production Runtime launches the real native browser |
| 7 | `HomeNavigation1710Test#homeRequiresAuthenticationAndSurvivesHiddenPresentationRevocation` | androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 15000 ms |
| 8 | `LauncherWorkspace70Test#directPrimaryVaultHidesOnlyInternalNavigationUntilReturningHome` | java.lang.IllegalStateException: Existing Vault or credentials require recovery |
| 9 | `NovaBrowserApps17102Test#launcherKeepsTorHiddenOnlyAndIgnoresLegacyTransportPreference` | androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 15000 ms |
| 10 | `NovaNavigation17101Test#authenticatedExistingInstallStartsAtNovaHomeAndAppRootReturnsHome` | androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 15000 ms |
| 11 | `PrimaryGalleryDeviceRepair1762Test#seededPrimaryGallerySurvivesHiddenNavigationAndRevokesOnLock` | java.lang.AssertionError: No existing Hidden fixture media |
| 12 | `PrivacyShield175Test#panicDuringRealPlayerPlaybackStopsImmediatelyAndRevokesSession` | java.lang.AssertionError: Real player must prepare before Panic and advance: focusLosses=1 focus=true cover=false epoch=1 lifecycle=RESUMED state=null error=null |
| 13 | `PrivacyShield175Test#videoCompletingUnderOwnedDialogCannotAutoplayOrCover` | java.lang.AssertionError: Owned dialog must not cover authorised content |
| 14 | `PrivacyViewerRepair1751Test#actualAndroidPermissionDialogKeepsPhotoAndCorrectForegroundPolicy` | java.lang.AssertionError: Native permission result and foreground focus must return before unregistering (callback=true) |
| 15 | `PrivacyViewerRepair1751Test#backgroundRequiresExplicitReopenWithoutAutomaticDecode` | java.lang.AssertionError: Failed: assertExists. |
| 16 | `PrivacyViewerRepair1751Test#screenshotPreferenceSurvivesRecreationAndBackgroundReturn` | androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 3000 ms |
| 17 | `PrivacyViewerRepair1751Test#ownedDialogDoesNotCoverOrReleaseViewerImage` | androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 3000 ms |
| 18 | `PrivacyViewerRepair1751Test#permissionStylePauseKeepsDecodedImageButSecuresPausedWindow` | androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 3000 ms |
| 19 | `core.browser.hidden.HiddenLibraryParcelPressure1762Test#maximalLibraryTraversesActualClaimedChildBinderWithoutTransactionTooLarge` | java.lang.AssertionError: Visible actionable 'Done' |
| 20 | `core.browser.hidden.HiddenNativePresentation1762Test#nativeChildUsesParentThemeAndHiddenCapturePolicyWithoutCrossProcessPreferenceRead` | java.lang.AssertionError: Actual native 320x640 chrome at OS two-times text |
| 21 | `core.browser.hidden.HiddenPrivateAppsLive178Test#vpnCatalogueCrudLaunchAndRevocationUseAuthenticatedSeparateProcess` | java.lang.AssertionError: Actual native control: Close |
| 22 | `core.browser.hidden.HiddenPrivateAppsLive178Test#torCatalogueCrudOnionLaunchAndRevocationUseAuthenticatedSeparateProcess` | java.lang.AssertionError: Actual native control: Close |
| 23 | `core.hidden.HiddenRecentlyDeleted177Test#independentRetentionRestoreMembershipAndThumbnailRecovery` | java.lang.IllegalStateException: Primary index missing for existing, partial or unavailable material |
| 24 | `core.media.BrowserVideoAssistantTest#leavingPlayingTabPausesItsRetainedMedia` | java.lang.AssertionError: Failed to inject touch input. |
| 25 | `core.media.BrowserVideoAssistantTest#pageAddressIsNeverMediaAndBlobDrmCookieSourcesKeepWebView` | java.lang.AssertionError: Failed to inject touch input. |
| 26 | `core.media.BrowserVideoAssistantTest#playingVideoAffordanceUsesNativeCustomViewAndRestoresPortrait` | java.lang.AssertionError: Failed to inject touch input. |
| 27 | `core.media.ConfigurationRetentionTest#explicitActivityRecreationRetainsOnlyInProcessUnlockedSession` | java.lang.AssertionError: expected same:<[B@ac30bda> was not:<null> |
| 28 | `core.media.PrivateVideoPlayerTest#galleryContentUriPlaybackAndCleanup` | androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 15000 ms |
| 29 | `core.vault.RecentlyDeleted177Test#expiryDoesNotRemoveAnActiveImportStagingFile` | java.lang.IllegalStateException: Primary index missing for existing, partial or unavailable material |
| 30 | `core.vault.RecentlyDeleted177Test#corruptPrimaryRestoreRetainsTrashAndCiphertext` | java.lang.IllegalStateException: Primary index missing for existing, partial or unavailable material |
| 31 | `core.vault.RecentlyDeleted177Test#interruptedPrimaryDeletionRestoresRetainedRecordAndRestoreIsIdempotent` | java.lang.IllegalStateException: Primary index missing for existing, partial or unavailable material |
| 32 | `core.vault.RecentlyDeleted177Test#deletionInterruptedAfterIndexCommitReconcilesPayloadAndPreviewOnly` | java.lang.IllegalStateException: Primary index missing for existing, partial or unavailable material |
| 33 | `core.vault.RecentlyDeleted177Test#restoreStopsWhenAuthorityEndsDuringPayloadVerification` | java.lang.IllegalStateException: Primary index missing for existing, partial or unavailable material |
| 34 | `core.vault.VaultDocumentRepositoryTest#cancelledOrInvalidDocumentCannotBecomeComplete` | java.lang.IllegalStateException: Primary index missing for existing, partial or unavailable material |
| 35 | `core.vault.VaultDocumentRepositoryTest#textRoundTripDuplicateTrashAndDownloadsRestore` | java.lang.IllegalStateException: Primary index missing for existing, partial or unavailable material |
| 36 | `ui.BrowserConsent1752Test#lightDelayedConsentRendersAndRequiresManualCheckbox` | androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 10000 ms |
| 37 | `ui.BrowserConsent1752Test#movedEligiblePhotoAppearsWhenPickerReopensButRestrictedPhotoDoesNot` | java.lang.IllegalArgumentException: Targeted input event injection from pid 7313 was not directed at a window owned by uid 10238. |
| 38 | `ui.BrowserConsent1752Test#darkTransformedConsentRendersAndRemainsInteractive` | androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 10000 ms |
| 39 | `ui.BrowserConsent1752Test#websiteDialogsUseThemedShellAndRevokeOnBackground` | java.lang.IllegalArgumentException: Targeted input event injection from pid 7313 was not directed at a window owned by uid 10238. |
| 40 | `ui.BrowserConsent1752Test#cancelledPreparationCannotClearNewerChooserRequest` | java.lang.IllegalArgumentException: Targeted input event injection from pid 7313 was not directed at a window owned by uid 10238. |
| 41 | `ui.BrowserConsent1752Test#realWebsiteAlertAndBeforeUnloadRequireExplicitChoice` | java.lang.IllegalArgumentException: Targeted input event injection from pid 7313 was not directed at a window owned by uid 10238. |
| 42 | `ui.BrowserConsent1752Test#extensionBasedUploadUsesThemedVaultPickerWithoutPrematureEgress` | java.lang.IllegalArgumentException: Targeted input event injection from pid 7313 was not directed at a window owned by uid 10238. |
| 43 | `ui.BrowserPolishTest#realPageScrollHidesThenRevealsChromeWithoutRecreatingWebView` | java.lang.AssertionError: Assert failed: The component is not displayed! |
| 44 | `ui.BrowserPolishTest#browserSettingsOpenLocallyAndReturnToSamePageAndView` | java.lang.AssertionError: Failed to inject touch input. |
| 45 | `ui.BrowserProfessionalUiTest#coverLandscapeLargeText` | java.lang.AssertionError: Assert failed: The component is not displayed! |
| 46 | `ui.BrowserProfessionalUiTest#innerLandscapeLight` | java.lang.AssertionError: Assert failed: The component is not displayed! |
| 47 | `ui.BrowserProfessionalUiTest#innerDark` | java.lang.AssertionError: Assert failed: The component is not displayed! |
| 48 | `ui.BrowserProfessionalUiTest#coverLight` | java.lang.AssertionError: Assert failed: The component is not displayed! |
| 49 | `ui.BrowserRuntimeDiagnosticsTest#clearingCurrentTraceRetainsClearlySeparatedPreviousFatalEvidence` | java.lang.AssertionError |
| 50 | `ui.BrowserV2HomeRenderTest#staticHostOmniboxSubmitDoesNotCrashOrNavigate` | java.lang.AssertionError: Failed to inject touch input. |
| 51 | `ui.BrowserV2HomeRenderTest#browserV2ChromeFitsFoldInnerWidth` | java.lang.AssertionError: Assert failed: The component is not displayed! |
| 52 | `ui.BrowserV2HomeRenderTest#browserMenuCanReturnToGalleryWithoutAppBottomBar` | java.lang.AssertionError: Failed to inject touch input. |
| 53 | `ui.BrowserV2HomeRenderTest#browserV2ChromeFitsFoldOuterWidth` | java.lang.AssertionError: Assert failed: The component is not displayed! |
| 54 | `ui.BrowserV2HomeRenderTest#tabCardsShowCurrentTabAndCloseControl` | java.lang.AssertionError: Failed to inject touch input. |
| 55 | `ui.BrowserV2HomeRenderTest#browserOverflowDoesNotExposeHostOrLocalFixtureControls` | java.lang.AssertionError: Failed to inject touch input. |
| 56 | `ui.BrowserV2HomeRenderTest#productionBrowserRouteShowsEveryAcceptanceLayerOnOuterDisplay` | java.lang.AssertionError: Assert failed: The component is not displayed! |
| 57 | `ui.BrowserV2HomeRenderTest#productionBrowserRouteShowsEveryAcceptanceLayerOnInnerDisplay` | java.lang.AssertionError: Assert failed: The component is not displayed! |
| 58 | `ui.BrowserV2HomeRenderTest#realWebViewSubmitKeepsChromeAndRetainedViewWhenNetworkingIsBlocked` | java.lang.AssertionError: Failed to inject touch input. |
| 59 | `ui.BrowserV2HomeRenderTest#browserV2ChromeSurvivesAnUnavailableWebViewProvider` | java.lang.AssertionError: Assert failed: The component is not displayed! |
| 60 | `ui.BrowserV2HomeRenderTest#browserToolbarSitsBelowThePageAndOpensBookmarks` | java.lang.AssertionError: Failed: assertExists. |
| 61 | `ui.BrowserV2HomeRenderTest#defaultRealWebViewHasNoLayoutColoursAndTypingKeepsChromeWithoutNavigating` | java.lang.AssertionError: Real WebView must be attached to AndroidView |
| 62 | `ui.BrowserV2InitialPresentationTest#directRealMountShowsChromePixelsWithoutAnyNavigationOrInteraction` | java.lang.AssertionError: Failed to capture a node to bitmap. |
| 63 | `ui.BrowserV2InitialPresentationTest#narrowStaticToRealKeepsChromePixelsAcrossLegitimatePlaceholderReflow` | java.lang.AssertionError: Failed to capture a node to bitmap. |
| 64 | `ui.BrowserV2InitialPresentationTest#staticToRealKeepsChromePixelsBeforeNavigationAndAfterLocalPage` | java.lang.AssertionError: Failed to capture a node to bitmap. |
| 65 | `ui.BrowserV2ModernWebTest#nativeConfirmDialogCompletesWithoutReplacingParentPage` | androidx.test.espresso.NoMatchingViewException: No views in hierarchy found matching: view.getId() is <16908313/android:id/button1> |
| 66 | `ui.HiddenBrowser17LayoutTest#foldCoverLight` | java.lang.AssertionError: Assert failed: The component is not displayed! |
| 67 | `ui.HiddenBrowser17LayoutTest#foldInnerLandscapeLight` | java.lang.AssertionError: Assert failed: The component is not displayed! |
| 68 | `ui.HiddenBrowser17LayoutTest#foldInnerDark` | java.lang.AssertionError: Assert failed: The component is not displayed! |
| 69 | `ui.HiddenBrowser17LayoutTest#foldCoverLandscapeDark` | java.lang.AssertionError: Assert failed: The component is not displayed! |
| 70 | `ui.HiddenBrowserCapturePolicy1762Test#parentSelectedThemeIsAuthoritativeAndIndependentOfHiddenCaptureOptIn` | java.lang.AssertionError: expected:<DARK> but was:<SYSTEM> |
| 71 | `ui.HiddenHostRoutingTest#foregroundCreatesFreshBrowserOwnerWithoutRestoringTabs` | java.lang.AssertionError: Failed to inject touch input. |
| 72 | `ui.HiddenHostRoutingTest#primaryGalleryBrowseReturnsToSelectedHiddenAndCancellationKeepsPrimaryLocked` | java.lang.AssertionError: Assert failed: The component is not displayed! |
| 73 | `ui.HiddenHostRoutingTest#hiddenGallerySwitchRequiresPrimaryAuthenticationAndReturnsToPhone` | java.lang.AssertionError: Failed to assert the following: (Selected = 'true') |
| 74 | `ui.HiddenHostRoutingTest#authenticatedHiddenTransitionToBrowserKeepsAuthorityAndNeverStartsUnavailableMode` | java.lang.AssertionError: Failed to inject touch input. |
| 75 | `ui.HiddenHostRoutingTest#primaryGalleryIsOriginalPhoneGalleryEvenWithStaleSourceSelection` | java.lang.AssertionError: Assert failed: The component is not displayed! |
| 76 | `ui.HiddenRestoreDialogTest#moveRequiresSeparateConfirmationAndLockRevokesDialogOnce` | java.lang.AssertionError: Action performScrollTo() failed. |
| 77 | `ui.HiddenRestoreDialogTest#choicesAreExplicitAndPrimaryRequiresFreshPinWithoutChangingHidden` | java.lang.AssertionError: Action performScrollTo() failed. |
| 78 | `ui.HiddenVaultIntegrationTest#normalVaultGridViewerAndFavouriteUsePersistedHiddenData` | androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 30000 ms |
| 79 | `ui.HiddenVaultIntegrationTest#hiddenBottomNavigationFitsActualActivityWindowNotItsOwnLayoutBounds` | androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 30000 ms |
| 80 | `ui.HiddenVaultIntegrationTest#settingsNavigationRetainsAuthenticationButHidesHiddenMedia` | java.lang.AssertionError: Assert failed: The component is not displayed! |
| 81 | `ui.HiddenVaultIntegrationTest#browserNavigationRetainsAuthenticationButHidesHiddenMedia` | java.lang.AssertionError: Assert failed: The component is not displayed! |
| 82 | `ui.HiddenVaultIntegrationTest#pickerResultSurvivesPrimaryLockButImportRequiresFreshHiddenPin` | androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 30000 ms |
| 83 | `ui.HiddenVaultIntegrationTest#galleryNavigationRetainsAuthenticationButHidesHiddenMedia` | java.lang.AssertionError: Assert failed: The component is not displayed! |
