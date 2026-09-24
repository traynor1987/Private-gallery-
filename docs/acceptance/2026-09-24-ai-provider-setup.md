# Owner AI provider setup

Starting main: `3db0842f167b51073b4160adbc52014484254659`.
This completes the existing editor configuration gap; no production release.

## Owner flow
Settings → AI editing → Set up provider → enter your Replicate API token → Test connection.
The successful check saves the token using Android Keystore AES-GCM in noBackupFilesDir and shows Connected. Open a Vault photo → Edit → AI Edit, enter a prompt, consent to remote processing, Generate, preview, Save copy.
Manage provider can test the stored token without showing it, verify and replace it, or remove configuration and remembered consent. Restart shows Configured until a fresh connection test; this distinguishes saved configuration from live connectivity. Local editing remains available without a provider. No account/token/credit is supplied by Private Gallery.

## Verified official API contract (24 September 2026)
- Model: https://replicate.com/bytedance/seedream-4.5
- Published schema: https://replicate.com/bytedance/seedream-4.5/versions/9fe3b8282dcb9d9063b05e33210a1432801f7c5a6641db944baefcec4886761a/api
- HTTP endpoints/authentication/polling/cancel/output hosts: https://replicate.com/docs/reference/http
- Inline file representation: https://replicate.com/docs/topics/predictions/input-files
- Prediction deadline: https://replicate.com/docs/topics/predictions/create-a-prediction

The adapter uses the official model endpoint, not an invented version hash: POST https://api.replicate.com/v1/models/bytedance/seedream-4.5/predictions. Its input has prompt, exactly one image_input data URI, size 2K, aspect_ratio match_input_image, sequential_image_generation disabled and max_images 1. No undocumented parameters or safety overrides. Seedream's documented schema has no mask input: only GENERATIVE_EDIT is advertised. Removal/replacement/restyling can be described in the prompt; dedicated brush, alpha-background and outpaint controls are not advertised by this adapter.

Test connection performs authenticated GET /v1/account and GET /v1/models/bytedance/seedream-4.5. It verifies the account response and selected model identity, without generating predictions, reading previous predictions, uploading media or spending generation credits. It does not prove account credit availability or successful generation. HTTP 402 during editing explains account credit; invalid tokens, permissions, rate limits, unavailable model, malformed response and network errors have concise messages. Raw provider error/log bodies are never displayed or logged.

## Security and lifecycle
- The owner explicitly requested on-device credentials, superseding the earlier backend-proxy suggestion. No app-wide/shared secret and no server proxy added.
- Token is password-masked, never prefilled/revealed, not saved into Compose saved-instance state. Setup dialog always uses SecureOn, even with acceptance screenshot override enabled. Keyboard correction is disabled. Back/background/recreation clears unsaved entry and cancels verification. JVM/HTTP immutable strings exist transiently and cannot be guaranteed physically zeroed; owned byte buffers are wiped.
- Verification precedes secure persistence. Failed replacement keeps the previously verified credential. Generation-counter checks prevent removal during a pending verification from resurrecting a key. Removal invalidates old provider instances and cancels their active edits.
- Registry initialized at app startup restores the adapter from verified encrypted credential presence; it never holds a decrypted credential field. Each operation reads an owned copy and wipes it.
- Only selected, sanitized image bytes and user's prompt enter the adapter. No Vault repository, item ID, key, collection, PIN/recovery or Browser data crosses the request boundary.
- Remote representation is a newly encoded JPEG, bounded to the existing 1.2 MP preview budget and 900,000 bytes (below the documented 1 MB data-URI recommendation), with alpha flattened on white. Local original/save pipeline is unchanged. JSON base64 is streamed without an extra full base64 copy. No plaintext temp file, upload staging service, public storage or FileProvider path.
- HTTPS with normal system trust, no redirect following, no disk caching, no cookie/session reuse. API URLs are fixed. Polling/cancel paths use validated prediction IDs, never arbitrary provider-supplied URLs. Output downloads accept only replicate.delivery and its subdomains, no credentials/userinfo/custom ports/HTTP. Authorization is sent only to those documented trusted endpoints.
- Response streaming is bounded (2 MiB JSON, 16 MiB result); only PNG/JPEG/WebP image responses proceed to the existing decode validation and metadata sanitization. Result preview and verified encrypted Save copy preserve originals.
- Cancellation disconnects transport and attempts a bounded remote cancel when an ID is known. Cancel-After 90s bounds remote jobs when a create response is lost or app/network disappears. Cancellation cannot guarantee avoiding provider charges or retracting already transmitted data.
- Android default routing includes active VPNs; no Browser-owned VPN gate, alternate network binding or VPN changes.

## Automated checks
Regression cases cover missing setup entry, verification/save/removal/restart, failed replacement, cancellation/backgrounding, secret-field clearing, capability gating, exact selected-image request contract, non-generating account/model test, polling, response rejection, host/redirect restrictions, byte bounds, remote cancel, cancellation result handoff and encrypted persistence. Existing editor immutability/sanitization/import and Browser regression suites remain gates. All provider responses use deterministic fakes; CI never requires credentials or spends credits.

CI #304 reproduced the original gap: the new setup-entry assertion failed because Set up provider was absent; all other 66 instrumentation tests passed. Local Gradle execution is blocked by network restrictions on the Gradle distribution download, so Android CI supplies compilation, unit, lint, APK and emulator evidence.

## Physical acceptance (owner credential required)
| Case | Steps | Expected |
|---|---|---|
| Fold and keyboard | Outer portrait, inner portrait/landscape, outer landscape where supported; light/dark; gesture/3-button navigation; open setup and keyboard | All controls reachable by scroll; token masked; no clipping |
| Screenshot privacy | Enable acceptance screenshots, open token dialog; screenshot/recents | Credential dialog protected; saved token never shown |
| Valid setup | Paste your Replicate token; Test connection | Connected; no generation/image sent; key saved encrypted |
| Restart | Relaunch app; open Settings and AI Edit | Configured status; stored token empty in field; AI prompt available |
| Invalid/offline | Invalid token, revoked token, airplane mode, retry | Useful errors; no new invalid credential persisted; stored key not echoed |
| Replacement | Try invalid replacement then valid replacement | Old key remains on failure; successful replacement becomes active |
| Remove | Confirm Remove configuration, restart | Token and remembered consent removed; remote tools disabled; local editor/Vault unchanged |
| Consent/edit | Open selected photo; decline then accept disclosure; request text removal | No upload before consent; only selected image; preview before encrypted Save copy |
| Fail/cancel | Cancel Generate; Home/lock/unfold during request; timeout/provider rejection | Original untouched; no plaintext file; no late save; remote cancel/deadline best effort |
| Networking | Edit with active VPN; lose VPN; verify Browser-required state separately | Android default AI routing; Browser fail-closed semantics unchanged |
| Large/alpha | Large JPEG with GPS, transparent PNG | Bounded remote representation and stripped metadata; white transparency documented; original unchanged |

The remaining external step is the owner's actual Replicate credential (and account credit for generation). Live API/device acceptance is not claimed by fake-provider CI. Feature freeze remains: only defects, compatibility/security issues and release blockers.
