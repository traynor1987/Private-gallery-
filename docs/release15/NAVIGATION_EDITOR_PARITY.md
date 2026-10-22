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
