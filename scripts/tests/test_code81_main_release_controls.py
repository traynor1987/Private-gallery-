"""Main-branch provenance checks, independent of Android compilation and emulator execution."""
import copy
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from scripts.tests import test_upgrade1711_controls as historical
gate = historical.gate


class MainReleaseControls(unittest.TestCase):
    def fixture(self, directory):
        values = historical.Upgrade1711Controls().gate_fixture(directory)
        marker = values[0]
        marker.update(baseline_code=83, candidate_code=84)
        values[1] += (
            b"Authorization: authenticated owner push\n"
            b"Release scope: public stable after disposable upgrade verification\n"
            b"Physical Fold acceptance: pending\n"
            b"Runtime workflow run: 123\n"
        )
        marker["review_sha256"] = gate.sha(values[1])
        values[4]["digest"] = "sha256:" + marker["runtime_archive_sha256"]
        return values

    def test_main_source_passes_provenance_before_separate_runtime_report_validation(self):
        with tempfile.TemporaryDirectory() as directory:
            values = self.fixture(directory)
            self.assertEqual("main", values[2]["head_branch"])
            with patch.object(gate, "runtime_cases", return_value=1) as reports:
                proof = gate.validate(*values)
            reports.assert_called_once()
            self.assertEqual("READY_FOR_GATED_SIGNER", proof["decision"])

    def test_other_branch_cannot_supply_production_runtime_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            original = self.fixture(directory)
            for branch in ("feature/novaos-code81-core-apps", "release/unreviewed"):
                values = copy.deepcopy(original)
                values[2]["head_branch"] = branch
                with self.subTest(branch=branch), patch.object(gate, "runtime_cases") as reports:
                    with self.assertRaisesRegex(ValueError, "runtime source/branch/event"):
                        gate.validate(*values)
                    reports.assert_not_called()

    def test_changed_source_or_artifact_invalidates_provenance(self):
        with tempfile.TemporaryDirectory() as directory:
            original = self.fixture(directory)
            for index, field, value in ((2, "head_sha", "2" * 40), (4, "digest", "sha256:" + "0" * 64)):
                values = copy.deepcopy(original)
                values[index][field] = value
                with self.subTest(field=field), patch.object(gate, "runtime_cases") as reports:
                    with self.assertRaises(ValueError):
                        gate.validate(*values)
                    reports.assert_not_called()

    def test_provenance_alone_cannot_replace_required_original_reports(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "Missing or duplicate focused cases"):
                gate.validate(*self.fixture(directory))


if __name__ == "__main__":
    unittest.main()
