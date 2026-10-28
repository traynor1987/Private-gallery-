# Browser Vault picker audit

Read-only source investigation of private-gallery HEAD 5b6be831; no implementation edits or device execution. Paths below are relative to the repository.

## Conclusion

No evidence establishes a remaining cross-vault storage/index bug that omits a successfully transferred, unrestricted, COMPLETE Primary item from a fresh Choose from Vault query. Do not relax vault ownership or vaultOnly filtering to address this report. There is a concrete thumbnail ownership defect and a callback-completion edge case, described below. Neither proves the reported missing-record symptom.

## Confirmed thumbnail defect

`ui/BrowserVaultUploadTile.kt` uses `DisposableEffect(item.id)` and recycles both a late delivered bitmap and the displayed bitmap on disposal. `MainActivity.kt:2283` `loadPreview` returns the exact `previewMemory` Bitmap on a cache hit, and lines 2364 onward insert newly decoded bitmaps into that same cache before delivering them. The tile therefore does not own these bitmaps. Closing the picker, scrolling a lazy tile out of composition, or a late callback can recycle a bitmap still retained by previewMemory and used by another view. Reopening retrieves this recycled bitmap without an `isRecycled` guard. This can break thumbnail drawing (potential recycled-bitmap rendering exception), but does not remove an item from ui.vaultItems.

Recommendation: establish explicit ownership. Either provide a separate caller-owned bitmap copy to this tile and retain recycling, or treat the supplied bitmap as borrowed and never recycle it in the tile. Add a regression exercising open -> preview -> close -> reopen and lazy scroll with cache reuse, checking bitmap usability and no renderer exception. Do not simply catch rendering errors.

## Callback completion edge case

`MainActivity.kt:2030` loadItems returns without invoking onLoaded when sessionKey is null. BrowserV2Home openVaultPicker sets vaultPickerLoading=true and clears it only in its callback (lines 309-323). A request accepted during loss of authority can remain loading until closed. Normal route locking may conceal this, so this is an observable code-level contract gap rather than a reproduced ordinary-unlocked failure. loadPreview has a similar early return at line 2285. Return an explicit unavailable result or empty list and complete callbacks exactly once. Also gate asynchronous delivery by the captured authority/session identity; loadItems currently only checks hideContent at completion, unlike loadPreview.

## Data path and expected exclusions

- `core/hidden/PrimaryCopyAccess.kt:174-197`: Primary-to-Hidden decrypts through a source lease, imports encrypted destination content, verifies size/hash, records VERIFIED, then optionally trashes the exact Primary source. An ordinary copy leaves Primary intact; a move intentionally removes it from Primary picker eligibility. Hidden destination media must not be queried by Primary browser.
- `core/hidden/HiddenRestoreAccess.kt:64-83`: Hidden-to-Primary uses a fresh authenticated Primary key, creates a distinct imported destination with origin and vaultOnly copied, verifies destination payload/hash, then optionally trashes Hidden source. Destination ownership uses Primary repository root and encryption key, not Hidden identity.
- `core/vault/AndroidVaultRepository.kt:54-55,566`: items reads an encrypted snapshot from disk each call and removes TRASHED; there is no stale in-memory item index to invalidate. BrowserV2Home:309-323 reloads on each picker opening. COMPLETE filtering is applied in the picker at line 319.
- `MainActivity.kt:2946-2947`: Primary browser supplies no items or thumbnails while hiddenState.selected. Preserve this separation.
- `ui/BrowserV2Home.kt:319`: !vaultOnly is intentional. Restrictions survive both transfer directions. Do not remove that filter or clear the flag on transfer. `core/hidden/HiddenRestoreAccessTest.kt` explicitly verifies restricted round trips.
- `core/browser/v2/BrowserAcceptTypes.java`: common extensions, comma lists, case normalization, MIME wildcards and unspecified accept work. Matching is a website constraint, so a PNG transfer must remain absent from JPEG-only input. Unknown extension tokens remain unmatched; no specific user format is supplied to establish a missing mapping. `safeName` maps HEIC/HEIF to .bin despite accepts recognizing them, a possible downstream website compatibility gap, not list filtering evidence.
- `core/hidden/HiddenRestoreAccessTest.kt:61`: transferredEligibleItemsAreImmediatelyVisibleToFreshPrimaryPickerQuery tests a real repository transfer with fresh Primary key and .png accept. This is not actual browser/WebView UI coverage.

## Authorization coverage and gaps

| Direction | Existing source evidence | Remaining coverage needed |
|---|---|---|
| Primary -> Hidden, correct/wrong | PrimaryCopyAccessTest freshPinMultiPhotoVideoDuplicateAndRestartPreservePrimaryExactly; PrimaryCopyDialogTest sourceChoiceFreshPinSelectionCopyAndReturnStayInHidden enters wrong then correct Primary PIN | Full MainActivity flow including move and browser picker state after return to Primary |
| Primary -> Hidden, cancel | PrimaryCopyAccessTest hundredsOfSelectionsReportActualProgressAndSafeCancellation, journalCancellationSurvivesNewAccessAndRetainsRemainingOriginals | UI cancel at credential entry and while authentication is completing; verify no late publication |
| Primary -> Hidden, lock/expiry | PrimaryCopyAccessTest hiddenLockStopsBatchAndOldSourceCannotInheritNewUnlock, backgroundRevokesTransferEvenWithinHiddenGrace, primaryRevocationBetweenItemsStopsBatchAndPreservesCompletedCopy | Explicit timeout/expiry through MainActivity both before and after PIN success, then picker reopen |
| Hidden -> Primary, correct/wrong | HiddenRestoreAccessTest primaryCopyReencryptsAndRetainsHiddenAcrossRepositoryRestart and missingAndWrongPrimaryAuthenticationNeverPublishesOrDeletes | HiddenRestoreDialogTest uses synthetic id and only opens credential UI; add wrong PIN -> correct PIN -> real copy/move UI with Primary destination checks |
| Hidden -> Primary, cancel | HiddenRestoreDialogTest choicesAreExplicitAndPrimaryRequiresFreshPinWithoutChangingHidden tests credential Cancel; access tests cancellationAndRevocationPreserveSources and reverseCancellationAtRemovalBoundaryPreservesOriginal | Full UI cancel during async authentication and transfer, with late-callback checks |
| Hidden -> Primary, lock/expiry | HiddenRestoreAccessTest lockAfterVerificationPreventsSourceRemoval, closingHistoryOwnerSynchronouslyRevokesReverseResume; HiddenRestoreDialogTest lock test is PHONE move | Primary-destination dialog lock and explicit expiry during credentials, authentication, transfer and return to Primary |

General HiddenVaultIntegrationTest wrong PIN/background and expiredBackgroundGrace tests are useful but do not close these cross-vault UI cases. Source tests were inspected, not run; their presence is not a passing execution claim.

## Minimal evidence to collect before another picker fix

Use disposable media and record only non-sensitive counters and flags: transfer direction/result, Primary query count, COMPLETE count, vaultOnly exclusion count, accept exclusion count, actual accept tokens, picker generation, callback invocation and returned count. Reproduce copy and move separately. After a successful Hidden-to-Primary transfer of unrestricted PNG, authenticate Primary, open a site input accepting image/* or .png, confirm real tile and upload callback, reopen picker, and scroll. Repeat with a deliberately restricted fixture to confirm absence is intentional. Do not log names, paths, hashes, bytes, thumbnails, keys or Hidden metadata into Primary diagnostics.
