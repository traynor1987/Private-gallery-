# Preview0.6.1 verification receipt

Package:uk.co.traynor.novaos.preview. Android17/API37 only. Version0.6.1-preview/code7. Development certificate SHA256:81cda5b72cb76940fa50972bc1c284b25e641b88814d2b9041c7111cb1d042d8. Production unchanged; no production merge.

## Evidence

- Host:101 Java assertions (URL/layout, App Mode/OAuth, shell, Mind, widgets/stack/PiP, backup/privacy, rollback, foreground continuity),10 Python tests,90-entry catalogue/icon/package checks.
- App and instrumentation APK assembly passed locally. Stable certificate, distinct package, Internet-only ordinary permission, production-class exclusion and16KB alignment verified.
- Run42011342852 at74048a6b: pinned code5→6 upgrade passed including existing Home and synthetic journal fixtures;26/28 experience tests passed. All14 existing Browser/App Mode/Recents/Mind/Fold cases passed. All three media continuity cases passed. Two new widget/fullscreen UI assertions prevented publication.
- Code6 narrowed run42013043832 at e8f7f9d220a7a7d657a6bc390a72af7a43c6175c:14/14 widget/media cases and dedicated upgrade passed; unchanged earlier14 cases are retained in source and were not rerun. Code6 published bytes independently verified; screenshot review found poor inherited widget text contrast, so the delivery patch is code7.
- Code7: explicit theme foreground, compact Home header/footer adjustment, rendered contrast assertion, and pinned code6→7 widget/stack/note upgrade fixture. Final runtime/publication pending.

## Audio continuity

| Situation | Automated evidence | Physical YouTube Music |
|---|---|---|
| Nova Home | Synthetic user-started playback time advances, document retained | Owner acceptance of this APK pending |
| Another installed app | Same original WebView, audio time advances | Pending |
| Browser V1 | Same original WebView, audio time advances | Pending |
| Rotation | Same WebView/document and advancing audio | Pending |
| Fold/unfold | Two viewport sizes plus forced Activity recreation preserve document/audio | Genuine hardware posture transition pending |
| Android Home/outside NovaOS | Background service/audio support deferred; not claimed | Unverified |

The synthetic page deliberately has no MediaSession playing state. Tests use an actual touch to start bundled/generated media; they do not exercise third-party accounts, alter provider pages or bypass restrictions. Actual stop/profile lifecycle assertions verify pause eligibility and retained ownership, not Android background playback. Explicit Close removes/destroys the source. WebView keeps its platform audio-focus handling; native sounds/video request focus. Multi-provider competition is not physically certified.

## Capabilities and limits

Six widget types: Clock & Date, Now Playing, Recent Apps, Nova Mind, Quick Notes, Nova Search. Search is local metadata only. Same-size stacks persist order/config/active index. Layout-only plain JSON backup validates before mutation, previews/confirms/cancels, and recovers/rolls back on failure; no credentials, cookies, mood/journal/note text, search history, production keys or media bytes are exported. Demonstration lock conceals app UI but is not authentication/encryption.

Native calm sounds expose their own real MediaSession controls/state/progress. Native local video offers controls/seek, mini-player and eligible opt-in Android PiP. Website information depends on public Media Session title/state; no fabricated artwork/progress/transport. Unknown provider mini-player/PiP is unavailable. Physical PiP and genuine Fold transitions require owner acceptance.

Store preserved:90 total entries;40 Games listings (35 Web Apps,4 Website Shortcuts,1 explicitly Unsupported),0 Verified PWA claims. Website availability/provider policies can change; no offline promise.

## Owner acceptance

1. Update in place; do not uninstall/clear data. Confirm old wallpaper/pages/Dock/folders/apps/journal remain.
2. Start YouTube Music, note page/position, then Home→another installed app→Home→Browser V1→return to Music. Start a different intended audio source and confirm appropriate focus handoff.
3. Rotate and fold/unfold while playing. Confirm no restart or position loss; Close App must stop/release that source. Android Home is a separate unverified check.
4. Add/move/resize widgets, create/swipe/reorder a compatible stack, restart NovaOS and confirm persistence.
5. Search Breathing/Browser/2048; export layout, preview/cancel restore, then confirm restore. Check demo lock entry/exit and native local video mini/expand/PiP/close.

Production integration requirements and source components are in PRODUCTION-INTEGRATION-HANDOVER.md. Real encrypted Space storage/authentication, Primary/Hidden isolation, secure Recents, original production signing and encrypted-data upgrades require separate authorization/review. Hidden VPN DNS/UDP teardown blocker and Tor are excluded.
