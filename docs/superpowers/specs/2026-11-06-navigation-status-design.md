# Unified NovaOS navigation and live status

Implement the owner's complete supplied UI integration specification on integration/novaos-1.7-home2,
starting from verified code58/fd6a492d. This is authorized implementation, no Home redesign.

Use one reusable NovaOS app control row (Recents, Home, Back), preserving Android system and
IME/gesture insets. It is application chrome, never a replacement system navigation bar.
Reuse existing navigation actions and security authority. Home retains its optional existing
navigation preference; three-button mode uses the shared component. Native supported roots and
owned workflows use that component; fullscreen media retains dedicated immersive controls.
App Mode alone adds overflow to the right of Back. Remove its floating top-right overlay.
Reuse every existing App Mode callback in a secure, scrollable bottom sheet above the row;
Back dismisses first, unavailable actions have disabled semantics and cannot invoke callbacks.
Opening/dismissing never changes WebView identity/session/audio. Background/lock/retirement
closes transient chrome and rejects stale callbacks. No Primary route exposes Hidden admission.

Status is a small, theme-aware row inside Home's existing header and at the top of immersive
NovaOS app surfaces only while the real Android status bar is hidden. Use callback/broadcast
battery, charging, saver, default connectivity and allowed telephony updates, with exact
percentage, validated internet distinct from Wi-Fi association, unknown/restricted details
omitted. No polling, location, SSID/SIM data or privileged permissions. Optional VPN badge
requires existing networking authority; omit it if no suitable authority can safely support it.
Choose this bounded shared-chrome approach over replacing browser/navigation engines or
adding global overlays; those alternatives increase lifecycle/privacy risk and violate scope.

Independent implementation checkpoints: navigation/menu, live status; one combined focused
API37 verification and original-key58-to-next upgrade through existing workflows. Preserve
Gallery/Vault/authentication/keys/data, BrowserV1 engine/history, code58 wallpapers, Home design,
Store and playback. No VPN/Tor repair. Physical Fold acceptance remains owner-side.

## Added first-launch requirement

Verified code58 still contains both defects: failed cold controller network admission loses
the saved-app launch, and presentation policy enables browser chrome for App Mode failures.
Queue a captured authenticated saved-app intent, keep app-owned privacy/loading/error UI
while policy blocks, and bind through the original retained session API only after real
readiness. No engine/gate/settings changes, artificial delay or unnecessary VPN initiation.
Revalidate saved UUID/definition, original authority and privacy acknowledgement. Return Home
on failed admission/changed definitions; Home/background/lock retire pending work. Verify
cold/warm/blocked/retry and Home reopening, including unchanged UUID/WebView/audio.
