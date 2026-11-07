#!/usr/bin/env python3
"""Run and verify the nine observed API37 failures before the complete mandatory matrix."""
from pathlib import Path
import argparse
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
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument('--single',action='store_true',help='Run only the settings restore regression')
 args=parser.parse_args()
 if args.single:
  expected={(PREFIX+'HomeOwnedWindowPanicTest','realSettingsRestoreLocksAndReloadsHomeMindAndWidgetsBeforeFurtherEdits')}
 directory='single-repair62' if args.single else 'focused-repair62'
 out=ROOT/'app/build/home2-evidence'/directory
 out.mkdir(parents=True,exist_ok=True)
 selection=','.join(cls+'#'+name for cls,name in sorted(expected))
 command=['./gradlew','connectedDebugAndroidTest','-Pandroid.testInstrumentationRunnerArguments.class='+selection,'-Pkotlin.incremental=false','--no-daemon','--console=plain']
 result=subprocess.run(command,cwd=ROOT)
 reports=ROOT/'app/build/outputs/androidTest-results/connected/debug'
 if reports.exists():shutil.copytree(reports,out/'reports',dirs_exist_ok=True)
 if result.returncode:sys.exit(result.returncode)
 cases=[c for f in (out/'reports').rglob('*.xml') for c in ET.parse(f).getroot().iter('testcase')]
 actual=[(c.attrib['classname'],c.attrib['name']) for c in cases]
 assert len(actual)==len(set(actual)) and set(actual)==expected, 'Report must contain exactly the selected test cases'
 assert all(not any(c.find(k) is not None for k in ('failure','error','skipped')) for c in cases), 'Focused failures/errors/skips'
 (out/'result.json').write_text(json.dumps(dict(result='PASS',cases=len(cases),source=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()),indent=2))
 print(f'Android 17 {directory}: {len(cases)}/{len(expected)} PASS')
