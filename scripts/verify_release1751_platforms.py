"""Fail closed on fresh full-suite and debug upgrade evidence for both supported SDKs."""
import hashlib
import json
from pathlib import Path
import sys
from verify_release175_evidence import count_reports


def verify(root, source, apk, expected_counts=None):
    expected_counts = expected_counts or {"focused": 115, "preflight": 74, "full": 395}
    root = Path(root)
    result = {}
    candidate_hash = hashlib.sha256(Path(apk).read_bytes()).hexdigest()
    for platform, sdk in (("api36", "36"), ("api37", "37")):
        evidence = root / platform
        for path, expected in (("source-sha.txt", source), ("sdk.txt", sdk), ("command-exit-status.txt", "0")):
            if (evidence / path).read_text().strip() != expected:
                raise ValueError(f"{platform}: mismatched {path}")
        if not (evidence / "build-fingerprint.txt").read_text().strip():
            raise ValueError(f"{platform}: missing device fingerprint")
        diagnostics = evidence / "diagnostics"
        for path in ("professional-ui/source-sha.txt", "full-regression-source-sha.txt", "upgrade-candidate-source-sha.txt"):
            if (diagnostics / path).read_text().strip() != source:
                raise ValueError(f"{platform}: evidence source mismatch: {path}")
        for path in ("apk-sha256.txt", "upgrade-candidate-sha256.txt"):
            if (diagnostics / path).read_text().split()[0] != candidate_hash:
                raise ValueError(f"{platform}: tested/upgrade debug APK hash mismatch: {path}")
        counts = {
            "focused": count_reports(diagnostics / "professional-ui/results", expected_counts["focused"]),
            "preflight": count_reports(diagnostics / "hidden-browser-preflight", expected_counts["preflight"]),
            "full": count_reports(evidence / "full-results", expected_counts["full"]),
        }
        for name in ("process-seed", "process-verify", "camera-process-seed", "camera-process-verify"):
            if "OK (1 test)" not in (diagnostics / "hidden-browser-preflight" / f"{name}.txt").read_text():
                raise ValueError(f"{platform}: missing process restart proof {name}")
        for name in ("seed", "verify", "after-restart"):
            if "OK (1 test)" not in (diagnostics / f"upgrade-{name}.txt").read_text():
                raise ValueError(f"{platform}: missing debug upgrade proof {name}")
        if not (diagnostics / "upgrade-result.txt").read_text().startswith("PASS:"):
            raise ValueError(f"{platform}: missing debug upgrade result")
        result[platform] = counts
    return {"source_sha": source, "platforms": result, "debug_upgrade_restart": "both passed"}


if __name__ == "__main__":
    print(json.dumps(verify(sys.argv[1], sys.argv[2], sys.argv[3]), indent=2))
