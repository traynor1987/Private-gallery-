# Android17 run40755407244 — d5c246a

Exact source `d5c246a6c1a366e71cee47751dd9062cec23b199`. Host193 checks, Kotlin/JVM/lint and both APK compilations passed. Android executed **273 cases:255 passed,18 failed,0 skipped,0 errors**. This is a failed release gate. Artifact `12845994047`, ZIP SHA256 `4701aa33b739a55ad86f49fe9c90f318e1eaa6a6b368f9acdb39631a96137bef`, contains449 entries including modal PNGs and per-case logs. No signing or physical acceptance.

All14 Main modal tests, six native capture-policy cases, real child presentation policy/theme, the real owned-Network-loss case, actual OS large-font Dialog probe, Unix successful DOMAIN/payload control and Unix connection-timeout control passed. These limited results do not establish complete VPN/Tor body/routing/isolation security; the native-live case did not reach its observations.

| Failing case | Recorded failure |
|---|---|
| `uk.co.traynor.privategallery.ui.BrowserPermissionOwnership1762Test.ownedDevicePickerSurvivesActualStopAndDefersEarlyResultUntilResume` | java.lang.SecurityException: android.content.pm.PackageManager$NameNotFoundException: uk.co.traynor.privategallery |
| `uk.co.traynor.privategallery.ui.BrowserPermissionOwnership1762Test.cancelledSingleMarkerBlocksMultipleReplacementUntilActualResultAndCapsFour` | java.lang.SecurityException: android.content.pm.PackageManager$NameNotFoundException: uk.co.traynor.privategallery |
| `uk.co.traynor.privategallery.ui.BrowserPermissionOwnership1762Test.cancelledDeviceValidationCannotDeliverLateReadableUri` | java.lang.SecurityException: android.content.pm.PackageManager$NameNotFoundException: uk.co.traynor.privategallery |
| `uk.co.traynor.privategallery.ui.BrowserPermissionOwnership1762Test.cancelledDeviceAUriCannotReachReplacementBOrEraseItsChoice` | java.lang.SecurityException: android.content.pm.PackageManager$NameNotFoundException: uk.co.traynor.privategallery |
| `uk.co.traynor.privategallery.ui.BrowserDeviceSelection1762Test.trustedDocumentIntentIsExplicitReadOnlyAndPreservesAllMimeAlternatives` | java.lang.SecurityException: android.content.pm.PackageManager$NameNotFoundException: uk.co.traynor.privategallery |
| `uk.co.traynor.privategallery.ui.BrowserDeviceSelection1762Test.privateFilesOwnProviderAliasesAndMixedResultsNeverBecomeDeviceUploads` | java.lang.SecurityException: android.content.pm.PackageManager$NameNotFoundException: uk.co.traynor.privategallery |
| `uk.co.traynor.privategallery.ui.BrowserDeviceSelection1762Test.actualExternalReadGrantAndDescriptorAcceptedThenRevocationDenied` | java.lang.SecurityException: android.content.pm.PackageManager$NameNotFoundException: uk.co.traynor.privategallery |
| `uk.co.traynor.privategallery.ui.RemainingMediaModals1762Test.narrowLightTwoTimesText` | java.lang.AssertionError: Menu sample must be opaque, not an isolated transparent glyph |
| `uk.co.traynor.privategallery.ui.RemainingMediaModals1762Test.shortLandscapeLightTwoTimesText` | java.lang.AssertionError: Menu sample must be opaque, not an isolated transparent glyph |
| `uk.co.traynor.privategallery.ui.RemainingMediaModals1762Test.shortLandscapeDarkTwoTimesText` | java.lang.AssertionError: Menu sample must be opaque, not an isolated transparent glyph |
| `uk.co.traynor.privategallery.ui.RemainingMediaModals1762Test.narrowDarkTwoTimesText` | java.lang.AssertionError: Menu sample must be opaque, not an isolated transparent glyph |
| `uk.co.traynor.privategallery.ui.BrowserModalSurfaces1762Test.coverDarkBusyVideoKeepsExplicitCancelWhenBackCannotDismissProgress` | androidx.test.espresso.base.RootViewPicker$RootViewWithoutFocusException: Waited for the root of the view hierarchy to have window focus and not request layout for 10 seconds. If you specified a non default root matcher, it may be picking a root that never takes focus. Root: |
| `uk.co.traynor.privategallery.core.browser.hidden.HiddenUploadPickerPaging1762Test.actualTransferredLastItemBeyondFiveHundredSurvivesCancelReopenAndTypedPages` | java.lang.AssertionError: Actionable Cancel |
| `uk.co.traynor.privategallery.ui.ModalCompletion1762Test.landscapeDarkSourcePickerCanReachLastOfLargeVaultAtLargeText` | androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 10000 ms |
| `uk.co.traynor.privategallery.core.browser.hidden.NativeTorIsolation1762Test.packagedTorNativeFormsUploadsDownloadsAndNetworkSink` | java.lang.AssertionError: Native JS-free form body |
| `uk.co.traynor.privategallery.core.browser.hidden.HiddenLibraryParcelPressure1762Test.maximalLibraryTraversesActualClaimedChildBinderWithoutTransactionTooLarge` | java.lang.AssertionError: Explicit authorized Done acknowledges supported profile clearing |
| `uk.co.traynor.privategallery.core.browser.hidden.HiddenRuntimeFailureTest.revokedNativeAuthorityClosesStalledUnixPeerAndDeniesReuse` | java.util.concurrent.TimeoutException |
| `uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnLive1762Test.nativeChildPostsFormsUploadsAndRevokesOnOwnedVpn` | java.lang.IllegalStateException: Previous browser process is still alive |

## Disposition

Seven external-provider cases failed before the relevant UI/validation assertions because the synthetic provider addressed the production package instead of the isolated debug suffix. Corrected in9f6d031; these failures are not evidence that the delayed-result boundary passed or that its lifecycle regression reached RED. A narrow serial diagnostic40758245611 uses the corrected provider and can never approve release.

Four menu failures concern alpha in the captured menu-content layer; actual popup-root capture and diagnostic pixels are being corrected without weakening thresholds. Busy-video Back selected an unfocused root; its replacement uses real OS Back in the focused owned dialog. The source-picker landscape timeout is under layout investigation, with missing original failure screenshot acknowledged.

Native proxy revocation showed blocked Unix reads surviving close and requires shutdown before descriptor close. Native form/picker accepted accessibility actions without observed POST/picker transition; actual touch and retained backend/body assertions are being investigated. The library Done test clicked identically named controls without waiting for dialog removal. A denied cold child launch also left an Activity record and caused repeated process resurrection, cascading into native-live startup rejection; hard termination must finish its Activity record while retaining unconditional kill. All proposed changes require new Android evidence and independent source review.
