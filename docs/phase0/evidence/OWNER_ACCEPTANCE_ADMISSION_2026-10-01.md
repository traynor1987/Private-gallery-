# Phase 0 owner acceptance and Phase 1 admission review — 1 October 2026

The original admission review below is preserved as history. The subsequent owner-authorized focused gate-closure milestone is recorded in [the current Phase 0 closeout report](../../../PRIVATE_GALLERY_2_0_PHASE_0_CLOSEOUT_2026-10-01.md). B02 research and synthetic restore/interruption additions supersede only the corresponding old gap assessments; the bounded owner upgrade report is unchanged.

## Final admission addendum — supersedes historical blockers below

**PHASE 0 RESULT: GO FOR PHASE 1 REVIEW**

On 1 October 2026, the owner confirms the remaining requested physical checks passed: the ordinary Primary PIN/biometric/lifecycle/revocation checks from the focused checklist are all good. The owner separately confirms an independent encrypted Private Gallery backup and its matching recovery material are retained. Record the grouped statement as owner-reported physical acceptance and private possession only. No recovery secret was requested, exposed, copied or recorded. No individual logs/timings/device metadata, destructive test, physical OEM transfer, actual power loss or owner restore is inferred.

Fresh GitHub metadata and job-step checks independently confirm exact `dafa877cf52158259d7882cb885ec3b376f5547c`: push [36860505285](https://github.com/traynor1987/Private-gallery-/actions/runs/36860505285), job `110363261383`, and PR [36860512264](https://github.com/traynor1987/Private-gallery-/actions/runs/36860512264), job `110363282277`, both COMPLETED / SUCCESS; every required step successful.

Together with the supported B02 policy, clean-state authenticated synthetic restore/interruption evidence, existing contracts/fixtures and accepted signed #51 release-input identity, this satisfies the current mandatory Phase 0 gates. Historical unresolved lists below are preserved as prior-review history and do not represent current blockers. Residual OEM/power-loss/enrollment limits and the separately CLOSED Hidden Browser/Tor design are unchanged.

PR #56 may be made ready and safely merged after verifying the evidence-only final head. Resulting main SHA and terminal main CI are recorded in PR #56 after verification, not asserted in advance here. The owner explicitly requires STOP after verified main. Phase 1 implementation, Hidden, a Hidden master key, Jenna Protocol and owner Primary migration remain forbidden.

## Historical admission review — superseded by the final addendum above

## SOURCE FACT — recovered repository identity

- Starting remote main: `a19218479eb9b9bcdb35ff0035fc78522263c891`, identical to the architecture audit baseline.
- Starting remote `phase0/security-admission`: `c0ce4013e65118843c4c69a9c868d99dc340cace` (tree `3a69e9933e62094e4e95e70c36cd28f20bb5e5fe`). Local HEAD matches; the recovered worktree had no uncommitted changes.
- [PR #56](https://github.com/traynor1987/Private-gallery-/pull/56): OPEN, draft, not merged, clean/mergeable; base is the main SHA above; 23 commits, 142 changed files, 6,299 insertions / 781 deletions.
- Exact binary-capable local diff command: `git diff --binary a19218479eb9b9bcdb35ff0035fc78522263c891 c0ce4013e65118843c4c69a9c868d99dc340cace`. SHA-256 of that command's output: `b322beac427101170317052e30ba8b62fa8aa930d4a0e376352aeca3b978ed77`. This identifies that Git-rendered diff, not an APK or source tree. Git object identities above are canonical.
- Phase 0 integration SHA: NONE. Main has not been changed or Phase 0 merged by this review.

## AUTOMATED TEST EVIDENCE — exact accepted source

| Workflow | Run / job | Verified outcome |
| --- | --- | --- |
| Normal Android push | [36843855926](https://github.com/traynor1987/Private-gallery-/actions/runs/36843855926), job `110309163630` | COMPLETED / SUCCESS on exact `c0ce4013`; all required steps successful; log reports 139/139 Android tests passed. |
| Normal Android PR | [36843862850](https://github.com/traynor1987/Private-gallery-/actions/runs/36843862850), job `110309186383` | COMPLETED / SUCCESS on exact `c0ce4013`; all required steps successful; log reports 139/139 Android tests passed. |
| Signed Device Acceptance Build #51 | [36851657287](https://github.com/traynor1987/Private-gallery-/actions/runs/36851657287), job `110334488562` | COMPLETED / SUCCESS on exact `c0ce4013`; signed release build, packaged exclusion checks, signature report, hashes and upload successful. |

Normal jobs retain debug JVM, lint, debug APK/instrumentation APK, evidence-variant JVM/lint/APKs, backup mutation/source/merged/packaged checks, immutable fixture equality, future vectors, no-secret, WireGuard/notices, retired runtime checks and API36 instrumentation. These are reviewed workflow results, not new local Android executions. They do not establish OEM or cross-platform transfer behavior.

Fresh local read-only checks: all 27 frozen fixture digests pass; corpus `SHA256SUMS` digest remains `cd26479fe9b077d81cd52b0c674900f0c475508e7081324cee396512770cc7bc`; source exclusion verifier and tracked-source no-secret scan pass. No full Gradle rerun is claimed for this documentation-only review.

## SIGNED BUILD EVIDENCE — artifact identity

Artifact `11155747957`, `private-gallery-signed-device-acceptance-apk`, belongs to run #51 and exact accepted SHA. Existing downloaded archive was hashed locally; its digest matches GitHub's artifact digest. APK bytes were hashed separately and match the archive's `app-release.apk.sha256`.

| Identity | Evidence |
| --- | --- |
| Git SHA / `build-sha.txt` | `c0ce4013e65118843c4c69a9c868d99dc340cace` |
| APK SHA-256 | `306d1f29a7bcaa7c28eccf024c04ecd59ff3da0e5562847182e14b72d217827d` |
| Artifact ZIP SHA-256 | `5acbef57c289e72149b49d2ba7e9dab2f0da6333d375a34633a13637253d38db` |
| Signer certificate SHA-256 | `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17` |
| Signature verification | Signed workflow ran `apksigner verify --verbose --print-certs`; log and artifact report say `Verifies`, v2 signature, one RSA4096 signer. Local hashing is independent; no new local `apksigner` execution is claimed. |

## PHYSICAL ACCEPTANCE EVIDENCE — owner report

On 1 October 2026 the owner explicitly reported physical acceptance passed: installed the exact same-signer Phase 0 acceptance APK from #51 over the existing installation, without uninstalling or clearing app data, and verified that the existing encrypted Primary Vault remained accessible and functional.

Record this as PASS for the reported in-place upgrade / existing Primary milestone. Do not leave that milestone globally marked unperformed. Do not infer individually executed PIN/biometric, collections, video seek, recovery, separate-device restore, OEM canary transfer, reboot, low-space/power interruption, stale callback, timeout or WebView measurements from the general report. Device/OEM/Android build and WebView versions were not supplied for this exact test. No production owner-data fault injection or migration occurred in this review.

## UNRESOLVED — genuine Phase 1 admission blockers

1. **B02 complete backup exclusion assurance.** `PHYSICAL_ACCEPTANCE.md` explicitly marks Android16 QPR2/API36.1 cross-platform omission/admission semantics and supported exclusion/opt-out verification as a mandatory unresolved admission gate. The implemented verifier covers declared cloud/D2D sections only. The current [official Auto Backup documentation](https://developer.android.com/identity/data/autobackup), rechecked 1 October, describes the independent cross-platform section, required iOS matching attributes and a general missing-mode enablement rule. That documentation alone does not prove a safe opt-out for this app without a corresponding iOS identity. No relevant-platform transfer execution or reviewed supported opt-out evidence is present. This is an evidence gap, not a demonstrated leak.
2. **Owner-safe recovery/restore evidence.** Audit sections 26/28 require owner-safe backup/recovery evidence and a confirmed recoverable backup. Repository acceptance checkpoints 7–13 and 22 require independent retained backup/recovery and separate clean-device restore proof. CI demonstrates synthetic format/restore cases; the owner's report does not establish the separate physical restore/possession gate. Do not request or publish the owner's secret.
3. **Remaining mandatory physical Primary gates.** `SECURITY_CONTRACT.md` and `PHYSICAL_ACCEPTANCE.md` retain Samsung/OEM cloud/D2D canary measurements, physical biometric/lifecycle and storage/interruption evidence (checkpoints 6, 9–19). No linked evidence closes them. The upgrade report must not be expanded to cover these experiments. Destructive tests belong exclusively to synthetic/expendable state on separate devices.

Hidden Browser persistence/provider/process and Tor are independently gated by audit section 28. They may remain closed without blocking Vault-only phases. The three blockers above concern Primary admission and cannot be dismissed under that exception.

## Required next evidence and decision

Resolve B02 with authoritative supported platform/provider semantics and relevant-platform verification. Record independent backup/recovery possession and separate-device synthetic restore evidence, then the remaining mandatory Primary physical checkpoint results or contract-permitted supported closures. Keep every test bounded to the actual measured device/artifact/state. Preserve the owner's working installation, ciphertext and only known-good backup. Do not repeat the already reported upgrade merely to generate redundant evidence.

The user explicitly requires stopping if a genuine Phase 1 admission blocker remains. Therefore this review records acceptance but does not merge PR #56, create a Phase 1 implementation branch, alter product code or claim GO. Historical records retain their original scope; this document supersedes their outdated assertion that the signed build and reported owner upgrade have not happened. It does not supersede unresolved Primary gates.

PHASE 0 RESULT: NO-GO FOR PHASE 1
