# Coordinated Hidden session continuity candidate

Recovered branch HEAD: 562e09dc66ce0b98d0168bf302565282d9cc9802, including all newer valid work after the earlier UI checkpoints. This implementation continues that source on the same release branch. Last delivered signed APK remains NovaOS 1.7.10.2/code54. All 87 recovered remote heads have versionCode<=54; the pending candidate is 1.7.10.3/code55. No replacement signing identity or encryption format is introduced.

## Implemented scope

A parent-owned ACTIVE/TEMPORARILY_SUSPENDED/RECONNECTING/LOCKED/TERMINATED state machine coordinates Activity lifecycle, runtime monitor, proxy watcher and heartbeat. Authorized temporary backgrounding preserves the existing child WebViews, Private App/opener ownership and pending JavaScript/POST document state. Ordinary crypto/IO authority is revoked, not retained. The process remains pinned to the retired owned Network; a replacement is admitted only after the same Hidden authentication and grace remain valid and fresh owned VPN, handshake and DNS/HTTPS checks pass. No default-network bind or automatic page reload/POST replay is added. Stale worker callbacks carry their old authority and cannot inherit a later epoch.

Explicit Lock, expiry, Panic, authorized Close, renderer loss and process death retain hard revocation. Concealed reconnect screens expose accessible native Lock and Panic buttons without uncovering the page. Independent Primary expiry does not disarm the still-current Hidden hard controls. Production continuation is VPN_ONLY; Tor Beta keeps existing hard retirement and mandatory native route tests. Prior professional UI and picker changes are preserved.

## Limits and security gates

This is a source implementation, not a claim of actual Google website authentication or release approval. Provider embedded-browser restrictions are not bypassed; no external OAuth receiver, UA spoof, cookie import or route exception is added. A Google account approval alone is not website login. Synthetic tests require a distinct protected website-session response after a one-use callback for two controlled sites.

Existing WebView app-private disk profile persistence remains the disclosed Beta limitation; this work does not encrypt Chromium profiles or claim memory-only cookies. It adds no persistent cookie format. Retention is only within the original authorized Hidden grace. In-flight sockets may fail and arbitrary websites may not recover failed requests; the app does not turn failed POST into GET.

WireGuard teardown is bounded, not an instantaneous OS firewall transaction. Fresh Android Network binding and final Binder/UI commit are not atomic. Native traffic is permitted only on freshly verified transport once it is published, including the pre-UI-commit interval; source review and packet/race evidence are mandatory. Exact post-bind onPause race injection is labeled as injection, not physical task scheduler evidence. Verification-stage expiry advances only the continuation fixture monotonic clock while the real Main/runtime/child are alive and reconnecting, avoiding misattribution to the unchanged3s production DNS deadline; the separate bound-stage expiry retains the original real30s grace. Receipts distinguish both modes and require original stop/grace preservation.

Focused matrix selects26 API37 cases (15 no-fixture,11 actual-route), including existing VPN/Tor fail-closed controls, actual Main/runtime transitions, old-worker denial, accessible hard controls, expiry/death and12 reconnect race scenarios. Existing unchanged UI evidence is reused only in its recorded scope. Host checks pass as recorded by the implementation report; Android build/runtime, final independent source/security approval, original-key54→55 install-r/restart and release artifact checks remain pending.

Signing activation is absent. Existing acceptance workflow has inactive exact-source, exact-artifact and structured-review gates plus pinned original54 baseline and original certificate checks. Never mark this candidate approved from a substring, a host-only graph test or successful Google approval.
