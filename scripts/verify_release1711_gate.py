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
BRANCH='integration/novaos-1.7-home2'
BASELINE='ab659cf3578d08cdc0deb643f887e561abb14c63'
CONTROLS=('compile_upgrade1711_fixture.sh','verify_compiled_upgrade1711.py',
          'verify_upgrade1711_baseline.py','verify_production_candidate1711.sh',
          'verify_synthetic_upgrade1711.sh','verify_signed_upgrade1711.py',
          'release_configuration1711.sh','verify_release1711_gate.py')
REQUIRED_STEPS={'Build isolated test identity and run JVM checks',
                'Verify reused Home policies and encrypted adapter',
                'Verify browser engine preservation and isolated APK',
                'Focused Android 17 integration and privacy tests',
                'Validate every selected original Android report'}

def sha(raw): return hashlib.sha256(raw).hexdigest()
def require(ok,message):
    if not ok: raise ValueError(message)
def controls(root=ROOT): return {name:sha((root/'scripts'/name).read_bytes()) for name in CONTROLS}
def controls_digest(value): return sha(json.dumps(value,sort_keys=True,separators=(',',':')).encode())
def source_ok(source): return isinstance(source,str) and re.fullmatch('[a-f0-9]{40}',source) is not None and source!='0'*40
def digest_ok(value): return isinstance(value,str) and re.fullmatch('[a-f0-9]{64}',value) is not None and value!='0'*64

def runtime_cases(archive,matrix,source):
    expected={(cls,name) for cls,names in matrix.items() for name in names}
    require(expected and sum(len(names) for names in matrix.values())==len(expected),'Empty or duplicate reviewed runtime matrix')
    seen=set()
    with zipfile.ZipFile(archive) as zipped:
        names=zipped.namelist();require(len(names)==len(set(names)),'Duplicate runtime archive members')
        sources=[name for name in names if name.endswith('home2-evidence/source-sha.txt')]
        require(len(sources)==1 and zipped.read(sources[0]).decode().strip()==source,'Runtime source differs')
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
    require(marker.get('baseline_source')==BASELINE and marker.get('baseline_code')==59 and marker.get('candidate_code')==60,'Wrong upgrade identity')
    require(marker.get('controls_sha256')==current_controls,'Release controls changed after review')
    require(marker.get('review_sha256')==sha(review),'Independent review changed')
    require(marker.get('fixture_sha256')==sha(fixture),'Upgrade fixture changed')
    require(marker.get('runtime_archive_sha256')==sha(Path(archive).read_bytes()),'Runtime archive changed')
    require(marker.get('matrix_sha256')==sha(matrix_raw),'Reviewed runtime matrix changed')
    text=review.decode()
    for line in ('Source: '+source,'Decision: APPROVED','Production signing: APPROVED',
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
    require(artifact.get('workflow_run',{}).get('id')==marker['test_run'] and artifact.get('workflow_run',{}).get('head_sha')==source,'Runtime artifact belongs to another source/run')
    matches=[job for job in jobs.get('jobs',[]) if job.get('name')=='home2-integration']
    require(len(matches)==1 and matches[0].get('conclusion')=='success','Missing successful original integration job')
    passed={step.get('name') for step in matches[0].get('steps',[]) if step.get('conclusion')=='success'}
    require(REQUIRED_STEPS<=passed,'Required runtime/build/identity step did not run')
    count=runtime_cases(archive,json.loads(matrix_raw),source)
    return dict(schema=1,decision='READY_FOR_GATED_SIGNER',source=source,baseline_source=BASELINE,
                baseline_code=59,candidate_code=60,test_run=marker['test_run'],test_artifact=marker['test_artifact'],review_sha256=sha(review),
                fixture_sha256=sha(fixture),controls_sha256=current_controls,matrix_sha256=sha(matrix_raw),
                runtime_archive_sha256=marker['runtime_archive_sha256'],validated_case_count=count)

def check_receipt(proof,source,current_controls,fixture):
    require(source_ok(source) and proof.get('schema')==1 and proof.get('decision')=='READY_FOR_GATED_SIGNER','Missing reviewed signer gate')
    require(proof.get('source')==source and proof.get('baseline_source')==BASELINE and proof.get('baseline_code')==59 and proof.get('candidate_code')==60,'Signer gate source/identity differs')
    require(proof.get('controls_sha256')==current_controls and proof.get('fixture_sha256')==sha(fixture),'Signer gate controls/fixture differ')
    require(type(proof.get('test_run')) is int and proof['test_run']>0 and type(proof.get('validated_case_count')) is int and proof['validated_case_count']>0,'Missing original runtime accounting')
    require(type(proof.get('test_artifact')) is int and proof['test_artifact']>0,'Missing original runtime artifact')
    for field in ('review_sha256','matrix_sha256','runtime_archive_sha256'): require(digest_ok(proof.get(field)),'Missing signer evidence '+field)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--receipt',type=Path);parser.add_argument('--source')
    for name in ('marker','review','run','jobs','artifact','runtime-archive','matrix','output'):parser.add_argument('--'+name,type=Path)
    args=parser.parse_args();fixture=(ROOT/'docs/release1711/UpgradeFixtureTest.kt').read_bytes();current=controls()
    if args.receipt:
        check_receipt(json.loads(args.receipt.read_bytes()),args.source,current,fixture)
        print('PASS: exact-source pre-signer approval receipt; production upgrade remains unverified');return
    require(all(getattr(args,name) is not None for name in ('marker','review','run','jobs','artifact','runtime_archive','matrix','output')),'All original review and runtime evidence paths are required')
    proof=validate(json.loads(args.marker.read_bytes()),args.review.read_bytes(),json.loads(args.run.read_bytes()),json.loads(args.jobs.read_bytes()),json.loads(args.artifact.read_bytes()),args.runtime_archive,args.matrix.read_bytes(),current,fixture)
    with args.output.open('x') as output:json.dump(proof,output,indent=2);output.write('\n')
    print('PASS: reviewed exact-source runtime gates; signer readiness only, no production upgrade or publication claim')
if __name__=='__main__':main()
