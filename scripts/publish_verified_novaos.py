#!/usr/bin/env python3
"""Publish an already signed and upgrade-verified candidate without rebuilding Android."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
from types import SimpleNamespace
import zipfile

import novaos_build97_gate as build97
import novaos_build99_publication as build99
from verify_novaos_release_assets import verify, verify_payload

REPO = "traynor1987/Private-gallery-"
BRANCH = "main"
ASSETS = {"private-gallery-release.apk", "private-gallery-release.apk.sha256",
          "private-gallery-release.metadata.json"}
PRODUCTION_STEPS = {
    "Validate original runtime reports and authenticated owner authorization before signer access",
    "Verify published Build 95 baseline before signer access",
    "Verify the existing permanent private key and original certificate",
    "Verify frozen candidate package signature OCR distribution and 16KiB alignment",
    "Original-key Build 95 to versionCode 96 upgrade and restart without uninstall or clear",
    "Preserve verified upgrade receipts only", "Stage and verify stable update assets",
    "Retain verified publication inputs",
}
REGRESSION_STEPS = {
    "Build isolated test identity and run JVM checks",
    "Verify reused Home policies and encrypted adapter",
    "Verify browser engine preservation and isolated APK",
    "Focused Android 17 integration and privacy tests",
    "Validate every selected original Android report",
    "Validate actual Backup process restart evidence",
}


def require(ok, message):
    if not ok:
        raise ValueError(message)


def check_job(jobs, name, mandatory):
    selected = [j for j in jobs if j["name"] == name]
    require(len(selected) == 1, f"Expected one {name} job")
    job, = selected
    passed = {step["name"] for step in job["steps"] if step["conclusion"] == "success"}
    require(mandatory <= passed, f"{name} lacks successful {sorted(mandatory - passed)}")
    return job


def check_provenance(candidate, regression, regression_jobs, production, production_jobs):
    if candidate["version_code"] == 97 or candidate["tag"] == "v1.7.51":
        check_build97_provenance(candidate, regression, regression_jobs, production, production_jobs)
        return
    # Promotion preserves the exact already-tested commit. Reuse its explicitly
    # reviewed preparation-branch run; the protected signer still runs on main
    # and binds that immutable run ID to the original upgrade/owner receipt.
    regression_branch = candidate.get("regression_branch", BRANCH)
    require(regression_branch in (BRANCH, "feature/novaos-code81-core-apps", "feature/novaos-code83-cloud-teacher", "feature/novaos-guardian-retention89", "feature/novaos-guardian-evidence90", "feature/novaos-guardian-reliability91", "feature/novaos-guardian-presentation93", "feature/novaos-guardian-fold-acceptance"),
            "Unreviewed regression evidence branch")
    for record, run_id, source, workflow, branch in (
        (regression, candidate["regression_run"], candidate["source"], "fast-development.yml", regression_branch),
        (production, candidate["production_run"], candidate["production_head"], "sanctuary-production.yml", BRANCH),
    ):
        require(record["id"] == run_id and record["head_sha"] == source and
                record["head_branch"] == branch and record["event"] == "push" and
                record["head_repository"]["full_name"] == REPO and record["status"] == "completed" and
                record["path"].split("@")[0] == f".github/workflows/{workflow}",
                f"Run {run_id} provenance does not match the pinned candidate")
    require(regression["conclusion"] == "success", "Android 17 regression did not pass")
    check_job(regression_jobs, "home2-integration", REGRESSION_STEPS)
    require(production["actor"]["id"] == 16270479 and production["run_attempt"] == 1,
            "Production run lacks the original owner trigger")
    job = check_job(production_jobs, "sanctuary-production", PRODUCTION_STEPS)
    failed = {step["name"] for step in job["steps"] if step["conclusion"] == "failure"}
    require(failed <= {"Publish verified APK to the stable GitHub feed"},
            "Production workflow failed outside publication")


def check_build97_provenance(candidate, regression, regression_jobs, production, production_jobs):
    require(all(candidate.get(key) == value for key, value in {
        "tag": "v1.7.51", "version_code": 97, "baseline_code": 96,
        "baseline_sha256": build97.BASELINE_DIGEST, "regression_run": build97.APP_RUN,
        "regression_branch": build97.FEATURE, "source": build97.MERGED_SOURCE,
    }.items()), "Wrong Build 97 publication identity")
    for record, run_id, source, branch, workflow, event in (
        (regression, build97.APP_RUN, build97.CANDIDATE, build97.FEATURE,
         ".github/workflows/nova-home-intelligence.yml", "pull_request"),
        (production, candidate["production_run"], candidate["production_head"], BRANCH,
         build97.WORKFLOW, "push"),
    ):
        require(record["id"] == run_id and record["head_sha"] == source and record["head_branch"] == branch
                and record["path"].split("@")[0] == workflow and record["event"] == event
                and record["head_repository"]["full_name"] == REPO and record["run_attempt"] == 1
                and record["status"] == "completed" and record["conclusion"] == "success",
                f"Build 97 run {run_id} lacks exact, successful source provenance")
    require(production["actor"]["id"] == build97.OWNER and production["triggering_actor"]["id"] == build97.OWNER,
            "Build 97 lacks original owner production authorization")
    regression_job = check_job(regression_jobs, "verify", build97.APP_STEPS)
    production_job = check_job(production_jobs, "build97-protected", build97.SIGNING_STEPS | {
        "Stage verified Build 97 stable assets", "Retain verified Build 97 production publication inputs",
        "Remove all Build 97 key and APK material",
    })
    require(regression_job["conclusion"] == production_job["conclusion"] == "success",
            "Build 97 Android or protected production job did not pass")


def digest(path):
    with path.open("rb") as file:
        return hashlib.file_digest(file, "sha256").hexdigest()


def check_local_receipts(candidate, assets, receipts):
    def read(path):
        return path.read_text().strip()

    approval = json.loads(read(receipts / "owner-approval.json"))
    require(all(approval[key] == value for key, value in {
        "source": candidate["source"], "version": candidate["tag"][1:],
        "version_code": candidate["version_code"], "approval_run": candidate["production_run"],
        "regression_run": candidate["regression_run"], "approval_commit": candidate["production_head"],
    }.items()), "Production receipt belongs to another release")
    require(digest(assets / "private-gallery-release.apk") == candidate["apk_sha256"],
            "Signed APK differs from the immutable candidate")
    is_build97 = candidate["version_code"] == 97
    is_build99 = build99.selected(candidate)
    if is_build99:
        build99.require_receipts(candidate, approval, json.loads(read(receipts / "build99-upgrade.json")))
    upgrade = receipts / ("from97" if is_build99 else "from96" if is_build97 else "from80")
    if is_build99:
        build99.verify_default_and_downgrade_receipts(upgrade)
    if is_build97:
        require(approval["mode"] == "production" and approval["candidate"] == build97.CANDIDATE
                and approval["approving_id"] == build97.OWNER
                and approval["regression_sha256"] == build97.APP_DIGEST
                and approval["inputs_sha256"] == build97.checksums(),
                "Build 97 production authorization differs from the reviewed signing controls")
        proof = json.loads(read(receipts / "build97-upgrade.json"))
        build97.require_upgrade(proof, candidate["source"])
        require(proof["apk_sha256"] == candidate["apk_sha256"], "Build 97 proof tested a different signed APK")
    require(read(upgrade / "upgrade-baseline-sha256.txt").split()[0] == candidate["baseline_sha256"],
            f"Upgrade used a different Build {candidate['baseline_code']} baseline")
    require(read(upgrade / "upgrade-candidate-sha256.txt").split()[0] == candidate["apk_sha256"],
            "Upgrade tested a different candidate")
    rehearsal=json.loads(read(upgrade / "in-app-update.json"))
    require(all(rehearsal.get(key)==value for key,value in {
        "result":"PASS","source":candidate["source"],"baseline_code":candidate["baseline_code"],
        "candidate_code":candidate["version_code"],"installed_code":candidate["version_code"],"apk_sha256":candidate["apk_sha256"],
        "discovery":"PASS","download_and_verification":"PASS","android_installer":"confirmed",
    }.items()), "Missing exact-artifact in-app Android upgrade rehearsal")
    return upgrade


def check_remote_assets(release, expected, allow_partial=False):
    current = {asset["name"]: asset["digest"] for asset in release["assets"]}
    require(len(current) == len(release["assets"]), "Duplicate published asset name")
    require(current.keys() <= expected.keys() and all(expected[k] == v for k, v in current.items()),
            "Existing GitHub release contains different assets")
    if not allow_partial:
        require(current == expected, "GitHub release is missing verified assets")
    return current


def gh(*args, binary=None):
    result = subprocess.run(["gh", *args], stdout=binary or subprocess.PIPE,
                            stderr=subprocess.PIPE, check=False)
    if result.returncode:
        raise RuntimeError(result.stderr.decode().strip() or "GitHub command failed")
    return result.stdout


def api(path):
    return json.loads(gh("api", f"repos/{REPO}/{path}"))


def verify_tag(candidate, *, allow_missing=False):
    tag = candidate["tag"]
    require(re.fullmatch(r"v\d+(?:\.\d+){2,3}", tag), "Invalid stable release tag")
    try:
        ref = api(f"git/ref/tags/{tag}")["object"]
    except RuntimeError as error:
        if "HTTP 404" in str(error):
            if allow_missing:
                return False
            raise ValueError(f"Missing tag {tag}; it must resolve to {candidate['source']}") from error
        raise
    if ref["type"] == "tag":
        ref = api(f"git/tags/{ref['sha']}")["object"]
    require(ref["type"] == "commit" and ref["sha"] == candidate["source"],
            "Remote tag does not point to the frozen release source")
    return True


def ensure_tag(candidate):
    if verify_tag(candidate, allow_missing=True):
        return
    source, tag = candidate["source"], candidate["tag"]
    require(re.fullmatch(r"[0-9a-f]{40}", source), "Invalid frozen release source")
    push_env = os.environ.copy()
    tag_token = push_env.pop("NOVAOS_TAG_TOKEN", "")
    if tag_token:
        push_env["GH_TOKEN"] = tag_token
    result = subprocess.run(
        ["git", "-c", "credential.helper=", "-c", "credential.helper=!gh auth git-credential",
         "push", "--porcelain", f"https://github.com/{REPO}.git", f"{source}:refs/tags/{tag}"],
        env=push_env, capture_output=True, text=True, check=False,
    )
    # A concurrent publisher may have created the tag. Trust only the remote target.
    if verify_tag(candidate, allow_missing=True):
        return
    if result.returncode:
        detail = "\n".join(stream.strip() for stream in (result.stdout, result.stderr) if stream.strip())
        raise ValueError(
            f"Automatic tag creation failed for {tag}: {detail}. "
            "If GitHub rejected the workflow token, configure the repository-scoped NovaOS release GitHub App "
            "(Contents and Workflows read/write) in the novaos-production-publication environment."
        )
    raise ValueError(f"Git accepted the push, but {tag} could not be verified on GitHub")


def archive(candidate, kind, name, work):
    artifact = candidate[f"{kind}_id"]
    expected = candidate[f"{kind}_sha256"]
    meta = api(f"actions/artifacts/{artifact}")
    require(meta["name"] == name and not meta["expired"] and
            meta["digest"] == "sha256:" + expected and
            meta["workflow_run"]["id"] == candidate["production_run"] and
            meta["workflow_run"]["head_sha"] == candidate["production_head"],
            f"Wrong, expired or modified {kind} artifact")
    path = work / f"{kind}.zip"
    with path.open("wb") as file:
        gh("api", f"repos/{REPO}/actions/artifacts/{artifact}/zip", binary=file)
    require(digest(path) == expected, f"Downloaded {kind} archive digest mismatch")
    out = work / kind
    with zipfile.ZipFile(path) as zipped:
        names = zipped.namelist()
        require(len(names) == len(set(names)) and all(
            not Path(n).is_absolute() and ".." not in Path(n).parts for n in names
        ), "Unsafe archive paths")
        zipped.extractall(out)
    return out


def find_release(tag):
    rows = gh("api", f"repos/{REPO}/releases", "--paginate",
              "--jq", f'.[] | select(.tag_name == {json.dumps(tag)})').decode().splitlines()
    matches = [json.loads(row) for row in rows if row.strip()]
    require(len(matches) <= 1, "Multiple releases refer to the candidate tag")
    return matches[0] if matches else None



def confirmed_release(tag, expected=None, *, stable=False, release_id=None, attempts=4):
    """Bounded read-after-write confirmation; missing state is never success."""
    for attempt in range(attempts):
        try:
            release = api("releases/latest") if stable else (api(f"releases/{release_id}") if release_id is not None else find_release(tag))
        except RuntimeError as error:
            if not stable or "HTTP 404" not in str(error):
                raise
            release = None
        if release is not None and release_id is not None:
            require(release["id"] == release_id and release["tag_name"] == tag, "Release ID refers to another candidate")
        if release is not None and (not stable or release["tag_name"] == tag):
            require(not release["prerelease"], "Stable candidate cannot use a prerelease")
            if expected is None:
                return release
            current = check_remote_assets(release, expected, allow_partial=True)
            if current == expected and (not stable or not release["draft"]):
                return release
        if attempt + 1 < attempts:
            time.sleep(2 ** attempt)
    raise ValueError("GitHub did not confirm the complete " + ("latest stable release" if stable else "draft release") + " within the bounded readback window")

def publish(candidate, assets, attempts=3):
    tag = candidate["tag"]
    expected = {name: "sha256:" + digest(assets / name) for name in ASSETS}
    for attempt in range(attempts):
        try:
            release_id = candidate.get("release_id")
            if release_id is not None:
                require(type(release_id) is int and release_id > 0, "Invalid pinned release ID")
                release = api(f"releases/{release_id}")
                require(release["id"] == release_id and release["tag_name"] == tag,
                        "Pinned release belongs to another candidate")
            else:
                release = find_release(tag)
            if release is None:
                gh("release", "create", tag, "--verify-tag", "--draft", "--repo", REPO,
                   "--title", f"NovaOS {tag[1:]} — Build {candidate['version_code']}",
                   "--notes-file", str(assets / "notes.md"))
                release = confirmed_release(tag)
            require(not release["prerelease"], "Stable candidate cannot use a prerelease")
            current = check_remote_assets(release, expected, allow_partial=release["draft"])
            if release["draft"]:
                missing = [str(assets / name) for name in sorted(expected.keys() - current.keys())]
                if missing:
                    gh("release", "upload", tag, *missing, "--repo", REPO)
                confirmed_release(tag, expected, release_id=release.get("id"))
                verify_tag(candidate)
                if build99.selected(candidate):
                    build99.verify_owner_marker(Path(build99.MANIFEST), api)
                    build99.verify_latest(candidate, api, verify_tag, check_remote_assets)
                gh("release", "edit", tag, "--draft=false", "--latest", "--repo", REPO)
            latest = confirmed_release(tag, expected, stable=True)
            require(not latest["draft"] and not latest["prerelease"] and latest["tag_name"] == tag,
                    "Candidate is not the latest stable release")
            check_remote_assets(latest, expected)
            verify_tag(candidate)
            require(all(asset["browser_download_url"] ==
                        f"https://github.com/{REPO}/releases/download/{tag}/{asset['name']}"
                        for asset in latest["assets"]), "Updater URLs do not match the verified release")
            return latest["html_url"]
        except RuntimeError as error:
            if "HTTP 403" in str(error):
                raise ValueError("Publication denied (HTTP 403): check repository policy and contents: write") from error
            if attempt + 1 == attempts or not re.search(r"HTTP (429|5\d\d)|timed out|connection reset", str(error), re.I):
                raise
            time.sleep(2 ** attempt)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--work", type=Path)
    parser.add_argument("--tools", type=Path)
    parser.add_argument("--preflight", action="store_true")
    args = parser.parse_args()
    candidate = json.loads(args.candidate.read_text())
    try:
        is_build99 = build99.selected(candidate)
        if is_build99:
            build99.validate_identity(candidate)
            parent = build99.verify_owner_marker(args.candidate, api)
            build99.verify_source(candidate, parent, api)
            build99.verify_workflows(candidate, api)
            build99.verify_latest(candidate, api, verify_tag, check_remote_assets)
        else:
            for key in ("regression_run", "production_run"):
                require(type(candidate[key]) is int, "Invalid workflow run")
            regression = f"actions/runs/{candidate['regression_run']}"
            production = f"actions/runs/{candidate['production_run']}"
            check_provenance(candidate, api(regression), api(regression + "/jobs?per_page=100")["jobs"],
                             api(production), api(production + "/jobs?per_page=100")["jobs"])
            if candidate["version_code"] == 97:
                build97.verify_release_tree(candidate["source"])
                subprocess.run(["git", "merge-base", "--is-ancestor", candidate["source"], "HEAD"], check=True)
        if args.preflight:
            if is_build99:
                verify_tag(candidate, allow_missing=True)
                print("PASS: Build 99 exact source and workflow provenance")
            else:
                ensure_tag(candidate)
                print("PASS: exact remote stable tag")
            return
        require(args.work is not None and args.tools is not None, "An isolated work directory and APK tools are required")
        if not is_build99:
            verify_tag(candidate)
            subprocess.run(["git", "fetch", "--no-tags", f"https://github.com/{REPO}.git",
                            f"refs/tags/{candidate['tag']}:refs/tags/{candidate['tag']}"], check=True)
        args.work.mkdir(mode=0o700)
        if is_build99:
            assets_name, receipts_name = build99.ASSETS, build99.RECEIPTS
        elif candidate["version_code"] == 97:
            assets_name, receipts_name = "novaos-build97-verified-stable-assets", "novaos-build97-production-receipts"
        else:
            assets_name, receipts_name = "novaos-code81-verified-stable-assets", "novaos-code81-production-upgrade-receipts"
        assets = archive(candidate, "assets", assets_name, args.work)
        receipts = archive(candidate, "receipts", receipts_name, args.work)
        upgrade = check_local_receipts(candidate, assets, receipts)
        check = verify_payload if is_build99 else verify
        apk_digest = check(SimpleNamespace(
            source=candidate["source"], tag=candidate["tag"], baseline_code=candidate["baseline_code"],
            version_code=candidate["version_code"], assets=assets, evidence=upgrade,
            notes=assets / "notes.md", tools=args.tools,
        ))
        require(apk_digest == candidate["apk_sha256"], "Verified APK does not match the approved source")
        if is_build99:
            build99.verify_owner_marker(args.candidate, api)
            build99.verify_latest(candidate, api, verify_tag, check_remote_assets)
            ensure_tag(candidate)
        print(publish(candidate, assets))
    except (ValueError, RuntimeError, OSError, KeyError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Stable release failed: {error}\n")


if __name__ == "__main__":
    main()
