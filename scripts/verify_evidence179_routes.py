#!/usr/bin/env python3
"""Fail-closed controlled-route proof. Inputs are observations, never self-declared PASS gates."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
from cryptography import x509
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import hashes
try:
    from verify_evidence179_cases import verify as verify_cases
    from evidence179_packet_policy import host_fixture_adb_multicast
except ModuleNotFoundError:
    from scripts.verify_evidence179_cases import verify as verify_cases
    from scripts.evidence179_packet_policy import host_fixture_adb_multicast

CASES = {
    'vpn_revoke': ('ownedVpnPostCancellationAndAuthorityRevocationNeverFallback', 'VPN', ['success', 'cancel', 'revoke', 'retired']),
    'vpn_loss': ('ownedVpnLossCancelsAdmittedPostAndRejectsOldRoute', 'VPN', ['success', 'loss', 'retired']),
    'tor_revoke': ('privateUnixTorPostCancellationAndAuthorityRevocationNeverFallback', 'Tor', ['success', 'cancel', 'revoke', 'retired']),
    'tor_death': ('torProcessDeathCancelsAdmittedPostAndRejectsOldRoute', 'Tor', ['success', 'death', 'retired']),
}
CLASS = 'uk.co.traynor.privategallery.core.evidence.EvidenceOwnedRoute179Test'
RESPONSE = b'evidence179-post-accepted\n'


def require(value, reason):
    if not value:
        raise ValueError(reason)


def sha(value):
    return hashlib.sha256(value).hexdigest()


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'Duplicate JSON key')
        result[key] = value
    return result


def decode(value):
    return json.loads(value, object_pairs_hook=unique_object, parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite JSON')))


def read(path):
    return decode(Path(path).read_text())


def lines(path):
    return [decode(line) for line in Path(path).read_text().splitlines()]


def check_events(events):
    require(bool(events), 'Missing independent endpoint observations')
    require([e['sequence'] for e in events] == list(range(1, len(events)+1)), 'Duplicate, missing or reordered event sequence')
    require(all(isinstance(e['epoch'], (int, float)) and e['epoch'] > 0 for e in events), 'Invalid event time')
    require(all(a['monotonic'] <= b['monotonic'] for a, b in zip(events, events[1:])), 'Reordered host events')


def genuine_close(event):
    allowed={('BrokenPipeError',32):'broken_pipe',('ConnectionResetError',104):'connection_reset',
             ('SSLEOFError',8):'tls_eof',('SSLZeroReturnError',6):'tls_close_notify',('peer_eof',0):'tls_eof'}
    return allowed.get((event.get('close_type'),event.get('close_errno'))) == event.get('close_evidence') and event.get('close_evidence') is not None


def check_action(action,arm,terminal=None):
    clock=action['arm']
    require(clock['sequence']==arm['sequence'] and clock['token']==arm['action_token'] and re.fullmatch(r'[a-f0-9]{32}',clock['token']) is not None,'Forged independent action receipt')
    sent=clock['sent_elapsed_ns'];received=clock['received_elapsed_ns'];at=action['action_elapsed_ns'];failed=action['request_failed_elapsed_ns']
    require(all(type(x) is int for x in (sent,received,at,failed)) and 0<sent<=received<=at<=failed, 'Pre-action request failure or invalid device causal order')
    require(action['live_at_action'] is True and action['published'] is False, 'Request was not live or published a response')
    require(failed-at<=20_000_000_000, 'Unbounded actual request failure')
    if terminal is not None:
        # Host arm timestamp occurred somewhere inside the device send/receive
        # interval. This upper bound is conservative for clock offset; require
        # closure later than every possible action time, with5ms outward margin.
        upper=arm['monotonic']+(at-sent)/1_000_000_000+.005
        require(terminal['monotonic']>=upper, 'Host closure not independently ordered after destructive action')
        require(0<=terminal['monotonic']-arm['monotonic']<60, 'Negative or unbounded host close duration')
        require(terminal['epoch']>=arm['epoch'], 'Negative host wall-clock close duration')


def check_case(value, events, dns, onion, source, window):
    case=value['case'];require(case in CASES,'Unknown Android route case')
    method,mode,phases=CASES[case]
    require(value['schema']==1 and value['method']==method and value['classname']==CLASS and value['mode']==mode,'Case identity mismatch')
    require(value['source_sha']==source and value['owner']=='HIDDEN','Source/owner mismatch')
    nonce=value['nonce'];require(re.fullmatch(r'[a-f0-9]{32}',nonce) is not None,'Invalid freshness nonce')
    require(re.fullmatch(r'[a-f0-9-]{36}',value['route_identity']) is not None,'Missing route identity')
    require(type(value['uid']) is int and value['uid']>0 and value['network_owner_uid']==value['uid'],'Unowned Android Network')
    require(type(value['network_handle']) is int and value['network_handle']>0 and bool(value['interface']),'Missing pinned Network/interface')
    for key in ('process_binding_before_null','process_binding_after_null','cleanup_retired'):require(value.get(key) is True,'Missing cleanup/process observation '+key)
    require([x['phase'] for x in value['operations']]==phases,'Missing/duplicate/unexpected submitted phase')
    selected=[e for e in events if e.get('nonce')==nonce]
    require(all(e['case']==case and e['phase'] in phases[:-1] for e in selected),'Unexpected/retired/foreign endpoint receipt')
    all_dns=[e for e in dns if nonce in e.get('name','')]
    for operation in value['operations']:
        phase=operation['phase'];host=onion if mode=='Tor' else f'e179-{nonce}-{phase}.fixture.invalid'
        path='/evidence179/post' if phase=='success' else f'/evidence179/slow/{case}/{phase}'
        body=f'{{"case":"{case}","nonce":"{nonce}","phase":"{phase}"}}'.encode()
        expected=dict(host=host,path=path,body_length=len(body),body_sha256=sha(body))
        require(all(operation.get(k)==v for k,v in expected.items()),'Forged Android request digest/endpoint')
        observed=[e for e in selected if e['phase']==phase];queries=[e for e in all_dns if e['name']==host]
        if phase=='retired':
            require(not observed and not queries,'Retired route reached DNS/origin');continue
        kinds=[e['kind'] for e in observed]
        if phase=='success':require(kinds==['post','response'],'Missing/duplicated successful POST')
        elif case=='vpn_loss' and phase=='loss':
            require(kinds[:3]==['post','slow-start','action-arm'] and len(kinds) in (3,4),'Missing/duplicated loss admission/action')
            if len(kinds)==4:require(kinds[-1] in ('slow-closed','slow-timeout','slow-complete','slow-io-error'),'Invalid loss terminal observation')
        else:require(kinds==['post','slow-start','action-arm','slow-closed'],'Missing, duplicated, retried, pre-action or unclosed POST')
        for event in observed:
            require(all(event.get(k)==v for k,v in expected.items()),'Host/Android receipt mismatch')
            require(event.get('method')=='POST' and event.get('tls') is True and event.get('auth_present') is False and event.get('cookie_present') is False,'Unauthorised headers or plaintext receipt')
            require(event['source']==('127.0.0.1' if mode=='Tor' else '10.91.0.2'),'Incorrect fixture source')
            if not (case=='vpn_loss' and phase=='loss' and event is observed[-1] and len(observed)==4):
                require(window['start']<=event['epoch']<=window['end'],'Stale endpoint receipt outside host phase')
            if event['kind']=='slow-closed':require(genuine_close(event),'No genuine peer EOF/reset/broken-pipe observation')
        require(all(a['monotonic']<=b['monotonic'] and a['epoch']<=b['epoch'] for a,b in zip(observed,observed[1:])),'Negative host event duration')
        if phase=='success':
            require(observed[1]['response_sha256']==sha(RESPONSE)==value['response_sha256'],'Response hash mismatch')
            require(observed[1]['sequence']==value['success_observation'],'Forged success observation')
        else:
            action=value['cancel'] if phase=='cancel' else value['retirement']
            terminal=observed[3] if len(observed)==4 else None
            check_action(action,observed[2],terminal)
            if phase=='cancel':require(action['start']==observed[1]['sequence'] and action['closed']==observed[3]['sequence'] and action['cancelled'] is True,'Cancellation observation mismatch')
            else:
                require(action['phase']==phase and action['admitted_sequence']==observed[1]['sequence'] and action['admitted_epoch']==observed[1]['epoch'],'Retirement before/missing actual admission')
                require(0<action['admission_observed_elapsed_ns']<=action['arm']['sent_elapsed_ns'],'Action before observed admission')
        if mode=='VPN':require({e['qtype'] for e in queries}=={1,28} and all(e['source']=='10.91.0.2' and window['start']<=e['time']<=window['end'] for e in queries),'Missing fresh peer A/AAAA DNS')
    retirement=value['retirement']
    require(retirement['request_failed_elapsed_ns']<=retirement['transport_retired_elapsed_ns'] and retirement['transport_retired_elapsed_ns']-retirement['action_elapsed_ns']<60_000_000_000,'Unbounded/reordered transport retirement')
    require(retirement['active_failed'] is True and retirement['old_route_current'] is False and retirement['new_post_rejected'] is True,'Retirement did not revoke route')
    require(retirement['credentials_retained'] is (case in ('vpn_loss','tor_death')),'Wrong independent credential lifetime')
    require(retirement['remote_close']==('unavailable_after_path_loss' if case=='vpn_loss' else 'required_peer_close'),'Incorrect remote-close claim/precondition')
    if mode=='VPN':require(retirement['wireguard_retained'] is (case=='vpn_revoke') and retirement['network_lost'] is (case=='vpn_loss'),'WireGuard authority/loss not independently tested')
    else:
        require(not all_dns and retirement['tor_absent'] is True,'Tor fallback DNS or remaining process')
        tor=value['tor'];require(tor['uid']==tor['parent_uid']==tor['socket_uid']==value['uid'] and tor['pid']>0,'Wrong Unix/process owner')
        require(tor['parent_mode']==448 and tor['socket_type']==49152 and tor['unix_ready'] is True and tor['bootstrap']==100,'Unverified private Unix endpoint')
        require(tor['unix_relative'].startswith('ts/') and '..' not in tor['unix_relative'].split('/') and not tor['unix_relative'].startswith('/'),'Invalid private relative path')
        require(value['sink']['v4']>0 and value['sink']['v6']>0 and value['sink']['udp']>=3,'Sink attempts not observed')
    return nonce


def packet_epoch(record):
    match = re.match(r'^(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d(?:\.\d+)?)', record)
    require(match is not None, 'Packet timestamp missing')
    return datetime.datetime.fromisoformat(match[1]).replace(tzinfo=datetime.timezone.utc).timestamp()


def check_packets(packets, values, windows, onion, capture):
    records = [x for x in re.split(r'(?=^\d{4}-\d\d-\d\d )', packets, flags=re.M) if re.match(r'^\d{4}-', x)]
    require(bool(records), 'Empty raw packet observation')
    require(all(capture['start'] <= packet_epoch(x) <= capture['end'] for x in records), 'Stale/out-of-window packet evidence')
    require(not any(onion in x or re.search(r'e179-[a-f0-9]{32}\.fixture\.invalid', x) for x in records), 'Tor name or sink DNS escaped')
    origin = [x for x in records if '203.0.113.10' in x and not host_fixture_adb_multicast(x)]
    require(bool(origin) and all(' pg1762 ' in x.splitlines()[0] for x in origin), 'Direct fixture-origin packet outside owned WireGuard')
    require(not any('2001:db8::10' in x for x in records), 'IPv6 sink packet escaped')
    fixture_dns = [x for x in records if re.search(r'\bfixture\.invalid\.', x)]
    require(bool(fixture_dns) and all(' pg1762 ' in x.splitlines()[0] for x in fixture_dns), 'Fixture DNS packet outside owned WireGuard')
    for case, value in values.items():
        window = windows[case]
        require(capture['start'] <= window['start'] < window['end'] <= capture['end'], 'Missing timely case capture')
        active = [x for x in records if window['start'] <= packet_epoch(x) <= window['end']]
        if value['mode'] == 'Tor':
            require(not any(('203.0.113.10' in x and not host_fixture_adb_multicast(x)) or 'fixture.invalid' in x or '2001:db8::10' in x for x in active), 'Direct fixture traffic during exact Tor host phase')
        else:
            require(any('203.0.113.10.443' in x and ' pg1762 ' in x.splitlines()[0] for x in active), 'Missing pinned HTTPS packets')
            for op in value['operations'][:-1]:
                for kind in (' A? ', ' AAAA? '):
                    require(any(op['host']+'.' in x and kind in x and '10.91.0.2.' in x for x in active), 'Missing packet-level fresh A/AAAA question')


def certificate_identity(root, expected_hosts):
    leaf_bytes=(root/'fixture.crt').read_bytes();ca_bytes=(root/'fixture-ca.crt').read_bytes()
    leaf=x509.load_pem_x509_certificate(leaf_bytes);ca=x509.load_pem_x509_certificate(ca_bytes)
    san=leaf.extensions.get_extension_for_class(x509.SubjectAlternativeName).value.get_values_for_type(x509.DNSName)
    require(san == expected_hosts and leaf.issuer == ca.subject, 'Wrong fixture SAN/issuer')
    ca.public_key().verify(leaf.signature, leaf.tbs_certificate_bytes, ec.ECDSA(leaf.signature_hash_algorithm))
    # Bind the actual debug trust anchor's public key, not a caller-supplied replacement CA.
    trusted=x509.load_pem_x509_certificate(Path('app/src/debug/res/raw/hidden_vpn_fixture_ca.crt').read_bytes())
    from cryptography.hazmat.primitives import serialization
    public=lambda cert:cert.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
    require(public(ca)==public(trusted), 'Untrusted fixture CA')
    return dict(san=san, leaf_sha256=sha(leaf_bytes), ca_sha256=sha(ca_bytes), leaf_der_sha256=leaf.fingerprint(hashes.SHA256()).hex())


def verify(args):
    require(re.fullmatch(r'[a-f0-9]{40}', args.source_sha) is not None, 'Invalid source SHA')
    manifest=read(args.cases);count=verify_cases(args.android_results,manifest)
    expected={(CLASS, v[0]) for v in CASES.values()}
    require(expected <= {(x['classname'], x['name']) for x in manifest}, 'Manifest omits actual route cases')
    root=Path(args.results_root);vpn=Path(args.vpn_root);onion_root=Path(args.onion_root)
    require((root/'source-sha.txt').read_text().strip()==args.source_sha, 'Original source SHA mismatch')
    files=list(root.glob('evidence179-route-*.json'));require(len(files)==4, 'Missing/duplicate route outputs')
    values={}
    for path in files:
        value=read(path);case=value['case'];require(case not in values and path.name==f'evidence179-route-{case}.json', 'Duplicate/misnamed route output');values[case]=value
    require(set(values)==set(CASES), 'Incorrect route selection')
    phases=lines(root/'host-phases.jsonl');require(len(phases)==8, 'Missing/duplicate host barriers')
    windows={}
    for case in CASES:
        observations=[x for x in phases if x['case']==case]
        require([x['kind'] for x in observations]==['start','end'] and all(x['source_sha']==args.source_sha for x in observations), 'Invalid host case barrier')
        require(observations[1]['original_exit_status']==0 and observations[1]['peer_close_exit_status']==0, 'Original Android invocation or independent close barrier failed')
        windows[case]=dict(start=observations[0]['epoch'],end=observations[1]['epoch'])
    ordered=sorted(windows.values(),key=lambda x:x['start']);require(all(a['end']<b['start'] for a,b in zip(ordered,ordered[1:])), 'Overlapping host phases')
    drain=read(root/'vpn-controlled-drain.json')
    require(drain['kind']=='controlled_fixture_cleanup_not_peer_close' and drain['source_sha']==args.source_sha, 'Missing honest controlled VPN cleanup')
    require(windows['vpn_loss']['end']<drain['start']<drain['end']<windows['tor_revoke']['start'] and 0<=drain['end_monotonic']-drain['start_monotonic'], 'Cleanup contaminated a proof window')
    require(drain['socket_destroy_exit_status']==drain['socket_check_exit_status']==drain['server_stop_exit_status']==0 and drain['remaining_socket_lines']==0 and drain['server_stop_status']=='stopped', 'Unverified fixture drain')
    readiness=read(onion_root/'evidence179-tls-readiness.json');onion=readiness['hostname']
    require(re.fullmatch(r'[a-z2-7]{56}\.onion', onion) is not None, 'No exact generated onion hostname')
    require(readiness['url']==f'https://{onion}/evidence179/ready' and readiness['tls_verified'] is True and readiness['independent_socks5h'] is True and readiness['exit_status']==0, 'Missing independent HTTPS readiness')
    require(readiness['sha256']==sha((onion_root/'evidence179-ready').read_bytes()) and readiness['bytes']==(onion_root/'evidence179-ready').stat().st_size, 'Wrong readiness body')
    require(0<min(w['start'] for w in windows.values())-readiness['epoch']<3600, 'Stale or postdated readiness')
    certs=dict(onion=certificate_identity(onion_root,[onion]),vpn=certificate_identity(vpn,['fixture.invalid','*.fixture.invalid']))
    require(all(readiness[k]==certs['onion'][k] for k in ('leaf_sha256','ca_sha256')), 'Readiness certificate mismatch')
    vpn_events=lines(vpn/'evidence179-events.jsonl');tor_events=lines(onion_root/'evidence179-events.jsonl');dns=[e for e in lines(vpn/'events.jsonl') if e['kind']=='dns']
    check_events(vpn_events);check_events(tor_events)
    nonces=[check_case(value,tor_events if value['mode']=='Tor' else vpn_events,dns,onion,args.source_sha,windows[case]) for case,value in values.items()]
    require(len(set(nonces))==4 and len({v['route_identity'] for v in values.values()})==4, 'Reused nonce/route identity')
    require(all(e['nonce'] in nonces for e in vpn_events+tor_events), 'Foreign endpoint observations')
    handshakes=(vpn/'handshakes.txt').read_text().splitlines();require(bool(handshakes) and all(int(x.split()[-1])>0 for x in handshakes), 'No actual WireGuard handshake')
    firewall=(vpn/'input-firewall.txt').read_text();require(re.search(r'REJECT\s+all\s+--\s+!pg1762\s+\*\s+0\.0\.0\.0/0\s+203\.0\.113\.10',firewall) is not None, 'Missing origin firewall boundary')
    require((vpn/'traffic.pcap').stat().st_size>24, 'Missing raw capture')
    capture=read(root/'capture.json');require(capture['stopped'] is True and capture['ownership']=='stopped', 'Capture still live/unflushed/unowned')
    process=read(root/'capture-process.json');original_pcap=str(Path('app/build/hidden-vpn1762/traffic.pcap').resolve())
    require(process['pid']>0 and process['start_ticks']>0 and bool(process['boot_id']) and Path(process['argv'][0]).name=='tcpdump' and original_pcap in process['argv'] and original_pcap in process['outputs'], 'Missing exact capture start/output ownership identity')
    require(all(capture['start'] <= int(x.split()[-1]) <= capture['end'] for x in handshakes), 'Stale WireGuard handshake')
    require(re.search(r'^0 packets dropped by kernel$', (vpn/'capture.log').read_text(), re.M) is not None, 'Capture dropped packets or never flushed')
    command=subprocess.run(['tcpdump','-tttt','-nn','-vv','-X','-r',str(vpn/'traffic.pcap')],capture_output=True,text=True,check=True,env={**os.environ,'TZ':'UTC'})
    packets=command.stdout
    check_packets(packets,values,windows,onion,capture)
    (vpn/'packets.txt').write_text(packets)
    bound={}
    paths=files+list(Path(args.android_results).rglob('*.xml'))+[Path(args.cases),root/'source-sha.txt',root/'host-phases.jsonl',root/'capture.json',root/'capture-process.json',root/'vpn-controlled-drain.json']
    paths += [p for folder in (vpn,onion_root) for p in folder.iterdir() if p.is_file() and p.suffix in ('.pcap','.jsonl','.json','.crt','.txt','.log')]
    paths += [p for p in (root/'android-output').rglob('*') if p.is_file()]
    paths += [Path('scripts')/x for x in ('verify_evidence179_routes.py','verify_evidence179_cases.py','evidence179_packet_policy.py','evidence179_fixture_http.py','evidence179_fixture_endpoints.py','ci_onion_probe.py','create_hidden_vpn_fixture_tls1762.py','hidden_vpn_fixture1762.py','hidden_tor_fixture1762.py','prepare_evidence179_routes.sh','run_evidence179.sh','cleanup_evidence179_routes.sh','archive_evidence179_routes.py','wait_evidence179_peer_close.py','drain_evidence179_vpn.py','evidence179_processes.py','setup_hidden_vpn_fixture1762.sh','cleanup_hidden_vpn_fixture1762.sh')]
    paths += [Path('docs/release179/work/task-7a-route-report.md'),Path('app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceOwnedRoute179Test.kt')]
    paths += [Path('app/src/main/java/uk/co/traynor/privategallery/core/evidence')/x for x in ('EvidenceNetwork.kt','EvidenceRouteCoordinator.kt')]
    paths += [Path('app/src/main/java/uk/co/traynor/privategallery/core/vpn/OwnedVpnDns.kt')]
    paths += [Path('app/src/debug/res/xml')/x for x in ('hidden_test_network_security.xml','hidden_network_security.xml')]
    for path in paths:
        require(path.is_file(), 'Missing bound evidence '+str(path));bound[str(path)]=sha(path.read_bytes())
    proof=dict(schema=1,decision='PASS',source_sha=args.source_sha,original_exit_status=0,android_cases=count,controlled_route_cases=list(CASES),remote_close_contract={'reachable_cancel_revoke_death':'required independently ordered peer close','vpn_loss':'unavailable_after_path_loss; no remote-close claim; subsequent host drain is cleanup only'},certificates=certs,hashes=bound,
               limits='Controlled Android17 emulator route assertions only; not arbitrary traffic absence or physical-device acceptance.')
    (root/'route-proof.json').write_text(json.dumps(proof,sort_keys=True,indent=2)+'\n')
    return proof


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('android-results','vpn-root','onion-root','results-root','source-sha','cases'):
        parser.add_argument('--'+name,required=True)
    args=parser.parse_args()
    # Stale PASS cannot survive a failed validation attempt.
    (Path(args.results_root)/'route-proof.json').unlink(missing_ok=True)
    verify(args)
    print('PASS: four actual owned EvidenceRoute cases cross-observed with HTTPS fixtures, fresh DNS and flushed raw capture')

if __name__=='__main__':main()
