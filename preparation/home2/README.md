# Inactive Home 2.0 Compose preparation

`app/src/main/java/uk/co/traynor/privategallery/ui/HomeDesktopScreen.kt` is inactive compiled preparation. It is not referenced by MainActivity. Activation requires focused Compose tests, authenticated persistence and revocation integration review. This checkpoint is not an APK or finished 1.7.11.

Implemented presentation draft: original procedural wallpapers; clock/date; swipe-up searchable drawer and explicit menu alternative; swipe-down internal Quick Panel; multiple logical pages; dock; folder open/rename/create; shortcut placement/removal/hiding; wallpaper dim/blur, grid and icon sizing controls; reduced-motion page navigation; Classic callback. Quick Panel uses caller-supplied real actions, never invented status.

Pending: runtime/visual verification, drag-and-drop, arbitrary app and folder ordering controls, folder drawer organisation, authenticated encrypted state persistence, Gallery wallpaper selection/crop/encryption, lock backgrounds, additional widgets with revocation-safe real providers, accent/theme integration, complete Fold/large-text/multi-window QA. No claim these are implemented.

Integration contract:
- Main owns authentication and app launch. Supply captured-session callbacks; revalidate authority on every callback.
- Key the entire composition by identity + epoch. Remove it synchronously on Lock, timeout or Panic; clear caller state, images and widget providers.
- Verify model Space matches captured EvidenceAccess owner; decode bytes only after authenticated decryption and wipe encoded plaintext after encryption. Model owner marker is not cryptographic authentication.
- Persist via existing lease.commit pattern with owner AAD; never plaintext preferences or thumbnails.
- Screenshot/Recents policy remains the existing host responsibility and must be tested for both Spaces before activation.
- Do not activate until code55 security baseline is resolved, reviewed and verified.
