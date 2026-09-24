# Viewer / editor V1 acceptance

Starting remote main: e2180fa304f10032ce7b52063110837113cc3755.
No production release is authorized by this milestone.

## Delivered architecture
- Viewer: compact accessible icon/label toolbar, shared GalleryMenuSheet, explicit image chrome toggling, native video control space, collection/import/restore/delete callbacks retain existing semantics.
- PhotoEdit + bounded EditHistory are transient. Crop uses visually oriented original coordinates, followed by rotate/flip and brightness/contrast/saturation. Rendering always starts from source plus state. Gesture completion creates one undo entry. Undo/redo/reset never recompress.
- PhotoRenderer produces sampled previews, normalizes all eight EXIF orientations and encodes a metadata-free PNG once for final output. Final render retains original resolution up to 16 MP and a device memory budget; larger images are sampled. Encoded inputs over 48 MiB and unreasonable dimensions fail explicitly. Peak stages release obsolete bitmaps. Transparent pixels are retained.
- Save copy uses VaultImportCoordinator and AndroidVaultRepository verified encryption/index commit. Explicit createDistinctCopy bypasses normal import dedup for edited copies only. Encrypted sourceReference records editedFrom:<original ID>. Original ciphertext and presentation metadata are not edited. No new Vault schema or public media output.
- Editor-only decrypt uses one exact-sized buffer, checks cancellation, and wipes on failure. No editor plaintext files, MediaStore writes, or FileProvider additions. Backgrounding discards the session, cancels jobs and immediately clears retained source/result/preview; in-flight bounded worker buffers clear in finally.
- AI provider interface is selected-image-only; adapters receive sanitized PNG plus prompt/capability/normalized mask/aspect. No context, repositories, item identity, filename, PIN, key, recovery material, Browser history or collection index.
- Capability-gated prompt/removal/fill/background/restyle/expand UI; fitted mask coordinates; brush size, undo/clear; expanded-canvas preview. Remote results are sanitized and previewed before the same Save copy route.
- Consent is scoped to the configured provider and clearable in Settings. API credentials have a Keystore AES-GCM store in noBackupFilesDir. No credentials are embedded in builds or shown in Settings.
- AI network policy is independent of Browser's owned-VPN gate and uses Android default routing, including any active VPN. No alternate network binding, bypass, or VPN implementation. No production network adapter is installed in V1's unconfigured state.

## External configuration blocker
The repository/build workflows contain no selected, documented remote editing adapter or AI credential configuration. Seedream is a model family, not one universal API contract. Required external configuration: an approved provider profile identifying the vendor/documented image-edit API contract, model/version and credential. A provider adapter must map that documented contract to AiImageEditProvider and retrieve its credential from AiCredentialStore; do not guess an endpoint or insert a key into source/BuildConfig. Until that profile exists, Settings and AI Edit say Not configured and no remote request can occur. Local tools and encrypted Save copy remain available.

## Physical acceptance matrix (NOT claimed by CI)
Run each viewport-sensitive case on Fold outer portrait, inner portrait, inner landscape, and outer landscape where supported; use gesture navigation and repeat toolbar checks with three-button navigation. Repeat with app light and dark settings; fullscreen editing intentionally stays dark.

| Area | Exact check | Required result |
|---|---|---|
| Viewer chrome | Single tap photo twice; pinch, pan, double-tap, swipe between at least 3 images | Both bars toggle together; gestures never activate toolbar actions; pager is disabled while zoomed |
| Toolbar/sheet | Back, counter, Collection, Edit, Restore, More; dismiss sheet; cancel Delete | Reachable controls with correct insets/contrast; no unintended deletion/export |
| Video | Play/pause, seek, rotate, back, adjacent photo/video paging | Native video controls reachable; playback/releases unchanged |
| Editor entry | Open legacy-cropped item immediately after opening viewer | Edit waits for metadata; crop matches existing presentation |
| Crop/history | Drag every edge/corner, drag entire crop, undo, redo, reset, undo reset | One gesture per undo; bounded crop; no source mutation |
| Transforms | Rotate 4 times, flip twice; combine asymmetric crop with rotate/flip | Identity restored; preview/output coordinates agree |
| Adjustments | Min/default/max brightness, contrast, saturation; undo/redo | Smooth bounded preview; saved output matches chosen state |
| Save copy | Save from viewer page 1 and later page; save unchanged and identical edits | Distinct encrypted item each time; new result displayed; original bytes/metadata unchanged |
| Formats/size | JPEG with GPS and every EXIF orientation; PNG alpha; large 12/50/200 MP fixtures; corrupt/unsupported image | Correct orientation/alpha; metadata absent; bounded sampling or useful error; no crash |
| Lifecycle | Cancel; Home/background; lock; rotate/unfold/refold; kill/relaunch during preview/generate/save | Original unchanged; no plaintext artifacts; safe discard on background/recreation; no late unwanted result; no stuck work |
| AI unconfigured | Open AI Edit and Settings, clear consent | Explicit Not configured; no remote request; local Save works |
| AI configured* | Cancel/continue disclosure; remember then clear; change provider | No upload before consent; disclosure names correct provider |
| AI tools* | Prompt, brush at image edges/letterbox, undo/clear, removal/fill, supported backgrounds and expansion presets/custom | Only advertised tools; mask/canvas matches selected image; preview before Save copy |
| AI failures* | Offline, timeout, error, invalid/oversized response, cancel, background | Concise failure; buffers cleaned; original unchanged; retry possible |
| Network* | Active owned VPN, VPN loss, Browser VPN-required toggle | AI follows Android routing; Browser's gate stays fail closed; no app-level VPN bypass |
| Storage | Save with insufficient private storage; inspect MediaStore before/after edits | Failed copy cleans encrypted staging; no plaintext/public copy |

*Remote live cases are blocked until the approved provider profile/adapter is configured. Deterministic fake-provider CI cases never use real credits.

## Feature freeze
After this milestone, only defects, Browser A/B compatibility regressions, security issues, acceptance failures and release blockers. No further unrelated feature work.
