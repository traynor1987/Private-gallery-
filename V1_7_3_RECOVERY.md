# Private Gallery1.7.3 recovery

Verified baseline release/1.7.2-camera-ai-refinements at a478b447876640b730c494c6e31e2384f096c786, clean checkout and no relevant active Java/Gradle/adb/emulator/git process. Remote independently verified. Owner reports1.7.2 installed: code35. Package uk.co.traynor.privategallery, original certificate94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17. Prior full tests, original-key35 acceptance and synthetic34→35 upgrade documented in V1_7_2_RECOVERY.md; complete physical acceptance not inferred.

New branch release/1.7.3-smart-unlock-gallery-sort created from that exact baseline. Targetcode36. Earlier branches/V2 recovery and blue icon untouched. No owner data/keys/credentials modified.

Design and implementation plan are under docs/superpowers (2026-10-24-smart-unlock-gallery-sort). Owner authorises autonomous execution. Initial findings: PIN length is not persisted; legacy credentials support variable lengths, so retain explicit confirmation where unknown. Primary auto-biometric cryptographic flow already exists; refine lifecycle/intent rather than replace it. Phone Gallery currently prioritises DATE_TAKEN. Vault importedAt sometimes represents capture time, so preserve old indexes and add authenticated supplemental dates as needed.

Status: baseline/design checkpoint; implementation/tests/signing pending. Next: numeric keypad and discreet route/biometric cancellation tests; six sorts + encrypted per-context metadata/preferences; independent security review; fresh regression and original-key35→36 upgrades. No1.8 face/visual indexing or main merge. Push coherent milestones, verify full remote SHAs and update this file before lengthy CI.
