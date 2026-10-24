# 1.7.4 independent security review

Independent read-only reviewer examined baseline-to-working changes, then new PNG/protected-video/Gallery additions. No Critical findings. Initial four Important findings:

1. Primary date check/promote could race revocation. Promotion now dispatches synchronously onto main lifecycle thread, where authentication check and short atomic rename cannot interleave with main-thread lock handling. Expensive encryption stays on IO.
2. Failed export write/fsync could leave untracked plaintext. Stage reference now assigned before opening/writing; finally deletes failed staging.
3. Handler-only expiry was lost on process death. FileProvider now enforces read-only15min expiry per open across process death. JobScheduler survives process death for best-effort disk cleanup; startup and live Handler sweep also remove files. Android scheduling delay and already-open destination descriptors remain limitations.
4. Reserved JPEG C8 extension was copied. Explicit rejection added; new host negative control failed before correction and passed after.

Narrow new-scope review found malformed PNG palette/transparency could survive sanitisation and invalid phone dates could appear as epoch dates. Added header/bit-depth/colour/palette/transparency length/order/duplicate validation; host malformed-tRNS negative control failed then passed. Gallery dates now use valid-date policy and display also omits invalid fallback timestamps. No further concrete boundary blocker reported for protected video reader or scoped storage.

Additional author corrections: native date/time dialogs dismissed at disposal/background; share/details close synchronously on background; storage state keyed to vault context and cleared on background. Gallery byte reads are bounded64MiB. JPEG/PNG original scan/compressed pixel bytes preserved; unsupported metadata/containers fail closed. Android decoder/EXIF and provider runtime fixtures are mandatory gates, not inferred from parser host checks.

Remaining scope limitations: video/WebP/GIF privacy export unavailable; no selective retain-other-EXIF modes (clean removes all optional metadata); PNG ICC removal can affect colour appearance; phone-gallery dates are read-only until import; device-specific frame-rate selection remains unavailable. Resolution choices are now filtered to CameraX-discovered supported qualities. Recent capture preview and independently encrypted camera preferences were subsequently implemented and require fresh runtime validation. No claim of full1.7.4 acceptance or physical Fold verification.

## Frozen-source follow-up
Independent final narrow review of actual stored-payload accounting, scoped camera roots, native DatePicker/TimePicker FLAG_SECURE mirroring/dismissal, and main-thread/deferred CameraX rotation updates found no concrete Critical/Important blockers. Reviewed local05a1bb50 and GitHub frozen0af69436 share exact tree62a435b0bf0cdc36174cdbd69e894c2b5eaf224d. Exact-source refinement40085715961 passed349 JVM+6 privacy UI tests (zero failures/errors/skips); full release/signing/upgrade40085716021 remains required. No owner-device testing occurred.

Final gates now passed on frozen0af69436:349 JVM,353 full Android,71 focused UI,74 preflight and separate6privacy UI executions; original signer/code37 and debug+production36→37 upgrade/restart. Counts overlap. Final synthetic renders inspected; owner Fold acceptance remains pending. Remaining limitations above remain in effect.
