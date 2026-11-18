# Build 85 updater and release verification

Target: NovaOS 1.7.39, versionCode 85, package uk.co.traynor.privategallery, API37 only.
Baseline: authentic original-key-signed NovaOS 1.7.38.8, versionCode 84.
Baseline source: b79b85efc5bae8e0bedb832e21a62d462e6f4397.
Baseline APK SHA-256: f0086165ec7e54ea90987b7001442161271347abcb820a4e696a0295397fcc30.
Original certificate SHA-256: 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17.

The existing Updates & About screen uses a lifecycle-retained update model. It checks the
stable official release, compares embedded release versionCode, shows release notes and
progress, and verifies the GitHub API asset digest, integrity metadata, APK package,
original certificate, increasing code and API37 requirements before Android handoff.
Downloads remain in private cache; the existing FileProvider exposes only the update copy
read-only. No Vault path or encryption key is added to provider roots. Metadata and APK
transfers are bounded and HTTPS-only. Cancellation deletes an incomplete download.
Only PackageManager reporting the requested installed version confirms installation.
The successful-check timestamp is preserved across failed checks and process restart.

The protected release workflow is extended in place, not replaced. It pins authentic
Build84, compiles the reviewed fixture against that exact baseline, and seeds the existing
representative encrypted records. Its in-app rehearsal substitutes only release transport
in the disposable instrumentation process, using loopback responses and the actual signed
candidate bytes. The immutable baseline application executes discovery, download, APK
validation and FileProvider/Android PackageInstaller handoff itself. The host confirms
Android's real Update/Install dialog. No adb install -r is used to substitute for that path.
The existing populated encrypted verify and force-stop/restart phases remain mandatory.
Publication additionally requires a source/hash-bound successful in-app rehearsal receipt.
A controlled prepublication rehearsal is not evidence of live public release discovery;
the published release must also be checked independently before claiming completion.

Verification in progress:
- Local updater JVM suite: 20 passed, zero failed.
- Local release controls/publication, packaged backup exclusions and checked-command suite: 39 passed, zero failed or skipped (API37 SDK configured).
- Protected core/manifest preservation: passed; encryption formats and keys unchanged.
- Build84 baseline package, API37, production signature and pinned hash: independently verified.
- New baseline updater rehearsal method: Kotlin syntax/API compilation against API37 passed.
- Initial branch Android17 run: #461 on 00d4f18cbf20aaf5b0726a8174d112661554d048;
  application and test compilation/JVM step passed; runtime result pending at this checkpoint.
- Full local Gradle: blocked by this host's Java dependency network access, not counted as passed.
- Final main CI, signed candidate, populated 84->85 in-app upgrade and publication: not yet run.
- Physical Fold installation/acceptance: not performed by automation.

Release controls remain fail-closed until their marker, review and immutable runtime receipt
are frozen against the final verified main source. Historical Build84 receipts are retained
as history, not reused as proof of the changed Build85 application.
