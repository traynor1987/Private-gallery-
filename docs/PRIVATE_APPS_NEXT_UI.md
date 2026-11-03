# First planned 1.7.11 UI task: immersive Private Apps

Owner-approved on 2026-11-03. Deferred from code55 so the unresolved mandatory VPN/DNS containment repair remains the sole implementation priority. This document does not create a release branch or change any version metadata.

Saved Private Apps should launch in immersive, standalone-style presentation using their existing rendering and session components. Hide the permanent NovaOS browser header, address bar, tab strip and toolbar. Give content maximum space while respecting system and keyboard insets.

Provide a compact app-specific toolbar, revealed by an accessible floating handle or a carefully scoped top-edge gesture. Include Back, Forward, Refresh, Nova Home, Switch App, App Settings and Exit App; collapse after interaction. Keep Android Back and an obvious accessible exit available. Prefer the handle wherever an edge gesture conflicts with website scrolling, pull-to-refresh or Android's notification shade.

Preserve keyboard operation, including screen pinning; Private App identity and authentication callback ownership; in-memory session state; Primary/Hidden separation; VPN/Tor routing; Lock, timeout and Panic Exit. Showing or hiding controls must not recreate or destroy a WebView. Support Fold cover/inner screens, landscape, light/dark themes, large text and accessibility.

Verification should focus on toolbar presentation/dismissal, Back/exit, keyboard and screen pinning, insets, retained WebView identity, callback/session ownership and Fold accessibility. Reuse unchanged rendering/session evidence; do not add unrelated CI suites. Implement in code55 only if the mandatory security repair is already stable and this work adds no release delay or risk; otherwise this remains the first planned 1.7.11 UI task.
