# Preview0.9 / Nova Chat Lite1.0 verification

Feature source: `ff3f7683d16276a134183d4d2812354d89ede52f` on `preview/home2-nova-store`. Main application version0.9-preview/code11, API37 only. Package `uk.co.traynor.novaos.preview`; stable development certificate `81cda5b72cb76940fa50972bc1c284b25e641b88814d2b9041c7111cb1d042d8`.

Local verification: app and instrumentation APK assembly passed;173 Java policy assertions,13 Python tests and90 catalogue/icon/package/source-boundary checks passed. Local APK certificate, API/package, permissions, ZIP/ARM64 alignment passed. CI APK bytes are verified independently; local build checksum is not substituted for release checksum.

## Contract and privacy

The bundled adapter checks exact HTTPS WhatsApp Web origin, `#side`/`#pane-side`, a unique non-conversation English All/Unread/Favourites group and explicit consistent ARIA selected states. Native state is closed to LOGIN_REQUIRED, CHECKING, FAVOURITES_ACTIVE, FILTER_UNAVAILABLE or COMPATIBILITY_ERROR. A click alone never authorises display. The native view is hidden before checking/navigation, and a document-local cover conceals observed selection/structure changes before debounced revalidation. Missing/ambiguous/conflicting controls stay covered. No fixed delay proves readiness; timeouts report failure.

Reload/navigation/resume/configuration invalidates old epochs and verifies current document state. The retained session is reused; no cookie/storage reset or auth/protocol access. Already-selected Favourites receives no extra click. Explicit confirmed Normal WhatsApp disconnects the adapter; launching Nova Chat enables it again. Only the active preview profile can run interactions. Background audio lifecycle is unchanged.

No messages, contact identifiers, account tokens, cookies, raw DOM or screenshots are collected. The existing Inspector remains optional and explicit; no report is uploaded. Updates are bundled signed APK code, not remotely downloaded selectors/JavaScript. No unrestricted bridge or console.

**Native Favourites is a convenience filter, not isolation.** Search, notifications, already-open conversations, filter switching and other WhatsApp navigation can expose other chats. No absolute concealment, encrypted filtering or stable contact allowlist claim. Current live WhatsApp semantics are not accessible in development; synthetic passes do not establish owner-account compatibility. English supported markup only; unknown structures fail closed.

## Focused Android17 evidence

Run42099217228 passed on the first run: **21/21 focused cases plus code10→11 upgrade1/1**. Build, certificate/package checks, runtime assertions and raw prerelease publication succeeded. See receipts/0.9/appmode-tests.txt and upgrade-tests.txt; immutable artifact13449666681 has ZIP SHA256 `4b7f7581600063c02d67bc1b9a0c984a0b84b0ef1dcefb48b0ffb675a2d52aca`.

Eleven new synthetic cases cover verified activation/badge ignorance/no private API or account actions; delayed interface; reload/sessionStorage/same-view retention; changed selection cover and duplicate group failure; contradictory selected state; timeout/retry; login detection; confirmed normal exit; Home/app/recreation/cover/inner/landscape retention; concurrent selected filters; inactive profile. Existing selected regressions cover advancing audio and close lifecycle, Browser V1, OAuth restrictions, local Inspector, secure transitions, protected clock and genuine native MediaSession controls. Upgrade verifies code10→11 preservation using the pinned0.8 development-signed APK and synthetic persisted data.

Cover904x2316, inner1840x2208 and landscape2316x904 are emulator viewport tests. Real hinge posture, Samsung keyboard and live linked-device session/Favourites remain owner acceptance. Android Home background audio remains deferred/unverified.

A legacy generic web-app settings warning still describes the older unsupported Friends allowlist. Dedicated Nova Chat title/status describes native Favourites; stronger allowlist isolation remains unimplemented.

Production source/vault/keys/Primary Browser/Hidden VPN/Tor untouched. No production merge or security approval. See CHAT-LITE-1.0-OWNER-GUIDE.md and PRODUCTION-INTEGRATION-HANDOVER.md.

## Published APK

Tag/source: `ff3f7683d16276a134183d4d2812354d89ede52f`. Public prerelease: https://github.com/traynor1987/Private-gallery-/releases/tag/novaos-home2-store-preview-0.9 . Direct APK: https://github.com/traynor1987/Private-gallery-/releases/download/novaos-home2-store-preview-0.9/NovaOS-Home2-Store-Preview.apk .

APK SHA256: `523bd764ba861798a28f59c1a41e6b088fe6cdbe060faabae533579a836652ad`;61,192,542bytes. Independently downloaded public bytes, checksum, package/API, stable certificate, permissions and16KB ZIP/ARM64 ELF alignment verified. Source and tag match; no publication recovery/rebuild was required.
