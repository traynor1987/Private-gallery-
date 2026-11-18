#!/usr/bin/env python3
"""Drive authentic production Build84's UI against the published public feed on disposable API37.

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
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
from fetch_code81_upgrade_baseline import fetch

PACKAGE = 'uk.co.traynor.privategallery'
PIN = '738291'  # Synthetic disposable owner, never a real owner credential.
REPO = 'traynor1987/Private-gallery-'

def require(ok, message):
    if not ok:
        raise RuntimeError(message)

def adb(*args):
    return subprocess.check_output(['adb', *map(str, args)], text=True, timeout=35).strip()

def installed():
    match = re.search(r'versionCode=(\d+)\b', adb('shell', 'dumpsys', 'package', PACKAGE))
    return int(match[1]) if match else None

def tree():
    adb('shell', 'rm', '-f', '/sdcard/nova85-live-ui.xml')
    adb('shell', 'uiautomator', 'dump', '--compressed', '/sdcard/nova85-live-ui.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/nova85-live-ui.xml'))

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

def click(root, text):
    matches = [n for n in root.iter('node') if n.get('text') == text or n.get('content-desc') == text]
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
    adb('shell', 'input', 'text', value)
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')

def scroll(root):
    visible = [n for n in root.iter('node') if n.get('scrollable') == 'true']
    require(visible, 'No observed scroll container')
    b = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', visible[-1].get('bounds', ''))
    require(b is not None, 'Scrollable bounds missing')
    x1, y1, x2, y2 = map(int, b.groups())
    adb('shell', 'input', 'swipe', (x1+x2)//2, y1+(y2-y1)*4//5, (x1+x2)//2, y1+(y2-y1)//5, 400)

def open_updates(allow_setup):
    adb('shell', 'am', 'start', '-n', PACKAGE + '/.LauncherActivity')
    for _ in range(28):
        root = tree()
        if has(root, 'Updates & About'):
            click(root, 'Updates & About')
            return wait_for(lambda t: has(t, 'Installed') and has(t, 'Check for updates'), 'Updates & About')
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
            keys = [re.sub(r'\s', '', s) for s in labels(root) if re.fullmatch(r'[A-Za-z0-9_\- ]{43,60}', s)]
            keys = [k for k in keys if len(k) == 43 and re.fullmatch(r'[A-Za-z0-9_-]+', k)]
            require(len(set(keys)) == 1, 'Synthetic recovery key was not visible')
            enter(fields(root)[0], keys[0])
            checks = [n for n in tree().iter('node') if n.get('checkable') == 'true']
            require(len(checks) == 1, 'Expected recovery acknowledgement checkbox')
            tap(checks[0])
            click(tree(), 'Continue')
        elif has(root, 'Enter passcode'):
            for digit in PIN:
                click(tree(), digit)
            click(tree(), 'Unlock')
        elif has(root, 'Use biometrics?'):
            click(root, 'Finish')
        elif has(root, 'Search apps and folders'):
            enter(fields(root)[0], 'Settings')
            click(wait_for(lambda t: has(t, 'Settings'), 'native Settings search result'), 'Settings')
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

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--candidate', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    require(os.environ.get('GITHUB_ACTIONS') == 'true' and os.environ.get('GITHUB_REPOSITORY') == REPO, 'Repository CI only')
    require(adb('shell', 'getprop', 'ro.kernel.qemu') == '1' and adb('shell', 'getprop', 'ro.build.version.sdk') == '37', 'Disposable API37 emulator required')
    require(installed() is None, 'Production package must be absent initially; never clear/uninstall')
    args.output.mkdir(parents=True, exist_ok=False)
    candidate = json.loads(args.candidate.read_text())
    require(candidate['tag'] == 'v1.7.39' and candidate['version_code'] == 85, 'Wrong release candidate')
    with urllib.request.urlopen(f'https://api.github.com/repos/{REPO}/releases/latest', timeout=30) as response:
        release = json.load(response)
    require(release['tag_name'] == candidate['tag'] and not release['draft'] and not release['prerelease'], 'Not the actual latest stable release')
    apk = next(a for a in release['assets'] if a['name'] == 'private-gallery-release.apk')
    require(apk['digest'] == 'sha256:' + candidate['apk_sha256'], 'Live APK differs from the verified candidate')
    (args.output / 'live-release.json').write_text(json.dumps(release, indent=2)+'\n')
    baseline = args.output.parent / 'authentic-build84.apk'
    fetch(baseline, Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0')
    adb('install', baseline)
    require(installed() == 84, 'Authentic Build84 installation not observed')
    adb('shell', 'svc', 'power', 'stayon', 'true')
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'wm', 'dismiss-keyguard')
    # This changes only the disposable emulator's ordinary install-source permission.
    adb('shell', 'appops', 'set', PACKAGE, 'REQUEST_INSTALL_PACKAGES', 'allow')
    before = open_updates(allow_setup=True)
    require(has(before, '1.7.38.8 · Build 84'), 'Build84 screen does not identify its real installed version')
    click(before, 'Check for updates')
    available = wait_for(lambda t: has(t, 'Update available · 1.7.39') and has(t, 'Download update'), 'Build84 real public update discovery')
    save_ui(available, args.output / 'build84-live-update-available.xml')
    print('PASS: authentic installed Build84 discovers Build85 through its real UI and public GitHub feed', flush=True)
    click(available, 'Download update')
    installers = {'com.android.packageinstaller', 'com.google.android.packageinstaller', 'com.android.permissioncontroller'}
    installer = wait_for(lambda t: any(n.get('package') in installers and n.get('text') in ('Update', 'Install') for n in t.iter('node')), 'real verified download and Android installer', 240)
    save_ui(installer, args.output / 'live-android-installer.xml')
    action = next(n for n in installer.iter('node') if n.get('package') in installers and n.get('text') in ('Update', 'Install'))
    tap(action)
    deadline = time.monotonic() + 120
    while installed() != 85:
        require(time.monotonic() < deadline, 'Android did not report installed Build85')
        time.sleep(1)
    adb('shell', 'am', 'force-stop', PACKAGE)
    after = open_updates(allow_setup=False)
    require(has(after, '1.7.39 · Build 85'), 'Restart did not show the installed Build85')
    if not has(after, '1.7.39 · Up to date'):
        click(after, 'Check for updates')
    after = wait_for(lambda t: has(t, '1.7.39 · Build 85') and has(t, '1.7.39 · Up to date'), 'Build85 real up-to-date screen')
    save_ui(after, args.output / 'build85-live-up-to-date.xml')
    receipt = dict(schema=1, result='PASS', source=candidate['source'], apk_sha256=candidate['apk_sha256'], baseline_code=84, installed_code=85, live_github_discovery='PASS', live_download='PASS', android_installer='confirmed', restart_and_same_pin='PASS', up_to_date_screen='PASS', instrumentation='none', physical_fold='owner_confirmation_pending')
    (args.output / 'live-upgrade.json').write_text(json.dumps(receipt, indent=2)+'\n')
    print('PASS: real public in-app Build84 -> Android installer -> installed Build85 -> restart -> Up to date', flush=True)

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
                    save_ui(tree(), destination / 'failure-ui-redacted.xml')
                    (destination / 'failure-installed-package.txt').write_text(adb('shell', 'dumpsys', 'package', PACKAGE))
            except Exception:
                pass  # Preserve the original failure and its traceback.
        raise
