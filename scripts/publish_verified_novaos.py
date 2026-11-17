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

from verify_novaos_release_assets import verify

REPO = "traynor1987/Private-gallery-"
BRANCH = "main"
ASSETS = {"private-gallery-release.apk", "private-gallery-release.apk.sha256",
          "private-gallery-release.metadata.json"}
PRODUCTION_STEPS = {
    "Validate original runtime reports and authenticated owner authorization before signer access",
    "Verify published Build 83 baseline before signer access",
    "Verify the existing permanent private key and original certificate",
    "Verify frozen candidate package signature OCR distribution and 16KiB alignment",
    "Original-key Build 83 to versionCode 84 upgrade and restart without uninstall or clear",
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
    for record, run_id, source, workflow in (
        (regression, candidate["regression_run"], candidate["source"], "fast-development.yml"),
        (production, candidate["production_run"], candidate["production_head"], "sanctuary-production.yml"),
    ):
        require(record["id"] == run_id and record["head_sha"] == source and
                record["head_branch"] == BRANCH and record["event"] == "push" and
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
    upgrade = receipts / "from80"
    require(read(upgrade / "upgrade-baseline-sha256.txt").split()[0] == candidate["baseline_sha256"],
            "Upgrade used a different Build 83 baseline")
    require(read(upgrade / "upgrade-candidate-sha256.txt").split()[0] == candidate["apk_sha256"],
            "Upgrade tested a different candidate")
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
    require(re.fullmatch(r"v\d+(?:\.\d+){3}", tag), "Invalid stable release tag")
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


def publish(candidate, assets, attempts=3):
    tag = candidate["tag"]
    expected = {name: "sha256:" + digest(assets / name) for name in ASSETS}
    for attempt in range(attempts):
        try:
            release = find_release(tag)
            if release is None:
                gh("release", "create", tag, "--verify-tag", "--draft", "--repo", REPO,
                   "--title", f"NovaOS {tag[1:]} — Build {candidate['version_code']}",
                   "--notes-file", str(assets / "notes.md"))
                release = find_release(tag)
                require(release is not None, "Cannot verify the GitHub draft")
            require(not release["prerelease"], "Stable candidate cannot use a prerelease")
            current = check_remote_assets(release, expected, allow_partial=release["draft"])
            if release["draft"]:
                missing = [str(assets / name) for name in sorted(expected.keys() - current.keys())]
                if missing:
                    gh("release", "upload", tag, *missing, "--repo", REPO)
                check_remote_assets(find_release(tag), expected)
                verify_tag(candidate)
                gh("release", "edit", tag, "--draft=false", "--latest", "--repo", REPO)
            latest = api("releases/latest")
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
        for key in ("regression_run", "production_run"):
            require(type(candidate[key]) is int, "Invalid workflow run")
        regression = f"actions/runs/{candidate['regression_run']}"
        production = f"actions/runs/{candidate['production_run']}"
        check_provenance(candidate, api(regression), api(regression + "/jobs?per_page=100")["jobs"],
                         api(production), api(production + "/jobs?per_page=100")["jobs"])
        if args.preflight:
            ensure_tag(candidate)
            print("PASS: exact remote stable tag")
            return
        verify_tag(candidate)
        subprocess.run(["git", "fetch", "--no-tags", f"https://github.com/{REPO}.git",
                        f"refs/tags/{candidate['tag']}:refs/tags/{candidate['tag']}"], check=True)
        args.work.mkdir(mode=0o700)
        assets = archive(candidate, "assets", "novaos-code81-verified-stable-assets", args.work)
        receipts = archive(candidate, "receipts", "novaos-code81-production-upgrade-receipts", args.work)
        upgrade = check_local_receipts(candidate, assets, receipts)
        apk_digest = verify(SimpleNamespace(
            source=candidate["source"], tag=candidate["tag"], baseline_code=candidate["baseline_code"],
            assets=assets, evidence=upgrade, notes=assets / "notes.md", tools=args.tools,
        ))
        require(apk_digest == candidate["apk_sha256"], "Verified APK does not match the approved source")
        print(publish(candidate, assets))
    except (ValueError, RuntimeError, OSError, KeyError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Stable release failed: {error}\n")


if __name__ == "__main__":
    main()
