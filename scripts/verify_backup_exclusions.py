#!/usr/bin/env python3
"""Fail closed on backup eligibility in source, merged manifest and packaged APK XML.

The platform excludes cache/codeCache/noBackup independently. This checks app
configuration, not vendor transfer compliance. APK checks require SDK aapt2.
"""
import argparse
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ANDROID = '{http://schemas.android.com/apk/res/android}'
DOMAINS = frozenset(('root', 'file', 'database', 'sharedpref', 'external', 'device_root', 'device_file', 'device_database', 'device_sharedpref'))


def manifest_errors(root):
    app = root.find('application')
    if app is None:
        return ['missing application']
    errors = []
    for name, expected in [('allowBackup', 'false'), ('fullBackupContent', 'false'), ('dataExtractionRules', '@xml/data_extraction_rules')]:
        if app.get(ANDROID + name) != expected:
            errors.append(f'{name} must be {expected}')
    if app.get(ANDROID + 'backupAgent'):
        errors.append('custom backupAgent requires a separate eligibility review')
    return errors


def rules_errors(root):
    errors = []
    if root.tag != 'data-extraction-rules':
        return ['missing data-extraction-rules root']
    modes = list(root)
    if sorted(section.tag for section in modes) != ['cloud-backup', 'device-transfer']:
        errors.append('exactly cloud-backup and device-transfer required; new transfer modes need review')
    for section in modes:
        excluded = set()
        for rule in section:
            if rule.tag != 'exclude' or set(rule.attrib) != {'domain', 'path'}:
                errors.append(f'{section.tag}: unsupported eligibility rule {rule.tag}')
                continue
            domain, path = rule.get('domain'), rule.get('path')
            if domain not in DOMAINS or path not in ('.', './'):
                errors.append(f'{section.tag}: {domain} must exclude its whole domain')
            else:
                excluded.add(domain)
        for domain in sorted(DOMAINS - excluded):
            errors.append(f'{section.tag}: eligible domain {domain} (missing whole-domain exclusion)')
    return errors


def decode_tree(output, resource_names=None):
    """Decode aapt2's XML tree without treating the compiled XML as source text."""
    resources = resource_names or {}
    stack = []
    root = None
    for line in output.splitlines():
        element = re.match(r'(\s*)E: ([^ ]+)', line)
        if element:
            indent, tag = len(element[1]), element[2]
            while stack and stack[-1][0] >= indent:
                stack.pop()
            node = ET.Element(tag)
            if stack:
                stack[-1][1].append(node)
            else:
                if root is not None:
                    raise ValueError('multiple XML roots')
                root = node
            stack.append((indent, node))
            continue
        attr = re.match(r'\s*A: ([^=(]+)(?:\([^)]*\))?=(.*)', line)
        if attr and stack:
            name, raw = attr.groups()
            if name.startswith('http://schemas.android.com/apk/res/android:'):
                name = ANDROID + name[len('http://schemas.android.com/apk/res/android:'):]
            elif name.startswith('android:'):
                name = ANDROID + name[len('android:'):]
            quoted = re.search(r'\(Raw: "(.*)"\)', raw)
            if quoted:
                value = quoted[1]
            elif raw.startswith('"'):
                value = raw.strip('"')
            elif '(type 0x12)' in raw:
                value = 'false' if raw.endswith('0x0') else 'true'
            elif raw.startswith('@0x'):
                value = '@' + resources.get(raw[1:].lower(), raw[1:])
            else:
                value = raw
            stack[-1][1].set(name, value)
    if root is None:
        raise ValueError('unable to decode APK XML tree')
    return root


def apk_errors(apk, aapt2):
    def dump(*args):
        return subprocess.run([str(aapt2), 'dump', *args, str(apk)], check=True, capture_output=True, text=True).stdout
    resources = dump('resources')
    names = {rid.lower(): name for rid, name in re.findall(r'resource (0x[0-9a-fA-F]+) (?:[^: ]+:)?(xml/[^\s:]+)', resources)}
    def tree(path):
        output = subprocess.run([str(aapt2), 'dump', 'xmltree', str(apk), '--file', path], check=True, capture_output=True, text=True).stdout
        return decode_tree(output, names)
    errors = manifest_errors(tree('AndroidManifest.xml'))
    with zipfile.ZipFile(apk) as archive:
        paths = [name for name in archive.namelist() if re.fullmatch(r'res/xml[^/]*/data_extraction_rules.xml', name)]
    if not paths:
        errors.append('packaged data_extraction_rules missing')
    for path in paths:
        errors.extend(f'{path}: {error}' for error in rules_errors(tree(path)))
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', type=pathlib.Path, default=pathlib.Path(__file__).resolve().parents[1])
    parser.add_argument('--merged-manifest', type=pathlib.Path)
    parser.add_argument('--apk', type=pathlib.Path)
    parser.add_argument('--aapt2', type=pathlib.Path)
    args = parser.parse_args()
    errors = []
    try:
        source = args.project / 'app/src/main'
        errors.extend(manifest_errors(ET.parse(source / 'AndroidManifest.xml').getroot()))
        paths = list((source / 'res').glob('xml*/data_extraction_rules.xml'))
        if not paths:
            errors.append('source data_extraction_rules missing')
        for path in paths:
            errors.extend(f'{path}: {error}' for error in rules_errors(ET.parse(path).getroot()))
        if args.merged_manifest:
            errors.extend(f'merged manifest: {error}' for error in manifest_errors(ET.parse(args.merged_manifest).getroot()))
        if args.apk:
            if not args.aapt2:
                errors.append('--aapt2 required with --apk')
            else:
                errors.extend(f'APK: {error}' for error in apk_errors(args.apk, args.aapt2))
    except (OSError, ValueError, ET.ParseError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        errors.append(f'cannot verify configuration ({type(error).__name__})')
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        return 1
    checked = ['source'] + (['merged manifest'] if args.merged_manifest else []) + (['packaged APK XML'] if args.apk else [])
    print('PASS: backup exclusions (' + ', '.join(checked) + '); vendor transfer acceptance remains physical')
    return 0

if __name__ == '__main__':
    sys.exit(main())
