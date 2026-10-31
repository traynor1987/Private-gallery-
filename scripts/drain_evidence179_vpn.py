#!/usr/bin/env python3
"""Controlled fixture cleanup after VPN proof windows, before Tor negative windows.

This destroys only host synthetic-origin sockets. It is never reported as a peer
closure or as the effect of Android Network loss.
"""
import json
import os
from pathlib import Path
import subprocess
import time


def drain():
    if os.environ.get('GITHUB_ACTIONS')!='true' or os.environ.get('GITHUB_REF')!='refs/heads/release/1.7.9-evidence-locker':raise RuntimeError('Disposable179 runner only')
    root=Path('app/build/evidence179');path=root/'vpn-controlled-drain.json'
    if path.exists():raise RuntimeError('Do not repeat controlled drain')
    phases=[json.loads(line) for line in (root/'host-phases.jsonl').read_text().splitlines()]
    if [e['case'] for e in phases if e['kind']=='end']!=['vpn_revoke','vpn_loss'] or any(e['case'].startswith('tor_') for e in phases):raise RuntimeError('Drain must be outside completed VPN / not-started Tor windows')
    report=dict(kind='controlled_fixture_cleanup_not_peer_close',start=time.time(),start_monotonic=time.monotonic(),source_sha=os.environ['GITHUB_SHA'])
    private=Path(os.environ['RUNNER_TEMP'])/'private-gallery-vpn1762'
    stop=subprocess.run(['sudo','python3','scripts/evidence179_processes.py','stop','--identity',str(private/'server.identity.json')],capture_output=True,text=True)
    report['server_stop_status']=stop.stdout.strip();report['server_stop_exit_status']=stop.returncode
    if stop.returncode!=0 or report['server_stop_status']!='stopped':
        report.update(end=time.time(),end_monotonic=time.monotonic(),socket_cleanup='not_attempted_unverified_owner')
        path.write_text(json.dumps(report,sort_keys=True)+'\n')
        raise RuntimeError('Refuse socket drain without verified fixture process ownership')
    (private/'server.identity.json').unlink();(private/'server.pid').unlink(missing_ok=True)
    command=['sudo','ss','-K','state','all','src','203.0.113.10','dst','10.91.0.2']
    result=subprocess.run(command,capture_output=True,text=True);report['socket_destroy_exit_status']=result.returncode
    # The no-socket check is scoped to this disposable origin/peer. Raw capture still
    # remains strict; any late origin packet inside a subsequent Tor window fails.
    check=subprocess.run(['sudo','ss','-Htan','src','203.0.113.10','dst','10.91.0.2'],capture_output=True,text=True)
    report['socket_check_exit_status']=check.returncode;report['remaining_socket_lines']=len(check.stdout.splitlines())
    time.sleep(2)
    report.update(end=time.time(),end_monotonic=time.monotonic())
    path.write_text(json.dumps(report,sort_keys=True)+'\n')
    if result.returncode or stop.returncode or check.returncode or check.stdout.strip():raise RuntimeError('Controlled fixture drain failed')

if __name__=='__main__':drain()
