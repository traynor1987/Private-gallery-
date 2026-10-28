# Encrypted persistent Hidden browser library

Implemented in the restored checkout based on 0a18ec85cbf9e6eb4df9585dddc285f7fc0bbc79. No commit or push. Existing concurrent owner changes, including PREVIEW transaction +7, runtime mode-wait helper, cleanup, transport and release work were preserved.

## Behavior

Explicit bookmarks persist across browser close, process restart and reauthentication. History is optional and independently configured for VPN and Tor. VPN defaults to saving history; Tor defaults to not saving it. Disabling history clears that mode's saved history. History clear affects only that mode's Hidden history. Browsing-data clear also restarts/ends its existing isolated browser session so cookies/cache/tabs remain fresh, while deliberate bookmarks and the history preference remain. Bookmarks can be removed explicitly in both library UIs. Saved URLs are displayed only after an explicit library action and opened only after a user selects one; nothing restores navigation automatically.

The existing URL-only library presentation is retained; titles are not stored. URL acceptance requires HTTP(S), a host, no userinfo/control characters, and at most 2,048 characters. Runtime/broker navigation policy checks additionally constrain the respective mode. Bookmarks are capped at 50 and history at 100 entries, deduplicated and recent first. These bounds also keep the combined IPC JSON snapshot below Binder's normal transaction limit.

## Implementation

- New `app/src/main/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenBrowserLibrary.java`: dedicated `noBackupFilesDir/hidden-browser-library-v1`, fixed distinct authenticated record identities per mode, bounded versioned binary payload using the existing HiddenRecordStore AES-GCM staging, verification, atomic promotion and directory synchronization. No change to credential storage, keys, existing record formats or Primary state. No plaintext URLs/titles on disk or logs; staged files contain ciphertext. Serialized plaintext buffers are wiped.
- Each wrapper captures HiddenSession.Authority once. Each operation starts with that exact authority; publication uses session.commit. Lock/background/revocation/new authentication cannot make old work valid again. A shared process mutation gate prevents lost read/modify/write updates across wrappers; the VPN child never receives keys or directly touches the store. Authentication/decryption failures fail closed and never silently replace unreadable records.
- `HiddenBrowserRuntime.kt`: Tor library operations now use the encrypted store. Runtime disposal releases the wrapper rather than erasing persistent bookmarks. Existing profile disposal remains responsible for session data.
- `HiddenBrowserHome.kt`: independent save-history switch, history clear, actual persistent-data copy and explicit clear-data behavior.
- `HiddenVpnBrowserBroker.kt`: authenticated LIBRARY transaction at FIRST_CALL_TRANSACTION+8; PREVIEW remains +7. Reads/mutations use the existing caller/capability checks plus captured store authority. Mutations and final reply are fenced by the existing broker gate. Transport, thumbnail and shutdown blocks were not replaced.
- `HiddenVpnBrowserActivity.kt`: replaces child memory library use with parent capability transactions; adds preference/history-clear, explicit bookmark removal and scrollable library/settings content. Closing the fresh child profile no longer removes explicit encrypted bookmarks.

## Validation performed

Host Java compilation and executable checks passed with installed JDK17 modules:

```
java -m jdk.compiler/com.sun.tools.javac.Main -d /tmp/hidden-library-host-classes app/src/main/java/uk/co/traynor/privategallery/core/hidden/HiddenSession.java app/src/main/java/uk/co/traynor/privategallery/core/hidden/HiddenRecordStore.java app/src/main/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenBrowserLibrary.java app/src/test/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenBrowserLibraryHostChecks.java
java -cp /tmp/hidden-library-host-classes uk.co.traynor.privategallery.core.browser.hidden.HiddenBrowserLibraryHostChecks
```

Covered encrypted-at-rest content inspection, correct-key restart, wrong-key read/write rejection without overwriting valid state, independent modes/defaults, opt-in Tor history, history disabling/clear and retained bookmarks, URL and entry bounds, eight concurrent wrappers without lost visits, expiry, and a deterministic lock/reauthentication interleaving before staged-write publication. Old authority is rejected and the late URL never appears; temporary staging files are removed. `git diff --check` also passed.

Updated `HiddenSessionLibrary1762Test.kt` to replace the obsolete reflection test that expected runtime closure to erase all library state. Its persistent test covers defaults, mode isolation, encrypted persistence after reauthentication, stale access, clear, disabling history and credential isolation. Other existing pure ephemeral-helper tests remain valid for that unused helper.

## Required downstream verification

No Android compilation, instrumentation, emulator, device, Binder live transaction or UI rendering execution was performed here; Android SDK is absent. Run the existing focused Android class gate including HiddenSessionLibrary1762Test, plus actual Tor/VPN library flows. Verify bookmark/history persistence after child close/process restart, both default settings, independent toggles, removal/clear, switching modes with fresh cookies/profile, no automatic navigation, and revocation during IPC/UI actions. Physical transport/DNS and Fold acceptance remain owner/release-gate work. The new host-check main is directly executable and is not a JUnit test automatically selected by Gradle.
