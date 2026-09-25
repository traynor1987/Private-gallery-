# On-device AI provider feasibility

Audit date: 2026-09-25. Starting main: `49bb9bc5f2239caec1b66d04ee5e10a48a3a8fac`.
Status: feasibility audit completed; implementation under verification; physical-device validation pending.
Private Gallery's current LICENSE is proprietary/all rights reserved. The current production VPN is WireGuard-only. Historical OpenVPN/GPL work is not part of this APK.

## Reference projects: separate rights, not blanket permission

| Component | Examined revision / licence | Decision |
| --- | --- | --- |
| [Local Dream](https://github.com/xororz/local-dream) application and native orchestration | `440899fb6dbd14712b4a1c8fe4d6b0e1c9f68a36`; LICENSE: CC BY-NC 4.0 | Technical reference only. No application, JNI, pipeline, conversion, device-gating or UI code copied. |
| [Nightmare Mobile](https://github.com/AbrahamPaulJ/nightmare-mobile) application, backend modifications | `2c038b2af798d9183e11f22be23831cd91d3bf5c`; LICENSE and NOTICE: CC BY-NC 4.0 | Technical reference only. NOTICE explicitly attributes inherited Local Dream pipeline and mask code. No reuse. |
| Qualcomm QAIRT/QNN runtime, Hexagon skels | Separate Qualcomm SDK terms; not included in Nightmare repository | Redistribution rights for an exact SDK package have NOT been established. Excluded. Repository licence does not grant these rights. |
| MNN | Upstream identifies Apache-2.0; separate from the surrounding app | Candidate alternative, not selected or shipped. Exact converted-model graph/weight licences and conversion provenance still require separate review. |
| Nightmare QuickJS / ONNX Runtime SAM2 path | QuickJS MIT; separate ONNX runtime/model terms | Not required or selected. No plugin engine or segmentation runtime copied. |
| Nightmare DiT engine prebuilt binaries | NOTICE describes stable-diffusion.cpp/ggml inside externally built binaries | Not selected: app notice alone does not establish complete binary provenance, SDK redistribution terms or device compatibility. |

CC BY-NC is not GPL/AGPL and does not itself require publishing Private Gallery source. Its noncommercial restriction nevertheless does not grant the unrestricted commercial reuse requested here. Attribution alone does not remove that restriction. No owner decision to adopt these restrictions is needed because independently licensed implementation is available.

Local Dream currently builds arm64, minSdk 28, with separate CPU/GPU MNN and QNN/NPU paths. Its README describes Hexagon V68+ for SD1.5 and Snapdragon 8 Gen 3+ for SDXL. Nightmare builds arm64, minSdk 31; its documented NPU tiers differ by model (SD1.5: Snapdragon 888+, SDXL/Anima: 8 Gen 3+, larger DiT/video: 8 Elite+). These are UPSTREAM claims about their stacks, not Private Gallery compatibility claims. Neither a chipset name nor Vulkan availability proves support for a particular model. Nightmare's public source does not contain all required backend/QNN binaries.

## Independently licensed candidate stack

[leejet/stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp/tree/19bbbca1c736bbb9538679fc0ae690cb2b46b492) revision `19bbbca1c736bbb9538679fc0ae690cb2b46b492`: MIT.
Its exact ggml submodule is `4bf5f6000653b7881d00963cd6ddb665ccd62a8d` in `leejet/ggml`: MIT. This is the independent upstream, not either reference application's fork.

Selected production backend: CPU only, baseline arm64 instructions; x86_64 only for deterministic native/instrumentation verification. Do not silently select GPU, Vulkan, OpenCL, Hexagon, CUDA, RPC or downloadable native libraries. Native code must be compiled from pinned source in the reviewed APK dependency path. Model files are data, never scripts or executable modules.

The core C API supports initial images, masks, context load/free, sampling progress and cancellation. SD1.5/SDXL support in source is evidence for integration feasibility, not proof of speed, image quality or memory safety on a Fold. Those require the final exact APK/model/device combination and the physical acceptance checklist.

Transitive native licence inventory requiring packaged notices if shipped: ggml (MIT), nlohmann/json (MIT), stb (MIT alternative), ZIP/miniz (their embedded licences), darts-clone (BSD-3-Clause), Oniguruma (BSD-2-Clause), utf8proc (MIT plus Unicode data terms), incorporated PyTorch RNG code (upstream BSD-style terms), and SentencePiece-derived tokenizer code (Apache-2.0). Optional server cpp-httplib, WebP/WebM, GPU and Hexagon dependencies are to be disabled, not casually added to shipped notices. Audit the actual compiled graph before claiming the notice inventory complete.

**Nested licence finding:** `src/core/rng_philox.hpp` explicitly ports `AUTOMATIC1111/stable-diffusion-webui/modules/rng_philox.py` at `5ef669de080814067961f28357256e8fe27544f4`; that repository's LICENSE.txt at the same revision is AGPL-3.0, and the cited Python file contains no separate permissive grant. Do not infer clearance from the runtime's top-level MIT label. The build adaptation removes this header and replaces every reference with the independently implemented `STDDefaultRNG` from `core/rng.hpp`; the JNI options explicitly select the standard RNG. No CUDA seed-equivalence claim is made. CI must verify exclusion in the actual populated source/build graph. No copied Philox implementation is shipped.

The same audit found references to AGPL AUTOMATIC1111 commit `cad87bf4e3e0b0a759afa94e933527c3123d59bc` in the custom-word CLIP conditioner and prompt-attention parser. The build removes the entire custom-word conditioner, its weighting helper, the entire attention parser, and dependent PhotoMaker extension. Private Gallery supplies an independently written literal 77-token CLIP conditioner for SD1.5 and SDXL base only, and a literal weight-one parser compatibility function. No textual inversion, prompt-weight syntax, PhotoMaker, or long prompt chunking is exposed. Prompts exceeding 75 content tokens fail explicitly instead of truncating. The mathematical tensor contract was cross-checked against Apache-2.0 Hugging Face Diffusers v0.35.1 `pipeline_stable_diffusion_xl.py` and `models/embeddings.py`: penultimate hidden-state concatenation, pooled bigG embeddings, and original/crop/target size sinusoidal embeddings. This does not reuse the removed implementation. Build-time assertions reject any remaining explicit AUTOMATIC1111 reference in compiled source. Host C++ syntax checking of the model-builder translation unit passes; generation correctness still requires exact-model physical acceptance.

With these exclusions, no selected runtime licence requires Private Gallery-owned source to be relicensed GPL/AGPL. Native licence notices and required attributions must still be retained. Model architecture/implementation permission does not replace weight permission.

## Exact candidate model data

### Lightweight: Stable Diffusion 1.5 FP16

Source: [Comfy-Org archive](https://huggingface.co/Comfy-Org/stable-diffusion-v1-5-archive), an explicitly identified archive rather than a claim that Comfy created SD1.5.
File `v1-5-pruned-emaonly-fp16.safetensors`, revision `4fddeb7f9096623f1b77f4708feb96126a08a0cf`.
Published LFS size: **2,132,696,762 bytes**. SHA-256: `e9476a13728cd75d8279f6ec8bad753a66a1957ca375a1464dc63b37db6e3916`.
[Published pointer](https://huggingface.co/Comfy-Org/stable-diffusion-v1-5-archive/raw/4fddeb7f9096623f1b77f4708feb96126a08a0cf/v1-5-pruned-emaonly-fp16.safetensors).
Weight terms: CreativeML Open RAIL-M, referenced by both the archive and [SD1.5 model card](https://huggingface.co/stable-diffusion-v1-5/stable-diffusion-v1-5).
[Original licence](https://huggingface.co/spaces/CompVis/stable-diffusion-license/raw/main/license.txt).
This is a base image-to-image model, NOT a nine-channel dedicated inpainting checkpoint. Masked denoising/compositing must not be advertised as a separately verified dedicated inpainting model.

### Advanced candidate: Stable Diffusion XL base 1.0

Source: [Stability AI model repository](https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0).
File `sd_xl_base_1.0.safetensors`, revision `f298da3c058bd8f1f1c62f3ecfa775244a243897`.
Published LFS size: **6,938,078,334 bytes**. SHA-256: `31e35c80fc4829d14f90153f4c74cd59c90b779f6afe05a74cd6120b893f7e5b`.
[Published pointer](https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0/raw/f298da3c058bd8f1f1c62f3ecfa775244a243897/sd_xl_base_1.0.safetensors).
Weight and complementary-material terms: [CreativeML Open RAIL++-M](https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0/blob/f298da3c058bd8f1f1c62f3ecfa775244a243897/LICENSE.md).
No refiner, LoRA, ControlNet, FLUX, Z-Image or third-party fine-tune is selected. Their presence in an upstream README does not imply integration or permission for their weights. The current CPU integration uses 512-pixel maximum side for SD1.5 and 768 for SDXL, rounded to 64-pixel dimensions; it is masked base-model img2img, not instruction-tuned or dedicated nine-channel inpainting.

Both model licences grant use/distribution rights with obligations, including retaining notices, providing the licence, carrying forward use restrictions and requiring users to comply. They are not unconditional MIT/Apache weight licences. The app's all-rights-reserved notice cannot override these model terms. The download screen must supply the complete applicable licence and require acceptance before download/use. Production weights must never enter the APK/AAB, Git, Git LFS or release assets. Downloads are optional and owner-initiated from the verified authoritative/licence-approved source; no Private Gallery mirror is planned. Show exact download and installed byte sizes before confirmation, including SDXL. Resume only when immutable source identity and range semantics can be verified; otherwise discard/restart safely. Completed models remain app-private. No model weight or training dataset is relicensed as Private Gallery property. No training-data licence grant or non-infringement guarantee is inferred. No modified weight files are currently proposed.

## Device and resource feasibility

Private Gallery itself remains minSdk 26; local inference may require a higher API without excluding older devices from the rest of the app. The isolated-process/shared-memory path requires API 29+ and a reviewed packaged ABI. CPU fallback avoids asserting GPU/NPU compatibility. Query actual ABI, Android version, total/available RAM, low-memory status, thermal state and app-private free storage at operation time. Vulkan/GPU/SoC may be diagnostic facts, not permission to enable an untested backend.

RAM thresholds must be conservative admission policies, not invented measured requirements. A worker process must isolate native crashes/OOM from Vault/UI, and unsupported states must remain visible. A one-generation limit, bounded input dimensions, immediate release on cancellation/background/lock and no permanent warm session are the initial lifecycle. Explicitly document slower CPU operation and no measured latency guarantee. Device acceptance must establish whether either candidate is usable; a successful compile cannot do that.

## Integration/security contract

Retain AiImageEditProvider and one editor. Extend processing locality/capabilities without routing local edits through Replicate. Existing configured users retain Replicate selection, token, consent and policy. Auto selects installed compatible local providers for supported tools; paid cloud fallback always requires per-request confirmation. No capability probing through paid generation.

Model manager: fixed manifest, immutable revision URLs, exact byte count and SHA-256, HTTPS-only validated redirects, bounded streaming, disk headroom, cancellation/retry, atomic install after verification, app-private storage outside FileProvider/MediaStore. Removal only touches that model. Reverify before use; partial/corrupt files cannot qualify as installed. Downloads use Android normal routing independently of Browser state. No native code download or custom source URL.

Vault input/output: existing bounded decrypt/render and result sanitizer; local worker receives only bounded pixels/mask/prompt and a read-only model descriptor, never Vault keys or repository access. Prefer isolated Android service (no inherited app permissions/network) and shared memory, with no plaintext image files. Cancel and destroy processing state on lock/background. All AI outputs use the existing encrypted Save Copy and restricted-derivative path; original stays immutable. Provenance must identify actual provider/model without retaining prompt content.

## Validation status and remaining work

The provider/model-manager and reviewed CPU integration are implemented on the feature branch and awaiting CI validation. No production model download, production model generation or paid request has been made. The inherited-FD stream has passed a local C++ cursor/concurrent-read test; Android isolated-UID access is covered by an instrumentation fixture awaiting CI. Neither candidate has passed physical-device acceptance. Local build tools are unavailable in this workspace (`cmake` missing; prior Gradle distribution transport blocked), so CI must provide native/Android build evidence. Record baseline and final APK bytes from the same build type/ABI set. Update this status with actual test, build and CI results; do not turn pending work into a support claim.

## APK baseline

Starting-main debug APK (`49bb9bc5`, CI run 36134558301): **37,209,501 bytes**. Latest pre-expansion signed acceptance APK (`78be3f35`, run 36117847403): **29,800,290 bytes**. These are extracted APK file sizes, not compressed artifact ZIP sizes. The signed baseline precedes the final video-loading fix; disclose that when comparing. Final sizes pending build.

## Implemented admission/lifecycle details

Local inference requires Android 10/API 29 for distinct isolated service instances and a shipped arm64-v8a or x86_64 runtime. SD1.5 policy: marketed 8 GiB total (90% reported-RAM allowance), 4 GiB currently available. SDXL policy: marketed 12 GiB total (90% allowance), 8 GiB available. These are conservative admission policies, NOT benchmarked minimums or a promise that a 12 GB Fold has sufficient free RAM. Severe thermal state or low-memory notification prevents/cancels generation. GPU/Vulkan/NPU capabilities are not used to select CPU admission.

Lifecycle is load -> generate -> release, with no warm model retained. Each generation has a distinct isolated UID/instance, no app/network permissions, a read-only model FD and bounded shared image memory. Native source was adapted to consume inherited descriptors with pread/fstat/dup/mmap; it never reopens a private pathname. Shared memory is wiped/released on every result/failure/cancellation. No plaintext image temporary file is created. The editor's existing background/lock cancellation reaches the worker.

Model partials are data only. Retry sends a byte range against the immutable URL, validates Content-Range, and safely restarts if the server sends a complete 200 response. A complete SHA-256 pass and verified activation marker precede installation; a mismatch deletes the partial. Cancellation retains only model-data partial bytes, never image data. An additional full hash check precedes every inference.
