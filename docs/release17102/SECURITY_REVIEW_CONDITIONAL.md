# NovaOS 1.7.10.2 conditional source security review

Source: 07c14135e4a2eb92ac6725e096406f8ba9bcc15d
Reviewer: /root/release_security_review
Decision: CONDITIONAL SOURCE REVIEW ONLY — SIGNING BLOCKED

Reviewed the professional-app changes from signed code53 recovery source73e89605, including final CameraX focus ownership, bounded exposure/temperature, rotation-aware crop presets, Editor teardown, Evidence dashboard/consent/capture, fixed VPN/Tor launcher admission and owner-specific Home migration. No unresolved additional Critical/Important implementation finding was identified. Clear Session failure handling and access to existing Tor Private Apps were corrected during review. MainActivity retains its authenticated screenshot policy; generic sensitive Evidence hosts remain secure for their window lifetime.

The final source preserves browser production files from4f4dd4f1, whose three live VPN/Tor/network-loss cases were independently reported green by the release controller with original XML and packet verification. This is retained boundary evidence, not a claim that the final full265-case matrix passed on one source. Run41363619033 is the targeted83-case engine/UI run; compilation/runtime outcomes remain to be checked. No runtime success, production signing or upgrade approval is asserted here.

## Unresolved release blocker: WebView-provider storage is not vault-key encrypted

HiddenVpnBrowserActivity supplies ordinary app-private lease data/cache directories to AndroidX ProcessGlobalConfig and creates a unique WebView profile. HiddenVpnStorageCleanup owns and removes those temporary roots after retirement/crash recovery. The WebView provider can write cookies, DOM/local storage and other profile data in those directories while the session is active, and retired files may remain until cleanup completes. Those bytes are not encrypted by NovaOS with the originating vault/session key. Isolation, Android device encryption, no-backup placement, cache suppression and eventual deletion do not satisfy the owner's separate vault-key encryption requirement.

Existing app-owned Hidden bookmarks/history and Private Apps catalogue use encrypted owner-bound stores. That does not extend to provider-managed WebView cookies/profile storage. An encrypted cookie snapshot alone would leave provider-managed copies unprotected and must not be described as resolving this requirement. This finding concerns the WebView provider, not AI-provider credential stores.

Do not activate a code54 signing marker or claim the full specification complete until the owner explicitly resolves the requirement or a reviewed implementation proves the requested at-rest protection across active sessions, crash/restart, lock, transport switching and cleanup. If the owner accepts ephemeral app-private WebView storage instead, record that exact scope decision and use accurate UI/release wording; do not infer a waiver from a request to continue.

Original-key code53-to54 upgrade/restart, final APK identity/certificate/hash verification and physical Fold acceptance also remain outstanding.
