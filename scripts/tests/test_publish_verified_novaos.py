import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import publish_verified_novaos as release

CANDIDATE = json.loads((Path(__file__).parents[2] / ".novaos-stable-candidate.json").read_text())
URL = f"https://github.com/{release.REPO}/releases/tag/{CANDIDATE['tag']}"


class ReleaseChecks(unittest.TestCase):
    def test_promoted_source_reuses_only_explicit_exact_commit_regression_evidence(self):
        candidate = dict(CANDIDATE)
        candidate.pop("regression_branch", None)
        branch = "feature/novaos-code81-core-apps"
        def record(run_id, source, workflow, evidence_branch):
            return dict(id=run_id, head_sha=source, head_branch=evidence_branch,
                        event="push", head_repository={"full_name": release.REPO},
                        status="completed", conclusion="success", path=".github/workflows/"+workflow,
                        actor={"id": 16270479}, run_attempt=1)
        regression = record(candidate["regression_run"], candidate["source"], "fast-development.yml", branch)
        production = record(candidate["production_run"], candidate["production_head"], "sanctuary-production.yml", "main")
        def jobs(name, mandatory):
            return [dict(name=name, steps=[dict(name=step, conclusion="success") for step in mandatory])]
        arguments = (regression, jobs("home2-integration", release.REGRESSION_STEPS),
                     production, jobs("sanctuary-production", release.PRODUCTION_STEPS))
        with self.assertRaisesRegex(ValueError, "provenance"):
            release.check_provenance(candidate, *arguments)
        candidate["regression_branch"] = branch
        release.check_provenance(candidate, *arguments)
        regression["head_sha"] = "0" * 40
        with self.assertRaisesRegex(ValueError, "provenance"):
            release.check_provenance(candidate, *arguments)
        candidate["regression_branch"] = "unreviewed/branch"
        with self.assertRaisesRegex(ValueError, "Unreviewed"):
            release.check_provenance(candidate, *arguments)

    def test_tag_must_exist_and_resolve_to_approved_source(self):
        with patch.object(release, "api", side_effect=RuntimeError("HTTP 404")):
            with self.assertRaisesRegex(ValueError, "Missing tag"):
                release.verify_tag(CANDIDATE)
        with patch.object(release, "api", return_value={"object": {"type": "commit", "sha": "bad"}}):
            with self.assertRaisesRegex(ValueError, "frozen release source"):
                release.verify_tag(CANDIDATE)
        with patch.object(release, "api", side_effect=[
            {"object": {"type": "tag", "sha": "annotation"}},
            {"object": {"type": "commit", "sha": CANDIDATE["source"]}},
        ]):
            release.verify_tag(CANDIDATE)

    def test_missing_tag_is_pushed_at_exact_source_and_verified(self):
        with patch.object(release, "verify_tag", side_effect=[False, True]), \
                patch.object(release.subprocess, "run") as push:
            release.ensure_tag(CANDIDATE)
        command = push.call_args.args[0]
        self.assertEqual(f"{CANDIDATE['source']}:refs/tags/{CANDIDATE['tag']}", command[-1])
        self.assertNotIn("--force", command)

    def test_existing_tag_is_never_replaced(self):
        with patch.object(release, "verify_tag", side_effect=ValueError("wrong target")), \
                patch.object(release.subprocess, "run") as push:
            with self.assertRaisesRegex(ValueError, "wrong target"):
                release.ensure_tag(CANDIDATE)
        push.assert_not_called()

    def test_tag_permission_failure_stops_before_publication(self):
        with patch.object(release, "verify_tag", return_value=False), \
                patch.object(release.subprocess, "run") as push:
            push.return_value.returncode = 1
            push.return_value.stdout = ""
            push.return_value.stderr = "remote: HTTP 403"
            with self.assertRaisesRegex(ValueError, "HTTP 403.*GitHub App"):
                release.ensure_tag(CANDIDATE)
        push.assert_called_once()

    def test_missing_and_incorrect_assets_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "missing verified assets"):
            release.check_remote_assets({"assets": []}, {"apk": "sha256:expected"})
        with self.assertRaisesRegex(ValueError, "different assets"):
            release.check_remote_assets({"assets": [{"name": "apk", "digest": "wrong"}]}, {"apk": "expected"})

    def test_http_403_is_not_retried(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(release, "digest", return_value="a"), \
                patch.object(release, "find_release", side_effect=RuntimeError("HTTP 403")) as find, \
                patch.object(release.time, "sleep") as sleep:
            with self.assertRaisesRegex(ValueError, "contents: write"):
                release.publish(CANDIDATE, Path(tmp))
            find.assert_called_once()
            sleep.assert_not_called()

    def test_interrupted_draft_resumes_after_transient_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            for name in release.ASSETS:
                (directory / name).write_text(name)
            path = f"https://github.com/{release.REPO}/releases/download/{CANDIDATE['tag']}/"
            assets = [{"name": name, "digest": "sha256:" + release.digest(directory / name),
                       "browser_download_url": path + name} for name in sorted(release.ASSETS)]
            partial = {"draft": True, "prerelease": False, "assets": assets[:1]}
            full = dict(partial, assets=assets)
            latest = dict(full, draft=False, tag_name=CANDIDATE["tag"], html_url=URL)
            with patch.object(release, "find_release", side_effect=[partial, full, full]), \
                    patch.object(release, "gh", side_effect=[RuntimeError("HTTP 503"), b""]) as gh, \
                    patch.object(release, "api", return_value=latest), patch.object(release, "verify_tag"), \
                    patch.object(release.time, "sleep") as sleep:
                self.assertEqual(URL, release.publish(CANDIDATE, directory))
            sleep.assert_called_once_with(1)
            self.assertEqual(("release", "upload"), gh.call_args_list[0].args[:2])
            self.assertEqual(("release", "edit"), gh.call_args_list[1].args[:2])

    def test_upgrade_receipts_are_bound_to_source_and_apk(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            upgrade = root / "from80"
            upgrade.mkdir()
            approval = {
                "source": CANDIDATE["source"], "version": CANDIDATE["tag"][1:],
                "version_code": CANDIDATE["version_code"], "approval_run": CANDIDATE["production_run"],
                "regression_run": CANDIDATE["regression_run"], "approval_commit": CANDIDATE["production_head"],
            }
            (root / "owner-approval.json").write_text(json.dumps(approval))
            (upgrade / "upgrade-baseline-sha256.txt").write_text(CANDIDATE["baseline_sha256"])
            (upgrade / "upgrade-candidate-sha256.txt").write_text(CANDIDATE["apk_sha256"])
            with patch.object(release, "digest", return_value=CANDIDATE["apk_sha256"]):
                self.assertEqual(upgrade, release.check_local_receipts(CANDIDATE, root, root))
                with self.assertRaisesRegex(ValueError, "another release"):
                    release.check_local_receipts(dict(CANDIDATE, source="0" * 40), root, root)
            with patch.object(release, "digest", return_value="0" * 64):
                with self.assertRaisesRegex(ValueError, "immutable candidate"):
                    release.check_local_receipts(CANDIDATE, root, root)


if __name__ == "__main__":
    unittest.main()
