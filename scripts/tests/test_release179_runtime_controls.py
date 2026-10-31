"""Synthetic fail-closed evidence tests, never runtime or signing approval."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from scripts.revalidate_runtime179 import extract, route_comparison, sha, verify_static
from scripts.curate_release179 import curate

class RuntimeControls179(unittest.TestCase):
    def test_route_revalidation_preserves_claims_and_every_raw_hash_across_extraction_paths(self):
        names=['vpn/traffic.pcap','vpn/events.jsonl','vpn/evidence179-events.jsonl','onion/evidence179-events.jsonl','host-phases.jsonl','capture.json','capture-process.json','vpn/fixture.crt','vpn/fixture-ca.crt','onion/fixture.crt','onion/fixture-ca.crt']
        original=dict(schema=1,decision='PASS',source_sha='1'*40,original_exit_status=0,android_cases=146,controlled_route_cases=['vpn_revoke','vpn_loss','tor_revoke','tor_death'],remote_close_contract={'vpn_loss':'unavailable'},certificates={'vpn':'fixture'},hashes={'app/build/evidence179/'+name:'2'*64 for name in names})
        new=copy.deepcopy(original);new['hashes']={'/tmp/extraction/evidence179/'+name:'2'*64 for name in names}
        self.assertEqual(len(route_comparison(original,new,'1'*40,146)),64)
        for field,value in [('source_sha','3'*40),('decision','PENDING'),('android_cases',145),('controlled_route_cases',[]),('hashes',{})]:
            bad=copy.deepcopy(new);bad[field]=value
            with self.subTest(field=field),self.assertRaises(ValueError):route_comparison(original,bad,'1'*40,146)
        new['hashes']['/tmp/extraction/evidence179/vpn/traffic.pcap']='4'*64
        with self.assertRaises(ValueError):route_comparison(original,new,'1'*40,146)
    def test_archive_path_traversal_duplicate_and_symlink_refused(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)
            for index,name in enumerate(['/absolute','../outside','a\\b','a//b','normal']):
                archive=root/f'{index}.zip'
                with zipfile.ZipFile(archive,'w') as z:
                    if name=='normal':
                        entry=zipfile.ZipInfo(name);entry.external_attr=0o120777<<16;z.writestr(entry,'target')
                    else:z.writestr(name,'contents')
                with self.subTest(name=name),self.assertRaises(ValueError):extract(archive,root/f'out{index}')
            archive=root/'duplicate.zip'
            with zipfile.ZipFile(archive,'w') as z:
                z.writestr('same/path','first');z.writestr('same//path','second')
            with self.assertRaises(ValueError):extract(archive,root/'duplicate-out')
    def test_successful_static_receipts_and_tampering(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);source='1'*40;manifest=b'[{"classname":"fixture.Class","name":"method"}]'
            (root/'android-results').mkdir();(root/'android-results/results.xml').write_text('<testsuite><testcase classname="fixture.Class" name="method"/></testsuite>')
            (root/'source-sha.txt').write_text(source)
            focused=dict(source_sha=source,decision='PASS',original_exit_status=0,network={k:'PASS' for k in ['owned_vpn','native_tor','dns_and_packet_observation','revocation']})
            (root/'focused-gates.json').write_text(json.dumps(focused));(root/'route-proof.json').write_text('{}')
            (root/'ocr-distribution.json').write_text(json.dumps(dict(model_sha256='7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2',model_bytes=4113088,required_abis=['x86_64'],ocr_native=[dict(path='lib/x86_64/libtesseract.so',bytes=100,load_segments=1,sha256='2'*64)])))
            (root/'apk-native-alignment.txt').write_text('Verification successful\n')
            metadata=b'AndroidVersion.ApiLevel=37\nAndroidVersion.PreviewSdkInt=0\n';(root/'android17-sdk-source.properties').write_bytes(metadata)
            (root/'android17-sdk.json').write_text(json.dumps(dict(api_level=37,platform='android-37.1',preview=False,metadata_sha256=sha(metadata))))
            (root/'emulator-health.txt').write_text('target=android-37.0\n37\n'+''.join(f'surfaceflinger[{i}]=123\n' for i in range(1,16)))
            for name in ['emulator-crash-preflight.txt','emulator-crash-final.txt']:(root/name).write_text('')
            gates={**focused,'validated_case_count':1,'case_manifest_sha256':sha(manifest)}
            for field,name in [('route_proof_sha256','route-proof.json'),('ocr_distribution_sha256','ocr-distribution.json'),('apk_native_alignment_sha256','apk-native-alignment.txt')]:gates[field]=sha((root/name).read_bytes())
            (root/'final-gates.json').write_text(json.dumps(gates))
            self.assertEqual(verify_static(root,source,manifest)[0],1)
            for name,bad in [('source-sha.txt','3'*40),('route-proof.json','changed'),('ocr-distribution.json','{}'),('apk-native-alignment.txt','failed'),('emulator-health.txt','target=android-37.0\n37\n'),('android17-sdk-source.properties','AndroidVersion.ApiLevel=36'),('emulator-crash-final.txt','hasReadColorBufferDma')]:
                path=root/name;previous=path.read_text();path.write_text(bad)
                try:
                    with self.subTest(name=name),self.assertRaises(ValueError):verify_static(root,source,manifest)
                finally:path.write_text(previous)
    def test_delivery_allowlist_excludes_apks_keys_configs_and_recursive_roots(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);source=root/'source';source.mkdir()
            for name in ['signature.txt','candidate.apk','instrumentation.apk','private.jks','client.conf','control_auth_cookie','fixture.key']:(source/name).write_bytes(b'synthetic')
            (source/'focused').mkdir();(source/'focused/secret.json').write_text('sensitive')
            inventory=curate(source,root/'output');self.assertEqual(set(inventory),{'signature.txt'})
            (source/'signature.txt').write_text('-----BEGIN PRIVATE KEY-----')
            with self.assertRaises(ValueError):curate(source,root/'failed')
if __name__=='__main__':unittest.main()
