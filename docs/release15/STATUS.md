# Private Gallery 1.5A historical execution status

Historical checkpoint. Current permanent Hidden integration and its exact-source verification are documented in [PERMANENT_HIDDEN_STATUS.md](PERMANENT_HIDDEN_STATUS.md). Statements below describe only the older checkpoint.

Superseded development order: see STAGE1_PROTOTYPE.md for the owner-approved functional-first strategy and current prototype boundary.

Development foundation only. No media feature, signed release candidate, production release, owner install or migration.

## Exact preservation and branch identities

| Item | Commit |
|---|---|
| Latest durable V2 recovery and preservation/v2-task3b-2026-10-17-d6c9ab4 | d6c9ab454a9f0ea90bcfe12783eb68e51acdd368 |
| Exact local V2-history archive branch preservation/v2-local-history-2026-10-17 | 24c92a6ee9019069de48671cd29576418e0de8f1 |
| Stable pre-V2 V1 baseline, version 1.0.27 / code 28 | a19218479eb9b9bcdb35ff0035fc78522263c891 |
| First 1.5A core/credential checkpoint | 944c6b5227e1236eda5c3633c3ce131f1bcbbbfc |
| Integrated 1.5A source on release/1.5-hidden-vault | f381ffa1a1533ee92e3968de2214575457bc22a5 |

Main remains 93ed56fbfcd6cb04731a87452dbcbf2e6d6bcb10. Protected Phase3 branch remains 79bb1928f3fbe16e935e587a3fe594158cb3f529; PR59 remains open, draft and unmerged. Existing V2 source, reports, evidence and plans are retained through the preserved history. Other inaccessible Work executions cannot be exhaustively inspected; no claim is made about unpushed work there.

The additional bundle preserves exact local commits 52599b07893e98c3608cd87d8a9fb0dde675c22f, 05f0c452703921f7d77a52a8d174ad6e44af739c and bde432a384093066ded04ac89b82cea9835437cc. Download verification and fresh-repository unbundling passed. Bundle SHA256: a78808ff3021046b837ac8a2c30545b619c7854f808589ad20d87fcdf43c5eaa.

## Implemented within 1.5A

- Original transient secret sequence: 5 Installed, 1 About version, 4 Installed; reset on timeout, navigation and background.
- Separate PIN-only setup/unlock dialog, confirmation, explicit Hidden lock and 60-second hard session timeout.
- Independent random Hidden key; versioned domain-authenticated AES-GCM PIN envelope, PBKDF2-HMAC-SHA256 with 600000 iterations; separate Android Keystore outer key/alias and no-backup credential storage.
- Atomic ciphertext persistence, bounded reads, ciphertext read-back, corruption/key-loss rejection and retained failed records.
- Shared serialization of credential writes; matching Primary/Hidden PINs rejected at Hidden setup and Primary creation/change/recovery replacement, including backup restore installation.
- One-shot authenticated attempts, stale completion rejection, best-effort array wiping, structured IO ownership and close/background/disposal revocation.
- Secure dialog window and explicit cloud/device-transfer domain exclusions.

## Verification

Host: 373 assertions pass, including wrong PIN, malformed/tampered envelopes, attempt revocation/supersession, clock rollback/deadlines and 100 unlock/lock cycles. Three existing Node browser-helper tests pass. Source secret, retired AI and WireGuard audits and diff checks pass.

Core-only CI581: SUCCESS on 944c6b5. JVM tests, lint, debug APK and AndroidTest APK compilation passed; all 131 Android cases passed, including five namespaced Hidden credential cases. Evidence: evidence/ci581-summary.txt.

Integrated CI582: SUCCESS on f381ffa1a1533ee92e3968de2214575457bc22a5. JVM tests, lint and both APK builds passed; all 134 Android cases passed with zero failures/skips. This does not validate later Stage1 prototype source.

Independent read-only review found no concrete scoped code blockers. The reviewer identified a test-description gap: the UI background case clears unsubmitted input and does not hold an active IO operation. It was renamed accurately. Core stale-attempt tests pass; controlled in-flight Android lifecycle instrumentation remains outstanding. Review details: SECURITY_REVIEW.md.

Local Gradle is unavailable because the SDK/dependency cache is absent and distribution download is blocked. Android results above come from the explicitly linked GitHub runs, not local executions.

## Compatibility, files and remaining work

Primary encryption/index/video formats, vault repository/media code, manifest, package identity and signing/version configuration have zero diff from V1. The accepted permanent signing certificate is recorded from historical evidence in FOUNDATION_PLAN.md; no signed APK was produced or cryptographically verified in this execution. No owner media was accessed.

Changed source: MainActivity.kt settings entry; PinVaultKeyStore.kt credential collision guard; four core/hidden classes; ui/HiddenFoundationDialog.kt; data_extraction_rules.xml. Added/updated tests: HiddenFoundationChecks.java, HiddenFoundationTest.java, HiddenCredentialStoreTest.kt, HiddenCredentialSeparationTest.kt, HiddenFoundationDialogTest.kt and ProfessionalUiTest.kt. Supporting changes: existing Android workflow release-branch push filter, dependency-free runner and release15 documentation/evidence.

No Hidden media, collections, metadata, thumbnails, viewing, deletion or export/recovery is implemented. A missing/corrupt Hidden device credential blocks conflicting Primary PIN writes without replacing existing data; normal Primary unlock remains available. Setup already writing ciphertext may finish after dismissal, but its late result cannot unlock/reopen Hidden. No universal JCE/native/graphics copy retirement, forensic invisibility, brute-force immunity, V2 security equivalence or automatic future V2 migration is claimed.

Next work stays inside 1.5A until its remaining validation gates pass. Do not begin 1.5B automatically. Signing, full device fault matrix and owner acceptance belong to the later release-candidate gate. No current APK should replace the owner's working Gallery during development.
