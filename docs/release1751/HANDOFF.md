# Private Gallery1.7.5.1 — NO-GO pending verification


## Capacity21 captured-output correction — release remains NO-GO

Run40157387905/source da6f510369532671b2760782f30d278500e74dd4, artifact12585467009 SHA256914df18059eb5aec75b072e61a10118625bf952b6032d4b5542e27c4c7cc0e9c. Digest/CRC independently verified. Compilation,349JVM and lint passed; ZERO Android cases ran. Preflight failed closed because the parser required mount string /data; the actual `df -k /data` returned Android17 alias /data/user/0. This is a new instrumentation parser defect, not a production or numeric capacity failure.

Raw before/after JSON, configuration, command status and identities are retained in video-repair/capacity21/. Independent reviewers verified SDK37, storage1751-37, disk.dataPartition.size=8G, actual total8302063616bytes, guest available8089088000bytes and host available85408133120bytes. Those budgets are satisfied. Original full2 initial capacity remains unknown; these numbers do not retrospectively describe it.

An exact captured-output host test reproduced RED. One-line finite allowlist now accepts only /data or /data/user/0; unrelated/system/other-user mounts, ambiguous rows and inadequate budgets still fail. GREEN11storage+6configuration+4method+7platform checks passed. No production, fixture, security assertion, threshold or timeout change. Original500MiB cases remain NOT RUN in21. No workflow was automatically retried.

The initial progress stop statement was premature: fresh raw evidence establishes adequate capacity and an exact parser-only cause, rather than failure of either affected case. A transient execution-environment disconnection then recovered with offline files intact. Root will use the existing owner authorisation only after exact independent review and a verified checkpoint for ONE bounded actual two-case execution. This remains NO-GO for release/signing. Full supported-platform regression, bothSDK debug/production upgrades, original certificate verification and physical acceptance remain mandatory. Stop if either actual case or budget fails; preserve new evidence before any further decision.

Previous full2: API36 115/74/395 anddebugupgradePASS; API37 115/74pass,393/395full with2capacityfailures. All video/Panic cases passed. Production signer/build/upgrades were skipped. No signed code39 APK exists.

Owner checklist: OWNER_ACCEPTANCE.md; all physical checks pending.
