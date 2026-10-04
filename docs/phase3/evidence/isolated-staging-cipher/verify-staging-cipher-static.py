import pathlib,subprocess,json,os
base=pathlib.Path(__file__).resolve().parent
root=base/'runtime-bounded-staging-cipher'
env=os.environ.copy();env['ANDROID_HOME']=str(base/'toolchain/android-sdk');env['ANDROID_SDK_ROOT']=env['ANDROID_HOME']
env['JAVA_HOME']=str(base/'toolchain/amazon-corretto-17.0.20.12.1-linux-x64');env['PATH']=env['JAVA_HOME']+'/bin:'+env['ANDROID_HOME']+'/build-tools/36.0.0:'+env['PATH']
commands=[
 ('backup-source',['python3','scripts/verify_backup_exclusions.py'],root),
 ('backup-mutations',['python3','-B','-m','unittest','discover','-s','scripts/tests','-p','*.py'],root),
 ('legacy-corpus',['sha256sum','--strict','--check','SHA256SUMS'],root/'app/src/test/resources/phase0/legacy-v1'),
 ('fixture-backup',['cmp','app/src/test/resources/phase0/legacy-v1/backup-v1.pgvault','app/src/androidTest/assets/phase0/backup-v1.pgvault'],root),
 ('fixture-expected',['cmp','app/src/test/resources/phase0/legacy-v1/expected.json','app/src/androidTest/assets/phase0/expected.json'],root),
 ('vectors',['python3','-B','-m','unittest','docs/phase0/test_future_format_vectors.py'],root),
 ('design',['python3','-B','-m','unittest','docs/phase3/test_design_contracts.py'],root),
 ('model-source',['python3','scripts/verify_local_ai_distribution.py'],root),
 ('browser',['node','--test','scripts/tests/browser-video-assistant.test.cjs'],root),
 ('secrets',['bash','scripts/no_secret_scan.sh'],root),
 ('vpn',['bash','scripts/verify_production_vpn_path.sh'],root)]
results={}
for name,args,cwd in commands:
    with (base/('staging-cipher-static-'+name+'.log')).open('w')as output:
        r=subprocess.run(args,cwd=cwd,env=env,stdout=output,stderr=subprocess.STDOUT)
    results[name]={'exit':r.returncode,'command':args,'SDK_configured':True}
    (base/'staging-cipher-static-results.json').write_text(json.dumps(results,indent=2)+'\n')
    print(name,r.returncode,flush=True)
    if r.returncode:raise SystemExit(r.returncode)
