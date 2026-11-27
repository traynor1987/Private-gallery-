from contextlib import redirect_stderr, redirect_stdout
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import novaos_build99_publication as build99
import publish_verified_novaos as release
from verify_novaos_release_assets import verify_payload


def candidate():
    return {
        "tag": "v2.0.0", "version_code": 99, "baseline_code": 97,
        "baseline_sha256": build99.build98.BASELINE_DIGEST, "regression_branch": build99.BRANCH,
        "source": "1" * 40, "candidate": "2" * 40, "source_tree": "3" * 40, "candidate_tree": "4" * 40,
        "rehearsal_head": "5" * 40, "production_head": "6" * 40, "apk_sha256": "7" * 64,
        "regression_run": 10, "full_run": 11, "rehearsal_run": 12, "production_run": 13,
        "assets_id": 20, "receipts_id": 21, "rehearsal_id": 22,
        "assets_sha256": "8" * 64, "receipts_sha256": "9" * 64, "rehearsal_sha256": "a" * 64,
    }


class Build99Publication(unittest.TestCase):
    def test_rejects_build98_baseline_and_incomplete_evidence(self):
        reviewed = candidate()
        build99.validate_identity(reviewed)
        for changed in (
            {"baseline_code": 98}, {"full_run": reviewed["regression_run"]},
            {"rehearsal_id": False}, {"candidate_tree": "unbound"},
        ):
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                build99.validate_identity(dict(reviewed, **changed))

    def test_build99_owner_marker_must_be_current_and_bind_the_manifest(self):
        head, parent = "1" * 40, "2" * 40
        env = {
            "GITHUB_REPOSITORY": build99.REPO, "GITHUB_REF": "refs/heads/main", "GITHUB_EVENT_NAME": "push",
            "GITHUB_ACTOR_ID": str(build99.build97.OWNER), "GITHUB_TRIGGERING_ACTOR": "traynor1987",
            "GITHUB_SHA": head, "GITHUB_EVENT_PATH": "event.json",
        }
        commands = {
            ("rev-parse", "HEAD"): head,
            ("rev-list", "--parents", "-n", "1", head): f"{head} {parent}",
            ("diff", "--name-only", parent, head): build99.PUB_MARKER,
            ("hash-object", build99.MANIFEST): "blob",
            ("rev-parse", f"{parent}:{build99.MANIFEST}"): "blob",
        }
        event = dict(sender={"id": build99.build97.OWNER}, after=head, before=parent, forced=False, created=False)
        previous = Path.cwd()
        with tempfile.TemporaryDirectory() as tmp:
            try:
                os.chdir(tmp)
                Path(env["GITHUB_EVENT_PATH"]).write_text(json.dumps(event))
                Path(build99.PUB_MARKER).write_text(parent + "\n")
                manifest = Path(build99.MANIFEST)
                manifest.write_text("{}")
                remote = {"object": {"sha": head}}
                with patch.dict(os.environ, env, clear=True), patch.object(build99, "git", side_effect=lambda *args: commands[args]):
                    self.assertEqual(parent, build99.verify_owner_marker(manifest, lambda _: remote))
                    remote["object"]["sha"] = parent
                    with self.assertRaisesRegex(ValueError, "marker-only"):
                        build99.verify_owner_marker(manifest, lambda _: remote)
                    with patch.dict(os.environ, GITHUB_ACTOR_ID="123"):
                        with self.assertRaisesRegex(ValueError, "authenticated owner"):
                            build99.verify_owner_marker(manifest, lambda _: remote)
            finally:
                os.chdir(previous)

    def test_latest_baseline_must_remain_the_authentic_build97(self):
        reviewed = candidate()
        baseline = dict(tag_name="v1.7.51", id=455746929, draft=False, prerelease=False, assets=[
            {"name": name, "digest": digest} for name, digest in build99.BASELINE_ASSETS.items()
        ])
        with patch.object(release, "verify_tag") as tag:
            build99.verify_latest(reviewed, lambda _: baseline, tag, release.check_remote_assets)
            tag.assert_called_once_with({"tag": "v1.7.51", "source": build99.build97.MERGED_SOURCE})
            with self.assertRaisesRegex(ValueError, "Latest stable release changed"):
                build99.verify_latest(reviewed, lambda _: dict(baseline, tag_name="v1.7.52"), tag, release.check_remote_assets)
            with self.assertRaisesRegex(ValueError, "different assets"):
                build99.verify_latest(reviewed, lambda _: dict(baseline, assets=[dict(name=next(iter(build99.BASELINE_ASSETS)), digest="changed")]),
                                      tag, release.check_remote_assets)

    def test_requires_full_ci_original_owner_and_immutable_rehearsal_receipt(self):
        reviewed = candidate()
        plan = (
            (10, "candidate", build99.BRANCH, build99.APP_WORKFLOW, "verify", build99.APP_STEPS),
            (11, "candidate", build99.BRANCH, build99.FULL_WORKFLOW, "Full Android 17 security", build99.FULL_STEPS),
            (12, "rehearsal_head", "main", build99.WORKFLOW, build99.JOB, build99.UPGRADE_STEPS),
            (13, "production_head", "main", build99.WORKFLOW, build99.JOB, build99.PRODUCTION_STEPS),
        )
        responses = {}
        for run_id, sha, branch, workflow, job, mandatory in plan:
            prefix = f"actions/runs/{run_id}"
            responses[prefix] = {
                "id": run_id, "head_sha": reviewed[sha], "head_branch": branch,
                "event": "push", "head_repository": {"full_name": build99.REPO},
                "path": workflow, "status": "completed", "conclusion": "success", "run_attempt": 1,
                "actor": {"id": build99.build97.OWNER}, "triggering_actor": {"id": build99.build97.OWNER},
            }
            responses[f"{prefix}/attempts/1/jobs?per_page=100"] = {
                "total_count": 1, "jobs": [{"name": job, "conclusion": "success", "steps": [
                    {"name": step, "conclusion": "success"} for step in mandatory
                ]}],
            }
        artifact = {
            "id": 22, "name": "novaos-build99-rehearsal-receipts", "expired": False,
            "digest": "sha256:" + reviewed["rehearsal_sha256"],
            "workflow_run": {"id": 12, "head_sha": reviewed["rehearsal_head"]},
        }
        responses["actions/artifacts/22"] = artifact
        build99.verify_workflows(reviewed, responses.__getitem__)
        full_step = responses["actions/runs/11/attempts/1/jobs?per_page=100"]["jobs"][0]["steps"][0]
        full_step["conclusion"] = "skipped"
        with self.assertRaisesRegex(ValueError, "full_run lacks successful steps"):
            build99.verify_workflows(reviewed, responses.__getitem__)
        full_step["conclusion"] = "success"
        production = responses["actions/runs/13"]
        production["triggering_actor"] = {"id": 12345}
        with self.assertRaisesRegex(ValueError, "original owner trigger"):
            build99.verify_workflows(reviewed, responses.__getitem__)
        production["triggering_actor"] = production["actor"]
        artifact["workflow_run"]["id"] = 13
        with self.assertRaisesRegex(ValueError, "pre-merge rehearsal receipts"):
            build99.verify_workflows(reviewed, responses.__getitem__)

    def test_exact_merge_only_allows_the_owner_rehearsal_marker(self):
        reviewed = candidate()
        source = reviewed["source"]
        feature = reviewed["candidate"]
        production = reviewed["production_head"]
        manifest = "f" * 40
        commands = {
            ("rev-parse", f"{source}^{{tree}}"): reviewed["source_tree"],
            ("rev-parse", f"{feature}^{{tree}}"): reviewed["candidate_tree"],
            ("rev-list", "--parents", "-n", "1", source): f"{source} {reviewed['rehearsal_head']} {feature}",
            ("diff", "--name-status", "--no-renames", feature, source): "A\t" + build99.REHEARSAL_MARKER,
            ("rev-list", "--parents", "-n", "1", production): f"{production} {source}",
            ("diff", "--name-only", source, production): build99.PRODUCTION_MARKER,
            ("rev-list", "--parents", "-n", "1", manifest): f"{manifest} {production}",
            ("diff", "--name-only", production, manifest): build99.MANIFEST,
        }
        pr = {
            "number": 87, "state": "closed", "merged": True, "merge_commit_sha": source,
            "base": {"ref": "main", "repo": {"full_name": build99.REPO}},
            "head": {"ref": build99.BRANCH, "sha": feature, "repo": {"full_name": build99.REPO}},
        }
        with patch.object(build99, "git", side_effect=lambda *args: commands[args]):
            build99.verify_source(reviewed, manifest, lambda _: pr)
            drift = ("diff", "--name-status", "--no-renames", feature, source)
            commands[drift] += "\nM\tapp/build.gradle.kts"
            with self.assertRaisesRegex(ValueError, "rehearsed Wave A merge"):
                build99.verify_source(reviewed, manifest, lambda _: pr)

    def test_requires_exact_original_key_populated_upgrade_and_signed_digest(self):
        reviewed = candidate()
        approval = {key: reviewed[key] for key in (
            "source", "source_tree", "candidate", "candidate_tree", "regression_run", "full_run", "rehearsal_run",
        )}
        approval.update(mode="production", version="2.0.0", version_code=99, approving_id=build99.build97.OWNER,
                        approval_run=reviewed["production_run"], approval_commit=reviewed["production_head"],
                        rehearsal={
                            "run": reviewed["rehearsal_run"], "attempt": 1, "artifact": reviewed["rehearsal_id"],
                            "sha256": reviewed["rehearsal_sha256"], "approval_commit": reviewed["rehearsal_head"],
                        })
        upgrade = dict(
            result="PASS", source=reviewed["source"], baseline_code=97, candidate_code=99, installed_code=99,
            certificate_sha256=build99.build97.CERTIFICATE, baseline_sha256=reviewed["baseline_sha256"],
            apk_sha256=reviewed["apk_sha256"], seed="PASS", verify="PASS", after_restart="PASS",
            transport="controlled-loopback", baseline_application="authentic-unmodified", discovery="PASS",
            download_and_verification="PASS", android_installer="confirmed",
            default_home_retention="PASS", anti_downgrade="PASS",
        )
        build99.require_receipts(reviewed, approval, upgrade)
        with self.assertRaisesRegex(ValueError, "owner approval"):
            build99.require_receipts(reviewed, dict(approval, full_run=1000), upgrade)
        for altered in ({"apk_sha256": "b" * 64}, {"after_restart": "FAIL"}, {"baseline_code": 98}, {"anti_downgrade": "FAIL"}):
            with self.subTest(altered=altered), self.assertRaisesRegex(ValueError, "original-key"):
                build99.require_receipts(reviewed, approval, dict(upgrade, **altered))

    def test_default_home_and_rollback_receipts_must_each_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for phase in ("default-before", "default-upgrade", "default-restart"):
                (root / f"upgrade-{phase}.txt").write_text("OK (1 test)\n")
            (root / "no-downgrade.txt").write_text("INSTALL_FAILED_VERSION_DOWNGRADE; installed Build 99\n")
            build99.verify_default_and_downgrade_receipts(root)
            (root / "no-downgrade.txt").write_text("installed Build 97")
            with self.assertRaisesRegex(ValueError, "downgrade protection"):
                build99.verify_default_and_downgrade_receipts(root)
            (root / "upgrade-default-restart.txt").write_text("0 tests")
            with self.assertRaisesRegex(ValueError, "default-restart"):
                build99.verify_default_and_downgrade_receipts(root)

    def test_preflight_is_read_only_and_failed_payload_does_not_tag_or_publish(self):
        reviewed = candidate()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest = root / "candidate.json"
            manifest.write_text(json.dumps(reviewed))
            prepare = ["publish", "--candidate", str(manifest)]
            artifact = root / "verified"
            with patch.object(build99, "verify_owner_marker"), patch.object(build99, "verify_workflows"), \
                 patch.object(build99, "verify_source"), patch.object(build99, "verify_latest"), \
                 patch.object(release, "verify_tag"), patch.object(release, "ensure_tag") as ensure, \
                 patch.object(release, "publish") as publish, patch.object(release, "archive", return_value=artifact), \
                 patch.object(release, "check_local_receipts", return_value=artifact):
                with patch("sys.argv", prepare + ["--preflight"]), redirect_stdout(io.StringIO()):
                    release.main()
                ensure.assert_not_called()
                publish.assert_not_called()
                args = prepare + ["--tools", str(root), "--work", str(root / "publication")]
                with patch("sys.argv", args), patch.object(release, "verify_payload", side_effect=ValueError("bad APK")):
                    with self.assertRaises(SystemExit), redirect_stderr(io.StringIO()):
                        release.main()
                ensure.assert_not_called()
                publish.assert_not_called()

    def test_payload_version_must_be_exact_before_inspecting_signature(self):
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "private-gallery-release.metadata.json").write_text(json.dumps({"versionCode": 98}))
            args = SimpleNamespace(tag="v2.0.0", source="a" * 40, baseline_code=97, version_code=99, assets=root)
            with patch("verify_novaos_release_assets.inspect") as inspect:
                with self.assertRaisesRegex(ValueError, "versionCode differs"):
                    verify_payload(args)
            inspect.assert_not_called()


if __name__ == "__main__":
    unittest.main()
