import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import publish_verified_novaos as release

CANDIDATE = json.loads((Path(__file__).parents[2] / ".novaos-stable-candidate.json").read_text())
CANDIDATE.pop("release_id", None)  # Publication scenario tests select their own draft state.
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

    def test_build85_three_part_tag_is_valid_and_bound_to_exact_main_source(self):
        candidate=dict(CANDIDATE,tag="v1.7.39",version_code=85)
        with patch.object(release,"api",return_value={"object":{"type":"commit","sha":candidate["source"]}}):
            self.assertTrue(release.verify_tag(candidate))
        for invalid in ("v1.7","v1.7.39-test","v1.7.39/other"):
            with patch.object(release,"api") as network:
                with self.assertRaisesRegex(ValueError,"Invalid stable release tag"):
                    release.verify_tag(dict(candidate,tag=invalid))
                network.assert_not_called()

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

    def test_pinned_existing_draft_is_reused_by_id_without_listing_or_duplicate_creation(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name in release.ASSETS:
                (root/name).write_text(name)
            prefix = f"https://github.com/{release.REPO}/releases/download/{CANDIDATE['tag']}/"
            assets = [dict(name=name, digest="sha256:"+release.digest(root/name), browser_download_url=prefix+name) for name in release.ASSETS]
            draft = dict(id=123, tag_name=CANDIDATE["tag"], draft=True, prerelease=False, assets=[])
            full = dict(draft, assets=assets)
            latest = dict(full, draft=False, html_url=URL)
            with patch.object(release, "api", side_effect=[draft, full, latest]) as api, \
                 patch.object(release, "find_release") as listing, patch.object(release, "gh", return_value=b"") as gh, \
                 patch.object(release, "verify_tag"):
                self.assertEqual(URL, release.publish(dict(CANDIDATE, release_id=123),root))
                listing.assert_not_called()
                self.assertEqual(["releases/123","releases/123","releases/latest"], [c.args[0] for c in api.call_args_list])
                self.assertFalse(any(c.args[:2] == ("release","create") for c in gh.call_args_list))
        with patch.object(release, "api", return_value=dict(id=123, tag_name="wrong")), patch.object(release, "digest", return_value="unchanged"):
            with self.assertRaisesRegex(ValueError,"another candidate"):
                release.publish(dict(CANDIDATE,release_id=123),Path("unused"))

    def test_new_draft_readback_waits_without_creating_a_second_draft(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name in release.ASSETS:
                (root / name).write_text(name)
            prefix = f"https://github.com/{release.REPO}/releases/download/{CANDIDATE['tag']}/"
            assets = [dict(name=name, digest="sha256:"+release.digest(root/name),
                           browser_download_url=prefix+name) for name in release.ASSETS]
            draft = dict(draft=True, prerelease=False, assets=[])
            full = dict(draft, assets=assets)
            latest = dict(full, draft=False, tag_name=CANDIDATE["tag"], html_url=URL)
            with patch.object(release, "find_release", side_effect=[None, None, draft, full]), \
                 patch.object(release, "gh", return_value=b"") as gh, \
                 patch.object(release, "api", return_value=latest), \
                 patch.object(release, "verify_tag"), patch.object(release.time, "sleep") as sleep:
                self.assertEqual(URL, release.publish(CANDIDATE, root))
            self.assertEqual(1, sum(call.args[:2] == ("release", "create") for call in gh.call_args_list))
            sleep.assert_called_once_with(1)

    def test_asset_readback_requires_complete_digests_and_rejects_mismatch_immediately(self):
        partial = dict(prerelease=False, draft=True, assets=[])
        complete = dict(partial, assets=[dict(name="apk", digest="sha256:expected")])
        with patch.object(release, "find_release", side_effect=[partial, complete]), patch.object(release.time, "sleep") as sleep:
            self.assertEqual(complete, release.confirmed_release(CANDIDATE["tag"], {"apk": "sha256:expected"}))
            sleep.assert_called_once_with(1)
        wrong = dict(partial, assets=[dict(name="apk", digest="wrong")])
        with patch.object(release, "find_release", return_value=wrong), patch.object(release.time, "sleep") as sleep:
            with self.assertRaisesRegex(ValueError, "different assets"):
                release.confirmed_release(CANDIDATE["tag"], {"apk": "sha256:expected"})
            sleep.assert_not_called()

    def test_asset_confirmation_uses_exact_draft_id_and_rejects_another_tag(self):
        draft = dict(id=123, tag_name=CANDIDATE["tag"], draft=True, prerelease=False, assets=[])
        with patch.object(release, "api", return_value=draft) as api, patch.object(release, "find_release") as listing:
            self.assertEqual(draft, release.confirmed_release(CANDIDATE["tag"], {}, release_id=123))
            api.assert_called_once_with("releases/123")
            listing.assert_not_called()
        with patch.object(release, "api", return_value=dict(draft, tag_name="v0.0.0.0")):
            with self.assertRaisesRegex(ValueError, "another candidate"):
                release.confirmed_release(CANDIDATE["tag"], {}, release_id=123)

    def test_latest_readback_waits_for_visibility_and_fails_closed_when_missing(self):
        latest = dict(tag_name=CANDIDATE["tag"], draft=False, prerelease=False, assets=[])
        with patch.object(release, "api", side_effect=[RuntimeError("HTTP 404"), latest]), patch.object(release.time, "sleep") as sleep:
            self.assertEqual(latest, release.confirmed_release(CANDIDATE["tag"], {}, stable=True))
            sleep.assert_called_once_with(1)
        with patch.object(release, "find_release", return_value=None) as find, patch.object(release.time, "sleep"):
            with self.assertRaisesRegex(ValueError, "bounded readback"):
                release.confirmed_release(CANDIDATE["tag"])
            self.assertEqual(4, find.call_count)

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
            rehearsal=dict(result="PASS",source=CANDIDATE["source"],baseline_code=CANDIDATE["baseline_code"],candidate_code=CANDIDATE["version_code"],installed_code=CANDIDATE["version_code"],apk_sha256=CANDIDATE["apk_sha256"],discovery="PASS",download_and_verification="PASS",android_installer="confirmed")
            (upgrade/"in-app-update.json").write_text(json.dumps(rehearsal))
            with patch.object(release, "digest", return_value=CANDIDATE["apk_sha256"]):
                self.assertEqual(upgrade, release.check_local_receipts(CANDIDATE, root, root))
                with self.assertRaisesRegex(ValueError, "another release"):
                    release.check_local_receipts(dict(CANDIDATE, source="0" * 40), root, root)
            for field in ("baseline_code", "candidate_code", "installed_code"):
                (upgrade/"in-app-update.json").write_text(json.dumps(dict(rehearsal, **{field: rehearsal[field]-1})))
                with patch.object(release,"digest",return_value=CANDIDATE["apk_sha256"]):
                    with self.assertRaisesRegex(ValueError,"in-app Android upgrade"):
                        release.check_local_receipts(CANDIDATE,root,root)
            (upgrade/"in-app-update.json").write_text(json.dumps(dict(rehearsal,apk_sha256="0"*64)))
            with patch.object(release,"digest",return_value=CANDIDATE["apk_sha256"]):
                with self.assertRaisesRegex(ValueError,"in-app Android upgrade"):
                    release.check_local_receipts(CANDIDATE,root,root)
            with patch.object(release, "digest", return_value="0" * 64):
                with self.assertRaisesRegex(ValueError, "immutable candidate"):
                    release.check_local_receipts(CANDIDATE, root, root)


if __name__ == "__main__":
    unittest.main()
