#!/usr/bin/env python3
"""Authorize signing only the merged Wave A tree with a verified original-key rehearsal."""

import argparse
import json
import os
from pathlib import Path
import tempfile
import zipfile

from novaos_build97_gate import api, artifact, check_steps, git, OWNER, REPO, report_from, require, run_record
from novaos_build99_rehearsal import (
    BRANCH, CODE, MARKER as REHEARSAL_MARKER, PR, VERSION, WORKFLOW,
    checksums, require_upgrade, sha, verify_candidate_configuration, verify_owner_push,
    verify_workflow_run, write_approval,
)


MARKER = ".novaos-build99-production-authorization"
JOB = "Build 99 protected"
REHEARSAL_ARTIFACT = "novaos-build99-rehearsal-receipts"
REHEARSAL_STEPS = {
    "Validate Build 99 owner authorization and exact evidence before signer access",
    "Recheck owner authorization and unsigned fixture before signer access",
    "Verify original production identity and sign the frozen Build 99 source",
    "Verify the signed Build 99 APK and protected packaging",
    "Rehearse populated Build 97 to 99 upgrade and restart through the in-app updater",
    "Retain only non-sensitive Build 99 verification receipts",
    "Remove all Build 99 key and APK material",
}


def verify_manifest(approved, parent):
    require(
        set(approved) == {
            "schema", "mode", "main_parent", "candidate", "candidate_tree", "source_tree", "version",
            "version_code", "rehearsal", "development", "full_security", "inputs_sha256",
        }
        and approved["schema"] == 1 and approved["mode"] == "production"
        and approved["main_parent"] == parent and sha(parent, 40)
        and approved["version"] == VERSION and approved["version_code"] == CODE
        and all(sha(approved[field], 40) for field in ("candidate", "candidate_tree", "source_tree"))
        and approved["inputs_sha256"] == checksums(),
        "Signing manifest does not bind the exact version, source and trusted workflow inputs",
    )
    rehearsal = approved["rehearsal"]
    require(
        set(rehearsal) == {"run", "attempt", "artifact", "sha256", "approval_commit"}
        and all(type(rehearsal[field]) is int and rehearsal[field] > 0 for field in ("run", "attempt", "artifact"))
        and rehearsal["attempt"] == 1 and sha(rehearsal["sha256"], 64)
        and sha(rehearsal["approval_commit"], 40),
        "Signing manifest requires a specific owner rehearsal attempt and artifact digest",
    )


def verify_merge(approved, source):
    candidate = approved["candidate"]
    parent = approved["rehearsal"]["approval_commit"]
    pr = api(f"pulls/{PR}")
    require(
        pr["number"] == PR and pr["state"] == "closed" and pr["merged"] is True
        and pr["head"]["sha"] == candidate and pr["head"]["ref"] == BRANCH
        and pr["head"]["repo"]["full_name"] == REPO and pr["base"]["ref"] == "main"
        and pr["base"]["repo"]["full_name"] == REPO and pr["merge_commit_sha"] == source,
        "Main source must be the exact reviewed merge of Wave A PR #87",
    )
    require(
        git("rev-list", "--parents", "-n", "1", source).split() == [source, parent, candidate]
        and git("rev-parse", source + "^{tree}") == approved["source_tree"]
        and git("rev-parse", candidate + "^{tree}") == approved["candidate_tree"]
        and git("diff", "--name-status", "--no-renames", candidate, source) == "A\t" + REHEARSAL_MARKER,
        "Merged tree differs from reviewed Wave A beyond its rehearsal authorization record",
    )
    verify_candidate_configuration(candidate, approved["candidate_tree"])


def verify_signed_rehearsal(approved, output):
    rehearsal = approved["rehearsal"]
    approval_commit = rehearsal["approval_commit"]
    candidate = approved["candidate"]
    run = run_record(rehearsal["run"], approval_commit, "main", WORKFLOW, "push")
    require(
        run["run_attempt"] == rehearsal["attempt"] and run["actor"]["id"] == OWNER
        and run["triggering_actor"]["id"] == OWNER,
        "Rehearsal run was not the original authenticated owner push",
    )
    check_steps(run, JOB, REHEARSAL_STEPS)
    expected = {
        **json.loads(git("show", f"{approval_commit}:{REHEARSAL_MARKER}")),
        "approval_commit": approval_commit,
        "approval_run": rehearsal["run"],
        "approving_id": OWNER,
    }
    require(
        expected["mode"] == "rehearsal" and expected["candidate"] == candidate
        and expected["candidate_tree"] == approved["candidate_tree"]
        and expected["version_name"] == VERSION and expected["version_code"] == CODE
        and expected["development"] == approved["development"]
        and expected["full_security"] == approved["full_security"]
        and expected["inputs_sha256"] == approved["inputs_sha256"],
        "Original-key rehearsal must bind the exact versioned application and CI receipts",
    )
    with tempfile.TemporaryDirectory(dir=output.parent) as scratch:
        retained = Path(scratch) / "rehearsal.zip"
        artifact(rehearsal["run"], approval_commit, rehearsal["artifact"], rehearsal["sha256"], REHEARSAL_ARTIFACT, retained)
        with zipfile.ZipFile(retained) as archive:
            require(json.loads(report_from(archive, "owner-approval.json")) == expected,
                    "Retained rehearsal authority differs from its owner marker")
            require_upgrade(json.loads(report_from(archive, "build99-upgrade.json")), candidate)
            for phase in ("seed", "verify", "after-restart"):
                require(report_from(archive, f"from97/upgrade-{phase}.txt").strip() == "OK (1 test)",
                        f"Missing populated upgrade phase {phase}")


def read_marker():
    marker_path = Path(MARKER)
    require(marker_path.is_file() and not marker_path.is_symlink(), "Missing Build 99 signing authorization")
    return json.loads(marker_path.read_text())


def authorize(output):
    env = os.environ
    approval_commit, source = verify_owner_push(env, json.loads(Path(env["GITHUB_EVENT_PATH"]).read_text()), MARKER)
    run = verify_workflow_run(env, approval_commit)
    approved = read_marker()
    verify_manifest(approved, source)
    verify_merge(approved, source)
    verify_signed_rehearsal(approved, output)
    write_approval(output, {
        **approved,
        "source": source,
        "regression_run": approved["development"]["run"],
        "full_run": approved["full_security"]["run"],
        "rehearsal_run": approved["rehearsal"]["run"],
    }, approval_commit, run, source, "production")


def recheck():
    env = os.environ
    _, source = verify_owner_push(env, json.loads(Path(env["GITHUB_EVENT_PATH"]).read_text()), MARKER)
    approved = read_marker()
    verify_manifest(approved, source)
    verify_merge(approved, source)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("authorize").add_argument("path", type=Path)
    commands.add_parser("recheck")
    args = parser.parse_args()
    if args.command == "authorize":
        authorize(args.path)
    else:
        recheck()


if __name__ == "__main__":
    main()
