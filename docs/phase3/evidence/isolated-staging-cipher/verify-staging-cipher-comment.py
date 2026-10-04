import pathlib, json, subprocess, hashlib
base = pathlib.Path(__file__).resolve().parent
root = base / 'runtime-bounded-staging-cipher'
ev = root / 'docs/phase3/evidence/isolated-staging-cipher'
inputs = json.loads((base / 'staging-cipher-final-inputs.json').read_text())
def confirm():
    for p, h in inputs.items(): assert hashlib.sha256((root / p).read_bytes()).hexdigest() == h, p
    actual = {str(p.relative_to(root)) for p in (root / 'app/src').rglob('*') if p.is_file()}
    assert actual == {p for p in inputs if p.startswith('app/src/')}
results = {}
for name, args in [('debug', [':app:lintDebug', ':app:assembleDebug', ':app:assembleDebugAndroidTest']),
                   ('phase0', ['-PPRIVATE_GALLERY_PHASE0_EVIDENCE=true', ':app:lintPhase0Evidence', ':app:assemblePhase0Evidence', ':app:assemblePhase0EvidenceAndroidTest'])]:
    confirm()
    command = ['python3', str(base / 'toolchain/run-gradle.py'), '-I', str(base / 'staging-cipher-isolated.init.gradle'),
               '--project-cache-dir', '/tmp/privategallery-phase3-staging-cipher/project-cache', *args, '--offline', '--stacktrace']
    with (base / ('staging-cipher-comment-' + name + '.log')).open('w') as out:
        result = subprocess.run(command, cwd=root, stdout=out, stderr=subprocess.STDOUT)
    results[name] = {'exit': result.returncode, 'command': command}
    (base / 'staging-cipher-comment-results.json').write_text(json.dumps(results, indent=2) + '\n')
    print(name, result.returncode, flush=True)
    if result.returncode: raise SystemExit(result.returncode)
confirm()
for variant in ['debug', 'phase0Evidence']:
    folder = pathlib.Path('/tmp/privategallery-phase3-staging-cipher/app-build/intermediates/javac') / variant / ('compile' + variant[0].upper() + variant[1:] + 'JavaWithJavac') / 'classes/uk/co/traynor/privategallery/core/security/staging'
    actual = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(folder.glob('*.class'))}
    before = json.loads((ev / 'before-comment-correction' / (variant + '-classes.json')).read_text())
    assert len(actual) == 7 and actual == before, (variant, actual, before)
    results[variant + '-classes'] = {'unchanged7': actual}
(base / 'staging-cipher-comment-results.json').write_text(json.dumps(results, indent=2) + '\n')
print('Final552 unchanged; same7 compiled private classes in both production variants', flush=True)
