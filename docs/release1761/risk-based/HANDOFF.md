# Private Gallery1.7.6.1 — signed risk-reviewed owner-acceptance handoff

**GO FOR OWNER ACCEPTANCE.** The independently approved patch gates passed and one original-key signed1.7.6.1/code42 APK is ready for physical Samsung Fold testing. This is not completed owner acceptance and does not relabel the historical full/Tor failure as fixed.

## Source and permanent policy

Recovered authoritative `4c080ffc9bd6483bc6c706fa277b528e023b8c23`, tree `f855162f4a15119924c93ae5daa2087f433d1ced`. Existing branch `release/1.7.6.1-editing-settings-polish` retained; no new branch, reset, force push, main merge or unrelated features. Installed baseline is1.7.6/code41, exact source `180a7ce7048f56371d1e0566b6333de65fd1aa75`.

Policy implementation backup `9fbff45776dc3d392736aefb1a8a26af223b7774` / `b948e2c942b44dcaa5a54fe13ae04436e5f5e9ee`. Exact Android-tested and signed source is **`33e9669daeb3bf5a3d2cb0aae4d3480fe4b538c2`**, tree `8d00a9b4c99fa7f855ba7c49efd5928b04c0f157`. [Patch run40433064873](https://github.com/traynor1987/Private-gallery-/actions/runs/40433064873), job121955157835, SUCCESS. Only this patch workflow ran; no duplicate fast/full/live-network admission. About27minutes versus the preceding failed full admission's roughly72minutes; this is a measured run comparison, not a future duration guarantee.

The final backup also contains a separately host-tested, independently reviewed future-admission guard. It is not claimed to have run Android instrumentation; production/instrumentation/build inputs remain identical to the tested33 source. See GUARD_REVIEW.md and the original implementation-approval-at-admission receipt in final-evidence.

Permanent policy: [POLICY.md](../../release-policy/POLICY.md). Major/high-risk/unknown-impact changes require full verification. Feature releases require appropriate comprehensive affected/integration/security coverage. Patches require independent change-impact approval, fresh affected tests/security/Android checks and original-key upgrade/signing. Emergency scope needs explicit risk justification; affected security boundaries cannot be omitted. Version numbering never waives risk.

Three execution lanes now exist:

| Lane | Required behavior |
| --- | --- |
| Fast development | Host/JVM/static/lint/compile/build; no emulator or signing; cached dependencies and source/artifact traceability |
| Patch acceptance | Approved exact scope/input hashes, fresh Android17/security/process tests, debug upgrade, original certificate/package build and original-key production upgrade/restart before upload |
| Full release/security | Existing complete Android17 suites, crypto/auth/media/browser/transport and live Tor controls, plus release upgrades/signing; original assertions/timeouts unchanged |

Monthly full-security dispatch is configured but **not scheduled-active until default-branch integration**, as required by GitHub's scheduling model. Current work remains on the requested release branch. Periodic non-release verification does not publish a signed candidate. The full marker-triggered lane remains available; its failing natural-timeout test was not removed or globally disabled.

## Complete impact review and security approval

[IMPACT.md](IMPACT.md) covers all10 production-file changes from installed1.7.6: Samsung external-editor/public-export authority, shared MainActivity return/lifecycle/concealment/settings, decoy presentation, storage callback lifetime, manifest handler visibility and min/target37. These are **not** dismissed as harmless UI: shared lifecycle and platform boundaries changed relative to baseline41.

The independently approved carry-forward basis is the exact already-tested52ef76 candidate. Current app/test/build/upgrade inputs match it;435 individual passing cases exercised those shared/platform changes. Browser/Tor, encryption, vault/Hidden credentials, ProtectedSessionState, actual PrivateVideoPlayer, network XML and dependency catalog remain unchanged versus accepted1.7.6. All four packaged Tor binaries match baseline hashes. Unchanged code alone is not the security justification: fresh critical controls, original raw evidence and independent review are required together.

Read-only reviewer `/root/risk_policy_security_review` reviewed the complete source diff, risk scope, original raw artifacts, actual CI implementation and final signed artifact/upgrade evidence. The initial conservative382fresh proposal was explicitly amended to152 after distinguishing fresh affected checks from individually verified exact-source carry-forward. Final decision: **GO FOR OWNER ACCEPTANCE**, subject to the residual limitations below. Exact152-method/29class scope and13 protected Git object pins are in approved-scope.json; original11implementation file hashes were checked before CI. Later guard rejects all other changed helpers/workflows/inputs, including rename/deletion paths, rather than merely reporting risk.

## Tests actually executed

| Gate | Verified result on tested33 source |
| --- | --- |
| Host/static security/build gates |PASS;158Python host tests before freeze; browser53/readiness13/SOCKS20/control-redaction16; original build checks retained |
| JVM |354/354PASS,0failure/error/skip |
| Approved Android17 instrumentation |152/152 exact methods PASS,0failure/error/skip |
| Actual browser-profile two-process isolation |Seed/force-stop/verify PASS |
| Synthetic debug code41→42 and restart |PASS; independent PINs/media/collections/settings/VPN preserved |
| Original permanent signing identity |PASS |
| Release lint/build and actual package/certificate checks |PASS |
| Synthetic original-key production code41→42 and restart |PASS; original certificate on both versions, same delivered candidate hash |
| Independent final archive/APK/source/security review |PASS; GO for owner acceptance |
| Later future guard-only tests |160host tests PASS; not a new Android admission |
| Physical owner acceptance |NOT PERFORMED |

Fresh scope includes external handoff/chooser/export/reimport/cleanup and immutable operation authority, fresh settings authentication, Hide Content/decoy/genuine identity, lock/background/Panic/session revocation, browser/vault isolation, local SOCKS/no-fallback/download publication, native date/time Save/Cancel and every previously focused27 video/security case. Primary and Hidden encrypted playback continuity/seek/speed/cleanup remain actual player tests, not mocked readiness. Software decoding and SELinux Enforcing are recorded before/after; the API37/image fingerprint matches the carried52 evidence. No new production or Android test/assertion changes were made in this policy task.

## Tests intentionally not executed and historical failure

The complete436 suite was **not** rerun. Exactly284 of its methods were intentionally not executed in this patch lane:283 have individually passing historical52 records; the one natural-timeout case remains a historical failure. Full method identities, fresh executions and435historical passes are separately recorded in final-evidence/patch/patch-summary.json. The435 historical passes overlap the152 fresh methods and must not be added together as a unique test count.

Untouched large-media500MiB/backup/camera/AI/browser-home checks and live Tor verification were carried rather than automatically rerun, under the exact-source independent impact approval. Fresh deterministic revocation/local transport tests are not described as fresh live Tor tests. No skipped case is represented as passed.

Original full run40420314223/source52ef76 remains **435/436 FAILED**. `HiddenRuntimeFailureTest.stalledConnectTimeoutDisconnectsRuntimeAndClosesSocket` remains FAILED: TRANSPORT_LOST at30091ms instead of required natural timeout. Disconnection/tab/socket cleanup, reuse rejection and no extra connection passed; previous positive live onion/bootstrap and separate live revocation observations passed. Cause remains unproved. No harmless-label, infrastructure-only or resolved-historical-cause claim is made.

That test remains unchanged and mandatory in the full lane for Tor, routing/DNS/proxy/VPN/dependency/configuration, relevant shared lifecycle/auth/revocation/platform changes and periodic full verification. New protected/unknown impact invalidates this specific exception and requires renewed independent review/full scope. The approved patch decision accepts the documented unresolved classification risk for the unchanged candidate; it is not a universal security proof or a permanent waiver.

## Original-key signed APK and integrity

| Property | Independently verified value |
| --- | --- |
| Package |uk.co.traynor.privategallery |
| Version |1.7.6.1 |
| Version code |42, next reserved code above installed41 |
| Minimum/target SDK |37/37 |
| Debuggable |false |
| APK signature |APK Signature Scheme v2 verified; one original signer |
| Original certificate SHA256 |`94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17` |
| APK SHA256 |`c7da24aba6f4c8d5f82d4fad8cd034cd65647812e627ac70fde16b61cef3867f` |
| APK bytes |104290142 |

Actual APK was independently checked with checksum/CRC-verified official build-tools36 apksigner/aapt. Hash matches the package upgraded in the production41→42/restart fixture. All FOUR production Tor ABI hashes remain byte-identical to baseline. No replacement production key generated; no owner media/credentials/encryption keys or formats changed. No uninstall/data-clear on the owner's device. Genuine Android metadata remains1.7.6.1/code42;0.0.1/Build4 remains in-app concealed presentation only.

Acceptance artifact12704296577 SHA256 `4499771c702e9fd913ede8e5aa6533a58cfec9e06d5be75d4f54b394ad07d861`; diagnostics artifact12703309774 SHA256 `b4b8fd07a79de992e76cdcc7ec7db831e2e631e55a25db38052c5d5289cbb72b`. Both independently downloaded/digest/allCRC/path verified. Raw native XML, JVM digests, process/upgrade/source/SDK/signature receipts and AUDIT.json are preserved in final-evidence. One signed acceptance APK is provided to the owner separately; no public stable release or main merge performed.

## Remaining acceptance and risks

Install the signed code42 APK over installed1.7.6/code41; **do not uninstall or clear data**. Verify existing Primary/Hidden credentials and media first, then use synthetic copies for edit/transfer trials. Follow [OWNER_ACCEPTANCE.md](OWNER_ACCEPTANCE.md) and its linked Samsung/settings checklist.

Physical Fold/Samsung editor compatibility, inner/cover-screen transitions, hardware decoder/high-bitrate/audio-sync performance, OEM screenshots/Recents and actual owner-media access remain unproved by emulators/synthetic upgrades. Test video playback/seek/speed, Panic while playing, locked entry/no visible frame, fresh unlock without unauthorised playback; test native metadata Save/Cancel and both vaults; confirm Samsung edited reimport and temporary cleanup. Historical Tor cause remains unknown; Android16 failures remain historical FAILED records on a no-longer-supported platform.

GO is for **physical owner acceptance only**, not a claim that the owner has accepted the release.
