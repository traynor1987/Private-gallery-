# 1.7.5 independent security review

Two read-only independent reviewer passes examined baseline03eb984b to implementation/review fixes. No Critical finding or demonstrated cross-vault disclosure was identified. Runtime verification remains a separate release gate.

Important findings corrected:
- A protected video completing after native focus cover could autoplay because only existing players were paused. Initial playWhenReady and newly registered players now check native cover, with late-completion instrumentation regression.
- Network player pause/resume without stop/start could remain stopped without retry. ON_RESUME now restores foreground/recovery UI. Lifecycle regression added.

Final native-cover review found and corrected an Important neutral-entry deadlock: reading pending state only inside SideEffect did not establish Compose observation, so Panic on an already unchanged LOCK/SETUP route could leave the curtain pending. The scheduling condition is now read during composition; a repeated-neutral-Panic regression asserts eventual uncover. Fresh runtime is required.

Further review:
- Adjacent previews remain limited to immediate neighbours, use1.2Mpixel bounded rendering, current image16Mpixel cap. No public plaintext cache. Source buffers/cancelled deliveries wiped; owned bitmaps recycled at replacement/disposal. No new domain-sharing state.
- Duplicate selected/adjacent test tag corrected to distinguish selected surface.
- Replaced two-animation-frame Panic cover timing with a pre-draw listener registered only after neutral locked composition commits. Pending Panic cannot be uncovered by focus restoration. Underlying accessibility descendants are suppressed while covered.
- Already dispatched explicit Primary favourite/collection metadata changes can finish after lock under existing collection-write semantics. They remain in their original Primary domain and stale UI callbacks are rejected. Panic does not roll back already-authorised metadata operations. This release does not rewrite collection persistence; Hidden retains operation/session commit checks.

Remaining physical checks: actual Fold gestures/rotation/open-close; Recents and screen flashes on supported Android versions; recording finalisation; accessibility and screenshot-enabled behavior. Synthetic state assertions cannot establish a visible-frame or coercion-protection guarantee. Original signer/package and37->38 fixture upgrade/restart remain required before distribution.
