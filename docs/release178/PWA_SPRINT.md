# Verified public PWA discovery and encrypted icons

Tested source: `5e1f83f58c87e2d75a6de1176da0204a5cf6d4da`.

[Grouped run40916333151](https://github.com/traynor1987/Private-gallery-/actions/runs/40916333151) passed application/test compilation,14 targeted JVM tests,4 API37 Android tests and4 Java host programs. Downloaded artifact12916018031 SHA256 `cd836e25e2a2d9d452624178bdba5274cf46f179c6550b3b300be0ed3c7b82d9`; source binding, XML and counts are retained in pwa-sprint-evidence.

Library editors now explicitly discover public manifest metadata and offer Private Web App or Private Website. Primary uses its existing owned VPN Network; Hidden uses its authenticated existing proxy. Metadata requests refuse redirects/cross-origin references, credentials, private literal destinations and ambient DNS. VPN DNS answers use existing public-address filtering. Responses and request lifetimes are bounded; revoked authority cancels calls and suppresses publication.

Manifest icons and favicon fallback are converted to small PNGs stored in existing encrypted catalogue records. No public cache. Both themes retain safe placeholders. Synthetic light-theme Android coverage verifies discovery, preview, save and encrypted restart; dark-theme CRUD and Primary authentication tests still pass.

Limits: public metadata only; no authenticated/cross-origin/redirect discovery; no service-worker installation, background push or offline guarantee; display preference is metadata and browser controls remain. Custom icons and dedicated app chrome are unfinished. Editor cancellation discards results but may leave bounded network work running until completion/deadline; revocation actively cancels. Synthetic interceptor/fixture tests do not prove real VPN/Tor routing or Hidden IPC.

Next: real authenticated Hidden broker discovery/CRUD/launch/revocation tests with synthetic HTTPS and Tor fixtures, then dedicated app controls and vault custom icons. Previous1.7.7 and upgrade evidence remain untouched. This is a development checkpoint, not a signed release.
