# Private Gallery 1.7.6 — signed owner-acceptance handoff

**GO for Galaxy Z Fold owner acceptance. Physical acceptance remains pending. Do not merge to main without owner approval.**

## Provenance and artifact

- Resume checkpoint: `344269ad4bce6a482c03d247146af32ed4cf45a1`, tree `049dee8b90256884d41b7d6066d7ec305c164cda`.
- Frozen tested/signed source: `180a7ce7048f56371d1e0566b6333de65fd1aa75`, tree `fe777d58d8bea958784214c8971e89b536528c3b`.
- [Successful admission run 40219349316](https://github.com/traynor1987/Private-gallery-/actions/runs/40219349316).
- Owner-reported installed baseline: 1.7.5.2/code40. Synthetic upgrade baseline source: `ec05b23ec28a44730439bd1090c1e64afff8f140`.
- Package `uk.co.traynor.privategallery`; version1.7.6/code41; minimum API36.
- Original certificate SHA-256: `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`.
- APK SHA-256: `f8d48a7017f46985d136e5e6a53beb0f000a98cf83ff986820cd37a1e8eb11e1`.
- APK size:104,257,250bytes. APKv2 signature verified by Android apksigner; certificate independently extracted from actual downloaded APKv2 signing block and matched to the original.
- [One signed owner-acceptance APK](https://chatgpt.com/library?file_id=libfile_ee210f2cb91881919025d6bec242e3ee&account_id=personal).

Install over1.7.5.2 without uninstalling or clearing data. No replacement signing material was generated. No owner's device/media/credentials/keys were used in automation.

## Completed implementation

Scoped selection and explicit Select All/Deselect All, authenticated metadata date/size tools, drag-range and accessible long-press selection, and compact quick filters. Sorting retains valid selections; context/lock/filter reconciliation removes invalid selections.

Both encrypted vault transfer directions have stable operation markers, encrypted ID-only journals, destination verification before source removal, cancellation boundaries, crash recovery/resume, duplicate retry protection and authenticated history. Failed current destination verification becomes FAILED/PARTIAL rather than inheriting historical success. Removal-only failures retain verified-copy accounting. Hidden history requires Hidden and fresh Primary transfer authentication.

Samsung external editing provides explicit encryption-boundary/cloud consent, an expiring read-only private content URI, runtime Samsung handler resolution and explicit export/edit/select-result fallback. Origin-bound authentication is revalidated; preview, verified Save as New, confirmed Replace, app-owned cleanup and authorised known-public-copy cleanup are implemented. Original encrypted media is retained until verified replacement, with recovery in Recently Deleted.

Collections support bulk add/remove without media deletion, validated covers, pin/unpin, accurate membership/count behavior and independent encrypted-domain presentation. Multiple collection membership is preserved.

## Verification

| Gate | Android16/API36 | Android17/API37 |
|---|---:|---:|
| Changed feature tests |61|61|
| Broad focused regression |187|187|
| Security preflight |74|74|
| Unfiltered full regression |420|420|
| Debug40→41 upgrade/restart |PASS|PASS|
| Original-key production40→41 upgrade/restart |PASS|PASS|

JVM351 PASS. Every native suite has zero failures/errors/skips and mandatory named cases. Host selection20, transfer journal15, edit lease8 and Python74 pass; existing encryption/camera/browser host checks also passed. Both platforms' raw counts/source/debug APK hashes and named regressions were independently reverified after download. Both production upgrade proofs reference the frozen source and identical delivered APK. Tests cover transfers over100photos,250IDs,500MiB payloads, cancellation/revocation, commit/deletion interruption, retries, corrupt/missing destinations, external import/Replace/auth expiry/cleanup and Fold-sized layouts. Existing browser, camera, playback, privacy, metadata and vault regressions remain mandatory.

Raw evidence and production package/signature/upgrade receipts: [final-evidence](final-evidence/summary.json). Downloaded acceptance/diagnostic archives matched GitHub SHA-256 and ZIP CRC. CI confirmed temporary signer and disposable onion cleanup. Later backup commits contain documentation/evidence only; the tested source above remains frozen.

## Independent security review

Independent read-only review checked transfer encryption, cancellation/recovery, source-deletion ordering, duplicate markers, vault authority, external handoff/result import/temp cleanup, collection isolation and Hidden metadata. Verified defects were corrected and covered: authenticated EOF on deduplicated resume, delegated reverse authority revocation, deletion-boundary cancellation, per-attempt verification receipts, stale external callback/epoch isolation, Panic curtain/lock ordering and pointer gesture ownership. Final independent raw-evidence review found no remaining verified blocker in this scope and recommended GO for owner acceptance only.

The previous real Tor timeout and stale fixture-readiness evidence were investigated without changing production transport, deadlines, app retries or exact-byte assertions. Fresh independent fixture checks now precede live suites. Final live browser tests passed on both platforms. The upgrade fixture now admits only the exact newly reviewed40→41 pair; package/emulator and all preservation/restart assertions remain enforced.

## Samsung findings and limitations

Android ACTION_EDIT does not promise edited output. Direct Samsung handler behavior, Object Eraser availability and actual public-copy behavior remain physical-device acceptance. The supported fallback is explicit export, Samsung editing, result selection, preview and verified encrypted import. After external screens, explicitly reopen the originating photo's Samsung-edit flow and authenticate if locked; Hidden never falls back to Primary. Process death drops the in-memory origin ticket; reselect the original and saved edited result after authentication.

Private Gallery can expire/remove its own temporary lease and request deletion of authorised public copies after verified import. It cannot erase or verify Samsung caches, cloud processing, backups or third-party copies. Document-provider copies may require manual cleanup. Vault-only egress restrictions remain enforced. Transfer progress is item-based with accurate final receipts; intra-file byte/rate progress is not provided. Durable journals cover encrypted vault-to-vault transfers; the existing public Phone export path is not a durable transfer journal. Device Gallery selects loaded results; range dragging applies to supported vault grids. No physical Fold/Samsung acceptance is claimed, and VPN+Tor remains unavailable.

## Owner acceptance

Use disposable synthetic media for transfer/edit trials first. Follow the [Galaxy Z Fold checklist](OWNER_ACCEPTANCE.md): selection/date/size/filters; bidirectional bulk transfer/progress/cancel/recovery/duplicates/history; Samsung edit/reimport/Save as New/cleanup; collection actions; existing media and independent PIN/fingerprint; Tor/VPN; cameras/video; signed in-place upgrade. Check inner and cover screens. Physical approval and main merge remain the owner's decision.
