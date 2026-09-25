# Local AI acceleration: source and licence findings

Research snapshot: 2026-09-25. This is evidence for implementation, not a claim of device acceptance. No model weights downloaded during this research. Repository code was read, not copied into production.

## Positive Qualcomm SDK clearance

The actual **QAIRT Community 2.50.0.260828** SDK `qairt/2.50.0.260828/LICENSE.pdf` was obtained from Qualcomm's archive using HTTP byte ranges (ZIP directory plus the licence entry only). It is **AI Stack License**, licensor Qualcomm Technologies, Inc., **147,577 bytes**, SHA-256 **ec1dccfdcba5c6e64126e84199b8362bf4999107bfa567ebe831dbb4c461692b**.

Official archive: <https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/2.50.0.260828/v2.50.0.260828.zip>. Archive Content-Range reports 2,601,473,189 total bytes; last-modified 2026-09-01. Scratch evidence: `/tmp/qairt-2.50-LICENSE.pdf`, extracted text `/tmp/qairt-2.50-LICENSE.txt`.

Section 1 grants royalty-and-fee-free rights to use/copy for application development, modify supplied source for application development, demonstrate object code, benchmark, and distribute/sublicense object code incorporated in an application. It excludes standalone distribution/sublicensing of the Software. There is **no requirement to publish Private Gallery source and no noncommercial-only restriction in this SDK licence**. Thus proprietary QNN is not a blanket blocker for the authorised owner APK.

Sections 2(a,b,c) prohibit reverse engineering supplied object/compiled code, require preserving proprietary notices, and require applicable third-party licence/readme compliance. Section 2(d) prohibits specified harmful AI applications; 2(e) advises against its listed high-risk uses. Section 3 reserves QTI ownership and does not grant patents. Section 10(h) requires retaining third-party notice files. Export, termination and other contractual conditions remain applicable. If QNN is shipped later, package original LICENSE.pdf, NOTICE.txt and QNN_NOTICE.txt for the selected SDK plus relevant third-party notices; do not relabel Qualcomm objects as Private Gallery-owned code. SDK source should not be republished merely because object-code app distribution is granted.

The same licence grant is independently reproduced by Dell's official publishing of Qualcomm terms: <https://www.dell.com/support/manuals/en-us/dell-pro-ai-studio/dpais_license/qualcommai-stack-license-terms-and-conditions-of-use?guid=guid-b08f0ec7-55e9-47bd-b8a5-a9a2c55afd23&lang=en-us>. The extracted SDK document, not the reproduction, is the controlling examined artifact.

## Current upstream implementations

| Source | Exact current commit | Findings |
|---|---|---|
| xororz/local-dream | `440899fb6dbd14712b4a1c8fe4d6b0e1c9f68a36` | SD1.5/SDXL QNN paths are separate from MNN CPU/OpenCL and DiT ggml Hexagon. CC BY-NC 4.0. |
| AbrahamPaulJ/nightmare-mobile | `2c038b2af798d9183e11f22be23831cd91d3bf5c` | Forks Local Dream native pipeline; QNN/native binaries are absent from repository. CC BY-NC 4.0. |
| AbrahamPaulJ/npuforge | `0c6f4ea7f4b3bfab3b26dbd76a469c0399af9140` | MIT original converter source, separately NC-derived templates, external SDK/generated graph assets. On-phone conversion is implemented but not a fresh-clone-complete app. |

Local Dream `app/src/main/cpp/CMakeLists.txt` pins QAIRT 2.50.0.260828 and copies QnnHtp/QnnSystem plus architecture-specific HTP backend/stub/skel objects for v68,69,73,75,79,81. It builds Qualcomm SampleApp source, MNN (OpenCL enabled), tokenizers-cpp, xtensor and zstd. `PipelineSd15Npu.hpp` uses persistent MNN CPU CLIP, QNN UNet, QNN VAE decoder and optional QNN VAE encoder. It exposes img2img only when encoder exists. `QnnRuntime.hpp` loads QnnHtp/QnnSystem and creates contexts from binary or memory buffers. These are not safetensors loaders. SD1.5 resolutions above its base use separately generated zstd UNet patches and tiled VAE. The native executable/server architecture would need adaptation to Private Gallery's bounded pixel/FD worker protocol, cancellation and masking contract; importing its Android UI/server wholesale is unnecessary.

Local Dream README claims SD1.5 Hexagon v68+ and SDXL Snapdragon 8 Gen 3+. Nightmare README claims 888+ for SD1.5, 8 Gen 3+ for SDXL/Anima, and 8 Elite+ for DiT/video. These are their implementation claims, not universal NPU or Private Gallery compatibility. Local Dream SDXL source allowlist contains SM8650/SM8750/SM8750P/SM8850/SM8850P/SM8845. Source catalog selects chipset-specific QNN2.28 ZIPs even though runtime is 2.50.

CC BY-NC 4.0 grants noncommercial reproduction/adaptation/sharing and does not require source publication. Owner-only noncommercial use is not excluded because the surrounding app is proprietary. Preserve attribution, licence, warranty notice, source link and modification indications when sharing; do not impose proprietary restrictions on the CC material itself. Section 1(i) defines noncommercial by primary commercial advantage/compensation, not simply by whether money changes hands. Source: <https://creativecommons.org/licenses/by-nc/4.0/legalcode.en>.

## Existing checkpoints and NPU options

The existing 2,132,696,762-byte SD1.5 and 6,938,078,334-byte SDXL safetensors cannot be passed directly to the examined QNN pipelines. They can remain CPU/Vulkan inputs. QNN requires converted/quantized graphs with matching component interfaces and target/runtime support, either separately downloaded or generated from existing weights. It is inaccurate to say every NPU path always requires a separately downloaded compiled model: npuforge does compile on-device.

npuforge README, NOTICE, docs/BUILD.md and docs/LIMITS.md establish:

- Supports complete LDM-layout SD1.5 and SDXL-base single-file safetensors, F16/F32/BF16. Existing files are architecture candidates, not verified conversions.
- UNet W8A16, MNN CLIP(s), QNN VAE encoder and decoder with FP16 VAE arithmetic; fixed activation calibration and graph shapes.
- SD1.5 512-square fixed compiler target v73/8MB VTCM; SDXL 1024-square v75/soc57/8MB VTCM; QAIRT2.50. Android13+ ARM64; principal demonstrated phone S25 Ultra 12GB.
- Working build additionally requires external qnn-context-binary-generator, HtpPrepare, SDK runtime, generated model libraries and CLIP/VAE recipes. The SDXL templates and both complete component-template sets are not checked in. Host template authoring needs source model assets and is separate work.
- Source explicitly states full-component SD1.5 rendering and img2img still need documented evaluation. Fixed calibration can fail particular checkpoints; historical MistoonAnime conversion loaded but rendered noise.

Thus npuforge is a credible conversion implementation reference under the revised licence policy, not evidence that the current APK can activate QNN merely by enabling a CMake flag.

## Compiled SD1.5 model sources

`xororz/sd-qnn` immutable revision `64d16039e0c3a71d3c5db1faccc090faae93cb28` was inspected through HF metadata; it has **no model card or declared repository licence**. Candidate identities (metadata only):

| Archive | Bytes | SHA-256 |
|---|---:|---|
| AbsoluteReality_qnn2.28_min.zip | 993451663 | c4396637a90dbd4ded0e8c0d2747e2f60925ced9c64fb286328be6478e9d6913 |
| AbsoluteReality_qnn2.28_8gen2.zip | 1054661172 | f479f3221fb90d31de1e695577f5aec9a5afb5fb7e076cfac40f00e039b22977 |
| DreamShaperV8_qnn2.28_8gen2.zip | 1032290626 | af042ddb88452e366fb4f83977b73612e1e66d4e5846113bc7125361bfaf0658 |
| DreamShaperV8_qnn2.28_min.zip | 1154529244 | 0a2db50baaba8925fc68a7a087e61067b2d2eb1db92d69fa65030f50ff5d2990 |

AbsoluteReality_min ZIP directory inspected with metadata-only byte ranges contains tokenizer.json, clip_v2.mnn, pos_emb.bin, token_emb.bin, vae_encoder.bin, vae_decoder.bin and unet.bin. No licence file is listed. Its original `Lykon/AbsoluteReality` revision `df7c3efce8f54d02cf73a21fcecd908a0fde2898` declares `other` and links Civitai; exact permissions were not established here. Do not substitute a generic RAIL-M assumption.

DreamShaper's original author `Lykon/dreamshaper-8`, revision `a7e52b98680b1ba8ff7bce97c7f9f2e2e5337917`, **does explicitly declare CreativeML OpenRAIL-M**, and identifies SD1.5 fine-tuning. That is stronger original-weight permission than AbsoluteReality, but the compiled publisher supplies no card describing which donor CLIP/VAE/tokenizer components and notices it used. Treat it as a concrete audit candidate, not automatically forbidden and not automatically fully cleared.

Official `qualcomm/Stable-Diffusion-v1.5`, revision `18c1caff27123921277323cef541fb0c6ef8fd47`, declares CreativeML OpenRAIL-M and lists QAIRT2.50 QNN_CONTEXT_BINARY device-specific bundles. Example versioned official URL: <https://qaihub-public-assets.s3.us-west-2.amazonaws.com/qai-hub-models/models/stable_diffusion_v1_5/releases/v0.63.0/stable_diffusion_v1_5-qnn_context_binary-w8a16-qualcomm_snapdragon_8gen3.zip>.

Current Qualcomm `src/qai_hub_models/models/stable_diffusion_v1_5/model.py` defines ONLY text_encoder, unet, and **VaeDecoderQuantizable**. It does not publish a VAE encoder in this model collection. Therefore this bundle alone does not satisfy Private Gallery img2img. A hybrid using the existing base checkpoint's CPU/Vulkan VAE encode plus official QNN denoising/decoding is technically plausible, but requires a new tensor adapter, matched scheduler/scaling, component loading lifecycle and verification. It is not a drop-in Local Dream archive. Official templates provide named tensor specs and can be used for independent implementation; measure device support and output correctness. No exact bundle SHA/size was established in this research.

## Recommendation

QNN runtime and noncommercial upstream-source licensing no longer justify automatic exclusion. The strongest currently verified model/provenance route is Vulkan acceleration of the existing audited safetensors with CPU fallback. NPU must remain explicitly unavailable in the delivered build until its separate context pipeline, complete editing component manifest and tensor adapters have been implemented and verified. This is an integration and model-provenance gap, not a claim that NPU is impossible or prohibited by the SDK licence. Do not add packaged QNN notices to imply it is shipped. Future routes include the Local Dream architecture with established compiled-component provenance, compiling the approved base weights through a documented exporter/SDK, or hybrid official QNN denoising with existing-checkpoint VAE encoding. Preserve the provider/security contract and measure physical-device correctness/performance; do not label Vulkan as NPU or promise speed without evidence.

Primary source URLs: https://github.com/xororz/local-dream ; https://github.com/AbrahamPaulJ/nightmare-mobile ; https://github.com/AbrahamPaulJ/npuforge ; https://github.com/qualcomm/ai-hub-models ; https://huggingface.co/xororz/sd-qnn ; https://huggingface.co/Lykon/dreamshaper-8 ; https://huggingface.co/qualcomm/Stable-Diffusion-v1.5 . Resolve repository file URLs against the exact revisions above for reproducibility.
