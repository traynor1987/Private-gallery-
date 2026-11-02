# NovaOS 1.7.10.2 conditional source security review

Source: 2b67f751a91961c138b4290c165415931e7c8b89
Reviewer: /root/release_security_review
Decision: CONDITIONAL SOURCE REVIEW ONLY — SIGNING BLOCKED

Reviewed the professional-app changes from signed code53 recovery source73e89605, including final CameraX focus ownership, bounded exposure/temperature, rotation-aware crop presets, Editor teardown, Evidence dashboard/consent/capture, fixed VPN/Tor launcher admission and owner-specific Home migration. No unresolved additional Critical/Important implementation finding was identified. Clear Session failure handling and access to existing Tor Private Apps were corrected during review. MainActivity retains its authenticated screenshot policy; generic sensitive Evidence hosts remain secure for their window lifetime.

The final source preserves browser production files from4f4dd4f1, whose three live VPN/Tor/network-loss cases were independently reported green by the release controller with original XML and packet verification. This is retained boundary evidence, not a claim that the final full266-case matrix passed on one source.

## Distinct runtime checkpoints and final source review

- Prior source07c14135e4a2eb92ac6725e096406f8ba9bcc15d, run41363619033: compilation and focused JVM checks passed;83 Android17 cases completed,79 passed and4 failed. Artifact13106692387 archive SHA-256:9d599e3be0d4f03c564b88bb23771bcf3114ed262f156be2f5fd2b54feebb606. This failed checkpoint is not release approval.
- Reviewed final diff07c14135 through2b67f751a91961c138b4290c165415931e7c8b89: six Editor buffer/bitmap disposal effects now capture immutable composition owners; regenerated-result checks remain. Exposure gesture begins away from Android Back and asserts positive exposure/Undo before actual saved-pixel checks. Concealed camera gestures own down, retain initial coordinates, reject consumed/multiple-pointer/drag input, observe bound state and use the latest callback; original CameraX capture checks remain with a new standalone gesture regression. Evidence search materializes the lazy row and also verifies the encrypted catalogue result. No additional Critical/Important source issue identified; approval is for targeted runtime progression only.
- Final source2b67f751a91961c138b4290c165415931e7c8b89, run41365205828/job125023798148: host checks and app/test assembly passed. Independent reviewer downloaded-artifact inspection confirms25 distinct Android17 closure cases and7 focused JVM cases, all with0 failures/errors/skips. Artifact13107287404 archive SHA-256:1794260bd5bbf24eb1fc5b8c5d9a06c616c8ce427086a7eb6d4f6e0ca8227056. Original source receipt matches2b67f751, UI exit status is0, and exact closure-matrix XML accounting passes. Closure covers the four entire previously failing classes plus the new gesture regression. Retained broader matrices are84 engine cases and266 full cases; no complete single-source pass is claimed.

Final scope decision: targeted closure verification is accepted, with no unresolved additional Critical/Important source finding. The final source may be preserved as a verified recovery checkpoint for this tested scope. Earlier passing checks and unchanged-browser route evidence remain distinct historical evidence; they are not relabelled as a final266-case exact-source run. Full specification/release approval remains WITHHELD because the owner-required vault-key encryption of WebView-provider cookie/profile storage has not been implemented or explicitly resolved. No code54 signing activation, original-key53-to54 upgrade or acceptance APK is approved or claimed.

No production signing, complete-runtime or original-key upgrade approval is asserted here. The unresolved cookie/profile encryption requirement below continues to block signing.

## Unresolved release blocker: WebView-provider storage is not vault-key encrypted

HiddenVpnBrowserActivity supplies ordinary app-private lease data/cache directories to AndroidX ProcessGlobalConfig and creates a unique WebView profile. HiddenVpnStorageCleanup owns and removes those temporary roots after retirement/crash recovery. The WebView provider can write cookies, DOM/local storage and other profile data in those directories while the session is active, and retired files may remain until cleanup completes. Those bytes are not encrypted by NovaOS with the originating vault/session key. Isolation, Android device encryption, no-backup placement, cache suppression and eventual deletion do not satisfy the owner's separate vault-key encryption requirement.

Existing app-owned Hidden bookmarks/history and Private Apps catalogue use encrypted owner-bound stores. That does not extend to provider-managed WebView cookies/profile storage. An encrypted cookie snapshot alone would leave provider-managed copies unprotected and must not be described as resolving this requirement. This finding concerns the WebView provider, not AI-provider credential stores.

Do not activate a code54 signing marker or claim the full specification complete until the owner explicitly resolves the requirement or a reviewed implementation proves the requested at-rest protection across active sessions, crash/restart, lock, transport switching and cleanup. If the owner accepts ephemeral app-private WebView storage instead, record that exact scope decision and use accurate UI/release wording; do not infer a waiver from a request to continue.

Original-key code53-to54 upgrade/restart, final APK identity/certificate/hash verification and physical Fold acceptance also remain outstanding.
