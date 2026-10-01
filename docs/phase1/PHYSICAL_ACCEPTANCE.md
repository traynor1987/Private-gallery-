# Phase 1 signed and physical acceptance

Status: **AGGREGATE OWNER-REPORTED PASS**, 1 October 2026.

Accepted candidate: `3d0edcd3cf563204beb33698486948bdaa809924`, Signed Device Acceptance Build **#53**, run `36912086850`. The owner expressly states that focused replacement Phase 1 physical acceptance PASSED, that the exact replacement signed APK was installed **IN PLACE** over the existing Private Gallery installation, and reports **"All working."**

This is aggregate owner-reported PASS for the requested focused checklist below. The existing owner Vault/data remains in use. No uninstall/data clear is authorized or reported. No granular observations, device/OEM/Android/WebView identity, individual lifecycle/AI/Browser/recovery/fault outcomes or latency measurements are inferred from that aggregate statement. No recovery secret was requested, received or recorded. Emulator evidence remains separate from physical evidence. The superseded `77c95d1` / signed #52 is historical evidence only.

The following is the requested checklist retained for traceability; its entries are not newly invented per-test observations. Phase 0 acceptance remains complete.

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

The owner's 1 October final-acceptance instruction authorizes final Phase 1 review, documentation/PR updates, safe merge and exact post-merge main CI verification once all gates pass, without another confirmation. It does not authorize publishing or Phase 2 implementation. Stop after verified green main. GO authorizes Phase 2 review only.
