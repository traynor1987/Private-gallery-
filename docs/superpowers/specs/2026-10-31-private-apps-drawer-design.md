# Private Apps drawer design — 1.7.8/code50

Owner supplied the fifteen drawer requirements and has already authorised autonomous implementation/signing without routine approval pauses. Continue existing release branch from e31845df and preserve signed49/source60f089d. No new release name, browser engine, profile or catalogue schema.

Use a native Compose drawer: adaptive four-column grid at typical360dp width, up to8 on wider layouts and fewer for large fonts/narrow covers. Large rounded-square themed icon frames, names underneath, subtle pin badges, direct tap launch through the original controller/PIN gate. No permanent management labels/buttons. Long press and a labelled three-dot button open the same secure themed Edit, Pin/Unpin, Reorder, App Settings, Remove menu. Search filters names in memory; Add App keeps the current editor/discovery flow. Existing PWA/custom icons and all11 app fields remain encrypted and unchanged unless explicitly edited.

Approaches: direct drag adds pointer/scroll/accessibility ambiguity; repeated Move Up/Down keeps the rejected management UI. Choose the explicitly owner-permitted simple reorder mode: clear/disable search, select a source, then tap its new position. Persist one atomic MOVE_TO14 operation against the full encrypted catalogue, not filtered indices or repeated writes. Keep favourites in their stored position.

Private Apps Library remains SecureOn and owner-bound. Context/settings and pending PIN callbacks cannot revive a closed/revoked drawer. Primary/HiddenVPN/HiddenTor controllers and metadata stay separate. App Settings reuses existing controls/profile warnings. Preserve code49 app-style launch, Exit/root Back, screenshot review/save/view, PWA choices, existing logins and transport restrictions.

Verify tap/PIN, longpress/overflow, search, every context action, atomic reorder/reencrypted reopen, theme/Fold/font/accessibility and revocation. Retain mandatory network/auth/screenshot tests in one existing grouped workflow. Independent review must approve exact source/runtime with0Critical/Important before original-key49→50 upgrade/restart and signing. Physical Fold acceptance remains required after delivery.
