# 1.7.5 independent security review

Read-only independent reviewer passes examined baseline03eb984b to implementation/review fixes. No Critical finding or demonstrated cross-vault disclosure was identified. Runtime verification remains a separate release gate.

Important findings corrected:
- A protected video completing after native focus cover could autoplay because only existing players were paused. Initial playWhenReady and newly registered players now check native cover, with late-completion instrumentation regression.
- Network player pause/resume without stop/start could remain stopped without retry. ON_RESUME now restores foreground/recovery UI. Lifecycle regression added.

Final native-cover review found and corrected an Important neutral-entry deadlock: reading pending state only inside SideEffect did not establish Compose observation, so Panic on an already unchanged LOCK/SETUP route could leave the curtain pending. The scheduling condition is now read during composition; a repeated-neutral-Panic regression asserts eventual uncover. Final-source runtime now passed.

Further review:
- Adjacent previews remain limited to immediate neighbours, use1.2Mpixel bounded rendering, current image16Mpixel cap. No public plaintext cache. Source buffers/cancelled deliveries wiped; owned bitmaps recycled at replacement/disposal. No new domain-sharing state.
- Duplicate selected/adjacent test tag corrected to distinguish selected surface.
- Replaced two-animation-frame Panic cover timing with a pre-draw listener registered only after neutral locked composition commits. Pending Panic cannot be uncovered by focus restoration. Underlying accessibility descendants are suppressed while covered.
- Already dispatched explicit Primary favourite/collection metadata changes can finish after lock under existing collection-write semantics. They remain in their original Primary domain and stale UI callbacks are rejected. Panic does not roll back already-authorised metadata operations. This release does not rewrite collection persistence; Hidden retains operation/session commit checks.

Remaining physical checks: actual Fold gestures/rotation/open-close; Recents and screen flashes on supported Android versions; recording finalisation; accessibility and screenshot-enabled behavior. Synthetic state assertions cannot establish a visible-frame or coercion-protection guarantee. Original signer/package and37->38 fixture upgrade/restart remain mandatory distribution gates.

Final gesture review confirmed duplicate pointer0 pan during symmetric scaling. The correction uses previous/current detector midpoints once during scale, and the same midpoint for non-scaling drags, resetting baselines when pointer count changes. Independent read-only follow-up found no concrete flaw; rendered centre/translation assertions passed in final-source focused and full runtime. No authentication or persistence change.

Final runtime source69bfade789df4a49d911091bb8cf8205c88a3a39 passed349JVM,91focused Android,74security/browser preflight and365unfiltered Android tests, including the corrected native cover and rendered gesture tests. This establishes automated coverage, not physical acceptance.

Independent read-only review of the release-only continuation found no concrete blocker: tested source/run/artifact/digest are pinned, unexpected prior failures rejected, exact passing XML counts and original debug APK hash validated, and fresh debug plus production upgrades required before publication. Signing material stays outside uploaded paths with unconditional cleanup. The reviewer could not independently inspect the remote commit as a local Git object; CI checkout/provenance and signature evidence must confirm it. No code edits were made by the reviewer.

Distribution gates completed in continuation40105499589: pinned source/artifact evidence, fresh349JVM/lint, debug and original-key production37->38 upgrades plus restart all passed. CI apksigner verified the original certificate and delivered APK hash; independent local identity/hash/ZIP check matches. Physical checks remain pending.
