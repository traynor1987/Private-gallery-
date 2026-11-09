# NovaOS 1.7.38.1 Code 73 repair

The pinned installed Code 73 APK is from source `c0db80efc8d1342896984bd937abb01dc786a0f4`. The original production artifact is `13698305559` from run `42666431368`. Android version code 74 identifies this maintenance upgrade. Both builds use `uk.co.traynor.privategallery` and the original production signing certificate.

The production gate validates the exact frozen source, its original Android 17 reports, restart evidence and the pinned Code 73 APK before using signing credentials. It compiles the upgrade fixture against the pinned baseline source first.

On an enforcing API 37 emulator, the workflow seeds the baseline, installs the candidate with `adb install -r`, verifies the result, and repeats verification after a force-stop. Both launcher layouts, the Android Home selection, the reminder preference, Sanctuary policy and encrypted data must pass before APK upload.

Install the verified APK over the existing installation with app data in place. Physical Galaxy Z Fold verification follows the emulator checks.
