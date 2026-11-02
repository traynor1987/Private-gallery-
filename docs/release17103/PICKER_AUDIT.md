# App-controlled media picker audit (53da3f28 / signed 54 baseline)

## Existing components retained

The baseline does not contain `GalleryComponents`, `MediaPicker`, `SourceImagePicker`, or `BrowserVaultUploadPicker` declarations/files. Their functioning equivalents are the components below. No media, transfer, authentication, permission, or index engine was replaced.

| Surface | Baseline | Change / scope |
| --- | --- | --- |
| `BrowserV2Home` Vault website uploads | Adaptive real-thumbnail grid using `BrowserVaultUploadTile`; owner validates candidates and upload authority | Shared count / original-order or name sort, shared bounded fullscreen thumbnail preview and info. Existing four-item/single selection limits, Cancel, Continue and separate upload consent remain. |
| `HiddenNativeUploadPickerGrid` | Adaptive real-thumbnail grid using `GalleryMediaSelectionTile`, scoped by isolated owner | Same count / sort / preview and MIME info; no callbacks, compatibility filters, loading fence or authorization ownership changed. |
| `CreateImageSheet` Vault references | Adaptive real-thumbnail grid using browser tile; immediate selection mutations and Done | Shared count / sort / preview and info. Draft references now commit only with Confirm; Cancel/dismiss retain the prior confirmed references. Existing model limits and source-size filters remain. |
| `AiSourcePicker` (editor source replacement) | Real-thumbnail adaptive grid; two concurrent scoped decodes, cancellation and owned bitmap wiping | Retained working grid and existing authenticated editor callbacks. This is not a filename-only list. It still selects immediately and does not use the shared preview/confirm controls. |
| `CollectionCoverDialog` in `MainActivity` | Filename-only `GalleryChoiceRow` list | Remaining root-owned integration: needs authenticated thumbnail callback added at owner callsite; do not load/decrypt through a new UI-owned path. |
| `EvidenceImportReview` step 0 | App-controlled filename-only originals selection before registration | Remaining: existing `EvidenceThumbnail` requires a registered evidence ID, and `withOriginalBytes` looks up the catalog entry. A safe pre-registration thumbnail callback is needed from the evidence authority. Do not register originals merely to preview them. |
| Evidence export / AI selections | Mixed incident/record rows with reference metadata, not a photo/video-only picker | Retained; media thumbnails require registered IDs through existing `EvidenceThumbnail`, while incidents are not media. |
| Device Gallery import, external-editor recovery, browser device documents, Evidence file import | Android Photo Picker / `OpenDocument(s)` contracts | Retained system contracts and URI grant / revocation validation. |

## Preview, metadata and security boundaries

`GalleryMediaSelectionTile` lends the existing in-memory thumbnail cache entry. The fullscreen surface explicitly says **Thumbnail preview**; it does not claim full-resolution original viewing or video playback. It inherits the owning secure-window policy. No bytes, files, external intents, saved-state media or new caches are introduced. Disposal still rejects late callbacks and drops its borrowed bitmap reference without recycling cache-owned data.

Video tiles accept optional duration metadata and format actual known durations. The current `VaultItem` index model does not expose duration and the thumbnail callback returns only a bitmap: these surfaces therefore say **Duration unavailable** rather than inventing a value or opening a full video outside the authenticated owner. Full-resolution previews / video playback and duration acquisition remain explicit integration gaps.

The existing loading and current-owner fences stay with their owners. Sort only changes visible order and does not alter the eligible set. Adaptive grids, Material colors and full-window preview layout support narrow/Fold layouts; device Fold/theme visual verification is still required.

## Verification

- Host red: the Java duration contract failed with `AssertionError: Shared picker duration labels are missing` before adding the production label helper.
- Host green: compile `PickerMediaLabels.java` with `tests/picker_presentation/PickerMediaLabelsChecks.java` using `java com.sun.tools.javac.Main -Xlint:all`, then run `PickerMediaLabelsChecks`. Covers unknown/negative, zero, minute and hour durations. Passed.
- Added focused Compose instrumentation coverage in `MediaPickerPresentation17103Test`: preview/info does not select, known duration and selection count remain visible.
- Attempted focused `connectedDebugAndroidTest` before UI implementation: Gradle wrapper could not download Gradle 9.5.0 (`java.net.SocketException: Network is unreachable`). Instrumentation red/green, Kotlin compilation, emulator and Fold/theme checks are **not verified** in this environment.
- `git diff --check` passed after edits.

No CI, pushes, branches, version/signing changes, transfer changes or claims about the separately deferred Beta disk limitation.
