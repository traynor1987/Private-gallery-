import unittest
from scripts.debug_apk_identity1762 import parse_identity


class DebugApkIdentityTest(unittest.TestCase):
    def test_reads_manifest_identity(self):
        self.assertEqual(parse_identity("package: name='uk.co.traynor.privategallery.hiddenprototype' versionCode='44'\n"),
                         'uk.co.traynor.privategallery.hiddenprototype')

    def test_refuses_production_and_unrelated_packages(self):
        for value in ('uk.co.traynor.privategallery', 'other.hiddenprototype'):
            with self.assertRaises(ValueError):
                parse_identity(f"package: name='{value}' versionCode='44'")

    def test_missing_identity_fails_closed(self):
        with self.assertRaises(ValueError):
            parse_identity('application-label: test')
