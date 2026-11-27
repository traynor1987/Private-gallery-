#!/usr/bin/env python3
"""Authorize the exact Build 99 pre-merge, receipt-only original-key rehearsal."""

import argparse
import json
import os
from pathlib import Path
import re
import tempfile
import xml.etree.ElementTree as ET
import zipfile

from novaos_build97_gate import (
    CERTIFICATE, FIXTURE, FIXTURE_DIGEST, OWNER, REPO, api, artifact,
    check_steps, digest, git, report_from, require, run_record, write_json,
)
from novaos_build98_rehearsal import BASELINE, BASELINE_DIGEST, fetch_baseline
from verify_code80_apk_identity import inspect
from verify_compiled_upgrade1711 import transform


PR = 87
BRANCH = "feature/novaos2-wave-a"
VERSION = "2.0.0"
CODE = 99
CONTROL_BASE = "b19610a7efb3df53edcec2610402c69ea8e1199c"
MARKER = ".novaos-build99-rehearsal-authorization"
WORKFLOW = ".github/workflows/novaos-build99-protected.yml"
DEVELOPMENT = ".github/workflows/novaos2-api37.yml"
FULL_SECURITY = ".github/workflows/novaos99-full.yml"
CONTROL_CHANGES = {
    WORKFLOW, "scripts/novaos_build99_rehearsal.py", "scripts/run_build99_upgrade.sh",
    "scripts/novaos_build99_production.py", "scripts/rehearse_in_app_update85.py",
    ".github/workflows/novaos-publish-verified.yml", "scripts/publish_verified_novaos.py",
    "docs/build85/verify_live_build84.py", "scripts/tests/test_novaos_build99_rehearsal.py",
    "scripts/tests/test_novaos_build99_production.py", "scripts/tests/test_rehearse_in_app_update85.py",
    "scripts/tests/test_publish_verified_novaos.py", "docs/novaos2/BUILD99_RELEASE.md",
    "scripts/novaos_build99_publication.py", "scripts/tests/test_novaos_build99_publication.py",
    "scripts/tests/test_live_update_driver.py", "scripts/verify_novaos_release_assets.py",
}
REQUIRED_CONTROLS = {
    WORKFLOW, "scripts/novaos_build99_rehearsal.py", "scripts/run_build99_upgrade.sh",
    "scripts/novaos_build99_production.py", "scripts/rehearse_in_app_update85.py",
    ".github/workflows/novaos-publish-verified.yml", "scripts/publish_verified_novaos.py",
    "docs/build85/verify_live_build84.py", "scripts/novaos_build99_publication.py",
    "scripts/verify_novaos_release_assets.py",
}
TRUSTED_INPUTS = CONTROL_CHANGES | {
    FIXTURE, "scripts/novaos_build97_gate.py", "scripts/novaos_build98_rehearsal.py",
    "scripts/verify_backup_exclusions.py", "scripts/verify_evidence179_distribution.py",
    "scripts/verify_code80_apk_identity.py", "scripts/verify_code79_baseline.py",
    "scripts/verify_compiled_upgrade1711.py", "scripts/verify_signed_upgrade1711.py",
    "scripts/verify_android17_sdk.py", "scripts/prime_gradle95_ci.sh",
}
DEVELOPMENT_STEPS = {
    "Set up Android 17 and record source", "Check Sanctuary and protected storage boundaries",
    "Compile the debug app and run JVM tests", "Run API 37 feature regressions",
    "Run actions/upload-artifact@v4",
}
FULL_STEPS = {
    "Validate frozen Build 99 source", "Run host security gates",
    "Build and check exact-source debug artifacts", "Start disposable security fixtures",
    "Run complete Android 17 security regression", "Verify complete Build 99 evidence",
    "Remove disposable security fixtures", "Upload Build 99 full security evidence",
}


def sha(value, size=40):
    return isinstance(value, str) and re.fullmatch(f"[0-9a-f]{{{size}}}", value) is not None


def checksums():
    hashes = {}
    for name in sorted(TRUSTED_INPUTS):
        path = Path(name)
        require(not path.is_symlink(), f"Trusted control is a symlink: {name}")
        if name in CONTROL_CHANGES - REQUIRED_CONTROLS and not path.exists():
            continue
        require(path.is_file(), f"Missing trusted control {name}")
        hashes[name] = digest(path)
    return hashes


def verify_evidence_identity(record):
    require(isinstance(record, dict) and set(record) == {"run", "attempt", "artifact", "sha256"}
            and all(type(record[name]) is int and record[name] > 0 for name in ("run", "attempt", "artifact"))
            and sha(record["sha256"], 64), "Workflow evidence must name an exact run, attempt and archive hash")


def marker_for(parent, candidate, evidence):
    require(sha(parent) and sha(candidate), "Rehearsal parent and candidate must be exact SHAs")
    require(isinstance(evidence, dict) and set(evidence) == {"development", "full_security"},
            "Rehearsal requires exact development and full security evidence")
    for record in evidence.values():
        verify_evidence_identity(record)
    return {
        "schema": 1, "mode": "rehearsal", "main_parent": parent, "candidate": candidate,
        "candidate_tree": git("rev-parse", candidate + "^{tree}"), "version_name": VERSION,
        "version_code": CODE, **evidence, "inputs_sha256": checksums(),
    }


def verify_owner_push(env, event, marker_name):
    require(env.get("GITHUB_REPOSITORY") == REPO and env.get("GITHUB_REF") == "refs/heads/main"
            and env.get("GITHUB_EVENT_NAME") == "push" and env.get("GITHUB_RUN_ATTEMPT") == "1"
            and env.get("GITHUB_ACTOR_ID") == str(OWNER) and env.get("GITHUB_TRIGGERING_ACTOR") == "traynor1987",
            "Build 99 requires an authenticated owner main push")
    head, parent = env["GITHUB_SHA"], event["before"]
    require(sha(head) and sha(parent) and event["sender"]["id"] == OWNER and event["forced"] is False
            and event["created"] is False and event["after"] == head and git("rev-parse", "HEAD") == head
            and git("rev-list", "--parents", "-n", "1", head).split() == [head, parent]
            and git("diff", "--name-only", parent, head) == marker_name,
            "Authorization must be an owner marker-only commit on main")
    require(api("git/ref/heads/main")["object"]["sha"] == head, "Main moved after owner authorization")
    return head, parent


def verify_workflow_run(env, head):
    run_id = int(env["GITHUB_RUN_ID"])
    run = api(f"actions/runs/{run_id}")
    require(run["id"] == run_id and run["head_sha"] == head and run["head_branch"] == "main"
            and run["event"] == "push" and run["actor"]["id"] == OWNER and run["triggering_actor"]["id"] == OWNER
            and run["run_attempt"] == 1 and run["path"].split("@")[0] == WORKFLOW,
            "Build 99 run is not bound to the authenticated owner marker")
    return run_id


def verify_candidate(approved):
    candidate, tree = approved["candidate"], approved["candidate_tree"]
    pr = api(f"pulls/{PR}")
    require(pr["number"] == PR and pr["state"] == "open" and not pr["merged"]
            and pr["head"]["sha"] == candidate and pr["head"]["ref"] == BRANCH
            and pr["head"]["repo"]["full_name"] == REPO and pr["base"]["ref"] == "main"
            and pr["base"]["repo"]["full_name"] == REPO,
            "PR #87 is not the exact open same-repository candidate")
    verify_candidate_configuration(candidate, tree)


def verify_candidate_configuration(candidate, tree):
    require(sha(candidate) and sha(tree) and git("rev-parse", candidate + "^{tree}") == tree,
            "Candidate tree differs")
    baseline_build = git("show", f"{BASELINE}:app/build.gradle.kts")
    expected_build = baseline_build.replace("versionCode = 97", "versionCode = 99", 1).replace(
        'versionName = "1.7.51"', 'versionName = "2.0.0"', 1,
    )
    require(git("show", f"{candidate}:app/build.gradle.kts") == expected_build,
            "Candidate configuration differs from the reviewed API 37 version bump")
    require(git("diff", "--name-only", BASELINE, candidate, "--", "gradlew", "gradlew.bat",
                "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradle",
                "gradle.lockfile", "app/gradle.lockfile") == "",
            "Candidate changes executable Gradle controls or dependency pins")
    require(api("releases/latest")["tag_name"] == "v1.7.51"
            and api("git/ref/tags/v1.7.51")["object"]["sha"] == BASELINE
            and not any(item["ref"] == "refs/tags/v2.0.0" for item in api("git/matching-refs/tags/v2.0.0")),
            "Build 97 baseline or Build 99 version availability changed")


def verify_development_reports(path, approved):
    with zipfile.ZipFile(path) as archive:
        require(report_from(archive, "novaos2/source.txt").splitlines()
                == [approved["candidate"], approved["candidate_tree"]], "Wave A source and tree evidence differs")
        sdk = json.loads(report_from(archive, "novaos2/android17-sdk.json"))
        require(sdk["api_level"] == 37 and sdk["preview"] is False
                and report_from(archive, "novaos2/sdk.txt").strip() == "37",
                "Wave A did not verify Android 17 SDK and runtime")
        transcript = report_from(archive, "novaos2/instrumentation.txt")
        summary = re.findall(r"^OK \((\d+) tests\)\s*$", transcript, re.M)
        require(len(summary) == 1 and int(summary[0]) >= 33
                and not re.search(r"^INSTRUMENTATION_STATUS_CODE: -[1-4]\s*$", transcript, re.M),
                "Wave A instrumentation results are incomplete or failed")
        for group in ("LauncherThemesStudioUiTest", "ControlCentreAndroid17Test", "NovaWaveAIntegrationTest",
                      "HiddenCredentialSeparationTest", "HiddenDiscoverabilityRegressionTest",
                      "SanctuaryLauncherPolicy70Test", "Updater85RuntimeTest", "Updater86UiTest"):
            require(group in transcript, f"Missing Wave A Android 17 case group: {group}")
        for folder in ("guardian-notification-pipeline-93", "guardian-fold-notification"):
            require("1/1 PASS" in report_from(archive, folder + "/result.txt"), f"Failed {folder}")
        restart = dict(line.split("=", 1) for line in report_from(archive, "launcher-process-70/receipt.txt").splitlines())
        require(restart["source"] == approved["candidate"] and restart["api"] == "37"
                and restart["result"] == "PASS" and restart["old_pid"].isdigit() and restart["new_pid"].isdigit()
                and restart["old_pid"] != restart["new_pid"], "Launcher restart evidence differs")
        tests = 0
        for name in archive.namelist():
            if name.endswith(".xml") and "testDebugUnitTest/" in name:
                suite = ET.fromstring(archive.read(name))
                require(all(int(suite.get(key, "0")) == 0 for key in ("skipped", "errors", "failures")),
                        f"JVM report failed: {name}")
                tests += int(suite.get("tests", "0"))
        require(tests >= 795, f"Expected all Wave A JVM tests, found {tests}")


def verify_full_reports(path, approved):
    with zipfile.ZipFile(path) as archive:
        report = json.loads(report_from(archive, "full99/receipt.json"))
        required = {"source_sha": approved["candidate"], "source_tree": approved["candidate_tree"],
                    "version_code": CODE, "api_level": 37, "decision": "PASS"}
        require(all(report.get(key) == value for key, value in required.items())
                and report_from(archive, "full99/source-sha.txt").strip() == approved["candidate"]
                and report_from(archive, "full99/source-tree.txt").strip() == approved["candidate_tree"],
                "Full Android 17 security receipt does not match the frozen Build 99 tree")


def verify_regressions(approved, output):
    lanes = (
        ("development", DEVELOPMENT, "verify", DEVELOPMENT_STEPS,
         "novaos2-api37-evidence", verify_development_reports),
        ("full_security", FULL_SECURITY, "Full Android 17 security", FULL_STEPS,
         "novaos99-full-evidence", verify_full_reports),
    )
    with tempfile.TemporaryDirectory(dir=output.parent) as scratch:
        for name, workflow, job, steps, archive_name, verify_reports in lanes:
            record = approved[name]
            verify_evidence_identity(record)
            run = run_record(record["run"], approved["candidate"], BRANCH, workflow, "push")
            require(run["run_attempt"] == record["attempt"], f"{name} workflow attempt differs")
            check_steps(run, job, steps)
            evidence = Path(scratch) / f"{name}.zip"
            artifact(record["run"], approved["candidate"], record["artifact"], record["sha256"], archive_name, evidence)
            verify_reports(evidence, approved)


def read_marker(name=MARKER):
    marker = Path(name)
    require(marker.is_file() and not marker.is_symlink(), "Missing authenticated Build 99 authorization")
    return json.loads(marker.read_text())


def write_approval(output, approval, head, run, source, mode):
    write_json(output / "owner-approval.json", {
        **approval, "approval_commit": head, "approval_run": run, "approving_id": OWNER,
    })
    with Path(os.environ["GITHUB_OUTPUT"]).open("a") as result:
        result.write(f"source={source}\nmode={mode}\n")


def verify_authority(env, event, approved):
    head, parent = verify_owner_push(env, event, MARKER)
    evidence = {name: approved[name] for name in ("development", "full_security")}
    require(approved == marker_for(parent, approved["candidate"], evidence),
            "Authorization does not bind the exact candidate, controls and CI evidence")
    common = git("merge-base", parent, approved["candidate"])
    # The controls-only merge commit may differ in identity from the common reviewed
    # control commit. Its complete tree must remain identical.
    require(git("rev-parse", common + "^{tree}") == git("rev-parse", parent + "^{tree}")
            and git("merge-base", common, CONTROL_BASE) == CONTROL_BASE,
            "Main or the Wave A base moved outside the reviewed rehearsal")
    changed = set(git("diff", "--name-only", CONTROL_BASE, parent).splitlines())
    require({WORKFLOW, "scripts/novaos_build99_rehearsal.py", "scripts/run_build99_upgrade.sh"} <= changed <= CONTROL_CHANGES,
            "Pre-merge main includes changes outside the reviewed release controls")
    require(git("diff", "--name-only", parent, approved["candidate"], "--", *sorted(TRUSTED_INPUTS)) == "",
            "Wave A changes a trusted release control after approval")
    return head


def authorize(output):
    env = os.environ
    approved = read_marker()
    head = verify_authority(env, json.loads(Path(env["GITHUB_EVENT_PATH"]).read_text()), approved)
    run = verify_workflow_run(env, head)
    verify_candidate(approved)
    verify_regressions(approved, output)
    write_approval(output, approved, head, run, approved["candidate"], "rehearsal")


def recheck_authorization():
    approved = read_marker()
    verify_authority(os.environ, json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text()), approved)
    verify_candidate(approved)


ASSISTANT_FIXTURE = b"""    private fun assistantState99(space: uk.co.traynor.privategallery.core.home2.SpaceContext, before: JSONObject, seed: Boolean) {
        val workspace = uk.co.traynor.privategallery.intelligence.IntelligenceWorkspace(space)
        val memory = uk.co.traynor.privategallery.intelligence.IntelligenceMemory(
            true, listOf(uk.co.traynor.privategallery.intelligence.IntelligenceExchange(
                "Synthetic Build 97 question", "Preserve this encrypted answer", false)))
        val preview = uk.co.traynor.privategallery.intelligence.OwnerPreviewStore(context)
        // Synthetic local settings only; the fixture never calls the preview service.
        val syntheticToken = "0".repeat(64)
        if (seed) {
            assertFalse(workspace.memory().enabled)
            assertFalse(preview.isEnabled())
            assertNull(preview.read())
            workspace.saveMemory(memory)
            preview.enable(syntheticToken)
            before.put("previewFixtureRevision", checkNotNull(preview.read()).revision.toString())
        }
        assertEquals(memory, workspace.memory())
        val connection = checkNotNull(preview.read())
        assertTrue(preview.isEnabled() && preview.isCurrent(connection))
        assertTrue("Preserve the encrypted Preview credential", connection.token == syntheticToken)
        assertEquals(before.getString("previewFixtureRevision"), connection.revision.toString())
    }

"""


HOME_FIXTURE = b"""    @Test fun verifySelectedDefaultHome() {
        guard()
        val roles = checkNotNull(context.getSystemService(RoleManager::class.java))
        assertTrue("Keep the explicitly selected NovaOS Home role", roles.isRoleHeld(RoleManager.ROLE_HOME))
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val flags = PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
        val resolved = checkNotNull(context.packageManager.resolveActivity(home, flags)).activityInfo
        assertEquals(context.packageName, resolved.packageName)
        assertEquals(context.packageName + ".launcher.NovaLauncherActivity", resolved.name)
    }

    private fun guardianPreferences99(seed: Boolean) {
        val notifications = uk.co.traynor.privategallery.launcher.GuardianSafeNotifications
        val prefs = notifications.prefs(context)
        val mode = uk.co.traynor.privategallery.core.guardian.GuardianPresentationMode.GUARDIAN
        if (seed) {
            check(prefs.edit().putString("presentation_mode", mode.name)
                .putBoolean("alert_validated_preview", false).commit())
        }
        assertEquals("Preserve the selected Guardian mode", mode, notifications.presentationMode(context))
        assertFalse("Preserve private Guardian preview setting", notifications.previewsEnabled(context))
    }

"""


WIDGETS_AND_APPEARANCE = b"""            val widgets = listOf(
                uk.co.traynor.privategallery.launcher.LauncherWidget(741, "com.android.settings/.Settings", false, 1),
                uk.co.traynor.privategallery.launcher.LauncherWidget(742, "com.android.settings/.Settings", true, 2))
            val appearance = uk.co.traynor.privategallery.launcher.LauncherAppearance(
                wallpaper = uk.co.traynor.privategallery.launcher.LauncherWallpaper.MIDNIGHT,
                theme = uk.co.traynor.privategallery.launcher.LauncherTheme.DARK,
                accent = uk.co.traynor.privategallery.launcher.LauncherAccent.AMBER,
                iconSize = uk.co.traynor.privategallery.launcher.LauncherIconSize.LARGE)
            val drawer = uk.co.traynor.privategallery.launcher.LauncherDrawerOptions(
                orientation = uk.co.traynor.privategallery.launcher.LauncherDrawerOrientation.HORIZONTAL,
                folders = false)
            val legacy = NovaLauncherState(configured = true, separateDisplays = true, cover = cover,
                inner = inner, dockOptions = dockOptions, widgets = widgets, appearance = appearance, drawer = drawer)
"""


SAVED_LAUNCHER_CHECKS = b"""
        assertEquals("Preserve widget placements on both layouts", listOf(
            uk.co.traynor.privategallery.launcher.LauncherWidget(741, "com.android.settings/.Settings", false, 1),
            uk.co.traynor.privategallery.launcher.LauncherWidget(742, "com.android.settings/.Settings", true, 2)), actual.widgets)
        assertEquals("MIDNIGHT", actual.appearance.wallpaper.name)
        assertEquals("DARK", actual.appearance.theme.name)
        assertEquals("AMBER", actual.appearance.accent.name)
        assertEquals("LARGE", actual.appearance.iconSize.name)
        assertEquals("HORIZONTAL", actual.drawer.orientation.name)
        assertFalse("Preserve App Library folder preference", actual.drawer.folders)
        if (!seed) {
            val studio = actual.appearance.javaClass.getMethod("getStudio").invoke(actual.appearance)
            val look = studio.javaClass.getMethod("getLook").invoke(studio) as Enum<*>
            assertEquals("Preserve default Nova style for a migrated launcher", "NOVA", look.name)
        }
"""


def fixture_replacements():
    return (
        (b"Build 95 to versionCode 96", b"Build 97 to versionCode 99", 1),
        (b"baselineCode == 95 && candidateCode == 96", b"baselineCode == 97 && candidateCode == 99", 1),
        (b"Build95", b"Build97", 3),
        (b"v1.7.50/", b"v2.0.0/", 1),
        (b"assertEquals(96L,available", b"assertEquals(99L,available", 1),
        (b'assertEquals("1.7.50",available', b'assertEquals("2.0.0",available', 1),
        (b"original=95 candidate=96", b"original=97 candidate=99", 1),
        (b"    private fun home2State(", ASSISTANT_FIXTURE + b"    private fun home2State(", 1),
        (b"coreApplicationState85(space,seed);novaBrainConfiguration86(seed)",
         b"coreApplicationState85(space,seed);novaBrainConfiguration86(seed);assistantState99(space,before,seed)", 1),
        (b"    private fun launcherLayouts(", HOME_FIXTURE + b"    private fun launcherLayouts(", 1),
        (b"            val legacy = NovaLauncherState(configured = true, separateDisplays = true, cover = cover, inner = inner, dockOptions = dockOptions)\n",
         WIDGETS_AND_APPEARANCE, 1),
        (b'dockOptions, actual.dockOptions)', b'dockOptions, actual.dockOptions)' + SAVED_LAUNCHER_CHECKS, 1),
        (b"        sanctuaryPolicy(seed = true)", b"        sanctuaryPolicy(seed = true)\n        guardianPreferences99(seed = true)", 1),
        (b"        sanctuaryPolicy(seed = false)", b"        sanctuaryPolicy(seed = false)\n        guardianPreferences99(seed = false)", 1),
    )


def create_fixture(baseline_root):
    archived = Path(FIXTURE)
    require(digest(archived) == FIXTURE_DIGEST, "Archived fixture differs from its review")
    data = archived.read_bytes()
    for old, new, count in fixture_replacements():
        require(data.count(old) == count, f"Reviewed upgrade fixture anchor differs: {old.decode()}")
        data = data.replace(old, new)
    build = baseline_root / "app/build.gradle.kts"
    transformed, target = transform(data, build.read_bytes())
    path = baseline_root / "app/src/androidTest/java/uk/co/traynor/privategallery/upgrade/UpgradeFixtureTest.kt"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(transformed)
    build.write_bytes(target)


def require_upgrade(upgrade, source):
    required = {
        "result": "PASS", "source": source, "baseline_code": 97, "candidate_code": CODE,
        "installed_code": CODE, "certificate_sha256": CERTIFICATE, "baseline_sha256": BASELINE_DIGEST,
        "seed": "PASS", "verify": "PASS", "after_restart": "PASS", "transport": "controlled-loopback",
        "baseline_application": "authentic-unmodified", "discovery": "PASS",
        "download_and_verification": "PASS", "android_installer": "confirmed", "default_home_retention": "PASS", "anti_downgrade": "PASS",
    }
    require(all(upgrade.get(key) == value for key, value in required.items())
            and sha(upgrade.get("apk_sha256"), 64),
            "Missing original-key populated Build 97 to 99 updater, default Home or restart proof")


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
    marker.add_argument("candidate")
    marker.add_argument("evidence", type=Path)
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
        print(inspect(args.path, args.tools, CODE, VERSION))
    else:
        print(json.dumps(marker_for(args.parent, args.candidate, json.loads(args.evidence.read_text())), indent=2))


if __name__ == "__main__":
    main()
