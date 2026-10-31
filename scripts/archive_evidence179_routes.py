#!/usr/bin/env python3
"""Stop/flush exact owned capture and preserve public fixture observations before cleanup."""
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import time
try:
    from evidence179_fixture_endpoints import events as endpoint_events
except ModuleNotFoundError:
    from scripts.evidence179_fixture_endpoints import events as endpoint_events


def archive():
    if os.environ.get('GITHUB_ACTIONS')!='true' or os.environ.get('GITHUB_REF')!='refs/heads/release/1.7.9-evidence-locker':
        raise RuntimeError('Disposable evidence179 runner only')
    root=Path('app/build/evidence179');root.mkdir(parents=True,exist_ok=True)
    if (root/'capture.json').exists():
        return # Immutable archive after capture stop; cleanup must not alter validated bytes.
    temp=Path(os.environ['RUNNER_TEMP']);vpn=Path('app/build/hidden-vpn1762');onion=temp/'private-gallery-onion-probe'
    # Preserve endpoint closes from in-flight cancellations before freezing the evidence.
    deadline=time.monotonic()+20
    while time.monotonic()<deadline:
        events=[]
        for path in (vpn/'evidence179-events.jsonl',onion/'evidence179-events.jsonl'):
            if path.exists():
                events += endpoint_events(path.parent)
        starts={(e['nonce'],e['phase']) for e in events if e['kind']=='slow-start' and e['case']!='vpn_loss'}
        closes={(e['nonce'],e['phase']) for e in events if e['kind']=='slow-closed' and e['case']!='vpn_loss'}
        if starts==closes:break
        time.sleep(.2)
    private_vpn=temp/'private-gallery-vpn1762'
    identity=private_vpn/'capture.identity.json'
    stopped=False
    ownership='missing-identity'
    if identity.exists():
        shutil.copyfile(identity,root/'capture-process.json')
        result=subprocess.run(['sudo','python3','scripts/evidence179_processes.py','stop','--identity',str(identity),'--signal','INT'],capture_output=True,text=True)
        ownership=result.stdout.strip()
        stopped=result.returncode==0 and ownership=='stopped'
        if stopped:
            identity.unlink()
            (private_vpn/'capture.pid').unlink(missing_ok=True)
    # Missing/reused/unflushed identities never trigger an alternate kill; preserve failures too.
    subprocess.run(['sudo','chmod','-R','a+rX',str(vpn)],check=False)
    vpndest=root/'vpn';vpndest.mkdir(exist_ok=True)
    for command,name in ((['sudo','wg','show','pg1762','latest-handshakes'],'handshakes.txt'),(['sudo','iptables','-L','INPUT','-nv'],'input-firewall.txt')):
        value=subprocess.run(command,capture_output=True)
        (vpndest/name).write_bytes(value.stdout if value.returncode==0 else b'UNAVAILABLE\n')
    for folder,dest,names in (
        (vpn,vpndest,['events.jsonl','evidence179-events.jsonl','traffic.pcap','capture.log','fixture.log']),
        (temp/'private-gallery-vpn1762',vpndest,['fixture.crt','fixture-ca.crt']),
        (onion,root/'onion',['evidence179-events.jsonl','evidence179-ready','evidence179-tls-readiness.json','fixture.crt','fixture-ca.crt','fixture.log','client.log','client-readiness.txt','fixture-health.txt','backend-requests.txt'])):
        dest.mkdir(exist_ok=True)
        for name in names:
            if (folder/name).is_file():shutil.copyfile(folder/name,dest/name)
    start=json.loads((root/'capture-start.json').read_text())['epoch'] if (root/'capture-start.json').exists() else 0
    (root/'capture.json').write_text(json.dumps(dict(start=start,end=time.time(),stopped=stopped,ownership=ownership))+'\n')

if __name__=='__main__':archive()
