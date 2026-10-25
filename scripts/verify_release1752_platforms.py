"""Preserve all1751 gates and require named1752 regressions before signing."""
import json,sys
from pathlib import Path
from verify_release1751_platforms import verify as verify_existing
from verify_professional_ui_results import verify as verify_named
REQUIRED=[
 'uk.co.traynor.privategallery.ui.BrowserConsent1752Test#lightDelayedConsentRendersAndRequiresManualCheckbox',
 'uk.co.traynor.privategallery.ui.BrowserConsent1752Test#darkTransformedConsentRendersAndRemainsInteractive',
 'uk.co.traynor.privategallery.ui.BrowserConsent1752Test#wrapContentReferenceReproducesCollapsedViewportHeights',
 'uk.co.traynor.privategallery.ui.HiddenVaultIntegrationTest#stopOnHiddenGridMustNotConcealNewlySelectedPhoto',
 'uk.co.traynor.privategallery.ui.HiddenVaultIntegrationTest#stopWithHiddenPhotoOpenMustKeepRetainedSelectionConcealed',
 'uk.co.traynor.privategallery.PrivacyViewerRepair1751Test#freshPhotoSelectionAfterGridBackgroundAcceptsCurrentPrivacyEpoch',
]
def verify(root,source,apk):
 result=verify_existing(root,source,apk,expected_counts={"focused":134,"preflight":74,"full":401})
 for platform in ('api36','api37'):
  evidence=Path(root)/platform
  for reports in (evidence/'full-results',evidence/'diagnostics/professional-ui/results'):
   verify_named(reports,REQUIRED)
 result['named1752_regressions']='all required cases passed in focused and unfiltered suites on both SDKs'
 return result
if __name__=='__main__':print(json.dumps(verify(sys.argv[1],sys.argv[2],sys.argv[3]),indent=2))
