# Encrypted Vault wallpapers — design

Owner request: both Wallpapers → Choose from NovaOS Vault and encrypted Gallery/Vault image
viewer → Set as NovaOS Wallpaper use one authenticated, owner-scoped workflow.

Current blocking release is complete: NovaOS1.7.11.1/code57, frozen source
3af2289e444771fd87188743879bec3361ea6346, original-key56→57 upgrade/restart passed.
This enhancement is a separate implementation checkpoint and next 1.7.x release.

Use the existing repository/authenticated EvidenceAccess reader and SpaceContext encrypted
wallpaper store. A captured actual-image owner plus presentation identity controls a shared
native grid/editor; retain current crop/blur/dim/Fold behavior and approved appearance.
Primary viewer within a retained Hidden presentation must still capture Primary image
authority explicitly. Never infer image owner merely from Hidden selection.

Both entrypoints present a draft, then explicit owner-labelled Apply. Cancel changes nothing
and Apply returns to the originating Home or unchanged Gallery viewer. No plaintext image
file, shared/exported URI, external editor, persistent thumbnail or new encryption key.
The original source image remains unchanged. Public Phone Gallery gets no new encrypted
Vault action; the existing Android system picker remains supported.

Use an authoritative wallpaper record within the existing encrypted Home preferences to
avoid stale Home layout saves replacing Gallery-applied wallpaper. Legacy wallpaper is a
read-only fallback until deliberate Apply/reset. Serialize ciphertext write, preference
commit and obsolete ciphertext cleanup; publish UI only after successful commit. Failed or
cancelled drafts must never enter PreviewData.pending or later retrySave. Preserve unrelated
Home customisations and original ciphertext/AAD formats.

Retire plaintext work/previews on lock, timeout, Panic, Space/presentation change and obsolete
request. Retained Hidden authentication alone is insufficient. System picker handoff follows
existing EvidenceOwnerSession/EvidenceLockerHost: retire sensitive presentation, keep only a
metadata ticket, validate same authentication/owner/request upon foreground return and acquire
fresh access. Never extend timeout or revive an old authority.

Choices: reuse the current editor and native stores. Separate readers/crypto, public export,
and independent Home/Gallery setting flows conflict with the owner's requested architecture.

Verification: actual Primary/Hidden repository isolation, both entrypoints, image-only action,
cancel, explicit persistence/reopen, ciphertext-only outputs, unchanged source image, crop/Fold,
stale callback and session/presentation retirement, safe system-picker handoff, bundled-only
lock screen and existing Gallery editing. Physical Galaxy Z Fold remains owner acceptance.
