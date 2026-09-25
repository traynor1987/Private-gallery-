# Physical acceptance: transient AI memory gate and Vault video evidence

Starting main: 470e4eb538b08de6fc89ce464dde5e7187e4ee30. Owner reports lightweight
"Not enough free memory" and no Vault video playback. Samsung's supplied Device
Care screenshot shows 6.7 GB / 12 GB RAM; this is not a readout of Android
ActivityManager.MemoryInfo.availMem. No device bridge or exported diagnostics was
available to this session. Exact detected totalMem, availMem, memoryClass,
largeMemoryClass, lowMemory and Android threshold are therefore **not yet known**.
Do not fabricate them from the retail RAM size or the subtraction 12 - 6.7.

## Root cause and evidence

The old policy grouped lowMemory=true and availMem < 4 GiB into the same blocking
state. The total RAM gate was 8 GiB with a 90% OS-reservation allowance. These were
conservative engineering choices in local-ai-provider-feasibility.md, not a
measured SD1.5 peak, an Android requirement or an upstream benchmark. A blocked
attempt never started the worker, so the old generation-only diagnostics could
not reveal the rejection values. The UI also failed to explain a transient
resource state separately from compatibility.

Primary Android references:
- https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo
  totalMem excludes below-kernel reservations; availMem is a changing system
  estimate, not an absolute allocation guarantee. lowMemory and threshold report
  OS pressure. Do not add swap/RAM Plus to physical totalMem.
- https://developer.android.com/reference/android/app/ActivityManager#getMemoryClass()
- https://developer.android.com/reference/android/app/ActivityManager#getLargeMemoryClass()
  Memory classes describe the managed application heap, not a hard cap on native
  isolated-worker tensors. largeHeap is not enabled as a workaround.
- https://www.samsung.com/us/support/answer/ANS10001953/
  RAM Plus can move inactive apps into virtual memory. This is useful, but does
  not guarantee sufficient physical working memory or native allocation success.

The integrated runtime remains CPU, mmap, disk-backed parameters, disabled
prefetch, tiled VAE, one generation, 512px SD1.5 / 768px SDXL. Its pinned source
and model identities remain in the feasibility audit. No benchmark for this
exact adapted runtime/model/device combination exists in our evidence. **Estimated
peak runtime memory is not established**. In particular, the 2,132,696,762-byte
weight file is not a peak RAM estimate; graph buffers, conversions, allocator
behavior and mapped/reclaimable pages matter. A claimed numeric peak would be
invented. The new worker reports its actual getrusage(RUSAGE_SELF).ru_maxrss in
bytes on progress/completion, with unavailable/last-report caveats if killed.

## Guarded acceptance policy

The normal SD1.5 target remains 4,294,967,296 available bytes and total floor
7,730,941,132 bytes (8 GiB * 90%, integer division). Under that target, an installed
SD1.5 model may show **Low memory — owner attempt available**, only if:
- API/ABI/runtime and total RAM gates pass;
- Android lowMemory=false, severe thermal limit is absent;
- available RAM >= max(weight bytes + 1 GiB, cancellation reserve + 1 GiB).

The lightweight base attempt floor is 3,206,438,586 bytes (~2.99 GiB). The 1 GiB
headroom is an explicit experimental engineering margin, **not proof of a safe
peak or a newly asserted model minimum**. It is constrained by live pressure
monitoring and disposable isolated execution. The cancellation reserve is the
larger of 512 MiB or Android's threshold + 256 MiB. If the OS threshold is large,
it raises the attempt floor. Real low memory, insufficient total RAM, unavailable
runtime, unsupported ABI/API, thermal limits or absent/corrupt model cannot be
overridden. Advanced retains its 8 GiB available requirement; this change does
not extend the experiment to SDXL.

Owner confirmation is per request, never persisted, never implied by Auto or
prior cloud consent. Auto only picks normally ready providers; it does not
silently authorize the memory experiment or paid cloud fallback. Recheck at
request, after hash verification, and after acquiring the single-worker gate.
During inference, Android memory/thermal state is checked each second and the
worker's trim callbacks still terminate it. Crossing the reserve cancels and
wipes the worker/shared buffers. Worker death, allocation failure and resource
cancellation fail gracefully without importing a result. No model is retained.
A fast allocation or OS kill can precede a monitor sample; isolation limits the
failure, it is not a guarantee that Android never kills any process.

## Diagnostics and acceptance

Settings -> Debug: Refresh AI memory diagnostics captures exact byte values,
managed memory classes, heap limit, lowMemory, OS threshold, provider states,
normal/admission/cancellation thresholds, and last reported measured worker peak.
No image, prompt, Vault filename, token, device identifier or browsing data.

1. Upgrade the signed acceptance APK in place. Refresh AI diagnostics before any
   attempt; supply the displayed values. Compare simultaneous Device Care screen.
2. With SD1.5 installed select Lightweight. Under normal resources generate
   normally. Under eligible Low memory, Generate must show Try once / Cancel.
   Cancel makes no request; every retry must ask again. Hard-blocked states must
   not offer an override. No cloud use occurs on this path.
3. Attempt a small non-sensitive image. Record outcome, elapsed time and worker
   peak. Cancel, background/lock, retry, and verify encrypted immutable Save Copy.
4. Open one affected Vault video. If it fails, return to Debug and provide only
   Vault video diagnostics. The trace now distinguishes decrypt/authentication,
   player preparation, track support, first frame, position progress, lifecycle,
   suppression and numeric errors. No physical video fix is claimed without this
   evidence. CI now requires a frame plus advancing playback for an encrypted
   fixture instead of accepting READY alone.

No weights were downloaded, no paid request was made, no production release is
part of this acceptance work. The original no-public-plaintext and Vault policies
remain unchanged. Final CI/build results are reported with the acceptance handoff.

## Follow-up: Fold transient-memory gate (2026-09-25)

The earlier experimental `model.bytes + 1 GiB` admission floor is removed for
Lightweight. Android `availMem` is a snapshot that includes reclaimable pages;
the safetensors file length is neither resident model memory nor peak process
memory. A 12 GB retail Fold with ~8 GB shown as used in Device Care must not be
rejected on that subtraction. The actual `MemoryInfo` snapshot still matters.

Lightweight keeps the existing conservative *total usable* RAM floor (8 GiB ×
90%; engineering policy, not a measured peak), supported ABI/runtime, installed
hash-verified weights, and severe thermal gate. `lowMemory=true` or
`availMem <= max(512 MiB, threshold + 256 MiB)` temporarily blocks any attempt.
Between that pressure floor and 4 GiB recommended available, the UI offers a
one-time, per-request owner warning/attempt. Above it, generation is allowed.
This does not modify SDXL's existing admission thresholds. The GPU and CPU
workers remain separate disposable processes; model verification is followed
by eviction of in-memory gallery and Vault thumbnail caches. Local source
preparation decodes no more pixels than the model's 512²/768² target before
re-encoding and passing bounded shared memory to the worker. During inference,
Android low-memory and severe thermal states or crossing the pressure reserve
cancel the generation; worker trim callbacks kill the disposable process.
Runtime allocation failure returns without saving a result. OS kills can still
occur between polls; no preflight can guarantee an allocation.

Debug diagnostics now distinguish ALLOWED, WARNING and BLOCKED with a fixed
reason, model file bytes versus resident bytes (unavailable until measurable),
Android threshold/lowMemory, memory classes and heap limit. Worker peak RSS and
load/generation time are recorded after a physical run; RSS may exclude GPU
allocations and the killed worker's final peak. **No physical Fold benchmark was
performed in CI or this development environment.** Do not infer the peak from
the file size or replace SDXL requirements with an SD1.5 result.

Physical acceptance: Refresh AI memory diagnostics before starting; use the
same small, non-sensitive input and prompt with Lightweight, acceptance backend
CPU then GPU/Vulkan, and finally Auto. Capture the status, exact memory fields,
actual backend, load and generation milliseconds, peak worker RSS, minimum
available memory and thermal maximum after each run. Confirm a warning asks
before attempting, pressure/thermal blocks, cancellation and failed worker
leave the original Vault media untouched. If Android reports a pressure block,
allow it to recover and refresh; do not override an active lowMemory state.
