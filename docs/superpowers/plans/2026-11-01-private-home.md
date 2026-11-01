# Private Home implementation plan

Goal: complete the owner's 1.7.10 specification on the existing release branch.
Architecture: reuse Compose destinations and session-bound EvidenceAccess leases for a separate encrypted Home preference file; no new browser or authentication path.
Constraints: Android 17; original signing identity; no destructive upgrade; existing-install Gallery startup; Hidden PIN only; one grouped verification campaign.

1. Extend HomeLayout with bounded round-trip persistence and unpin; run host checks.
2. Add owner-bound encrypted Home preferences and responsive Compose launcher. Keep search transient and scoped to the captured vault authority; clear on retirement.
3. Integrate Home into MainActivity and Hidden authenticated presentation, startup migration, feature routing and Back. Reuse VaultHome modes and Browser Private Apps entry.
4. Replace Evidence full-workflow windows with activity pages while retaining security controllers, picker tickets and consent. Add staged import review.
5. Repair screenshot window policy without weakening privacy covers, credentials or Recents.
6. Run host checks, grouped API 37 compilation/instrumentation, independent security review and original-key code 51 upgrade before signing delivery.

Review focus: lock during search or save; delayed callback after vault switch; process recreation with encrypted preferences; picker foreground gap; screenshot enable while a sensitive child window exists.
