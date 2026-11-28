#!/usr/bin/env python3
"""Drive the authentic previous production build's UI against the public feed on disposable API37.

No instrumentation, substituted transport, secondary test app, app data editing, uninstall,
or replacement adb installation is used. The Android installer performs the update.
The protected production rehearsal separately verifies populated encrypted records.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import urllib.request
import urllib.error
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
from fetch_code81_upgrade_baseline import fetch
from novaos_build97_gate import BASELINE_DIGEST, fetch_build96
from novaos_build98_rehearsal import BASELINE_DIGEST as BUILD97_DIGEST, fetch_baseline as fetch_build97

PACKAGE = 'uk.co.traynor.privategallery'
PIN = '738291'  # Synthetic disposable owner, never a real owner credential.
REPO = 'traynor1987/Private-gallery-'

def require(ok, message):
    if not ok:
        raise RuntimeError(message)

def adb(*args):
    return subprocess.check_output(['adb', *map(str, args)], text=True, timeout=60 if args and args[0] == 'install' else 35).strip()

def wait_device_ready():
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        try:
            if adb('shell', 'getprop', 'sys.boot_completed') == '1' and adb('shell', 'cmd', 'package', 'path', 'android').startswith('package:'):
                adb('shell', 'ls', '/sdcard')  # FUSE must be mounted before any real APK installation.
                return
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
            pass
        time.sleep(2)
    raise RuntimeError('Disposable emulator package/storage services did not become ready')

def installed():
    match = re.search(r'versionCode=(\d+)\b', adb('shell', 'dumpsys', 'package', PACKAGE))
    return int(match[1]) if match else None

def tree():
    # Android can expose a null accessibility root during a real Activity/package restart.
    # Each attempt deletes the old dump, so no stale hierarchy can satisfy an assertion.
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        try:
            adb('shell', 'rm', '-f', '/sdcard/nova85-live-ui.xml')
            adb('shell', 'uiautomator', 'dump', '--compressed', '/sdcard/nova85-live-ui.xml')
            return ET.fromstring(adb('shell', 'cat', '/sdcard/nova85-live-ui.xml'))
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired, ET.ParseError):
            time.sleep(1)
    raise RuntimeError('Android did not expose a fresh accessibility root after restart')

def labels(root):
    return [value for n in root.iter('node') for key in ('text', 'content-desc') if (value := n.get(key))]

def has(root, text):
    return text in labels(root)

def tap(node):
    require(node.get('enabled') != 'false', 'Refusing disabled UI action')
    bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
    require(bounds is not None, 'Missing observed UI bounds')
    x1, y1, x2, y2 = map(int, bounds.groups())
    require(x2 > x1 and y2 > y1, 'UI action is not visible')
    adb('shell', 'input', 'tap', (x1+x2)//2, (y1+y2)//2)

def action_nodes(root, text):
    return [n for n in root.iter('node') if n.get('class') != 'android.widget.EditText' and (n.get('text') == text or n.get('content-desc') == text)]

def click(root, text):
    matches = action_nodes(root, text)
    require(matches, 'Missing UI action: ' + text)
    matches.sort(key=lambda n: n.get('clickable') != 'true')
    tap(matches[0])

def wait_for(predicate, label, seconds=60):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        root = tree()
        if predicate(root):
            return root
        time.sleep(1)
    raise RuntimeError('UI timeout: ' + label)

def fields(root):
    return [n for n in root.iter('node') if n.get('class') == 'android.widget.EditText']

def enter(node, value):
    tap(node)
    try:
        adb('shell', 'input', 'text', value)
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
        raise RuntimeError('Synthetic credential/input action failed') from None
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')

def scroll(root, reverse=False):
    visible = [n for n in root.iter('node') if n.get('scrollable') == 'true']
    require(visible, 'No observed scroll container')
    b = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', visible[-1].get('bounds', ''))
    require(b is not None, 'Scrollable bounds missing')
    x1, y1, x2, y2 = map(int, b.groups())
    high, low = y1+(y2-y1)//5, y1+(y2-y1)*4//5
    adb('shell', 'input', 'swipe', (x1+x2)//2, high if reverse else low, (x1+x2)//2, low if reverse else high, 400)

def reveal_action(text):
    # Long real release notes can place both update buttons below the Fold cover viewport.
    for _ in range(12):
        root = tree()
        if any(n.get('enabled') != 'false' for n in action_nodes(root, text)):
            return root
        scroll(root)
    raise RuntimeError('Could not reveal the real update action: ' + text)

def updates_summary():
    for _ in range(12):
        root = tree()
        if has(root, 'Installed'):
            return root
        scroll(root, reverse=True)
    raise RuntimeError('Could not return to the real installed-version summary')

def recovery_checkbox(root):
    checks = [node for node in root.iter('node') if node.get('checkable') == 'true']
    return checks[0] if len(checks) == 1 else None

def recovery_continue(root):
    checkbox = recovery_checkbox(root)
    if checkbox is None or checkbox.get('checked') != 'true':
        return None
    return next((node for node in root.iter('node')
                 if node.get('clickable') == 'true' and node.get('enabled') == 'true' and has(node, 'Continue')), None)

def enter_recovery_key(node, key):
    tap(node)
    wait_for(lambda page: any(field.get('focused') == 'true' for field in fields(page)),
             'focused recovery confirmation field')
    try:
        # Give the focused password field time to apply each key event before sending the next.
        for character in key:
            adb('shell', 'input', '-d', '0', 'text', character)
            time.sleep(0.05)
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
        raise RuntimeError('Synthetic recovery key input failed') from None
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')

def complete_recovery_setup(root):
    keys = [re.sub(r'\s', '', s) for s in labels(root) if re.fullmatch(r'[A-Za-z0-9_\- ]{43,60}', s)]
    keys = {key for key in keys if re.fullmatch(r'[A-Za-z0-9_-]{43}', key)}
    require(len(keys) == 1, 'Synthetic recovery key was not visible')

    # Acknowledge before opening the keyboard, which moves the checkbox. Read back
    # its actual state and the enabled Compose click target before continuing.
    checkbox = recovery_checkbox(root)
    require(checkbox is not None, 'Expected recovery acknowledgement checkbox')
    if checkbox.get('checked') != 'true':
        tap(checkbox)
        root = wait_for(lambda page: (check := recovery_checkbox(page)) is not None and
                        check.get('checked') == 'true', 'checked recovery acknowledgement')
    inputs = fields(root)
    require(len(inputs) == 1, 'Expected one recovery confirmation field')
    enter_recovery_key(inputs[0], next(iter(keys)))
    root = wait_for(lambda page: recovery_continue(page) is not None, 'enabled recovery Continue')
    tap(recovery_continue(root))
    rejection = 'Recovery key could not be confirmed. Check the saved key and try again.'
    result = wait_for(lambda page: has(page, rejection) or not has(page, 'Re-enter your saved recovery key'),
                      'completed recovery confirmation')
    require(not has(result, rejection), 'Build 94 rejected the re-entered synthetic recovery key')

def open_updates(allow_setup):
    authenticated = False
    adb('shell', 'am', 'start', '-n', PACKAGE + '/.LauncherActivity')
    for _ in range(28):
        root = tree()
        if has(root, 'Updates & About'):
            require(allow_setup or authenticated, 'Restart must authenticate the existing owner PIN')
            click(root, 'Updates & About')
            return wait_for(lambda t: has(t, 'Installed'), 'Updates & About installed-version summary')
        if has(root, 'Don’t Ask Again'):
            click(root, 'Don’t Ask Again')
        elif has(root, 'Set up NovaOS'):
            require(allow_setup, 'Update lost the existing owner PIN/setup')
            inputs = fields(root)
            require(len(inputs) == 2, 'Expected the two real PIN setup fields')
            enter(inputs[0], PIN)
            enter(fields(tree())[1], PIN)
            click(tree(), 'Create PIN')
        elif has(root, 'Re-enter your saved recovery key'):
            require(allow_setup, 'Unexpected recovery setup after upgrade')
            complete_recovery_setup(root)
        elif has(root, 'Enter NovaOS'):
            click(root, 'Enter NovaOS')
        elif has(root, 'Enter passcode'):
            for digit in PIN:
                click(tree(), digit)
            click(tree(), 'Unlock')
            authenticated = True
        elif has(root, 'Use biometrics?'):
            click(root, 'Finish')
        elif has(root, 'Search apps and folders'):
            enter(fields(root)[0], 'Settings')
            click(wait_for(lambda t: bool(action_nodes(t, 'Settings')), 'native Settings search result'), 'Settings')
        elif has(root, 'Settings') and not has(root, 'Privacy & Security'):
            click(root, 'Settings')
        elif has(root, 'Apps'):
            click(root, 'Apps')
        elif has(root, 'Open apps'):
            click(root, 'Open apps')
        elif has(root, 'Privacy & Security') or has(root, 'Settings'):
            scroll(root)
        else:
            time.sleep(1)
    raise RuntimeError('Could not reach existing Updates & About through the actual UI')

def save_ui(root, output):
    # A fresh test owner's recovery secret is never retained in CI evidence.
    for n in root.iter('node'):
        for key in ('text', 'content-desc'):
            value = n.get(key, '')
            if n.get('password') == 'true' or (len(re.sub(r'\s', '', value)) == 43 and re.fullmatch(r'[A-Za-z0-9_\- ]+', value)):
                n.set(key, '[synthetic credential redacted]')
    ET.ElementTree(root).write(output, encoding='utf-8', xml_declaration=True)

def public_release(output):
    deadline = time.monotonic() + 65 * 60
    url = f'https://api.github.com/repos/{REPO}/releases/latest'
    while True:
        try:
            request = urllib.request.Request(url, headers={'Accept': 'application/vnd.github+json', 'User-Agent': 'NovaOS-public-upgrade-verification'})
            with urllib.request.urlopen(request, timeout=30) as response:
                return json.load(response)
        except urllib.error.HTTPError as failure:
            reset = failure.headers.get('X-RateLimit-Reset')
            retry = failure.headers.get('Retry-After')
            limited = failure.headers.get('X-RateLimit-Remaining') == '0'
            if failure.code not in (403, 429) or not (limited and reset or retry):
                raise
            retry_at = max(time.time() + 1, float(reset) + 3 if limited and reset else time.time() + float(retry))
            require(time.monotonic() + retry_at - time.time() < deadline, 'Public GitHub quota did not recover within the bounded verification window')
            (output / 'public-feed-rate-limit.json').write_text(json.dumps({'status': failure.code, 'remaining': failure.headers.get('X-RateLimit-Remaining'), 'reset_epoch': reset, 'retry_after': retry}, indent=2)+'\n')
            print(f'Public updater feed is rate limited on this disposable runner; honoring reset epoch {int(retry_at)} before app testing', flush=True)
            while time.time() < retry_at:
                time.sleep(max(0.05, min(30, retry_at - time.time())))

def baseline_for(candidate):
    identity = candidate['tag'], candidate['version_code'], candidate['baseline_code']
    if identity == ('v2.0.0', 99, 97):
        require(candidate['baseline_sha256'] == BUILD97_DIGEST, 'Wrong Build 97 baseline digest')
        return '1.7.51', fetch_build97
    if identity == ('v1.7.51', 97, 96):
        require(candidate['baseline_sha256'] == BASELINE_DIGEST, 'Wrong Build 96 baseline digest')
        return '1.7.50', fetch_build96
    require(identity == ('v1.7.50', 96, 95), 'Wrong release candidate')
    return '1.7.49', fetch


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--candidate', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    require(os.environ.get('GITHUB_ACTIONS') == 'true' and os.environ.get('GITHUB_REPOSITORY') == REPO, 'Repository CI only')
    require(adb('shell', 'getprop', 'ro.kernel.qemu') == '1' and adb('shell', 'getprop', 'ro.build.version.sdk') == '37', 'Disposable API37 emulator required')
    args.output.mkdir(parents=True, exist_ok=False)
    avd = Path(os.environ['ANDROID_AVD_HOME']) / 'novaos85-live-public-update.avd/config.ini'
    configuration = {k.strip(): v.strip() for line in avd.read_text().splitlines() if '=' in line for k, v in [line.split('=', 1)]}
    target = configuration.get('target', '')
    (args.output / 'avd-target.txt').write_text(target + '\n')
    require(target == 'android-37.0', 'AVD tools did not create the requested API37 metadata: ' + target)
    wait_device_ready()
    require(installed() is None, 'Production package must be absent initially; never clear/uninstall')
    candidate = json.loads(args.candidate.read_text())
    tag, code, prior = candidate['tag'], candidate['version_code'], candidate['baseline_code']
    prior_version, fetch_baseline = baseline_for(candidate)
    version = tag[1:]
    installed_label = f'{version} · Build {code}'
    available_label = installed_label + ' available'
    up_to_date = version + ' · Up to date'
    release = public_release(args.output)
    require(release['tag_name'] == candidate['tag'] and not release['draft'] and not release['prerelease'], 'Not the actual latest stable release')
    apk = next(a for a in release['assets'] if a['name'] == 'private-gallery-release.apk')
    require(apk['digest'] == 'sha256:' + candidate['apk_sha256'], 'Live APK differs from the verified candidate')
    (args.output / 'live-release.json').write_text(json.dumps(release, indent=2)+'\n')
    baseline = args.output.parent / f'authentic-build{prior}.apk'
    fetch_baseline(baseline, Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0')
    adb('install', '--no-streaming', baseline)
    require(installed() == prior, f'Authentic Build {prior} installation not observed')
    adb('shell', 'svc', 'power', 'stayon', 'true')
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'wm', 'dismiss-keyguard')
    # This changes only the disposable emulator's ordinary install-source permission.
    adb('shell', 'appops', 'set', PACKAGE, 'REQUEST_INSTALL_PACKAGES', 'allow')
    before = open_updates(allow_setup=True)
    require(has(before, f'{prior_version} · Build {prior}'), 'Baseline screen does not identify its real installed version')
    # The installed build starts its first real check on About entry. Wait for
    # that completed result before exercising the explicit check action.
    before = wait_for(lambda t: has(t, available_label), f'Build {prior} automatic first check')
    save_ui(before, args.output / f'build{prior}-first-public-discovery.xml')
    click(reveal_action('Check for updates'), 'Check for updates')
    updates_summary()
    available = wait_for(lambda t: has(t, available_label), f'Build {prior} real public update discovery')
    save_ui(available, args.output / f'build{prior}-live-update-available.xml')
    print(f'PASS: authentic installed Build {prior} discovers Build {code} through its real UI and public GitHub feed', flush=True)
    download = reveal_action('Download update')
    save_ui(download, args.output / f'build{prior}-live-download-action.xml')
    click(download, 'Download update')
    installers = {'com.android.packageinstaller', 'com.google.android.packageinstaller', 'com.android.permissioncontroller'}
    installer = wait_for(lambda t: any(n.get('package') in installers and n.get('text') in ('Update', 'Install') for n in t.iter('node')), 'real verified download and Android installer', 240)
    save_ui(installer, args.output / 'live-android-installer.xml')
    action = next(n for n in installer.iter('node') if n.get('package') in installers and n.get('text') in ('Update', 'Install'))
    tap(action)
    deadline = time.monotonic() + 120
    while installed() != code:
        require(time.monotonic() < deadline, f'Android did not report installed Build {code}')
        time.sleep(1)
    (args.output / 'live-update-installed.json').write_text(json.dumps({'result': 'INSTALLED', 'installed_code': installed(), 'source': candidate['source'], 'apk_sha256': candidate['apk_sha256'], 'live_github_discovery': 'PASS', 'live_download': 'PASS', 'android_installer': 'confirmed', 'restart_review': 'pending'}, indent=2)+'\n')
    adb('shell', 'am', 'force-stop', PACKAGE)
    after = open_updates(allow_setup=False)
    require(has(after, installed_label), f'Restart did not show the installed Build {code}')
    # PackageManager has already confirmed the upgrade. Explicitly refresh the real feed
    # after restart as well as verifying the installed version.
    after = wait_for(lambda t: has(t, up_to_date), 'installed-version confirmation')
    click(reveal_action('Check for updates'), 'Check for updates')
    updates_summary()
    after = wait_for(lambda t: has(t, installed_label) and has(t, up_to_date), f'Build {code} real up-to-date screen')
    save_ui(after, args.output / f'build{prior}-live-up-to-date.xml')
    receipt = dict(schema=1, result='PASS', source=candidate['source'], apk_sha256=candidate['apk_sha256'], baseline_code=prior, installed_code=code, live_github_discovery='PASS', live_download='PASS', android_installer='confirmed', restart_and_same_pin='PASS', up_to_date_screen='PASS', instrumentation='none', physical_fold='owner_confirmation_pending')
    (args.output / 'live-upgrade.json').write_text(json.dumps(receipt, indent=2)+'\n')
    print(f'PASS: real public in-app update from Build {prior} to Build {code}, restart and Up to date', flush=True)

if __name__ == '__main__':
    try:
        main()
    except Exception:
        # Capture only this disposable CI emulator; never inspect an arbitrary device.
        if os.environ.get('GITHUB_ACTIONS') == 'true' and os.environ.get('GITHUB_REPOSITORY') == REPO:
            try:
                if adb('shell', 'getprop', 'ro.kernel.qemu') == '1':
                    destination = Path(sys.argv[sys.argv.index('--output') + 1])
                    destination.mkdir(parents=True, exist_ok=True)
                    (destination / 'host-disk.txt').write_text(subprocess.check_output(['df', '-h'], text=True))
                    for name, command in [('device-disk', ('shell', 'df', '-h')), ('device-crash', ('logcat', '-d', '-b', 'crash', '-t', '100')), ('installed-package', ('shell', 'dumpsys', 'package', PACKAGE))]:
                        try:
                            (destination / ('failure-' + name + '.txt')).write_text(adb(*command))
                        except Exception:
                            pass
                    save_ui(tree(), destination / 'failure-ui-redacted.xml')
            except Exception:
                pass  # Preserve the original failure and its traceback.
        raise
