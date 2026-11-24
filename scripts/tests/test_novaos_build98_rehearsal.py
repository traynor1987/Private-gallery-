import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import novaos_build97_gate as shared
import novaos_build98_rehearsal as gate


class Build98RehearsalTests(unittest.TestCase):
    def setUp(self):
        self.head = "1" * 40
        self.parent = "2" * 40
        self.env = {
            "GITHUB_REPOSITORY": gate.REPO, "GITHUB_REF": "refs/heads/main", "GITHUB_EVENT_NAME": "push",
            "GITHUB_RUN_ATTEMPT": "1", "GITHUB_ACTOR_ID": str(gate.OWNER),
            "GITHUB_TRIGGERING_ACTOR": "traynor1987", "GITHUB_SHA": self.head,
        }
        self.event = {
            "before": self.parent, "after": self.head, "sender": {"id": gate.OWNER},
            "forced": False, "created": False,
        }
        self.approval = gate.marker_for(self.parent)
        self.git_responses = {
            ("rev-parse", "HEAD"): self.head,
            ("rev-list", "--parents", "-n", "1", self.head): f"{self.head} {self.parent}",
            ("diff", "--name-only", self.parent, self.head): gate.MARKER,
            ("merge-base", self.parent, gate.CANDIDATE): gate.STABLE_MAIN,
            ("diff", "--name-only", gate.STABLE_MAIN, self.parent): "\n".join(gate.CONTROL_CHANGES),
        }

    def verify_authority(self, env, event, approval):
        with patch.object(gate, "git", side_effect=lambda *args: self.git_responses[args]):
            with patch.object(gate, "api", return_value={"object": {"sha": self.head}}):
                return gate.verify_authority(env, event, approval)

    def test_owner_marker_on_main_permits_exact_rehearsal(self):
        self.assertEqual(self.head, self.verify_authority(self.env, self.event, self.approval))

    def test_non_owner_non_main_retry_and_force_push_are_denied(self):
        for key, value in (
            ("GITHUB_ACTOR_ID", "1"), ("GITHUB_TRIGGERING_ACTOR", "another-user"),
            ("GITHUB_REF", f"refs/heads/{gate.BRANCH}"), ("GITHUB_REPOSITORY", "test/fork"),
            ("GITHUB_RUN_ATTEMPT", "2"), ("GITHUB_EVENT_NAME", "workflow_dispatch"),
        ):
            with self.subTest(key=key), self.assertRaisesRegex(ValueError, "authenticated owner main push"):
                self.verify_authority(dict(self.env, **{key: value}), self.event, self.approval)
        for key, value in (("sender", {"id": 1}), ("forced", True), ("created", True)):
            with self.subTest(key=key), self.assertRaisesRegex(ValueError, "owner marker-only commit"):
                self.verify_authority(self.env, dict(self.event, **{key: value}), self.approval)

    def test_marker_rejects_altered_inputs_source_version_or_evidence(self):
        for key, value in (
            ("candidate", "0" * 40), ("candidate_tree", "0" * 40), ("mode", "production"),
            ("version_name", "1.7.53"), ("version_code", 99), ("android_run", 1),
            ("android_artifact", 1), ("android_artifact_sha256", "0" * 64),
            ("inputs_sha256", dict(self.approval["inputs_sha256"], **{gate.WORKFLOW: "0" * 64})),
        ):
            with self.subTest(key=key), self.assertRaisesRegex(ValueError, "exact rehearsal, candidate and controls"):
                self.verify_authority(self.env, self.event, dict(self.approval, **{key: value}))

    def test_control_commit_cannot_introduce_application_files(self):
        key = ("diff", "--name-only", gate.STABLE_MAIN, self.parent)
        self.git_responses[key] += "\napp/build.gradle.kts"
        with self.assertRaisesRegex(ValueError, "outside the reviewed rehearsal control changes"):
            self.verify_authority(self.env, self.event, self.approval)

    def candidate_pr(self):
        return {
            "number": gate.PR, "state": "open", "merged": False,
            "head": {"sha": gate.CANDIDATE, "ref": gate.BRANCH, "repo": {"full_name": gate.REPO}},
            "base": {"ref": "main", "repo": {"full_name": gate.REPO}},
        }

    def test_changed_or_merged_pr_and_changed_build_version_fail(self):
        for mutate in (
            lambda pr: pr["head"].update(sha="0" * 40),
            lambda pr: pr["head"]["repo"].update(full_name="other/fork"),
            lambda pr: pr.update(state="closed", merged=True),
        ):
            pr = self.candidate_pr()
            mutate(pr)
            with patch.object(gate, "api", return_value=pr), self.assertRaisesRegex(ValueError, "exact open"):
                gate.verify_candidate()
        baseline = gate.git("show", f"{gate.BASELINE}:app/build.gradle.kts")
        with patch.object(gate, "api", return_value=self.candidate_pr()):
            with patch.object(gate, "git", side_effect=["0" * 40]), self.assertRaisesRegex(ValueError, "tree differs"):
                gate.verify_candidate()
            with patch.object(gate, "git", side_effect=[gate.CANDIDATE_TREE, baseline, baseline]):
                with self.assertRaisesRegex(ValueError, "reviewed API 37 version bump"):
                    gate.verify_candidate()

    def test_moved_main_stops_before_candidate_and_signer(self):
        with patch.dict(gate.os.environ, {"GITHUB_SHA": self.head}), patch.object(gate, "git", return_value=self.head):
            with patch.object(gate, "api", return_value={"object": {"sha": "3" * 40}}):
                with patch.object(gate, "verify_candidate") as verify_candidate:
                    with self.assertRaisesRegex(ValueError, "moved before signer access"):
                        gate.recheck_authorization()
                    verify_candidate.assert_not_called()

    def test_wrong_or_failed_ci_never_reaches_artifact_verification(self):
        exact = {
            "id": gate.APP_RUN, "head_sha": gate.CANDIDATE, "head_branch": gate.BRANCH,
            "event": "pull_request", "path": gate.APP_WORKFLOW, "head_repository": {"full_name": gate.REPO},
            "status": "completed", "conclusion": "success", "workflow_id": gate.APP_WORKFLOW_ID,
            "run_attempt": 1,
        }
        for key, value in (
            ("id", 1), ("head_sha", "0" * 40), ("workflow_id", 1), ("run_attempt", 2),
            ("conclusion", "failure"), ("status", "in_progress"),
        ):
            with self.subTest(key=key), patch.object(shared, "api", return_value=dict(exact, **{key: value})):
                with patch.object(gate, "artifact") as artifact, self.assertRaises(ValueError):
                    gate.verify_regressions(Path("unused"))
                artifact.assert_not_called()

    def test_missing_ci_report_prevents_authorization_output(self):
        with tempfile.TemporaryDirectory() as scratch:
            root = Path(scratch)
            marker = root / "owner-marker.json"
            event = root / "push-event.json"
            out = root / "output"
            receipt = root / "receipts"
            receipt.mkdir()
            marker.write_text(json.dumps(self.approval))
            event.write_text(json.dumps(self.event))
            current = {
                "head_sha": self.head, "head_branch": "main", "event": "push", "actor": {"id": gate.OWNER},
                "triggering_actor": {"id": gate.OWNER}, "run_attempt": 1, "path": gate.WORKFLOW,
            }

            def incomplete_artifact(*args):
                with zipfile.ZipFile(args[-1], "w") as archive:
                    archive.writestr("intelligence/android17-sdk.json", '{"api_level": 37, "preview": false}')

            with patch.object(gate, "MARKER", str(marker)), patch.dict(gate.os.environ, {
                "GITHUB_EVENT_PATH": str(event), "GITHUB_RUN_ID": "1", "GITHUB_OUTPUT": str(out),
            }), patch.object(gate, "verify_authority", return_value=self.head), patch.object(gate, "api", return_value=current), \
                    patch.object(gate, "verify_candidate"), patch.object(gate, "run_record", return_value={
                        "workflow_id": gate.APP_WORKFLOW_ID, "run_attempt": 1,
                    }), patch.object(gate, "check_steps"), patch.object(gate, "artifact", side_effect=incomplete_artifact):
                with self.assertRaisesRegex(ValueError, "Missing unique intelligence/instrumentation.txt"):
                    gate.authorize(receipt)
            self.assertFalse(out.exists())
            self.assertFalse((receipt / "owner-approval.json").exists())

    def test_missing_or_altered_fixture_fails_before_instrumentation_is_created(self):
        with tempfile.TemporaryDirectory() as scratch:
            root = Path(scratch)
            fixture = root / "fixture.kt"
            with patch.object(gate, "FIXTURE", str(fixture)):
                with self.assertRaises(FileNotFoundError):
                    gate.create_fixture(root)
                fixture.write_text("Unreviewed instrumentation")
                with self.assertRaisesRegex(ValueError, "Archived fixture differs"):
                    gate.create_fixture(root)
            self.assertFalse((root / "app").exists())

    def test_exact_fixture_targets_the_baseline_abi_and_checks_preserved_data(self):
        with tempfile.TemporaryDirectory() as scratch:
            root = Path(scratch)
            gradle = root / "app/build.gradle.kts"
            gradle.parent.mkdir()
            gradle.write_text(gate.git("show", f"{gate.BASELINE}:app/build.gradle.kts"))
            gate.create_fixture(root)
            fixture, = gradle.parent.rglob("UpgradeFixtureTest.kt")
            source = fixture.read_text()
            self.assertIn("baselineCode == 97 && candidateCode == 98", source)
            self.assertIn("assistantState98(space,before,seed)", source)
            self.assertIn('getMethod("getAutoSave")', source)
            self.assertIn('previewFixtureRevision', source)
            self.assertIn("getConstructor", source)
            self.assertNotIn("applicationIdSuffix", gradle.read_text())

    def test_receipt_only_job_places_unprivileged_checks_before_secrets(self):
        workflow = Path(gate.WORKFLOW).read_text()
        new = workflow.split("  build98-rehearsal:\n", 1)[1]
        previous = shared.git("show", f"{gate.STABLE_MAIN}:{gate.WORKFLOW}")
        self.assertEqual(previous.split("jobs:\n", 1)[1].strip(), workflow.split("jobs:\n", 1)[1].split(
            "  build98-rehearsal:\n", 1)[0].strip())
        pre_sign, sign = new.split("Verify original production identity and sign the frozen Build 98 source", 1)
        self.assertIn("validate", pre_sign.lower())
        self.assertIn("assembleDebugAndroidTest", pre_sign)
        self.assertIn("novaos_build98_rehearsal.py recheck", pre_sign)
        self.assertIn('sha256sum -c "$root/instrumentation.sha256"', pre_sign)
        self.assertIn("status --porcelain=v1 --untracked-files=all --ignored=matching", pre_sign)
        self.assertNotIn("secrets.", pre_sign)
        self.assertIn("secrets.PRIVATE_GALLERY_SIGNING_KEYSTORE_BASE64", sign)
        self.assertIn("path: ${{ runner.temp }}/novaos98/receipts", sign)
        self.assertNotIn("contents: write", new)
        self.assertNotIn("publish", new.lower())


if __name__ == "__main__":
    unittest.main()
