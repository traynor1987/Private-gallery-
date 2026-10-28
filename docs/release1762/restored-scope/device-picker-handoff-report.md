# Device picker lifecycle correction

## Source rationale

The explicit trusted read-only ACTION_OPEN_DOCUMENT launch now arms a single owned stop handoff with a fixed 120-second elapsed-realtime deadline. The reservation still belongs to the immutable callback/channel captured at launch. Android results arriving before ON_RESUME remain captured for that owner; only resumption starts validation. The live request, generation, document/tab/view fence, foreground/VPN gate, upload policy, lifecycle RESUMED state and original deadline are rechecked before validation and delivery. A second stop or a stop during validation cancels; navigation, disposal, lock and policy revocation retain cancellation. The deadline is never renewed by a result or lifecycle event.

MainActivity security, secure cover, authentication deadlines and authority destruction are unchanged. Its lock/disposal invalidates the pending request. URI provenance validation and confirmed Vault upload routing remain unchanged. Source Android 17 / version 1.7.6.2 / code 44, production package and signer are untouched.

Cancellation detaches the old request, owner, validation job, timer and deferred result before invoking cancellation callbacks, so a reentrant new request cannot be cleared by old cleanup. Completion cleanup checks the exact callback and owner after the wrapped callback, which must evaluate its authority fence before notifying the website. A completed result gate still permits the retained file callback and validation job to be cancelled directly.

## Regression and evidence

Test-first lifecycle regression committed by coordinator as d5c246a. Diagnostic Android run 40758245611 / job 123066743870 observed its expected failure at 20:09:27 UTC: `Owned picker stop must preserve its website callback`. This is valid RED against actual Activity CREATED/STARTED/RESUMED transitions with the production BrowserV2Home and captured selected URI result. The earlier full run failed this case before that assertion due to fixture package lookup and is not lifecycle RED evidence.

That diagnostic also exposed the public synthetic provider's missing explicit read grant (the platform can omit an unnecessary grant when the provider needs no read permission). The test-only provider now declares a signature read permission that the target does not request, retaining target-UID caller checks and explicit read-only grant/revoke. Production URI validation is not relaxed.

Additional coverage checks reentrant cancellation, policy revocation and URI read-grant revocation while stopped, and real Activity stop during asynchronous external-provider validation. Existing A/B ownership, cancelled marker, navigation, recreation, private URI, read-only chooser and capability/location lifecycle cases remain in the same test class.

Local `git diff --check` passed. No local Android SDK is available; post-fix Android compile/runtime verification is pending coordinator CI. No unrun test is claimed passing. This regression uses real production UI-owner Activity lifecycle with captured registry results, not a launched system DocumentsUI activity; MainActivity lock behavior remains source-preserved and covered by its separate lifecycle suite.

## Exact changed files in this task

- app/src/main/java/uk/co/traynor/privategallery/ui/BrowserPermissionOwners.kt (compiler-correct captured contract property; committed by coordinator)
- app/src/main/java/uk/co/traynor/privategallery/ui/BrowserV2Home.kt
- app/src/androidTest/java/uk/co/traynor/privategallery/ui/BrowserPermissionOwnership1762Test.kt
- app/src/androidTest/java/uk/co/traynor/privategallery/ui/BrowserSelectionFixtureProvider.java (debug target package and fixed provider authority; committed by coordinator)
- app/src/androidTest/AndroidManifest.xml (debug target query plus synthetic provider read permission)
- docs/release1762/restored-scope/device-picker-handoff-report.md

## Actual system picker cancellation crosscheck (pending)

Added `app/src/androidTest/java/uk/co/traynor/privategallery/ui/BrowserSystemPicker1762Test.kt`, a test-only actual-platform crosscheck. Its outer BrowserViewportRule sets a 392×840 phone viewport before Activity creation and restores settings after teardown, leaving font scale unchanged. It mounts production BrowserV2Home in ComponentActivity with its real ActivityResultRegistry, a synthetic session/default document and saved/restored VAULT_AND_DEVICE policy. It resolves the expected trusted component using the production contract, touches the exact visible device-choice control through fresh bounded UiAutomation accessibility coordinates, waits for that system picker package to own the active window and for the target owner to actually STOP, then requires the website callback still pending.

Real OS Back cancels the system picker. The test waits for target RESUMED and exactly one null callback, checks the callback's lifecycle state, receives no URI, and compares Android permission and persisted URI grant snapshots before/after. It uses UiAutomation rather than adding a UiDevice dependency; no Compose/Espresso call runs while the external window owns focus. No file is selected, no remote website is loaded, and no production source or manifest changed for this crosscheck. It does not verify full MainActivity authentication or Samsung device behavior. Post-addition `git diff --check` passes; compilation and device execution are pending the coordinator's final full gate. Current affected-suite CI 40762251532 predates this new test and cannot establish its result.


System-picker compilation follow-up: run40763804636 failed Android-test compilation because two core.browser.v2 imports were omitted (BrowserUploadPolicy/BrowserUploadPreference). Coordinator added the explicit imports and checked other repository type references; no production changes. Actual system-picker execution remains pending, and the failed run executed no Android cases.
