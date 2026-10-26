"""Preserve exact prior platform/upgrade gates and require all new release regressions."""
import json,sys
from pathlib import Path
from verify_release1751_platforms import verify as verify_existing
from verify_release176_platforms import REQUIRED as PRIOR176
from verify_professional_ui_results import verify as verify_named
from verify_release175_evidence import count_reports
REQUIRED=PRIOR176+['uk.co.traynor.privategallery.core.media.ExternalEditorIntents1761Test#editorChooserUsesImplicitEditAndReadOnlyContentGrant', 'uk.co.traynor.privategallery.core.media.ExternalEditorIntents1761Test#publicGalleryFallbackUsesViewWithoutClaimingDirectEditingOrSend', 'uk.co.traynor.privategallery.core.media.ExternalEditorIntents1761Test#fileAndNetworkUrisCannotEnterEditingChooser', 'uk.co.traynor.privategallery.core.media.ExternalPhotoExport1761Test#galleryFallbackPublishesOnlyVerifiedGenericCopyAndRetainsPrivateLease', 'uk.co.traynor.privategallery.core.media.ExternalPhotoExport1761Test#revokedAuthorityAndNonLeaseFilesNeverPublish', 'uk.co.traynor.privategallery.ui.SettingsPolish1761Test#decoyAboutRestoresGenuineVersionOnlyAfterAuthenticatedReveal', 'uk.co.traynor.privategallery.ui.SettingsPolish1761Test#panicConfigurationIsAbsentFromSecretWhileHideContentAccessRemains', 'uk.co.traynor.privategallery.ui.SettingsPolish1761Test#primaryScreenshotEnableStillRequiresFreshAuthenticationInSecurity', 'uk.co.traynor.privategallery.HideContent1761Test#concealedTabsRestartAndPanicRetainFlagWithoutDeletingSyntheticState', 'uk.co.traynor.privategallery.ui.PhotoEditorUiTest#externalEditorEntryCarriesOriginInBothVaults', 'uk.co.traynor.privategallery.ExternalEditingLifecycle176Test#explicitEditorReturnRenewsOnlyUiAndOldHiddenIoStaysRevoked', 'uk.co.traynor.privategallery.ExternalEditingLifecycle176Test#expiredHiddenEditorReturnCannotResumeAgainstPrimaryAuthentication', 'uk.co.traynor.privategallery.ui.ExternalEditingUi176Test#compactEntryKeepsHandoffBehindExplicitConsentAndCanCancel', 'uk.co.traynor.privategallery.ui.ExternalEditingUi176Test#confirmedEditorChoicePreparesOnceAndImportIsExplicit']
REQUIRED += ['uk.co.traynor.privategallery.PrivacyViewerRepair1751Test#panicSettingsProvideDiscoverableSafeTest']
SECURITY_REQUIRED=[
 'uk.co.traynor.privategallery.core.browser.hidden.HiddenTorBootstrapTest#packagedTorBootstrapsInPrivateProcessAndStopsWhenReleased',
 'uk.co.traynor.privategallery.core.browser.hidden.HiddenRuntimeFailureTest#stalledConnectTimeoutDisconnectsRuntimeAndClosesSocket',
 'uk.co.traynor.privategallery.core.browser.hidden.HiddenRuntimeFailureTest#sessionRevocationDuringStalledConnectCancelsRuntimeAndClosesSocket']
def verify(root,source,apk):
 result=verify_existing(root,source,apk,expected_counts={'focused':201,'preflight':76,'full':436},platforms=(('api37','37'),))
 sdk=json.loads((Path(root).parent/'acceptance-diagnostics/android17-sdk.json').read_text())
 if sdk.get('api_level')!=37 or sdk.get('preview') is not False or len(sdk.get('metadata_sha256',''))!=64:raise ValueError('Installed Android17 SDK receipt invalid')
 result['debug_upgrade_restart']='Android17 passed'
 result['jvm']=count_reports(Path(root).parent/'test-results/testDebugUnitTest',354)
 for platform in ('api37',):
  evidence=Path(root)/platform
  changed=Path(root).parent/'acceptance-diagnostics/changed1761'/platform
  if (changed/'source-sha.txt').read_text().strip()!=source:raise ValueError('Changed-source mismatch')
  count_reports(changed/'results',101)
  verify_named(changed/'results',SECURITY_REQUIRED)
  for reports in (evidence/'full-results',evidence/'diagnostics/hidden-browser-preflight'):verify_named(reports,SECURITY_REQUIRED)
  if (changed/'android-release.txt').read_text().strip().split('.')[0]!='17':raise ValueError('Android release mismatch')
  observations='\n'.join(p.read_text(errors='replace') for p in (changed/'results').rglob('logcat-*HiddenTorBootstrapTest-*.txt'))
  if 'phase=subscribed' not in observations or 'readerAlive=false' not in observations or 'readerAlive=true' in observations:raise ValueError('Tor control observer not verified')
  for reports in (evidence/'full-results',evidence/'diagnostics/professional-ui/results'):verify_named(reports,REQUIRED)
 result['named1761_regressions']='all required cases passed in focused and unfiltered suites on Android17'
 return result
if __name__=='__main__':print(json.dumps(verify(*sys.argv[1:]),indent=2))
