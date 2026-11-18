# NovaOS Code 82 / Build 86

## Recovered baseline (18 November 2026)

- Main: 72bdc9c6304cb06d92a6d9bd36beea3d276fc95a.
- Build85 exact APK source/tag v1.7.39: e062aac04c5de268db52f4b3fc6066e90cb4bbcd.
- Installed baseline: 1.7.39 / 85, package uk.co.traynor.privategallery.
- Original signing certificate SHA256: 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17.
- Latest stable APK SHA256: fede7eefd717cbac1806d8eddbd89a6687495852ae24f449675c3a093499401b, 151516847 bytes.
- Main CI470: 299 selected API37 runtime cases passed. Protected production36 and public publication11 passed.
- Existing GitHub metadata, independent identity/hash, pinned certificate, versionCode comparison, private FileProvider and Android confirmation are the release baseline.
- Existing backend source: scripts/cloud_teacher/server.py. GET /health → {status:ok}; authenticated GET /guardian/status → status, model, analysis_enabled, budget. POST /guardian/analyze requires per-request consent. Staging --staging forces analysis off and omits funded credentials/volume.
- Live HTTPS brain.jamestraynor.co.uk/health returned 200 and {status:ok}, TLS verification result0; /guardian/status returned401 without a token. No credentials or private payloads used. Authenticated live state remains owner configuration dependent.

## Implementation and checks

In progress. Do not treat this file as a release-complete receipt.

Build85's installed UI cannot be changed by publishing86. It will use its existing MB/bar interface for85→86; the new percentage/modal UI takes effect after86 installation.
