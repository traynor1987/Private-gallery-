# Supplemental browser compatibility evidence

- Source: `1be5dc953ccfc2bc374ccc9c3fa85c985920706e`.
- [Workflow 40184858082](https://github.com/traynor1987/Private-gallery-/actions/runs/40184858082): passed.
- Artifact: `12595971956`; ZIP SHA-256: `b83985e3bf7836185c7f53fb26314240b54a83e0e25d480bf09821829a1d45eb`.
- Downloaded archive digest, ZIP integrity and raw XML independently checked. Both API 36 and API 37 ran the two named light/dark tests: 2 passed, zero failures, errors or skips on each platform.
- Each test selected only a generated 133-byte PNG through the existing picker UI. Preparation remained blocked until the explicit Upload action; exactly one preparation occurred. The HTML File bytes matched the generated PNG SHA-256. FormData was serialized into a local 317-byte multipart Request; no network upload occurred.
- The same WebView remained interactive through viewport widths 840 → 392 → 840. Modal bounds, readable text, manual decline and subsequent page interaction passed.

The workflow first proved that production source and Gradle/build inputs match frozen release candidate `ec05b23ec28a44730439bd1090c1e64afff8f140`. These are supplementary results, not part of that candidate's full-regression XML. This synthetic picker callback is not proof of owner-vault authentication, actual network upload or physical Fold posture/configuration changes. Those retain their separate release and owner acceptance gates.
