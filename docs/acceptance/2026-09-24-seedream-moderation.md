# Seedream moderation preference

Audit base: f21e70f43572988935c0be803c5e991f66c4c2fe.

The production AiImageEditProvider is ReplicateSeedreamProvider, using ReplicateSeedreamApi's streaming JSON request to the official bytedance/seedream-4.5 predictions endpoint. Previously its input contained prompt, image_input, size, aspect_ratio, sequential_image_generation and max_images only.

Replicate's documented schema exposes boolean disable_safety_checker, default false:
https://replicate.com/bytedance/seedream-4.5/versions/a7b8d76538b9e242d81edea9310f55b7e2a5b75b80c67ea07c3dafa035332925/api
https://replicate.com/bytedance/seedream-4.5/api/schema

Settings → AI editing → Relax Seedream moderation defaults OFF. OFF omits the input field; ON emits JSON boolean true, never a string. Replicate/model policies and illegal-content restrictions still apply. Prompts are passed unchanged. No alternate endpoints, prompt transformations, retry-to-evade behaviour or access-control changes are introduced.

The owner preference persists locally and is read for every new edit, then captured before image preparation. An edit already in progress keeps its original choice. Clearing remembered disclosure consent does not reset owner preferences. Credentials, connection testing, remote-processing consent and Vault-only output policy are unchanged.

Tests inspect request bodies using fake transports only; no paid prediction calls. They cover default omission, explicit OFF/ON/OFF serialization, unchanged prompts, per-edit preference reads, in-flight choice stability, default-OFF storage and preference persistence.

Physical acceptance: open AI editing settings, confirm OFF on a fresh preference, toggle ON, leave/reopen settings, then turn OFF. No live generation is required to verify the control.
