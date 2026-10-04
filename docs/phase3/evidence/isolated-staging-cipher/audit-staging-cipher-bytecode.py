import pathlib, subprocess, json, hashlib, re, sys
base = pathlib.Path(__file__).resolve().parent
classes = base / ('cipher-' + sys.argv[1] + '-classes')
ev = base / 'runtime-bounded-staging-cipher/docs/phase3/evidence/isolated-staging-cipher'
java = base / 'toolchain/amazon-corretto-17.0.20.12.1-linux-x64/bin/javap'
namespace = 'uk/co/traynor/privategallery/core/security/staging/'
allowed = {'org/bouncycastle/crypto/params/KeyParameter', 'org/bouncycastle/crypto/params/ParametersWithIV',
           'org/bouncycastle/util/Arrays', 'org/bouncycastle/util/Pack', 'org/bouncycastle/util/Integers',
           'org/bouncycastle/util/Strings', 'org/bouncycastle/crypto/DataLengthException',
           'org/bouncycastle/crypto/OutputLengthException', 'org/bouncycastle/crypto/InvalidCipherTextException',
           'org/bouncycastle/crypto/MaxBytesExceededException'}
entries = {}
for p in sorted((classes / namespace).glob('*.class')):
    if p.name.startswith('Phase3'): continue
    name = str(p.relative_to(classes)).removesuffix('.class').replace('/', '.')
    text = subprocess.check_output([str(java), '-classpath', str(classes), '-p', '-c', name], text=True)
    assert not any(s in text for s in ['CryptoServicesRegistrar', 'java/lang/reflect/', 'java/security/Security',
                                      'java/security/Provider', 'java/security/AccessController'])
    calls = sorted(set(re.findall(r'// (?:Interface)?Method ([^\s]+)', text)))
    external = sorted(set(c.split('.')[0] for c in calls if c.startswith('org/bouncycastle/')))
    assert set(external) <= allowed, (name, external)
    (ev / ('bytecode-' + p.stem + '.txt')).write_text(text)
    entries[name] = {'sha256': hashlib.sha256(p.read_bytes()).hexdigest(), 'calls': calls,
                     'externalBouncyCastleOwners': external}
assert len(entries) == 7, entries.keys()
jar = next(pathlib.Path('/root/.gradle/caches/modules-2/files-2.1').glob('org.bouncycastle/bcprov-jdk18on/1.79/*/*.jar'))
result = {'classes': entries, 'bcJarSha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
          'qualification': 'Standalone JDK17 compiled private7 class-file inspection including constants class; no registrar/provider/reflection call in these class files. External owners enumerated against pinned source helpers. Unused inherited/getMac paths are included; this is not complete path reachability, Android packaged-bytecode/Native/JIT/OEM or owned key/nonce/FD integration proof.'}
(ev / 'bytecode-audit.json').write_text(json.dumps(result, indent=2) + '\n')
print('Private7 class bytecode closure captured; BC jar identity verified')
