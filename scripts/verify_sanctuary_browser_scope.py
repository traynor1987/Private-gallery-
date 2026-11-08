"""Keep signed Code 68 engines, manifest and package configuration pinned during Code 69."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BASE = "0de573ccd4b5f2ef0ee557ca8c90ebf7d2aba1d3"
CORE = "app/src/main/java/uk/co/traynor/privategallery/core"
EVIDENCE = f"{CORE}/evidence"
EXPECTED_CORE = {
    f"{EVIDENCE}/{name}" for name in (
        "EvidenceCatalog.java", "EvidenceChronology68.kt", "EvidenceCollectionSync.kt",
        "EvidenceController.kt", "EvidenceReview68.kt", "EvidenceTranscript69.kt",
    )
}

for revision in (("HEAD",), ()):
    raw = subprocess.check_output(
        ["git", "diff", "--name-only", BASE, *revision, "--", CORE, "app/src/main/AndroidManifest.xml"], cwd=ROOT, text=True,
    )
    unexpected = set(raw.splitlines()) - EXPECTED_CORE
    assert not unexpected, f"Unexpected core changes: {sorted(unexpected)}"
build_file = "app/build.gradle.kts"
original = subprocess.check_output(["git", "show", f"{BASE}:{build_file}"], cwd=ROOT, text=True)
expected = original.replace("versionCode = 68", "versionCode = 69", 1).replace(
    'versionName = "1.7.33"', 'versionName = "1.7.34"', 1,
)
assert (ROOT / build_file).read_text() == expected, "Unexpected production package or build configuration change"
print("PASS: Code 68 encrypted engines, Hidden browser boundaries and package configuration preserved")
