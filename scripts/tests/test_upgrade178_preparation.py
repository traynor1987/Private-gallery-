"""Host source gates only: no claim of fixture compilation or an Android upgrade run."""
import importlib.util
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("validate_upgrade178", ROOT / "scripts/validate_upgrade178.py")
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)


class Upgrade178PreparationTest(unittest.TestCase):
    def test_prepared_never_means_release_approved(self):
        result = GATE.audit(ROOT)
        self.assertEqual([], result["errors"])
        self.assertEqual("PREPARED", result["source_audit"])
        self.assertEqual("BLOCKED", result["approval"])
        self.assertEqual("NOT_RUN", result["android_execution"])
        self.assertEqual("NOT_RUN", result["original_key_upgrade"])

    def config(self, baseline=None, branch="refs/heads/release/1.7.8-private-apps"):
        env = {**os.environ, "GITHUB_REF": branch}
        env.pop("PRIVATE_GALLERY_UPGRADE_BASELINE", None)
        if baseline is not None:
            env["PRIVATE_GALLERY_UPGRADE_BASELINE"] = str(baseline)
        return subprocess.run(["bash", "-c", 'source scripts/release_configuration.sh; printf "%s %s %s %s %s %s" "$release_min_sdk" "$release_version" "$release_code" "$baseline_version" "$baseline_code" "$baseline_sha"'], cwd=ROOT, env=env, capture_output=True, text=True)

    def test_exact_baseline_default_and_explicit(self):
        for baseline in (None, 47):
            result = self.config(baseline)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(f"37 1.7.8 48 1.7.8 47 {GATE.BASELINE_SHA}", result.stdout)

    def test_unsupported_baselines_and_branch_refused(self):
        for baseline in (45, 46, 48, "", "047", "47; echo bypass"):
            with self.subTest(baseline=baseline):
                # Empty means the explicitly supported shell default, so test an actual blank instead.
                result = self.config(" " if baseline == "" else baseline)
                self.assertNotEqual(0, result.returncode)
        self.assertNotEqual(0, self.config(branch="refs/heads/unreviewed-1.7.8").returncode)

    def test_old_release_selection_unchanged(self):
        result = self.config(branch="refs/heads/release/1.7.7-recently-deleted")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("37 1.7.7 46 1.7.6.2 45 97e94452ceb9c5262521d7552f0c25eeb07374eb", result.stdout)

    def test_missing_coverage_and_tampering_fail_closed(self):
        mutations = (
            ("docs/release178/UpgradeFixtureTest.kt", "candidateCode == 48", "candidateCode == 49"),
            ("docs/release178/UpgradeFixtureTest.kt", "assertCiphertextAfterReads(before)", "// omitted"),
            ("docs/release178/UpgradeFixtureTest.kt", 'assertRecentlyDeleted(before.getJSONObject("hiddenDeleted"),repo.recentlyDeleted(),repo.items())', "// omitted"),
            ("scripts/release_configuration.sh", GATE.BASELINE_SHA, "0" * 40),
            ("scripts/upgrade/UpgradeFixtureTest.kt", "candidateCode == 46", "candidateCode == 47"),
            ("scripts/verify_synthetic_upgrade.sh", "adb install -r", "adb install"),
        )
        for relative, old, new in mutations:
            with self.subTest(relative=relative, removed=old), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                for path in (*GATE.PRESERVED, "scripts/release_configuration.sh", "docs/release178/UpgradeFixtureTest.kt"):
                    destination = root / path
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(ROOT / path, destination)
                target = root / relative
                original = target.read_text()
                self.assertIn(old, original)
                target.write_text(original.replace(old, new))
                result = GATE.audit(root)
                self.assertEqual("FAILED", result["source_audit"])
                self.assertEqual("BLOCKED", result["approval"])


if __name__ == "__main__":
    unittest.main()
