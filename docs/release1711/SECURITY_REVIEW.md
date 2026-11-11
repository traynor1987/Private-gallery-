# NovaOS 1.7.38.5 Code 78 signing review

Source: c6ffbb769476b37b0c5bc4939e0f765eff3eef2a
Authorization: authenticated owner push
Physical Fold Main encrypted capture: PASS
Physical Fold Friends separation: PASS
Upgrade fixture SHA-256: feac998045bdf02081cf07b53a322e083907849280a11faa0497f2d01ea936ff
Upgrade controls SHA-256: 7f9a713fbb48907ee0b3ef76d5578b95f57d7ce3c7f97e157b907bcb59ac13f1
Runtime matrix SHA-256: 789b0eef5c8a8383ffefa050bb69064f32a447201838e2e61e6a204ee2092694
Runtime archive SHA-256: 0e93f6c2de0175ba5235bb120d87d6aefc891e806d1fb1712204f62071982bfd
Signing workflow SHA-256: 452ba2d99cb6e29a557183f288625bec61b74f59568ed47a4be9a035142060db

Runtime run 43032632621 (#307), artifact 13866005380. Exact-source 263-case archive validation passed.

Upgrade fixture uses synthetic data and checks the retained Guardian message, training label and ciphertext before and after process restart. Android execution remains pending the approved original-key signing job.

Ordering: authenticated owner approval and runtime evidence validation precede baseline fixture compilation and signer access. The Android 17 in-place upgrade and process restart run after candidate signing. Staging requires a passing upgrade receipt bound to the frozen source and candidate APK checksum. On failure the workflow uploads only selected diagnostics.

Policy: the authenticated repository owner approves a frozen release revision by committing only .novaos-production-authorization with the immediately preceding revision SHA. Future releases require fresh approval. The GitHub push sender and first-attempt workflow actor are verified against the owner account ID.
