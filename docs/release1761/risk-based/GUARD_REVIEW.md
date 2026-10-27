# Future-admission change-path guard

The active Android patch admission is frozen at33e9669daeb3bf5a3d2cb0aae4d3480fe4b538c2. Its transitive helpers were independently checked unchanged. Its original one-run approval remains valid; this later guard is not a reason to cancel or duplicate Android testing.

A subsequent read-only review identified a permanent-policy gap: classify(unknown paths) reported full, but admission only pinned13application/build/upgrade objects and11reviewedfiles. An unlisted CI helper could change without automated scope invalidation, despite the independent-review requirement. This did not occur in the active candidate.

Corrective guard compares every changed path against recovery4c080ffc with gitdiff--no-renames, admitting only the exact reviewed implementation paths, scope/approval manifests, patch marker and explicit documentation paths/suffixes. Unlisted SDK/device verifiers, host security scripts, unknown workflows, renamed/deleted unreviewed paths and runtime source under the evidence directory are rejected. Reviewed-file deletion also fails its required hash check.

Two new negative/allowlist tests observed RED then GREEN. Complete host suite160PASS. Reviewer/root/risk_policy_security_review independently reviewed the actual two-file guard diff and approved a separate guard-only commit with updated11file approval hashes; no Android rerun is required because application/instrumentation/build inputs and scope remain unchanged. The guard was also explicitly applied to the frozen33candidate's18changed paths and passed.

The final backup containing this host-tested guard must be distinguished from the original Android-tested/signing source33e9669. No new Android admission or live-Tor/full-suite run is authorised by this guard addition alone. Original historical full436 remainsFAILED, and the natural-timeout failure remainsFAILED.
