# Private Gallery 1.7.3 — smart unlock and sorting design

Owner requested autonomous design/implementation; routine choices below are implementer decisions, not a new approval gate.

Baseline a478b447876640b730c494c6e31e2384f096c786, owner-installed1.7.2/code35, package uk.co.traynor.privategallery, original certificate94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17. Target1.7.3/code36. Prior1.7.2 physical installation confirmed, complete physical acceptance not inferred.

## Approaches and decision
Reuse Compose and existing cryptographic authentication/repositories. A stock system credential screen cannot authenticate independent app PINs; replacing credentials or encrypting indexes differently risks compatibility. Implement an original Samsung-inspired keypad over existing callbacks and retain Android BiometricPrompt/CryptoObject for Primary only.

Use MediaStore SQL paging for phone sorting, and authenticated vault metadata for vault sorting. Preserve original encrypted index formats and payloads. Where existing index timestamps conflate capture/import, add a small domain-separated encrypted supplemental timeline rather than rewriting the index schema. No media reindex or EXIF mutation.

## Authentication
Neutral dark full-screen numeric keypad: lock, Enter passcode, dots, 0–9, backspace, haptics, accessible controls and Fold responsive placement. No keyboard by default; no plaintext digits in indicator/logs/saved state. Clear input on failure/background/disposal, serialize attempts and retain existing rejection/lockout logic. Configured known fixed lengths may auto-submit; existing envelopes support variable lengths and do not store the length, so those retain explicit confirmation. Never infer credential length by trying prefixes or silently change existing PINs. Existing UI credentials are numeric; cryptographic compatibility remains unchanged.

Primary auto-biometric path already exists: preserve official BiometricPrompt with authenticated cipher/key unwrap, cancellation generation checks and once-per-lock-entry behavior. Provide discreet PIN fallback/retry without repetitive prompts. A deliberate discreet Hidden route must cancel/invalidate pending Primary biometric callbacks before accepting input. Hidden has its own PIN only, no biometric enrollment/fallback or Primary-key reuse. Neutral entry never labels Hidden; long-press existing discreet title route remains. Lock/restart/recreation clear transient Hidden entry/input; next entry neutral Primary. Error copy uniform; no automatic probing of both credentials.

## Sorting and timestamps
Six orders: Date Added newest/oldest, Date Taken newest/oldest, Filename A–Z/Z–A. Default Date Added newest. Stable deterministic ID tie-breakers; missing/invalid capture timestamps fall back to date added, then ID; no guessed EXIF timezone. MediaStore DATE_ADDED seconds normalized to milliseconds; DATE_TAKEN milliseconds preserved. SQL CASE fallback and allowlisted order expressions; existing scoped permission selection stays intact.

Vault date added means destination publication time, distinct from capture date. New imports/copy/move receive a fresh destination addition time while preserving valid capture metadata. Existing items retain legacy timestamps as documented fallback where true addition/capture cannot be recovered without scanning media. Supplemental metadata uses existing vault encryption primitives and separate Primary/Hidden roots/AAD; no public caches. Lazy metadata reads only, no background full-payload scan. Persist sorting independently by context; Hidden preference protected by its authenticated encrypted store. Collections/favourites and selections retain behavior; compact toolbar sort menu replaces oversized/permanent controls.

## Verification and release
Focused pure sorting/keypad policies and Android UI tests; real cryptographic PIN/bio boundary, cancellation/recreation/screen-off/process cases; MediaStore query order/permissions; import/copy/move dates; missing/duplicate timestamps and independent preference persistence. Fresh full JVM/UI/security/browser/camera/AI regressions and original-key production-package35→36 upgrade/restart. Independent authentication review mandatory. No owner data, key replacement, uninstall/clear-data, main merge, V2,1.8 face/visual-index work. Preserve blue icon and all1.7.2 features; VPN+Tor unavailable.
