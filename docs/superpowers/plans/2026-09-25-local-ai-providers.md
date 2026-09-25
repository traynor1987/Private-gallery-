# Local AI implementation plan

The owner's 35-section milestone is the authoritative design brief. Implement under the existing provider and editor, retaining proprietary ownership and the accepted cloud path. Do not publish a production release.

1. Audit current upstream code/licences and pin exact data/runtime revisions. Record findings in docs/audits/local-ai-provider-feasibility.md. Do not use reference-app code or unaudited Qualcomm binaries.
2. Add testable model manifest, device admission rules, atomic hash-verified downloader, removal, selection and Auto resolution. Keep credentials and current preference files untouched.
3. Compile an independent CPU runtime from immutable source. Run it in an isolated Android worker process with no network/Vault permissions. Send bounded pixels through shared memory and model data through a read-only descriptor. One worker at a time; release after every request, cancellation, lock or background event. No permanent model warm cache.
4. Integrate both providers and model management into existing Settings/editor. Display real capabilities/status and licence acceptance. Auto cloud fallback requires explicit per-edit consent. Preserve current Replicate setup and disclosure.
5. Preserve shared normalized mask geometry, encrypted Save Copy and derivative restrictions; record truthful local/cloud provenance. Add tests for model/device/download/selection/cleanup/security boundaries, and native initialization failure. Ordinary CI uses fixtures, never production weights or paid requests.
6. Review actual changes, packaged notices and APK size. Run all existing gates, push and repair failures through green CI. Report remaining unverified physical-device performance and exact acceptance checks without claiming measured support.

Conservative CPU admission thresholds are policy choices pending physical measurement, not claimed benchmarks. Advanced SDXL must remain unavailable if measured device resources do not meet its policy. No FLUX/NPU/GPU support is promised.
