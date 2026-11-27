"""Exact-source admission for the Wave A release in the existing stable publisher."""

import json
import os
from pathlib import Path
import re

import novaos_build97_gate as build97
import novaos_build98_rehearsal as build98

REPO = build97.REPO
BRANCH = "feature/novaos2-wave-a"
TAG = "v2.0.0"
VERSION_CODE = 99
BASELINE_CODE = 97
PR = 87
WORKFLOW = ".github/workflows/novaos-build99-protected.yml"
JOB = "Build 99 protected"
ASSETS = "novaos-build99-verified-stable-assets"
RECEIPTS = "novaos-build99-production-receipts"
APP_WORKFLOW = ".github/workflows/novaos2-api37.yml"
FULL_WORKFLOW = ".github/workflows/novaos99-full.yml"
MANIFEST = ".novaos-stable-candidate.json"
PUB_MARKER = ".novaos-publish-authorization"
REHEARSAL_MARKER = ".novaos-build99-rehearsal-authorization"
PRODUCTION_MARKER = ".novaos-build99-production-authorization"
BASELINE_ASSETS = {
    "private-gallery-release.apk": "sha256:" + build98.BASELINE_DIGEST,
    "private-gallery-release.apk.sha256": "sha256:fb1f7d4621fd24c466eb2a886c3b8ccf9f1d812ec20bd6add9acd9e184f2526f",
    "private-gallery-release.metadata.json": "sha256:1831c576738136760c1780b2f3381d586681836f7675e495a59d9ded09e00c6a",
}
APP_STEPS = {
    "Set up Android 17 and record source",
    "Check Sanctuary and protected storage boundaries",
    "Compile the debug app and run JVM tests",
    "Run API 37 feature regressions",
    "Run actions/upload-artifact@v4",
}
FULL_STEPS = {
    "Validate frozen Build 99 source",
    "Run host security gates",
    "Build and check exact-source debug artifacts",
    "Start disposable security fixtures",
    "Run complete Android 17 security regression",
    "Verify complete Build 99 evidence",
    "Remove disposable security fixtures",
    "Upload Build 99 full security evidence",
}
UPGRADE_STEPS = {
    "Validate Build 99 owner authorization and exact evidence before signer access",
    "Recheck owner authorization and unsigned fixture before signer access",
    "Verify original production identity and sign the frozen Build 99 source",
    "Verify the signed Build 99 APK and protected packaging",
    "Rehearse populated Build 97 to 99 upgrade and restart through the in-app updater",
    "Retain only non-sensitive Build 99 verification receipts",
    "Remove all Build 99 key and APK material",
}
PRODUCTION_STEPS = UPGRADE_STEPS | {
    "Stage verified Build 99 stable assets",
    "Retain verified Build 99 production publication inputs",
}

require = build97.require
git = build97.git


def selected(candidate):
    return candidate.get("tag") == TAG or candidate.get("version_code") == VERSION_CODE


def validate_identity(candidate):
    require((candidate.get("tag"), candidate.get("version_code"), candidate.get("baseline_code"),
             candidate.get("baseline_sha256"), candidate.get("regression_branch")) ==
            (TAG, VERSION_CODE, BASELINE_CODE, build98.BASELINE_DIGEST, BRANCH),
            "Wrong Wave A Build 99 or authentic stable Build 97 identity")
    runs = ("regression_run", "full_run", "rehearsal_run", "production_run")
    for key in (*runs, "assets_id", "receipts_id", "rehearsal_id"):
        require(type(candidate.get(key)) is int and candidate[key] > 0, f"Invalid Build 99 {key}")
    require(len({candidate[key] for key in runs}) == len(runs), "Build 99 evidence runs are not distinct")
    for key in ("source", "candidate", "source_tree", "candidate_tree", "rehearsal_head", "production_head"):
        require(re.fullmatch(r"[a-f0-9]{40}", candidate.get(key, "")), f"Invalid Build 99 {key}")
    for key in ("apk_sha256", "assets_sha256", "receipts_sha256", "rehearsal_sha256"):
        require(re.fullmatch(r"[a-f0-9]{64}", candidate.get(key, "")), f"Invalid Build 99 {key}")


def verify_owner_marker(candidate_path, api):
    env = os.environ
    require(env.get("GITHUB_REPOSITORY") == REPO and env.get("GITHUB_REF") == "refs/heads/main"
            and env.get("GITHUB_EVENT_NAME") == "push" and env.get("GITHUB_ACTOR_ID") == str(build97.OWNER)
            and env.get("GITHUB_TRIGGERING_ACTOR") == "traynor1987",
            "Build 99 publication requires an authenticated owner push on main")
    event = json.loads(Path(env["GITHUB_EVENT_PATH"]).read_text())
    head, parent = env["GITHUB_SHA"], event["before"]
    require(event["sender"]["id"] == build97.OWNER and event["forced"] is False and event["created"] is False
            and event["after"] == head and git("rev-parse", "HEAD") == head
            and api("git/ref/heads/main")["object"]["sha"] == head
            and git("rev-list", "--parents", "-n", "1", head).split() == [head, parent]
            and git("diff", "--name-only", parent, head) == PUB_MARKER
            and Path(PUB_MARKER).read_text().strip() == parent and candidate_path == Path(MANIFEST)
            and not candidate_path.is_symlink()
            and git("hash-object", str(candidate_path)) == git("rev-parse", f"{parent}:{MANIFEST}"),
            "Build 99 publication must bind the unchanged manifest through an owner marker-only commit")
    return parent


def verify_source(candidate, manifest_commit, api):
    source = candidate["source"]
    feature = candidate["candidate"]
    rehearsal = candidate["rehearsal_head"]
    production = candidate["production_head"]
    require(git("rev-parse", f"{source}^{{tree}}") == candidate["source_tree"]
            and git("rev-parse", f"{feature}^{{tree}}") == candidate["candidate_tree"]
            and git("rev-list", "--parents", "-n", "1", source).split() == [source, rehearsal, feature]
            and git("diff", "--name-status", "--no-renames", feature, source) == "A\t" + REHEARSAL_MARKER,
            "Build 99 main source is not the rehearsed Wave A merge")
    require(git("rev-list", "--parents", "-n", "1", production).split() == [production, source]
            and git("diff", "--name-only", source, production) == PRODUCTION_MARKER,
            "Build 99 signing did not start from the exact merged source")
    require(git("rev-list", "--parents", "-n", "1", manifest_commit).split() == [manifest_commit, production]
            and git("diff", "--name-only", production, manifest_commit) == MANIFEST,
            "Unapproved tracked changes followed Build 99 production signing")
    pr = api(f"pulls/{PR}")
    require(pr["number"] == PR and pr["state"] == "closed" and pr["merged"] and pr["merge_commit_sha"] == source
            and pr["base"]["ref"] == "main" and pr["base"]["repo"]["full_name"] == REPO
            and pr["head"]["sha"] == feature and pr["head"]["ref"] == BRANCH
            and pr["head"]["repo"]["full_name"] == REPO,
            "GitHub does not identify PR #87 as the verified Build 99 merge")


def verify_workflows(candidate, api):
    records = (
        ("regression_run", "candidate", BRANCH, APP_WORKFLOW, "verify", APP_STEPS),
        ("full_run", "candidate", BRANCH, FULL_WORKFLOW, "Full Android 17 security", FULL_STEPS),
        ("rehearsal_run", "rehearsal_head", "main", WORKFLOW, JOB, UPGRADE_STEPS),
        ("production_run", "production_head", "main", WORKFLOW, JOB, PRODUCTION_STEPS),
    )
    for key, sha_field, branch, workflow, job_name, mandatory in records:
        run_id = candidate[key]
        run = api(f"actions/runs/{run_id}")
        require(run["id"] == run_id and run["head_sha"] == candidate[sha_field] and run["head_branch"] == branch
                and run["event"] == "push" and run["head_repository"]["full_name"] == REPO
                and run["path"].split("@")[0] == workflow
                and run["status"] == "completed" and run["conclusion"] == "success",
                f"Build 99 {key} lacks successful exact-source evidence")
        attempt = run["run_attempt"]
        if branch == "main":
            require(attempt == 1 and run["actor"]["id"] == build97.OWNER
                    and run["triggering_actor"]["id"] == build97.OWNER,
                    f"Build 99 {key} lacked its original owner trigger")
        page = api(f"actions/runs/{run_id}/attempts/{attempt}/jobs?per_page=100")
        jobs = [job for job in page["jobs"] if job["name"] == job_name]
        require(page["total_count"] == len(page["jobs"]) and len(jobs) == 1
                and jobs[0]["conclusion"] == "success",
                f"Build 99 {key} lacks the complete successful {job_name} job")
        passed = {step["name"] for step in jobs[0]["steps"] if step["conclusion"] == "success"}
        require(mandatory <= passed, f"Build 99 {key} lacks successful steps: {sorted(mandatory - passed)}")
    artifact = api(f"actions/artifacts/{candidate['rehearsal_id']}")
    require(artifact["id"] == candidate["rehearsal_id"] and not artifact["expired"]
            and artifact["name"] == "novaos-build99-rehearsal-receipts"
            and artifact["digest"] == "sha256:" + candidate["rehearsal_sha256"]
            and artifact["workflow_run"]["id"] == candidate["rehearsal_run"]
            and artifact["workflow_run"]["head_sha"] == candidate["rehearsal_head"],
            "Build 99 pre-merge rehearsal receipts are missing or have changed")


def require_receipts(candidate, approval, upgrade):
    owner_fields = {
        "mode": "production", "version": TAG[1:], "version_code": VERSION_CODE,
        "approval_commit": candidate["production_head"], "approval_run": candidate["production_run"],
        "approving_id": build97.OWNER,
        **{key: candidate[key] for key in (
            "source", "source_tree", "candidate", "candidate_tree", "regression_run", "full_run", "rehearsal_run",
        )},
    }
    require(all(approval.get(key) == value for key, value in owner_fields.items())
            and approval.get("rehearsal") == {
                "run": candidate["rehearsal_run"], "attempt": 1, "artifact": candidate["rehearsal_id"],
                "sha256": candidate["rehearsal_sha256"], "approval_commit": candidate["rehearsal_head"],
            }, "Build 99 owner approval does not bind the exact rehearsed release and evidence")
    from novaos_build99_rehearsal import require_upgrade
    require_upgrade(upgrade, candidate["source"])
    require(upgrade["apk_sha256"] == candidate["apk_sha256"],
            "Build 99 published APK is not the one that passed the populated original-key rehearsal")


def verify_default_and_downgrade_receipts(receipts):
    for phase in ("default-before", "default-upgrade", "default-restart"):
        require((receipts / f"upgrade-{phase}.txt").read_text().strip() == "OK (1 test)",
                f"Missing successful default Home upgrade phase {phase}")
    require((receipts / "no-downgrade.txt").read_text().strip() ==
            "INSTALL_FAILED_VERSION_DOWNGRADE; installed Build 99",
            "Missing Android production downgrade protection receipt")


def verify_latest(candidate, api, verify_tag, check_assets):
    latest = api("releases/latest")
    require(not latest["draft"] and not latest["prerelease"],
            "Build 99 baseline must be the authentic latest stable release")
    if latest["tag_name"] == TAG:
        verify_tag(candidate)
        apk = [asset for asset in latest["assets"] if asset["name"] == "private-gallery-release.apk"]
        require(len(apk) == 1 and apk[0]["digest"] == "sha256:" + candidate["apk_sha256"],
                "A different Build 99 APK is already public")
        return
    require(latest["tag_name"] == "v1.7.51" and latest["id"] == 455746929,
            "Latest stable release changed since the Build 97 upgrade rehearsal")
    check_assets(latest, BASELINE_ASSETS)
    verify_tag({"tag": "v1.7.51", "source": build97.MERGED_SOURCE})
