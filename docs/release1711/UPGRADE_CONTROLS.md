# NovaOS 1.7.38 Code 73 production upgrade

The pinned source for the installed Code 72 APK is `7422343e73b13569a6a18501ecfe077bcca55c3d`. Its original production artifact is `13687062546` from run `42641862359`. The package is `uk.co.traynor.privategallery` and both APKs use the original signing certificate on Android 17.

The candidate marker and independent review bind the exact Code 73 source, Android 17 runtime archive, and reviewed control hashes. The workflow validates the original runtime reports and the archived Code 72 APK before using signing credentials. It compiles the upgrade fixture against the pinned Code 72 source without signing credentials.

The disposable enforcing API 37 emulator seeds Code 72 and installs Code 73 with `adb install -r`. It repeats verification after a force-stop. Both populated launcher layouts, the Android Home default, Sanctuary policy and retained encrypted workspace state must pass before the workflow uploads the signed APK and its checksum.

Install the release APK over the existing NovaOS installation with its app data in place. Samsung Dual Messenger discoverability and physical Galaxy Z Fold acceptance require device verification.
