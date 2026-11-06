"""Synthetic control tampering tests; no Android build/signing/upgrade proof is manufactured."""
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import warnings
import zipfile

ROOT=Path(__file__).resolve().parents[2]
def module(name):
    spec=importlib.util.spec_from_file_location(name,ROOT/'scripts'/(name+'.py'))
    value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value

baseline=module('verify_upgrade1711_baseline')
compiled=module('verify_compiled_upgrade1711')
signed=module('verify_signed_upgrade1711')
gate=module('verify_release1711_gate')

class Upgrade1711Controls(unittest.TestCase):
    def test_exact_baseline_archive_source_payload_size_and_name_required(self):
        for kind in ('valid','archive','source','apk','size','multiple','missing','wrong-name','duplicate'):
            with self.subTest(kind=kind),tempfile.TemporaryDirectory() as temporary:
                path=Path(temporary)/'synthetic.zip';payload=b'SYNTHETIC ORIGINAL APK'
                with warnings.catch_warnings(),zipfile.ZipFile(path,'w') as archive:
                    warnings.simplefilter('ignore',UserWarning)
                    archive.writestr('production-evidence/source-sha.txt',baseline.SOURCE_SHA if kind!='source' else '0'*40)
                    if kind!='missing':archive.writestr(baseline.APK_NAME if kind!='wrong-name' else 'replacement.apk',payload if kind!='apk' else b'other')
                    if kind=='multiple':archive.writestr('extra.apk',payload)
                    if kind=='duplicate':archive.writestr(baseline.APK_NAME,payload)
                with patch.object(baseline,'ARCHIVE_SHA256',baseline.hashlib.sha256(path.read_bytes()).hexdigest() if kind!='archive' else '0'*64),patch.object(baseline,'APK_SHA256',baseline.hashlib.sha256(payload).hexdigest()),patch.object(baseline,'APK_BYTES',len(payload)+(kind=='size')):
                    if kind=='valid':self.assertEqual(payload,baseline.validated_apk(path))
                    else:
                        with self.assertRaises(ValueError):baseline.validated_apk(path)

    def test_reviewed_fixture_keeps_every_original_native_assertion_and_operation(self):
        previous=(ROOT/'docs/release17102/UpgradeFixtureTest.kt').read_text()
        current=(ROOT/'docs/release1711/UpgradeFixtureTest.kt').read_text()
        start=current.index('            // Persistent synthetic login marker:')
        end=current.index('            InstrumentationRegistry.getInstrumentation().runOnMainSync {manager.flush()}',start)
        current=current[:start]+current[end:]
        current=current.replace('        assertTrue("Existing persistent HttpOnly browser login survives upgrade/restart",retained.orEmpty().split(\';\').any {it.trim()=="nova1711_login=synthetic-persistent-auth"})\n','')
        for now,prior in (
            ('code54 -> code56','code53 -> code54'),
            ('baselineCode == 54 && candidateCode == 56','baselineCode == 53 && candidateCode == 54'),
            ('Encrypted browser metadata; persistent synthetic CookieManager login is checked separately below.','Explicit encrypted browser metadata only; WebView cookie/profile disk storage is a documented Beta limitation.'),
            ('actual signed code54 model; verifies code56 migration','actual signed code53 model; verifies code54 migration'),
            ('before installing code56','before installing code54'),
        ):current=current.replace(now,prior)
        self.assertEqual(previous,current)
        self.assertEqual(2,previous.count('File(context.filesDir, "profile'))

    def test_compilation_transform_uses_actual_original54_build_and_only_two_reviewed_edits(self):
        fixture=(ROOT/'docs/release1711/UpgradeFixtureTest.kt').read_bytes()
        build=subprocess.check_output(['git','show',baseline.SOURCE_SHA+':app/build.gradle.kts'],cwd=ROOT)
        transformed,changed_build=compiled.transform(fixture,build)
        self.assertEqual(build.replace(compiled.BUILD_TARGET,b''),changed_build)
        self.assertEqual(fixture.replace(compiled.TARGET,b'uk.co.traynor.privategallery'),transformed)
        self.assertIn(b'versionCode = 54',changed_build)
        for bad_fixture,bad_build in ((fixture*2,build),(fixture,build*2),(b'absent',build),(fixture,b'absent')):
            with self.assertRaises(ValueError):compiled.transform(bad_fixture,bad_build)
        payload=b'SYNTHETIC INSTRUMENTATION, NOT AN APK';compiler=(ROOT/'scripts/compile_upgrade1711_fixture.sh').read_bytes()
        proof=dict(schema=1,decision='COMPILED',baseline_source_sha=compiled.BASELINE,candidate_source_sha='1'*40,fixture_sha256=compiled.sha(fixture),transformed_fixture_sha256=compiled.sha(transformed),baseline_build_sha256=compiled.sha(build),transformed_build_sha256=compiled.sha(changed_build),compiler_sha256=compiled.sha(compiler),instrumentation_apk_sha256=compiled.sha(payload),instrumentation_apk_bytes=len(payload),instrumentation_package_sha256='2'*64,instrumentation_manifest_sha256='3'*64,transformations=['remove_debug_applicationIdSuffix','fixture_target_production_package'])
        compiled.validate(proof,'1'*40,fixture,compiler,payload,build)
        for key in proof:
            bad=copy.deepcopy(proof);bad[key]=None
            with self.subTest(field=key),self.assertRaises((ValueError,TypeError)):compiled.validate(bad,'1'*40,fixture,compiler,payload,build)

    def test_signed_copy_cannot_change_any_compiled_application_entry(self):
        with tempfile.TemporaryDirectory() as temporary:
            original=Path(temporary)/'original.apk';changed=Path(temporary)/'changed.apk'
            with zipfile.ZipFile(original,'w') as archive:
                archive.writestr('classes.dex',b'synthetic classes');archive.writestr('AndroidManifest.xml',b'synthetic manifest')
                archive.writestr('META-INF/ANDROIDD.SF',b'synthetic debug signature')
            for mutation in ('signatures-only','classes','manifest','extra','missing','duplicate'):
                with self.subTest(mutation=mutation),warnings.catch_warnings(),zipfile.ZipFile(changed,'w') as archive:
                    warnings.simplefilter('ignore',UserWarning)
                    if mutation!='missing':archive.writestr('classes.dex',b'other' if mutation=='classes' else b'synthetic classes')
                    archive.writestr('AndroidManifest.xml',b'other' if mutation=='manifest' else b'synthetic manifest')
                    archive.writestr('META-INF/CERT.SF',b'synthetic original-key signature')
                    if mutation=='extra':archive.writestr('assets/new.txt',b'new unreviewed resource')
                    if mutation=='duplicate':archive.writestr('classes.dex',b'synthetic classes')
                if mutation=='signatures-only':self.assertEqual(2,signed.verify(original,changed))
                else:
                    with self.assertRaises(ValueError):signed.verify(original,changed)

    def gate_fixture(self,folder,cases=None):
        source='1'*40;matrix=json.dumps({'Synthetic.Case':['passes']}).encode()
        archive=Path(folder)/'SYNTHETIC-runtime.zip'
        with zipfile.ZipFile(archive,'w') as zipped:
            zipped.writestr('home2-evidence/source-sha.txt',source)
            zipped.writestr('outputs/androidTest-results/connected/debug/TEST-synthetic.xml',cases or '<testsuite><testcase classname="Synthetic.Case" name="passes"/></testsuite>')
        fixture=b'SYNTHETIC fixture';controls={'SYNTHETIC-control':gate.sha(b'SYNTHETIC')}
        marker=dict(source=source,baseline_source=gate.BASELINE,baseline_code=54,candidate_code=56,test_run=123,test_artifact=456,controls_sha256=controls,fixture_sha256=gate.sha(fixture),matrix_sha256=gate.sha(matrix),runtime_archive_sha256=gate.sha(archive.read_bytes()))
        review=('\n'.join(('Source: '+source,'Decision: APPROVED','Production signing: APPROVED','Upgrade fixture SHA-256: '+gate.sha(fixture),'Upgrade controls SHA-256: '+gate.controls_digest(controls),'Runtime matrix SHA-256: '+gate.sha(matrix),'Runtime archive SHA-256: '+marker['runtime_archive_sha256']))+'\n').encode()
        marker['review_sha256']=gate.sha(review)
        run=dict(id=123,event='push',head_sha=source,head_branch=gate.BRANCH,status='completed',conclusion='success',head_repository={'full_name':'traynor1987/Private-gallery-'},path='.github/workflows/fast-development.yml')
        jobs={'jobs':[dict(name='home2-integration',conclusion='success',steps=[dict(name=name,conclusion='success') for name in gate.REQUIRED_STEPS])]}
        artifact=dict(id=456,expired=False,name='novaos-home2-integration-verification',workflow_run=dict(id=123,head_sha=source))
        return [marker,review,run,jobs,artifact,archive,matrix,controls,fixture]

    def test_signer_gate_requires_original_exact_source_review_job_artifact_and_receipt(self):
        with tempfile.TemporaryDirectory() as temporary:
            args=self.gate_fixture(temporary);proof=gate.validate(*args)
            gate.check_receipt(proof,'1'*40,args[7],args[8])
            for field in proof:
                bad=copy.deepcopy(proof);bad[field]=None
                with self.subTest(receipt=field),self.assertRaises((ValueError,TypeError)):gate.check_receipt(bad,'1'*40,args[7],args[8])
            mutations=[(0,'source','2'*40),(0,'candidate_code',55),(0,'controls_sha256',{}),(0,'review_sha256','0'*64),(0,'fixture_sha256','0'*64),(0,'matrix_sha256','0'*64),(0,'runtime_archive_sha256','0'*64),(2,'head_branch','main'),(2,'head_sha','2'*40),(2,'conclusion','failure'),(2,'path','.github/workflows/other.yml'),(3,'jobs',[]),(4,'expired',True),(4,'id',999),(4,'workflow_run',dict(id=123,head_sha='2'*40))]
            for index,field,value in mutations:
                bad=copy.deepcopy(args);bad[index][field]=value
                with self.subTest(index=index,field=field),self.assertRaises(ValueError):gate.validate(*bad)
            bad=copy.deepcopy(args);bad[1]=bad[1].replace(b'Production signing: APPROVED',b'Production signing: PENDING');bad[0]['review_sha256']=gate.sha(bad[1])
            with self.assertRaises(ValueError):gate.validate(*bad)

    def test_gate_rejects_missing_extra_duplicate_failed_and_skipped_runtime_cases(self):
        for content in ('<testsuite/>','<testsuite><testcase classname="Synthetic.Case" name="other"/></testsuite>','<testsuite><testcase classname="Synthetic.Case" name="passes"/><testcase classname="Synthetic.Case" name="passes"/></testsuite>',*[f'<testsuite><testcase classname="Synthetic.Case" name="passes"><{tag}/></testcase></testsuite>' for tag in ('failure','error','skipped')]):
            with self.subTest(content=content),tempfile.TemporaryDirectory() as temporary:
                with self.assertRaises(ValueError):gate.validate(*self.gate_fixture(temporary,content))

    def test_prepared_scripts_fail_closed_without_branch_or_signer_gate_and_never_clear_data(self):
        for name in ('compile_upgrade1711_fixture.sh','verify_production_candidate1711.sh','verify_synthetic_upgrade1711.sh'):
            result=subprocess.run(['bash',str(ROOT/'scripts'/name)],env={'PATH':'/usr/bin:/bin','GITHUB_REF':'refs/heads/main'},capture_output=True)
            self.assertNotEqual(0,result.returncode)
        runner=(ROOT/'scripts/verify_synthetic_upgrade1711.sh').read_text()
        for forbidden in ('adb uninstall','pm clear','apksigner" sign','STORE_PASSWORD','KEY_PASSWORD','--ks'):
            self.assertNotIn(forbidden,runner)
        self.assertIn('adb install -r "$candidate_apk"',runner)
        self.assertIn('-e baselineCode 54 -e candidateCode 56',runner)
        self.assertIn('run_fixture verify after-restart',runner)

    def test_actual_shell_controls_reject_debug_candidate_and_baseline_before_any_install(self):
        # Run the actual scripts with explicit synthetic tools. A fabricated receipt here is
        # only a fixture for reaching the package guard, never external release evidence.
        for script,debug_phase in (('verify_synthetic_upgrade1711.sh','baseline'),('verify_synthetic_upgrade1711.sh','candidate'),('verify_production_candidate1711.sh','candidate')):
            with self.subTest(script=script,phase=debug_phase),tempfile.TemporaryDirectory() as temporary:
                folder=Path(temporary);project=folder/'project';project.mkdir()
                bins=folder/'bin';bins.mkdir();sdk=folder/'sdk/build-tools/36.0.0';sdk.mkdir(parents=True)
                log=folder/'tools.log';source='1'*40
                def executable(path,text):path.write_text('#!/bin/bash\nset -eu\n'+text);path.chmod(0o755)
                executable(bins/'git','printf "%s\\n" "'+source+'"\n')
                executable(bins/'adb','printf "%s\\n" "adb $*" >> "$TOOL_LOG"\ncase "$*" in\n"shell getprop ro.kernel.qemu") echo 1;;\n"shell getprop ro.build.version.sdk") echo 37;;\n"shell getenforce") echo Enforcing;;\n"shell pm list packages uk.co.traynor.privategallery") :;;\n*) exit 90;;\nesac\n')
                executable(bins/'sha256sum','if [[ "$1" == "$FAKE_BASELINE" ]]; then printf "%s  %s\\n" "'+baseline.APK_SHA256+'" "$1"; else /usr/bin/sha256sum "$@"; fi\n')
                executable(sdk/'aapt','phase=candidate; code=56; version=1.7.11\nif [[ "$*" == *"$FAKE_BASELINE"* ]]; then phase=baseline; code=54; version=1.7.10.2; fi\nprintf "package: name=\x27uk.co.traynor.privategallery\x27 versionCode=\x27%s\x27 versionName=\x27%s\x27\\n" "$code" "$version"\nprintf "sdkVersion:\x2737\x27\\ntargetSdkVersion:\x2737\x27\\napplication-label:\x27NovaOS\x27\\n"\nif [[ "$phase" == "$DEBUG_PHASE" ]]; then echo application-debuggable; fi\n')
                executable(sdk/'apksigner','printf "%s\\n" "apksigner $*" >> "$TOOL_LOG"\n[[ "$1" == verify ]] || exit 91\nprintf "%s\\n" "Signer #1 certificate SHA-256 digest: 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17" "Verified using v2 scheme (APK Signature Scheme v2): true" "Number of signers: 1"\n')
                original=folder/'baseline.apk';original.write_bytes(b'SYNTHETIC original APK')
                candidate=folder/'candidate.apk';candidate.write_bytes(b'SYNTHETIC candidate APK')
                fixture=ROOT/'docs/release1711/UpgradeFixtureTest.kt'
                receipt=folder/'SYNTHETIC-gate.json'
                receipt.write_text(json.dumps(dict(schema=1,decision='READY_FOR_GATED_SIGNER',source=source,baseline_source=gate.BASELINE,baseline_code=54,candidate_code=56,test_run=123,test_artifact=456,validated_case_count=1,controls_sha256=gate.controls(),fixture_sha256=gate.sha(fixture.read_bytes()),review_sha256='2'*64,matrix_sha256='3'*64,runtime_archive_sha256='4'*64)))
                environment=dict(PATH=str(bins)+':/usr/bin:/bin',TOOL_LOG=str(log),FAKE_BASELINE=str(original),DEBUG_PHASE=debug_phase,GITHUB_ACTIONS='true',GITHUB_EVENT_NAME='push',GITHUB_REPOSITORY='traynor1987/Private-gallery-',GITHUB_REF='refs/heads/'+gate.BRANCH,FROZEN_SOURCE=source,PRODUCTION_UPGRADE='1',RUNNER_TEMP=str(folder),ANDROID_HOME=str(folder/'sdk'),PRIVATE_GALLERY_1711_ROOT=str(project),PRIVATE_GALLERY_1711_GATE_RECEIPT=str(receipt),PRIVATE_GALLERY_1711_BASELINE_APK=str(original),PRIVATE_GALLERY_1711_CANDIDATE_APK=str(candidate),PRIVATE_GALLERY_1711_EVIDENCE_DIR=str(folder/'evidence'),PRIVATE_GALLERY_1711_UPGRADE_FIXTURE=str(fixture),PRIVATE_GALLERY_1711_UPGRADE_FIXTURE_SHA256=gate.sha(fixture.read_bytes()))
                result=subprocess.run(['bash',str(ROOT/'scripts'/script)],env=environment,capture_output=True,text=True)
                self.assertNotEqual(0,result.returncode)
                self.assertIn('Debuggable application rejected',result.stderr)
                calls=log.read_text() if log.exists() else ''
                self.assertNotIn('adb install',calls);self.assertNotIn('apksigner sign',calls)

if __name__=='__main__':unittest.main()
