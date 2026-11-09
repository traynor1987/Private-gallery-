# NovaOS 1.7.37 Code 72 production upgrade

| Input | Pinned value |
|---|---|
| Branch | `feature/novaos-sanctuary-integration` |
| Production package | `uk.co.traynor.privategallery` |
| Platform | Android 17 / API 37 |
| Original APK | NovaOS 1.7.36 Code 71 |
| Original production run and artifact | `42614242525` and `13672198205` |
| Original application source | `dd23090dd72682a9f9d0d22e7de39e1778fffa71` |
| Original APK SHA-256 | `7de326f185f0d58562dc164f551fdf2cb3700e121af782b325b743c727a92405` |
| Original certificate SHA-256 | `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17` |

The candidate marker and signing review identify the exact Code 72 application source, the Android 17 verification archive and the reviewed control hashes. The workflow checks the original runtime reports, Backup and Launcher process restart evidence and the pinned Code 71 distribution before it accesses the production signing environment. It compiles the upgrade instrumentation against the exact Code 71 source and verifies the compiled payload before signing a disposable copy.

The upgrade runs on a fresh enforcing API 37 emulator. It installs the pinned Code 71 APK, seeds test data, then updates to the signed Code 72 APK with `adb install -r`. Verification repeats after a force-stop and checks both launcher layouts, Android Home selection, Sanctuary settings, Vault and encrypted workspace data, Evidence Locker and Backup history. The workflow uploads the production APK and checksum only after those checks pass.

Install the delivered APK over the existing NovaOS installation with its app data in place. Physical Galaxy Z Fold acceptance is recorded separately.
