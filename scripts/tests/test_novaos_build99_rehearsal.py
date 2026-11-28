import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import novaos_build99_rehearsal as gate


class Build99RehearsalTests(unittest.TestCase):
    def setUp(self):
        self.head, self.parent, self.source, self.tree = (digit * 40 for digit in "1234")
        self.evidence = {
            "development": {"run": 11, "attempt": 1, "artifact": 12, "sha256": "a" * 64},
            "full_security": {"run": 21, "attempt": 1, "artifact": 22, "sha256": "b" * 64},
        }
        self.env = {
            "GITHUB_REPOSITORY": gate.REPO, "GITHUB_REF": "refs/heads/main", "GITHUB_EVENT_NAME": "push",
            "GITHUB_RUN_ATTEMPT": "1", "GITHUB_ACTOR_ID": str(gate.OWNER),
            "GITHUB_TRIGGERING_ACTOR": "traynor1987", "GITHUB_SHA": self.head,
        }
        self.event = {"before": self.parent, "after": self.head, "sender": {"id": gate.OWNER},
                      "forced": False, "created": False}
        self.common, self.control_tree = "5" * 40, "6" * 40
        checksums = patch.object(gate, "checksums", return_value={gate.WORKFLOW: "7" * 64})
        checksums.start()
        self.addCleanup(checksums.stop)
        self.git_responses = {
            ("rev-parse", "HEAD"): self.head,
            ("rev-list", "--parents", "-n", "1", self.head): f"{self.head} {self.parent}",
            ("diff", "--name-only", self.parent, self.head): gate.MARKER,
            ("rev-parse", self.source + "^{tree}"): self.tree,
            ("merge-base", self.parent, self.source): self.common,
            ("rev-parse", self.common + "^{tree}"): self.control_tree,
            ("rev-parse", self.parent + "^{tree}"): self.control_tree,
            ("merge-base", self.common, gate.CONTROL_BASE): gate.CONTROL_BASE,
            ("diff", "--name-only", gate.CONTROL_BASE, self.parent): "\n".join(gate.CONTROL_CHANGES),
            ("diff", "--name-only", self.parent, self.source, "--", *sorted(gate.TRUSTED_INPUTS)): "",
        }
        with patch.object(gate, "git", side_effect=self.git_request):
            self.approval = gate.marker_for(self.parent, self.source, self.evidence)

    def git_request(self, *args):
        return self.git_responses[args]

    def authorize(self, env, event, approval):
        with patch.object(gate, "git", side_effect=self.git_request), \
                patch.object(gate, "api", return_value={"object": {"sha": self.head}}):
            return gate.verify_authority(env, event, approval)

    def test_only_owner_marker_for_exact_reviewed_inputs_is_admitted(self):
        self.assertEqual(self.authorize(self.env, self.event, self.approval), self.head)
        for key, value in (("GITHUB_ACTOR_ID", "1"), ("GITHUB_TRIGGERING_ACTOR", "other"),
                           ("GITHUB_RUN_ATTEMPT", "2"), ("GITHUB_EVENT_NAME", "workflow_dispatch"),
                           ("GITHUB_REF", f"refs/heads/{gate.BRANCH}")):
            with self.subTest(key=key), self.assertRaisesRegex(ValueError, "authenticated owner main push"):
                self.authorize(dict(self.env, **{key: value}), self.event, self.approval)
        for key, value in (("forced", True), ("sender", {"id": 1}), ("created", True)):
            with self.subTest(key=key), self.assertRaisesRegex(ValueError, "marker-only"):
                self.authorize(self.env, dict(self.event, **{key: value}), self.approval)
        for key, value in (("candidate_tree", "9" * 40), ("inputs_sha256", {}), ("version_code", 98)):
            with self.subTest(key=key), self.assertRaisesRegex(ValueError, "bind the exact candidate"):
                self.authorize(self.env, self.event, dict(self.approval, **{key: value}))

    def test_candidate_cannot_change_a_trusted_control_or_skip_its_reviewed_parent(self):
        key = ("diff", "--name-only", self.parent, self.source, "--", *sorted(gate.TRUSTED_INPUTS))
        self.git_responses[key] = "scripts/run_build99_upgrade.sh"
        with self.assertRaisesRegex(ValueError, "changes a trusted release control"):
            self.authorize(self.env, self.event, self.approval)
        self.git_responses[key] = ""
        self.git_responses[("rev-parse", self.common + "^{tree}")] = "0" * 40
        with self.assertRaisesRegex(ValueError, "moved outside"):
            self.authorize(self.env, self.event, self.approval)

    def test_release_controls_cannot_add_an_unrelated_application_change(self):
        self.git_responses[("diff", "--name-only", gate.CONTROL_BASE, self.parent)] += "\napp/build.gradle.kts"
        with self.assertRaisesRegex(ValueError, "outside the reviewed release controls"):
            self.authorize(self.env, self.event, self.approval)

    def test_run_attempt_mismatch_and_missing_full_receipt_prevent_authorization(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "receipts"
            with patch.object(gate, "run_record", return_value={"run_attempt": 2}), \
                    patch.object(gate, "artifact") as download:
                with self.assertRaisesRegex(ValueError, "attempt differs"):
                    gate.verify_regressions(self.approval, output)
                download.assert_not_called()
            proof = Path(directory) / "proof.zip"
            with zipfile.ZipFile(proof, "w") as archive:
                archive.writestr("full99/receipt.json", json.dumps({
                    "source_sha": self.source, "source_tree": "9" * 40,
                    "version_code": 99, "api_level": 37, "decision": "PASS",
                }))
                archive.writestr("full99/source-sha.txt", self.source)
                archive.writestr("full99/source-tree.txt", self.tree)
            with self.assertRaisesRegex(ValueError, "does not match"):
                gate.verify_full_reports(proof, self.approval)

    def test_archived_fixture_exercises_legacy_widgets_role_and_guardian_preferences(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            build = root / "app/build.gradle.kts"
            build.parent.mkdir()
            build.write_text(gate.git("show", f"{gate.BASELINE}:app/build.gradle.kts"))
            gate.create_fixture(root)
            fixture, = build.parent.rglob("UpgradeFixtureTest.kt")
            source = fixture.read_text()
            for text in ("baselineCode == 97 && candidateCode == 99", "verifySelectedDefaultHome()",
                         "GuardianPresentationMode.GUARDIAN", "Preserve widget placements on both layouts",
                         "Preserve App Library folder preference", "assistantState99(space,before,seed)",
                         'assertEquals("2.0.0",available'):
                self.assertIn(text, source)
            self.assertNotIn("applicationIdSuffix", build.read_text())

    def test_production_assets_only_follow_signed_runtime_proof(self):
        workflow = Path(gate.WORKFLOW).read_text()
        pre_sign, sign = workflow.split("Verify original production identity and sign the frozen Build 99 source", 1)
        self.assertNotIn("secrets.", pre_sign)
        self.assertIn("novaos_build99_rehearsal.py recheck", pre_sign)
        self.assertIn("novaos_build99_production.py recheck", pre_sign)
        self.assertIn('sha256sum -c "$root/instrumentation.sha256"', pre_sign)
        self.assertIn("secrets.PRIVATE_GALLERY_SIGNING_KEYSTORE_BASE64", sign)
        self.assertIn("script: bash scripts/run_build99_upgrade.sh", sign)
        self.assertEqual(sign.count("if: steps.authorize.outputs.mode == 'production'"), 2)
        self.assertLess(sign.index("script: bash scripts/run_build99_upgrade.sh"), sign.index("Stage verified Build 99 stable assets"))
        self.assertNotIn("contents: write", workflow)


if __name__ == "__main__":
    unittest.main()
