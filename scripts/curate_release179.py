#!/usr/bin/env python3
"""Copy an explicit public receipt allowlist, never recursively copy build/fixture roots."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

PUBLIC = {
    'focused-run.json','original-ci-route-proof.json','revalidated-route-proof.json','packet-replay.json',
    'original178code50-provenance.json','original178code50-signature.txt','original178code50-package.txt',
    'android17-sdk.json','android17-sdk-source.properties','permanent-signer.txt','signature.txt','package.txt',
    'apk-sha256.txt','source-sha.txt','release-manifest.txt','ocr-distribution.json','apk-native-alignment.txt',
    'tor-distribution.json','upgrade-validation.json','release-summary.json','control-hashes.json','release-control-sha.txt',
    'FINAL_SCOPE_APPROVAL.json','SECURITY_REVIEW.md','UpgradeFixtureTest.kt','compilation.json',
}
UPGRADE = {'upgrade-'+name for name in ['baseline-package.txt','candidate-package.txt','baseline-signature.txt','candidate-signature.txt',
    'baseline-sha256.txt','candidate-sha256.txt','baseline-source-sha.txt','candidate-source-sha.txt','instrumentation-signature.txt',
    'instrumentation-sha256.txt','fixture-sha256.txt','compiled-fixture-sha256.txt','compilation.json','seed.txt','verify.txt','after-restart.txt','result.txt']}

def curate(source,destination):
    if destination.exists():shutil.rmtree(destination)
    destination.mkdir(parents=True);inventory={}
    for relative in sorted(PUBLIC | {'from50/'+name for name in UPGRADE}):
        path=source/relative
        if not path.exists():continue
        if not path.is_file() or path.is_symlink() or path.stat().st_size>16*1024*1024:raise ValueError('Invalid receipt '+relative)
        data=path.read_bytes()
        if b'PRIVATE KEY-----' in data or b'PrivateKey =' in data or b'PresharedKey =' in data:raise ValueError('Private material in receipt '+relative)
        target=destination/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
        inventory[relative]=hashlib.sha256(data).hexdigest()
    (destination/'delivery-evidence-sha256.json').write_text(json.dumps(inventory,sort_keys=True,indent=2)+'\n')
    return inventory

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('source',type=Path);p.add_argument('destination',type=Path)
    args=p.parse_args();curate(args.source,args.destination)
if __name__=='__main__':main()
