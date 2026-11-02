# Independent Hidden WebView storage review

2026-11-02; source reviewed: 4351a7318ac55f665c906b222e7b93442c125fd0 (application code not modified). Signing remains blocked for the owner requirement that provider cookie/session/profile data never persist outside the originating vault-key encrypted boundary.

## Corrected scope and actual ownership

HiddenVpnBrowserActivity configures ProcessGlobalConfig before provider initialization, supplying lease.data and lease.cache plus the hidden_vpn suffix. WebView creates subdirectories under these bases. The roots are noBackupFilesDir/hidden-vpn-webview-v1/<UUID>/data and cacheDir/hidden-vpn-webview-v1/<UUID>. Both whole roots are owned and later removed; guessing a particular provider Cookies filename is unnecessary.

HiddenBrowserRuntime separately loads an unnecessary parent-process hidden16 profile. On the successful native VPN/Tor path it never creates a parent WebView: the child activity owns interactive browsing. Removing that unused parent profile initialization is sensible minimal cleanup, but does not encrypt the child's live provider files and cannot resolve the signing blocker. Do not claim that the parent profile contains child authentication cookies without runtime evidence.

## What lifecycle protection currently proves

HiddenVpnStorageCleanup prepares a synced journal and schedules a persisted JobScheduler job before admission. Parent and child file locks prevent deleting active roots. Kernel process termination releases the child lock even if callbacks fail. Cleanup deletes both data and cache trees before retiring the lease marker and syncs their containing directories. This is useful crash-recovery and eventual-deletion machinery.

It is not encryption. Non-RETURN termination (including revocation/background/Panic paths) kills the child before provider clearing. The durable filesystem remains after process death until another process executes cleanup. A persisted job cannot turn this gap into encrypted storage or guarantee immediate execution under force-stop, power loss, system scheduling delay, or I/O failure. RETURN attempts asynchronous provider clearing with a bounded kill; it too cannot establish active-session encryption.

Existing tests exercise cookie independence, fresh profiles, eventual root removal, and lock exclusion. They do not establish that plaintext never reached persistent storage. No new device run or plaintext scan was performed in this independent source/API review; no green runtime or signing claim is made.

## Primary API/source evidence

- ProcessGlobalConfig.setDirectoryBasePaths specifies ordinary readable/writable absolute directory paths and provider-created subdirectories; there is no encryption key or storage adapter parameter. It was available in the pinned AndroidX 1.13.0 (added 1.7.0). Current documentation deprecates it in 1.16.0, which is not a reason to remove the existing isolation mechanism. https://developer.android.com/reference/androidx/webkit/ProcessGlobalConfig
- ProfileStore exposes profile create/get/list/delete. Deleting a loaded profile is forbidden; even successful deletion can finish asynchronously. It offers no app-supplied encryption key or incognito flag. https://developer.android.com/reference/androidx/webkit/ProfileStore
- Chromium's Android WebView browser-context source configures cookie persistence including session cookies: persist_session_cookies=true and restore_old_session_cookies=true. Session cookie does not mean RAM-only. https://chromium.googlesource.com/chromium/src.git/+/refs/heads/main/android_webview/browser/aw_browser_context.cc
- Chromium commit d9c031fa48a414f929caf5d12b45fc1057243a50, 2025-10-07, removes the Android WebView cookie encryption provider. Its rationale says GetCookieCryptoDelegate previously returned nullptr on Android; an accidentally introduced crypto delegate caused Google login hangs. This is strong evidence against assuming provider cookie encryption. It does not identify the exact installed API37 provider version; that must be captured in device evidence. https://chromium.googlesource.com/chromium/src.git/+/d9c031fa48a414f929caf5d12b45fc1057243a50%5E%21/
- CookieManager.flush explicitly writes accessible cookies to persistent storage. Avoiding flush is not a documented no-write mode. https://developer.android.com/reference/android/webkit/CookieManager
- StorageManager.openProxyFileDescriptor exposes one seekable file with callbacks, useful for encrypted uploads already in this app. It does not expose a mounted writable directory tree or provider storage backend; it cannot supply the arbitrary database/LevelDB files and directory operations required by a WebView profile. https://developer.android.com/reference/android/os/storage/StorageManager#openProxyFileDescriptor(int,android.os.ProxyFileDescriptorCallback,android.os.Handler)

Do not overclaim that the exact tested provider persisted sessionStorage specifically: current source and APIs permit provider-managed storage and the app enables DOM storage, but this review did not inspect an exact-provider sessionStorage disk fixture. Cookie persistence alone is sufficient to invalidate the no-plaintext invariant.

## Feasibility decision

No safe minimal correction using the existing public Android WebView/AndroidX 1.13/API37 APIs was identified that both preserves interactive VPN/Tor/authentication and implements originating vault-key encryption of all active provider-managed storage. Encrypting a snapshot after closure, clearing more often, or moving ordinary files changes retention, not the live storage boundary. Disabling cookies/DOM storage or replacing requests with native GET interception sacrifices required behavior. App-private/device encryption is not the originating vault key.

A solution needs an engine/provider with a supported ephemeral nonpersistent profile or custom encrypted storage backend, or an actual vault-key encrypted writable filesystem boundary compatible with Chromium filesystem operations and process death. A normal third-party Android app cannot merely mount its own tmpfs/FUSE encrypted directory through these public WebView APIs. Either route requires an architectural change and new exact-source auth, transport and storage evidence. An owner-approved scope change to eventual deletion would be a separate requirement decision, never an inferred waiver.
