# Private Gallery 1.7.8 owner acceptance

This document describes implemented behaviour and physical checks. It does not certify a candidate, signing, upgrade or owner acceptance. Exact release evidence is recorded separately.

## Private Apps

Open Private Apps from the authenticated browser. Add, discover, name, rename, edit, remove, pin and reorder saved entries. Open entries in the existing authorised browser; use Home, Back, Forward, Refresh/Stop, app settings, optional address bar, the library and the open-app switcher. Website icons fall back to a letter tile. Custom icons can be replaced or cleared; normalized icons remain in encrypted catalogue records.

Primary custom icons use explicit photo selection, subject to the existing background timeout. Hidden custom icons use the authenticated in-app Hidden image picker. If backgrounding locks Primary during photo selection, unlock and retry; no timeout exception is granted.

## Web app compatibility

Discovery supports public same-origin manifest name/short name, start URL, scope, display metadata and bounded raster icons, with favicon fallback. Compatible metadata offers Private Web App; unsupported metadata offers Private Website. Saved apps remain inside Private Gallery. No public Android installation or launcher shortcut is created.

Display preferences support app-style chrome; manifest scope is shown, not enforced as a new security sandbox. Public metadata discovery sends no site login cookies. Redirected, cross-origin, login-protected or unsupported resources may fall back to manual website mode. There is no background push, offline guarantee, Chrome installation API or service-worker installation promise. Hidden Tor keeps JavaScript disabled; JavaScript-dependent services may not work.

## Login and storage boundaries

Primary apps and ordinary Primary browser tabs share the existing Primary WebView profile. Keep signed in and clear session/data apply to that entire profile, with explicit confirmation. They are not per-app cookie isolation. Complete profile clearing requires a compatible WebView and prevents fresh renderer entry until clearing completes.

Hidden VPN and Hidden Tor use the existing independently authenticated transport/process boundaries. Tabs within an active Hidden browser share its ephemeral profile. Closing or revoking that browser clears its logins; persistent Hidden login is unsupported. Primary and Hidden encrypted catalogues are separate. Require PIN protects opening a saved entry, not direct navigation to the same website. Reset removes saved presentation choices; removal deletes the encrypted definition. Neither silently deletes shared cookies or downloaded vault files; use the explicitly scoped shared-profile clear control when needed.

## Transfers and screenshots

Supported downloads use the existing encrypted destination and owner authority. Generic document downloads support complete GET responses with progress, cancellation and explicit retry after interruption, not automatic range resumption or replay of POST/blob requests. Primary generic downloads require an owned Private Gallery VPN; no direct fallback is offered. Supported inert documents are PDF (32 MiB maximum), UTF-8 text and CSV (2 MiB maximum). PDF preview is bounded and in memory; text/CSV is displayed literally. Unsupported or protected documents may not preview. Downloads never automatically export to public storage.

Uploads use Choose from Vault, MIME filtering, explicit selection and confirmation. Hidden uses its authenticated revocable descriptor path. Navigation or switching cancels pending upload selection. Session revocation closes Hidden grants/readers; data already uploaded to a website cannot be recalled.

Screenshot to Vault requires confirmation and successful Android PixelCopy. It refuses secure windows, protected/embedded/video content, JavaScript-disabled pages and stale/revoked ownership. It does not clear FLAG_SECURE, use a drawing bypass, enable Tor JavaScript or defeat DRM. A refusal is expected where the existing privacy policy protects the window.

## Samsung Galaxy Z Fold physical checklist

1. Install the verified signed APK over 1.7.7 without uninstalling or clearing data. Confirm independent PINs, existing Primary/Hidden media, Recently Deleted, collections, favourites and settings.
2. On cover and inner screens, in portrait and landscape, repeat library add/edit/pin/reorder/remove and app switching in light/dark theme and large text; check keyboard/dialog scrolling and fold transitions.
3. Repeat supported journeys in Primary, Hidden VPN and Hidden Tor. Confirm visible mode, Tor JavaScript restrictions and no connectivity after transport loss.
4. Test the reopening PIN, profile-wide retention/clear confirmation, lock/timeout, Hide Content, Panic Exit and protected Recents preview. Confirm Hidden is inaccessible without its separate PIN.
5. Save synthetic image/video/PDF/text fixtures, cancel a transfer, lock during transfer and retry. Upload only an explicitly selected file from the correct vault. Verify screenshot refusal/capture matches the visible policy.
6. Restart the app and device; confirm catalogue/icons and chosen supported retention persist, Hidden sessions clear, and old encrypted data remains readable.

Physical owner acceptance is pending until the owner performs these checks.
