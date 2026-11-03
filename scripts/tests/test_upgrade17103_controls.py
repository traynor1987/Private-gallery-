"""Pre-signer provenance and immutable upgrade-body checks; no Android/signing claim."""
import importlib, unittest, tempfile, zipfile, hashlib, copy, subprocess
from pathlib import Path
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[2]
class NonDebuggablePackageGuards(unittest.TestCase):
 def run_guard(self,phase,debuggable):
  # Execute the actual release-script block between its adjacent identity gates.
  # A negated grep under errexit must not permit the next gate to execute.
  if phase=='production':
   source=(ROOT/'scripts/verify_production_candidate17103.sh').read_text()
   block=source.split('grep -q "application-label:\'NovaOS\'" app/build/production-evidence/package.txt\n',1)[1].split("grep -Fq 'Signer #1",1)[0]
  else:
   source=(ROOT/'scripts/verify_synthetic_upgrade17103.sh').read_text()
   block=source.split('  grep -Fxq "targetSdkVersion:\'37\'" "$artifact_dir/upgrade-$phase-package.txt"\n',1)[1].split('  "$sdk/apksigner" verify',1)[0]
  with tempfile.TemporaryDirectory() as folder:
   root=Path(folder);evidence=root/'app/build/production-evidence';evidence.mkdir(parents=True)
   filename='package.txt' if phase=='production' else 'upgrade-'+phase+'-package.txt'
   if debuggable is not None:
    (evidence/filename).write_text("package: name='uk.co.traynor.privategallery'\n"+('application-debuggable\n' if debuggable else ''))
   program='set -euo pipefail\nartifact_dir=app/build/production-evidence\nphase=$1\n'+block+'\nprintf "REACHED_NEXT_GATE\\n"\n'
   return subprocess.run(['bash','-c',program,'guard-test',phase],cwd=root,text=True,capture_output=True)
 def test_debuggable_package_stops_before_next_gate_in_every_phase(self):
  for phase in ('production','baseline','candidate'):
   with self.subTest(phase=phase):
    result=self.run_guard(phase,True)
    self.assertNotEqual(result.returncode,0,result.stdout+result.stderr)
    self.assertNotIn('REACHED_NEXT_GATE',result.stdout)
 def test_nondebuggable_package_reaches_next_gate_in_every_phase(self):
  for phase in ('production','baseline','candidate'):
   with self.subTest(phase=phase):
    result=self.run_guard(phase,False)
    self.assertEqual(result.returncode,0,result.stderr)
    self.assertIn('REACHED_NEXT_GATE',result.stdout)
 def test_unreadable_package_evidence_stops_before_next_gate_in_every_phase(self):
  for phase in ('production','baseline','candidate'):
   with self.subTest(phase=phase):
    result=self.run_guard(phase,None)
    self.assertNotEqual(result.returncode,0,result.stdout+result.stderr)
    self.assertNotIn('REACHED_NEXT_GATE',result.stdout)

class ContinuityUpgradeControls(unittest.TestCase):
 def test_original54_archive_and_apk_denials(self):
  m=importlib.import_module('scripts.verify_upgrade17103_baseline')
  with tempfile.TemporaryDirectory() as folder:
   p=Path(folder)/'baseline.zip';apk=b'original signed bytes'
   with zipfile.ZipFile(p,'w')as z:
    z.writestr('baseline.apk',apk);z.writestr('production-evidence/source-sha.txt','9'*40)
   with patch.multiple(m,ARCHIVE_SHA256=hashlib.sha256(p.read_bytes()).hexdigest(),APK_SHA256=hashlib.sha256(apk).hexdigest(),APK_BYTES=len(apk),SOURCE_SHA='9'*40):
    self.assertEqual(apk,m.validated_apk(p))
    with patch.object(m,'SOURCE_SHA','8'*40),self.assertRaises(ValueError):m.validated_apk(p)
    with patch.object(m,'APK_SHA256','0'*64),self.assertRaises(ValueError):m.validated_apk(p)
    with patch.object(m,'APK_BYTES',1),self.assertRaises(ValueError):m.validated_apk(p)
   with self.assertRaises(ValueError):m.validated_apk(p)
 def test_compiled_fixture_source_and_replacements_are_bound(self):
  m=importlib.import_module('scripts.verify_compiled_upgrade17103');fixture=b'fixture '+m.TARGET;build=b'build\n'+m.BUILD_TARGET+b'\n';compiler=b'compiler';apk=b'apk'
  f,b=m.transform(fixture,build)
  proof=dict(schema=1,decision='COMPILED',baseline_source_sha=m.BASELINE,candidate_source_sha='1'*40,fixture_sha256=m.sha(fixture),transformed_fixture_sha256=m.sha(f),compiler_sha256=m.sha(compiler),baseline_build_sha256=m.sha(build),transformed_build_sha256=m.sha(b),instrumentation_apk_sha256=m.sha(apk),instrumentation_apk_bytes=len(apk),instrumentation_package_sha256='7'*64,instrumentation_manifest_sha256='8'*64,transformations=['remove_debug_applicationIdSuffix','fixture_target_production_package'])
  m.validate(proof,'1'*40,fixture,compiler,apk,build)
  for key in proof:
   bad=copy.deepcopy(proof);bad[key]=None
   with self.subTest(key=key),self.assertRaises((ValueError,TypeError)):m.validate(bad,'1'*40,fixture,compiler,apk,build)
 def test_upgrade_fixture_preserves_all_prior_assertions(self):
  old=(ROOT/'docs/release17102/UpgradeFixtureTest.kt').read_text();new=(ROOT/'docs/release17103/UpgradeFixtureTest.kt').read_text()
  expected=old.replace('code53 -> code54','code54 -> code55').replace('baselineCode == 53 && candidateCode == 54','baselineCode == 54 && candidateCode == 55').replace('actual signed code53 model; verifies code54','actual signed code54 model; verifies code55').replace('installing code54','installing code55')
  self.assertEqual(expected,new)
