import json
import unittest
from unittest.mock import patch

import novaos_build97_gate as gate


class Build97PostMergeVerification(unittest.TestCase):
    def setUp(self):
        self.app = {
            "id": gate.APP_RUN, "head_sha": gate.CANDIDATE, "head_branch": gate.FEATURE,
            "event": "pull_request", "path": ".github/workflows/nova-home-intelligence.yml",
            "head_repository": {"full_name": gate.REPO}, "status": "completed",
            "conclusion": "success", "run_attempt": 1, "pull_requests": [],
        }

    def test_exact_successful_ci_stays_valid_after_actions_clears_its_pr_list(self):
        jobs = {"total_count": 1, "jobs": [{
            "name": "verify", "conclusion": "success",
            "steps": [{"name": step, "conclusion": "success"} for step in gate.APP_STEPS],
        }]}
        with patch.object(gate, "api", side_effect=[self.app, jobs]):
            gate.verify_app_run("production")
        with patch.object(gate, "api", return_value=self.app):
            with self.assertRaisesRegex(ValueError, "not associated"):
                gate.verify_app_run("rehearsal")

    def test_post_merge_rejects_any_changed_or_failed_app_run(self):
        for key, value in (
            ("id", gate.APP_RUN + 1), ("head_sha", "0" * 40), ("conclusion", "failure"),
            ("event", "push"), ("path", "different.yml"), ("run_attempt", 2),
        ):
            with self.subTest(key=key), patch.object(gate, "api", return_value=dict(self.app, **{key: value})):
                with self.assertRaises(ValueError):
                    gate.verify_app_run("production")

    def test_commit_to_pr_lookup_requires_the_audited_merge_and_tree(self):
        pr = {
            "number": gate.PR, "state": "closed", "merged": True, "merged_at": "2026-11-23T16:29:04Z",
            "merge_commit_sha": gate.MERGED_SOURCE, "head": {"sha": gate.CANDIDATE}, "base": {"ref": "main"},
        }
        with patch.object(gate, "api", return_value=[pr]) as lookup:
            gate.verify_merged_source(pr, gate.MERGED_SOURCE)
            lookup.assert_called_once_with(f"commits/{gate.CANDIDATE}/pulls")
        with patch.object(gate, "api", return_value=[dict(pr, head={"sha": "0" * 40})]):
            with self.assertRaisesRegex(ValueError, "exact tested commit"):
                gate.verify_merged_source(pr, gate.MERGED_SOURCE)
        with patch.object(gate, "git", return_value="0" * 40):
            with self.assertRaisesRegex(ValueError, "exact audited merge"):
                gate.verify_release_tree(gate.MERGED_SOURCE)

    def test_rehearsal_reuse_allows_only_the_authorized_lookup_change(self):
        approved = json.loads(gate.git("show", f"{gate.REHEARSAL_HEAD}:{gate.MARKER}"))["inputs_sha256"]
        repaired = dict(approved)
        repaired[gate.GATE] = "1" * 64
        gate.verify_rehearsal_controls(approved, repaired)
        repaired[gate.WORKFLOW] = "2" * 64
        with self.assertRaisesRegex(ValueError, "Controls outside the authorized"):
            gate.verify_rehearsal_controls(approved, repaired)
        with self.assertRaisesRegex(ValueError, "original owner authorization"):
            gate.verify_rehearsal_controls(dict(approved, **{gate.GATE: "3" * 64}), approved)


if __name__ == "__main__":
    unittest.main()
