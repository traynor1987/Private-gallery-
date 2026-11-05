# Nova Chat Lite1.0 native Favourites implementation

User authorises autonomous preview delivery. Main preview version0.9-preview/code11 (feature1.0; not NovaOS2.0). Existing branch, package and certificate only. Baseline500509471d9a5f14723b49c40db0ab3fedcc8512.

Use the retained WhatsApp WebView, not a duplicate messaging/session engine. Replace the unsupported native guard presentation with a verified native-Favourites convenience filter. Only built-in fixed English UI labels/structural controls and explicit aria-selected/pressed/checked states can authorise reveal. No conversation text, IDs, counts, network, storage/auth access or message actions. Unknown DOM stays opaque. Native cover before navigation and initial checking; document-local neutral cover and lightweight observer cover any loss of selected state before restoring. Mutation checks are debounced, native checks bounded, no reload loops. On Home/app/resume/configuration recheck without recreating or clicking already-active Favourites. Normal mode explicit confirmation disconnects the adapter, preserves session and leaves native WhatsApp available. Search/other navigation can expose other chats; no access-control or isolation claim.

- [ ] Test exact closed state protocol, no reveal on unverified/unknown state.
- [ ] Bundled adapter: sidebar-only filter control identification, native click once, selected-state verification, debounced structural/state monitoring and cleanup.
- [ ] Retained model lifecycle/cover controller, minimal Friends header/error/settings, existing Inspector entry, Sanctuary optional entry. Keep former manual approval data untouched but do not pretend it controls native Favourites.
- [ ] Synthetic fixtures: load/activation/already-selected, reloadAll restoration, state failure/missing/changedDOM/login, lifecycle/Fold/session and no account side effects. Browser/media and upgrade regression.
- [ ] Focused API37 build, stable development cert, prerelease raw APK, independent hash/source/package verification and handover.

Live WhatsApp DOM cannot be inspected here; official docs confirm Favourites convenience but do not document DOM selectors. Locally validated structures must pass on the owner device; unsupported markup fails closed and is reported honestly, not guessed.
