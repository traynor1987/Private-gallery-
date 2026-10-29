#!/usr/bin/env python3
"""Exact affected final-boundary gate; unchanged-scope evidence is reviewed separately."""
import re
import sys
from pathlib import Path
from verify_selected_android1762 import verify

# Owner minimal-CI directive: no unrelated305/307-case rerun. These existing actual
# scenarios cover the final native upload/transport changes and enabled Main entry.
required = {
    ('uk.co.traynor.privategallery.core.vault.RecentlyDeleted177Test', 'restoreStopsWhenAuthorityEndsDuringPayloadVerification'),
    ('uk.co.traynor.privategallery.core.vault.RecentlyDeleted177Test', 'deletionInterruptedAfterIndexCommitReconcilesPayloadAndPreviewOnly'),
    ('uk.co.traynor.privategallery.core.vault.RecentlyDeleted177Test', 'expiryDoesNotRemoveAnActiveImportStagingFile'),
    ('uk.co.traynor.privategallery.ui.RecentlyDeletedUi177Test', 'emptyRequiresConfirmationAndBulkRestoreStaysInOriginCallbacks'),
    ('uk.co.traynor.privategallery.core.vault.RecentlyDeleted177Test', 'corruptPrimaryRestoreRetainsTrashAndCiphertext'),
    ('uk.co.traynor.privategallery.core.vault.RecentlyDeleted177Test', 'interruptedPrimaryDeletionRestoresRetainedRecordAndRestoreIsIdempotent'),
    ('uk.co.traynor.privategallery.core.hidden.HiddenRecentlyDeleted177Test', 'corruptHiddenRestoreRetainsTrashAndRejectsLockedAccess'),
    ('uk.co.traynor.privategallery.core.hidden.HiddenRecentlyDeleted177Test', 'independentRetentionRestoreMembershipAndThumbnailRecovery'),
    ('uk.co.traynor.privategallery.PrimaryGalleryDeviceRepair1762Test', 'seededPrimaryGallerySurvivesHiddenNavigationAndRevokesOnLock'),
    ('uk.co.traynor.privategallery.ui.ExternalEditingUi176Test', 'returnedWithoutResultExposesGuidedExportWithSeparateConsent'),
    ('uk.co.traynor.privategallery.ExternalEditingLifecycle176Test', 'oldHiddenOperationCannotReviveAfterConcealmentAndExplicitReopen'),
    ('uk.co.traynor.privategallery.ExternalEditingLifecycle176Test', 'hiddenResultNeverFallsBackToPrimaryAndRejectedBytesAreWiped'),
    ('uk.co.traynor.privategallery.ExternalEditingLifecycle176Test', 'panicRevokesPendingExternalTicket'),
    ('uk.co.traynor.privategallery.ExternalEditingLifecycle176Test', 'explicitEditorReturnRenewsOnlyUiAndOldHiddenIoStaysRevoked'),
    ('uk.co.traynor.privategallery.ExternalEditingLifecycle176Test', 'expiredHiddenEditorReturnCannotResumeAgainstPrimaryAuthentication'),
    ('uk.co.traynor.privategallery.core.browser.hidden.HiddenUploadPickerPaging1762Test', 'actualTransferredLastItemBeyondFiveHundredSurvivesCancelReopenAndTypedPages'),
    ('uk.co.traynor.privategallery.core.browser.hidden.NativeTorIsolation1762Test', 'packagedTorNativeFormsUploadsDownloadsAndNetworkSink'),
    ('uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnLive1762Test', 'nativeChildPostsFormsUploadsAndRevokesOnOwnedVpn'),
    ('uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnNetworkLoss1762Test', 'lostOwnedNetworkRetiresNativeChildAndCannotReuseOldCapability'),
    ('uk.co.traynor.privategallery.HiddenMainBrowserLifecycle1762Test', 'realMainToNativeChildThenHomeAppliesIndependentLockPolicies'),
    ('uk.co.traynor.privategallery.HiddenMainBrowserLifecycle1762Test', 'realMainVpnTorVpnSwitchRetiresOldProcessAndDoesNotRestoreCookies'),
}

selectors = re.search("classes='([^']+)'", Path('scripts/run_focused1762.sh').read_text()).group(1)
selected = set()
for selector in selectors.split(','):
    name, separator, method = selector.partition('#')
    source = Path('app/src/androidTest/java').joinpath(*name.split('.')).with_suffix('.kt')
    methods = set(re.findall(r'@Test(?:\([^)]*\))?\s+fun\s+(\w+)', source.read_text()))
    if separator:
        if method not in methods:
            raise SystemExit('Selected method is absent from source: ' + selector)
        selected.add((name, method))
    else:
        selected.update((name, method) for method in methods)
if selected != required:
    raise SystemExit(f'Final affected boundary selection differs: missing={required-selected}, extra={selected-required}')
count = verify(Path(sys.argv[1]), selectors)
print(f'PASS: {count} exact affected native/Main cases, once each, zero failures/skips; protocol evidence and consolidated scope/security approval remain separate mandatory gates')
