"""Reuse policy denial tests; synthetic proof stubs never count as device evidence."""
import io
import json
import hashlib
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import reuse_hidden_continuity17103 as reuse

class CompatibilityTests(unittest.TestCase):
 def git(self,root,*args):return subprocess.check_output(['git','-C',str(root),*args],stderr=subprocess.DEVNULL).decode().strip()
 def setup_repo(self,root):
  self.git(root,'init');self.git(root,'config','user.email','fixture@invalid');self.git(root,'config','user.name','Synthetic fixture')
  (root/'protected.txt').write_text('baseline');self.git(root,'add','.');self.git(root,'commit','-m','synthetic baseline');return self.git(root,'rev-parse','HEAD')
 def test_every_unlisted_path_including_production_debug_and_passed_helpers_denies(self):
  paths=['app/src/main/java/Hidden.kt','app/src/debug/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenContinuityFixtureService.kt','app/src/debug/AndroidManifest.xml','app/src/main/res/xml/network.xml','app/build.gradle.kts','gradle/libs.versions.toml',
         'app/src/androidTest/java/HiddenVpnLive1762Test.kt','app/src/androidTest/java/FixtureAccessibility178.java','scripts/hidden_vpn_fixture1762.py','scripts/verify_hidden_vpn_fixture1762.py','native/library.so','docs/release17103/UpgradeFixtureTest.kt','docs/release17103/continuity-release-cases.json']
  with tempfile.TemporaryDirectory() as folder:
   root=Path(folder);baseline=self.setup_repo(root)
   for path in paths:
    self.git(root,'reset','--hard',baseline);file=root/path;file.parent.mkdir(parents=True,exist_ok=True);file.write_text('incompatible');self.git(root,'add','.');self.git(root,'commit','-m','incompatible')
    with patch.object(reuse,'BASELINE_SOURCE',baseline),self.subTest(path=path),self.assertRaisesRegex(ValueError,'protected paths'):reuse.compatibility(root,self.git(root,'rev-parse','HEAD'))
 def test_exact_allowlist_passes_but_wrong_or_non_descendant_source_denies(self):
  with tempfile.TemporaryDirectory() as folder:
   root=Path(folder);baseline=self.setup_repo(root)
   paths=['app/src/androidTest/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenContinuityFixtureClient.kt','scripts/hidden_continuity_fixture17103.py']
   for path in paths:
    file=root/path;file.parent.mkdir(parents=True,exist_ok=True);file.write_text('reviewed fixture correction')
   self.git(root,'add','.');self.git(root,'commit','-m','allowed');candidate=self.git(root,'rev-parse','HEAD')
   with patch.object(reuse,'BASELINE_SOURCE',baseline):
    proof=reuse.compatibility(root,candidate);self.assertEqual(paths,[item['path'] for item in proof['changed']])
    with self.assertRaises(ValueError):reuse.compatibility(root,'HEAD')
   with patch.object(reuse,'BASELINE_SOURCE',candidate),self.assertRaisesRegex(ValueError,'ancestor'):reuse.compatibility(root,baseline)

class BaselineTests(unittest.TestCase):
 ui={('SyntheticUi','case'+str(i)) for i in range(15)}
 routes={('SyntheticRoute','case'+str(i)) for i in range(3)}
 def archive(self,mutate=None):
  def xml(cases):return '<testsuite>'+''.join('<testcase classname="'+cls+'" name="'+method+'"/>' for cls,method in sorted(cases))+'</testsuite>'
  tor=dict(mode='TOR_ONLY',javascript=False,**dict.fromkeys(('form','multipart','download_verified','discard_v4','discard_v6','discard_dns_udp','session_revoked','tor_process_death'),True))
  files={'nova17102/source-sha.txt':reuse.BASELINE_SOURCE,
   'nova17102/ui/outputs/androidTest-results/original.xml':xml(self.ui),
   'nova17102/base-route/outputs/androidTest-results/original.xml':xml(self.routes),
   'outputs/androidTest-results/failed-fresh.xml':'<testsuite><testcase classname="Failed8" name="not_reused"><failure/></testcase></testsuite>',
   'nova17102/ui-original-exit-status.txt':'0','nova17102/baseline-route-original-exit-status.txt':'0',
   'nova17102/ui-accounting.txt':'PASS: synthetic15','nova17102/baseline-route-accounting.txt':'PASS: synthetic3',
   'nova17102/tor-route-result.txt':'PASS: isolated packaged-Tor native forms/uploads/verified GET media synthetic unit stub',
   'nova17102/baseline-vpn/result.txt':'PASS: synthetic route stub','hidden-vpn1762/result.txt':'PASS: synthetic route stub',
   'nova17102/baseline-vpn/events.jsonl':'synthetic','nova17102/baseline-vpn/packets.txt':'synthetic','nova17102/baseline-vpn/traffic.pcap':'synthetic'}
  for name,data in [('native-tor1762-result.json',tor),('vpn-native-revocation1762-result.json',{}),('vpn-network-loss1762-result.json',{})]:
   files['nova17102/baseline-vpn/'+name]=json.dumps(data)
   files['nova17102/base-route/outputs/connected_android_test_additional_output/device/'+name]=json.dumps(data)
  if mutate:mutate(files)
  output=io.BytesIO()
  with zipfile.ZipFile(output,'w') as z:
   for name,data in files.items():z.writestr(name,data)
  return output.getvalue()
 def recheck(self,root):
  # Model the required native verifier call; real pinned ZIP is separately revalidated.
  for name in ('events.jsonl','packets.txt','traffic.pcap','native-tor1762-result.json','vpn-native-revocation1762-result.json','vpn-network-loss1762-result.json'):
   if not (root/name).is_file():raise ValueError('Missing raw route proof')
 def inspect(self,raw):
  with patch.object(reuse,'BASELINE_SHA256',hashlib.sha256(raw).hexdigest()),patch.object(reuse,'verify_fixture',side_effect=self.recheck) as check:
   result=reuse.baseline(raw,self.ui,self.routes);check.assert_called_once();return result
 def test_only_original18_xml_and_snapshot_are_derived_failed8_stay_in_raw_zip(self):
  files=self.inspect(self.archive())
  self.assertEqual(2,sum(name.endswith('.xml') for name in files));self.assertFalse(any('failed-fresh' in name for name in files))
  self.assertTrue(any('baseline-vpn/events' in name for name in files))
 def test_complete_archive_hash_tamper_denies_before_parsing(self):
  raw=self.archive()
  with patch.object(reuse,'BASELINE_SHA256',hashlib.sha256(raw).hexdigest()),self.assertRaisesRegex(ValueError,'SHA256'):reuse.baseline(raw+b'tamper',self.ui,self.routes)
 def test_wrong_source_failed_or_omitted_original_case_and_missing_proof_deny(self):
  changes=[lambda f:f.__setitem__('nova17102/source-sha.txt','0'*40),
   lambda f:f.__setitem__('nova17102/ui/outputs/androidTest-results/original.xml','<testsuite/>'),
   lambda f:f.__setitem__('nova17102/ui/outputs/androidTest-results/original.xml',f['nova17102/ui/outputs/androidTest-results/original.xml'].replace('/>','><failure/></testcase>',1)),
   lambda f:f.pop('nova17102/baseline-vpn/events.jsonl'),lambda f:f.pop('nova17102/tor-route-result.txt'),
   lambda f:f.__setitem__('nova17102/tor-route-result.txt','FAIL: synthetic'),lambda f:f.__setitem__('nova17102/baseline-route-original-exit-status.txt','1'),
   lambda f:f.__setitem__('nova17102/baseline-vpn/native-tor1762-result.json','{}')]
  for change in changes:
   with self.subTest(change=change),self.assertRaises((ValueError,AssertionError)):self.inspect(self.archive(change))
 def test_candidate_manifest_and_derived_original_bytes_cannot_be_substituted(self):
  expected={'source':'1'*40,'derived_sha256':{'proof':'hash'}};derived={'proof':b'original'}
  with patch.object(reuse,'plan',return_value=(expected,derived)):
   self.assertEqual(expected,reuse.verify_reuse(b'raw',expected,lambda _:b'original',Path('.'),'1'*40))
   with self.assertRaises(ValueError):reuse.verify_reuse(b'raw',dict(expected,source='2'*40),lambda _:b'original',Path('.'),'1'*40)
   with self.assertRaises(ValueError):reuse.verify_reuse(b'raw',expected,lambda _:b'changed',Path('.'),'1'*40)

if __name__=='__main__':unittest.main()
