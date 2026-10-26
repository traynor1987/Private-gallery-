"""Strict source/APK-bound positive and negative codec-control evidence."""
import hashlib,json,sys,xml.etree.ElementTree as E
from pathlib import Path
CONTROL='uk.co.traynor.privategallery.core.media.PrivateVideoPlayerTest#protectedMemoryPlaybackAndCleanup'
DEFAULT_MARKERS=['c2.goldfish.h264.decoder','avc:  denied  { read write }','scontext=u:r:mediacodec:s0','path=2F6D656D66643A4D6573736167655175657565202864656C6574656429','tclass=memfd_file permissive=0','Operation not permitted','queueCSDInputBuffer failed w/ error 14']
REQUIRED=['uk.co.traynor.privategallery.core.media.PrivateVideoPlayerTest#measuredDecryptionModalShowsPercentageAndCancel', 'uk.co.traynor.privategallery.core.media.PrivateVideoPlayerTest#realEncryptedPayloadPlaysThroughProtectedViewer', 'uk.co.traynor.privategallery.core.media.PrivateVideoPlayerTest#changingLifecycleOwnerDoesNotReleaseRetainedPlayer', 'uk.co.traynor.privategallery.core.media.PrivateVideoPlayerTest#pauseResumeWithoutStopExposesRecoveryForStoppedNetworkPlayer', 'uk.co.traynor.privategallery.core.media.PrivateVideoPlayerTest#speedSelectionAndInMemoryResumeUseActualPlayer', 'uk.co.traynor.privategallery.core.media.PrivateVideoPlayerTest#surfaceDoubleTapsSeekActualMemoryPlayerAtBothEdges', 'uk.co.traynor.privategallery.core.media.PrivateVideoPlayerTest#protectedPageReuseLoadsFreshBytesAndPlaysAgain', 'uk.co.traynor.privategallery.core.media.PrivateVideoPlayerTest#closingProtectedViewerCancelsPendingDecryption', 'uk.co.traynor.privategallery.core.media.PrivateVideoPlayerTest#systemBackClosesDedicatedPlayer', 'uk.co.traynor.privategallery.core.media.PrivateVideoPlayerTest#protectedMemoryPlaybackAndCleanup', 'uk.co.traynor.privategallery.core.media.PrivateVideoPlayerTest#galleryContentUriPlaybackAndCleanup', 'uk.co.traynor.privategallery.PrivacyShield175Test#panicRevokesBothSessionsWipesKeyAndNeverReopensHidden', 'uk.co.traynor.privategallery.PrivacyShield175Test#videoCompletingUnderOwnedDialogCannotAutoplayOrCover', 'uk.co.traynor.privategallery.PrivacyShield175Test#panicDuringRealPlayerPlaybackStopsImmediatelyAndRevokesSession', 'uk.co.traynor.privategallery.PrivacyShield175Test#repeatedPanicOnAlreadyNeutralEntryEventuallyUncovers', 'uk.co.traynor.privategallery.PrivacyShield175Test#actualBackgroundStopCoversContentAndEnforcesSecureWindow', 'uk.co.traynor.privategallery.ui.MediaDetails174Test#nativeDateAndTimePickersFollowOwnerScreenshotPolicy', 'uk.co.traynor.privategallery.ui.MediaDetails174Test#restoreOriginalDateRefreshesDetailsWithoutChangingMediaHash', 'uk.co.traynor.privategallery.EncryptedVideoContinuity1751Test#shortEncryptedVideoAdvancesBeforePanic', 'uk.co.traynor.privategallery.EncryptedVideoContinuity1751Test#primaryEncryptedSessionSurvivesBufferingControlsAndRecomposition', 'uk.co.traynor.privategallery.EncryptedVideoContinuity1751Test#hiddenEncryptedSessionSurvivesBufferingControlsAndRecomposition', 'uk.co.traynor.privategallery.EncryptedVideoContinuity1751Test#boundedMemoryFallbackKeepsOnePlayerAcrossRecomposition', 'uk.co.traynor.privategallery.core.vault.VaultVideoDataSourceTest#media3SeeksAcrossAuthenticatedChunksAndStopsOnLock', 'uk.co.traynor.privategallery.core.vault.VaultVideoDataSourceTest#revocationDuringReadRejectsAndWipesDeliveredBytes', 'uk.co.traynor.privategallery.core.vault.VaultVideoDataSourceTest#sessionCloseRetiresLiveReaderKeysAndCachedPlaintext', 'uk.co.traynor.privategallery.core.media.AdaptiveVideoPlaybackTest#hlsManifestAndSegmentPrepareThroughNetworkGate', 'uk.co.traynor.privategallery.core.media.AdaptiveVideoPlaybackTest#dashManifestAndSegmentsPrepareThroughNetworkGate']

def cases(directory):
 reports=sorted(Path(directory).rglob('TEST-*.xml'))
 if not reports:raise ValueError('Missing native XML')
 result={}
 for report in reports:
  for c in E.parse(report).getroot().iter('testcase'):
   key=c.get('classname')+'#'+c.get('name')
   if key in result:raise ValueError('Duplicate testcase')
   result[key]=c
 return result

def verify(root,source,apk):
 root=Path(root);digest=hashlib.sha256(Path(apk).read_bytes()).hexdigest()
 for label in ('default','software'):
  d=root/label
  for name,wanted in [('source-sha.txt',source),('sdk.txt','37'),('android-release.txt','17'),('selinux.txt','Enforcing'),('selinux-after.txt','Enforcing'),('apk-sha256.txt',digest)]:
   if (d/name).read_text().strip()!=wanted:raise ValueError(label+' mismatched '+name)
 default=root/'default';positive=root/'software'
 if (default/'command-exit-status.txt').read_text().strip()=='0':raise ValueError('Default negative control did not reproduce')
 failed=cases(default)
 if set(failed)!={CONTROL} or failed[CONTROL].find('failure') is None or failed[CONTROL].find('skipped') is not None:raise ValueError('Wrong negative control')
 logs=(default/'logcat.txt').read_text(errors='replace')
 if any(m not in logs for m in DEFAULT_MARKERS):raise ValueError('Original codec/permission failure chain absent')
 if (default/'avcdec.txt').read_text().strip()!='2':raise ValueError('Default hardware codec configuration absent')
 if (positive/'command-exit-status.txt').read_text().strip()!='0':raise ValueError('Positive command failed')
 actual=cases(positive)
 if set(actual)!=set(REQUIRED):raise ValueError('Missing or substituted positive case')
 if any(c.find(x) is not None for c in actual.values() for x in ('failure','error','skipped')):raise ValueError('Nonpassing positive testcase')
 logs=(positive/'logcat.txt').read_text(errors='replace')
 if 'c2.android.avc.decoder' not in logs or 'c2.goldfish.h264.decoder' in logs or 'tclass=memfd_file permissive=0' in logs:raise ValueError('Software decoder not independently observed or permission failure persists')
 if (positive/'avcdec.txt').read_text().strip() not in ('','0'):raise ValueError('Unexpected software codec configuration')
 return {'source_sha':source,'apk_sha256':digest,'passing_cases':len(actual),'default_failure_reproduced':True,'selinux':'Enforcing','physical_hardware_decoder_acceptance':'pending'}
if __name__=='__main__':print(json.dumps(verify(*sys.argv[1:]),indent=2))
