#!/usr/bin/env python3
"""Host-side peer-close barrier after a destructive Android route action."""
import argparse
import json
import os
from pathlib import Path
import time
try:
    from verify_evidence179_routes import CASES
except ModuleNotFoundError:
    from scripts.verify_evidence179_routes import CASES


def wait(case,timeout=60):
    _,mode,phases=CASES[case]
    root=(Path(os.environ['RUNNER_TEMP'])/'private-gallery-onion-probe' if mode=='Tor' else Path('app/build/hidden-vpn1762'))
    path=root/'evidence179-events.jsonl'
    wanted=set(phases)-{'success','retired'}
    if case=='vpn_loss':
        # The delivery path has actually disappeared. Remote closure is deliberately
        # unavailable, never inferred from local timeout/completion or host cleanup.
        print('remote_close=unavailable_after_path_loss')
        return
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        with path.open() as source:
            import fcntl
            fcntl.flock(source,fcntl.LOCK_SH)
            events=[json.loads(line) for line in source if line.strip()]
        found=[e for e in events if e.get('case')==case]
        if len({e['nonce'] for e in found})!=1:raise RuntimeError('Missing/duplicate case nonce at close barrier')
        if any(e['kind'] in ('slow-complete','slow-timeout','slow-io-error') for e in found):raise RuntimeError('Actual peer closure missing')
        closed=[e['phase'] for e in found if e['kind']=='slow-closed']
        if len(closed)==len(wanted) and set(closed)==wanted:return
        time.sleep(.1)
    raise RuntimeError('Peer-close host barrier timed out')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--case',choices=CASES,required=True);args=parser.parse_args();wait(args.case)
