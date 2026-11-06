"""Require the original, complete selected API37 cases exactly once, without skipped tests."""
from pathlib import Path
import re, sys, xml.etree.ElementTree as ET, json
root=Path(__file__).resolve().parents[1]
classes=[
 'uk.co.traynor.privategallery.core.home2.SpacePreferencesTest',
 'uk.co.traynor.privategallery.core.home2.MindForegroundLifecycleTest',
 'uk.co.traynor.privategallery.core.browser.v2.NovaRetainedPrivateAppsTest',
 'uk.co.traynor.privategallery.ui.NovaPrivateAppAudioTest',
 'uk.co.traynor.privategallery.ui.BrowserPermissionOwnership1762Test',
 'uk.co.traynor.privategallery.ui.BrowserV2ModernWebTest',
 'uk.co.traynor.privategallery.core.home2.Home2IntegrationTest',
 'uk.co.traynor.privategallery.ui.DiscreetPinUnlockTest',
 'uk.co.traynor.privategallery.SmartUnlockLifecycle173Test',
 'uk.co.traynor.privategallery.Home2MainAuthorityTest',
 'uk.co.traynor.privategallery.HomeOwnedWindowPanicTest',
 'uk.co.traynor.privategallery.core.hidden.HiddenCredentialStoreTest',
 'uk.co.traynor.privategallery.core.evidence.EvidenceStore179Test',
 'uk.co.traynor.privategallery.core.camera.VaultCaptureAccessTest',
 'uk.co.traynor.privategallery.core.camera.CameraHardwareTest',
 'uk.co.traynor.privategallery.PanicCapture1751Test',
]
selected={
 'uk.co.traynor.privategallery.ui.BrowserPermissionOwnership1762Test':['mixedUnknownCapabilityCannotExpandMicrophoneOrCameraGrant'],
 'uk.co.traynor.privategallery.ui.BrowserV2ModernWebTest':['inactiveNativeFocusCannotSelectTabButExplicitSelectionKeepsExistingView'],
 'uk.co.traynor.privategallery.core.camera.CameraHardwareTest':['primaryNormalPhoto','hiddenNormalPhoto']}
expected=set()
for cls in classes:
 names=re.findall(r'@Test\s+fun\s+(\w+)\s*\(', (root/'app/src/androidTest/java'/Path(*cls.split('.'))).with_suffix('.kt').read_text())
 chosen=selected.get(cls,names);assert set(chosen)<=set(names),(cls,chosen)
 expected.update((cls,name) for name in chosen)
if len(sys.argv)==2 and sys.argv[1]=='--classes':
 print(','.join(value for cls in classes for value in ([cls+'#'+name for name in selected[cls]] if cls in selected else [cls])));raise SystemExit
if len(sys.argv)==2 and sys.argv[1]=='--matrix':
 print(json.dumps({cls:sorted(name for c,name in expected if c==cls) for cls in classes},indent=2));raise SystemExit
assert expected
reports=Path(sys.argv[1]);cases=[c for f in reports.rglob('*.xml') for c in ET.parse(f).getroot().iter('testcase')]
actual=[(c.attrib['classname'],c.attrib['name']) for c in cases]
assert len(actual)==len(set(actual)), 'Duplicate runtime cases'
assert set(actual)==expected, {'missing':sorted(expected-set(actual)),'unexpected':sorted(set(actual)-expected)}
assert all(not any(c.find(k) is not None for k in ('failure','error','skipped')) for c in cases), 'Runtime failures/errors/skips'
print(json.dumps({'result':'PASS','cases':len(cases),'classes':classes},indent=2))
