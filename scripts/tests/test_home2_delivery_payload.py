"""Execute the actual signing workflow's staging body against harmless synthetic inputs."""
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import yaml

REPO = Path(__file__).resolve().parents[2]
REPORTS = ('compilation.json', 'baseline-build.gradle.kts', 'instrumentation-package.txt',
           'instrumentation-manifest.txt', 'compile.log')


class Home2DeliveryPayloadTest(unittest.TestCase):
    def stage(self, unexpected=None):
        workflow = yaml.safe_load((REPO / '.github/workflows/acceptance1762.yml').read_text())
        step = next(s for s in workflow['jobs']['home2-production']['steps']
                    if s.get('name') == 'Stage the one verified production APK and complete upgrade evidence')
        code = step['run'].split("python3 - <<'PY'\n", 1)[1].split('\nPY\n', 1)[0]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            build = root / 'app/build'
            evidence = build / 'production-evidence'
            upgrade = evidence / 'from64'
            upgrade.mkdir(parents=True)
            source = '2' * 40
            candidate = build / 'outputs/apk/release/app-release.apk'
            candidate.parent.mkdir(parents=True)
            candidate.write_bytes(b'synthetic authorized application')
            digest = hashlib.sha256(candidate.read_bytes()).hexdigest()
            (upgrade / 'upgrade-result.txt').write_text('PASS: synthetic upgrade')
            (upgrade / 'upgrade-candidate-source-sha.txt').write_text(source)
            (upgrade / 'upgrade-candidate-sha256.txt').write_text(digest + ' application.apk')
            for phase in ('seed', 'verify', 'after-restart'):
                (upgrade / f'upgrade-{phase}.txt').write_text('OK (1 test)')
            runner = root / 'runner'
            inputs = runner / 'release1711-inputs'
            inputs.mkdir(parents=True)
            for name in ('github-run.json', 'github-jobs.json', 'github-artifact.json',
                         'baseline-github-run.json', 'baseline-github-job.json', 'baseline-github-artifact.json'):
                (inputs / name).write_text('{}')
            controls = runner / 'release1711-controls'
            controls.mkdir()
            (controls / 'review.txt').write_text('Synthetic reviewed inputs')
            compiled = runner / 'compiled'
            compiled.mkdir()
            for name in REPORTS:
                (compiled / name).write_text('receipt:' + name)
            (compiled / 'instrumentation.apk').write_bytes(b'synthetic compiled test harness')
            signed = runner / 'signed-instrumentation.apk'
            signed.write_bytes(b'synthetic production-target test harness')
            if unexpected:
                (evidence / unexpected).write_bytes(b'must never be published')
            env = dict(RUNNER_TEMP=str(runner), FROZEN_SOURCE=source,
                       PRIVATE_GALLERY_1711_COMPILED_DIR=str(compiled),
                       PRIVATE_GALLERY_1711_SIGNED_INSTRUMENTATION_APK=str(signed),
                       GITHUB_OUTPUT=str(runner / 'outputs'))
            prior = Path.cwd()
            try:
                os.chdir(root)
                with patch.dict(os.environ, env):
                    if unexpected:
                        with self.assertRaisesRegex(AssertionError, 'Evidence contains'):
                            exec(compile(code, '<actual workflow staging>', 'exec'), {})
                        self.assertFalse((build / 'NovaOS-1.7.30-visual-foundation-code65-evidence.zip').exists())
                        return
                    exec(compile(code, '<actual workflow staging>', 'exec'), {})
                self.assertEqual(f'frozen_source={source}\napk_sha256={digest}\n', (runner / 'outputs').read_text())
                self.assertEqual(candidate.read_bytes(), (build / 'NovaOS-1.7.30-visual-foundation-code65-owner-review.apk').read_bytes())
                with zipfile.ZipFile(build / 'NovaOS-1.7.30-visual-foundation-code65-evidence.zip') as zipped:
                    names = zipped.namelist()
                    self.assertFalse(any(Path(n).suffix.lower() in {'.apk', '.apks', '.aab', '.jks', '.keystore', '.zip'} for n in names))
                    for name in REPORTS:
                        self.assertEqual(('receipt:' + name).encode(), zipped.read('compiled-upgrade/' + name))
                    self.assertIn(b'synthetic DEMO data', zipped.read('REVIEW-NOTES.md'))
                    summary = json.loads(zipped.read('release-summary.json'))
                    self.assertEqual(digest, summary['apk_sha256'])
                    self.assertEqual(('1.7.30-visual-foundation', 65, 'PASS'), (summary['version'], summary['version_code'], summary['original_key_code64_upgrade']))
            finally:
                os.chdir(prior)

    def test_baseline_provenance_and_compilation_precede_credentials_for_existing_job(self):
        workflow=yaml.safe_load((REPO/'.github/workflows/acceptance1762.yml').read_text())
        self.assertEqual(9,len(workflow['jobs']))
        steps=workflow['jobs']['home2-production']['steps']
        fetch=next(s['with']['script'] for s in steps if s.get('name')=='Fetch original exact-source runtime API evidence and pinned signed baseline')
        for pin in ('run_id:42398677707','job_id:128481534136','artifact_id:13584728173',
                    "baselineJob.run_id!==42398677707", "baselineJob.name!=='home2-production'",
                    "baselineJob.conclusion!=='success'", "baselineJob.head_sha!=='826ab9a48bcd20ff6dd764bf1c5caa770d45f676'",
                    '61126104','46aed1b6c8b0019bc60df1e6d8de422467aecb8a8bb5f1c4e9c63e8ae0a9cbcf'):
            self.assertIn(pin,fetch)
        compile_index=next(i for i,s in enumerate(steps) if s.get('name')=='Compile reviewed baseline-compatible fixture with no signing environment')
        gate_index=next(i for i,s in enumerate(steps) if s.get('name')=='Validate original runtime reports and independent production approval before signer access')
        self.assertLess(gate_index,compile_index)
        tools=next(s['run'] for s in steps if s.get('name')=='Install Android 17 tools and verify the original code64 APK')
        self.assertIn('cmdline-tools;22.0',tools)
        self.assertIn('platforms;android-37.1',tools)
        upgrade_index=next(i for i,s in enumerate(steps) if s.get('name')=='Original-key code64 to code65 upgrade and restart without uninstall or clear')
        upgrade=steps[upgrade_index]['with']
        self.assertEqual(('37.0','google_apis','x86_64','novaos1711-original-key-from64'),
                         (upgrade['api-level'],upgrade['target'],upgrade['arch'],upgrade['avd-name']))
        self.assertIs(True,upgrade['force-avd-creation'])
        self.assertIs(False,upgrade['disable-animations'])
        self.assertIn('-no-snapshot',upgrade['emulator-options'])
        stage_index=next(i for i,s in enumerate(steps) if s.get('name')=='Stage the one verified production APK and complete upgrade evidence')
        delivery_index=next(i for i,s in enumerate(steps) if s.get('name')=='Preserve verified delivery and evidence')
        self.assertLess(upgrade_index,stage_index)
        self.assertLess(stage_index,delivery_index)
        self.assertEqual('novaos-1.7.30-visual-foundation-code65-owner-review',steps[delivery_index]['with']['name'])
        for i,step in enumerate(steps):
            if 'secrets.PRIVATE_GALLERY_SIGNING_' in str(step): self.assertLess(compile_index,i)
        job=workflow['jobs']['home2-production']
        self.assertEqual('read',job['permissions']['contents'])
        self.assertIn("github.ref == 'refs/heads/feature/novaos-visual-foundation'",job['if'])
        self.assertIn("run.head_branch!=='feature/novaos-visual-foundation'",fetch)
        self.assertIn("baselineRun.head_branch!=='feature/novaos-visual-foundation'",fetch)
        self.assertNotIn('gh release',str(steps))
        notes=steps[stage_index]['run']
        self.assertIn('synthetic DEMO data',notes)
        self.assertIn('do not activate protection',notes)
        self.assertIn('Galaxy Z Fold acceptance remains pending',notes)

    def test_publication_is_separate_and_verifies_only_the_exact_application(self):
        workflow=yaml.safe_load((REPO/'.github/workflows/acceptance1762.yml').read_text())
        signer=workflow['jobs']['home2-production']
        publish=workflow['jobs']['home2-publish']
        self.assertEqual({'contents':'read','actions':'read'},signer['permissions'])
        self.assertEqual({'contents':'write','actions':'read'},publish['permissions'])
        self.assertEqual('home2-production',publish['needs'])
        self.assertIn("needs.home2-production.result == 'success'",publish['if'])
        self.assertNotIn('actions/checkout',str(publish))
        self.assertNotIn('secrets.',str(publish))
        self.assertNotIn('runner.',str(publish['env']))
        self.assertIn('$RUNNER_TEMP/novaos-code65-delivery',publish['steps'][0]['run'])
        self.assertEqual('${{ env.DELIVERY_DIR }}',publish['steps'][1]['with']['path'])
        self.assertIn('needs.home2-production.outputs.frozen_source',str(publish))
        self.assertIn('needs.home2-production.outputs.apk_sha256',str(publish))
        self.assertIn('novaos-1.7.30-visual-foundation-code65',str(publish))
        self.assertIn('--prerelease --latest=false',str(publish))
        self.assertIn('--target "$FROZEN_SOURCE"',str(publish))
        self.assertNotIn('gh api --method POST',str(publish))
        self.assertIn("needs.home2-production.result == 'skipped'",publish['if'])
        self.assertIn('[home2-publish-code65]',publish['if'])
        recover=next(step for step in publish['steps'] if step.get('name')=='Recover the signed Code 65 artifact from acceptance run 32')
        for requirement in ('42410507182','128517442213','13589877482',"job.conclusion!=='success'",'d2b6c0a22bfc5cb663ef79bec6e929ec494fb338b106d950468df3e1d2b8c3f9'):
            self.assertIn(requirement,recover['with']['script'])
        self.assertNotIn('gh release upload',str(publish))

    def test_publication_rejects_wrong_source_bytes_upgrade_and_extra_files(self):
        workflow=yaml.safe_load((REPO/'.github/workflows/acceptance1762.yml').read_text())
        publish=workflow['jobs']['home2-publish']
        step=next(s for s in publish['steps'] if s.get('name')=='Recheck the exact signed delivery before publication')
        code=step['run'].split("python3 - <<'PYPUBLISH'\n",1)[1].split('\nPYPUBLISH\n',1)[0]
        source='2'*40
        apk=b'synthetic authorized application'
        digest=hashlib.sha256(apk).hexdigest()
        apk_name=publish['env']['APK_NAME']
        evidence_name='NovaOS-1.7.30-visual-foundation-code65-evidence.zip'
        for mutation in ('valid','source','apk','upgrade','restart','extra'):
            with self.subTest(mutation=mutation),tempfile.TemporaryDirectory() as temporary:
                delivery=Path(temporary)
                (delivery/apk_name).write_bytes(apk if mutation!='apk' else b'different application')
                if mutation=='extra':
                    (delivery/'instrumentation.apk').write_bytes(b'forbidden payload')
                summary=dict(source_sha=source,apk_sha256=digest,version='1.7.30-visual-foundation',
                             version_code=65,package='uk.co.traynor.privategallery',
                             original_certificate_sha256='94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17',
                             original_key_code64_upgrade='PASS',restart='PASS')
                if mutation=='source':
                    summary['source_sha']='3'*40
                if mutation=='upgrade':
                    summary['original_key_code64_upgrade']='FAIL'
                if mutation=='restart':
                    summary['restart']='FAIL'
                with zipfile.ZipFile(delivery/evidence_name,'w') as archive:
                    archive.writestr('release-summary.json',json.dumps(summary))
                with patch.dict(os.environ,dict(DELIVERY_DIR=temporary,APK_NAME=apk_name,
                                               FROZEN_SOURCE=source,EXPECTED_APK_SHA256=digest)):
                    if mutation=='valid':
                        exec(compile(code,'<actual workflow publication check>','exec'),{})
                        self.assertEqual(f'{digest}  {apk_name}\n',(delivery/'SHA256SUMS.txt').read_text())
                    else:
                        with self.assertRaises(AssertionError):
                            exec(compile(code,'<actual workflow publication check>','exec'),{})
                        self.assertFalse((delivery/'SHA256SUMS.txt').exists())

    def test_only_application_is_delivered_and_all_upgrade_receipts_remain(self):
        self.stage()

    def test_unexpected_installable_or_signing_payload_is_rejected_before_archiving(self):
        for name in ('unexpected.apk', 'unexpected.APK', 'split.apks', 'bundle.aab', 'signer.jks', 'signer.keystore', 'nested.zip'):
            with self.subTest(name=name):
                self.stage(name)


if __name__ == '__main__':
    unittest.main()
