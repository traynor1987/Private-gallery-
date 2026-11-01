# Private Gallery 1.7.10 independent security review

Source: f301ee73320df2e366b7cd258dae5105296237a7
Tree: 48425f7ab0581bcba1b43e1c8b29f690f82bd378
Decision: APPROVED

Scope: code/runtime approval to activate the original-key production workflow. Delivery is conditional on production package/certificate validation and successful code51 -> code52 in-place upgrade and restart. Physical Samsung Galaxy Z Fold acceptance remains an owner check.

Independent reviewer: /root/security_review, read-only separate agent. No unresolved Critical or Important findings. Reviewed authentication and session revocation, Primary/Hidden ownership, encrypted per-owner Home preferences and metadata search, navigation authority, explicit Evidence cloud payload consent, offline import review, screenshot/credential protection, Panic Exit, upgrade preservation and signing gates.

Resolved review/runtime findings include: exact captured Hidden search authority; explicit Private Apps root exit distinct from app launch/settings; credential protection independent of permission handoff; concurrent EvidenceAccess retirement snapshots; feature-only route retention through configuration recreation. No authentication result is Bundle-saved, no parallel WebView was introduced, and permission/focus/security assertions remain mandatory.

Exact-source GitHub run: https://github.com/traynor1987/Private-gallery-/actions/runs/41332619845
Artifact: 13094009280, private-home1710-verification
Archive SHA-256: 2409eea76e466ce4c4ce4a41acdaf876cb5735701ed0f8619bde4f99a2cf3ea2

Independently read original Android XML: 54 distinct successful cases across all 11 required classes; 0 failures, 0 errors, 0 skipped. Mandatory runtime validator passes. Host checks and grouped app/test assembly pass. Reviewer's confirmed local source tree is identical to independently queried remote commit tree.

Release requirements: original certificate SHA-256 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17; package uk.co.traynor.privategallery; version1.7.10 code52, API37 only. No replacement key, uninstall or data clear. Existing code51 Primary/Hidden media, Evidence, Private Apps, browser metadata, credentials/preferences and Recently Deleted must survive upgrade and restart. Publish one APK only after these gates pass.

## Independently reviewed upgrade-fixture correction

Upgrade fixture SHA-256: 4f18349574aefb128ddc74009e27e5a6ad978de6689f6fdd02bde9e7e819f43f

Run41333681584 passed production lint/assembly, original certificate/package/API37/OCR/alignment checks, baseline fixture seed and in-place install-r code52. Verification correctly blocked publication on an inherited fixture error: AndroidX's exact top-level profileinstaller_profileWrittenFor_lastUpdateTime.dat is a runtime package-update timestamp, not encrypted owner data. Official AndroidX ProfileInstaller writes PackageInfo.lastUpdateTime into it.

The corrected fixture excludes only that exact top-level file from full-file snapshots. No vault subpath, credential, Evidence record or preference is excluded. Independent reviewer /root/security_review approved this precise correction and reviewed SHA above with no Critical/Important findings. Workflow captures and hash-binds the reviewed fixture before immutable application checkout, then uses the existing frozen compiler and compilation receipt checks. App source f301ee73320df2e366b7cd258dae5105296237a7 and its54-case runtime approval remain unchanged. Original-key upgrade/restart still must pass before delivery.
