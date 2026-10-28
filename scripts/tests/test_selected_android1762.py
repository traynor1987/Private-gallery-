from pathlib import Path
import tempfile
import unittest
from scripts.verify_selected_android1762 import verify


class SelectedAndroidEvidenceTest(unittest.TestCase):
    def check_report(self, cases):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source = root / 'source' / 'fixture' / 'Checks.kt'
            source.parent.mkdir(parents=True)
            source.write_text('@Test fun first() {}\n@Test(timeout=50) fun second() {}')
            reports = root / 'reports'
            reports.mkdir()
            (reports / 'tests.xml').write_text('<testsuite>' + cases + '</testsuite>')
            return verify(reports, 'fixture.Checks', root / 'source')

    def test_exact_whole_selected_suite(self):
        self.assertEqual(2, self.check_report('<testcase classname="fixture.Checks" name="first"/><testcase classname="fixture.Checks" name="second"/>'))

    def test_missing_case_rejected(self):
        with self.assertRaises(ValueError):
            self.check_report('<testcase classname="fixture.Checks" name="first"/>')

    def test_duplicate_and_extra_case_rejected(self):
        for last in ('first', 'unselected'):
            with self.assertRaises(ValueError):
                self.check_report('<testcase classname="fixture.Checks" name="first"/><testcase classname="fixture.Checks" name="second"/><testcase classname="fixture.Checks" name="' + last + '"/>')

    def test_skips_errors_and_failures_rejected(self):
        for kind in ('skipped', 'error', 'failure'):
            with self.assertRaises(ValueError):
                self.check_report('<testcase classname="fixture.Checks" name="first"/><testcase classname="fixture.Checks" name="second"><' + kind + '/></testcase>')
