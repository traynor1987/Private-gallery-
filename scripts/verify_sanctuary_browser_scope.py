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
    f"{CORE}/guardian/GuardianPipelineDiagnostics.kt",
    f"{CORE}/guardian/NotificationProbe.kt",
    f"{CORE}/guardian/GuardianCapture.kt",
    f"{CORE}/guardian/GuardianCapturePolicy.kt",
    f"{CORE}/guardian/GuardianCaptureStore.kt",
    f"{CORE}/guardian/GuardianRetention89.kt",
    f"{CORE}/guardian/GuardianAutomaticCloudPolicy.kt",
    f"{CORE}/guardian/GuardianAutomaticCloudSettings.kt",
    f"{CORE}/guardian/GuardianCloudJob.kt",
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
# Audited main-promotion adaptations retain the installed encryption format.
CODE81_EXACT_ADAPTERS = {
    f"{CORE}/browser/v2/BrowserV2FatalCrashCapture.kt": "aedb8e170870d07b4507d9bac6aa9e24eaf6f85379f4d136059174e95fe38d47",
    f"{CORE}/browser/BrowserAcceptanceDebugConsole.kt": "745cfd76252583242988b64ed5508ea5420e5d73f9bc4bb2abd1ebe1a60c708b",
    f"{CORE}/browser/BrowserDiagnosticPrivacy.kt": "3d5625ce4a7d6a5c3e626841ee8232695806a5adb1a674e56a0b2d8c38d30777",
    f"{CORE}/security/PrimaryVaultSetupGuard.kt": "af0c8edd45bcb8a27f225950c6a1166c507d070a9c4b1c0b516f88dd44484f97",
    f"{CORE}/security/RecoverySetupLifecycle.kt": "554959c73ad9c8d4018110a26d56b3fc8665a041afbce5a4143cfd5ff664eadf",
    f"{CORE}/security/RecoveryVaultKeyStore.kt": "155cb9855e8ac253598f00adecb2d569b1d785ff03c63679cebcf423319c976b",
    f"{CORE}/security/RecoveryKeySetupPolicy.kt": "8696e66b15e2f44e8d6f6b8fb8eaec730a8248a2c1360bdced1656f29f06205d",
    f"{CORE}/security/PinVaultKeyStore.kt": "e7f2f8eadf36f50c2e881752407ae9a9106a1acebff43b041fcf45d2aeeb2ece",
    f"{CORE}/vault/AndroidVaultRepository.kt": "2a820fba2e75b48c126d0c5908a863dc696dca978a78f90c51f5e7f7eeea12ad",
    f"{CORE}/vault/EncryptedIndexStore.kt": "64b1ff89e0d6ca96eb1a56652af6deed1631680714d0f961fd80fe3136520783",
    f"{CORE}/security/PrimaryStorageInventory.kt": "41187c425f3139258e23cdfeeb8b41f0d6a93712480b95616df8958c10390ab3",
    # Build87 reviewed ordinary Settings navigation; no authentication/encryption engine change.
    f"{CORE}/ui/SettingsCategory.kt": "df26ffaba9eb7b212ac30914336862c37d376f202aa092621ca85cfddb86cb46",
    f"{CORE}/ui/SettingsSections.kt": "d6851604f9f98d644391981cb39cd51581dc392d4a2e1b7da22b0c24c7f41716",
    # Build86 reviewed connectivity/presentation adapters; encrypted engines remain pinned above.
    f"{CORE}/update/UpdateTransferMeter.kt": "aa36c46552d3e3b037457d6e5719fed323c215e0538b3fe3aa06d77f704090bb",
    f"{CORE}/update/NovaUpdateDownloadInterface.kt": "20c22bc603b92356a5c3c89b97807e147918397b4d4ae7a0719cb685b7342ddf",
    f"{CORE}/brain/NovaBrainConnection.kt": "f25b14108639d83ce0bc38b1b6632b3faafdd25bfc4ed98e750b1d24e93de0af",
    f"{CORE}/brain/NovaBrainConnectionStore.kt": "f176c3e1fbf1c9ef4e89930338632940f95d8667da016df8a61d6474c3fbf321",
    f"{CORE}/brain/CloudTeacherProtocol.kt": "0c1ce2e743cf487e97141b89f0e0c7f581d7a408fce06f428369a98e95ffb22a",
    f"{CORE}/brain/CloudTeacherClient.kt": "54830b041b5cf532aa8ac25909d7ebb05d078c6930a03e12bbd7743633ef1db9",
    f"{CORE}/brain/CloudTeacherStore.kt": "dbf532602efa25692f063d87b7328db8c5c7284172b5e08907e8320fb9b9bcdd",
    # Build85 updater-only adaptations, reviewed independently of encrypted engines.
    f"{CORE}/update/ReleaseUpdate.kt": "810b3488c5b4ee8cdd22f1922265be35c50c123842fda17ed0e173d341468f67",
    f"{CORE}/update/UpdateInstallPolicy.kt": "480a2268b8326ab01c3775947811da8f6de201171a21c9f64ff6b58887956083",
    f"{CORE}/update/NovaUpdateState.kt": "198b6c41d6bfcbe4eb3e6f93897d2b86d937e698be902b6cd29c25a7696c9567",
    f"{CORE}/update/NovaUpdateModel.kt": "aa45b54c579ea3009afdfd1b32e7413c7e959952b4f90f5d1dc5f75dd076e7fb",
    f"{CORE}/update/VerifiedUpdateTransfer.kt": "d9f813cc1d855eed8f0299b04c86c1ea01594601a9da784d3da2874b54e23889",
    f"{CORE}/media/ExpiringShareProvider.kt": "42a7c51bc7fe69079c0deff07663bceb65aafcb0bc253c9709fb19045892a409",
    f"{CORE}/backup/BackupCoordinator.kt": "fa2dedf1d88865e0bee5e9362109f5c1e6a921909fb00bb6ab168474f8d5b8fa",
    f"{CORE}/vault/PhoneVaultTransfer.kt": "8fe53b2491622958d4c8d59322ffee3e2a4d12041a6d88f926e12a59ed8ec604",
}
# Hidden Space exposes the existing protected process and adds encrypted browser preferences/download metadata.
# Pin these exact adapters while continuing to reject other changes in the protected core.
HIDDEN_SPACE_EXACT_ADAPTERS = {
    f"{CORE}/browser/hidden/HiddenTorService.kt": "dcf18efe3cdb4707e2aae2331e17e371d67b283d94172d13178b6294107a1dee",
    f"{CORE}/browser/hidden/HiddenTorRelayBridge.kt": "aa7bd3cdb6e600ffa106f279c6931a8da363e6f41ab7c62292d45786a216e484",
    f"{CORE}/browser/hidden/HiddenBrowserLibrary.java": "7e289978a890662f425fc894b56252a7b5b232b32614cc9f88d43a6b346c0fb4",
    f"{CORE}/browser/hidden/HiddenBrowserRuntime.kt": "33b1c2d38ceb9b1883a035b3a2ff6eb9a9af7a48a4a9bff83b521a4733b1d18d",
    f"{CORE}/browser/hidden/HiddenVpnBrowserActivity.kt": "06f4551a8a195b7754da744f852ee96054f56ae07bd9e2cbc099a883a1020883",
    f"{CORE}/browser/hidden/HiddenVpnBrowserBroker.kt": "2181e407655b01a898c6c505546c77ee73f82c28d7b926c682d011b6c62ad050",
    f"{CORE}/browser/hidden/HiddenWebViewFactory.kt": "bc2597ec4cad6a8f5b23f2e6042e42f43c4b17ee0c51cde5b16412658124502a",
    f"{CORE}/browser/v2/SecureWebViewFactory.kt": "e8ce63a4d13cafa71388757f2daacb7c18cd1a79753a959938d206649fbec437",
    f"{CORE}/hidden/HiddenRepository.kt": "ea5b2767c5f37de9bc81432d84ba6e79342d81111531648a3ad7d316041b9ef2",
}
for path, checksum in (CODE81_EXACT_ADAPTERS | HIDDEN_SPACE_EXACT_ADAPTERS).items():
    assert hashlib.sha256((ROOT / path).read_bytes()).hexdigest() == checksum, f"Unexpected protected adapter changes: {path}"

for revision in (("HEAD",), ()):
    raw = subprocess.check_output(
        ["git", "diff", "--name-only", BASE, *revision, "--", CORE], cwd=ROOT, text=True,
    )
    unexpected = (set(raw.splitlines()) - ALLOWED_SANCTUARY - ALLOWED_GUARDIAN
                  - ALLOWED_CORE_APP_ADAPTERS - CODE81_BACKUP_WIRING.keys() - CODE81_EXACT_ADAPTERS.keys()
                  - HIDDEN_SPACE_EXACT_ADAPTERS.keys())
    assert not unexpected, f"Unexpected protected-core changes: {sorted(unexpected)}"

build_file = "app/build.gradle.kts"
expected = original(build_file).replace("versionCode = 69", "versionCode = 97", 1).replace(
    'versionName = "1.7.34"', 'versionName = "1.7.51"', 1,
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
cloud_job, = [node for node in application.findall("service")
              if node.get(ANDROID + "name") == ".core.guardian.GuardianCloudJob"]
assert structure(cloud_job) == structure(ET.fromstring(f"""
    <service xmlns:android="{ANDROID[1:-1]}" android:name=".core.guardian.GuardianCloudJob"
        android:permission="android.permission.BIND_JOB_SERVICE" android:exported="false" />
"""))
application.remove(cloud_job)

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
calendar_read, = [permission for permission in after.findall("uses-permission")
                  if permission.get(ANDROID + "name") == "android.permission.READ_CALENDAR"]
assert calendar_read.attrib == {ANDROID + "name": "android.permission.READ_CALENDAR"} and len(calendar_read) == 0
after.remove(calendar_read)

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

# Exact, visibility-only installer query; no new permission or exported component.
installer_query = ET.fromstring(f"""
    <intent xmlns:android="{ANDROID[1:-1]}">
      <action android:name="android.intent.action.VIEW" />
      <data android:mimeType="application/vnd.android.package-archive" />
    </intent>
""")
installer_visibility, = [node for node in queries.findall("intent") if structure(node) == structure(installer_query)]
queries.remove(installer_visibility)

# Only enable the three existing, private components. Their process, exposure, intent filters and
# permission declarations must remain identical to the protected baseline.
for name in ("HiddenVpnBrowserActivity", "HiddenTorIsolationService", "HiddenTorService"):
    component, = [node for node in application if node.get(ANDROID + "name") == ".core.browser.hidden." + name]
    assert component.get(ANDROID + "exported") == "false" and component.get(ANDROID + "enabled") is None
    component.set(ANDROID + "enabled", "false")

def require_novaos2_manifest_nodes(feature_file, target, expected_nodes):
    if not (ROOT / f"app/src/main/java/uk/co/traynor/privategallery/novaos2/{feature_file}.kt").is_file():
        return
    for expected_node in expected_nodes:
        expected = ET.fromstring(expected_node.replace("ANDROID_NAMESPACE", ANDROID[1:-1]))
        name = expected.get(ANDROID + "name")
        matching = [node for node in target.findall(expected.tag) if node.get(ANDROID + "name") == name]
        assert len(matching) == 1 and structure(matching[0]) == structure(expected), f"Invalid NovaOS declaration: {name}"
        target.remove(matching[0])


# Reviewed additive feature declarations. The original exact-comparison gate covers every other node.
require_novaos2_manifest_nodes("NovaLockScreenStudio", after, [
    '<uses-permission xmlns:android="ANDROID_NAMESPACE" android:name="android.permission.SET_WALLPAPER" />',
])
require_novaos2_manifest_nodes("HealthConnectSteps", after, [
    '<uses-permission xmlns:android="ANDROID_NAMESPACE" android:name="android.permission.health.READ_STEPS" />',
])
require_novaos2_manifest_nodes("HealthConnectSteps", application, [
    '<activity xmlns:android="ANDROID_NAMESPACE" android:name=".novaos2.HealthConnectRationaleActivity" android:exported="false" />',
    '''<activity-alias xmlns:android="ANDROID_NAMESPACE" android:name=".novaos2.HealthConnectPermissionUsage"
        android:targetActivity=".novaos2.HealthConnectRationaleActivity" android:exported="true"
        android:permission="android.permission.START_VIEW_PERMISSION_USAGE">
      <intent-filter>
        <action android:name="android.intent.action.VIEW_PERMISSION_USAGE" />
        <category android:name="android.intent.category.HEALTH_PERMISSIONS" />
      </intent-filter>
    </activity-alias>''',
])
require_novaos2_manifest_nodes("NovaShareHub", application, [
    '''<activity xmlns:android="ANDROID_NAMESPACE" android:name=".novaos2.NovaShareTargetActivity" android:label="Nova Share Hub"
        android:exported="true" android:excludeFromRecents="true" android:noHistory="true">
      <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
      </intent-filter>
    </activity>''',
])

assert structure(before) == structure(after), "The existing manifest or permission set changed"
print("PASS: Code 69 protected engines, original package and other permissions preserved; Home and call screening are additive")
