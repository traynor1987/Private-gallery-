import pathlib, shutil, json, hashlib, xml.etree.ElementTree as E
base = pathlib.Path(__file__).resolve().parent
root = base / 'runtime-staging-framing'
ev = root / 'docs/phase3/evidence/staging-framing'
inputs = json.loads((base / 'staging-framing-frozen-inputs.json').read_text())
assert len(inputs) == 550
supplemental=json.loads((base/'staging-framing-build-supplemental-inputs.json').read_text())['files']
for path,digest in supplemental.items():assert hashlib.sha256((root/path).read_bytes()).hexdigest()==digest,path
for path, digest in inputs.items(): assert hashlib.sha256((root / path).read_bytes()).hexdigest() == digest, path
results = json.loads((base / 'staging-framing-checkpoint-build-results.json').read_text())
assert len(results) == 5 and all(r['exit'] == 0 for r in results.values())
for name, count in [('targeted', 594), ('full-jvm', 1162), ('phase0', 1169)]:
    r = results[name]; assert r['tests'] == count and r['failures'] == r['errors'] == r['skipped'] == 0
for suffix, count in [('static-results.json', 11), ('packaged-static-results.json', 4)]:
    r = json.loads((base / ('staging-framing-' + suffix)).read_text())
    assert len(r) == count and all(x['exit'] == 0 for x in r.values())
for pattern in ['staging-framing-checkpoint-*.log', 'staging-framing-checkpoint-build-results.json',
                'staging-framing-static*', 'staging-framing-packaged*', 'verify-staging-framing-*.py',
                'audit-staging-framing-bytecode.py', 'staging-framing-frozen-inputs.json','staging-framing-build-supplemental-inputs.json']:
    for p in base.glob(pattern):
        if p.is_file(): shutil.copy2(p, ev / p.name)
shutil.copy2(base / 'staging-framing-isolated.init.gradle', ev / 'isolated-build.init.gradle')
summary = {}
for name in ['targeted', 'full-jvm', 'phase0']:
    source = base / ('staging-framing-checkpoint-' + name + '-xml')
    entries = {p.name: {'sha256': hashlib.sha256(p.read_bytes()).hexdigest(), 'attributes': E.parse(p).getroot().attrib}
               for p in sorted(source.glob('TEST-*.xml'))}
    summary[name] = entries
    if name == 'targeted':
        dest = ev / 'targeted-xml'; dest.mkdir(exist_ok=True)
        for p in source.glob('TEST-*.xml'): shutil.copy2(p, dest / p.name)
(ev / 'full-suite-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
build = pathlib.Path('/tmp/privategallery-phase3-staging-framing/app-build')
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
    'qualification': 'All5 groups from550 frozen source/build inputs plus8 supplemental build files independently equal parent during initial targeted build and at completion; ART15 compiled in both test variants, execution pending exact new remote CI. No local device/permanent-signer/owner/Native/owned integration acceptance.'}, indent=2) + '\n')
(ev/'supplemental-input-completion.json').write_text(json.dumps({'qualification':'Original driver550 map preserved;8 omitted build/workflow files also remain identical to published parent and during first targeted observation','files':supplemental,'finalCombined558':{**inputs,**supplemental}},indent=2)+'\n')
print('Framing full-suite/lint/APK/static/package evidence collected')
