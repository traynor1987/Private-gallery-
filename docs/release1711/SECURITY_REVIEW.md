# NovaOS 1.7.11.2 Vault wallpaper — independent signing review

Source: ce743678cca903f41a376e5227eb381f9fbb9574
Decision: APPROVED
Production signing: APPROVED
Upgrade fixture SHA-256: 80f4e185bac42114b54f91bef7fe5e3f6d9feeefca79ae761ee3040372a066c8
Upgrade controls SHA-256: 20e6acabaea36a2038028a133f4a4ca24f193d80e0dba48040634d55fd367ef1
Runtime matrix SHA-256: cb59e1d4f9e115c38e319059de91595b070b6641660af539e922ffb18e719d2b
Runtime archive SHA-256: e3986eb7464c2706819914706417ec516b07c3a7fde7c6a7df23f3a3fd1ace87
Signing workflow SHA-256: 7456c5571eca908b9df17b3702a815d8b970311814335bd3dbf720d1d841dc58

Review date: 2026-11-06 UTC. Reviewer: independent wallpaper security review agent.
Frozen tree: 76d26382fcfe9a2d2c2a32365383439f43b91be8.

This authorizes original-key production signing of NovaOS 1.7.11.2/code58,
package uk.co.traynor.privategallery, through the reviewed home2-production workflow only.
Publication remains conditional on successful original code57-to58 in-place upgrade,
retained-data verification and restart. This approval does not assert signing or upgrade
has already occurred. Physical Galaxy Z Fold acceptance is not claimed.

## Independently verified original evidence

The reviewer independently re-fetched successful exact-source GitHub run42169911689,
job127735051647 and artifact13480494453. The original ZIP is67564861 bytes and matches
its authenticated digest above. Exactly108 unique Android cases across25 classes match
this matrix, plus405 JVM cases, with zero failures, errors, skips or duplicates.
Isolated debug APK identity, API37 and source receipt were checked. The parent repeated
original archive integrity, source, matrix, Android and JVM accounting checks.

Earlier failed runs42166340511 and42168412479 remain failed evidence, not approval inputs.
Diagnostics established post-IO Compose publication could resume off Main under the test
interceptor. The bounded fix explicitly retains Main affinity for publication and lifecycle
work, with IO and authority checks unchanged. All108 cases remain.

## Reviewed application changes

No unresolved Critical or Important findings remain.

- Wallpapers and the encrypted image viewer share one native Vault grid/editor workflow,
  using the existing authenticated repository, verified reader and crop policy. Original
  media remains unchanged.
- Captured authentication and presentation authority restrict selection and Apply to its
  actual Primary or Hidden owner. Factory denial cannot fall back to weaker authority.
  Primary-visible images in retained Hidden presentation use Primary authority explicitly.
- Sensitive images and encoded drafts are bounded, owned and retired on session or
  presentation revocation. Provider streams close on retirement. IO publication rechecks
  authority. No public files, decrypted shared URIs, external editors or persistent
  plaintext thumbnails are introduced.
- Existing Space-specific encrypted wallpaper storage and keys are reused. Apply serializes
  write, preference commit and pruning. Cancel publishes no draft. Stale Home saves cannot
  replace the authoritative wallpaper record. Corrupt preferences fail safely without Apply.
  Home refreshes on close and resume.
- Existing Nova wallpapers, system picker, blur, dimming, crop, Fold rendering, Gallery
  editing, original media and neutral lock-screen wallpaper are preserved.
- Code57 privacy, authentication, Browser V1 and immersive App Mode fixes remain included.
  No Home redesign, encryption/schema replacement, plaintext Preview import or VPN/Tor work.

## Reviewed signing and upgrade controls

The current eight controls, preservation fixture, matrix and full signing workflow match
these approved hashes. Original runtime gates and fixture compilation against original
signed57 source run before signing credentials become available.

Baseline: actual signed57 source3af2289e444771fd87188743879bec3361ea6346,
activation79c9edb79f9d57b43144ff1bc3d61633478418c4, run42161566466,
artifact13477498130. Baseline provenance was independently refetched; the actual archive,
APK and nested evidence were verified. Original archive:60512367 bytes, SHA-256
1a3051db04518097e2e793f2e02595c2f6df5e2ec76e6a532de0f54dadce9903.
Original APK:144500338 bytes, SHA-256
c499ac46b393d1eaac6e625b172415a4e226c5d13452f4fcdfb9391755d01c76.
Nested evidence:103746 bytes, SHA-256
4c206eaad1db0fb6d8c1f0f1add302094c1554b5f19aaa6560cf6c9a8e73c73c.

The disposable enforcing API37 experiment must begin with the production package absent,
install original57, seed synthetic encrypted Primary/Hidden media, collections, evidence,
browser state/cookies and independent Home layout/theme/widgets/notes/mood/journal/wallpaper,
install58 in place with adb install -r, verify preservation, force-stop and repeat after
restart. No seeded data may be cleared or application uninstalled. Existing assertions
and ciphertext exclusions remain. This is not a test on the owner's device.

Required original certificate SHA-256:
94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17.

Wrong-package, debuggable or wrong-certificate APKs fail. Publication requires passing
upgrade receipts. Evidence excludes instrumentation APKs, nested archives and signing
material. Signing material is removed on every outcome. Any changed frozen source,
fixture, controls, workflow, matrix or archive requires new approval.

## Limits

API37 verification uses synthetic emulator data. Physical Fold posture, real Samsung
fingerprint hardware and personal website accounts require owner acceptance. Upgrade
NovaOS in place without uninstalling it or clearing encrypted data.
