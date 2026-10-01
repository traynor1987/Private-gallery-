# Phase 1 signed and physical acceptance

Status: NOT RUN. Phase 0 acceptance remains complete; this checklist accepts only the new exact Phase 1 candidate. Record PASS/FAIL/NOT RUN explicitly. Do not infer a physical pass from API36 emulator results.

1. After final candidate CI passes, open **Signed Device Acceptance Build**, select `phase1/primary-scoped-security`, and verify the branch still equals the full SHA in the handoff. Run the manual artifact-only workflow. Download `private-gallery-signed-device-acceptance-apk` and compare `build-sha.txt` with that SHA. Preserve its workflow URL, APK SHA256 and public signing certificate SHA256; never share signing credentials.
2. Compare the certificate with the installed permanent-signer release and verify package/version continuity. Stop on mismatch or downgrade. Retain the already verified independent encrypted backup and matching offline recovery possession privately; do not disclose the recovery secret or rotate it for this test.
3. Install the exact signed APK **in place** over the owner app using the ordinary forward upgrade path. Never uninstall, clear data, change signer, downgrade or install the debug/instrumentation package over owner data. No owner-data migration or reencryption is permitted.
4. Record device/OEM, Android/API/build, WebView provider/version and source/artifact identities. Authenticate with the existing PIN and biometric method. Verify owner-selected existing Primary images/video/seek, collections/Favourite, edits/provenance, vaultOnly/origin restrictions and Recently Deleted. Record observations rather than assuming untested actions work.
5. With disposable synthetic media on a separate test installation/device, exercise COPY/MOVE and cancellation/source preservation; crop/local/AI saved copies; Create Image references; Browser chosen Vault upload; download/image/video acquisition; and backup/restore. Confirm results go to the originating Primary destination. Real AI submission may incur account credit and requires the existing explicit consent; no provider secrets, prompts, private media or URLs enter evidence.
6. Exercise lock, background expiry, delayed completion and lock→unlock during preview/image/video/editor/AI/Browser import. Old callbacks/results must not appear or commit in the new session. Video stops and future reads are denied. Exercise configuration recreation, actual process stop/relaunch and reboot; process restart must begin locked. Record temporary-file cleanup and best-effort mutable-state clearing without claiming forensic/GPU erasure.
7. On synthetic devices only, test PIN change/recovery replacement/biometric mutation and backup restore/failed restore/partial-state/fault cases. Confirm independent foreign test canaries are unchanged. Do not corrupt owner media, induce owner storage failure, rotate working owner recovery, delete the sole verified copy or run destructive instrumentation against owner storage.
8. Check ordinary Gallery/Vault navigation, WireGuard Browser gating, Settings and update checks without publishing or installing unrelated updates. Record Vault open/index load, preview scroll/image open, video startup/seek, AI and Browser upload preparation latency compared with the retained ordinary baseline; report stalls/regressions and unmeasured paths.

Return only sanitized evidence:

```text
Candidate branch / full SHA:
Android CI run / conclusion:
Signed workflow run / conclusion / build-sha match:
APK SHA256 / public certificate SHA256 / installed signer match:
Device / OEM / Android build / WebView:
In-place install; no uninstall/data-clear/downgrade/migration:
PIN / biometric / existing media / metadata / trash observations:
Synthetic import / editor / AI / Browser / backup observations:
Lock / expiry / ABA / recreation / actual restart / reboot observations:
Performance comparison and unmeasured paths:
Failures or pending checkpoints:
```

Do not merge, publish or begin Phase 2 automatically. GO is available only when every mandatory Phase 1 gate passes; it authorizes Phase 2 review only.
