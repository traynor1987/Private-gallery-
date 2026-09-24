# Viewer and image editor V1

Binding brief: owner's 29-section viewer/editor milestone, 24 September 2026.
Starting remote main: e2180fa304f10032ce7b52063110837113cc3755.

Use the existing neutral theme and GalleryMenuSheet. Preserve image gestures and video playback. Editing is a separate fullscreen workspace with transient state, crop, quarter-turn rotation, flip, brightness, contrast, saturation, undo/redo/reset. Render previews from a sampled original; final output is rendered once from the bounded original. Save copy always creates a new encrypted item, including identical content. Existing crop metadata stays readable, never modified by this editor.

AI providers receive only sanitized image bytes, optional normalized mask, and explicit edit parameters. No repository/context/session/key is supplied to providers. Capabilities control tool visibility. No remote provider is selected or configured in current sources. Production therefore starts unconfigured; no speculative endpoint or embedded credential. Configure only a documented adapter with its own secure credential boundary. Network policy is explicitly independent of Browser's VPN requirement, follows Android's normal routing, never binds around VPN. No remote request before provider-scoped consent. UI cancels processing on background and discards buffers. Results are sanitized and previewed, then imported through the existing verified encrypted pipeline.

No public plaintext files, no new storage architecture, no production release, no unrelated features. CI uses deterministic providers only. Physical Fold acceptance remains distinct from emulator CI.
