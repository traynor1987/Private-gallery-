"""Correlate disposable Android and onion-origin shutdown evidence."""
import json
import subprocess
import sys
import time
from pathlib import Path

WINDOW_NS = 20_000_000_000
UPTIME_RESOLUTION_NS = 10_000_000


def clock_sample():
    samples = []
    for _ in range(7):
        before = time.monotonic_ns()
        uptime = subprocess.check_output(['adb', 'shell', 'cat', '/proc/uptime'], text=True, timeout=10)
        after = time.monotonic_ns()
        seconds, hundredths = uptime.split()[0].split('.')
        android_ns = int(seconds) * 1_000_000_000 + int(hundredths) * UPTIME_RESOLUTION_NS
        samples.append(dict(before=before, after=after, android=android_ns,
                            lower=before-android_ns-UPTIME_RESOLUTION_NS, upper=after-android_ns))
    return min(samples, key=lambda sample: sample['after']-sample['before'])


def report(evidence, onion, marker_path):
    lock = json.loads(marker_path.read_text())
    for proof in ('session_locked', 'browser_retired', 'tor_retired', 'sink_released', 'old_socket_denied'):
        assert lock[proof] is True, f'Local proof missing: {proof}'
    start, end = [json.loads((evidence / f'clock-{phase}.json').read_text()) for phase in ('before', 'after')]
    trigger = lock['trigger_elapsed_ns']
    assert start['android'] <= trigger <= end['android'], 'Lock is outside clock calibration interval'
    lower = min(start['lower'], end['lower'])
    upper = max(start['upper'], end['upper'])
    assert upper-lower <= 250_000_000, 'Clock offset uncertainty exceeds 250 ms'
    # Retain late arrival evidence before the fixture is torn down. The verdict
    # uses the original window, not this diagnostic collection window.
    stop = trigger + upper + 40_000_000_000
    while True:
        events = [json.loads(row) for row in (onion / 'interactive-events.jsonl').read_text().splitlines()]
        events = [event for event in events if event.get('case') == lock['case']]
        if any(event['kind'] != 'slow-start' for event in events) or time.monotonic_ns() >= stop:
            break
        time.sleep(.1)
    (evidence / 'origin-case.json').write_text(json.dumps(events, indent=2) + '\n')
    starts = [event for event in events if event['kind'] == 'slow-start']
    assert len(starts) == 1 and starts[0]['monotonic_ns'] <= trigger + lower, 'Request was not proven open before lock'
    close = next((event for event in events if event['kind'] == 'slow-closed'), None)
    assert not any(event['kind'] == 'slow-complete' for event in events), 'Request completed normally'
    result = dict(case=lock['case'], socket_port=starts[0]['peer_port'],
                  lock=lock, maximum_clock_uncertainty_ns=upper-lower)
    if close:
        assert close['close_evidence'] in ('peer_eof', 'connection_reset', 'broken_pipe')
        result.update(closure_lower_ns=close['monotonic_ns']-trigger-upper,
                      closure_upper_ns=close['monotonic_ns']-trigger-lower,
                      previous_wall_clock_ms=close['at_ms']-lock['trigger_at_ms'],
                      close_evidence=close['close_evidence'])
    (evidence / 'shutdown-verdict.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, sort_keys=True))
    assert close and 0 <= result['closure_lower_ns'] <= result['closure_upper_ns'] <= WINDOW_NS, (
        'Live onion closure was not proven within 20 seconds using the full clock uncertainty')


if __name__ == '__main__':
    if len(sys.argv) == 2 and sys.argv[1] == '--sample':
        print(json.dumps(clock_sample()))
    else:
        report(*(Path(arg) for arg in sys.argv[1:]))
