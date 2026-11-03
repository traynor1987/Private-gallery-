# Kernel probe aperture rejected

Decision: REJECTED. This concerns the unshipped Task2b experiment, not a new Android result or a change to the original Critical C1 verdict.

The113-check Java17 helper suite passed but did not prove the temporal ownership premise. Independent review supplied actual loopback counterexamples; root compiled and ran both unchanged against the same frozen helper using Temurin17. The results were:

```text
FailedConnectRetention:
BUG: source binding free BEFORE revoke acknowledgement
connect: java.net.ConnectException
BUG: binding free after unknown revoke, isClosed=false

InheritedSocketPaths:
BUG: unregistered proxy endpoint received SOCKS greeting=5
BUG: new connection established AFTER logical close and unknown revoke
connect returned successfully=true
BUG: parked stream skip consumed bytes=1
```

An independent raw-socket experiment also retained a duplicated descriptor across a refused TCP connect. Another exclusive socket could bind the same source port before closing the original. Therefore adding an FD duplicate or a later Java catch/revoke cannot repair the failed-connect ownership premise. No real website, credential, token or external packet was used.

Ruling: do not integrate the helper — real inherited/kernel behavior contradicts the required source-binding invariant — retaining it would allow ordinary browser packets to match a stale parent probe aperture. Preserve the full experiment and counterexamples only under `native/continuousvpn/rejected-kernel-probes/`, outside Android sources. Existing tuple, UDP and packet assertions remain unchanged.

The bounded alternative under review uses the exact already-pinned WG netstack/gVisor sources for parent-only readiness, with real Android TUN traffic always denied during PROBE. This is a native verification transport change, not a browser-engine replacement or restricted Beta. The root-only host compile preflight for WG21636207a675/gVisor703fd9b7fbc0 succeeded with Go1.20.3 and `-buildvcs=false`; its dependency receipt SHA256 is `587ba39418cbcb97cf3208d188b3722ff2bf9297dcf94c051afddced389bd5e8`. It does not prove lifecycle, Android TLS trust, Android routing or C1 closure.

Required next steps: complete the independent lifecycle/TLS design review; implement tagged native probe ingress and safe stack retirement; wire the persistent Android fence and coordinated runtime; then execute the existing full packet security gate. No release approval or signing activation is permitted from this foundation evidence.
