# Local AI accelerated inference acceptance

Starting main: `39617df3c65efada01e3f046fb987a4b2da6ce79`.
Baseline debug APK: 138,407,792 bytes. Baseline permanently signed acceptance APK: 108,568,230 bytes (run 36161929722). Record the new CI/artifact values before acceptance handoff. No production release.

## Implemented combination

Existing audited stable-diffusion.cpp 19bbbca1 / ggml 4bf5f600, static CPU and Vulkan backends, exact existing SD1.5/SDXL model catalogue. No new weights/download format and no model migration. NPU is explicitly unavailable; see the separate licence audit for positive Qualcomm SDK terms and the remaining complete-editing-model/adapter gap. Do not describe NPU as legally forbidden or advertise that it runs.

Auto probes Vulkan in a dedicated non-exported app-UID process. The pinned backend enumerates supported Vulkan devices, initializes a device, and this integration runs a bounded synthetic tensor addition before accepting any image descriptor. Vulkan requires loader API 1.2 and device storageBuffer16BitAccess; native runtime checks actual driver features. No Fold/chipset name allowlist is used. Auto is a preference for available acceleration, not a benchmark-proven fastest ranking. Hardware speed is unmeasured until the owner tests it.

No compatible Vulkan device, failed mandatory network restriction, failed probe, or probe timeout/death permits CPU fallback only after the previous connected worker's death is confirmed. Unconfirmed termination blocks subsequent generation. Forced GPU failure reports unavailable without silently timing CPU. Failure after model/image submission stops the request without another automatic generation. CPU executes in the existing isolated UID; Vulkan registration is disabled in that worker before ggml initialization.

## Security boundary

Android ordinary isolated processes cannot access GPU devices. GPU processing therefore uses a separate app-UID process, not the isolated CPU service. Before probing it must install an all-thread seccomp filter denying new IPv4/IPv6 sockets, connect and send syscalls. This is defence in depth, not an isolated UID: filesystem/Binder permissions remain, and writes through already connected descriptors are not categorically denied. Reviewed runtime has no networking backend, no image upload path, no dynamic backend loading and no arbitrary downloaded native code. Only reviewed native application code and Android's driver run. This boundary is materially different from CPU and is explicitly documented rather than described as permission-free.

No Vault keys, token, source path or filename are passed. The GPU process starts with the default Application, receives a read-only verified model FD and bounded shared pixel buffer only after probe and a fresh resource admission check. It has no retained unlocked Vault object. Existing lock/background cancellation kills/unbinds the worker; shared buffers are wiped in finally. There is no plaintext image temp file. Keep AI edits inside Vault, immutable source and encrypted Save Copy use the unchanged provider-neutral pipeline. Remote provider selection/consent and settings are unchanged.

Existing RAM/thermal thresholds, low-memory owner confirmation and one-generation mutex remain. GPU allocations can increase pressure, so the existing one-second stop guard remains authoritative; no swap is added to physical RAM and no thresholds are lowered. Native getrusage peak RSS is a process measurement, not total GPU allocations. Minimum system available memory is sampled every 500 ms; thermal start/end/max are sampled. These are honest available metrics, not a claimed exact global GPU peak.

## Measurements and limitations

Debug/Acceptance -> On-device AI diagnostics offers session-only Auto, GPU (Vulkan), CPU. Provider selection in AI editing remains separate: choose Local Lightweight. Changing a backend never chooses cloud. A run records actual selected backend, driver probe time, model-context load time, generation time, inference-client duration, last reported worker peak RSS, minimum sampled available memory, thermal start/end/max and fixed outcome. Model load includes native context construction. Integrity verification happens before these timings and can warm the operating-system file cache; these are not cold-storage benchmarks. The client duration excludes that verification and initial preview rendering. A separate initialization value remains unavailable when it cannot be independently measured. No prompt, image, filename, token, browsing data or raw native errors are logged. Six sanitized runs are retained only in memory.

| Physical backend | Model load | Generation | Peak RSS / min available RAM | Thermal | Status |
|---|---|---|---|---|---|
| NPU | Not measured | Not measured | Not measured | Not measured | Not integrated |
| GPU/Vulkan | Pending owner | Pending owner | Pending owner | Pending owner | Requires actual Fold validation |
| CPU | Pending owner | Pending owner | Pending owner | Pending owner | Fallback retained; no inferred performance |

Ordinary CI uses synthetic fixtures and a bounded compute probe, never production weights or paid requests. Compile success does not validate production-model output or Fold performance.

## Physical checklist — Lightweight first

1. Install the signed acceptance APK over the existing app. Do not uninstall/reset. Confirm Vault access, Replicate configuration and installed model status survive.
2. Keep only Local Lightweight selected in AI editing. Download the existing 2,132,696,762-byte SD1.5 file only if absent. Do not download SDXL for this first test. Keep the same source image and short nonprivate test prompt for comparisons.
3. Let the phone cool, close unnecessary applications, and record Refresh AI memory diagnostics. Keep existing OOM gates. If it says Low memory, only the existing explicit one-time owner attempt can proceed; genuinely insufficient RAM stays blocked.
4. In Debug/Acceptance select GPU (Vulkan). Return to the SAME editor, run a small Lightweight edit (maximum input 512, fixed existing 20 steps). Record actual backend, load and generation times, peak RSS, minimum available memory and thermal start/end/max. Confirm preview is sensible and Save Copy creates a new encrypted Vault item without altering the original.
5. Let temperature/memory settle, select CPU and repeat the same edit/input dimensions. Record the same metrics. Repeat each backend three times if acceptable to assess variability; there is no warm-session retention. Do not compare a stopped/failed run with a successful one. Native random seeds differ, so this is a latency comparison, not an exact-pixel equivalence test.
6. Select Auto and repeat. Expect Vulkan if the probe succeeds, or visible CPU fallback if unavailable. A forced unavailable GPU must fail clearly rather than silently report CPU timings as GPU. NPU must remain unavailable.
7. Test Cancel during model preparation and during generation. Retry only after termination. Test Home/background and Vault lock during generation; reopen/unlock and verify no stale preview/result or continued generation. Verify the next attempt works, or reports a still-stopping worker without concurrent processing.
8. In airplane mode, repeat one installed-model edit, preview and encrypted Save Copy. No API account/token is needed and no cloud confirmation should appear for explicit local processing.
9. Verify normal editor masking/rotation/crop, Vault-only derivative restriction, conventional editing, Browser/Vault and video playback regressions. Video acceptance remains separate; acceleration does not claim to repair prior physical playback failure.
10. Return sanitized run diagnostics and observed timings/errors. Keep SDXL disabled for physical acceptance until Lightweight passes; evaluate its existing 6,938,078,334-byte optional model separately only then. Return backend override to Auto after comparison.
