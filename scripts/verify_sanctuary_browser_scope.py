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


ALLOWED_GUARDIAN = {
    f"{CORE}/evidence/EvidenceController.kt",
    f"{CORE}/guardian/GuardianNotificationListener.kt",
    f"{CORE}/guardian/NotificationProbe.kt",
    f"{CORE}/guardian/GuardianCapture.kt",
    f"{CORE}/guardian/GuardianCapturePolicy.kt",
    f"{CORE}/guardian/GuardianCaptureStore.kt",
    f"{CORE}/guardian/GuardianInboxPolicy.kt",
    f"{CORE}/guardian/GuardianLocalClassifier.kt",
    f"{CORE}/guardian/GuardianCallShield.kt",
    f"{CORE}/guardian/GuardianEvidenceReference.kt",
    f"{CORE}/guardian/GuardianPhoneScreeningService.kt",
    f"{CORE}/guardian/NovaBrainEvaluator.kt",
    f"{CORE}/guardian/NovaBrainValidationImport.kt",
    f"{CORE}/guardian/NovaBrainKnowledge.kt",
    f"{CORE}/guardian/NovaBrainEvidenceSources.kt",
    f"{CORE}/guardian/PersonalImpact.kt",
    f"{CORE}/guardian/PeaceJournal.kt",
    f"{CORE}/guardian/NovaBrainPatterns.kt",
    f"{CORE}/guardian/GuardianNoticePolicy.kt",
}


def original(path):
    return subprocess.check_output(["git", "show", f"{BASE}:{path}"], cwd=ROOT, text=True)


for revision in (("HEAD",), ()):
    raw = subprocess.check_output(
        ["git", "diff", "--name-only", BASE, *revision, "--", CORE], cwd=ROOT, text=True,
    )
    unexpected = set(raw.splitlines()) - ALLOWED_SANCTUARY - ALLOWED_GUARDIAN
    assert not unexpected, f"Unexpected protected-core changes: {sorted(unexpected)}"

build_file = "app/build.gradle.kts"
expected = original(build_file).replace("versionCode = 69", "versionCode = 79", 1).replace(
    'versionName = "1.7.34"', 'versionName = "1.7.38.6"', 1,
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
expected = expected.replace(
    '      applicationIdSuffix = ".hiddenprototype"',
    '      val guardianProbe = providers.gradleProperty("novaGuardianProbe").orNull == "true"\n'
    '      applicationIdSuffix = if (guardianProbe) ".capture77.guardianprobe" else ".hiddenprototype"',
    1,
).replace(
    '      resValue("string", "app_name", "NovaOS TEST ONLY")',
    '      resValue("string", "app_name", if (guardianProbe) "NovaOS Capture Recovery TEST ONLY" else "NovaOS TEST ONLY")',
    1,
)
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


listener, = [service for service in application.findall("service")
             if service.get(ANDROID + "name") == ".core.guardian.GuardianNotificationListener"]
expected_listener = ET.fromstring(f"""
    <service xmlns:android="{ANDROID[1:-1]}" android:name=".core.guardian.GuardianNotificationListener"
        android:exported="false" android:label="@string/guardian_listener_label"
        android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
      <intent-filter><action android:name="android.service.notification.NotificationListenerService" /></intent-filter>
    </service>
""")
assert structure(listener) == structure(expected_listener)
application.remove(listener)
inbox, = [activity for activity in application.findall("activity")
          if activity.get(ANDROID + "name") == ".launcher.GuardianInboxActivity"]
assert structure(inbox) == structure(ET.fromstring(f"""
    <activity xmlns:android="{ANDROID[1:-1]}" android:name=".launcher.GuardianInboxActivity"
        android:exported="false" android:excludeFromRecents="true" />
"""))
application.remove(inbox)
settings_query = ET.fromstring(f"""
    <intent xmlns:android="{ANDROID[1:-1]}">
      <action android:name="android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS" />
    </intent>
""")
listener_settings, = [intent for intent in queries.findall("intent")
                      if structure(intent) == structure(settings_query)]
queries.remove(listener_settings)
sms_query = ET.fromstring(f"""
    <intent xmlns:android="{ANDROID[1:-1]}">
      <action android:name="android.provider.Telephony.SMS_DELIVER" />
    </intent>
""")
sms_visibility, = [intent for intent in queries.findall("intent") if structure(intent) == structure(sms_query)]
queries.remove(sms_visibility)

screening, = [service for service in application.findall("service")
              if service.get(ANDROID + "name") == ".core.guardian.GuardianPhoneScreeningService"]
assert structure(screening) == structure(ET.fromstring(f"""
    <service xmlns:android="{ANDROID[1:-1]}" android:name=".core.guardian.GuardianPhoneScreeningService"
        android:exported="true" android:permission="android.permission.BIND_SCREENING_SERVICE">
      <intent-filter><action android:name="android.telecom.CallScreeningService" /></intent-filter>
    </service>
""")), "Only Android Telecom may bind to the call screening service"
application.remove(screening)
contacts, = [permission for permission in after.findall("uses-permission")
             if permission.get(ANDROID + "name") == "android.permission.READ_CONTACTS"]
assert contacts.attrib == {ANDROID + "name": "android.permission.READ_CONTACTS"} and len(contacts) == 0
after.remove(contacts)

assert structure(before) == structure(after), "The existing manifest or permission set changed"
print("PASS: Code 69 protected engines, original package and other permissions preserved; Home and call screening are additive")
