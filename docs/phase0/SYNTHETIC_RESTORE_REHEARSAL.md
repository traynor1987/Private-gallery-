# Synthetic restore rehearsal and physical acceptance

SOURCE FACT: The portable archive reader authenticates recovery, index and every payload before installation. Recently Deleted entries are ciphertext backup contents; Browser/provider/shared preferences/temp files are not portable archive entries. Phase 0 does not change owner Primary keys, formats or archives.

IMPLEMENTED PHASE 0 CHANGE: `Phase0FrozenRestoreRehearsalTest` uses the independently authored frozen archive (whole image plus two-chunk PGVIDEO1), randomly namespaced cache directories and preference names. It does not open the app's owner `filesDir/vault`, owner key preferences or MediaStore. It restores, reconstructs the PIN adapter, checks VDEK/envelope identity, both payload digests, metadata and file inventory, then removes only that synthetic namespace. Reconstruction is restart-equivalent adapter evidence, not a process-death measurement.

## Authorized synthetic device execution

Use a dedicated clean emulator or spare device containing no owner app/data. Normal debug/test APKs have the production package identity; do not install them over an owner's existing application. Use the separate Browser evidence package only for the Browser probe; its intentionally restricted source set does not run this restore instrumentation.

Build without signing/release commands:

```sh
JAVA_HOME=/tmp/private-gallery-jdk17 ANDROID_HOME=/tmp/private-gallery-android-sdk GRADLE_USER_HOME=/tmp/private-gallery-phase0-gradle flock /tmp/private-gallery-phase0-gradle.lock ./gradlew -Dorg.gradle.java.installations.paths=/tmp/private-gallery-jdk17 assembleDebug assembleDebugAndroidTest
```

On the clean synthetic device, install those debug/test artifacts and execute only:

```sh
adb shell am instrument -w -e class uk.co.traynor.privategallery.core.vault.Phase0FrozenRestoreRehearsalTest uk.co.traynor.privategallery.test/androidx.test.runner.AndroidJUnitRunner
```

Record exact build SHA, APK digests, emulator/device API, runner result and whether the device is synthetic. Do not report a build-only result as execution. In-app source paths and fixture credentials are deliberately synthetic and public; do not substitute owner archives, PINs or recovery secrets.

## Remaining physical gates

PHYSICAL ACCEPTANCE REQUIRED: On a separately authorized synthetic acceptance device, export a ciphertext backup containing an actual playable image/video, collections/favourite/current and previous crop/provenance/vaultOnly/trash metadata; record inventory and digests. Keep an independent unchanged backup and recovery secret. Fresh restore after actual process death/reboot must unlock only through deliberate authentication, reproduce both payloads and all metadata, and preserve the envelope bytes. Simulate device loss by restoring on another clean acceptance device. Verify portable archives never contain Browser/provider/temp/plaintext canaries.

PHYSICAL ACCEPTANCE REQUIRED: Same-signer forward upgrade of the owner's app, rollback/recovery build compatibility, low-storage/power-cut/filesystem durability, hardware biometric behavior and OEM cloud/device-transfer exclusion remain separate gates. The JVM exception/cancellation tests are not power-cut or directory-fsync evidence. Do not attempt an owner upgrade, owner key/ciphertext migration, release build or live workflow as part of this rehearsal.

UNRESOLVED: No device process-death/reboot/OEM/signer or media-decoder result has been supplied by this document. Fill acceptance evidence with measured results only; leave those gates open until performed under their own authorization.
