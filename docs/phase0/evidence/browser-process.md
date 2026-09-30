# Phase 0 synthetic Browser/profile/process evidence

## Admission result

**IMPLEMENTED PHASE 0 CHANGE:** A separate `phase0Evidence` APK uses application ID `uk.co.traynor.privategallery.phase0evidence`. Its only Activity is nonexported and runs in `:phase0_browser`; its Application has no production initialization. The evidence manifest removes every permission, production Activity/service/provider, including `INTERNET`. Normal `debug`/`release`, production Browser settings/profile, Vault roots, keys and navigation are unchanged by this harness. This is a dedicated **same-UID process**, not an Android isolated UID, hostile-code sandbox, encrypted engine or key-isolation proof. Production code remains compiled in the evidence APK but has no manifest entry point.

**TESTED EVIDENCE:** Cached Kotlin 2.0.21 + JUnit 4.13.2 ran the actual admission policy. A deliberately permissive implementation failed unsupported-startup, unsupported-profile and wrong-process assertions (3 failures/4 tests); the fail-closed implementation passed 4/4. A deliberately permissive resource/deletion policy failed external-URL and already-loaded-profile tests (2 failures/7 tests); the final policy passed 7/7. [Exact RED/GREEN output](browser-policy-red-green.txt) is retained. Evidence source and dedicated instrumentation/JVM tests also compile directly against Android API36 and the existing AndroidX WebKit1.12.1/test classes. These checks prove policy behavior/API compatibility, not installed-provider behavior.

**UNRESOLVED:** Complete Gradle variant/unit/lint/APK checks are recorded below as they complete. Initial Gradle verification was blocked by a JRE lacking `javac`; the replacement JDK initially lacked the proxy CA, addressed with the system Java trust store. No physical device or emulator has executed this harness in this workspace. Feature availability, profile isolation, interception under network blocking, storage residues, renderer behavior, deletion completion and timing values are therefore **UNMEASURED / PHYSICAL ACCEPTANCE REQUIRED**. A successful compile or ordinary CI is never substituted for those results.

## Official source facts, checked 29 September 2026

- [AndroidX Profile](https://developer.android.com/reference/androidx/webkit/Profile) exposes profile-owned cookie, geolocation, web-storage and service-worker controllers. These APIs require runtime `MULTI_PROFILE` support. The existing 1.12.1 dependency has the needed APIs; no dependency bump is required.
- [WebViewCompat.setProfile](https://developer.android.com/reference/androidx/webkit/WebViewCompat#setProfile(android.webkit.WebView,java.lang.String)) must run before other operations on a newly created WebView, except attaching it to the hierarchy. The harness calls it immediately after construction and before settings/navigation/JavaScript. It never switches an already used instance.
- [ProcessGlobalConfig](https://developer.android.com/reference/androidx/webkit/ProcessGlobalConfig) must be applied once before provider loading; startup configuration belongs at the beginning of application startup. `setDataDirectorySuffix` separates process data/cache paths and requires startup-feature support. Only `isStartupFeatureSupported` precedes `apply` in the evidence Application. Stable suffix `phase0_browser_evidence` is exclusive to this package/process and enables cold lifecycle observations; it is not a newly randomized suffix on every launch.
- [ProfileStore.deleteProfile](https://developer.android.com/reference/androidx/webkit/ProfileStore#deleteProfile(java.lang.String)) rejects live WebViews **and profiles loaded in the process**, even if their views were destroyed. Some deletion is asynchronous; a successful return is not evidence every disk byte is gone. Default-profile deletion is forbidden. The harness deletes only the two synthetic named profiles, before either is loaded in a fresh process.
- [WebSettings.setBlockNetworkLoads](https://developer.android.com/reference/android/webkit/WebSettings#setBlockNetworkLoads(boolean)) blocks network resource loads; disabling it without `INTERNET` throws. Evidence WebViews and supported service-worker controllers keep it enabled. An exact fixed-resource interceptor supplies synthetic bytes and returns a non-null 403 for every other request. This cannot be treated as evidence of HTTP-cache persistence or remote network-stack compatibility.
- [Android process model](https://developer.android.com/guide/components/processes-and-threads) distinguishes component processes from the app security identity. A colon process name supplies process separation, not a new UID or protection against another compromised same-UID component.

## Probe boundary and individual dimensions

Only fixed `https://phase0.invalid/` and `/sw.js` are intercepted. No live local/remote server, trusted certificate change, SSL-error bypass, external intent, download path, file/content access, JavaScript bridge, Tor, proxy, VPN or owner data is used. The run ID accepts only 1–48 ASCII letters/digits/underscores; markers are `PHASE0_<runId>_NORMAL` and `PHASE0_<runId>_HIDDEN`. `NORMAL_PROFILE_TEST` and `HIDDEN_PROFILE_TEST` are test-only engine names, not production containers.

| Dimension | Implemented probe | Current result and limits |
|---|---|---|
| Native/DOM cookies | Same cookie names, distinct synthetic values in each profile; native reads + DOM reads | UNMEASURED; opposite marker must never appear |
| HttpOnly cookie | Profile CookieManager sets `Secure; HttpOnly; SameSite=Strict` marker; native presence and DOM absence required | UNMEASURED; no genuine server login asserted |
| localStorage | Same origin/key with distinct markers; read Normal again after Hidden write | UNMEASURED |
| IndexedDB | Same database/store/key; awaited transaction completion; 5s bounded operation | UNMEASURED; errors/timeouts are separate fixture/provider failures |
| CacheStorage | Same named cache/key, local synthetic Response marker | UNMEASURED; this is web CacheStorage, not Chromium HTTP cache |
| Service worker | Fixed worker script, distinct profile marker, MessageChannel read; named-profile controller interceptor and network blocking | UNMEASURED; required features checked; intercepted script failure is not isolation success |
| Geolocation permission | Profile permission store set/clear in both directions; Normal=true/Hidden=false then inverse | UNMEASURED; no actual location/OS permission requested |
| DOM sessionStorage | Two separate top-level views retain distinct document-context values across same-origin reload | UNMEASURED; documents/browsing contexts, not proof of profile-scoped persistent sessions |
| Other site permissions | Every native permission request denied | UNMEASURED_DENY_ONLY; no camera/microphone permission persistence claim |
| Login/session | Synthetic cookie + local/DOM/session markers | No genuine account login/logout or server session exercised |
| Provider HTTP cache/history | Interception uses no-store fixture and per-view synthetic navigation | UNMEASURED_SYNTHETIC_INTERCEPTION / UNMEASURED_PROVIDER_HISTORY; supported architecture/device work required |
| Plaintext disk residue | Bounded read-only search for ASCII/UTF16LE markers in evidence-suffix engine data/cache roots after view destruction, or scan-only fresh process | UNMEASURED; missing roots/I/O omissions explicit; marker absence proves neither encryption nor erasure |

Every report contains provider/version, API, UID/PID/process, startup suffix, individual dimension values/statuses and bounded scan coverage. Unknown/missing values cannot satisfy an isolation assertion. The instrumentation **fails**, without `Assume`/skip, when a required capability/dimension is unsupported/unmeasured. It still prints the synthetic report for diagnosis. HTTP cache/history remain partial even on a fully passing exercise.

The scan reads no production package/root. It does not copy or modify live Chromium databases, WALs or internals; it skips symlinks, caps depth, files, per-file and total reads, reports omissions, and makes no consistent-snapshot claim. Renderer/provider work may continue after WebView destruction. Plain marker findings would establish readable synthetic residue at that observation point; no findings would establish only that the bounded search did not find its encodings.

`startupConfigurationNanos` measures application suffix configuration only, excluding process spawn/provider/view initialization. `twoWebViewsCreationNanos` measures those view allocations after feature/provider queries, excluding Activity/IPC startup. Neither value exists until the harness runs, and neither is full cold-launch latency.

## Reproducible build and physical procedure

Normal instrumentation continues to target `debug`. Only `-PPRIVATE_GALLERY_PHASE0_EVIDENCE=true` selects `phase0Evidence`; that setting excludes normal production Browser instrumentation sources and includes `src/androidTestPhase0Evidence`. Do not run owner data through the evidence package.

```sh
JAVA_HOME=/tmp/private-gallery-jdk17 ANDROID_HOME=/tmp/private-gallery-android-sdk GRADLE_USER_HOME=/tmp/private-gallery-phase0-gradle \
  flock /tmp/private-gallery-phase0-gradle.lock ./gradlew \
  -Dorg.gradle.java.installations.paths=/tmp/private-gallery-jdk17 \
  -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts \
  :app:testPhase0EvidenceUnitTest :app:lintPhase0Evidence :app:assemblePhase0Evidence

JAVA_HOME=/tmp/private-gallery-jdk17 ANDROID_HOME=/tmp/private-gallery-android-sdk GRADLE_USER_HOME=/tmp/private-gallery-phase0-gradle \
  flock /tmp/private-gallery-phase0-gradle.lock ./gradlew \
  -Dorg.gradle.java.installations.paths=/tmp/private-gallery-jdk17 \
  -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts \
  -PPRIVATE_GALLERY_PHASE0_EVIDENCE=true :app:assemblePhase0EvidenceAndroidTest
```

Expected outputs: `app/build/outputs/apk/phase0Evidence/app-phase0Evidence.apk`, `app/build/outputs/apk/androidTest/phase0Evidence/app-phase0Evidence-androidTest.apk`; JVM XML `app/build/test-results/testPhase0EvidenceUnitTest/`; lint report `app/build/reports/lint-results-phase0Evidence.html`. Connected execution: opt-in `:app:connectedPhase0EvidenceAndroidTest`, with results under `app/build/outputs/androidTest-results/connected/phase0Evidence/`. A test list/manifest check must confirm ordinary Browser tests are absent from the evidence test APK and the evidence Activity/permission changes are absent from debug/release.

1. Record git SHA, APK SHA256, package/application ID, test runner, device model/API/OS build, provider package/version and install/update method. Use a test device and this synthetic APK only. Verify merged **and packaged** evidence manifest has no `INTERNET`, no production entry points, no exported Activity and the correct `:phase0_browser` name.
2. Install those two APKs. Run the test below with a fresh run ID; preserve its full synthetic report, test result and device/provider identity. Confirm dedicated host PID differs from the instrumentation/main process and UIDs match. Independently capture cold-launch elapsed time/RSS if performance is a requirement; harness configuration/allocation timings alone cannot answer it.

```sh
adb install -r app/build/outputs/apk/phase0Evidence/app-phase0Evidence.apk
adb install -r app/build/outputs/apk/androidTest/phase0Evidence/app-phase0Evidence-androidTest.apk
adb shell am instrument -w \
  -e class uk.co.traynor.privategallery.phase0.Phase0BrowserEvidenceTest \
  -e phase0Mode exercise -e phase0RunId synthetic_physical_001 \
  uk.co.traynor.privategallery.phase0evidence.test/androidx.test.runner.AndroidJUnitRunner
```

3. Check every storage field independently and the two-way geolocation result. Repeat normal/hidden mutations with new run IDs on each supported provider/API pair; a blocked local fixture yields UNMEASURED, never PASS. Include minSdk26/27 (startup capability may fail), API28+ and current target36/provider devices. Repeat after an owner-approved provider update; record results anew rather than inheriting support from API level.
4. Observe the report's after-destruction bounded disk scan. Preserve coverage/omissions; search engine-generated files only for those exact synthetic markers using read-only authorized test-device access. Do not delete/copy/restore Chromium trees or patch live SQLite/WAL state.
5. Force-stop **only the evidence package**, record process absence, then run the same test with `phase0Mode scan` and the same run ID. This mode reads bounded engine roots without constructing a WebView/loading a named profile. Repeat after reboot before any read/exercise mode. Record marker matches/residue/deletion timing separately for live view destruction, process death and reboot. `FLAG_SECURE` here is unrelated to a production Vault lock; actual lock/session transitions and OS/native dumps remain PHYSICAL ACCEPTANCE REQUIRED.

```sh
adb shell am force-stop uk.co.traynor.privategallery.phase0evidence
adb shell am instrument -w \
  -e class uk.co.traynor.privategallery.phase0.Phase0BrowserEvidenceTest \
  -e phase0Mode scan -e phase0RunId synthetic_physical_001 \
  uk.co.traynor.privategallery.phase0evidence.test/androidx.test.runner.AndroidJUnitRunner
```

6. In a fresh host process, run `phase0Mode read` to collect persistent cookie/storage/worker state using public APIs. This recreates empty named profiles/resources when absent but writes no marker; it therefore occurs **after** scan-only observations. SessionStorage lifetime is distinct. Retained markers are persistence evidence; absent markers must be checked per API and cannot imply erasure of disk remnants.
7. Force-stop the evidence package again; run `phase0Mode cold_delete` before any read/exercise. The supported API requests deletion of only `NORMAL_PROFILE_TEST`/`HIDDEN_PROFILE_TEST`. A false result means that profile did not exist; rejection after prior profile touch is closed. A true result means deletion requested, not synchronous completion. Scan with the same run ID immediately, after quiescence, after another force-stop, and after reboot; then read through new named profiles. Preserve individual results and any retained raw markers. Never substitute raw directory deletion for this procedure.
8. Test unsupported provider/startup branches and failure paths. Verify reports state NOT_SUPPORTED/CLOSED and no named/default WebView is created as a fallback. Inject renderer death on the evidence device and repeat residue/restart observations, separately from normal Browser acceptance. Emulator results are source-compatible/runtime evidence for that emulator only; OEM transfer, crash residue and physical provider requirements stay open.

## A / B / C decision

| Option | Bounded promise | Recommendation and gate |
|---|---|---|
| A — ephemeral/data-minimized WebView | Fresh named profile; terminate host and request supported cold deletion before another session | Candidate for reducing retained state only. AndroidX1.12.1 named profiles are persistent engine storage while live; no memory-only/incognito or forensic erasure guarantee is established. Requires measured death/reboot/deletion matrix and truthful persistence limits. |
| B — persistent isolated WebView | Separate named profile/suffix/process, with retained cookies/site state | Technically testable identity separation. Engine state is not encrypted by a Vault VDEK. Only admissible if the owner explicitly accepts weaker engine-at-rest protection; cannot satisfy independent encrypted Hidden browser-state confidentiality. |
| C — supported alternative engine/architecture | Architecture with documented supported containment and a demonstrable at-rest/lifecycle policy meeting the actual requirement | Recommended direction if independent key-encrypted engine storage or plaintext absence is mandatory. No engine/provider is selected or implemented in Phase0. Maintenance, feature support, process/network/storage and recovery guarantees need independent evaluation. |

**PROPOSED 2.0 DESIGN:** Keep Hidden Browser separately closed pending the owner's A/B/C policy and physical evidence. A passing profile exercise would permit narrower claims about measured synthetic identity separation only. It would not authorize production Hidden Browser, persistent encrypted-at-rest engine storage, Tor/VPS, or main-process key absence.

## Build verification record

- Policy RED→GREEN and direct Android/WebKit compile: TESTED EVIDENCE as described above.
- Gradle variant/unit/lint/APK checks: final attempted command was `--no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process -PPRIVATE_GALLERY_PHASE0_EVIDENCE=true :app:testPhase0EvidenceUnitTest --tests uk.co.traynor.privategallery.phase0.Phase0BrowserPolicyTest :app:assemblePhase0Evidence :app:lintPhase0Evidence :app:assemblePhase0EvidenceAndroidTest` with the JDK/SDK/trust-store parameters above. It failed during shared production-source compilation (`MainActivity.restore` commit inference and `PhotoEditor` bitmap assignment), while those integrations were under development. No evidence-source error was reported. Full Gradle tests/lint/APK creation remain UNRESOLVED until the combined branch is stabilized and rerun; direct evidence-only compilation/JVM policy success is narrower evidence.
- Merged and generated packaged-manifest XML were inspected: exact `.phase0evidence` package, zero uses-permissions, one nonexported `:phase0_browser` evidence Activity, zero production Activity/service/provider. The inherited ProfileInstaller receiver was subsequently removed too; final merged and binary APK verification must include that change. A generated packaged-manifest XML is not a completed APK.
- Provider/device/storage/death/reboot/performance: NOT RUN / PHYSICAL ACCEPTANCE REQUIRED.
