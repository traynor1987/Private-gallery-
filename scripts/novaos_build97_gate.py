#!/usr/bin/env python3
"""Frozen Build 97 controls for the existing, main-triggered original-key workflow."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

from verify_code80_apk_identity import inspect
from verify_compiled_upgrade1711 import transform


REPO = "traynor1987/Private-gallery-"
OWNER = 16270479
PR = 73
CANDIDATE = "25305c69992565f5e91185f0c7362f93d092ef02"
CANDIDATE_TREE = "64934ba88fe651016e27e0b16ad196a0b8bf608e"
FEATURE = "feature/nova-home-intelligence"
STABLE_MAIN = "1588c76d1573dc2098f1f5402c16026a1ca70e2f"
BASELINE = "6d91450aac55c805df5d6f0bac20164b09697ab2"
BASELINE_DIGEST = "beb2c7eb52f43f2bd0ead66eecdc6ffa509ef254e778e0624496c648f0c3707b"
BASELINE_SIZE = 152_057_539
CERTIFICATE = "94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17"
PACKAGE = "uk.co.traynor.privategallery"
APP_RUN = 45169787224
APP_ARTIFACT = 14817007032
APP_DIGEST = "efdb7c2890865408cd2181d0fce1c4335e13b942cfa99906db3ff093359e7afe"
PREVIEW_RUN = 45169968138
PREVIEW = "39df141a046ed960b733cf1147cde2a4a3238ca7"
MARKER = ".novaos-build97-authorization"
WORKFLOW = ".github/workflows/sanctuary-production.yml"
FIXTURE = "docs/release1711/UpgradeFixtureTest.kt"
FIXTURE_DIGEST = "0d1c2207aeab2ef68fe29f10b0a868914521c0d49aaea79a9e79d9b3996c6e7e"
CONTROLS = {
    WORKFLOW, FIXTURE, "scripts/novaos_build97_gate.py", "scripts/run_build97_upgrade.sh",
    "scripts/rehearse_in_app_update85.py", "scripts/verify_backup_exclusions.py",
    "scripts/verify_evidence179_distribution.py", "scripts/verify_code80_apk_identity.py",
    "scripts/verify_code79_baseline.py", "scripts/verify_compiled_upgrade1711.py",
    "scripts/verify_signed_upgrade1711.py", "scripts/verify_android17_sdk.py",
    "scripts/prime_gradle95_ci.sh",
}
MAIN_ONLY_CHANGES = CONTROLS | {
    MARKER, ".github/workflows/novaos-publish-verified.yml",
    "scripts/publish_verified_novaos.py", "docs/build85/verify_live_build84.py",
    ".novaos-stable-candidate.json", ".novaos-publish-authorization",
    "scripts/tests/test_novaos_build97_gate.py", "scripts/tests/test_build93_release_controls.py",
}
APP_STEPS = {
    "Install and verify Android 17 SDK", "Verify Nova Brain authorization and accounting",
    "Build and check the Cloud Teacher image", "Check protected Hidden and Sanctuary scope",
    "Compile and test the isolated debug build", "Verify Intelligence and launcher regressions on Android 17",
}
SIGNING_STEPS = {
    "Validate Build 97 owner authorization and exact evidence before signer access",
    "Prepare the Build 96 baseline and upgrade fixture without signing access",
    "Verify original production identity and sign the frozen Build 97 source",
    "Verify the signed Build 97 APK and protected packaging",
    "Rehearse populated Build 96 to 97 upgrade and restart through the in-app updater",
    "Retain only non-sensitive Build 97 verification receipts",
}
APK_NAME = "private-gallery-release.apk"


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def api(path):
    return json.loads(subprocess.check_output(["gh", "api", f"repos/{REPO}/{path}"]))


def run_record(run_id, source, branch, workflow, event):
    record = api(f"actions/runs/{run_id}")
    require(record.get("id") == run_id and record.get("head_sha") == source
            and record.get("head_branch") == branch and record.get("event") == event
            and record["path"].split("@")[0] == workflow and record["head_repository"]["full_name"] == REPO
            and record.get("status") == "completed" and record.get("conclusion") == "success",
            f"Workflow {run_id} is not successful evidence for the frozen source")
    return record


def check_steps(run, job_name, required):
    result = api(f"actions/runs/{run['id']}/attempts/{run['run_attempt']}/jobs?per_page=100")
    jobs = [job for job in result["jobs"] if job["name"] == job_name]
    require(result["total_count"] == len(result["jobs"]) and len(jobs) == 1,
            f"Missing complete {job_name} job evidence")
    job, = jobs
    passed = {step["name"] for step in job["steps"] if step["conclusion"] == "success"}
    require(job["conclusion"] == "success" and required <= passed,
            f"{job_name} lacks passing steps: {sorted(required - passed)}")


def artifact(run_id, source, artifact_id, checksum, name, destination):
    metadata = api(f"actions/artifacts/{artifact_id}")
    require(metadata["id"] == artifact_id and not metadata["expired"] and metadata["name"] == name
            and metadata["digest"] == "sha256:" + checksum and metadata["workflow_run"]["id"] == run_id
            and metadata["workflow_run"]["head_sha"] == source,
            f"Wrong, changed or expired {name} artifact")
    with destination.open("wb") as output:
        subprocess.run(["gh", "api", f"repos/{REPO}/actions/artifacts/{artifact_id}/zip"], stdout=output, check=True)
    require(digest(destination) == checksum, f"{name} archive SHA-256 differs")


def report_from(archive, suffix):
    names = [name for name in archive.namelist() if name == suffix or name.endswith("/" + suffix)]
    require(len(names) == 1, f"Missing unique {suffix} report")
    return archive.read(names[0]).decode()


def verify_app_reports(path):
    with zipfile.ZipFile(path) as archive:
        sdk = json.loads(report_from(archive, "intelligence/android17-sdk.json"))
        require(sdk["api_level"] == 37 and sdk["preview"] is False, "Verified SDK must be Android 17")
        transcript = report_from(archive, "intelligence/instrumentation.txt")
        require(re.search(r"^OK \(56 tests\)\s*$", transcript, re.M)
                and not re.search(r"^INSTRUMENTATION_STATUS_CODE: -[1-4]\s*$", transcript, re.M),
                "Android 17 instrumentation results are incomplete or failed")
        for name in ("OwnerPreviewIsolationTest", "HiddenCredentialSeparationTest", "HiddenRestoreAccessTest",
                     "IntelligenceLauncherUiTest", "SanctuaryLauncherPolicy70Test"):
            require(name in transcript, f"Missing Android 17 case group: {name}")
        for folder in ("guardian-notification-pipeline-93", "guardian-fold-notification"):
            require("1/1 PASS" in report_from(archive, folder + "/result.txt"), f"Failed {folder}")
        restart = report_from(archive, "launcher-process-70/receipt.txt").splitlines()
        require({f"source={CANDIDATE}", "api=37", "result=PASS"} <= set(restart)
                and re.fullmatch(r"old_pid=\d+", restart[2]) and re.fullmatch(r"new_pid=\d+", restart[3])
                and restart[2].split("=")[-1] != restart[3].split("=")[-1], "Launcher did not restart on the frozen source")
        tests = 0
        for name in archive.namelist():
            if name.endswith(".xml") and "testDebugUnitTest/" in name:
                suite = ET.fromstring(archive.read(name))
                require(all(int(suite.get(key, "0")) == 0 for key in ("skipped", "errors", "failures")),
                        f"JVM report failed: {name}")
                tests += int(suite.get("tests", "0"))
        require(tests == 786, f"Expected all 786 JVM cases, found {tests}")


def checksums():
    for path in CONTROLS:
        require(Path(path).is_file() and not Path(path).is_symlink(), f"Missing trusted input {path}")
    return {path: digest(Path(path)) for path in sorted(CONTROLS)}


def ensure_source_unchanged(source, baseline):
    difference = set(git("diff", "--name-only", baseline, source).splitlines())
    require(difference <= MAIN_ONLY_CHANGES, f"Source drift outside release controls: {sorted(difference - MAIN_ONLY_CHANGES)}")


def authorize(output):
    env = os.environ
    require(env["GITHUB_REPOSITORY"] == REPO and env["GITHUB_REF"] == "refs/heads/main"
            and env["GITHUB_EVENT_NAME"] == "push" and env["GITHUB_RUN_ATTEMPT"] == "1"
            and env["GITHUB_ACTOR_ID"] == str(OWNER) and env["GITHUB_TRIGGERING_ACTOR"] == "traynor1987",
            "Protected Build 97 requires an authenticated owner main push")
    event = json.loads(Path(env["GITHUB_EVENT_PATH"]).read_text())
    head = env["GITHUB_SHA"]
    parent = event["before"]
    require(event["sender"]["id"] == OWNER and not event["forced"] and not event["created"]
            and event["after"] == head and git("rev-parse", "HEAD") == head
            and git("rev-list", "--parents", "-n", "1", head).split() == [head, parent]
            and git("diff", "--name-only", parent, head) == MARKER,
            "Authorization must be an owner marker-only commit on main")
    marker = json.loads(Path(MARKER).read_text())
    require(marker["main_parent"] == parent and marker["candidate"] == CANDIDATE
            and git("rev-parse", CANDIDATE + "^{tree}") == CANDIDATE_TREE
            and marker["inputs_sha256"] == checksums(), "Authorization does not bind the exact candidate and controls")
    mode, source = marker["mode"], marker["source"]
    pr = api(f"pulls/{PR}")
    require(pr["number"] == PR and pr["head"]["sha"] == CANDIDATE and pr["head"]["ref"] == FEATURE
            and pr["head"]["repo"]["full_name"] == REPO and pr["base"]["ref"] == "main",
            "PR #73 does not match the frozen, same-repository candidate")
    if mode == "rehearsal":
        require(source == CANDIDATE and pr["state"] == "open" and not pr["merged"]
                and MARKER not in git("ls-tree", "--name-only", parent).splitlines(),
                "The one-time rehearsal requires the exact unmerged PR #73")
        ensure_source_unchanged(parent, STABLE_MAIN)
    elif mode == "production":
        require(pr["merged"] and pr["merge_commit_sha"] == source, "Production requires the verified PR #73 merge")
        subprocess.run(["git", "merge-base", "--is-ancestor", CANDIDATE, source], check=True)
        subprocess.run(["git", "merge-base", "--is-ancestor", source, parent], check=True)
        ensure_source_unchanged(source, CANDIDATE)
        verify_rehearsal(marker, output)
    else:
        raise ValueError("Unknown Build 97 signing mode")
    run = api(f"actions/runs/{env['GITHUB_RUN_ID']}")
    require(run["head_sha"] == head and run["head_branch"] == "main" and run["event"] == "push"
            and run["actor"]["id"] == OWNER and run["run_attempt"] == 1 and run["path"].split("@")[0] == WORKFLOW,
            "Signing run is not bound to the authenticated owner marker")
    require(api("git/ref/heads/main")["object"]["sha"] == head, "Main moved after Build 97 authorization")
    require(api("releases/latest")["tag_name"] == "v1.7.50"
            and not any(item["ref"] == "refs/tags/v1.7.51" for item in api("git/matching-refs/tags/v1.7.51")),
            "Build 96 is no longer stable or the Build 97 tag is already in use")
    app = run_record(APP_RUN, CANDIDATE, FEATURE, ".github/workflows/nova-home-intelligence.yml", "pull_request")
    require([pull["number"] for pull in app["pull_requests"]] == [PR], "App CI is not associated with PR #73")
    check_steps(app, "verify", APP_STEPS)
    with tempfile.TemporaryDirectory(dir=output.parent) as scratch:
        evidence = Path(scratch) / "app-evidence.zip"
        artifact(APP_RUN, CANDIDATE, APP_ARTIFACT, APP_DIGEST, "nova-intelligence-android17", evidence)
        verify_app_reports(evidence)
    preview = run_record(PREVIEW_RUN, PREVIEW, "verification/nova-brain-fold-owner-preview",
                         ".github/workflows/nova-brain-fold-preview-checks.yml", "push")
    check_steps(preview, "isolate", {
        "Check default-off TLS arming and simulated provider dispatch policy",
        "Apply the reviewed firewall inside a disposable network and restrict the preview port",
        "Verify the preview container contract without an OpenAI or TLS key",
    })
    receipt = dict(mode=mode, source=source, candidate=CANDIDATE, approval_commit=head,
                   approval_run=int(env["GITHUB_RUN_ID"]), approving_id=OWNER, version="1.7.51",
                   version_code=97, regression_run=APP_RUN, regression_artifact=APP_ARTIFACT,
                   regression_sha256=APP_DIGEST, preview_run=PREVIEW_RUN, inputs_sha256=marker["inputs_sha256"])
    write_json(output / "owner-approval.json", receipt)
    with Path(env["GITHUB_OUTPUT"]).open("a") as result:
        result.write(f"source={source}\nmode={mode}\n")
    print(f"Verified exact PR #{PR} app, Android 17, protected-core and VPS preview evidence for {mode}.")


def verify_rehearsal(marker, output):
    rehearsal = marker["rehearsal"]
    record = run_record(rehearsal["run"], rehearsal["head"], "main", WORKFLOW, "push")
    require(record["actor"]["id"] == OWNER and record["run_attempt"] == 1, "Rehearsal lacks original owner authorization")
    check_steps(record, "build97-protected", SIGNING_STEPS)
    with tempfile.TemporaryDirectory(dir=output.parent) as scratch:
        target = Path(scratch) / "verified-rehearsal.zip"
        artifact(record["id"], record["head_sha"], rehearsal["artifact"], rehearsal["sha256"],
                 "novaos-build97-rehearsal-receipts", target)
        with zipfile.ZipFile(target) as archive:
            approval = json.loads(report_from(archive, "owner-approval.json"))
            upgrade = json.loads(report_from(archive, "build97-upgrade.json"))
    require(approval["mode"] == "rehearsal" and approval["source"] == CANDIDATE
            and approval["approval_commit"] == rehearsal["head"] and approval["approval_run"] == rehearsal["run"]
            and approval["approving_id"] == OWNER and approval["inputs_sha256"] == marker["inputs_sha256"],
            "Rehearsal receipts belong to different reviewed controls or source")
    require_upgrade(upgrade, CANDIDATE)


def fetch_build96(destination, tools):
    url = f"https://github.com/{REPO}/releases/download/v1.7.50/{APK_NAME}"
    with tempfile.TemporaryDirectory(prefix=".nova96-", dir=destination.parent) as folder:
        staged = Path(folder) / APK_NAME
        subprocess.run(["curl", "--disable", "--fail", "--silent", "--show-error", "--location",
                        "--proto", "=https", "--proto-redir", "=https", "--max-time", "180",
                        "--max-filesize", str(BASELINE_SIZE), "--output", str(staged), url], check=True)
        require(staged.stat().st_size == BASELINE_SIZE and inspect(staged, tools, 96, "1.7.50") == BASELINE_DIGEST,
                "Published Build 96 does not match its original-key receipt")
        subprocess.run([str(tools / "zipalign"), "-c", "-P", "16", "4", str(staged)], check=True)
        staged.chmod(0o600)
        staged.replace(destination)


def create_fixture(baseline_root):
    fixture = Path(FIXTURE)
    require(digest(fixture) == FIXTURE_DIGEST, "Archived Build 96 fixture differs from its review")
    data = fixture.read_bytes()
    for old, new, count in (
        (b"Build 95 to versionCode 96", b"Build 96 to versionCode 97", 1),
        (b"baselineCode == 95 && candidateCode == 96", b"baselineCode == 96 && candidateCode == 97", 1),
        (b"Build95", b"Build96", 3),
        (b"v1.7.50/", b"v1.7.51/", 1),
        (b"assertEquals(96L,available", b"assertEquals(97L,available", 1),
        (b'assertEquals("1.7.50",available', b'assertEquals("1.7.51",available', 1),
        (b"original=95 candidate=96", b"original=96 candidate=97", 1),
        (b'assertEquals(expected,store.read())', b'assertEquals(expected,store.read())\n'
         b'        assertFalse(uk.co.traynor.privategallery.core.editor.AiCredentialStore(context)'
         b'.isConfigured("nova-brain-owner-preview"))', 1),
    ):
        require(data.count(old) == count, f"Reviewed upgrade fixture anchor differs: {old.decode()}")
        data = data.replace(old, new)
    build = baseline_root / "app/build.gradle.kts"
    transformed, target = transform(data, build.read_bytes())
    path = baseline_root / "app/src/androidTest/java/uk/co/traynor/privategallery/upgrade/UpgradeFixtureTest.kt"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(transformed)
    build.write_bytes(target)


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n")


def require_upgrade(upgrade, source):
    require(all(upgrade.get(key) == value for key, value in {
        "result": "PASS", "source": source, "baseline_code": 96, "candidate_code": 97,
        "installed_code": 97, "certificate_sha256": CERTIFICATE, "baseline_sha256": BASELINE_DIGEST,
        "seed": "PASS", "verify": "PASS", "after_restart": "PASS", "transport": "controlled-loopback",
        "discovery": "PASS", "download_and_verification": "PASS", "android_installer": "confirmed",
    }.items()) and re.fullmatch("[0-9a-f]{64}", upgrade.get("apk_sha256", "")),
            "Missing original-key populated Build 96 to 97 updater and restart proof")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    task = parser.add_subparsers(dest="task", required=True)
    for name in ("authorize", "fixture"):
        task.add_parser(name).add_argument("path", type=Path)
    for name in ("baseline", "candidate"):
        sub = task.add_parser(name)
        sub.add_argument("path", type=Path)
        sub.add_argument("tools", type=Path)
    task.add_parser("hashes")
    args = parser.parse_args()
    if args.task == "authorize":
        authorize(args.path)
    elif args.task == "fixture":
        create_fixture(args.path)
    elif args.task == "baseline":
        fetch_build96(args.path, args.tools)
    elif args.task == "candidate":
        print(inspect(args.path, args.tools, 97, "1.7.51"))
    else:
        print(json.dumps(checksums(), indent=2))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        raise SystemExit(f"Build 97 gate failed: {error}") from None
