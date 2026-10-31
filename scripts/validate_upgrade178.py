#!/usr/bin/env python3
"""Read-only preparation audit. Never builds, installs, signs, dispatches CI or approves release.

The disabled acceptance178 job stages the new fixture in its disposable checkout only
after building the candidate, then invokes verify_synthetic_upgrade178.sh with the
exact archived signed code47 APK. Never replace the historical fixture in the source
branch or run production operations based on this audit's exit status.
"""
import hashlib
import json
from pathlib import Path
import re
import sys

BASELINE_SHA = "04b91ae01c64323aac02975672d6046dfba62e30"
CERTIFICATE_SHA256 = "94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17"
PRESERVED = {
    "scripts/upgrade/UpgradeFixtureTest.kt": "04448cb6653d9bbd70236fdea841de10b82fe004060909586caf94d73f9e6db0",
    "scripts/verify_synthetic_upgrade.sh": "9649314a9ded7427a909fbe4de4920a8d744f10ee52603d7ef0735b9af64021d",
}


def audit(root: Path) -> dict:
    errors = []
    for relative, expected in PRESERVED.items():
        path = root / relative
        if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            errors.append(f"Historical upgrade source changed: {relative}")
    config = (root / "scripts/release_configuration.sh").read_text()
    match = re.search(r"refs/heads/release/1\.7\.8-private-apps\)\n(.*?)\n    ;;", config, re.S)
    block = match.group(1) if match else ""
    for required in ("release_min_sdk=37", "release_version=1.7.8", "release_code=48",
                     "baseline_version=1.7.8", "baseline_code=47", f"baseline_sha={BASELINE_SHA}",
                     '[[ "${PRIVATE_GALLERY_UPGRADE_BASELINE:-47}" == 47 ]]'):
        if required not in block:
            errors.append(f"Missing pinned release configuration: {required}")
    fixture = (root / "docs/release178/UpgradeFixtureTest.kt").read_text()
    pairs = re.findall(r"baselineCode == (\d+) && candidateCode == (\d+)", fixture)
    if pairs != [("47", "48")]:
        errors.append("Fixture must permit only code47 -> code48")
    required_checks = (
        'check(context.packageName == "uk.co.traynor.privategallery.hiddenprototype")',
        'check(!proof.exists())', 'assertEquals(baselineCode,', 'assertEquals(candidateCode,',
        'repository.moveToRecentlyDeleted(it)', 'repo.trash(hiddenDeletedIds)',
        'repository.retentionDays(60)', 'repo.retentionDays(15)',
        'assertEquals(60,repository.retentionDays())', 'assertEquals(15,repo.retentionDays())',
        'assertRecentlyDeleted(before.getJSONObject("primaryDeleted"),repository.recentlyDeleted(),repository.items())',
        'assertRecentlyDeleted(before.getJSONObject("hiddenDeleted"),repo.recentlyDeleted(),repo.items())',
        'assertEquals(VaultItemState.TRASHED,item.state)', 'item.deletedAtEpochMillis',
        'item.plaintextSha256', 'item.payloadNonce',
        'arguments.getString("restartCheck")=="true"',
        'Preserve encrypted file $path', 'Preserve Hidden encrypted file $path',
        'assertCiphertextAfterReads(before)', 'Preserve ciphertext after reads $field/$path',
        'candidatePresentationFiles', 'candidateHiddenPresentationFiles',
        'primaryBrowser(key,false)', 'hiddenBrowser(hidden,false)',
        'HiddenCredentialStore(context).unlock("738291".toCharArray())',
        'PinVaultKeyStore(context).unlock("905184".toCharArray())',
        'repository.readForViewing(item)', 'repo.read(item.id)',
        'repository.favouriteCollection()', 'repo.favouriteCollection()',
        'assertDates(before.getJSONObject("primaryDates"),dateSnapshot(repository))',
        'assertDates(before.getJSONObject("hiddenDates"),dateSnapshot(repo))',
        'vpn.activeProfileId', 'AiCredentialStore(context).read("upgrade-fixture")',
        'keep_ai_edits_in_vault', 'background-timeout', 'hide-content', 'privacy-shield',
    )
    errors.extend(f"Missing fixture contract: {value}" for value in required_checks if value not in fixture)
    if fixture.count("createDistinctCopy=true") != 2:
        errors.append("Both vaults must seed distinct deleted copies alongside original live media")
    if any(token in fixture for token in ("restoreRecentlyDeleted(", "deletePermanently(", "deleteExpiredRecentlyDeleted(")):
        errors.append("Preservation fixture must not restore/purge baseline deleted records")
    return {
        "source_audit": "PREPARED" if not errors else "FAILED",
        "approval": "BLOCKED",
        "android_execution": "NOT_RUN",
        "original_key_upgrade": "NOT_RUN",
        "baseline_source": BASELINE_SHA,
        "baseline_code": 47,
        "candidate_code": 48,
        "required_certificate_sha256": CERTIFICATE_SHA256,
        "errors": errors,
        "remaining": [
            "Independent final source/security review and approval of the exact candidate and runner staging",
            "Compile fixture against frozen code47 baseline; compile candidate and Android tests",
            "Download pinned run41081513370 artifact12990222572; verify exact archive/APK hashes and original certificate before signer access",
            "On a disposable emulator, install exact archived code47 APK plus original-signed baseline instrumentation",
            "Seed code47, install candidate with adb install -r, verify, force-stop, verify with restartCheck=true",
            "Archive exact baseline/candidate source IDs, APK hashes, package/signature reports and all three passing fixture logs",
            "Review source-bound evidence before any release/signing acceptance claim",
        ],
    }


def main() -> int:
    try:
        result = audit(Path(__file__).resolve().parents[1])
    except (OSError, ValueError) as error:
        result = {"source_audit": "FAILED", "approval": "BLOCKED", "errors": [str(error)]}
    print(json.dumps(result, indent=2))
    return 0 if result["source_audit"] == "PREPARED" else 1


if __name__ == "__main__":
    sys.exit(main())
