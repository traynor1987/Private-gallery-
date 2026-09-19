# Private Gallery final VPN, secure acquisition, and favourite collection design

**Status:** approved technical direction; pending user review before implementation  
**Base:** main at 8543f2d9618ec4d28ffcbb0dc60718d0776fbe02  
**Scope:** final planned feature milestone only. After a successful signed release, Private Gallery enters feature freeze.

## Intent and invariants

Private Gallery keeps its authenticated local Vault, encrypted payloads, VDEK/PIN/biometric/recovery architecture, permanent Android signing identity, updater, media lifecycle, browser security boundary, Collections and non-destructive crop state.

This milestone adds an app-owned OpenVPN 2 tunnel for Browser, fail-closed Browser policy when configured, direct Browser download and viewport screenshot ingestion into the encrypted Vault, and a generic one-favourite Collection shortcut in the centre navigation position.

No source control, APK, release asset, log, crash diagnostic or backup may include a signing key/password, GitHub secret, VPN credential, imported VPN profile, PIN, recovery material, browsing/session data, Vault metadata/media or plaintext user content.

## Licence and dependency audit

### Distribution designation

The combined application will be distributed as GPL-2.0-only, retaining the upstream OpenSSL and Apache-2.0 linking exceptions and all upstream copyright/licence notices.

The repository will add a root GPL-2.0 licence, a NOTICE/third-party notices document, a versioned dependency manifest, reproducible build/source instructions, and a release-facing source URL and exact source revision documentation.

The repository stays public and contains only code and public build configuration. It excludes all user/runtime secrets and data.

### Audited VPN subset

Private Gallery will use a narrow, maintained fork of the OpenVPN 2 path from:

| Component | Pin | Licence / obligation | Included |
|---|---|---|---|
| OpenVPN for Android / ics-openvpn | v0.7.65 / ee574f6ff65703e09c1a1f6c2d9243d7940f3f8d | GPLv2-or-later with explicit derivative-work clarification and OpenSSL/Apache-2.0 exceptions | service/profile Java code required for OpenVPN2 only |
| OpenVPN 2 core | 566ebc9f23259f5d55a8cc763c761073090f0595 | GPL-2.0-only, OpenSSL and Apache-2.0 linking exceptions | yes |
| OpenSSL Android source | 19a1b558a09892381199b2d1484fe1c0ba6d76cf | Apache-2.0 / NOTICE retained | yes |
| LZO | source bundled in pinned ics tree, LZO 2.10 (LZO_VERSION 0x20a0), tree 4bac163027dc61c7ee15679e53a71d83326eccce | GPL-2.0-or-later; upstream OpenVPN/LZO exception retained | yes |

The application will exclude the ics OpenVPN 3 submodule c4f61851e119dbe4cd57521a7ff4b0e5805fa65e and all AGPL/MPL source, generated bindings, libraries and build paths. It will also exclude the unused OpenVPN3-only mbedTLS, ASIO, fmt and LZ4 paths.

The final build will contain an explicit CI guard that fails if an OpenVPN3 source path, native target, dependency or generated binding is present.

### Existing application dependencies

Existing AndroidX/Compose/Media3/Room/Paging dependencies are Apache-2.0. The inherited ics/OpenVPN Apache-2.0 linking exceptions are preserved verbatim and listed in notices. Existing Bouncy Castle, JSON and test dependency notices will be captured in the third-party manifest. The final implementation must not retain Gradle's current blanket removal of licence resources as the sole notices mechanism; notices are retained in source and made available from Settings → About/Licences.

This is a technical licence audit, not legal advice; release preparation will include a final no-secret and notices review.

## VPN architecture

### Profile and credential storage

VpnProfileRepository accepts only a document-picker selected .ovpn profile. It parses into a deliberately restricted local model:
- allowed: standard remote/proto/port, CA/cert/key inline blocks, TLS/auth/cipher settings supported by the embedded OpenVPN2 engine, and optional username/password authentication;
- rejected: scripts, plugins, up/down, management socket, arbitrary auth-user-pass filename, external certificate/key references, arbitrary executable directives, and unrecognised security-sensitive directives.

The original document is never changed. The validated profile is copied to app-private storage with an opaque ID. Credentials are separately encrypted with an Android Keystore-backed key. The stored password is never returned to UI or logged. Forgetting credentials or a profile securely removes the private copy and metadata.

### State machine and Browser gateway

BrowserVpnController exposes actual engine lifecycle states: Unconfigured → Disconnected → Connecting → Connected → Reconnecting/Failed → Disconnecting.

Browser route entry:
1. If no profile or auto-connect is off, ordinary Browser policy applies.
2. If configured with Require VPN for browsing, Browser shows local chrome and a VPN status surface; no URL load or Browser-originated download is started.
3. Only an engine-confirmed Connected state enables HTTP(S) navigation.
4. Tunnel loss, failure or network change cancels pending loads/downloads and returns Browser to a fail-closed blocked state. No fallback to the ordinary network is initiated.
5. Leaving Browser or locking schedules an owned-tunnel disconnect after a 30-second grace interval; re-entering Browser cancels it.
6. A device VPN conflict is surfaced without disconnecting another app's VPN. The user may cancel or explicitly choose the supported existing-VPN policy; Private Gallery never claims to own an external VPN.

VpnService routing is device-wide while connected: Android cannot reliably implement only-this-WebView traffic with a normal app-owned VpnService. The safety claim is therefore narrow: while Private Gallery's Browser requires its owned tunnel, it will not deliberately initiate browsing/download traffic unless the tunnel state is confirmed. The UI must not claim a system-wide leak-proof kill switch.

## Secure Vault acquisition

### Shared ingestion

A new source-neutral VaultImportSource and VaultImportCoordinator will be extracted from the existing trusted import sequence. Sources provide a private stream, trusted/display metadata candidate and cancellation signal. The coordinator performs:

source stream → staged encrypted payload → authenticated verification → VaultItem commit → optional collection membership.

It never commits a complete VaultItem until verification succeeds. Generated opaque payload filenames remain authoritative; remote filenames never become paths.

Existing MediaStore and Photo Picker imports move onto the same coordinator without changing their safe move/delete ordering.

### Browser downloads

The WebView download listener supplies only metadata and a URL to a BrowserDownloadRequest. A user sees Save to Vault or Cancel; no automatic public download occurs. After VPN gating and HTTP response validation, the response body streams directly into the coordinator—no persistent plaintext file. Status moves through Downloading, Encrypting/Verifying and Saved to Vault. On VPN loss, HTTP failure, cancellation, encryption/verification failure, no complete VaultItem is committed.

Remote MIME, length and display name are validated/sanitised; path traversal, unsupported types and suspicious response states are rejected. Collection membership is offered only after the Vault commit succeeds.

### Browser screenshot

The Browser overflow action captures the current visible WebView viewport in memory. It captures page content, not Browser chrome, and then encodes/streams directly into the coordinator as a BROWSER_SCREENSHOT image. It does not write Android Pictures/Screenshots or Gallery. This internal operation does not change the external screenshot-debug preference or disable secure-window policy.

The screenshot becomes an ordinary VaultItem, and therefore uses existing viewer, crop and generic Collection flows.

## Favourite Collection

The centre heart destination is generic. favouriteCollectionId is the single source of truth, stored alongside protected Vault collection metadata/state.

- no favourite: label Favourite; opening presents a generic picker/empty state;
- setting Collection B favourite atomically replaces Collection A;
- labels resolve live from the collection name and truncate only visually;
- deletion clears the ID; it never deletes VaultItems or chooses another collection;
- the heart opens the normal collection grid/viewer dataset.

Migration recognises the current legacy Jenna pinned identity by stable collection ID, preserves its memberships and sets favouriteCollectionId to it once. Fresh installations create no Jenna collection. Jenna remains ordinary user data, not an architecture type.

## Migration, lifecycle and diagnostics

All new persistent fields use non-destructive migration. Existing VaultItems, collection memberships, crop metadata, recovery/PIN/biometric envelopes and settings remain unchanged. Imported VPN profiles/credentials are private runtime state and excluded from backup.

Diagnostics use categories only: profile rejected reason class, connection lifecycle state, network transition, download category and Vault import phase. They do not contain URLs, credentials, profile contents, filenames, keys or decrypted media.

Lock immediately disables Browser interaction, clears exposed page view, cancels Browser acquisition, releases the VPN session according to the grace policy and requires normal authentication before reopening.

## Testing and release gates

Unit/instrumentation coverage must include:
- profile parser allow/reject paths and credential secrecy;
- controller transitions, browser gating, failure/reconnect and leave/lock grace;
- no unsupported scheme/file/content/WebView bridge regression;
- direct download/screenshot streams: success, interruption, HTTP failure, VPN loss, sanitisation, encryption verification and absence of public plaintext;
- collection membership only after commit;
- one-favourite invariant, rename/delete behaviour, legacy Jenna migration and fresh-install no-Jenna;
- migration fixtures preserving Vault/recovery/Collections/crop state;
- build guard excluding OpenVPN3/AGPL paths;
- public notice/source documentation checks.

CI gates: unit tests, lint, debug assembly, Android tests, source/no-secret scan, dependency/notice audit and a signed release assembly using the unchanged permanent signer. Release preparation verifies the established public certificate fingerprint and provides the source/notice links beside the APK.
