# Private Gallery 1.5 production integration

Owner specification: 22 October 2026 production-upgrade request.
Baseline: 5d4e1436494a517398200602aa9986da0cb873cf. Existing checkout preserved; isolated worktree.

## Implementation plan

1. Require fresh Primary authentication on explicit exit from Hidden; revoke Hidden operations and clear both presentations. Add menu and Settings actions. Test actual Activity authority and stale operation rejection.
2. Add Phone Gallery / Primary Vault tabs, reuse VaultHome and viewers. When leaving Hidden through Primary Gallery, authenticate Primary first, then return to the selected Gallery source. Preserve phone permissions and copy-only Hidden imports. Replace Hidden import source popup with direct device action and explicit Primary-copy menu action.
3. Separate Hidden feature availability from test branding. Version 1.5/code29 (baseline V1 code28); production package unchanged. Do not sign or distribute until baseline certificate and synthetic upgrade are verified.
4. Large media gate: current PGHV1 records authenticate one whole-file GCM message; repository import, read, thumbnail, Primary-copy and viewer require whole-file arrays. Retain the existing limit until a separately versioned bounded authenticated reader/writer, random-access playback and compatibility tests exist. A simple streaming wrapper cannot safely satisfy bounded authenticated seeking. Report this exact blocker per owner instruction; no weakening or silent limit removal.
5. Run host checks and Android CI once per coherent checkpoint; independently review authorization and upgrade boundaries. Verify remote SHA after push. No main, V2 or PR59 edits; no release publication.

## Release gates

No release readiness claim until large-media paths, exact certificate match, synthetic in-place upgrade, full CI and owner acceptance pass. Historical baseline certificate evidence is not final APK verification.

## Checkpoint evidence

Implemented fresh Primary return, Gallery integrated tabs using existing VaultHome/viewer, direct Hidden device imports and separate authenticated Primary copy action, context-aware viewer copy, copy-only Gallery controls, production Hidden flag, version1.5/code29. Independent review findings about viewer destination, destructive move, overlay authority and explanatory text were addressed.

Fresh host checks: 373 foundation assertions; isolation/import/favourites/collections/reset; operation revocation; record corruption/bounds/recovery; directory durability; background timeout; 3 browser script tests; no-secret, WireGuard dependency and retired-runtime scans; diff whitespace. All passed. Android Gradle execution blocked locally by unavailable distribution/network; CI remains required. No red/green Android runtime cycle claimed.

Recovered signed baseline APK: package uk.co.traynor.privategallery, name1.0.27/code28; SHA25630154bd79792e4fa1ee5e551136e9664e5e249823b6a9dea4b9c96a3aeecb07b; certificate94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17. Independent APKv2 signature and content digest verified. Exact owner-installed version still requires comparison.

Large-file, permanent candidate signing, synthetic in-place upgrade and physical acceptance are NOT completed by this checkpoint. No production candidate issued.

## Synthetic upgrade verification added

The CI-only fixture checks emulator identity, uses only the separate test package,
and constructs V1 code28 from a pinned source in a temporary checkout. It seeds
an original PIN, valid PNG/MP4, favourite collection, actual theme/screenshot
preferences and encrypted synthetic VPN record. It installs the candidate with
`adb install -r`, compares encrypted-file hashes, unlocks with the original PIN,
decrypts both media items, and repeats verification after process restart.
No uninstall or clearing occurs after V1 seeding. Baseline/test package suffix is
the only baseline product change; permanent secrets are never accessed.
This tests storage compatibility, not permanent signer or actual owner-device identity.
Execution result remains pending until the CI evidence says otherwise.

## Exact large-media blocker

HiddenRecordStore PGHV1 uses a single authenticated whole-file GCM record.
HiddenRepository.importMedia/read/thumbnail, PrimaryCopyAccess and Hidden viewer
byte callbacks all require complete arrays; metadata decoding enforces16MiB too.
Primary already has ChunkedVaultVideoStore/PGVIDEO1 and VaultVideoSession, but
Hidden cannot simply use the Primary repository: it needs Hidden-domain key/AAD,
metadata nonce/version support, session-bound reader retirement, atomic staged
publication/recovery and bounded image/video thumbnails. Those adaptations and
20/100/500MiB corruption/cancellation/seek/legacy-compatibility tests are not yet
implemented. The safety bound is retained; none of those larger imports is certified.
