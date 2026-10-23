# Private Gallery 1.7.2 design — camera and AI refinements

## Verified baseline and release boundary
Start from release/1.7.1-professional-ui at 397c3b480c60f5ea494020804dc0117705099d62, independently fetched and checked with git ls-remote. The checkout was clean. The new branch is release/1.7.2-camera-ai-refinements. All previous branches remain untouched.

1.7.1 is a signed acceptance candidate, not physically accepted production: original-key code34 and synthetic upgrade/restart verification passed, alongside 333 JVM / 31 focused UI / 70 preflight / 317 full Android cases. Recovery documentation identifies the repaired post-test shell failure separately. Owner physical acceptance remains pending. Preserve that separate candidate and its recovery document.

## Proposed approach
Reuse the existing CameraX controller, immutable VaultCaptureAccess and private capture journal. Normal, Black-Screen and Reading View are presentations of the same authenticated session. Alternatives considered: a separate Reading controller duplicates lifecycle/security logic; an external camera complicates encrypted destination ownership. Neither is recommended.

The existing editor already has revision-scoped authenticated loading and independent source IDs. Refine those components rather than replacing their authority model.

## Combine Photos
Add a per-editor Preserve realistic proportions switch, default On, applied only to Combine. Preserve the owner's prompt verbatim. Append the supplied scale/perspective/lighting guidance under an explicit condition: apply only where it does not conflict with the owner's requested unusual scale, surreal proportions or positioning. Do not use keyword heuristics to rewrite user intent. Off sends the original prompt unchanged. Show the complete effective prompt in an expandable preview and use that exact value for generation and regeneration. Explain that guidance cannot guarantee model results. Ensure upload consent describes the effective prompt accurately.

Two equally sized cards show Photo 1 and Photo 2, actual authenticated thumbnails, independent replacement and explicit preview actions. Remove gallery IDs/oversized photo numbering from the composition picker. Retain loading/error states, cancellation, byte wiping and bitmap disposal on authority loss. Prompt edits must not change references. Responsive layout must keep controls reachable with keyboard open.

## Camera presentation
Normal is the default full-screen live viewfinder with overlaid compact controls: Photo/Video, prominent shutter/stop, timer, front/rear, supported flash/zoom, audio preference and permissions. Keep accessible labels and 48dp interaction targets. A compact selector opens Normal, Black-Screen and Reading modes. Disable capture type, destination and mode changes while recording/finalising. Rebind only when idle; preserve origin authority throughout.

Reading View shows an original, static local article with no Preview use case and no network. Before activation explain permissions/privacy indicators, exit and capture controls; choose Photo/Video and optional audio. Photo: designated reading-area tap or Volume Down, existing input gate prevents duplicates. Video: clearly labelled intentional start/stop control or Volume Down. Retain article after capture; optional confirmation vibration only after successful encrypted saving. No artificial duration limit beyond existing file/storage safeguards. Accessible close/back always exits.

## Capture ownership and lifecycle
Every controller retains its originating VaultCaptureAccess; never resolve destination from current navigation. Reject input immediately when authority is absent/revoked. Device screen-off, Activity background and session expiry revoke capture, stop/finalise recording and clear navigation. Reopening uses neutral Primary unlock, without prior Hidden-mode disclosure. Keep Android indicators and foreground requirements; no service/background recording.

Preserve journal sealing, capture timestamps, integrity metadata and interrupted recovery. Delete private plaintext only after encrypted import verification. Failed/interrupted import retains recoverable private material according to existing journal policy. Review actual implementation before adjusting it; no encryption/storage migration, key replacement or V2 lifecycle import.

## Verification
Focused JVM tests: exact effective prompt, default/off, owner-priority conditional wording, non-Combine preservation and regeneration snapshots. UI tests: equal cards, actual thumbnails, independent replacement, prompt/reference stability, preview/loading/error/lock clearing, Fold cover/inner, landscape and keyboard.

Camera tests: full-screen controls, modes, permissions/errors, photo/video/audio, Volume Down debounce, recording restrictions, screen-off/auto-lock authority loss, interrupted recovery, verified-import cleanup and Primary/Hidden destinations. Preserve meaningful existing camera layout assertions. Inspect screenshots; emulator tests do not establish physical camera acceptance.

Run fresh full JVM, instrumentation/UI/security/browser/process-restart regression and synthetic original-key production-package code34 to code35 upgrade with both vaults checked immediately and after restart. Do not reuse identical-app 1.7.1 evidence for modified app code.

## Release
Target version1.7.2/code35, provided no newer installed or remote version supersedes it. Keep package uk.co.traynor.privategallery and original certificate SHA256 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17. Preserve metallic-blue launcher artwork and browser transport code. VPN+Tor stays unavailable.

Commit coherent features and independently verify remote SHAs; maintain V1_7_2_RECOVERY.md. Push before lengthy CI. Original-key signing, APK hash/certificate/package verification and synthetic in-place upgrade are release gates. No replacement keys, owner-data operations, uninstall/clear-data or main merge. Provide one signed acceptance APK only after gates pass; production GO awaits owner Fold testing.

## Design status
Proposed for owner approval before implementation, as required by the active brainstorming workflow. No 1.7.2 application changes or APK are claimed.
