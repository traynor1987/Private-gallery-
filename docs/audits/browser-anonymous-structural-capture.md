# Anonymous structural acceptance capture

Scope: acceptance diagnostics only, based on `acc33f8`. The owner confirms both current failures: A displays its entry gate then only a blue background; B dims its existing page and shows a blank overlay. Private target identities are deliberately unavailable and are not required. This change must not alter navigation, cookies, VPN, TLS, window, rendering or compatibility settings.

## Evidence contract

The existing `visible_elements` count only checks each element's own box and style. It is not a rendered-pixel measure. The extended bounded scan reports own and ancestor hiding, effective opacity, viewport intersection, ancestor overflow clipping, structural content candidates, fixed/absolute covering layers, stacking-context depth, relative z-index bands, and sampled overlap order. Layer records contain allowlisted enums, counts and percentages only. No selectors, tag labels from custom elements, IDs, classes, CSS strings, colors, addresses, attributes, text, form values, credentials, storage or request payloads are exported.

A structural content candidate means a standard control/replaced element or an element with a direct text node. The probe never reads that text. It is a geometric heuristic, not a judgment about the page's meaning. Five bounded points per sampled candidate test overlap. Unrelated elements in front are potential occluders, including translucent/transparent ones; hit testing does not prove paint opacity. Pointer-events:none layers are separately flagged because the browser's hit-test stack excludes them. Rounded corners, masks, clip paths, complex transforms, pseudo elements, closed shadow roots and cross-origin frame interiors cannot be fully resolved by this probe. Coverage flags and counts prevent those limitations being mistaken for a clean rendering result.

## One interaction at a time

Arming clears the previous pinned comparison, takes an explicit baseline and installs a one-shot capture-phase pointerdown listener. That listener takes another structural snapshot before normal target/bubbling page handlers, without preventing or redirecting the input. An earlier page window-capture listener can still run first; events inside frames are outside the top-document listener. The report therefore distinguishes a captured pre-input snapshot from the earlier armed baseline rather than pretending they are interchangeable.

The first native touch starts a numbered transition. Follow-up snapshots and the owner's final capture carry the same transition identifier, document generation and relative elapsed time. A generated in-memory document marker supplements native page-start observations. Root/body replacement flags distinguish DOM replacement from stable-document mutation; neither a page-start callback nor marker alone is asserted to prove a committed navigation.

Native HTTP/resource failures are correlated by time and tab without addresses. Errors before the interaction and during it remain distinguishable. HTTP 401 is an observation, never a causal verdict. A request may start before the touch and fail afterwards: callback timing is not request initiation timing.

The most recent baseline/follow-up/error comparison is pinned separately from the rolling event queue so normal resource traffic cannot evict it. Disable, clear, re-arm and WebView destruction cancel pending captures and remove one-shot listeners. The probe never reads or changes cookies/storage, intercepts requests, installs a JavaScript bridge, patches site APIs or changes DOM presentation.

## Physical capture procedure

Use the new acceptance build with verbose diagnostics on, REAL host and debug colours off.

For each application separately:
1. Load the application and stop immediately before the failing action: A at its entry gate; B at its working underlying page.
2. Open Browser diagnostics and select **Arm next interaction**. Wait for the console to close automatically after its baseline is ready.
3. Tap exactly the failing page control once. Let the failed view settle for two seconds without scrolling, navigating or changing tabs.
4. Reopen Browser diagnostics and select **Capture diagnostic snapshot**. Wait for completion, then **Copy all**. Send that single report labelled A or B, plus whether the familiar blue/blank-overlay result is visible. No URL or screenshot is needed.
5. Re-arm separately for the other application. The latest pinned comparison replaces the previous one, so copy each before proceeding.

Console-open focus values can reflect the native diagnostic dialog. The pre-input and scheduled post-input samples are the relevant focus measurements. Instrumentation measures structure, not final composited pixels; physical appearance remains the owner's ground truth.
