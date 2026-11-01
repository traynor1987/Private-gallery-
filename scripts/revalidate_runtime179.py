#!/usr/bin/env python3
"""Reparse immutable original runtime evidence and execute the frozen route validator."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile
try:
    from verify_final_scope179 import cases_from_manifest, verify_xml, NETWORK
    from replay_evidence179_packets import replay, verify_original_hashes
except ModuleNotFoundError:
    from scripts.verify_final_scope179 import cases_from_manifest, verify_xml, NETWORK
    from scripts.replay_evidence179_packets import replay, verify_original_hashes

def require(condition,message):
    if not condition:raise ValueError(message)
def sha(raw):return hashlib.sha256(raw).hexdigest()
def extract(archive,destination):
    require(not destination.exists(),'Evidence destination already exists')
    with zipfile.ZipFile(archive) as z:
        names=z.namelist();require(len(names)==len(set(names)), 'Duplicate evidence ZIP path')
        normalized=[Path(name).as_posix() for name in names]
        require(len(normalized)==len(set(normalized)), 'Colliding evidence ZIP paths')
        for info in z.infolist():
            path=Path(info.filename)
            require(not path.is_absolute() and '..' not in path.parts and '\\' not in info.filename and path.as_posix()==info.filename.rstrip('/') and str(path)!='.','Unsafe evidence ZIP path')
            require((info.external_attr>>16)&0o170000 != 0o120000,'Evidence ZIP symlink')
            require(info.file_size <= 256*1024*1024,'Oversized evidence entry')
        require(sum(i.file_size for i in z.infolist())<=1024*1024*1024,'Oversized evidence ZIP')
        z.extractall(destination)
def canonical(name):
    name=str(name).replace('\\','/')
    # The route CLI intentionally rebinds paths to the independent extraction root.
    if '/evidence179/' in name:return 'evidence179/'+name.split('/evidence179/',1)[1]
    if name.startswith('evidence179/'):return name
    return name

def route_comparison(original,revalidated,source,count):
    fields=['schema','decision','source_sha','original_exit_status','android_cases','controlled_route_cases','remote_close_contract','certificates']
    require(all(original.get(k)==revalidated.get(k) for k in fields),'Revalidated route claims differ from original CI proof')
    require(original.get('source_sha')==source and original.get('decision')=='PASS' and original.get('android_cases')==count,'Wrong original route source/cases')
    require(type(original.get('original_exit_status')) is int and original['original_exit_status']==0,'Failed original route invocation')
    require(set(original.get('controlled_route_cases',[]))=={'vpn_revoke','vpn_loss','tor_revoke','tor_death'},'Wrong actual route selection')
    def normalized(proof):
        hashes=proof.get('hashes',{});result={}
        require(bool(hashes),'Missing raw route hashes')
        for name,value in hashes.items():
            key=canonical(name)
            require(key not in result and re.fullmatch('[a-f0-9]{64}',value) is not None,'Duplicate/invalid normalized raw hash')
            result[key]=value
        return result
    old=normalized(original);new=normalized(revalidated)
    require(old==new,'Raw route/control hashes differ after independent validation')
    required={'evidence179/vpn/traffic.pcap','evidence179/vpn/events.jsonl','evidence179/vpn/evidence179-events.jsonl',
        'evidence179/onion/evidence179-events.jsonl','evidence179/host-phases.jsonl','evidence179/capture.json','evidence179/capture-process.json',
        'evidence179/vpn/fixture.crt','evidence179/vpn/fixture-ca.crt','evidence179/onion/fixture.crt','evidence179/onion/fixture-ca.crt'}
    require(required<=set(old),'Missing independently bound raw observations')
    return sha(json.dumps(old,sort_keys=True,separators=(',',':')).encode())

def verify_static(root,source,manifest):
    require((root/'source-sha.txt').read_text().strip()==source,'Wrong archived source')
    count=verify_xml(root/'android-results',cases_from_manifest(manifest))
    gates=json.loads((root/'final-gates.json').read_text());focused=json.loads((root/'focused-gates.json').read_text())
    expected=dict(source_sha=source,decision='PASS',original_exit_status=0,network={k:'PASS' for k in NETWORK})
    require(focused==expected and type(focused.get('original_exit_status')) is int,'Invalid original focused gates')
    require(all(gates.get(k)==v for k,v in expected.items()) and type(gates.get('original_exit_status')) is int,'Invalid original final gates')
    require(gates.get('validated_case_count')==count and gates.get('case_manifest_sha256')==sha(manifest),'Wrong final cases/manifest')
    bindings={}
    for field,name in [('route_proof_sha256','route-proof.json'),('ocr_distribution_sha256','ocr-distribution.json'),('apk_native_alignment_sha256','apk-native-alignment.txt')]:
        actual=sha((root/name).read_bytes());require(gates.get(field)==actual,'Changed original runtime binding '+name);bindings[field]=actual
    distribution=json.loads((root/'ocr-distribution.json').read_text())
    require(distribution.get('model_sha256')=='7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2' and distribution.get('model_bytes')==4113088,'Wrong packaged OCR model')
    require(distribution.get('required_abis')==['x86_64'] and bool(distribution.get('ocr_native')),'Missing runtime OCR ABI')
    for native in distribution['ocr_native']:
        require(re.fullmatch(r'lib/x86_64/[^/]*(?:tess|lept)[^/]*\.so',native.get('path','')) and native.get('bytes',0)>0 and native.get('load_segments',0)>0 and re.fullmatch('[a-f0-9]{64}',native.get('sha256','')),'Invalid OCR native report')
    require((root/'apk-native-alignment.txt').read_text().rstrip().endswith('Verification successful'),'Missing successful actual ZIP alignment')
    sdk=json.loads((root/'android17-sdk.json').read_text());metadata=(root/'android17-sdk-source.properties').read_bytes()
    require(sdk.get('api_level')==37 and sdk.get('platform')=='android-37.1' and sdk.get('preview') is False and sdk.get('metadata_sha256')==sha(metadata),'Unverified compile SDK')
    properties={line.split('=',1)[0].strip():line.split('=',1)[1].strip() for line in metadata.decode().splitlines() if '=' in line and not line.lstrip().startswith('#')}
    require(properties.get('AndroidVersion.ApiLevel') in ('37','37.1') and properties.get('AndroidVersion.PreviewSdkInt','0')=='0' and properties.get('AndroidVersion.CodeName','REL') in ('','REL'),'Wrong actual SDK metadata')
    health=(root/'emulator-health.txt').read_text()
    require('target=android-37.0' in health.splitlines() and '37' in health.splitlines(),'Wrong runtime Android platform')
    samples=re.findall(r'^surfaceflinger\[(\d+)\]=(\d+)\s*$',health,re.M)
    require([int(x[0]) for x in samples]==list(range(1,16)) and len({x[1] for x in samples})==1 and int(samples[0][1])>0,'Unstable emulator compositor')
    for name in ['emulator-crash-preflight.txt','emulator-crash-final.txt']:
        require((root/name).is_file() and 'hasReadColorBufferDma' not in (root/name).read_text(),'Missing/failed emulator crash evidence')
    bindings.update(final_gates_sha256=sha((root/'final-gates.json').read_bytes()),focused_gates_sha256=sha((root/'focused-gates.json').read_bytes()),
        android_sdk_sha256=sha((root/'android17-sdk.json').read_bytes()),emulator_health_sha256=sha((root/'emulator-health.txt').read_bytes()))
    return count,bindings

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--archive',type=Path,required=True);p.add_argument('--destination',type=Path,required=True)
    p.add_argument('--source',required=True);p.add_argument('--run',type=Path,required=True);p.add_argument('--manifest',type=Path,required=True)
    p.add_argument('--receipts',type=Path,required=True);args=p.parse_args()
    run=json.loads(args.run.read_text())
    require('sha256:'+sha(args.archive.read_bytes())==run['artifact_digest'],'Downloaded original archive digest changed')
    extract(args.archive,args.destination);root=args.destination/'evidence179'
    manifest=args.manifest.read_bytes();require(manifest==Path('scripts/evidence179_cases.json').read_bytes(),'Frozen case manifest differs')
    count,bindings=verify_static(root,args.source,manifest)
    capture=json.loads((root/'capture-process.json').read_text())
    expected_pcap=str(Path('app/build/hidden-vpn1762/traffic.pcap').resolve())
    require(expected_pcap in capture.get('argv',[]) and expected_pcap in capture.get('outputs',[]),
        'Original capture checkout path relocated; frozen validator requires exact original GITHUB_WORKSPACE path')
    original=(root/'route-proof.json').read_bytes();args.receipts.mkdir(parents=True,exist_ok=True)
    (args.receipts/'original-ci-route-proof.json').write_bytes(original)
    verify_original_hashes(json.loads(original),root,Path.cwd())
    # Keep the original archive extraction immutable; the frozen CLI writes its
    # independent proof/rendering into a separate replay tree only.
    replay_root=args.destination/'replay'/'evidence179'
    shutil.copytree(root,replay_root)
    replay(replay_root,args.source,args.receipts)
    revalidated=(replay_root/'route-proof.json').read_bytes()
    bindings['raw_route_hashes_sha256']=route_comparison(json.loads(original),json.loads(revalidated),args.source,count)
    (args.receipts/'revalidated-route-proof.json').write_bytes(revalidated)
    verify_original_hashes(json.loads(original),root,Path.cwd())
    require((root/'route-proof.json').read_bytes()==original,'Original proof changed during replay')
    run.update(evidence_validation='PASS',validated_case_count=count,original_exit_status=0,network_evidence_revalidated=True,
        evidence_hashes=bindings,revalidated_route_proof_sha256=sha(revalidated))
    args.run.write_text(json.dumps(run,indent=2)+'\n')
    print('PASS: original exact cases, SDK/health/model/ZIP alignment and independently replayed raw route validator')
if __name__=='__main__':main()
