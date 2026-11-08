"""Keep the signed Code 67 engines and package configuration pinned during Evidence Locker 2.0."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BASE = "5ee1a5458bb5d62daeae2369ee9dde05952a0faa"
CORE = "app/src/main/java/uk/co/traynor/privategallery/core"
EVIDENCE = f"{CORE}/evidence"
EXPECTED_CORE = {
    f"{EVIDENCE}/{name}" for name in (
        "EvidenceAccess.kt", "EvidenceAi.kt", "EvidenceAiReport.kt", "EvidenceChronology68.kt",
        "EvidenceCollectionSync.kt", "EvidenceController.kt", "EvidenceMetadata.kt", "EvidenceReview68.kt",
    )
} | {f"{CORE}/sanctuary/SanctuaryPatternLearning68.kt"}

for revision in (("HEAD",), ()):
    raw = subprocess.check_output(
        ["git", "diff", "--name-only", BASE, *revision, "--", CORE], cwd=ROOT, text=True,
    )
    unexpected = set(raw.splitlines()) - EXPECTED_CORE
    assert not unexpected, f"Unexpected core changes: {sorted(unexpected)}"
build_file = "app/build.gradle.kts"
original = subprocess.check_output(["git", "show", f"{BASE}:{build_file}"], cwd=ROOT, text=True)
expected = original.replace("versionCode = 67", "versionCode = 68", 1).replace(
    'versionName = "1.7.32"', 'versionName = "1.7.33"', 1,
)
assert (ROOT / build_file).read_text() == expected, "Unexpected production package or build configuration change"
print("PASS: Code 67 encrypted engines, Hidden browser boundaries and package configuration preserved")
