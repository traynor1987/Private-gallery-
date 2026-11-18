# Build 86 Nova Brain audit / Build 87 repair

Audit baseline: owner-confirmed installed NovaOS 1.7.40, code 86, Android 17/API 37. Main `a531e08e010c818bf278454f9556dc9ddec0b51d`; original APK/tag source `15c1a2754d90bba0be58881101a72dc9aa480391`; APK SHA256 `9c7d5918e0321556a42507ed914216724eb0354c01c8359f9022dd253b94fe7d`, 151566003 bytes. The owner confirms the in-app updater installed 86 successfully.

## What 86 actually shipped

- A real read-only HTTPS client in `core/brain/NovaBrainConnection.kt`, using existing GET `/health` and authenticated GET `/guardian/status`. It uses normal Android certificate/hostname validation, no redirects, bounded timeouts/read-only retries and response limits.
- No preconfigured VPS origin or owner token. The connection is not authenticated or tested merely by installing the APK.
- Existing AES-GCM Android Keystore storage in `NovaBrainConnectionStore`/`AiCredentialStore`, no-backup ciphertext, separate owner token (not an OpenAI key).
- Configuration and test card located under **Settings → AI editing**, and in the optional Cloud Teacher review dialog. It was absent from the main Settings category list under the expected Nova Brain name.
- Connection status existed only in transient Compose state; it was reset when stopped/disposed. No durable last-authenticated timestamp or dedicated environment/privacy/offline overview.
- Guardian's existing remote request is exposed only after revealing a message and opting the conversation into Cloud Teacher. Evidence Locker exposes an optional reviewed OCR excerpt. Both load the shared saved configuration, require edited preview plus explicit request consent, and POST only through the existing `/guardian/analyze` route. They do not automatically synchronize learning, upload conversations/evidence or send private feedback.
- Local Knowledge evaluation (`Not yet validated`) concerns held-out local learning evaluation. It is unrelated to TLS, backend authentication or VPS availability.

## Actual backend evidence and limits

The current source (`scripts/cloud_teacher/server.py`), staging Compose/deployment controls and existing deployment documentation were inspected. Authenticated status reports `status`, `model`, `analysis_enabled` and budget metadata. Health alone has no authenticated mode/analysis information.

Live HTTPS probes during this audit returned `/health` HTTP 200 with `{status:ok}` and unauthenticated `/guardian/status` HTTP 401, using normal certificate validation (TLS verification 0). No real owner credential or sensitive payload was sent. Independent raw-socket DNS was unavailable in this execution environment; the successful HTTPS probes used the normal configured network route and certificate verification.

No direct authenticated VPS administration session or real owner token is available here. The exact live container command, deployed source revision, staging/production configuration and paid-analysis flag therefore remain unverified. Existing `--staging` forces model `staging`, analysis disabled, no OpenAI credential/budget-volume mounts and loopback binding. These source/deployment restrictions are preserved; no deployment or OS/service change is performed.

Successful authenticated mock/local TLS tests are contract evidence, not proof of a successful authenticated request to the owner's VPS. Build 87 includes a separate Android check of the actual live HTTPS endpoint using an intentionally invalid synthetic token; it must observe authentication rejection and never reports that as authenticated success.

## Confirmed repair scope

Target: NovaOS 1.7.41, code 87, same package/signing identity/API 37 and existing release channel.

- Add ordinary **Settings → Nova Brain** routing; reuse the existing connection component/client and shared encrypted credential storage.
- Add visible connection result, last successful authenticated timestamp, backend configuration environment from the authenticated status model, server analysis availability, explicit automatic-analysis-off and privacy/offline indicators.
- Store only actual successful authenticated checks in the existing encrypted configuration record. Legacy 86 origin/token records remain readable. Saving/replacing/removing credentials clears history, and late responses for different/removed credentials cannot restore it.
- Historical results are labeled as history, not a new/current authenticated connection. Opening the screen does not automatically send owner content, enable Cloud Teacher or activate paid analysis. Model `staging` is classified as staging under the inspected backend contract; this is not an OS/container deployment attestation.
- Preserve Guardian/Evidence consent and local-first operation, Knowledge evaluation, Sanctuary, Vault encryption and updater code.
- Verify real Settings navigation and both viewports, status/auth/outage/history handling, encrypted legacy record compatibility, protected-core regressions and authentic populated86→87 update/restart.

## Owner-only live verification

After the verified in-app 87 release, open Settings → Nova Brain → Connect. Enter the existing HTTPS origin `https://brain.jamestraynor.co.uk` and existing backend owner token locally, Save securely, then Test connection. Inspect the authenticated result, environment, last successful check, offline and server-analysis indicators. Do not turn on paid analysis or send private content for this check.

If the token is unavailable or rejected, use existing trusted VPS access to check the existing owner-token file documented at `/etc/nova-cloud-teacher/secrets/owner-token`; no token rotation, initial setup or deployment is required for a read-only audit. The existing authenticated `/guardian/status` can be queried with the token supplied through curl's stdin header facility, rather than printed or placed on a command line. Its non-secret `status`, `model` and `analysis_enabled` fields establish live authenticated mode/analysis reporting. To independently verify actual staging deployment, inspect the existing teacher container's command for `--staging` using the existing VPS session; do not change NGINX, services, volumes or the OS.

Build 87 is not considered published or verified by this audit document alone. Final CI, source, signed-APK, upgrade and public-feed receipts will be recorded separately after completion.
