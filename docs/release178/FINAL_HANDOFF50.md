# Private Gallery 1.7.8 / code50 owner-acceptance handoff

One original-production-key APK is verified for installation over code49. Never uninstall or clear app data. Physical Galaxy Z Fold acceptance remains OWNER_REQUIRED.

## Native Private Apps drawer

Saved apps appear as adaptive rounded-square icons with names and small pin indicators. Tap launches through the existing authenticated controller. Long-press or the accessible 48dp three-dot action opens the themed secure Edit, Pin/Unpin, Reorder, App Settings and Remove menu. Permanent management-button rows are removed. Search is local by app name; Add App remains clear.

Reorder uses the owner-approved accessible alternative to dragging: select an app, then its new position; Done exits. Entering reorder clears search and uses full persisted UUID order. One atomic encrypted MOVE_TO update preserves every saved field; pinning never silently changes order. Four columns at typical phone width adapt to Fold, landscape and large fonts. Existing names, custom/website icons, pins and settings retain catalogue schema2. No persistent plaintext preview is created. Primary and Hidden drawers remain separate. Pending PIN callbacks cannot launch from a retired drawer. Catalogue loads/mutations, discovery start/results and asynchronous revocation now explicitly publish on Android Main, with captured authority rechecked inside the dispatch.

## Retained app experience and limitations

Saved apps retain their standalone-style name/icon, direct start URL, branded loading, maximum content area and hidden browser tabs/address/footer in app style. Compact controls now keep Exit App and Screenshot directly accessible. History Back stays inside the app; root Back and Exit App return to the encrypted library without clearing the profile. Close App cancels owned operations/destroys that view. Switch App lists saved icons/names/current state and retains configured fresh-PIN checks. Exit to Gallery uses the authenticated originating vault. Lock Vault and Panic Exit keep their distinct existing revocation/emergency paths.

Screenshot now captures only the permitted visible app viewport, validates nonblank content, then presents a SecureOn memory-only review. Cancel publishes nothing before save. Save writes a distinct encrypted vaultOnly PNG in the captured authenticated originating Vault; success acknowledges the committed item. View Screenshot decrypts that actual saved item through a bounded owner/receipt gate. Delayed captures, close callbacks, in-flight reads, bitmaps, buffers and pipes are fenced and cleaned on replacement/navigation/exit/lock/lifecycle loss. No public Gallery copy or persistent plaintext preview is created.

Add Current Website opens the normal encrypted library editor with address/name prefill and automatic supported discovery. An unmodified current-page name adopts the manifest name. Compatible same-origin public metadata offers Install as Private Web App; otherwise Add as Private Website explains the limitation. Start URL/scope/icon and safe out-of-scope warnings remain supported. This is an experience inside Private Gallery, not a separate Android package; push/background/guaranteed offline capability is not promised.

## Security and limitations

Primary apps/tabs share the existing Primary profile; retention and clearing affect that shared profile. Close App does not promise per-app cookie deletion. Hidden apps remain within their separate ephemeral current VPN/Tor profile; closing that browser clears its logins. Hidden PIN-only authentication, session expiry/revocation, VPN/Tor/DNS boundaries, no direct-network fallback, Panic/Hide and Recents privacy remain intact. Screenshot save/view stays within its captured authenticated originating Vault. Android secure/protected/video/embedded restrictions remain enforced; JavaScript-disabled Tor pages cannot be inspected/captured. Tor JavaScript is not enabled automatically. Metadata requiring login, redirects or unsupported discovery can require an explained Website fallback.


## Exact-source verification

The final source passes 47 unique Android and 35 JVM cases, zero failure/error/skip/duplicate, and strict VPN/Tor/DNS/revocation packet gates. Coverage includes drawer tap/PIN/search/long-press/secure menu/reorder/reopen, production grid widths/themes/large fonts, separate owners and stale authentication, plus retained app controls, PWA, screenshot Save/View, documents, transfers, Panic and Hide. API37.0 rendering health has15 stable SurfaceFlinger observations and empty crash buffers. Independent source/runtime approval has0 open Critical/Important findings.

All prior failed runs remain failed and archived:80 runtime44/47,93 pre-instrumentation host-disk failure,eee runtime45/47. Harness corrections use real Close/disposal/fresh presentations and measured production grid constraints. Controlled onion readiness uses at most2 GET-only attempts within180s before WebView, original owned Tor/authority checks and closing watchdogs; incorrect content/status/security assertions fail immediately. No functional browser/POST retry, production timeout relaxation or direct fallback was introduced.

Production lint/build/identity/distribution pass. Actual archived original-signed49 to50 install-r, data verify and force-stop/restart pass without uninstall or clearing. The fixture preserves encrypted media, independent PINs, Recently Deleted, collections/favourites, browser cookies/settings, camera/AI/privacy configuration and all11 catalogue fields across three owners; candidate MOVE_TO reverse/restore persists after reopen. Root and independent reviewer verify the final APK v2 RSA-SHA512 signature, all103 content chunks, compiled manifest and original certificate.

Frozen application: `4820e176dcb60a3aa2f1bfd7b36e2de19fafee11`. Signing control: `d06c54c128a94dbd88d57e191ad80ccf0b1f79df`. Production run41200936129, artifact13042353155. Focused run41199473035, artifact13042566506.

Package `uk.co.traynor.privategallery`, version1.7.8/code50, min/targetAPI37, nondebug. APK 105601622 bytes.

APK SHA256: `df958e447ce1c5a9bc9b515d16de03c0d11a961ae6efb030b305b2dfe733ccf3`

Original certificate SHA256: `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`

[Verified GitHub artifact](https://github.com/traynor1987/Private-gallery-/actions/runs/41200936129/artifacts/13042353155). Download receipts follow in FINAL_REPAIR50_DELIVERY.json. Physical checklist: DRAWER50_OWNER_ACCEPTANCE.md.
