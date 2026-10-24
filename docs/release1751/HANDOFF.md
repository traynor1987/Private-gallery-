# Private Gallery1.7.5.1 / code39 — acceptance repair handoff

Status: **NO-GO pending focused runtime, full regression, signing and upgrade verification.** No physical acceptance is inferred from automated tests. No main merge authorised.

## Verified baseline and scope

Initial authoritative1.7.5 branch SHA `c593215cea53d05489fb6215ee4514a9d9e7e1df`, fetched tree `b6d5b7076f608db5e8d89f04efc824e16e876bc2`, matching clean local checkout. Owner-installed1.7.5/code38 exact signed source `69bfade789df4a49d911091bb8cf8205c88a3a39`; APK SHA256 `a5d42d3420fa639828546ca84de1ef2d1bd0971313e444af2d50b8a5d17ec114`. Repair branch `release/1.7.5.1-privacy-viewer-repair`; target1.7.5.1/code39. Previous release/V2 branches preserved. No owner media/device, credentials or encryption formats changed; no uninstall, data clear, replacement key, force push or main merge.

## Four root causes and repairs

1. **Menu blackout:** focus was treated as backgrounding. Opening an owned Popup/Dialog calls MainActivity.onWindowFocusChanged(false), which unconditionally added a black native curtain and FLAG_SECURE. Focus-only changes now pause playback without covering or disposing images. Actual user leave, stop and screen-off still cover and increment a concealment epoch. Every pause still adds FLAG_SECURE for Recents, including older Android. Foreground curtain removal waits for a matching committed safe composition and pre-draw.
2. **Screenshots:** focus-triggered cover forced secure-window state even with permission enabled; owned windows could inherit it. Foreground screenshot restoration now resolves current presentation after cover handoff. Hidden has an independent default-deny preference and authenticated setting; Primary cannot override it. Independent review caught the underlying Primary Gallery route remaining when Hidden becomes visible; the policy now checks !hidden.visible before allowing the Primary exception. Windows explicitly secured for credential/provider secrets retain their existing protection.
3. **Misleading image error:** the viewer had no explicit privacy-covered state. It disposed pages on pause, automatically tried to reload on resume and collapsed non-cancellation failures into a single generic media error; backgrounding can invalidate the old read authority. It now preserves only a concealed selection/error state, displays Preview hidden and the requested explanation, and requires explicit Reopen. Primary renews authority for the exact current viewer/session; Hidden checks current session/owner. Locked/expired authority does not decode. Late callbacks are cancelled/epoch-checked and buffers wiped. Genuine decode/integrity/missing/unsupported failures retain the real error state; concealment never relabels them harmless.
4. **Panic inaccessible:** opt-in controls were buried below long security settings and absent while protected settings were open. Three-finger activation was disabled by default, and its Activity handler did not latch/consume the rest of the gesture. Panic controls now appear first under Settings → Privacy & Security, remain visible in protected settings, and provide enable, three-finger option, optional floating Quick Lock, explicit Quick Lock now and a safe-confirmed test. The native pointer gate triggers on the third simultaneous finger, consumes the remainder once, and respects disabled/touch exploration. Quick Lock works without the gesture. Existing revocation and camera-journal finalisation are reused.

## Verification evidence

Baseline run40108215519 compiled/JVM/lint passed but its5UI cases hit a fixture error (test rule replacing an Activity-owned composition). Corrected fixture uses Activity.setContent, then baseline40109107048 reproduced4failures: native cover for sort/dialog, missing intentional covered-preview UI and missing Panic section; genuine corrupt media passed. Neither baseline run is claimed green.

Host28Python tests,8release-policy checks and18gesture-gate cases passed. Existing13capture policy/18journal,373Hidden foundation, gallery isolation/revocation/encrypted records,20/100/500MiB authenticated stream/seek/tamper and53browser-policy checks plus secret scan passed. Focused source5dcc76c8 stopped at one unqualified Material3 Switch; corrected in c1271f49 before rerunning. Initial repaired runtime40110348909 passed36/40 Android, including the four owner-defect regressions, native Panic and video. Four fixture/environment failures are documented in the recovery ledger and corrected for rerun; API32/full/signing remain pending.

[Independent security review](REVIEW.md) records the screenshot isolation finding and required foreground/authority/gesture boundaries. No additional demonstrated cross-vault or late-image disclosure found; this is not a security guarantee. Physical Recents/frame behavior and OEM gesture handling remain owner checks.

## Signing and recovery

Required production package `uk.co.traynor.privategallery`, non-debuggable1.7.5.1/code39. Original certificate SHA256 `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`. Acceptance workflow requires exact-source focused and full reports, original-key package/signature/hash checks, then synthetic38→39 debug and production in-place upgrades/restarts from source69bf. Preservation assertions include independent PINs, encrypted media/ciphertext, collections, settings and VPN. No39APK is available until these gates pass. Recovery ledger: [V1_7_5_1_RECOVERY.md](../../V1_7_5_1_RECOVERY.md).

CI uses one writer, read-only independent review, coherent checkpoints, path/concurrency-filtered focused runs, a skip-CI recovery backup and a separate stable-candidate marker for complete gates. Focused tests target API36 inner dimensions and API32 pre-33 cover dimensions. Full regression is not triggered after each edit; genuine failures are investigated before affected reruns. Mandatory security/signing/upgrades are retained.

## Remaining limits and owner acceptance

Pure focus-only overlays do not activate the black curtain; playback pauses and actual backgrounding remains protected. Paused windows are temporarily secure; foreground screenshot preference returns after safe handoff. Media resume remains session-only within the open viewer and bounded rendering remains as1.7.5. OEM gesture interception, touch exploration, cover/inner transitions, visible Recents frames and actual owner data preservation require physical testing. Quick Lock is the explicit fallback. Prior1.7.4/1.7.5 export and transport limitations remain; VPN+Tor is unavailable.

Complete the [short Galaxy Z Fold checklist](OWNER_ACCEPTANCE.md) after installing only the final verified APK in place. Physical acceptance remains pending.
