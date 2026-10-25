# Bounded CI storage recovery


## Reviewed capacity-precondition correction — affected execution pending

Starting backup9b1f78b994c0c72e82fab5c56588379cfaaa36f3/tree2be52ae0de3902c6b250f38782ef48687ef71da7 was independently verified by GitHub commit/tree and git ls-remote before editing. Production main tree remains303d7f80f3b0a6fe716294943b5dea02de58cdb0. No production source, crypto format, credentials, vault authority, privacy protection or assertion changed.

Fresh CI AVDs now request8GiB userdata. A CI/qemu/actualAVD-name/supportedSDK guarded helper records actual AVD configuration, guest df/inodes, host df/statvfs and private synthetic-debug footprint. It requires at least6GiB actual data partition,3GiB guest available and4GiB host available; a requested size alone never passes. Full regression preflights every phase and archives a failure-preserving final snapshot; production upgrades also check the budget. The two existing500MiB fixtures only add generic actual Java File usable/total and cleanup-success/root-exists logs, including import/copy stages.

The existing focused workflow has an explicit [storage-recovery] commit mode: exactly the two original API37 cases on fresh storage1751-37, with pre/post snapshots and raw XML validation. It avoids repeating unrelated82-case dual-platform focused work. Normal13+69 focused mode and complete115/74/395+bothSDK debug/production upgrade/signing gates remain mandatory and unchanged. A storage-only green run is NOT a normal focused or full release pass.

Independent recovery review caught a rejected helper label before CI; it was corrected and an actual shell-dispatch/archive host harness now tests it. Both reviewers approve one bounded affected execution. Security review requested cleanup booleans and early failure JSON; these were added. Host9storage+6configuration+4method+7platform tests, shell syntax, YAML parse and diff checks passed. Exact source/log hashes: CAPACITY_PRE_CI_VERIFICATION.json and capacity-host-checks.txt. Historical initial capacity remains unknown; this is a corrected resource precondition, not proof of why the old volume filled. Stop if actual capacity or either case fails. Proceed to one full gate only after raw two-case XML, capacity and cleanup footprints are independently verified. Signing remains pending/NO-GO until all full release gates pass.
