import pathlib,json,hashlib,zipfile,subprocess,shutil,shlex,re
base=pathlib.Path(__file__).resolve().parent;repo=base/'runtime-staging-key-root';ev=repo/'docs/phase3/evidence/staging-key/packaged-native';ev.mkdir(parents=True,exist_ok=True);build=pathlib.Path('/tmp/privategallery-phase3-staging-key/app-build');scratch=pathlib.Path('/tmp/privategallery-phase3-staging-key-packaged-native');scratch.mkdir(exist_ok=True);llvm=base/'toolchain/android-sdk/ndk/27.3.13750724/toolchains/llvm/prebuilt/linux-x86_64/bin';symbol='Java_uk_co_traynor_privategallery_core_security_staging_StagingEntropyInvocation_fillNative';results={}
abis={'arm64-v8a','armeabi-v7a','x86','x86_64'}
for apk in sorted((build/'outputs/apk').rglob('*.apk')):
 with zipfile.ZipFile(apk) as z:
  names=[n for n in z.namelist() if n.endswith('/libpg_staging_entropy.so')]
  if 'androidTest' in apk.name:assert not names
  else:assert {n.split('/')[1] for n in names}==abis
  entry={'apkSHA256':hashlib.sha256(apk.read_bytes()).hexdigest(),'libraries':{}}
  for n in names:
   abi=n.split('/')[1];p=scratch/(apk.stem+'-'+abi+'.so');p.write_bytes(z.read(n));stem=apk.stem+'-'+abi
   cmd=[str(llvm/'llvm-objdump'),'--disassemble-symbols='+symbol,str(p)];run=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);assert run.returncode==0 and symbol in run.stdout;(ev/(stem+'-assembly.txt')).write_text(run.stdout)
   cmd2=[str(llvm/'llvm-readelf'),'--dyn-syms','--notes','--file-header',str(p)];run2=subprocess.run(cmd2,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);assert run2.returncode==0 and symbol in run2.stdout;(ev/(stem+'-elf-metadata.txt')).write_text(run2.stdout)
   entry['libraries'][abi]={'zipEntry':n,'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'assemblySHA256':hashlib.sha256(run.stdout.encode()).hexdigest(),'metadataSHA256':hashlib.sha256(run2.stdout.encode()).hexdigest(),'commands':[cmd,cmd2]}
  results[str(apk.relative_to(build))]=entry
# Inspect actual AGP compile targets/settings, not standalone proposed-model binaries.
commands={}
for p in (repo/'app/.cxx').rglob('compile_commands.json'):
 rows=json.loads(p.read_text())
 if any(str(repo/'app/src/main/c/staging_entropy.c')==r.get('file') for r in rows):
  targets=[next(a.removeprefix('--target=') for a in shlex.split(r['command']) if a.startswith('--target=')) for r in rows]
  assert all(re.search(r'(?:android|androideabi)26$',t) for t in targets),targets
  commands[str(p.relative_to(repo))]=rows
assert len(commands)>=4,len(commands)
(ev/'actual-AGP-compile-commands.json').write_text(json.dumps(commands,indent=2)+'\n')
(ev/'actual-packaged-native.json').write_text(json.dumps({'apks':results,'sourceSHA256':hashlib.sha256((repo/'app/src/main/c/staging_entropy.c').read_bytes()).hexdigest(),'qualification':'Actual4ABI libraries extracted from both main APK variants; instrumentationAPK does not inject entropy implementation. Exact ELF/assembly hash/source/API26 compile-target binding. Manual independent known32scratch-return wipe review separate; no GC/JIT/OEM/deadstack/ART claim.'},indent=2)+'\n');shutil.copyfile(__file__,ev/pathlib.Path(__file__).name);print('Actual2 main APK x4ABI +2 test APK Native inventories/disassembly retained')
