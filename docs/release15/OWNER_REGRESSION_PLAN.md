# Owner regression correction — TEST ONLY

The owner-reported Copy/Move and Gallery-context regressions supersede acceptance of the earlier production candidate. Do not install that candidate or build another production candidate before physical acceptance.

Focused implementation:
- Preserve Hidden selection/session when independently authenticating Primary Gallery; retain explicit Return to Primary behavior.
- Restore Phone Copy/Move with captured destination authority, bounded verification, source digest comparison, and Android deletion confirmation only for verified copies.
- Add Primary Gallery selection Copy/Move to Hidden. Verify destination before guarded source removal into existing 30-day Recently Deleted, with explicit confirmation. Never resume deletion automatically.
- Preserve streaming formats, existing authentication/timeout policies, VPN and data layout.
- Add regression tests and independent review; run full Android CI and synthetic debug upgrade; produce TEST ONLY artifact.

Local Gradle execution is blocked: the Gradle distribution is absent and services.gradle.org is unreachable. Android runtime claims must come from CI, not source inspection.
