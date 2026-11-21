"""Bound Android BOOTTIME timestamps on the disposable runner's monotonic clock."""
import json
import subprocess
import time

UPTIME_TICK_NS = 10_000_000
MAX_UNCERTAINTY_NS = 250_000_000


def sample():
    samples = []
    for _ in range(7):
        before = time.monotonic_ns()
        uptime = subprocess.check_output(['adb', 'shell', 'cat', '/proc/uptime'], text=True, timeout=10)
        after = time.monotonic_ns()
        seconds, hundredths = uptime.split()[0].split('.')
        assert len(hundredths) == 2, 'Unexpected Android uptime precision'
        device = int(seconds) * 1_000_000_000 + int(hundredths) * UPTIME_TICK_NS
        samples.append(dict(before=before, after=after, android=device))
    return min(samples, key=lambda item: item['after']-item['before'])


def bounds(directory, markers):
    before, after = [json.loads((directory / f'tor-clock-{phase}.json').read_text())
                     for phase in ('before', 'after')]
    assert before['after'] <= after['before'], 'Runner monotonic clock samples are reversed'
    assert before['android'] < after['android'], 'Android monotonic clock samples are reversed'
    for marker in markers:
        assert before['android'] + UPTIME_TICK_NS <= marker['trigger_elapsed_ns'] <= after['android'], (
            'Tor trigger is outside both clock samples')
    for item in (before, after):
        assert 0 < item['before'] <= item['after'], 'Invalid runner monotonic clock sample'
    minimum = min(item['before']-item['android']-UPTIME_TICK_NS for item in (before, after))
    maximum = max(item['after']-item['android'] for item in (before, after))
    assert 0 < maximum-minimum <= MAX_UNCERTAINTY_NS, 'Tor clock calibration is too uncertain'
    return minimum, maximum


if __name__ == '__main__':
    print(json.dumps(sample()))
