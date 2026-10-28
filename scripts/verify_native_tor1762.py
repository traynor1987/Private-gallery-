#!/usr/bin/env python3
"""Require native isolated Tor body evidence and deny-all sink facts from exact-run tests."""
import json
import re
import sys
import time
from pathlib import Path


def verify(evidence, onion_root):
    result=json.loads((evidence/'native-tor1762-result.json').read_text())
    assert result.get('mode')=='TOR_ONLY' and result.get('javascript') is False,'Wrong native Tor mode/settings'
    for key in ('form','multipart','download_verified','discard_v4','discard_v6','discard_dns_udp','session_revoked','tor_process_death'):
        assert result.get(key) is True, f'Missing asserted native Tor boundary: {key}'
    deadline=time.monotonic()+5
    while True:
        events=[json.loads(line) for line in (onion_root/'interactive-events.jsonl').read_text().splitlines() if line]
        if any(event.get('kind')=='slow-closed' for event in events) or time.monotonic()>=deadline:
            break
        time.sleep(.1)
    for path,flag in (('/form-result','form_ok'),('/uploaded','multipart_png')):
        assert any(event.get('path')==path and event.get('method')=='POST' and event.get(flag) is True for event in events),f'Missing actual Tor body: {path}'
    for path in ('/script-probe','/media.png','/cookie-echo'):
        assert any(event.get('path')==path and event.get('method')=='GET' for event in events),f'Missing native Tor GET: {path}'
    assert any(event.get('path')=='/cookie-echo' and event.get('cookie_present') is True for event in events),'Tor cookies not exercised'
    assert not any(event.get('path')=='/script-must-not-run' for event in events),'Tor JavaScript unexpectedly executed'
    assert any(event.get('kind')=='slow-start' for event in events),'Tor revocation not exercised in flight'
    assert any(event.get('kind')=='slow-closed' for event in events),'Tor revocation did not close upstream'
    packets=(evidence/'packets.txt').read_text()
    assert 'private-gallery-tor-sink-only' not in packets,'Direct Tor-mode UDP marker escaped the sink'
    assert not re.search(r'\btor\.invalid\.',packets),'Tor-mode DNS probe escaped the sink'
    assert not re.search(r'\b[a-z2-7]{56}\.onion\.',packets),'Onion hostname appeared in external DNS packet'
    assert (evidence/'traffic.pcap').stat().st_size>24,'Missing packet observation'
    return 'PASS: isolated packaged-Tor native forms/uploads/verified GET media, JavaScript-off, private cookies, actual v4/v6/UDP/DNS sink attempts and in-flight revocation. Separate Android assertions and source review are required; this does not prove physical-device acceptance.'


if __name__=='__main__':
    print(verify(Path(sys.argv[1]),Path(sys.argv[2])))
