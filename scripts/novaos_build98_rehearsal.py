#!/usr/bin/env python3
"""Authorize the exact Build 98 pre-merge, receipt-only original-key rehearsal."""

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

from novaos_build97_gate import (
    APK_NAME, APP_STEPS, CERTIFICATE, FIXTURE, FIXTURE_DIGEST, OWNER, REPO, WORKFLOW,
    api, artifact, check_steps, digest, git, report_from, require, run_record, write_json,
)
from verify_code80_apk_identity import inspect
from verify_compiled_upgrade1711 import transform


PR = 77
BRANCH = "repair/build-98-nova-intelligence"
CANDIDATE = "26553075df3c530fd81a98640e59ddf09c545215"
CANDIDATE_TREE = "a7cda80f5afb9e39a0df2dea9d5fe9a7738e774e"
STABLE_MAIN = "7bbcdacaa9af57862b4f6505c1ad5a71ebbfbc3a"
BASELINE = "6b7f1bbe40db3f213ff087833f37e05954e1c792"
BASELINE_DIGEST = "75760970c6dcc00f560cbb5493b67a6bbc190397948dabb32e50e4753e12c988"
BASELINE_SIZE = 152_385_219
APP_RUN = 45290853524
APP_WORKFLOW_ID = 425037366
APP_WORKFLOW = ".github/workflows/nova-home-intelligence.yml"
APP_ARTIFACT = 14876255573
APP_DIGEST = "7563b8daa0efeecf7ecb5b3618e8609462e3539a9823e80d1208fa7e39b9444b"
MARKER = ".novaos-build98-rehearsal-authorization"
CONTROL_CHANGES = {
    WORKFLOW, "scripts/novaos_build98_rehearsal.py", "scripts/run_build98_rehearsal.sh",
    "scripts/rehearse_in_app_update85.py", "scripts/tests/test_novaos_build98_rehearsal.py",
}
TRUSTED_INPUTS = CONTROL_CHANGES | {
    FIXTURE, "scripts/novaos_build97_gate.py", "scripts/verify_backup_exclusions.py",
    "scripts/verify_evidence179_distribution.py", "scripts/verify_code80_apk_identity.py",
    "scripts/verify_code79_baseline.py", "scripts/verify_compiled_upgrade1711.py",
    "scripts/verify_signed_upgrade1711.py", "scripts/verify_android17_sdk.py",
    "scripts/prime_gradle95_ci.sh",
}


def checksums():
    hashes = {}
    for name in sorted(TRUSTED_INPUTS):
        path = Path(name)
        require(path.is_file() and not path.is_symlink(), f"Missing trusted control {name}")
        hashes[name] = digest(path)
    return hashes


def marker_for(parent):
    return {
        "schema": 1, "mode": "rehearsal", "main_parent": parent, "candidate": CANDIDATE,
        "candidate_tree": CANDIDATE_TREE, "version_name": "1.7.52", "version_code": 98,
        "android_run": APP_RUN, "android_artifact": APP_ARTIFACT, "android_artifact_sha256": APP_DIGEST,
        "inputs_sha256": checksums(),
    }


def verify_authority(env, event, marker):
    require(env.get("GITHUB_REPOSITORY") == REPO and env.get("GITHUB_REF") == "refs/heads/main"
            and env.get("GITHUB_EVENT_NAME") == "push" and env.get("GITHUB_RUN_ATTEMPT") == "1"
            and env.get("GITHUB_ACTOR_ID") == str(OWNER) and env.get("GITHUB_TRIGGERING_ACTOR") == "traynor1987",
            "Rehearsal requires an authenticated owner main push")
    head, parent = env["GITHUB_SHA"], event["before"]
    require(event["sender"]["id"] == OWNER and event["forced"] is False and event["created"] is False
            and event["after"] == head and git("rev-parse", "HEAD") == head
            and git("rev-list", "--parents", "-n", "1", head).split() == [head, parent]
            and git("diff", "--name-only", parent, head) == MARKER,
            "Authorization must be an owner marker-only commit on main")
    require(marker == marker_for(parent), "Authorization does not bind the exact rehearsal, candidate and controls")
    require(git("merge-base", parent, CANDIDATE) == STABLE_MAIN
            and set(git("diff", "--name-only", STABLE_MAIN, parent).splitlines()) == CONTROL_CHANGES,
            "Main moved outside the reviewed rehearsal control changes")
    require(api("git/ref/heads/main")["object"]["sha"] == head, "Main moved after owner authorization")
    return head


def verify_candidate():
    pr = api(f"pulls/{PR}")
    require(pr["number"] == PR and pr["state"] == "open" and not pr["merged"]
            and pr["head"]["sha"] == CANDIDATE and pr["head"]["ref"] == BRANCH
            and pr["head"]["repo"]["full_name"] == REPO and pr["base"]["ref"] == "main"
            and pr["base"]["repo"]["full_name"] == REPO,
            "PR #77 is not the exact open same-repository candidate")
    require(git("rev-parse", CANDIDATE + "^{tree}") == CANDIDATE_TREE, "Candidate tree differs")
    baseline_build = git("show", f"{BASELINE}:app/build.gradle.kts")
    expected_build = baseline_build.replace("versionCode = 97", "versionCode = 98", 1).replace(
        'versionName = "1.7.51"', 'versionName = "1.7.52"', 1,
    )
    require(git("show", f"{CANDIDATE}:app/build.gradle.kts") == expected_build,
            "Candidate configuration differs from the reviewed API 37 version bump")
    require(api("releases/latest")["tag_name"] == "v1.7.51"
            and api("git/ref/tags/v1.7.51")["object"]["sha"] == BASELINE
            and not any(item["ref"] == "refs/tags/v1.7.52" for item in api("git/matching-refs/tags/v1.7.52")),
            "Build 97 baseline or Build 98 version availability changed")


def verify_app_reports(path):
    with zipfile.ZipFile(path) as archive:
        sdk = json.loads(report_from(archive, "intelligence/android17-sdk.json"))
        require(sdk["api_level"] == 37 and sdk["preview"] is False, "Android 17 SDK evidence missing")
        transcript = report_from(archive, "intelligence/instrumentation.txt")
        require(re.search(r"^OK \(63 tests\)\s*$", transcript, re.M)
                and not re.search(r"^INSTRUMENTATION_STATUS_CODE: -[1-4]\s*$", transcript, re.M),
                "Android 17 instrumentation results are incomplete or failed")
        for group in ("IntelligenceBuild98VisualTest", "IntelligenceLauncherUiTest", "IntelligenceWorkspaceTest",
                      "OwnerPreviewIsolationTest", "HiddenCredentialSeparationTest", "HiddenRestoreAccessTest",
                      "SanctuaryLauncherPolicy70Test"):
            require(group in transcript, f"Missing Android 17 case group: {group}")
        for folder, expected in {
            "guardian-notification-pipeline-93": "1/1 PASS", "guardian-fold-notification": "1/1 PASS",
            "guardian-evidence-90": "5/5 PASS", "intelligence-sanctuary-accessibility": "1/1 PASS",
        }.items():
            require(expected in report_from(archive, folder + "/result.txt"), f"Failed {folder}")
        restart = dict(line.split("=", 1) for line in report_from(archive, "launcher-process-70/receipt.txt").splitlines())
        require(restart["source"] == CANDIDATE and restart["api"] == "37" and restart["result"] == "PASS"
                and restart["old_pid"].isdigit() and restart["new_pid"].isdigit()
                and restart["old_pid"] != restart["new_pid"], "Launcher restart evidence differs")
        tests = 0
        for name in archive.namelist():
            if name.endswith(".xml") and "testDebugUnitTest/" in name:
                suite = ET.fromstring(archive.read(name))
                require(all(int(suite.get(key, "0")) == 0 for key in ("skipped", "errors", "failures")),
                        f"JVM report failed: {name}")
                tests += int(suite.get("tests", "0"))
        require(tests == 798, f"Expected all 798 JVM tests, found {tests}")


def verify_regressions(output):
    regression = run_record(APP_RUN, CANDIDATE, BRANCH, APP_WORKFLOW, "pull_request")
    require(regression["workflow_id"] == APP_WORKFLOW_ID and regression["run_attempt"] == 1,
            "Unexpected Android 17 workflow or attempt")
    check_steps(regression, "verify", APP_STEPS)
    with tempfile.TemporaryDirectory(dir=output.parent) as scratch:
        evidence = Path(scratch) / "app-evidence.zip"
        artifact(APP_RUN, CANDIDATE, APP_ARTIFACT, APP_DIGEST, "nova-intelligence-android17", evidence)
        verify_app_reports(evidence)


def authorize(output):
    env = os.environ
    marker = Path(MARKER)
    require(marker.is_file() and not marker.is_symlink(), "Missing owner rehearsal authorization")
    approved = json.loads(marker.read_text())
    head = verify_authority(env, json.loads(Path(env["GITHUB_EVENT_PATH"]).read_text()), approved)
    run = api(f"actions/runs/{env['GITHUB_RUN_ID']}")
    require(run["head_sha"] == head and run["head_branch"] == "main" and run["event"] == "push"
            and run["actor"]["id"] == OWNER and run["triggering_actor"]["id"] == OWNER
            and run["run_attempt"] == 1 and run["path"].split("@")[0] == WORKFLOW,
            "Rehearsal run is not bound to the authenticated owner marker")
    verify_candidate()
    verify_regressions(output)
    write_json(output / "owner-approval.json", {
        **approved,
        "approval_commit": head, "approval_run": int(env["GITHUB_RUN_ID"]), "approving_id": OWNER,
    })
    with Path(env["GITHUB_OUTPUT"]).open("a") as result:
        result.write(f"source={CANDIDATE}\n")


def recheck_authorization():
    head = os.environ["GITHUB_SHA"]
    require(git("rev-parse", "HEAD") == head and api("git/ref/heads/main")["object"]["sha"] == head,
            "Main moved before signer access")
    verify_candidate()


def fetch_baseline(destination, tools):
    url = f"https://github.com/{REPO}/releases/download/v1.7.51/{APK_NAME}"
    with tempfile.TemporaryDirectory(dir=destination.parent) as folder:
        staged = Path(folder) / APK_NAME
        subprocess.run(["curl", "--disable", "--fail", "--silent", "--show-error", "--location",
                        "--proto", "=https", "--proto-redir", "=https", "--max-time", "180",
                        "--max-filesize", str(BASELINE_SIZE), "--output", str(staged), url], check=True)
        require(staged.stat().st_size == BASELINE_SIZE and inspect(staged, tools, 97, "1.7.51") == BASELINE_DIGEST,
                "Published Build 97 differs from its original-key receipt")
        subprocess.run([str(tools / "zipalign"), "-c", "-P", "16", "4", str(staged)], check=True)
        staged.chmod(0o600)
        staged.replace(destination)


MEMORY_FIXTURE = b"""    private fun assistantState98(space: uk.co.traynor.privategallery.core.home2.SpaceContext, before: JSONObject, seed: Boolean) {
        val workspace = uk.co.traynor.privategallery.intelligence.IntelligenceWorkspace(space)
        val question = "Synthetic Build 97 assistant question"
        val answer = "Encrypted assistant answer preserved"
        if (seed) {
            assertFalse(workspace.memory().enabled)
            // These constructors exist on the authentic baseline; Build 98 adds fields to both.
            val exchange = uk.co.traynor.privategallery.intelligence.IntelligenceExchange::class.java
                .getConstructor(String::class.java, String::class.java, java.lang.Boolean.TYPE)
                .newInstance(question, answer, true)
            val memory = uk.co.traynor.privategallery.intelligence.IntelligenceMemory::class.java
                .getConstructor(java.lang.Boolean.TYPE, List::class.java).newInstance(true, listOf(exchange))
            workspace.saveMemory(memory)
        }
        val stored = workspace.memory()
        assertTrue(stored.enabled)
        val exchange = stored.exchanges.single()
        assertEquals(question, exchange.question)
        assertEquals(answer, exchange.answer)
        assertTrue(exchange.cloud)
        if (!seed) {
            assertFalse(stored.javaClass.getMethod("getAutoSave").invoke(stored) as Boolean)
            assertFalse(exchange.javaClass.getMethod("getOwnerPreview").invoke(exchange) as Boolean)
            assertNull(exchange.javaClass.getMethod("getTimestamp").invoke(exchange))
        }

        // Verify an encrypted, separate Preview credential without performing any network calls.
        val preview = uk.co.traynor.privategallery.intelligence.OwnerPreviewStore(context)
        val syntheticToken = "0".repeat(64)
        if (seed) {
            assertFalse(preview.isEnabled())
            assertNull(preview.read())
            preview.enable(syntheticToken)
            before.put("previewFixtureRevision", checkNotNull(preview.read()).revision.toString())
        }
        val connection = checkNotNull(preview.read())
        assertTrue(preview.isEnabled() && preview.isCurrent(connection))
        assertTrue("Preserve separate encrypted Preview credential", connection.token == syntheticToken)
        assertEquals(before.getString("previewFixtureRevision"), connection.revision.toString())
    }

"""


def create_fixture(baseline_root):
    fixture = Path(FIXTURE)
    require(digest(fixture) == FIXTURE_DIGEST, "Archived fixture differs from its review")
    data = fixture.read_bytes()
    for old, new, count in (
        (b"Build 95 to versionCode 96", b"Build 97 to versionCode 98", 1),
        (b"baselineCode == 95 && candidateCode == 96", b"baselineCode == 97 && candidateCode == 98", 1),
        (b"Build95", b"Build97", 3),
        (b"v1.7.50/", b"v1.7.52/", 1),
        (b"assertEquals(96L,available", b"assertEquals(98L,available", 1),
        (b'assertEquals("1.7.50",available', b'assertEquals("1.7.52",available', 1),
        (b"original=95 candidate=96", b"original=97 candidate=98", 1),
        (b"    private fun home2State(", MEMORY_FIXTURE + b"    private fun home2State(", 1),
        (b"coreApplicationState85(space,seed);novaBrainConfiguration86(seed)",
         b"coreApplicationState85(space,seed);novaBrainConfiguration86(seed);assistantState98(space,before,seed)", 1),
    ):
        require(data.count(old) == count, f"Reviewed upgrade fixture anchor differs: {old.decode()}")
        data = data.replace(old, new)
    build = baseline_root / "app/build.gradle.kts"
    transformed, target = transform(data, build.read_bytes())
    path = baseline_root / "app/src/androidTest/java/uk/co/traynor/privategallery/upgrade/UpgradeFixtureTest.kt"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(transformed)
    build.write_bytes(target)


def require_upgrade(upgrade):
    required = {
        "result": "PASS", "source": CANDIDATE, "baseline_code": 97, "candidate_code": 98,
        "installed_code": 98, "certificate_sha256": CERTIFICATE, "baseline_sha256": BASELINE_DIGEST,
        "seed": "PASS", "verify": "PASS", "after_restart": "PASS", "transport": "controlled-loopback",
        "baseline_application": "authentic-unmodified", "discovery": "PASS",
        "download_and_verification": "PASS", "android_installer": "confirmed",
    }
    require(all(upgrade.get(key) == value for key, value in required.items())
            and re.fullmatch("[0-9a-f]{64}", upgrade.get("apk_sha256", "")),
            "Missing original-key populated Build 97 to 98 updater and restart proof")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    tasks = parser.add_subparsers(dest="task", required=True)
    for name in ("authorize", "fixture"):
        tasks.add_parser(name).add_argument("path", type=Path)
    tasks.add_parser("recheck")
    for name in ("baseline", "candidate"):
        task = tasks.add_parser(name)
        task.add_argument("path", type=Path)
        task.add_argument("tools", type=Path)
    marker = tasks.add_parser("marker")
    marker.add_argument("parent")
    args = parser.parse_args()
    if args.task == "authorize":
        authorize(args.path)
    elif args.task == "recheck":
        recheck_authorization()
    elif args.task == "fixture":
        create_fixture(args.path)
    elif args.task == "baseline":
        fetch_baseline(args.path, args.tools)
    elif args.task == "candidate":
        print(inspect(args.path, args.tools, 98, "1.7.52"))
    else:
        require(re.fullmatch("[0-9a-f]{40}", args.parent), "Rehearsal control parent must be an exact SHA")
        print(json.dumps(marker_for(args.parent), indent=2))


if __name__ == "__main__":
    main()
