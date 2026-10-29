# Bounded public PWA discovery sprint

Use an explicit library editor action to inspect the entered website. Fetch public HTML, same-origin manifest and one icon (with favicon fallback), without cookies, redirects or renderer JavaScript. This supports Hidden Tor without enabling scripts. Primary uses its current owned WireGuard Network; Hidden uses its existing authenticated proxy. Every request is bounded and authority-fenced. Do not add a generic direct-network fallback.

Alternative renderer fetch was rejected: it would depend on JavaScript, authenticated page state and asynchronous DOM/transport behaviour. A new general browser transport is unnecessary. Metadata uses the already established pinned Network/proxy mechanisms with a short-lived constrained client.

Accept HTTPS (or v3 onion in Tor), standard ports and same-origin metadata/start/scope/icon references. DNS in VPN mode is resolved through the pinned Network and all returned addresses must pass the existing public-address policy. Tor origin DNS remains remote. Redirects are refused. Unsupported or unavailable metadata leaves a normal website entry available. No cookie/password import, service-worker installation, push or offline guarantee.

Normalize raster icons to at most64x64 and6KiB, store inline in the existing encrypted catalogue, and render only bounded validated bitmaps. No public cache. Custom vault icon selection and dedicated app chrome remain separate work if budget is exhausted.

Implementation: parser/service with injected fetch function and synthetic tests; constrained transport adapters; controller and Hidden bounded IPC discovery; shared editor discovery/preview and icon rendering; focused JVM/Android tests; one grouped workflow; archive evidence and recovery checkpoint. Keep version and1.7.7 files unchanged.
