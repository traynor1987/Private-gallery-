# Private Gallery 1.7.1 design

Owner supplied a complete brief and explicitly requested autonomous design, implementation and delivery. Continue the existing Kotlin/Compose Material 3 application on release/1.7.1-professional-ui. Baseline23b4b1681736e7c9b6635a9c16184abfcc95b507, signed code33. New version1.7.1/code34.

## Direction and alternatives
Refine the existing neutral slate palette, stable navigation and authenticated callback interfaces. Use 4/8/12/16/24dp spacing,48dp minimum controls, restrained16dp cards, compact title hierarchy, clear loading/error/empty/selected states. No new navigation tabs. A replacement navigation/design framework would increase lifecycle and regression risk; per-screen unrelated styling would preserve inconsistency. Chosen implementation approach (agent decision within owner's brief): shared Material3 primitives plus bounded edits to existing screens.

AI Edit is the priority: source image card and provider/model/prompt sections; Combine Photos displays two actual in-memory authenticated thumbnails, explicit Photo1/Photo2 labels, replacement picker and larger previews. Preserve exact custom combine prompt, existing consent and provider multi-image requests. Results have preview/save/regenerate/discard. No disk caches or new provider APIs.

Media grids keep dense adaptive columns, square correctly oriented bounded previews and distinct loading/failure/selection. Collection cards have covers, titles/counts/menu; never migrate memberships. Settings keep existing auth gates and seven-tap diagnostics, with compact category selection and grouped surfaces. Camera changes are entry and control appearance only: no changes to capture controller, input gate, plaintext journal, lock finalisation or black surface behavior. Browsers use matching compact chrome and overflow-safe settings without network changes.

## Boundaries
Do not edit core browser/VPN, crypto/vault formats/keys, PIN lifecycles, camera controller/journal, provider transport or combine request composition. UI preview state must be discarded on lock/background/disposal; asynchronous thumbnail responses must not repopulate disposed UI. Preserve consent, full original source bytes for generation and bounded thumbnails for display. No face recognition, cloud backup or V2 lifecycle work. Preserve all prior branches. No owner-device operations or main merge.

## Validation
Fresh JVM/lint/build and full Android regressions; focused source replacement/prompt/result/lock tests, shared component and viewport screenshots across cover/inner portrait/landscape and light/dark. Independent UI/accessibility and sensitive-preview review. Physical Fold acceptance remains separate. Signed code34 must use original certificate94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17 and upgrade synthetic original-key code33 in place. Existing live Tor integration flakiness must remain visible; do not alter transports or weaken assertions to make CI green.
