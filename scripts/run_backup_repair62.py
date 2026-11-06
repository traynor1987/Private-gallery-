#!/usr/bin/env python3
"""Run and verify the nine observed API37 failures before the complete mandatory matrix."""
from pathlib import Path
import json, shutil, subprocess, sys, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
PREFIX='uk.co.traynor.privategallery.'
CASES={
 'core.backup.AutomaticBackupRuntime62Test':['encryptedOptInRunsOnEligibleForegroundWithoutManualBackupAndVerifiesUniqueFiles'],
 'core.backup.BackupSafRuntimeTest':['retirementCancelsRealProviderOpenAndPreservesOriginalsInBothSpaces'],
 'core.backup.BackupUi62Test':['cancelledPickerAndLateDuplicateDoNotInstallARecoveryInput'],
 'core.backup.BackupDocumentsUiTest':['androidCreateDocumentResultResumesAndWritesActualGrantedDestination','actualTreePickerRequiresRecoveryAcknowledgementBeforeArmingEncryptedAutomaticSchedule'],
 'home2.CalculatorUiTest':['longPressSequenceHasNeutralSemanticsNeverEvaluatesAndStaleCallbacksCannotAct'],
 'HomeOwnedWindowPanicTest':['activeBackupIoStopsAtRealMainLockPanicAndBackgroundInBothSpaces','realForegroundHomeExecutesScheduledPrimaryBackupWithoutManualBackupAction','realSettingsRestoreLocksAndReloadsHomeMindAndWidgetsBeforeFurtherEdits'],
}
expected={(PREFIX+cls,name) for cls,names in CASES.items() for name in names}
if __name__=='__main__':
 out=ROOT/'app/build/home2-evidence/focused-repair62';out.mkdir(parents=True,exist_ok=True)
 selection=','.join(cls+'#'+name for cls,name in sorted(expected))
 command=['./gradlew','connectedDebugAndroidTest','-Pandroid.testInstrumentationRunnerArguments.class='+selection,'-Pkotlin.incremental=false','--no-daemon','--console=plain']
 result=subprocess.run(command,cwd=ROOT)
 reports=ROOT/'app/build/outputs/androidTest-results/connected/debug'
 if reports.exists():shutil.copytree(reports,out/'reports',dirs_exist_ok=True)
 if result.returncode:sys.exit(result.returncode)
 cases=[c for f in (out/'reports').rglob('*.xml') for c in ET.parse(f).getroot().iter('testcase')]
 actual=[(c.attrib['classname'],c.attrib['name']) for c in cases]
 assert len(actual)==len(set(actual)) and set(actual)==expected, 'Focused report must contain exactly the nine failures'
 assert all(not any(c.find(k) is not None for k in ('failure','error','skipped')) for c in cases), 'Focused failures/errors/skips'
 (out/'result.json').write_text(json.dumps(dict(result='PASS',cases=len(cases),source=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()),indent=2))
 print('Focused Android 17 repairs: 9/9 PASS; full mandatory matrix may now run')
