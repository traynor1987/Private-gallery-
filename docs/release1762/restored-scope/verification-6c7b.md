# Exact-source affected verification6c7b

Source6c7b94543890eed4e79e95f3e3c49458ac4be907; run40769959605/job123104169923. APK compile/JVM/lint and host checks passed. Android emulator SDK37/release17, fingerprint `google/sdk_gslim_x86_64/emu64x:17/CE2A.260420.051.A3/16571990:userdebug/dev-keys`. This is not a physical Samsung result.

88 selected Android cases executed:85 passed,3 failed,zero skips/errors. Artifact12852318988 contains XML, screenshots, semantics, logs and fixture observations; downloaded ZIP SHA256a1ed87f3b829ab89bb8df29ac2d27cf149eed663ca893cce6d10f790588c0d4c was checked. The explicitly non-acceptance workflow failed because of the cases below, before protocol verification; its forced non-success diagnostic rule did not cause these failures.

| Failure | Actual boundary |
|---|---|
| HiddenUploadPickerPaging1762Test.actualTransferredLastItemBeyondFiveHundredSurvivesCancelReopenAndTypedPages | Actionable Cancel missing after actual HTML upload control touch. Logs show focused app window/no IME/visible control, but do not yet establish DOM activation or WebChrome callback. No paging/transfer success claimed. |
| NativeTorIsolation1762Test.packagedTorNativeFormsUploadsDownloadsAndNetworkSink | Tor script probe not rendered. Origin sees preflight GET only, no script-probe request; scripted test Go action needs actual-input verification. No full Tor body/sink pass claimed. |
| HiddenVpnLive1762Test.nativeChildPostsFormsUploadsAndRevokesOnOwnedVpn | Selected-tab closure did not restore visible address; tabs modal remained registered until teardown. Exact Close versus Done action failure is not established. Original restored-address assertion retained. |

Passing affected cases include actual system picker stop/Back, result-owner lifecycle/URI validation, capture policy, menu theme matrices and source-picker final thumbnail, Unix socket success/timeout/revocation, encrypted library/Done, dedicated task-parent retention and retirement boundary, owned-network loss and native presentation. In the failed Live method, HTTPS JavaScript POST, service-worker POST and website alert/confirm/prompt assertions before tab closure were reached successfully; they are partial observations, not a passing live suite or independent packet verification.

Rendered menu evidence observed headless drawing disabled before capture; real screenshot wrapper produced lightffedeff3/darkff22252a opaque surface samples with security flags unchanged. New shared panel/editor/library metadata source after2781 is not covered by this run. Original-key upgrade/signing/final source approval and owner physical Fold acceptance remain pending. Both activation flags remain false.
