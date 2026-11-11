#!/usr/bin/env python3
"""Validate downloaded original CI evidence and exact-source independent signing approval.

The future workflow must fetch run/jobs/artifact through GitHub's authenticated API before
calling this tool and before exposing any signing environment. This tool has no network,
signing, install, publishing or credential access.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[1]
BRANCH='feature/novaos-sanctuary-integration'
BASELINE='c818c670baa4b980708dc2effb1ab2ab0331ade1'
CONTROLS=('compile_upgrade1711_fixture.sh','verify_compiled_upgrade1711.py',
          'verify_upgrade1711_baseline.py','verify_production_candidate1711.sh',
          'verify_synthetic_upgrade1711.sh','verify_signed_upgrade1711.py',
          'release_configuration1711.sh','verify_release1711_gate.py')
REQUIRED_STEPS={'Build isolated test identity and run JVM checks',
                'Verify reused Home policies and encrypted adapter',
                'Verify browser engine preservation and isolated APK',
                'Focused Android 17 integration and privacy tests',
                'Validate every selected original Android report',
                'Validate actual Backup process restart evidence'}

def sha(raw): return hashlib.sha256(raw).hexdigest()
def require(ok,message):
    if not ok: raise ValueError(message)
def controls(root=ROOT): return {name:sha((root/'scripts'/name).read_bytes()) for name in CONTROLS}
def controls_digest(value): return sha(json.dumps(value,sort_keys=True,separators=(',',':')).encode())
def source_ok(source): return isinstance(source,str) and re.fullmatch('[a-f0-9]{40}',source) is not None and source!='0'*40
def digest_ok(value): return isinstance(value,str) and re.fullmatch('[a-f0-9]{64}',value) is not None and value!='0'*64

def restart_cases(files,source):
    proof=json.loads(files['receipt.json'])
    require(proof.get('schema')==1 and proof.get('source')==source,'Restart evidence source differs')
    token=proof.get('token');require(isinstance(token,str) and re.fullmatch('[a-f0-9]{32}',token),'Restart token invalid')
    expected={owner+'-'+boundary for owner in ('primary','hidden') for boundary in ('staging','uncommitted','committed','verified')}
    phases=proof.get('phases',[]);require(len(phases)==8 and {p.get('name') for p in phases}==expected,'Missing or duplicate restart phase')
    require(set(files)=={'receipt.json'}|{name+suffix for name in expected for suffix in ('-prepare.txt','-verify.txt')},'Unexpected or missing restart evidence')
    for phase in phases:
        name=phase['name'];owner,boundary=name.split('-',1)
        before=files[name+'-prepare.txt'].decode();after=files[name+'-verify.txt'].decode()
        def observation(text,label):
            rows=re.findall(r'^INSTRUMENTATION_STATUS: '+label+r'=(.+)$',text,re.M)
            require(len(rows)==1,'Missing or duplicate process observation')
            value=json.loads(rows[0]);require(value.get('token')==token and value.get('owner')==owner and value.get('boundary')==boundary,'Process phase identity differs')
            return value
        ready=observation(before,'nova62_ready');verified=observation(after,'nova62_verified')
        require(type(ready.get('pid')) is int and ready['pid']>0 and phase.get('old_pid')==ready['pid'],'Original PID differs')
        require(phase.get('kill_exit')==0 and phase.get('absent_after_kill') is True,'Process was not stopped')
        require('OK (' not in before and 'shortMsg=Process crashed' in before,'Prepared process was not killed')
        require(verified.get('oldPid')==ready['pid'] and type(verified.get('pid')) is int and verified['pid']>0 and verified['pid']!=ready['pid'],'Recovery did not use a new process')
        require(after.count('OK (1 test)')==1 and 'INSTRUMENTATION_CODE: -1' in after and not any(x in after for x in ('FAILURES!!!','INSTRUMENTATION_FAILED','shortMsg=','FAILURE:')),'Recovery verification failed')
    return len(phases)

def runtime_cases(archive,matrix,source):
    expected={(cls,name) for cls,names in matrix.items() for name in names}
    require(expected and sum(len(names) for names in matrix.values())==len(expected),'Empty or duplicate reviewed runtime matrix')
    seen=set()
    with zipfile.ZipFile(archive) as zipped:
        names=zipped.namelist();require(len(names)==len(set(names)),'Duplicate runtime archive members')
        sources=[name for name in names if name.endswith('home2-evidence/source-sha.txt')]
        require(len(sources)==1 and zipped.read(sources[0]).decode().strip()==source,'Runtime source differs')
        restart_prefixes=[name[:-len('receipt.json')] for name in names if name.endswith('home2-evidence/backup-restart62/receipt.json')]
        require(len(restart_prefixes)==1,'Missing original process restart evidence')
        prefix=restart_prefixes[0]
        restart_cases({name[len(prefix):]:zipped.read(name) for name in names if name.startswith(prefix) and not name.endswith('/')},source)
        groups={'web-scroll-regression-77':1,'wallpaper-return-regression-77':12,'guardian-notification-compatibility-77':2,'guardian-capture-store-77':10,'guardian-inbox-77':2,'guardian-evidence-learning-77':2,'visual-checkpoint':9,'sanctuary-checkpoint':4,'sanctuary-dnd':1,
                'sanctuary-guardian':1,'sanctuary-accessibility':1,'sanctuary-browser':2,
                'redesign621':18,'navigation-hidden':1,'navigation-web':1,'navigation-haptics':1,
                'evidence-collections-68':4,'evidence-ai-68':4,'evidence-dates-68':1,
                'evidence-locker-68':2,'evidence-sanctuary-68':2,
                'evidence-legacy-locker':7,'evidence-legacy-ai':1,'evidence-legacy-reports':2}
        exact = {
            'launcher-workspace-layout-74': [
                ('uk.co.traynor.privategallery.launcher.WorkspaceLayoutStore74Test',
                 'primaryProjectionRequiresCurrentRequestAndCopyIsDurable'),
                ('uk.co.traynor.privategallery.launcher.WorkspaceLayoutStore74Test',
                 'reopeningKeepsMirrorSnapshotAndRestoresBothLauncherDisplays'),
            ],
            'launcher-smart-presentation-73': [
                ('uk.co.traynor.privategallery.launcher.LauncherSmartFolderPresentation73UiTest',
                 'expandedFoldersUseTwoColumnsAndUpdateLiveWithoutChangingSlots'),
            ],
            'launcher-default-entry-73': [
                ('uk.co.traynor.privategallery.launcher.LauncherDefaultHomeEntry73Test',
                 'appIconOffersDefaultHomeAndHonorsTheUsersReminderChoice'),
                ('uk.co.traynor.privategallery.launcher.LauncherDefaultHomeEntry73Test',
                 'appIconSkipsTheReminderDuringSanctuaryProtection'),
            ],
            'launcher-library-pager-73': [('uk.co.traynor.privategallery.launcher.LauncherHomeLibraryPager73UiTest', 'coverGuardianIsLeftOfHomeAndDoesNotExposeAnInboxOrDock'), ('uk.co.traynor.privategallery.launcher.LauncherHomeLibraryPager73UiTest', 'foldGuardianDisplaysTheRealPanicSnapshotAndCanBeDisabled'), ('uk.co.traynor.privategallery.launcher.LauncherHomeLibraryPager73UiTest', 'landscapeGuardianRemainsReachableWithLargeText'), ('uk.co.traynor.privategallery.launcher.LauncherHomeLibraryPager73UiTest', 'coverSwipesAcrossEveryHomePageBeforeLibraryAndBack'), ('uk.co.traynor.privategallery.launcher.LauncherHomeLibraryPager73UiTest', 'foldSwipesBetweenItsLastHomePageAndLibrary'), ('uk.co.traynor.privategallery.launcher.LauncherHomeLibraryPager73UiTest', 'coverBackLeavesLibraryForLastHomePageAndDrawerStillOpens'), ('uk.co.traynor.privategallery.launcher.LauncherHomeLibraryPager73UiTest', 'coverDrawerShortcutOpensPagerLibraryAndBackReturnsToDrawer'), ('uk.co.traynor.privategallery.launcher.LauncherHomeLibraryPager73UiTest', 'coverDragsFromTrailingLibraryOntoLastHomePage'), ('uk.co.traynor.privategallery.launcher.LauncherHomeLibraryPager73UiTest', 'coverHomeVerticalGesturesStillOpenDrawerAndWorkspace'), ('uk.co.traynor.privategallery.launcher.LauncherHomeLibraryPager73UiTest', 'coverDockRemainsStationaryDuringAndAfterSwipeAndLibraryScroll'), ('uk.co.traynor.privategallery.launcher.LauncherHomeLibraryPager73UiTest', 'coverDrawerReturnRevealsDockOverMultipleFrames'), ('uk.co.traynor.privategallery.launcher.LauncherHomeLibraryPager73UiTest', 'coverLibraryHonorsSavedDockVisibilityWithoutShowingItInDrawer'), ('uk.co.traynor.privategallery.launcher.LauncherHomeLibraryPager73UiTest', 'foldNativeArtworkScalesAcrossAllSizesAndKeepsAppLaunchWorking'), ('uk.co.traynor.privategallery.launcher.LauncherHomeLibraryPager73UiTest', 'coverDockLockKeepsNormalLaunchAndBlocksLayoutEdits'), ('uk.co.traynor.privategallery.launcher.LauncherHomeLibraryPager73UiTest', 'coverSanctuaryHidesOnlyRestrictedProfileAndRestoresItsSlots'), ('uk.co.traynor.privategallery.launcher.LauncherHomeLibraryPager73UiTest', 'coverProtectedCardAtLargeTextShowsDeadlineWithoutPreview'), ('uk.co.traynor.privategallery.launcher.LauncherHomeLibraryPager73UiTest', 'foldProtectedCardAtLargeTextShowsDeadlineWithoutPreview'), ('uk.co.traynor.privategallery.launcher.LauncherHomeLibraryPager73UiTest', 'conversationTrainingCardRequiresReviewAndConcealsHeldText')],
            'launcher-smart-tracking-73': [
                ('uk.co.traynor.privategallery.launcher.LauncherSmartTrackingStore73Test',
                 'denyingUsageAccessKeepsThePreviousMode'),
                ('uk.co.traynor.privategallery.launcher.LauncherSmartTrackingStore73Test',
                 'persistsModeAndPrivacyControlsAcrossStoreReopen'),
            ],
            'launcher-default-home-73': [
                ('uk.co.traynor.privategallery.launcher.LauncherDefaultHomeStore73Test',
                 'reminderDefaultsOnAndOptOutSurvivesReopeningAndLayoutRestore'),
            ],
            'launcher-smart-editor-73': [
                ('uk.co.traynor.privategallery.launcher.LauncherSmartFolderEditor73Test',
                 'editsNameCategoryAndManualAppInclusions'),
            ],
            'launcher-smart-folders-73': [
                ('uk.co.traynor.privategallery.launcher.LauncherSmartFolderStore73Test',
                 'legacyPortableLayoutRetainsPlainShortcutsAndManualFolders'),
                ('uk.co.traynor.privategallery.launcher.LauncherSmartFolderStore73Test',
                 'smartFolderImportRejectsNonAppKeysWithoutReplacingExistingLayout'),
                ('uk.co.traynor.privategallery.launcher.LauncherSmartFolderStore73Test',
                 'smartFoldersAndProfileKeysSurviveLocalAndPortableLayouts'),
            ],
            'launcher-library-73': [
                ('uk.co.traynor.privategallery.launcher.LauncherLibrary73UiTest',
                 'coverDragsFromLibraryToHomeAndKeepsTheDrawer'),
                ('uk.co.traynor.privategallery.launcher.LauncherLibrary73UiTest',
                 'coverExpandedOtherCategoryRendersLargeCollectionLazily'),
                ('uk.co.traynor.privategallery.launcher.LauncherLibrary73UiTest',
                 'coverFocusedSearchShowsAlphabeticalListAndJumpsByLetter'),
                ('uk.co.traynor.privategallery.launcher.LauncherLibrary73UiTest',
                 'coverLaunchesTopIconAndOpensGroupForRemainingApps'),
                ('uk.co.traynor.privategallery.launcher.LauncherLibrary73UiTest',
                 'coverShowsPinsAndTrackingStatusWhenRecentsAreOff'),
                ('uk.co.traynor.privategallery.launcher.LauncherLibrary73UiTest',
                 'iconArtworkPreservesIntrinsicAspectAndNativeAdaptiveMask'),
                ('uk.co.traynor.privategallery.launcher.LauncherLibrary73UiTest',
                 'innerUsesAtLeastThreeColumns'),
            ],
            'launcher-gestures-72': [
                ('uk.co.traynor.privategallery.launcher.NovaLauncher72GesturesTest',
                 'drawerHoldAndMoveCanDropOntoAnEmptyHomePage'),
                ('uk.co.traynor.privategallery.launcher.NovaLauncher72GesturesTest',
                 'droppingAtTheDockMovesTheShortcutOffItsHomePage'),
                ('uk.co.traynor.privategallery.launcher.NovaLauncher72GesturesTest',
                 'droppingInTheMiddleOfAnAppGroupsTheTwoIcons'),
                ('uk.co.traynor.privategallery.launcher.NovaLauncher72GesturesTest',
                 'foldExpansionCancelsTheActiveCoverDragWithoutSaving'),
                ('uk.co.traynor.privategallery.launcher.NovaLauncher72GesturesTest',
                 'holdAndMoveReordersDirectlyOnHomeAndSurvivesStoreReopen'),
                ('uk.co.traynor.privategallery.launcher.NovaLauncher72GesturesTest',
                 'holdingNearPageEdgeMovesTheDropToTheNextPage'),
                ('uk.co.traynor.privategallery.launcher.NovaLauncher72GesturesTest',
                 'iconExposesItsContextMenuAsAnAccessibilityAction'),
                ('uk.co.traynor.privategallery.launcher.NovaLauncher72GesturesTest',
                 'rightToLeftInsertionFollowsTheDisplayedOrder'),
                ('uk.co.traynor.privategallery.launcher.NovaLauncher72GesturesTest',
                 'stationaryHoldOpensCompactContextMenu'),
            ],
            'launcher-platform-70': [
                ('uk.co.traynor.privategallery.launcher.NovaLauncherPlatform70Test',
                 'activeSanctuaryBlocksLauncherTouchUntilTheSharedApprovalChanges'),
                ('uk.co.traynor.privategallery.launcher.NovaLauncherPlatform70Test',
                 'appDiscoveryAndLaunchUseAnInstalledPersonalProfileEntry'),
                ('uk.co.traynor.privategallery.launcher.NovaLauncherPlatform70Test',
                 'defaultHomeCanBeSelectedAndPreviousLauncherRestored'),
                ('uk.co.traynor.privategallery.launcher.NovaLauncherPlatform70Test',
                 'deniedNativeWidgetBindingRemovesUnownedHostId'),
                ('uk.co.traynor.privategallery.launcher.NovaLauncherPlatform70Test',
                 'homeRegistrationAndExistingWorkspaceEntryAreSeparate'),
                ('uk.co.traynor.privategallery.launcher.NovaLauncherPlatform70Test',
                 'launcherIgnoresForeignIntentDataAndRecreatesInItsOwnTask'),
                ('uk.co.traynor.privategallery.launcher.NovaLauncherPlatform70Test',
                 'sanctuaryHomeSettingsRequiresPinPhraseAndUninterruptedHold'),
                ('uk.co.traynor.privategallery.launcher.NovaLauncherPlatform70Test',
                 'unresolvedProfileKeyDoesNotOpenThePersonalCopy'),
            ],
            'launcher-sanctuary-controls-70': [
                ('uk.co.traynor.privategallery.launcher.LauncherSanctuaryControls70Test',
                 'coverActiveControlsSendManagementToNovaOsAndKeepEmergencyAvailable'),
                ('uk.co.traynor.privategallery.launcher.LauncherSanctuaryControls70Test',
                 'coverCanStartASelectedModeThroughTheExistingPolicyCallback'),
                ('uk.co.traynor.privategallery.launcher.LauncherSanctuaryControls70Test',
                 'innerShowsCountdownAndPauseStateWithoutReplacingTheMode'),
            ],
            'launcher-storage-70': [
                ('uk.co.traynor.privategallery.launcher.LauncherLayoutStore70Test',
                 'categoryOverridesRetainAppProfileIdentityAcrossStoreReopens'),
                ('uk.co.traynor.privategallery.launcher.LauncherLayoutStore70Test',
                 'cleanupUndoAndWidgetPlacementSurviveRestartWithLegacyWidgetCompatibility'),
                ('uk.co.traynor.privategallery.launcher.LauncherLayoutStore70Test',
                 'iconStyleMigratesAndSurvivesStoreReopenWithoutChangingLayouts'),
                ('uk.co.traynor.privategallery.launcher.LauncherLayoutStore70Test',
                 'layoutAndAppearanceSurviveAStoreReopen'),
                ('uk.co.traynor.privategallery.launcher.LauncherLayoutStore70Test',
                 'legacyStockDuplicateIsRemovedWithoutRecreatingOwnerDockOrWidgets'),
                ('uk.co.traynor.privategallery.launcher.LauncherLayoutStore70Test',
                 'newlyInstalledAppsArePlacedAfterOptInAcrossStoreReopens'),
                ('uk.co.traynor.privategallery.launcher.LauncherLayoutStore70Test',
                 'portableBackupPreservesLocalBindingsAndRejectsMalformedLayouts'),
            ],
            'launcher-ui-70': [
                ('uk.co.traynor.privategallery.launcher.NovaLauncher70UiTest',
                 'approvedOnlyDrawerHidesBlockedSearchResultsWhileProtectionIsActive'),
                ('uk.co.traynor.privategallery.launcher.NovaLauncher70UiTest',
                 'copyWizardExplainsSamsungLayoutLimitBeforePreview'),
                ('uk.co.traynor.privategallery.launcher.NovaLauncher70UiTest',
                 'coverDragReordersShortcutsAndReviewedCleanupCanBeUndone'),
                ('uk.co.traynor.privategallery.launcher.NovaLauncher70UiTest',
                 'coverShowsAndEditsItsOwnPageWithoutChangingInnerLayout'),
                ('uk.co.traynor.privategallery.launcher.NovaLauncher70UiTest',
                 'drawerSearchExposesBlockedAppForPlatformDecisionAndWorkspaceShortcut'),
                ('uk.co.traynor.privategallery.launcher.NovaLauncher70UiTest',
                 'innerShowsAndEditsItsOwnPageWithoutChangingCoverLayout'),
                ('uk.co.traynor.privategallery.launcher.NovaLauncher70UiTest',
                 'wizardPreviewsTemplateBeforeExplicitDefaultHomeSelection'),
            ],
            'launcher-utility-70': [
                ('uk.co.traynor.privategallery.launcher.LauncherUtility70Test',
                 'arcadeCanStartAGameWithoutPrivateHomeShortcuts'),
                ('uk.co.traynor.privategallery.launcher.LauncherUtility70Test',
                 'calculatorUsesOrdinaryLauncherWithoutDiscreetAction'),
                ('uk.co.traynor.privategallery.launcher.LauncherUtility70Test',
                 'storeUsesBundledCatalogueWithoutPrivateAppsOrInstallControls'),
            ],
            'launcher-workspace-70': [
                ('uk.co.traynor.privategallery.LauncherWorkspace70Test',
                 'directPrimaryVaultHidesOnlyInternalNavigationUntilReturningHome'),
                ('uk.co.traynor.privategallery.LauncherWorkspace70Test',
                 'openWorkspaceIntentRevokesRetainedPrimaryAndHiddenAuthority'),
                ('uk.co.traynor.privategallery.LauncherWorkspace70Test',
                 'primaryGalleryHasNoExportedWorkspaceActivity'),
                ('uk.co.traynor.privategallery.LauncherWorkspace70Test',
                 'primaryGalleryIntentRevokesRetainedPrimaryAndHiddenAuthority'),
            ],
            'sanctuary-launcher-policy-70': [
                ('uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy70Test',
                 'androidSettingsAndPhoneStayExemptDuringProtection'),
                ('uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy70Test',
                 'approvalAndRevocationChangeLauncherAndGuardianTogether'),
                ('uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy70Test',
                 'deviceCredentialAuthorisesOnlyTheWorkspacePause'),
                ('uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy70Test',
                 'panicAppliesItsAllowlistAndVisibilityWithoutReplacingSavedPolicy'),
                ('uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy70Test',
                 'persistentShieldBlocksExternalAppAndClearsOnNovaLauncherHome'),
            ],
            'evidence-dashboard-69': [
                ('uk.co.traynor.privategallery.ui.EvidenceDashboardPresentationTest',
                 'busyImportCannotStartAnotherWorkflow'),
                ('uk.co.traynor.privategallery.ui.EvidenceDashboardPresentationTest',
                 'coverLargeTextImportHasAccessibleTargetAndOnlyInvokesImport'),
                ('uk.co.traynor.privategallery.ui.EvidenceDashboardPresentationTest',
                 'landscapeLargeTextSearchAndShortcutsRemainReachable'),
            ],
            'evidence-timeline-69': [
                ('uk.co.traynor.privategallery.ui.EvidenceTimeline69Test',
                 'standaloneScreenshotWithoutMetadataOrOcrAppearsAndOpensOriginal'),
            ],
            'evidence-viewer-69': [
                ('uk.co.traynor.privategallery.ui.EvidenceViewer69UiTest',
                 'coverShowsOriginalAndOwnerApprovalPreservesLegacyOcr'),
                ('uk.co.traynor.privategallery.ui.EvidenceViewer69UiTest',
                 'expandedShowsConversationAndRemembersOwnerRejection'),
            ],
            'navigation-haptics-69': [
                ('uk.co.traynor.privategallery.ui.NovaNavigationHaptics69Test',
                 'bottomRowRequestsOnePlatformKeyEffectOnlyForHomeAndBack'),
                ('uk.co.traynor.privategallery.ui.NovaNavigationHaptics69Test',
                 'headersAndGestureStripUseTheSameEffect'),
                ('uk.co.traynor.privategallery.ui.NovaNavigationHaptics69Test',
                 'settingsTestUsesTheSharedEffectAndRespectsPersistedToggleAndViewPolicy'),
            ],
        }
        for group, identities in exact.items():
            require(len(identities) >= groups.get(group, 1), 'Focused coverage was reduced for '+group)
            groups[group] = len(identities)
        for group,count in groups.items():
            reports=[name for name in names if f'home2-evidence/{group}/reports/' in name and name.endswith('.xml')]
            cases=[case for name in reports for case in ET.fromstring(zipped.read(name)).iter('testcase')]
            require(len(cases)==count and len({(c.get('classname'),c.get('name')) for c in cases})==count,
                    'Missing or duplicate focused cases for '+group)
            if group in exact:
                require({(c.get('classname'),c.get('name')) for c in cases} == set(exact[group]),
                        'Focused test identities changed for '+group)
            require(all(not any(c.find(tag) is not None for tag in ('failure','error','skipped')) for c in cases),
                    'Focused test failed, errored or skipped for '+group)
        prefix = 'home2-evidence/launcher-process-70/'
        required = {prefix+name for name in ('receipt.txt','seed.txt','verify.txt')}
        require(required <= set(names),'Missing original launcher process restart evidence')
        receipt = zipped.read(prefix+'receipt.txt').decode()
        observed = re.fullmatch(r'source='+re.escape(source)+r'\napi=37\nold_pid=([1-9][0-9]*)\nnew_pid=([1-9][0-9]*)\nresult=PASS\n',receipt)
        require(observed is not None and observed.group(1)!=observed.group(2),
                'Wrong launcher process restart source, API, result or PID')
        for name in ('seed.txt','verify.txt'):
            output = zipped.read(prefix+name).decode()
            require(len(re.findall(r'^OK \(1 test\)\s*$',output,re.M))==1
                    and not any(error in output for error in ('FAILURES!!!','INSTRUMENTATION_FAILED','Process crashed','FAILURE:')),
                    'Original launcher process instrumentation failed: '+name)
        for name in names:
            if 'outputs/androidTest-results/' not in name or not name.endswith('.xml'): continue
            for case in ET.fromstring(zipped.read(name)).iter('testcase'):
                identity=(case.get('classname'),case.get('name'))
                require(identity not in seen,'Duplicate original runtime case')
                require(not any(case.find(tag) is not None for tag in ('failure','error','skipped')),'Runtime case failed, errored or skipped')
                seen.add(identity)
    require(seen==expected,'Runtime cases do not match the complete reviewed matrix')
    return len(seen)

def validate(marker,review,run,jobs,artifact,archive,matrix_raw,current_controls,fixture):
    source=marker.get('source');require(source_ok(source),'Unfrozen candidate source')
    require(marker.get('baseline_source')==BASELINE and marker.get('baseline_code')==76 and marker.get('candidate_code')==77,'Wrong upgrade identity')
    require(marker.get('controls_sha256')==current_controls,'Release controls changed after review')
    require(marker.get('review_sha256')==sha(review),'Independent review changed')
    require(marker.get('fixture_sha256')==sha(fixture),'Upgrade fixture changed')
    require(marker.get('runtime_archive_sha256')==sha(Path(archive).read_bytes()),'Runtime archive changed')
    require(marker.get('matrix_sha256')==sha(matrix_raw),'Reviewed runtime matrix changed')
    text=review.decode()
    for line in ('Source: '+source,'Decision: APPROVED','Production signing: APPROVED',
                 'Physical Fold Main encrypted capture: PASS','Physical Fold Friends separation: PASS',
                 'Upgrade fixture SHA-256: '+sha(fixture),'Upgrade controls SHA-256: '+controls_digest(current_controls),
                 'Runtime matrix SHA-256: '+sha(matrix_raw),'Runtime archive SHA-256: '+marker['runtime_archive_sha256']):
        require(line in text.splitlines(),'Missing exact independent approval line: '+line)
    require(type(marker.get('test_run')) is int and marker['test_run']>0 and run.get('id')==marker['test_run'],'Wrong runtime run')
    require(run.get('event')=='push' and run.get('head_sha')==source and run.get('head_branch')==BRANCH,'Wrong runtime source/branch/event')
    require(run.get('status')=='completed' and run.get('conclusion')=='success','Runtime workflow not successful')
    require(run.get('head_repository',{}).get('full_name')=='traynor1987/Private-gallery-','Wrong runtime repository')
    require(run.get('path','').split('@')[0]=='.github/workflows/fast-development.yml','Wrong runtime workflow')
    require(type(marker.get('test_artifact')) is int and marker['test_artifact']>0 and artifact.get('id')==marker['test_artifact'],'Wrong runtime artifact')
    require(artifact.get('expired') is False and artifact.get('name')=='novaos-home2-integration-verification','Expired or wrong runtime artifact')
    require(artifact.get('digest') == 'sha256:'+marker['runtime_archive_sha256'],'Runtime archive does not match original artifact digest')
    require(artifact.get('workflow_run',{}).get('id')==marker['test_run'] and artifact.get('workflow_run',{}).get('head_sha')==source,'Runtime artifact belongs to another source/run')
    matches=[job for job in jobs.get('jobs',[]) if job.get('name')=='home2-integration']
    require(len(matches)==1 and matches[0].get('conclusion')=='success','Missing successful original integration job')
    passed={step.get('name') for step in matches[0].get('steps',[]) if step.get('conclusion')=='success'}
    require(REQUIRED_STEPS<=passed,'Required runtime/build/identity step did not run')
    count=runtime_cases(archive,json.loads(matrix_raw),source)
    return dict(schema=1,decision='READY_FOR_GATED_SIGNER',source=source,baseline_source=BASELINE,
                baseline_code=76,candidate_code=77,test_run=marker['test_run'],test_artifact=marker['test_artifact'],review_sha256=sha(review),
                fixture_sha256=sha(fixture),controls_sha256=current_controls,matrix_sha256=sha(matrix_raw),
                runtime_archive_sha256=marker['runtime_archive_sha256'],validated_case_count=count,validated_restart_count=8,validated_launcher_restart_count=1)

def check_receipt(proof,source,current_controls,fixture):
    require(source_ok(source) and proof.get('schema')==1 and proof.get('decision')=='READY_FOR_GATED_SIGNER','Missing reviewed signer gate')
    require(proof.get('source')==source and proof.get('baseline_source')==BASELINE and proof.get('baseline_code')==76 and proof.get('candidate_code')==77,'Signer gate source/identity differs')
    require(proof.get('controls_sha256')==current_controls and proof.get('fixture_sha256')==sha(fixture),'Signer gate controls/fixture differ')
    require(type(proof.get('test_run')) is int and proof['test_run']>0 and type(proof.get('validated_case_count')) is int and proof['validated_case_count']>0,'Missing original runtime accounting')
    require(proof.get('validated_restart_count')==8,'Missing actual restart phase accounting')
    require(proof.get('validated_launcher_restart_count')==1,'Missing launcher process restart accounting')
    require(type(proof.get('test_artifact')) is int and proof['test_artifact']>0,'Missing original runtime artifact')
    for field in ('review_sha256','matrix_sha256','runtime_archive_sha256'): require(digest_ok(proof.get(field)),'Missing signer evidence '+field)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--receipt',type=Path);parser.add_argument('--source');parser.add_argument('--restart-folder',type=Path)
    for name in ('marker','review','run','jobs','artifact','runtime-archive','matrix','output'):parser.add_argument('--'+name,type=Path)
    args=parser.parse_args();fixture=(ROOT/'docs/release1711/UpgradeFixtureTest.kt').read_bytes();current=controls()
    if args.restart_folder:
        require(source_ok(args.source),'Exact runtime source required')
        count=restart_cases({p.name:p.read_bytes() for p in args.restart_folder.iterdir() if p.is_file()},args.source)
        print('PASS: '+str(count)+' original killed-process recovery phases');return
    if args.receipt:
        check_receipt(json.loads(args.receipt.read_bytes()),args.source,current,fixture)
        print('PASS: exact-source pre-signer approval receipt; production upgrade remains unverified');return
    require(all(getattr(args,name) is not None for name in ('marker','review','run','jobs','artifact','runtime_archive','matrix','output')),'All original review and runtime evidence paths are required')
    proof=validate(json.loads(args.marker.read_bytes()),args.review.read_bytes(),json.loads(args.run.read_bytes()),json.loads(args.jobs.read_bytes()),json.loads(args.artifact.read_bytes()),args.runtime_archive,args.matrix.read_bytes(),current,fixture)
    with args.output.open('x') as output:json.dump(proof,output,indent=2);output.write('\n')
    print('PASS: reviewed exact-source runtime gates; signer readiness only, no production upgrade or publication claim')
if __name__=='__main__':main()
