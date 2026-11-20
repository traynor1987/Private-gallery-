# NovaOS 1.7.47 (Build 93) release review

Source: 39e29649ce961b251a9783e09731814f6e0ab30c
Authorization: authenticated owner push
Release scope: public stable after disposable upgrade verification
Physical Fold acceptance: pending
Upgrade fixture SHA-256: 69757813f2733c13ebab99c452b4d9065faee733d89ec8f55937437d8d4a3753
Upgrade controls SHA-256: 35e3e2dae5e3ed2cfee0d8d508a3ae66da3e0b489cb6602f4b16017ff2e9e10b
Runtime matrix SHA-256: b3a221684edbd0e3462614ff8dbb402fb74d257823a6e9d5b7ea658ebf6242fa
Runtime workflow run: 44726268958
Runtime branch: feature/novaos-guardian-presentation93
Signing workflow SHA-256: 0c1ca8f81b956320dab524a8640fff44d819dd896b26b3332919e8ea683fbd16

Build 93 recognises the reported hostile command even when the message ends
with incomplete context. The Android listener captures and encrypts the
supported source payload before requesting cancellation. Guardian posts the
replacement after Android confirms removal of the original. The listener
excludes notification posts it observed as summaries when Android regroups
them and redispatches the same post.

Smart Filtering keeps the original low-risk WhatsApp alert and replaces
held originals with neutral Guardian alerts when delivery is enabled.
Nova Guardian Notifications also replaces eligible low-risk originals when
it can deliver the new alert. Eligible safe previews require local validation
and opt-in. Held text stays behind review. Android can show a source alert
before its listener callback reaches Guardian.

The Android 17 run passed 344 regression cases, 64 focused
report groups and 776 JVM tests. A synthetic source APK on the
disposable emulator exercised the system listener with both modes, exact
cancellation, duplicate delivery, encrypted recovery, contact exclusions,
source summaries, unavailable alerts, and safe and held messages with the
screen off. The Inbox tests verified Android credential confirmation and
protected review. No real WhatsApp account or owner data was used. Physical
Samsung notification timing and delivery, including Dual Messenger, require
owner verification.

The production workflow must sign with the original key and verify an
in-place upgrade from populated Build 91 data before stable publication.
The existing updater workflow must then verify discovery, download and
installation from the public feed, restart, and the Up to date screen.
