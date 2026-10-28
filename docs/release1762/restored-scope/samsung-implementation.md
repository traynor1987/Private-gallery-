# Samsung external editing implementation

Implemented in shared working tree, without commits or pushes:

- ExternalEditorIntents discovers actual ACTION_EDIT/image/png handlers for the temporary content URI with MATCH_DEFAULT_ONLY and resolved filters. It excludes non-edit filters, own package, disabled applications/activities, unexported activities, and permission-inaccessible targets, deduplicates components and ranks Samsung Gallery first.
- MainActivity offers a narrowed list when multiple editors exist, directly opens a single available editor, re-queries before launching a chosen explicit component, and keeps the MediaStore/Gallery manual reimport fallback for empty discovery or launch failure. No ACTION_SEND or ACTION_VIEW receivers enter the editor picker.
- ExternalEditingDialog uses generic photo editor wording, shows the ranked list, and avoids claiming the original remains when save fails after a possible committed replacement. It clarifies that temporary URI access expires after 15 minutes while physical cleanup may occur later.
- Existing encrypted-origin boundary checks, read-only grants, temporary lease lifetime, verified encrypted import, Save as New, explicit replacement confirmation, public-export consent and public cleanup are preserved.

New instrumentation test: app/src/androidTest/java/uk/co/traynor/privategallery/core/media/ExternalEditorDiscovery1762Test.kt. Covers ranking/deduplication, explicit component/EDIT/read-only content grant, exclusion of VIEW/SEND-only handlers, disabled activity/application, private activity, own package, inaccessible permission and unresolved filter, permitted protected editor, and empty discovery.

Validation: git diff --check passes. Attempted ./gradlew :app:compileDebugAndroidTestKotlin --offline; wrapper attempted download of Gradle 9.5.0 and failed with java.net.SocketException: Network is unreachable before any compilation. Android tests are unexecuted and Android compilation is unverified; no Android SDK/physical Fold available. Tests were authored before implementation, but red/green execution could not be established due to that environment blocker.

No process-death recovery was added. Published copy recovery and Activity recreation workflow retention remain known limitations from the audit. A matching EDIT intent filter establishes an advertised handler, not proof that an OEM editor accepts a read-only private URI or returns an edited photo; physical One UI/Fold validation remains outstanding.

Changed production files: MainActivity.kt, core/media/ExternalEditorIntents.kt, ui/ExternalEditingDialog.kt. Added test file above. No browser, release or documentation repository files edited by this task.
