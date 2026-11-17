"""Require the signed Code 69 security boundary and an additive Android Home registration."""
from pathlib import Path
import hashlib
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
    f"{CORE}/evidence/EvidenceAccess.kt",
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


# Code 81 adds a Primary Space adapter without changing the encrypted engine.
ALLOWED_CORE_APP_ADAPTERS = {f"{CORE}/home2/NovaCoreAppDocuments.kt"}

# Validate the exact additive backup wiring against the protected baseline.
# The new data schema lives in the existing Code 81 adapter above.
CODE81_BACKUP_WIRING = {
    f"{CORE}/home2/SpacePreferences.kt": (
        (
            " internal fun onRetire(action:()->Unit)=access.onRetire(action)",
            " internal fun onRetire(action:()->Unit)=access.onRetire(action)\n"
            " /** Serialize a durable background write with retirement of its originating authority. */\n"
            " internal fun commitAuthorizedWrite(action: () -> Unit) {\n"
            "  access.leases.acquire().use { lease ->\n"
            "   lease.commit {\n"
            "    checkLease(lease)\n"
            "    action()\n"
            "   }\n"
            "  }\n"
            " }",
        ),
        ("BackupPolicy.preferences.forEach", "BackupPolicy.preferencesFor(spaceId).forEach"),
        ("BackupPolicy.allowed(namespace)", "BackupPolicy.allowed(namespace,spaceId)"),
        ("BackupPolicy.allowed(name)", "BackupPolicy.allowed(name,spaceId)"),
        (
            "   else->values.forEach{(key,value)->require(!Regex",
            "   in uk.co.traynor.privategallery.core.home2.NovaCoreAppBackup.namespaces ->\n"
            "    safe.putAll(uk.co.traynor.privategallery.core.home2.NovaCoreAppBackup.portableAt(name,values,spaceId,java.time.Instant.now()))\n"
            "   else->values.forEach{(key,value)->require(!Regex",
        ),
    ),
    f"{CORE}/backup/BackupPolicy.kt": (
        (
            " fun allowed(name:String)=name in preferences",
            ' fun preferencesFor(owner:String)=if(owner=="primary")preferences+'
            'uk.co.traynor.privategallery.core.home2.NovaCoreAppBackup.namespaces else preferences\n'
            ' fun allowed(name:String,owner:String="primary")=name in preferencesFor(owner)\n'
            ' fun readerFor(entries:Set<String>)=if(entries.any{it.startsWith("preferences:")&&\n'
            '  it.removePrefix("preferences:") in uk.co.traynor.privategallery.core.home2.NovaCoreAppBackup.namespaces})2 else 1\n'
            ' fun minimumVersion(reader:Int)=when(reader){1->"1.7.11.6";2->"1.7.38.7";else->error("Unsupported backup reader")}',
        ),
    ),
    f"{CORE}/backup/PortablePreferenceSchema.kt": (
        (
            '   else->error("Unsupported preference namespace")',
            '   in uk.co.traynor.privategallery.core.home2.NovaCoreAppBackup.namespaces ->\n'
            '    uk.co.traynor.privategallery.core.home2.NovaCoreAppBackup.validate(name,values,owner)\n'
            '   else->error("Unsupported preference namespace")',
        ),
    ),
}

for path, replacements in CODE81_BACKUP_WIRING.items():
    expected = original(path)
    for before, after in replacements:
        assert expected.count(before) == 1, f"Backup baseline moved: {path}"
        expected = expected.replace(before, after, 1)
    assert (ROOT / path).read_text() == expected, f"Unexpected protected backup wiring: {path}"

# These two adapters are pinned byte-for-byte, including
# the archive reader compatibility and the new, stricter Files import limit.
CODE81_EXACT_ADAPTERS = {
    f"{CORE}/update/ReleaseUpdate.kt": "4fa36d12cf9044d695faba3f10d4233cc0fa4ab21a8040f6e3d33120343a5a59",
    f"{CORE}/backup/BackupCoordinator.kt": "fa2dedf1d88865e0bee5e9362109f5c1e6a921909fb00bb6ab168474f8d5b8fa",
    f"{CORE}/vault/PhoneVaultTransfer.kt": "8fe53b2491622958d4c8d59322ffee3e2a4d12041a6d88f926e12a59ed8ec604",
}
for path, checksum in CODE81_EXACT_ADAPTERS.items():
    assert hashlib.sha256((ROOT / path).read_bytes()).hexdigest() == checksum, f"Unexpected protected adapter changes: {path}"

for revision in (("HEAD",), ()):
    raw = subprocess.check_output(
        ["git", "diff", "--name-only", BASE, *revision, "--", CORE], cwd=ROOT, text=True,
    )
    unexpected = (set(raw.splitlines()) - ALLOWED_SANCTUARY - ALLOWED_GUARDIAN
                  - ALLOWED_CORE_APP_ADAPTERS - CODE81_BACKUP_WIRING.keys() - CODE81_EXACT_ADAPTERS.keys())
    assert not unexpected, f"Unexpected protected-core changes: {sorted(unexpected)}"

build_file = "app/build.gradle.kts"
expected = original(build_file).replace("versionCode = 69", "versionCode = 84", 1).replace(
    'versionName = "1.7.34"', 'versionName = "1.7.38.8"', 1,
)
debug_version = """
// The isolated Fold preview advances independently of the production release.
androidComponents {
  onVariants(selector().withBuildType("debug")) { variant ->
    variant.outputs.forEach { output ->
      output.versionCode.set(71)
      output.versionName.set("1.7.38.7-TEST-ONLY")
    }
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

review_later, = [node for node in application.findall("receiver")
                 if node.get(ANDROID + "name") == ".launcher.GuardianReviewLaterReceiver"]
assert review_later.attrib == {ANDROID + "name": ".launcher.GuardianReviewLaterReceiver", ANDROID + "exported": "false"}
assert len(review_later) == 0
application.remove(review_later)

reminder, = [node for node in application.findall("receiver")
             if node.get(ANDROID + "name") == ".home2.NovaReminderReceiver"]
assert structure(reminder) == structure(ET.fromstring(f"""
    <receiver xmlns:android="{ANDROID[1:-1]}" android:name=".home2.NovaReminderReceiver" android:exported="false">
      <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.TIME_SET" />
        <action android:name="android.intent.action.TIMEZONE_CHANGED" />
      </intent-filter>
    </receiver>
""")), "Only the private reminder receiver and the three system broadcasts are permitted"
application.remove(reminder)

assert structure(before) == structure(after), "The existing manifest or permission set changed"
print("PASS: Code 69 protected engines, original package and other permissions preserved; Home and call screening are additive")
