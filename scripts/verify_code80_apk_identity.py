#!/usr/bin/env python3
"""Read-only identity checks for the distributed Code 79 APK and a signed Code 80 candidate."""

import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

if __package__:
    from .verify_code79_baseline import APK_NAME, RECEIPT, verify
else:
    from verify_code79_baseline import APK_NAME, RECEIPT, verify


def inspect(apk: Path, tools: Path, code: int, version: str) -> str:
    def read(tool, *args):
        return subprocess.run([str(tools / tool), *args, str(apk)], check=True,
                              capture_output=True, text=True).stdout

    badging = read("aapt", "dump", "badging").splitlines()
    package = f"package: name='{RECEIPT['package']}' versionCode='{code}' versionName='{version}'"
    if not any(line == package or line.startswith(package + " ") for line in badging):
        raise ValueError(f"Unexpected Code {code} package or version")
    if not {"sdkVersion:'37'", "targetSdkVersion:'37'"}.issubset(badging):
        raise ValueError(f"Code {code} must require and target API 37")
    if any(line.startswith("application-debuggable") for line in badging):
        raise ValueError(f"Code {code} is debuggable")
    signature = read("apksigner", "verify", "--verbose", "--print-certs")
    signers = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([a-f0-9]{64})$", signature, re.M)
    if signers != [RECEIPT["original_certificate_sha256"]] or not {
        "Number of signers: 1", "Verified using v2 scheme (APK Signature Scheme v2): true",
    }.issubset(signature.splitlines()):
        raise ValueError(f"Code {code} was not verified with the original production certificate")
    with apk.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("build_tools", type=lambda value: Path(value).resolve())
    parser.add_argument("--candidate", type=Path)
    args = parser.parse_args()
    try:
        expected = verify(args.baseline)
        with tempfile.TemporaryDirectory(prefix="nova-code79-") as directory:
            with zipfile.ZipFile(args.baseline) as archive:
                archived = Path(archive.extract(APK_NAME, directory))
            if inspect(archived, args.build_tools, 79, RECEIPT["version"]) != expected:
                raise ValueError("Verified baseline APK hash differs")
        print(f"Code 79 original certificate and Android identity verified. SHA-256 {expected}")
        if args.candidate:
            digest = inspect(args.candidate, args.build_tools, 80, "1.7.38.7")
            print(f"Code 80 original certificate and Android identity verified. SHA-256 {digest}")
    except (ValueError, OSError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
