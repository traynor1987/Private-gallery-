"""Focused exact-case accounting refuses ambiguous or incomplete Android evidence."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

SCRIPT = Path(__file__).resolve().parents[1] / 'verify_evidence179_cases.py'

class Cases(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location('gate179', SCRIPT)
        self.gate = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.gate)
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.expected = [{'classname': 'fixture.Owner', 'name': 'lockRevokes'}]

    def write(self, identities, child=None):
        suite = ET.Element('testsuite')
        for cls, name in identities:
            c = ET.SubElement(suite, 'testcase', classname=cls, name=name)
            if child:
                ET.SubElement(c, child)
        ET.ElementTree(suite).write(self.root / 'result.xml')

    def test_exact_unique_success(self):
        self.write([('fixture.Owner', 'lockRevokes')])
        self.assertEqual(self.gate.verify(self.root, self.expected), 1)

    def test_missing_extra_duplicate_and_no_results_refused(self):
        for identities in [[], [('other.Owner','lockRevokes')], [('fixture.Owner','lockRevokes')]*2]:
            with self.subTest(identities=identities):
                self.write(identities)
                with self.assertRaises(ValueError):
                    self.gate.verify(self.root, self.expected)

    def test_failure_error_skipped_refused(self):
        for tag in ['failure', 'error', 'skipped']:
            self.write([('fixture.Owner','lockRevokes')], tag)
            with self.assertRaises(ValueError):
                self.gate.verify(self.root, self.expected)

    def test_empty_duplicate_and_malformed_manifest_refused(self):
        self.write([('fixture.Owner','lockRevokes')])
        for manifest in [[], self.expected*2, [{'classname':'fixture.Owner'}], [{'classname':'fixture.Owner','name':''}]]:
            with self.assertRaises(ValueError):
                self.gate.verify(self.root, manifest)

if __name__ == '__main__':
    unittest.main()
