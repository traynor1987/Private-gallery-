# Navigation and editor parity — owner acceptance sprint

Baseline: f26d24ad47dc3b3c4b0b2c866bef4d9a7f7dc90f. No production artifact is authorized by this sprint.

## Changes
- Primary Gallery is the original Phone Gallery without the source selector. Hidden alone has contextual Phone/Primary browsing; the existing independent authentication remains.
- Explicit Return to Primary clears the contextual source flag. Phone Move uses the effective source, so a stale Hidden browsing flag cannot disable normal Primary moves.
- Hidden uses the existing PhotoEditor and CreateImageSheet. Full-resolution encoded reads share Primary's 48 MiB editor bound (large media import/playback limits are unchanged). Edited/generated copies are distinct, verified, encrypted Hidden items with provenance. Originals remain intact.
- Primary editor requests capture original presentation authority; navigation, locking and switching revoke it. Both destinations gate publication. Generation callbacks reject cancelled/stale requests.
- Editor destination captions and pinned Crop/Adjust/AI tool selectors improve clarity and reachability on narrow screens.

## Actual supported parity
Both: crop/auto-crop, rotate/flip, brightness/contrast/saturation, preview, undo/redo/reset, Save copy, configured AI editing providers/capabilities, Replicate creation and supported reference/model controls. There is no existing Replace Original editor command or filter-preset library to port. Combine Photos remains excluded. Hidden never exports edits to Phone Gallery.

## Verification before CI
- Hidden host foundation: 373 checks; synthetic isolation/collections/favourites and lifecycle checks pass.
- Authenticated streaming/seek/tamper at 20/100/500 MiB and low-storage checks pass under 48 MiB host heap.
- Browser helper: 3 tests pass. WireGuard dependency audit, retired model audit, source secret scan and diff integrity pass.
- Independent read-only review found and resolved positional API compatibility, stale Gallery flag affecting Phone Move, and pre-provider authority check. No remaining concrete blocker identified by inspection.
- New Android coverage: selector context; return flag reset; Primary editor authority; Hidden editor UI; local/AI/generated destination isolation; distinct copies; cancellation/epoch; publication denial; old metadata; full encoded read over 16 MiB; cancelled creation callback race.
- Android/JVM/lint/upgrade execution pending CI. No runtime pass claimed at this checkpoint.

## Owner acceptance still required
Folded/unfolded editor controls and crop/adjust previews; Primary Gallery without selector; Hidden Primary browsing preserves Hidden Vault/Favourites; Copy/Move all accepted directions; explicit Return authentication; both Save copy destinations and unchanged originals; configured provider edit/create and cancellation; background lock/screenshot settings; real WireGuard browsing. Live paid provider requests are not made by synthetic tests.

Browser state remains shared between vaults. Separate environments and Tor are deferred to 1.6. Production V1 remains untouched.

## CI 612 diagnosis
Source 1fd0ecf69bab723bee32827c0db0eb27ea78aca9 compiled; JVM/lint passed. Android: 217/218 passed, zero skips. New Hidden editor test successfully saved and verified a Hidden copy, then queried the wrong menu accessibility label (Vault menu rather than the existing title-derived Hidden Vault menu). Corrected only that selector; all assertions retained. Synthetic upgrade was correctly gated off by the failing suite. A full clean CI rerun is required before delivery.

## Final verification — CI 613 PASS

APK source: `0fe169a9f4267e14605a40201fe0f9df8b0951ce`.
GitHub Actions: https://github.com/traynor1987/Private-gallery-/actions/runs/39803675811
Artifact: `private-gallery-debug-and-instrumentation-diagnostics`, ID `12439155308`.

- 218/218 Android tests passed; zero failures, errors or skips. The corrected Hidden editor UI flow passed in full.
- JVM tests, Android lint, debug/app instrumentation builds, source checks and APK checks passed.
- 373 Hidden foundation checks and 20/100/500 MiB authenticated stream/seek/tamper tests passed; browser helper 3/3 passed.
- Synthetic V1 code28 → V1.5 code29 install-with-replacement passed. Seed, post-upgrade verification and post-restart verification all passed. Existing PIN, encrypted photos/video and payload hashes, collections/favourites, settings and VPN configuration were preserved.
- This upgrade test used the separately installable debug package. It is evidence of data-format compatibility, not approval to install a production update.
- Navigation, independent Primary authentication while Hidden remains selected, explicit Return, Phone/cross-vault Copy/Move, cancellation, stale publication, screenshot policy and encrypted persistence regressions passed.
- Independent review resolved all three findings; no further concrete security blocker was identified by inspection. Live cloud requests and real WireGuard traffic on the owner's Fold remain physical checks.

### Editing parity matrix

| Existing capability | Primary | Hidden |
|---|---|---|
| Crop / auto-crop / rotate / flip | Shared editor | Same shared editor |
| Brightness / contrast / saturation | Supported | Supported |
| Previews / undo / redo / reset / cancel | Supported | Supported |
| Save edited copy; original retained | Primary repository | Hidden repository and key |
| AI edit tools/effects, configured providers/models | Existing controls | Same controls; isolated save |
| Image creation and supported references | Primary references/save | Hidden references/save |
| Collections / favourites / Recently Deleted | Primary-only | Hidden-only |
| Replace Original / filter presets / video editing | No such existing editor feature | No invented feature |

Destination labels identify Primary or Hidden. Crop/Adjust/AI selectors remain outside the scrolling controls. Existing Fold-sized tests passed; narrow portrait and landscape screenshots were visually inspected. Real folded/unfolded interaction still needs owner acceptance.

### Verified TEST ONLY APK

Filename: `Private-Gallery-1.5-TEST-ONLY-0fe169a.apk`
Package: `uk.co.traynor.privategallery.hiddenprototype`
Version: `1.5-TEST-ONLY`, code `29`, debuggable.
Size: 38,222,460 bytes.
APK SHA-256: `e99c95e625044e61a755888c117cb4c36751aedcb767a4e270b963649e36c288`
Signing: Android Debug, RSA 2048, APK Signature Scheme v2; independently verified signature/content digest and matched CI apksigner output.
Certificate SHA-256: `4109e2e599e6cd43abbefbe1eba6be62ee74c3e7f3d01496ac05cda52e7f71f2`
Original production certificate SHA-256: `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17` (different).
Prior CI611 TEST ONLY certificate SHA-256: `944ee6b22ff36018f34e8888056bfaf7ac82ced687e6364757f1f96b40e00992` (different).

This APK installs alongside production V1. It cannot update the CI611 TEST ONLY installation in place because its development signer differs. Do not uninstall or clear an existing test installation containing valuable media. Use a separate disposable test device or recover the matching development signing identity before updating that installation. No production APK was produced, installed or published in this sprint.

### Remaining Fold acceptance

1. Use a compatible TEST ONLY installation environment while preserving production V1 and existing test data.
2. Primary: Gallery opens Phone directly with no selector; Vault/Favourites remain normal; five navigation tabs and Copy/Move remain familiar.
3. Hidden: Gallery's Primary view requires appropriate authentication, then Vault/Favourites still show Hidden. Explicit Return requires Primary authentication.
4. With disposable media, repeat Phone Copy/Move to both vaults and Primary Copy/Move to Hidden. Deny/cancel source deletion and verify originals remain with a copy-only result.
5. In both vaults, crop/rotate/adjust/undo/reset/cancel and Save copy. Verify destination and unchanged originals; check folded/unfolded portrait/landscape controls and orientation.
6. Check configured AI providers and image creation, preview/cancel/save and Hidden-only reference selection. Background/lock during editing/generation must discard stale results.
7. Check screenshot settings, collections/favourites, restart persistence, larger media and actual WireGuard browser traffic.

Owner acceptance is pending. Browser state remains shared; separate environments and VPN+Tor belong to 1.6. Main, V2 recovery branches and PR #59 were not modified.
