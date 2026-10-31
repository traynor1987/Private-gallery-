#!/usr/bin/env python3
"""Validate source-bound original50→51 upgrade receipts; never signs or installs."""
import argparse
import hashlib
import json
from pathlib import Path
import re

CERTIFICATE='94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17'
BASELINE_SOURCE='4820e176dcb60a3aa2f1bfd7b36e2de19fafee11'
BASELINE_APK='df958e447ce1c5a9bc9b515d16de03c0d11a961ae6efb030b305b2dfe733ccf3'

def digest(value):return hashlib.sha256(value).hexdigest()
def require(condition,message):
    if not condition:raise ValueError(message)

def validate(root,source,candidate_sha,fixture):
    require(re.fullmatch('[a-f0-9]{40}',source) is not None and source!='0'*40,'Unfrozen source')
    require(re.fullmatch('[a-f0-9]{64}',candidate_sha) is not None and candidate_sha!='0'*64,'Invalid candidate digest')
    root=Path(root);receipts={}
    def read(name):
        path=root/name
        require(path.is_file() and not path.is_symlink() and path.stat().st_size<=2*1024*1024,'Missing or invalid receipt '+name)
        raw=path.read_bytes();receipts[name]=digest(raw)
        return raw.decode('utf-8')
    def hash_receipt(name):
        fields=read(name).split()
        require(len(fields)>=1 and re.fullmatch('[a-f0-9]{64}',fields[0]) is not None and fields[0]!='0'*64,'Invalid SHA receipt '+name)
        return fields[0]
    def signature(name):
        text=read(name)
        for line in ['Verified using v2 scheme (APK Signature Scheme v2): true','Number of signers: 1','Signer #1 certificate SHA-256 digest: '+CERTIFICATE]:
            require(text.splitlines().count(line)==1,'Wrong signature identity '+name)
    require(read('upgrade-baseline-source-sha.txt').strip()==BASELINE_SOURCE,'Wrong original source')
    require(read('upgrade-candidate-source-sha.txt').strip()==source,'Wrong candidate source')
    require(hash_receipt('upgrade-baseline-sha256.txt')==BASELINE_APK,'Wrong original APK bytes')
    require(hash_receipt('upgrade-candidate-sha256.txt')==candidate_sha,'Wrong tested candidate APK bytes')
    for phase,code,version in [('baseline',50,'1.7.8'),('candidate',51,'1.7.9')]:
        text=read(f'upgrade-{phase}-package.txt')
        expected=f"package: name='uk.co.traynor.privategallery' versionCode='{code}' versionName='{version}'"
        require(sum(line.startswith(expected) for line in text.splitlines())==1,'Wrong package identity '+phase)
        require(all(text.splitlines().count(line)==1 for line in ["sdkVersion:'37'","targetSdkVersion:'37'"]),'Wrong supported Android API '+phase)
        require('application-debuggable' not in text,'Debuggable production application')
        signature(f'upgrade-{phase}-signature.txt')
    signature('upgrade-instrumentation-signature.txt')
    instrumentation_sha=hash_receipt('upgrade-instrumentation-sha256.txt')
    require(hash_receipt('upgrade-fixture-sha256.txt')==digest(fixture),'Unreviewed fixture')
    old=b'uk.co.traynor.privategallery.hiddenprototype'
    require(fixture.count(old)==1,'Unexpected fixture target transformation')
    changed=fixture.replace(old,b'uk.co.traynor.privategallery')
    require(hash_receipt('upgrade-compiled-fixture-sha256.txt')==digest(changed),'Wrong compiled fixture source')
    for phase in ['seed','verify','after-restart']:
        text=read(f'upgrade-{phase}.txt')
        require(len(re.findall(r'^OK \(1 test\)\s*$',text,re.M))==1,'Missing/duplicate successful phase '+phase)
        require(not any(value in text for value in ['FAILURES!!!','INSTRUMENTATION_FAILED','Process crashed','FAILURE:']),'Failed upgrade phase '+phase)
    require(read('upgrade-result.txt').startswith('PASS: original signed code50 -> code51 in-place upgrade/restart;'),'Incomplete upgrade result')
    return dict(decision='PASS',source_sha=source,version='1.7.9',version_code=51,package='uk.co.traynor.privategallery',
        baseline_code=50,baseline_source_sha=BASELINE_SOURCE,baseline_apk_sha256=BASELINE_APK,candidate_apk_sha256=candidate_sha,
        certificate_sha256=CERTIFICATE,fixture_sha256=digest(fixture),compiled_fixture_sha256=digest(changed),instrumentation_apk_sha256=instrumentation_sha,
        phases=['seed','verify','after-restart'],receipts=receipts,owner_physical_acceptance='NOT_PERFORMED')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence',type=Path,required=True);parser.add_argument('--candidate',type=Path,required=True)
    parser.add_argument('--source',required=True);parser.add_argument('--fixture',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    with args.candidate.open('rb') as stream:candidate_sha=hashlib.file_digest(stream,'sha256').hexdigest()
    report=validate(args.evidence,args.source,candidate_sha,args.fixture.read_bytes())
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(report,indent=2)+'\n')
    print('PASS: original-key50→51 source-bound upgrade/restart receipts; physical acceptance remains owner-required')
if __name__=='__main__':main()
