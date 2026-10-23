import importlib.util
import io
from pathlib import Path
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('provenance', Path(__file__).parents[1] / 'verify_camera_acceptance_source.py')
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)

class ProvenanceTest(unittest.TestCase):
    def archive(self, fail=False, skip=False, short=False):
        output = io.BytesIO()
        with zipfile.ZipFile(output, 'w') as z:
            for group, (prefix, count) in p.SUITES.items():
                count -= int(short and group == 'android_full')
                cases = ['<testcase name="t%d">%s</testcase>' % (i, '<failure/>' if fail and i == 0 else '<skipped/>' if skip and i == 0 else '') for i in range(count)]
                z.writestr(prefix + 'TEST-provenance.xml', '<testsuites><testsuite>' + ''.join(cases) + '</testsuite></testsuites>')
        output.seek(0)
        return zipfile.ZipFile(output)

    def test_complete_nested_suites(self):
        with self.archive() as z:
            self.assertEqual(292, p.count_suites(z)['android_full']['tests'])

    def test_failure_is_never_reused(self):
        with self.archive(fail=True) as z, self.assertRaises(ValueError): p.count_suites(z)

    def test_skip_is_never_reused(self):
        with self.archive(skip=True) as z, self.assertRaises(ValueError): p.count_suites(z)

    def test_missing_case_is_never_reused(self):
        with self.archive(short=True) as z, self.assertRaises(ValueError): p.count_suites(z)

    def test_application_or_build_change_rejected(self):
        for path in ['app/build.gradle.kts', 'app/src/main/X.kt', 'app/src/androidTest/X.kt', 'build.gradle.kts', 'gradle/libs.versions.toml', '.github/workflows/android.yml', 'scripts/verify_production_candidate.sh']:
            with self.subTest(path=path), self.assertRaises(ValueError): p.check_paths([path])

    def test_only_reviewed_non_app_paths_allowed(self):
        p.check_paths(['V1_7_RECOVERY.md', 'docs/release17/evidence-ci657/summary.json', 'scripts/upgrade/UpgradeFixtureTest.kt', '.github/workflows/camera-acceptance.yml', 'scripts/verify_camera_acceptance_source.py', 'scripts/tests/test_camera_acceptance_source.py'])

    def test_wrong_artifact_or_source_rejected(self):
        good = {'id': p.ARTIFACT, 'digest': 'sha256:' + p.DIGEST, 'workflow_run': {'id': p.RUN, 'head_sha': p.SOURCE, 'repository_id': 1374324372}}
        p.check_metadata(good)
        for field, value in [('id', 1), ('digest', 'sha256:' + '0'*64), ('workflow_run', {'id': p.RUN, 'head_sha': '0'*40, 'repository_id': 1374324372})]:
            bad = dict(good); bad[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError): p.check_metadata(bad)

if __name__ == '__main__': unittest.main()
