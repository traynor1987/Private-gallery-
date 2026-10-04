import pathlib, shutil, json, hashlib, xml.etree.ElementTree as E
base = pathlib.Path(__file__).resolve().parent
root = base / 'runtime-bounded-staging-cipher'
ev = root / 'docs/phase3/evidence/isolated-staging-cipher'
inputs = json.loads((base / 'staging-cipher-frozen-inputs.json').read_text())
assert len(inputs) == 552
for path, digest in inputs.items(): assert hashlib.sha256((root / path).read_bytes()).hexdigest() == digest, path
results = json.loads((base / 'staging-cipher-checkpoint-build-results.json').read_text())
assert len(results) == 5 and all(r['exit'] == 0 for r in results.values())
for name, count in [('targeted', 579), ('full-jvm', 1147), ('phase0', 1154)]:
    r = results[name]; assert r['tests'] == count and r['failures'] == r['errors'] == r['skipped'] == 0
for suffix, count in [('static-results.json', 11), ('packaged-static-results.json', 4)]:
    r = json.loads((base / ('staging-cipher-' + suffix)).read_text())
    assert len(r) == count and all(x['exit'] == 0 for x in r.values())
for pattern in ['staging-cipher-checkpoint-*.log', 'staging-cipher-checkpoint-build-results.json',
                'staging-cipher-static*', 'staging-cipher-packaged*', 'verify-staging-cipher-*.py',
                'audit-staging-cipher-bytecode.py', 'staging-cipher-frozen-inputs.json']:
    for p in base.glob(pattern):
        if p.is_file(): shutil.copy2(p, ev / p.name)
shutil.copy2(base / 'staging-cipher-isolated.init.gradle', ev / 'isolated-build.init.gradle')
summary = {}
for name in ['targeted', 'full-jvm', 'phase0']:
    source = base / ('staging-cipher-checkpoint-' + name + '-xml')
    entries = {p.name: {'sha256': hashlib.sha256(p.read_bytes()).hexdigest(), 'attributes': E.parse(p).getroot().attrib}
               for p in sorted(source.glob('TEST-*.xml'))}
    summary[name] = entries
    if name == 'targeted':
        dest = ev / 'targeted-xml'; dest.mkdir(exist_ok=True)
        for p in source.glob('TEST-*.xml'): shutil.copy2(p, dest / p.name)
(ev / 'full-suite-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
build = pathlib.Path('/tmp/privategallery-phase3-staging-cipher/app-build')
apks = {str(p.relative_to(build)): {'bytes': p.stat().st_size, 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()}
        for p in sorted((build / 'outputs/apk').rglob('*.apk'))}
assert len(apks) == 4
lint = {}
for p in sorted((build / 'reports').glob('lint-results-*.xml')):
    severity = {s: sum(x.attrib.get('severity') == s for x in E.parse(p).getroot()) for s in ['Fatal', 'Error', 'Warning', 'Information']}
    assert severity['Fatal'] == severity['Error'] == 0
    lint[p.name] = {'sha256': hashlib.sha256(p.read_bytes()).hexdigest(), 'severity': severity}
assert len(lint) == 2
(ev / 'compiled-artifacts-lint.json').write_text(json.dumps({'apks': apks, 'lint': lint,
    'qualification': 'All5 groups from exact frozen552 source; ART12 compiled in both test variants, execution pending exact new remote CI. No local device/permanent-signer/owner/Native/owned integration acceptance.'}, indent=2) + '\n')
(ev / 'command-path-qualification.json').write_text(json.dumps({
    'reportedLogicalInitPath': 'docs/phase3/evidence/staging-cipher/isolated-build.init.gradle',
    'actualDriverInitArgument': str(base / 'staging-cipher-isolated.init.gradle'),
    'preservedEquivalentInitArtifact': 'docs/phase3/evidence/isolated-staging-cipher/isolated-build.init.gradle',
    'qualification': 'Original results preserve their mistakenly abbreviated logical init path. Actual executed driver invokes the absolute scratch init above; copied init has identical bytes. No failed selection or second execution is implied.'}, indent=2) + '\n')
print('Frozen552 full-suite/lint/APK/static/package evidence collected')
