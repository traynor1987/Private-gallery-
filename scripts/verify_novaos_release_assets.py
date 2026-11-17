#!/usr/bin/env python3
"""Check the staged stable release against the protected Android upgrade evidence."""

import argparse
import json
from pathlib import Path
import re
import subprocess

from verify_code79_baseline import RECEIPT
from verify_code80_apk_identity import inspect

APK_NAME = "private-gallery-release.apk"


def text(path):
    return path.read_text().strip()


def verify(args):
    if not re.fullmatch(r"v\d+(?:\.\d+){2,3}", args.tag):
        raise ValueError("A numbered stable release tag is required")
    if not re.fullmatch(r"[a-f0-9]{40}", args.source):
        raise ValueError("A frozen source commit is required")
    resolved = subprocess.check_output(
        ["git", "rev-parse", "--verify", f"refs/tags/{args.tag}^{{commit}}"], text=True,
    ).strip()
    if resolved != args.source:
        raise ValueError("Release tag does not resolve to verified source")
    manifest = json.loads(text(args.assets / f"{APK_NAME.removesuffix('.apk')}.metadata.json"))
    apk = args.assets / APK_NAME
    version = args.tag[1:]
    code = manifest.get("versionCode")
    if type(code) is not int or code <= args.baseline_code:
        raise ValueError("Production versionCode must advance")
    expected = inspect(apk, args.tools.resolve(), code, version)
    if manifest != {
        "package": RECEIPT["package"], "versionName": version, "versionCode": code,
        "apkSha256": expected, "certificateSha256": RECEIPT["original_certificate_sha256"],
    }:
        raise ValueError("Published metadata differs from the signed APK")
    if text(args.assets / f"{APK_NAME}.sha256") != f"{expected}  {APK_NAME}":
        raise ValueError("Published checksum differs from the signed APK")
    if text(args.evidence / "upgrade-candidate-source-sha.txt") != args.source:
        raise ValueError("Upgrade evidence belongs to another source")
    if text(args.evidence / "upgrade-candidate-sha256.txt").split()[0] != expected:
        raise ValueError("Upgrade evidence belongs to a different APK")
    for label in ("seed", "verify", "after-restart"):
        result = text(args.evidence / f"upgrade-{label}.txt")
        if len(re.findall(r"^OK \(1 test\)\s*$", result, re.M)) != 1 or any(
            message in result for message in ("FAILURES!!!", "INSTRUMENTATION_FAILED", "Process crashed", "FAILURE:")
        ):
            raise ValueError(f"Populated upgrade phase failed: {label}")
    if not text(args.evidence / "upgrade-result.txt").startswith("PASS:"):
        raise ValueError("Missing successful populated upgrade")
    if not text(args.notes):
        raise ValueError("Release notes are required")
    subprocess.run([str(args.tools.resolve() / "zipalign"), "-c", "-P", "16", "4", str(apk)], check=True)
    return expected


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("assets", "tools", "evidence", "notes"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--source", required=True)
    parser.add_argument("--baseline-code", type=int, required=True)
    args = parser.parse_args()
    try:
        print("Verified stable release APK SHA-256:", verify(args))
    except (OSError, ValueError, IndexError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Release gate failed: {error}\n")


if __name__ == "__main__":
    main()
