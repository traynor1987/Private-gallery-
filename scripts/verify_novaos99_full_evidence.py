#!/usr/bin/env python3
"""Require a complete, zero-skip Android 17 result from the frozen Build 99 tree."""

import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

from verify_android17_media_environment import validate as validate_media
from verify_release1711_gate import restart_cases

PACKAGE = "uk.co.traynor.privategallery"
BROWSER = f"{PACKAGE}.core.browser.hidden"
LAUNCHER = f"{PACKAGE}.launcher.LauncherProcess70Test"
BACKUP = f"{PACKAGE}.core.backup.BackupProcessRestartTest"
GUARDIAN = f"{PACKAGE}.core.guardian"
VPN_CLASSES = (f"{BROWSER}.HiddenVpnLive1762Test", f"{BROWSER}.HiddenVpnNetworkLoss1762Test")
TOR_CLASS = f"{BROWSER}.NativeTorIsolation1762Test"
GUARDIAN_REPORTS = {
    f"{GUARDIAN}.GuardianNotificationPipeline93Test": "guardian-notification-pipeline-93",
    f"{GUARDIAN}.GuardianFoldNotificationTest": "guardian-fold-notification",
}
LAUNCHER_CASES = {
    f"{LAUNCHER}#seedConfiguredLauncher",
    f"{LAUNCHER}#verifyConfiguredLauncherAfterProcessDeath",
}
BACKUP_CASES = {
    f"{BACKUP}#prepareBoundaryAndAwaitHostTermination",
    f"{BACKUP}#verifyRecoveryInNewProcess",
}
TOR_CONTROL_CASES = {
    f"{BROWSER}.HiddenTorBootstrapTest#packagedTorBootstrapsInPrivateProcessAndStopsWhenReleased",
    f"{BROWSER}.HiddenRuntimeFailureTest#nativeUnixConnectTimeoutClosesPeerWithoutFallback",
    f"{BROWSER}.HiddenRuntimeFailureTest#revokedNativeAuthorityClosesStalledUnixPeerAndDeniesReuse",
}
MANIFEST = Path(__file__).with_name("evidence179_cases.json")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def manifest_cases():
    cases = json.loads(MANIFEST.read_text())
    identities = {f'{case["classname"]}#{case["name"]}' for case in cases}
    require(identities and len(identities) == len(cases), "Invalid Evidence Locker test manifest")
    return identities


def fixture_selectors():
    return set(VPN_CLASSES) | {TOR_CLASS, LAUNCHER, BACKUP} | set(GUARDIAN_REPORTS)


def discovered_cases(discovery):
    cases = set()
    status = {}
    for line in discovery.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, _, value = line.removeprefix("INSTRUMENTATION_STATUS: ").partition("=")
            status[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            if line == "INSTRUMENTATION_STATUS_CODE: 1":
                identity = f'{status["class"]}#{status["test"]}'
                require(identity.startswith(f"{PACKAGE}."), f"Unexpected Android test identity {identity}")
                require(identity not in cases, f"Duplicate discovered test {identity}")
                cases.add(identity)
            status.clear()
    require(cases and "INSTRUMENTATION_CODE: -1" in discovery, "Incomplete Android test discovery")
    require(re.search(rf"(?m)^OK \({len(cases)} tests?\)\s*$", discovery), "Android discovery count mismatch")
    return cases


def xml_cases(directory):
    reports = sorted(directory.rglob("TEST-*.xml"))
    require(reports, f"No test reports in {directory}")
    identities = set()
    for report in reports:
        suite = ET.parse(report).getroot()
        cases = list(suite.iter("testcase"))
        require(int(suite.get("tests", len(cases))) == len(cases), f"Invalid test count in {report}")
        require(not any(int(suite.get(key, 0)) for key in ("failures", "errors", "skipped")),
                f"Failed, errored or skipped tests in {report}")
        for case in cases:
            identity = f'{case.attrib["classname"]}#{case.attrib["name"]}'
            require(identity not in identities, f"Duplicate executed test {identity}")
            require(all(case.find(tag) is None for tag in ("failure", "error", "skipped")), f"Non-passing test {identity}")
            identities.add(identity)
    require(identities, f"No passing tests in {directory}")
    return identities


def exact_cases(actual, expected, scope):
    missing = sorted(expected - actual)
    unexpected = sorted(actual - expected)
    require(not missing and not unexpected, f"{scope}: missing={missing}; unexpected={unexpected}")


def same_text(path, value):
    require(path.read_text().strip() == value, f"Unexpected {path}")


def passed_one(path):
    text = path.read_text()
    require(re.search(r"(?m)^OK \(1 test\)\s*$", text), f"Missing process verification in {path}")
    require(not re.search(r"(?m)^INSTRUMENTATION_STATUS_CODE: -[1-4]\s*$", text), f"Instrumentation failed in {path}")


def verify(build, source, tree):
    require(re.fullmatch(r"[a-f0-9]{40}", source) and re.fullmatch(r"[a-f0-9]{40}", tree), "Bad source identity")
    out = build / "full99"
    same_text(out / "source-sha.txt", source)
    same_text(out / "source-tree.txt", tree)
    same_text(out / "runtime-exit-status.txt", "0")
    same_text(out / "sdk.txt", "37")
    same_text(out / "debug-package.txt", f"{PACKAGE}.hiddenprototype")
    sdk = json.loads((out / "android17-sdk.json").read_text())
    require(sdk["api_level"] == 37 and sdk["preview"] is False, "Invalid Android SDK evidence")
    for phase in ("before", "after"):
        validate_media(json.loads((out / f"media-{phase}.json").read_text()), source)

    inventory = discovered_cases((out / "discovery.txt").read_text())
    selectors = fixture_selectors()
    evidence_expected = manifest_cases()
    excluded = {identity for identity in inventory if identity.partition("#")[0] in selectors} | evidence_expected
    require(excluded <= inventory, "A dedicated fixture case is missing from Android discovery")
    broad = xml_cases(out / "broad-results")
    exact_cases(broad, inventory - excluded, "Broad Android")
    require(TOR_CONTROL_CASES <= broad, "Missing Tor bootstrap, natural CONNECT timeout or revocation assertion")
    browser_bootstrap = [
        path.read_text()
        for path in (out / "broad-results").rglob("logcat-*HiddenTorBootstrapTest-*.txt")
    ]
    observations = "\n".join(browser_bootstrap)
    require("phase=subscribed" in observations and "readerAlive=false" in observations
            and "readerAlive=true" not in observations, "Missing Tor control observer lifecycle")

    routes = build / "hidden-space" / "routes"
    same_text(routes / "source-sha.txt", source)
    verified = set(broad)
    for fixture, classes in (("vpn", VPN_CLASSES), ("tor", (TOR_CLASS,))):
        cases = xml_cases(routes / fixture)
        exact_cases(cases, {case for case in inventory if case.partition("#")[0] in classes}, f"Native {fixture}")
        require((routes / f"{fixture}-packet-result.txt").read_text().startswith("PASS:"), f"Missing observed {fixture} routing")
        verified.update(cases)

    evidence = build / "evidence179"
    same_text(evidence / "source-sha.txt", source)
    owned_route_proof = json.loads((evidence / "route-proof.json").read_text())
    require(owned_route_proof["source_sha"] == source and owned_route_proof["decision"] == "PASS"
            and owned_route_proof["original_exit_status"] == 0, "Missing exact Evidence Locker owned route proof")
    evidence_cases = xml_cases(evidence / "android-results")
    exact_cases(evidence_cases, evidence_expected, "Evidence Locker Android")
    require(owned_route_proof["android_cases"] == len(evidence_cases), "Evidence Locker route case count mismatch")
    verified.update(evidence_cases)

    process = build / "home2-evidence"
    launcher = process / "launcher-process-70"
    launcher_proof = dict(line.split("=", 1) for line in (launcher / "receipt.txt").read_text().splitlines())
    require(launcher_proof["source"] == source and launcher_proof["api"] == "37" and launcher_proof["result"] == "PASS"
            and launcher_proof["old_pid"].isdigit() and launcher_proof["new_pid"].isdigit()
            and launcher_proof["old_pid"] != launcher_proof["new_pid"], "Missing two-process launcher proof")
    for phase in ("seed", "verify"):
        passed_one(launcher / f"{phase}.txt")
    verified.update(LAUNCHER_CASES)

    backup = {p.name: p.read_bytes() for p in (process / "backup-restart62").iterdir()}
    backup_boundaries = restart_cases(backup, source)
    require(backup_boundaries == 8, "Missing all eight backup termination boundaries")
    verified.update(BACKUP_CASES)

    for fixture, folder in GUARDIAN_REPORTS.items():
        guardian = process / folder
        cases = xml_cases(guardian / "reports")
        exact_cases(cases, {case for case in inventory if case.partition("#")[0] == fixture}, f"Guardian {folder}")
        require(len(cases) == 1, f"Missing Guardian replacement checkpoint: {folder}")
        verified.update(cases)

    for phase in ("process-seed", "process-verify", "camera-process-seed", "camera-process-verify"):
        passed_one(build / "acceptance-diagnostics" / "hidden-browser-preflight" / f"{phase}.txt")

    exact_cases(verified, inventory, "Full Android")
    jvm = xml_cases(build / "test-results" / "testDebugUnitTest")
    apk = build / "outputs" / "apk" / "debug" / "app-debug.apk"
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    same_text(routes / "apk-sha256.txt", f"{digest}  {apk}")
    receipt = dict(source_sha=source, source_tree=tree, version_code=99, api_level=37, decision="PASS",
                   android_cases=len(inventory), broad_cases=len(broad), jvm_cases=len(jvm),
                   debug_apk_sha256=digest, backup_restart_boundaries=backup_boundaries,
                   owned_route_cases=len(evidence_cases), guardian_checkpoints=len(GUARDIAN_REPORTS))
    (out / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps(receipt, indent=2))


if __name__ == "__main__":
    if sys.argv[1:] == ["--exclusions"]:
        print(",".join(sorted(fixture_selectors() | manifest_cases())))
    elif len(sys.argv) == 4:
        verify(Path(sys.argv[1]), *sys.argv[2:])
    else:
        raise SystemExit("Usage: verify_novaos99_full_evidence.py BUILD SOURCE_SHA TREE_SHA")
