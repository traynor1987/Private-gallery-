"""Check the scope of the Sanctuary integration against the verified Code 65 source."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BASE = "a02cfcf6bb3bbae15f00935c2b2124ae7eb4e674"
CORE = "app/src/main/java/uk/co/traynor/privategallery/core"
EXCEPTIONS = (
    f":(exclude){CORE}/sanctuary/**",
    f":(exclude){CORE}/browser/v2/BrowserV2Session.kt",
    f":(exclude){CORE}/browser/v2/SecureWebViewFactory.kt",
)
for revision in (("HEAD",), ()):
    subprocess.run(
        ["git", "diff", "--exit-code", BASE, *revision, "--", CORE, *EXCEPTIONS],
        cwd=ROOT, check=True,
    )
build_file = "app/build.gradle.kts"
original = subprocess.check_output(["git", "show", f"{BASE}:{build_file}"], cwd=ROOT, text=True)
expected = original.replace("versionCode = 65", "versionCode = 66", 1).replace(
    'versionName = "1.7.30-visual-foundation"', 'versionName = "1.7.31"', 1,
)
assert (ROOT / build_file).read_text() == expected, "Unexpected production package or build configuration change"
print("PASS: encrypted engines, Hidden browser boundaries and package configuration match Code 65")
