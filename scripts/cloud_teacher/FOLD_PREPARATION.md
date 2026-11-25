# Disarmed Fold owner preview

This launcher prepares `fold-owner-20261125` from Build 98 backend source
`26553075df3c530fd81a98640e59ddf09c545215`. The 2026-11-23 allowance remains
unverified. Use a clean, detached worktree of the reviewed preparation commit
with the Build 98 candidate commit available in the same Git repository.

The launcher checks for older preview traces and conflicting mounts or
listeners before it creates a new approval directory. An existing destination,
suspected ledger, or unclassifiable database stops preparation for recovery
review. Empty private directories left at the original approval path are
allowed and retained. Preserve any recovered files, including SQLite sidecars.

Run from the existing IPv4 SSH session on the same network as the Fold.

```bash
sudo bash scripts/cloud_teacher/fold-owner-preview.sh "$SSH_CONNECTION"
```

Success prints **Disarmed Owner Preview ready** and shows the separate preview
token directly in the SSH terminal. Enter it only in NovaOS → Settings →
Nova Brain → Owner Preview. Disable an existing preview connection first if
necessary. Tap **Check preview connection**. Both HTTPS routes must authenticate
with paid analysis off. The two synthetic send controls should be disabled.
Return to SSH and press Enter to print the token-free connection receipt.

The temporary HTTPS listener uses port 18443, the existing TLS certificate and
an allowlist for the Fold's address. Its private container has no published
port, outbound network route, provider credential or paid execution path.
The new SQLite ledger is bound to the new approval and preview token. It
records a two-request maximum and a shared US$0.25 ceiling. Both limits apply
to the two fixed synthetic inputs under any later, separately authorised paid
phase.

Ctrl-C or the 30-minute expiry removes the temporary listener, firewall table,
network and container. The new token and ledger remain under
`/var/lib/nova-fold-owner-preview/approval-20261125` for later verification.
Guardian production, its port, TLS files, NGINX sites and SQLite data are
outside this launcher's writes. This is a one-time preparation. A restart or
later paid phase must verify and reuse the newly prepared ledger.
