# Private Apps 1.7.8 design and release requirements

Owner specification in the execution request is authoritative. Implement the full feature without beginning1.7.9/1.8, preserving Android17-only support and original signing identity.

## Chosen approach
Extend existing authorised Primary and Hidden browser destinations with an encrypted private-app catalogue and app presentation. Retain existing transports, vault access and lifecycle owners. A separate uncontrolled WebView activity would duplicate security policy; persistent Hidden Chromium folders would violate cleanup. Neither is acceptable.

Catalogue entries are vault-specific, with immutable random IDs, validated HTTP(S) start URL, bounded name/icon, manifest scope/display metadata, favourite/order and optional address-bar setting. Catalogue storage must reject corrupt data, stale authority and cross-domain ciphertext substitution. No preinstalled services or automatic login. PWA metadata and website icons must use the originating transport and reject stale page callbacks, cross-origin scope/start URLs, unsupported schemes and unbounded metadata/icons.

Shared-profile limitations must be explicit. Primary's established retention setting remains authoritative. Per-app session-clearing controls cannot be advertised when they would clear shared browser sessions. Hidden keeps its established ephemeral login policy, including process-death cleanup; encrypted app definitions remain. No JavaScript discovery in Tor, no automatic transport switching. Service-worker/offline/push support must be capability-based, never inferred from manifest presence.

Reuse the existing choose-from-vault confirmation and media download paths with immutable initiating-view/app ownership. App switching must cancel stale upload callbacks. Documents require encrypted storage, list/open/upload/delete/restore behaviour and safe placeholders, while forbidding active-content execution. Screenshots require explicit confirmation and existing capture policy. Do not silently treat these as already implemented.

## Required implementation milestones
1. Encrypted bounded catalogue, ownership/authority tests, validated manifest metadata and icons.
2. Shared professional library/grid/editor and dedicated app chrome in both browser destinations, app switching, ordering, favourites, settings and authentication.
3. PWA discovery through authorised transport; synthetic malformed/oversized/redirect/scope tests and honest fallback.
4. Session policy controls and profile-aware ownership throughout uploads/downloads. Preserve ephemeral Hidden policy or implement reviewed encrypted snapshot lifecycle before exposing persistence.
5. Document/screenshot/download/upload lifecycle integration, cancellation/recovery, stale callback rejection and encrypted destination verification.
6. One coherent focused Android verification batch, independent whole-change security review, exact-source approval, original-key46→47 upgrade including Recently Deleted, final signing and artifact verification.

## Release gates
No signing until Android compile/JVM checks, synthetic PWA/UI tests, authentication/storage isolation, VPN/Tor packet/DNS/revocation, upload/download lifecycle and independent security review all pass for exact frozen source. Existing 1.7.7 approval cannot approve 1.7.8. Preserve historic workflow gates and evidence; adapt focused and signing controls only for new verified source. Never uninstall/clear application data or generate signing material.
