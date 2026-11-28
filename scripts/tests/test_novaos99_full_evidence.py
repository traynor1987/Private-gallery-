from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_novaos99_full_evidence import discovered_cases, exact_cases, xml_cases


class CompleteAndroidEvidenceTest(unittest.TestCase):
    name = "uk.co.traynor.privategallery.ExampleTest"

    def result(self, *names, declared=None):
        records = []
        for name in names:
            records.extend((f"INSTRUMENTATION_STATUS: class={self.name}",
                            f"INSTRUMENTATION_STATUS: test={name}",
                            "INSTRUMENTATION_STATUS_CODE: 1"))
        return "\n".join([*records, f"OK ({declared or len(names)} tests)", "INSTRUMENTATION_CODE: -1"])

    def test_discovery_requires_complete_unique_test_inventory(self):
        self.assertEqual(discovered_cases(self.result("first", "second")),
                         {self.name + "#first", self.name + "#second"})
        with self.assertRaisesRegex(ValueError, "count mismatch"):
            discovered_cases(self.result("first", "second", declared=3))
        with self.assertRaisesRegex(ValueError, "Duplicate discovered"):
            discovered_cases(self.result("first", "first"))
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            discovered_cases(self.result("first", "second").replace("INSTRUMENTATION_CODE: -1", ""))

    def test_xml_rejects_skips_failures_and_duplicate_ids(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp)
            testcase = f'<testcase classname="{self.name}" name="first" />'
            report = path / "TEST-fixture.xml"
            report.write_text(f'<testsuites tests="1"><testsuite>{testcase}</testsuite></testsuites>')
            self.assertEqual(xml_cases(path), {self.name + "#first"})
            with self.assertRaisesRegex(ValueError, "missing"):
                exact_cases(xml_cases(path), {self.name + "#first", self.name + "#second"}, "Full")
            for outcome in ("skipped", "failure", "error"):
                with self.subTest(outcome=outcome):
                    report.write_text(f'<testsuite tests="1"><testcase classname="{self.name}" name="first">'
                                      f"<{outcome}/></testcase></testsuite>")
                    with self.assertRaisesRegex(ValueError, "Non-passing"):
                        xml_cases(path)
            report.write_text(f'<testsuite tests="2">{testcase}{testcase}</testsuite>')
            with self.assertRaisesRegex(ValueError, "Duplicate executed"):
                xml_cases(path)


if __name__ == "__main__":
    unittest.main()
