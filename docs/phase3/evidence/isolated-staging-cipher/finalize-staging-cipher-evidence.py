import pathlib, json, shutil, hashlib, xml.etree.ElementTree as E
base = pathlib.Path(__file__).resolve().parent
root = base / 'runtime-bounded-staging-cipher'
ev = root / 'docs/phase3/evidence/isolated-staging-cipher'
inputs = json.loads((base / 'staging-cipher-final-inputs.json').read_text())
assert len(inputs) == 552
for p, h in inputs.items(): assert hashlib.sha256((root / p).read_bytes()).hexdigest() == h, p
checks = json.loads((base / 'staging-cipher-comment-results.json').read_text())
assert checks['debug']['exit'] == checks['phase0']['exit'] == 0
assert len(checks['debug-classes']['unchanged7']) == len(checks['phase0Evidence-classes']['unchanged7']) == 7
review = json.loads((ev / 'independent-final-review.json').read_text())
assert review['critical'] == review['important'] == review['minor'] == 0
for p, h in review['files'].items(): assert hashlib.sha256((root / p).read_bytes()).hexdigest() == h
for pattern in ['staging-cipher-comment-*.log', 'staging-cipher-comment-results.json',
                'verify-staging-cipher-comment.py', 'staging-cipher-final-inputs.json',
                'staging-cipher-packaged*', 'collect-staging-cipher-evidence.py',
                'finalize-staging-cipher-evidence.py']:
    for p in base.glob(pattern):
        if p.is_file(): shutil.copy2(p, ev / p.name)
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
    'qualification': 'Final552 only4-line Javadoc correction after actual579/1147/1154 JVM verification. Both variants lint/4APK assemblies rechecked after correction; all7 actual private class bytes identical in both variants and standalone. All production/host/ART/build inputs otherwise unchanged. Whole final552 full-suite reexecution not claimed. Final12 standalone passed; new head ART12 execution still pending. No owner/permanent-signer/Task3/GO acceptance.'}, indent=2) + '\n')
old = json.loads((ev / 'before-comment-correction/compiled-artifacts-lint.json').read_text())
(ev / 'compiled-artifact-comparison.json').write_text(json.dumps({'unchangedApks': apks == old['apks'],
    'old': old['apks'], 'final': apks, 'classIdentity': checks,
    'qualification': 'Actual compiled class identities, not inference from comments; APK hashes independently computed.'}, indent=2) + '\n')
manifest = {str(p.relative_to(ev)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(ev.rglob('*'))
            if p.is_file() and p.name != 'EVIDENCE_SHA256.json'}
(ev / 'EVIDENCE_SHA256.json').write_text(json.dumps(manifest, indent=2) + '\n')
print('Final552 class/lint/APK/review and preserved failure/CI evidence finalized')
