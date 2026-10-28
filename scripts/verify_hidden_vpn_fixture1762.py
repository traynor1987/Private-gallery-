#!/usr/bin/env python3
"""Require observed real VPN HTTP bodies/DNS; never turn missing evidence into a skip."""
import json,re,sys,time
from pathlib import Path

root=Path(sys.argv[1])
deadline=time.monotonic()+5
while True:
 events=[json.loads(line) for line in (root/'events.jsonl').read_text().splitlines() if line]
 if any(event['kind']=='slow-closed' for event in events) or time.monotonic()>=deadline:break
 time.sleep(.1)
http=[event for event in events if event['kind']=='http']
assert http and all(event['source']=='10.91.0.2' for event in http),'Origin traffic was not exclusively from the ephemeral WireGuard peer'
for path,flag in [('/post','javascript_body'),('/form-result','form_body'),('/uploaded','multipart_png')]:
 assert any(event.get('path')==path and event.get('method')=='POST' and event.get(flag) is True for event in http),f'Missing real body observation: {path}'
assert any(event.get('path')=='/post' and event.get('javascript_body') is True and event.get('tls') is True for event in http),'No HTTPS CONNECT POST evidence'
assert any(event.get('path')=='/worker-post' and event.get('worker_body') is True and event.get('tls') is True for event in http),'No native service-worker HTTPS POST evidence'
assert any(event['kind']=='websocket' and event.get('tls') is True and event.get('exact_body') is True for event in events),'Missing real secure WebSocket frame observation'
assert any(event.get('probe_started') is True for event in events) and any(event.get('probe_complete') is True for event in events),'Missing completed native WebRTC route probe'
stun=[event for event in events if event['kind']=='stun']
assert stun,'No STUN traffic observed: completed ICE gathering alone does not prove VPN UDP routing'
assert all(event['source']=='10.91.0.2' for event in stun),'STUN bypassed the owned VPN route'
continuous=[event for event in http if event.get('continuous_started') is True]
barriers=[event for event in http if event.get('revocation_barrier') is True]
assert continuous and barriers,'Missing continuous native UDP / post-kill parent-network barrier'
start=continuous[-1]['time'];barrier=barriers[-1]['time']
revocation=json.loads((root/'vpn-native-revocation1762-result.json').read_text())
assert revocation.get('wireguard_retained') is True and revocation.get('owned_roots_removed') is True,'Missing retained VPN / offline cleanup proof'
assert revocation.get('post_barrier_stun')==0,'Native test observed post-revocation UDP'
assert 0<=revocation['death_observed_ms']-revocation['lock_return_ms']<1000,'Native child did not die promptly after lock'
assert abs(revocation['revocation_barrier_seconds']-barrier)<.000001,'Revocation marker source mismatch'

assert barrier>start and sum(start<event['time']<barrier for event in stun)>=3,'UDP flow was not live across the revocation exercise'
assert not any(event['time']>barrier+.15 for event in stun),'Native UDP continued after the confirmed-child-death barrier'
assert not any(event.get('name')=='continuous-phase.fixture.invalid' and event['time']>barrier+.15 for event in events if event['kind']=='dns'),'Native UDP DNS continued after revocation'

assert any(event.get('path')=='/media.png' and event.get('method')=='GET' for event in http),'Missing supported GET media download observation'
assert any(event['kind']=='slow-start' for event in events),'Missing in-flight revocation fixture request'
assert any(event['kind']=='slow-closed' for event in events),'Revocation did not close the observed upstream socket'
assert any(event['kind']=='dns' and event.get('name')=='native-phase.fixture.invalid' and event.get('qtype')==1 and event['source']=='10.91.0.2' for event in events),'No VPN-owned DNS query observed'
packets=(root/'packets.txt').read_text()
records=re.split(r'(?=^\d{4}-\d\d-\d\d )',packets,flags=re.M)
fixture_dns=[record for record in records if re.search(r'\bfixture\.invalid\.',record)]
assert fixture_dns,'No packet-level fixture DNS evidence'
assert all('pg1762' in record for record in fixture_dns),'Fixture DNS appeared outside WireGuard interface'
origin_packets=[record for record in records if '203.0.113.10' in record]
assert origin_packets and all('pg1762' in record for record in origin_packets),'Origin traffic attempted a non-WireGuard route'
assert (root/'traffic.pcap').stat().st_size>24,'Missing packet capture'
(root/'result.txt').write_text('PASS: actual JavaScript POST, HTML form POST and multipart bodies; WireGuard-only origin and fixture DNS observed; in-flight revocation fixture exercised. Instrumentation results separately required for cancellation/profile/mode-switch assertions.\n')
print((root/'result.txt').read_text().strip())
