#!/usr/bin/env python3
"""Require native isolated Tor body evidence and deny-all sink facts from exact-run tests."""
import json
import re
import sys
import time
from pathlib import Path


CLOSE_WINDOW_MS = 20_000


def close_observation(events, marker, label):
    token = marker['case']
    trigger = marker['trigger_at_ms']
    selected = [event for event in events if event.get('case') == token]
    starts = [event['at_ms'] for event in selected if event.get('kind') == 'slow-start']
    assert len(starts) == 1 and starts[0] <= trigger, f'{label}: request not independently open at trigger'
    assert not any(event.get('kind') == 'slow-complete' for event in selected), f'{label}: request completed normally'
    closes = [event for event in selected if event.get('kind') == 'slow-closed']
    timely = [event for event in closes if trigger <= event['at_ms'] <= trigger + CLOSE_WINDOW_MS]
    assert len(timely) == 1 and timely[0].get('close_evidence') in ('peer_eof', 'connection_reset', 'broken_pipe'), (
        f'{label}: no peer closure within {CLOSE_WINDOW_MS} ms of trigger; observed={closes}'
    )
    return timely[0]['at_ms'] - trigger


def verify(evidence, onion_root, *, require_production_lock=False):
    result=json.loads((evidence/'native-tor1762-result.json').read_text())
    assert result.get('mode')=='TOR_ONLY' and result.get('javascript') is False,'Wrong native Tor mode/settings'
    for key in ('form','multipart','download_verified','discard_v4','discard_v6','discard_dns_udp','session_revoked','tor_process_death'):
        assert result.get(key) is True, f'Missing asserted native Tor boundary: {key}'
    lock_file = evidence / 'native-tor1762-lock.json'
    transitions = {}
    if require_production_lock or lock_file.is_file():
        lock = json.loads(lock_file.read_text())
        for key in ('session_locked', 'browser_retired', 'tor_retired', 'sink_released', 'old_socket_denied'):
            assert lock.get(key) is True, f'Missing production Hidden lock boundary: {key}'
        assert lock['case'].startswith('lock-') and result['case'].startswith('loss-'), 'Unexpected Tor evidence cases'
        transitions = {'Hidden session lock': lock, 'Abrupt Tor transport loss': result}

    # This runs while the fixture is still live. Anchor the deadline to the cause,
    # since Gradle and the second test may finish well after the first request closed.
    deadline = time.monotonic() + 20
    while True:
        events = [json.loads(line) for line in (onion_root/'interactive-events.jsonl').read_text().splitlines() if line]
        closures = [event for event in events if event.get('kind') == 'slow-closed']
        if transitions:
            now_ms = time.time_ns() // 1_000_000
            if all(now_ms >= marker['trigger_at_ms'] + CLOSE_WINDOW_MS or
                   any(event.get('case') == marker['case'] for event in closures)
                   for marker in transitions.values()):
                break
        elif closures:
            break
        if time.monotonic() >= deadline:
            break
        time.sleep(.1)
    for path,flag in (('/form-result','form_ok'),('/uploaded','multipart_png')):
        assert any(event.get('path')==path and event.get('method')=='POST' and event.get(flag) is True for event in events),f'Missing actual Tor body: {path}'
    for path in ('/script-probe','/media.png','/cookie-echo'):
        assert any(event.get('path')==path and event.get('method')=='GET' for event in events),f'Missing native Tor GET: {path}'
    assert any(event.get('path')=='/cookie-echo' and event.get('cookie_present') is True for event in events),'Tor cookies not exercised'
    assert not any(event.get('path')=='/script-must-not-run' for event in events),'Tor JavaScript unexpectedly executed'
    delays = {label: close_observation(events, marker, label) for label, marker in transitions.items()}
    if not transitions:
        assert any(event.get('kind')=='slow-start' for event in events),'Tor revocation not exercised in flight'
        assert any(event.get('kind')=='slow-closed' for event in events),'Tor revocation did not close upstream'
    packets=(evidence/'packets.txt').read_text()
    assert 'private-gallery-tor-sink-only' not in packets,'Direct Tor-mode UDP marker escaped the sink'
    assert not re.search(r'\btor\.invalid\.',packets),'Tor-mode DNS probe escaped the sink'
    assert not re.search(r'\b[a-z2-7]{56}\.onion\.',packets),'Onion hostname appeared in external DNS packet'
    assert (evidence/'traffic.pcap').stat().st_size>24,'Missing packet observation'
    timings = '; '.join(f'{label} closed upstream in {ms} ms' for label, ms in delays.items())
    return ('PASS: packaged-Tor forms/uploads/downloads, JavaScript-off, isolated cookies, and v4/v6/UDP/DNS sink. '
            + (timings or 'In-flight revocation observed.'))


if __name__=='__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('evidence', type=Path)
    parser.add_argument('onion_root', type=Path)
    parser.add_argument('--require-production-lock', action='store_true')
    args = parser.parse_args()
    print(verify(args.evidence, args.onion_root, require_production_lock=args.require_production_lock))
