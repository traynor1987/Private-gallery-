"""Preserve exact prior platform/upgrade gates and require all new release regressions."""
import json,sys
from pathlib import Path
from verify_release1751_platforms import verify as verify_existing
from verify_release1752_platforms import REQUIRED as PRIOR
from verify_professional_ui_results import verify as verify_named
from verify_release175_evidence import count_reports
REQUIRED=PRIOR+[
 'uk.co.traynor.privategallery.ui.MediaSelection176Test#draggingAcrossVisibleThumbnailsSelectsStableRangeWithoutReadingPayloads',
 'uk.co.traynor.privategallery.ExternalEditingLifecycle176Test#oldHiddenOperationCannotReviveAfterConcealmentAndExplicitReopen',
 'uk.co.traynor.privategallery.ExternalEditingLifecycle176Test#hiddenResultNeverFallsBackToPrimaryAndRejectedBytesAreWiped',
 'uk.co.traynor.privategallery.ExternalEditingLifecycle176Test#panicRevokesPendingExternalTicket',
 'uk.co.traynor.privategallery.core.media.ExternalEditing176Test#handoffProviderIsReadOnlyExpiresAndNeverPublishesVaultPaths',
 'uk.co.traynor.privategallery.ui.ExternalEditingUi176Test#latePreviewForPreviousSelectedResultIsWipedAndRecycled',
 'uk.co.traynor.privategallery.ui.ExternalEditingUi176Test#replaceRequiresExplicitConfirmationAfterPreview',
 'uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccessTest#journalResumesCommitBeforeVerificationWithoutCreatingDuplicate',
 'uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccessTest#journalResolvesSourceAlreadyTrashedAfterMoveCommit',
 'uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccessTest#journalCancellationSurvivesNewAccessAndRetainsRemainingOriginals',
 'uk.co.traynor.privategallery.core.hidden.HiddenRestoreAccessTest#hiddenMoveResumesVerifiedCopyWithFreshAuthenticationWithoutDuplicate',
 'uk.co.traynor.privategallery.core.hidden.HiddenRestoreAccessTest#closingHistoryOwnerSynchronouslyRevokesReverseResume',
 'uk.co.traynor.privategallery.core.hidden.HiddenRestoreAccessTest#reverseCancellationAtRemovalBoundaryPreservesOriginal',
 'uk.co.traynor.privategallery.core.hidden.HiddenRestoreAccessTest#collectionCoversPinsAndBulkMembershipRemainIndependentAcrossRestart',
 'uk.co.traynor.privategallery.core.hidden.HiddenRestoreAccessTest#externalEditedResultSaveAsNewAndReplaceStayInHiddenAndVerifyBytes',
 'uk.co.traynor.privategallery.core.hidden.HiddenRestoreAccessTest#externalPrimaryResultReplacementPreservesIndependentHiddenOriginal',
 'uk.co.traynor.privategallery.ui.MediaSelection176Test#dateSelectionIncludesBothLocalBoundariesAndNeverAddsOutsideScope',
 'uk.co.traynor.privategallery.ui.MediaSelection176Test#sizeSelectionUsesMetadataAndStrictPresetBoundary',
]
def verify(root,source,apk):
 result=verify_existing(root,source,apk,expected_counts={'focused':187,'preflight':74,'full':420})
 result['jvm']=count_reports(Path(root).parent/'test-results/testDebugUnitTest',351)
 for platform in ('api36','api37'):
  evidence=Path(root)/platform
  for reports in (evidence/'full-results',evidence/'diagnostics/professional-ui/results'):verify_named(reports,REQUIRED)
 result['named176_regressions']='all required cases passed in focused and unfiltered suites on both SDKs'
 return result
if __name__=='__main__':print(json.dumps(verify(*sys.argv[1:]),indent=2))
