# Seedream 4.5 Combine Photos

API contract checked 2026-10-22 against primary documentation:
- https://replicate.com/bytedance/seedream-4.5/api
- https://replicate.com/bytedance/seedream-4.5/versions/e563f451edd459e76456dc2b32d7d2c02176059629759923965f9e86494325f6/api
- https://developers.cloudflare.com/ai/models/bytedance/seedream-4.5/ (explicit multi-image example)

Replicate accepts an `image_input` array of image URIs. Combine sends two ordered inline JPEG data URIs to the existing official Seedream 4.5 prediction endpoint, with sequential generation disabled and one output. The user prompt is preserved byte-for-byte through the editor/provider path, without enhancement. Existing optional moderation setting remains unchanged.

Reference choices are limited to image entries provided by the current vault viewer, excluding current and restricted entries. The same vault-scoped cancellable editor loader reads the reference; no primary/hidden repository is passed across the AI adapter boundary. Both buffers are sanitized, prepared, and wiped; the result uses the existing provenance and encrypted-copy save callback, preserving originals. Combine separately requests consent for both displayed images even if general provider consent was previously remembered.

Verification added: ordered two-image payload and exact prompt; two-photo sanitation/wiping; missing reference and consent rejection; only Seedream 4.5 advertises Combine. No external generation was performed.

Environment limitation: both focused and full `./gradlew testDebugUnitTest` attempts stop before compilation because Gradle 9.5.0 is not cached and services.gradle.org is unreachable. Android compilation and device UI acceptance remain required. `git diff --check` and tracked-secret scan pass.
