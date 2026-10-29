# Private Gallery 1.7.6.2 code45 — owner acceptance candidate

The signed candidate is ready for physical acceptance. Physical Fold/Samsung acceptance has NOT been performed by automation.

## Verified release identity

- Version:1.7.6.2; code45; Android17/API37 only.
- Package:`uk.co.traynor.privategallery`.
- Frozen application source:`97e94452ceb9c5262521d7552f0c25eeb07374eb`.
- Release control:`a19d19603c8ccbfaa1449113ba61592f5249c19e`.
- Production workflow:[40852039088](https://github.com/traynor1987/Private-gallery-/actions/runs/40852039088), job123373658697:SUCCESS.
- Artifact12887938200; ZIP SHA256:`45926763f057854527984387a8a21e2e9c32220bc86dde934220107923171b31`.
- Delivered APK:`Private_Gallery_1.7.6.2_code45_ACCEPTANCE.apk`;104897070 bytes.
- APK SHA256:`ad39f9b3a4d04d75621eeb21969f131fd96deae0620a5e34325a9bf400359aff`.
- Original certificate SHA256:`94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`.
- APKv2 signature verification, production package/version identity, downloaded ZIP/APK checksums:PASS.
- Fresh original-key44→45 in-place upgrade, restart, independent credentials and encrypted data preservation:PASS. No uninstall/data clearing after seeding.
- Historical41/42/43→44 evidence retained. Direct41/42/43→45 was not freshly executed; unchanged storage/migration code was reviewed. No key migration or replacement signing key.

## Repairs and scope

| Report | Candidate behavior and evidence |
| --- | --- |
| Primary appears empty from Hidden | Primary queries now admit the independently authenticated Primary Gallery context; key identity and navigation generation reject stale results. Valid Primary authentication is reused; Hidden retains its independent session and timeout. Actual seeded encrypted MainActivity query/navigation/lock/ciphertext-preservation test passes. |
| Hidden transport selector missing | Native browser settings exposes VPN Only/Tor Only with active mode and explicit close-tabs confirmation. Unsupported VPN+Tor remains unavailable. Actual native switching proves retirement, fresh profile/cookie isolation and independent authentication. |
| Blank white Hidden browser | Empty tabs show start guidance; loading/HTTP/TLS/transport errors are visible, with a bounded stalled-load error. VPN JavaScript/forms/POST/uploads/redirects and Tor JavaScript-off/sink behavior pass controlled runtime and packet tests. No direct-network workaround. |
| Transfer missing from upload picker | Actual encrypted Hidden→Primary copy/move, empty-picker priming, reopening and eligible selection pass. Transfer records preserve ownership/hash/metadata and privacy flags. No erroneous metadata rewrite was justified. Vault-only restricted items remain excluded with explicit counts/reasons; no unsafe export bypass was added. |
| Samsung external editing | Explicit validated Samsung editor target; consented guided public Gallery export when unsupported or no result returns; selected-result reimport, encrypted verification, Save as New and explicit Replace confirmation retained. Samsung AI/Object Eraser availability still requires physical acceptance. |
| External modal appearance | Selected-app theme, rounded shell, spaced full-width actions,48dp minimum controls and bounded scrolling; dark/large-font landscape and consent/lifecycle checks pass. |

## Evidence attribution

Run40846531662/sourcef7c69a54f586f8cc370b247f0086b20842bc3341 had114/117PASS with3 failures and0skips. Unchanged transfer/picker/layout/authentication passes are attributed to that source, not represented as newly executed on final source.

Run40848723808/source97e94452ceb9c5262521d7552f0c25eeb07374eb had all13 selected Android cases PASS. It retained an overall FAILED result because the packet parser rejected host ADB mDNS question ordering. The two rejected packets were host-loopback TTL1 exact ADB discovery, not browser origin traffic. A strict exact-question multiset correction retains interface/TTL/address/port/type/count constraints and the WireGuard-only assertion. Regression was observed RED then GREEN; malformed/direct traffic remains rejected.

Immutable artifact12887270723 SHA256b01b11d5ba2bbfeadfa097957b1ca5b7ab2313c502c1a622617a1abf7457a2fc was revalidated locally, independently reviewed, then revalidated again before signer access in the successful release workflow. All13 exact XML cases and VPN/Tor body/packet/revocation proofs pass. Original failure and exit1 remain recorded separately from evidence_validation PASS. Corrected validator SHA256a9359061347d0dd79bf527fe724dcbceb4e7f998332e19570c922f33eb33e3c0.

Consolidated independent review:APPROVED_FOR_SIGNING,0Critical/0Important, exact source97e9445. Review digestcb63e704490e293b6a5946e4fd175ccbc6bad2a405243a2a027d9bbbbdab334d. No new workflow and no repeat Android suite for the parser-only correction.

## Physical owner checklist

1. Install over code44 without uninstalling. Confirm existing Primary/Hidden media and independent PINs remain available.
2. Enter Hidden → Gallery → Primary. Confirm real media, copy/move in both directions, return to Hidden and check configured lock timeout. Primary biometrics must not unlock a locked Hidden vault.
3. Explicitly return to Primary, open its browser, and confirm an eligible transferred photo appears in Choose from Vault. Vault-only items must still be explained and excluded.
4. In Hidden Browser settings, switch VPN Only→Tor Only→VPN Only. Confirm active mode, closed old tabs and meaningful connection/error feedback. Use harmless pages to check VPN forms/uploads; Tor JavaScript remains disabled.
5. Select a nonrestricted encrypted photo → Edit with Samsung Gallery. If direct editing is unavailable, confirm temporary public export, edit/save in Samsung Gallery, return, Import edited result, preview, Save as New. Confirm the original remains and edited result opens after relocking/reunlocking its originating vault. Test Replace only with a disposable photo and explicit confirmation.
6. Check Object Eraser/available Samsung AI tools on the Fold itself. Confirm public-copy cleanup separately; this app cannot guarantee deletion of Samsung caches/cloud copies.
7. Check the editor modal on cover/inner screens, light/dark themes and large text: scrolling, separated actions and Cancel must remain usable.
