import os
import pathlib
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

SCRIPT = pathlib.Path(__file__).resolve().parents[1] / 'verify_backup_exclusions.py'
REPO = SCRIPT.parent.parent
ANDROID = '{http://schemas.android.com/apk/res/android}'
DOMAINS = ('root', 'file', 'database', 'sharedpref', 'external', 'device_root', 'device_file', 'device_database', 'device_sharedpref')

class BackupExclusionsTest(unittest.TestCase):
    def run_check(self, mutate=lambda manifest, rules: None):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            manifest = ET.parse(REPO / 'app/src/main/AndroidManifest.xml')
            rules = ET.ElementTree(ET.Element('data-extraction-rules'))
            for mode in ('cloud-backup', 'device-transfer'):
                section = ET.SubElement(rules.getroot(), mode)
                for domain in DOMAINS:
                    ET.SubElement(section, 'exclude', domain=domain, path='.')
            mutate(manifest, rules)
            (root / 'app/src/main/res/xml').mkdir(parents=True)
            manifest.write(root / 'app/src/main/AndroidManifest.xml')
            rules.write(root / 'app/src/main/res/xml/data_extraction_rules.xml')
            return subprocess.run([sys.executable, str(SCRIPT), '--project', str(root)], capture_output=True, text=True)

    def test_complete_domains_exclude_current_and_future_sensitive_locations(self):
        result = self.run_check()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_missing_domain_leaves_paths_eligible(self):
        for mode in ('cloud-backup', 'device-transfer'):
            for domain in DOMAINS:
                def mutate(manifest, rules):
                    section = rules.find(mode)
                    section.remove(next(rule for rule in section if rule.get('domain') == domain))
                with self.subTest(mode=mode, domain=domain):
                    result = self.run_check(mutate)
                    self.assertNotEqual(0, result.returncode)
                    self.assertIn(domain, result.stderr)

    def test_narrow_exclusion_cannot_cover_future_vault_or_webview_files(self):
        def mutate(manifest, rules):
            next(rule for rule in rules.find('device-transfer') if rule.get('domain') == 'file').set('path', 'vault')
        self.assertNotEqual(0, self.run_check(mutate).returncode)

    def test_enabling_legacy_or_cloud_backup_fails(self):
        for name, value in [('allowBackup', 'true'), ('fullBackupContent', '@xml/backup_rules'), ('dataExtractionRules', '@xml/other_rules')]:
            def mutate(manifest, rules):
                manifest.find('application').set(ANDROID + name, value)
            with self.subTest(attribute=name):
                self.assertNotEqual(0, self.run_check(mutate).returncode)

    def test_missing_transfer_or_new_eligible_section_fails(self):
        def missing(manifest, rules):
            rules.getroot().remove(rules.find('device-transfer'))
        self.assertNotEqual(0, self.run_check(missing).returncode)
        def new_mode(manifest, rules):
            ET.SubElement(rules.getroot(), 'cross-platform-transfer', platform='ios')
        self.assertNotEqual(0, self.run_check(new_mode).returncode)

    def test_unsafe_merged_manifest_cannot_hide_behind_safe_source(self):
        with tempfile.TemporaryDirectory() as temp:
            merged = pathlib.Path(temp) / 'AndroidManifest.xml'
            manifest = ET.parse(REPO / 'app/src/main/AndroidManifest.xml')
            manifest.find('application').set(ANDROID + 'allowBackup', 'true')
            manifest.write(merged)
            result = subprocess.run([sys.executable, str(SCRIPT), '--project', str(REPO), '--merged-manifest', str(merged)], capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('merged manifest: allowBackup', result.stderr)

    def test_source_current_project_is_excluded(self):
        result = subprocess.run([sys.executable, str(SCRIPT), '--project', str(REPO)], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

class PackagedBackupExclusionsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        sdk = pathlib.Path(os.environ.get('ANDROID_HOME', '/tmp/private-gallery-android-sdk'))
        cls.aapt2 = sdk / 'build-tools/36.0.0/aapt2'
        cls.android = sdk / 'platforms/android-36/android.jar'
        if not cls.aapt2.is_file() or not cls.android.is_file():
            raise unittest.SkipTest('SDK aapt2 and android-36 platform required for binary APK mutations')

    def verify_apk(self, missing_domain=None, allow_backup=False, shorten_paths=False, unsafe_override=False):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp)
            res = root / 'res/xml'
            res.mkdir(parents=True)
            rules = ET.parse(REPO / 'app/src/main/res/xml/data_extraction_rules.xml')
            if missing_domain:
                section = rules.find('device-transfer')
                section.remove(next(rule for rule in section if rule.get('domain') == missing_domain))
            rules.write(res / 'data_extraction_rules.xml')
            if unsafe_override:
                override = root / 'res/xml-v31'
                override.mkdir()
                unsafe = ET.parse(REPO / 'app/src/main/res/xml/data_extraction_rules.xml')
                section = unsafe.find('device-transfer')
                section.remove(next(rule for rule in section if rule.get('domain') == 'device_database'))
                unsafe.write(override / 'data_extraction_rules.xml')
            manifest = root / 'AndroidManifest.xml'
            manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.backup.exclusions"><application android:allowBackup="' + ('true' if allow_backup else 'false') + '" android:fullBackupContent="false" android:dataExtractionRules="@xml/data_extraction_rules" /></manifest>')
            compiled, apk = root / 'compiled.zip', root / 'test.apk'
            subprocess.run([str(self.aapt2), 'compile', '--dir', str(root / 'res'), '-o', str(compiled)], check=True, capture_output=True)
            subprocess.run([str(self.aapt2), 'link', '-I', str(self.android), '--manifest', str(manifest), '-o', str(apk), str(compiled)], check=True, capture_output=True)
            if shorten_paths:
                optimized = root / 'optimized.apk'
                subprocess.run([str(self.aapt2), 'optimize', '--shorten-resource-paths', '-o', str(optimized), str(apk)], check=True, capture_output=True)
                apk = optimized
            return subprocess.run([sys.executable, str(SCRIPT), '--project', str(REPO), '--apk', str(apk), '--aapt2', str(self.aapt2)], capture_output=True, text=True)

    def test_binary_apk_with_complete_exclusions_is_accepted(self):
        result = self.verify_apk()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_packaged_device_preference_eligibility_fails_even_with_safe_source(self):
        result = self.verify_apk(missing_domain='device_sharedpref')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('device_sharedpref', result.stderr)

    def test_packaged_manifest_backup_enablement_fails_even_with_safe_source(self):
        result = self.verify_apk(allow_backup=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('allowBackup', result.stderr)

    def test_release_shortened_rules_are_verified_through_the_resource_table(self):
        result = self.verify_apk(shorten_paths=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_shortened_rules_still_reject_eligible_device_preferences(self):
        result = self.verify_apk(missing_domain='device_sharedpref', shorten_paths=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('device_sharedpref', result.stderr)

    def test_shortened_manifest_still_rejects_backup_enablement(self):
        result = self.verify_apk(allow_backup=True, shorten_paths=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('allowBackup', result.stderr)

    def test_all_resource_configurations_are_checked_including_shortened_overrides(self):
        for shortened in (False, True):
            with self.subTest(shortened=shortened):
                result = self.verify_apk(shorten_paths=shortened, unsafe_override=True)
                self.assertNotEqual(0, result.returncode)
                self.assertIn('device_database', result.stderr)


if __name__ == '__main__':
    unittest.main()
