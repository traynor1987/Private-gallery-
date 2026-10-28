# Tor parity architecture: read-only finding

**The current Tor browser has a real Section5 capability gap.** `HiddenWebViewFactory` sets blockNetworkLoads=true and disables scripts/storage; `HiddenBrowserRuntime.onShowFileChooser` always cancels; the Tor HTTP interceptor accepts GET only and does not receive request bodies. A native HTML POST or multipart upload does not require JavaScript, so JS-off-by-default does not justify omitting these capabilities. Adding a file picker to the existing intercepted renderer alone cannot deliver the POST body. This investigation changed no repository source and produced no runtime evidence.

## Recommended contained design: native Tor child + deny-all local VPN boundary

A viable architecture exists using existing Tor binaries and public Android APIs. It is an engineering design supported by the component contracts below, **not a proven implementation**:

1. Stop Primary browsing and the WireGuard owner; confirm release. Establish an app-owned local `VpnService` whose TUN is a **sink**, not an internet tunnel. Route the app package into it, cover both IP families (or deliberately block an omitted family), and give its DNS a synthetic sink destination. Drain/discard TUN packets. Do not call allowBypass, exclude the application, forward arbitrary TCP/UDP, or configure a DIRECT proxy alternative. This consumes Android's one active VPN slot only as an enforcement boundary for Tor Only; it does not chain WireGuard+Tor and does not require a provider profile.
2. Start a session-authenticated local **Tor relay egress bridge**. Only this bridge may call VpnService.protect(Socket), before connect, to open underlying-network TCP connections requested by the Tor daemon. Restrict it to authenticated CONNECT requests with numeric relay/guard addresses and valid relay ports, with no hostname resolution or general origin-URL request API. Tor relay ports are not confined to80/443. Public-address restrictions apply to production; any controlled private relay fixture must be explicitly debug-only.
3. Configure the existing Tor daemon to reach its OR connections through that bridge, using supported `Socks5Proxy` + RFC1929 credentials, or `HTTPSProxy` + `HTTPSProxyAuthenticator` and HTTP CONNECT. These are **outbound Tor options**, distinct from Tor's browser-facing SocksPort. A separate role/secret/listener is mandatory: never let the browser's origin proxy use the protected direct-egress path. No Tor shared-library/JNI socket hook or replacement binary is required. The default client uses directory streams through OR connections; direct paths not covered by that configuration still hit the sink and fail, rather than leak. Exact packaged0.4.9.14 cold-bootstrap/directory behavior must be verified before adopting this route.
4. In a fresh non-exported Tor browser process, claim its authenticated generation, verify the exact owned sink VPN Network, and bind the process to it **before any WebView/provider initialization**. Use the existing process-admission/retirement/cleanup mechanism, with a Tor-specific mode identity and fresh dedicated data/cache roots/profile. Do not reuse or rebind a VPN-mode process.
5. Configure a separate authenticated HTTP-forward/CONNECT browser proxy with no bypass/DIRECT route. Its upstream connects only to the existing Tor SocksPort on loopback and sends the **unresolved destination hostname** (SOCKS5 DOMAIN), including v3 onion names. It must never perform local website DNS. Native WebView then handles HTML forms, POST encodings, multipart bodies, cookies, redirects and canonical origins itself. Keep JS disabled by default; file chooser/picker, selected-media pipes, explicit egress consent, vaultOnly restrictions, supported GET downloads, website dialogs, tabs, desktop layout and library UI can use the same polished child shell/broker interfaces as VPN mode.
6. Every revoke closes browser proxy admissions/streams and the protected relay egress bridge, kills/retires the Tor process, and destroys the child while it remains bound to the old Network. Keep the sink established until the browser/Tor lifetimes are confirmed stopped, then release it. OS revocation can remove the sink first; the child's immutable dead-Network binding must fail, not clear or rebind. Broker/child/relay-service death, mode changes, screen-off, timeout and late callbacks retain the already-reviewed authentication-epoch rules. New mode means a new process, new lease and independent storage.

Android's per-app VPN routing operates at package/UID level, so a plain in-app full TUN would also capture this package's Tor process and recurse. The protected relay bridge solves that without excluding the whole package or turning on allowBypass. Merely placing Tor in `:hidden_tor` does not give it a different UID. Binding the Tor process to a physical Network or setting Tor's source IP is not a substitute for a proven protected-egress boundary.

## Why a proxy by itself is insufficient here

ProxyController governs WebView URL networking, not a complete operating-system egress firewall. A separate process prevents Primary proxy changes, but supplies no Tor-only Network by itself. Native DNS-prefetch/preconnect, provider traffic, QUIC/UDP or future networking behavior must be contained even if normal HTTP(S) requests obey a proxy. JS-off reduces JavaScript/WebRTC/worker reachability; it is not an OS guarantee against every native network path.

With the proposed sink and immutable process binding:

| Path | Allowed result |
|---|---|
| HTTP(S), native form POST, multipart | Browser proxy → Tor SOCKS hostname → Tor circuit |
| HTTPS/WSS | End-to-end TLS inside CONNECT/Tor; never MITM |
| Website DNS | Tor remote resolution only; local DNS attempts hit sink |
| DNS-prefetch, direct preconnect, provider auxiliary requests | Sink/drop, unless correctly routed through the Tor browser proxy |
| QUIC, UDP, STUN/WebRTC/IPv6 bypass attempts | Sink/drop; no UDP-over-Tor functionality is assumed |
| Old service workers/cache | Fresh independent profile plus verified cleanup; Tor worker networking can remain blocked with JS off |
| Tor relay/guard connection | Authenticated relay bridge → protected TCP socket; no site-name resolver |
| Route loss / proxy failure / Tor death | Fail closed; no default-network retry |

An optional packet-to-SOCKS full tunnel could instead forward all TCP and explicitly handle Tor DNS while dropping unsupported UDP. That is a larger native/network-stack dependency and is unnecessary for native HTML form parity when an HTTP/CONNECT browser proxy plus sink can provide the same routing boundary.

## Official Tor/Orbot alternatives and actual constraints

Orbot and Tor VPN provide real app-level Tor VPN routing; their existence rules out a general claim that Android WebView+Tor native forms are impossible. Orbot's current repository explicitly depends on hev-socks5-tunnel, and Tor VPN supports selecting individual apps. An external provider can therefore supply a Tor Network for a freshly pinned browser after user setup, with separately verified provider ownership, DNS, routing and revocation. This introduces installation/configuration/provider-identity dependencies and changes the current self-contained experience. Modern Orbot releases also changed/removed old background-start receiver behavior; do not assume a historical START broadcast is a maintained integration contract. Embedding an Orbot tunnel stack requires independent licensing, packaging, ABI and native-audit work; it is not just enabling the existing TorService.

Concrete acceptance constraints, rather than impossibility claims:
- Android permits only one active VPN service; stop/confirm WireGuard release before the Tor sink/other Tor VPN, and reverse the sequence on return. A device configured with another always-on/lockdown VPN can prevent establishing this boundary; report connection unavailable and do not fallback.
- TorService currently configures no outbound egress proxy and the app has no Tor sink VpnService/relay bridge; this design is not a small picker-only patch.
- Protected sockets intentionally bypass the sink **only for Tor relay connections**. Protect failure, credential failure, unknown target type, relay/TUN death or authority loss must reject before connecting. No protected browser-origin sockets may exist.
- The source consulted for Tor connection/directory internals is the project's current generated0.5.0.0-alpha documentation, not a pinned0.4.9.14 source audit. The options are long-standing; exact packaged-source/config/probe verification remains a concrete dependency, not a demonstrated blocker.
- Production Android network-security policy permits cleartext onion origins but blocks other public HTTP origins. That existing product policy needs explicit capability mapping; native form support for HTTPS and HTTP onion sites does not silently broaden Primary's global cleartext policy.

## Required proof before activation

First prove the smallest vertical slice with unchanged packaged Tor: cold bootstrap under the sink, all successful guard/directory TCP seen only through authenticated protected bridge, native JS-off HTML form and multipart upload to a controlled onion endpoint, exact body/hash, successful HTTPS through CONNECT, and no visited-site DNS on physical interfaces. Then deliberately trigger DNS-prefetch/preconnect/QUIC/UDP/IPv6 and worker/cache paths; assert sink capture/drop and no external origin/DNS packets. Disable proxy, revoke VPN permission, kill each process/service, switch modes during uploads/downloads, and replace networks. Verify old child cannot rebind; verify both authentication deadlines, explicit return, library/profile separation, persisted cleanup after death and no newly authenticated session can inherit old callbacks. A successful SOCKS GET or Tor bootstrap alone proves none of the body/native-browser isolation requirements.

## Primary sources checked (28 October2026)

- Android VpnService / protect / revocation / one prepared owner: https://developer.android.com/reference/android/net/VpnService
- Android routing, allowed applications, families, allowBypass: https://developer.android.com/reference/android/net/VpnService.Builder
- Android VPN implementation guide: https://developer.android.com/develop/connectivity/vpn
- Native process binding and dead-Network socket/DNS failure: https://developer.android.com/ndk/reference/group/networking
- WebView proxy override scope and completion: https://developer.android.com/reference/androidx/webkit/ProxyController
- Chromium proxy protocols and SOCKS TCP/name-resolution scope: https://chromium.googlesource.com/chromium/src/+/HEAD/net/docs/proxy.md
- Chromium DNS-prefetch design (demonstrates an independent native feature, not a leak claim): https://www.chromium.org/developers/design-documents/dns-prefetching/
- Tor outbound proxy option/auth validation: https://tpo.pages.torproject.net/core/doc/tor/config_8c_source.html
- Tor outgoing proxy selection/auth implementation: https://tpo.pages.torproject.net/core/doc/tor/connection_8c_source.html
- Tor client encrypted directory streams and SOCKS direct-path rejection: https://tpo.pages.torproject.net/core/doc/tor/dirclient_8c_source.html
- Packaged library upstream and supported native TorService model: https://github.com/guardianproject/tor-android
- Maintained Orbot app and tunnel dependency: https://github.com/guardianproject/orbot-android
- Orbot release/integration changes: https://github.com/guardianproject/orbot-android/releases
- Official Tor VPN app selection: https://support.torproject.org/tor-vpn/features/config-apps/
- Tor TCP transport limitation (do not infer UDP delivery from proxy setup): https://support.torproject.org/relays/legal-and-abuse/ddos/
