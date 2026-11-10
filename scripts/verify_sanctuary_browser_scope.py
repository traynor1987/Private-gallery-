"""Require the signed Code 69 security boundary and an additive Android Home registration."""
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BASE = "cc8aaed81b8d6ed27c71c9923edbf51334466cb2"
CORE = "app/src/main/java/uk/co/traynor/privategallery/core"
ANDROID = "{http://schemas.android.com/apk/res/android}"
MANIFEST = "app/src/main/AndroidManifest.xml"
ALLOWED_SANCTUARY = {
    f"{CORE}/sanctuary/{name}" for name in (
        "SanctuaryLauncherPolicy.kt", "SanctuaryGuardian.kt", "SanctuaryGuardianService.kt",
        "SanctuaryBlockingOverlay.kt", "SanctuaryAccessibilityService.kt", "SanctuaryState.kt", "SanctuaryTemporaryAccess70.kt",
        "SanctuaryWorkspacePause.kt",
    )
}


def original(path):
    return subprocess.check_output(["git", "show", f"{BASE}:{path}"], cwd=ROOT, text=True)


for revision in (("HEAD",), ()):
    raw = subprocess.check_output(
        ["git", "diff", "--name-only", BASE, *revision, "--", CORE], cwd=ROOT, text=True,
    )
    unexpected = set(raw.splitlines()) - ALLOWED_SANCTUARY
    assert not unexpected, f"Unexpected protected-core changes: {sorted(unexpected)}"

build_file = "app/build.gradle.kts"
expected = original(build_file).replace("versionCode = 69", "versionCode = 75", 1).replace(
    'versionName = "1.7.34"', 'versionName = "1.7.38.2"', 1,
)
debug_version = """
// The isolated Fold preview advances independently of the production release.
androidComponents {
  onVariants(selector().withBuildType("debug")) { variant ->
    variant.outputs.forEach { it.versionCode.set(71) }
  }
}
"""
expected = expected.replace("\ndependencies {", debug_version + "\ndependencies {", 1)
assert (ROOT / build_file).read_text() == expected, "Unexpected production package or build configuration change"

before = ET.fromstring(original(MANIFEST))
after = ET.parse(ROOT / MANIFEST).getroot()
application = after.find("application")
launcher, = [node for node in application.findall("activity")
             if node.get(ANDROID + "name") == ".launcher.NovaLauncherActivity"]
assert launcher.get(ANDROID + "exported") == "true", "Android must be able to start Home"
assert launcher.get(ANDROID + "launchMode") == "singleTask"
assert launcher.get(ANDROID + "excludeFromRecents") == "true"
assert launcher.get(ANDROID + "taskAffinity") not in (
    None, "", "${applicationId}", "uk.co.traynor.privategallery"
), "The launcher and private workspace must have independent tasks"
intent_filter, = launcher.findall("intent-filter")
assert [node.get(ANDROID + "name") for node in intent_filter.findall("action")] == ["android.intent.action.MAIN"]
assert {node.get(ANDROID + "name") for node in intent_filter.findall("category")} == {
    "android.intent.category.HOME", "android.intent.category.DEFAULT"
}
assert len(launcher) == 1 and len(intent_filter) == 3
application.remove(launcher)
queries = after.find("queries")
widget_bind, = [intent for intent in queries.findall("intent") if
                [node.get(ANDROID + "name") for node in intent.findall("action")] ==
                ["android.appwidget.action.APPWIDGET_BIND"]]
assert len(widget_bind) == 1 and not widget_bind.attrib, "Widget visibility must be scoped to the binding action"
queries.remove(widget_bind)
recognition, = [intent for intent in queries.findall("intent") if
                 [node.get(ANDROID + "name") for node in intent.findall("action")] ==
                 ["android.speech.RecognitionService"]]
assert not recognition.attrib and len(recognition) == 1 and len(recognition[0].attrib) == 1, (
    "Speech recognition discovery must be scoped to its service action"
)
queries.remove(recognition)


def structure(node):
    return (node.tag, sorted(node.attrib.items()), (node.text or "").strip(), tuple(map(structure, node)))


assert structure(before) == structure(after), "The existing manifest or permission set changed"
print("PASS: Code 69 protected engines, original package and permission set preserved; Android Home is additive")
