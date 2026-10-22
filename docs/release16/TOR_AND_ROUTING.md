# Hidden browser transport and limits

Implementation candidate, not a Tor Browser replacement and not physically accepted.

Dependency selected: Guardian Project `info.guardianproject:tor-android:0.4.9.14` (native Tor with Android service, three-clause BSD declared in its POM), transitive jtorctl 0.4.5.7. POM verified against the publisher's gpmaven repository on 2026-10-22. README metadata/version examples lag the current published POM; use exact version, never a dynamic version. TorService API source reviewed. Native ABI/Android compatibility must pass CI and owner device testing. Upstream full licence bundle is retained in this directory and in in-app notices; final dependency/native composition audit remains a release gate. No Orbot VPN, Privoxy binary or pluggable transport is intentionally bundled.

Tor runs in a non-exported bound service in process `:hidden_tor`. Native HTTP uses an explicit SOCKS proxy; OkHttp's SOCKS path constructs an unresolved origin address, and local DNS is forbidden. SafeSocks rejects pre-resolved origin requests. Browser close kills only that dedicated Tor process so native connections cannot outlive the session. No onion destination is sent to device DNS. Circuit readiness is read through private Binder from Tor control state, not a spoofable broadcast.

VPN Only reuses the existing WireGuard owner. API30+ VPN Network ownership must match the app UID and the existing engine must report a verified peer. Native sockets use that exact Network.socketFactory and DNS uses Network.getAllByName. Network loss cannot cause those bound sockets to move to the default route. This remains unverified on a physical device; test VPN revocation during requests, DNS and downloads.

VPN + Tor is UNAVAILABLE. No implementation in this milestone proves Tor native sockets are bound to the owned WireGuard Network across revocation. Selecting it starts no alternative transport.

All WebView network loads stay blocked; only native-mediated HTTP(S) GET responses are returned. JS, workers, frames, file/content access, external handoffs, permissions, uploads and HTTP redirects are blocked. Hidden uses a supported unique WebView profile; unsupported multi-profile is unavailable. Cookies are separate memory-only native state (not full browser SameSite/third-party-context enforcement), no HTTP disk cache is used. Browsing sessions are cleared on route exit/mode change/background and never restored after process death. Existing orphan Hidden profiles are deleted on the next authenticated browser start; no stale profile is reused.

Limitations: many modern sites and logins/forms will not work. No JavaScript enable toggle, HTTP redirects, POST, downloads other than image/video, bridges or Tor Browser anti-fingerprinting protections. Media downloads stream into existing encrypted Hidden storage, with no DownloadManager/MediaStore path. Non-media downloads fail closed. WebView profile storage itself is Android-private, not a new encrypted browser database; this implementation avoids durable session restoration and cleans profiles, but does not promise forensic erasure.

Official API references reviewed:
- https://developer.android.com/reference/androidx/webkit/WebViewCompat#setProfile(android.webkit.WebView,java.lang.String)
- https://developer.android.com/reference/androidx/webkit/Profile
- https://developer.android.com/reference/android/webkit/WebSettings#setBlockNetworkLoads(boolean)
- https://developer.android.com/reference/android/net/Network#getSocketFactory()
- https://developer.android.com/reference/android/net/NetworkCapabilities#getOwnerUid()
- https://github.com/guardianproject/tor-android

Independent architecture review found the existing handshake-polling gap, Primary retained-page lifetime, stale Hidden authority, redirect-origin and service-worker hazards. The restricted design addresses these in source; real Android and packet-level checks are still required. No live routing/security guarantee follows from mocked tests.

Publisher Maven repository pinned to commit `5fdb8dbcfe98d1b7e027e2f99bc086709870b005`. Tor Android release tag0.4.9.14 resolves to source commit `0340ba1609cb73e663e202a316d083803e399db4`. Verified directly through GitHub API on2026-10-22. Native reproducibility and complete composition notices remain unverified.

Production retains Android's existing cleartext prohibition: use HTTPS origins. The reserved `.invalid` cleartext exception exists only in the TEST ONLY build for local SOCKS protocol fixtures; it is not in the production manifest. Plain HTTP onion sites are not currently supported. This limitation must not be mistaken for Tor network failure.

Native source-license inventory retained verbatim from the upstream release's pinned submodules: Tor `4a522a3ca491c6c04d4a44cb006b92101c42efc1`, OpenSSL `45e844fa2a14ec92d146bd8f5778ac130b6625fb`, libevent `79ddfb460847999b807cba76d04e73891f29c6ee`, zlib `da607da739fa6047df13e66a2af6b8bec7c2a498`, zstd `f8745da6ff1ad1e7bab384bd1f9d742439278e99`, plus jtorctl0.4.5.7 BSD notice. Copies are under licenses/ and bundled in ordinary About notices. The upstream top-level notice includes historical Orbot/Privoxy/iptables terms; their presence is not evidence those binaries are bundled. Native artifact inventory must be checked after a successful build.
