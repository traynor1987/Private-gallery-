# NovaOS 2.0 Wave A acceptance

Wave A contains the shared foundation, Themes Studio and Control Centre 2.0. The draft integration branch is `feature/novaos2-wave-a`. Its merge parents identify the exact Themes and Control Centre revisions, and the draft PR links evidence for the integration tree. Build 98 and the later NovaOS 2.0 waves retain their independent branches.

## Recovered baseline

| Source | Recovered evidence |
| --- | --- |
| Production Build 97, 1.7.51 | [Stable release](https://github.com/traynor1987/Private-gallery-/releases/tag/v1.7.51), source `6b7f1bbe40db3f213ff087833f37e05954e1c792` |
| Production main at recovery | `b19610a7efb3df53edcec2610402c69ea8e1199c`, a Build 98 rehearsal authorization commit |
| Shared foundation | [PR #78](https://github.com/traynor1987/Private-gallery-/pull/78), `cf38aec03c4de71e6c455d56e4b529a42654ec53`, [786 JVM and three API 37 cases](https://github.com/traynor1987/Private-gallery-/actions/runs/45902255953) |
| Themes Studio before Wave A finishing changes | [PR #85](https://github.com/traynor1987/Private-gallery-/pull/85), `b15ceba6690e40575620a865b6195ab56862ef60`, [791 JVM and five API 37 cases](https://github.com/traynor1987/Private-gallery-/actions/runs/45903913840) |
| Control Centre before Wave A finishing changes | [PR #80](https://github.com/traynor1987/Private-gallery-/pull/80), `6ef18e0869d6b713b3ca07d9ba995198601ef125`, [790 JVM and seven API 37 cases](https://github.com/traynor1987/Private-gallery-/actions/runs/45903923425) |
| Earlier all-system integration | [API 37 run and archive](https://github.com/traynor1987/Private-gallery-/actions/runs/45905240278/artifacts/15157379361), `e542aee94812935c3700a0e6310cbfac58471b7e`. The archive contains 818 passing JVM tests, 26 passing Android tests, 17 distinct screenshots, a launcher process restart and both Guardian notification checkpoints. The [security report](https://github.com/traynor1987/Private-gallery-/pull/78#issuecomment-7204757271) covers that historical tree. |

## Wave A inventory

| Surface | Behaviour to verify |
| --- | --- |
| NovaOS Original | Graphite and navy, geometric Home clock, Nova icons, translucent dock and original wallpaper presets |
| One UI-inspired | Rounded masks and rounded clock surface, wider spacing, larger widgets and a comfortably spaced Home grid |
| iOS-inspired | Rounded-square icons, floating dock, centered date and time, folder member previews and selectable widget stacks |
| Themes Studio | Live cover and inner previews, two-pane editor on wide windows, wallpaper presets, accent and icon styling, folders and dock, light/dark scheduling, apply and restore, offline preset import/export |
| Persistent layout | Appearance writes preserve the existing home pages, app placements, folder membership, widgets, dock and search/hidden app settings |
| Control Centre | Header swipe and shortcut, expand and collapse, persistent quick-action arrangement, media, brightness, volume, battery, connectivity, flashlight, rotation, Sanctuary, Themes Studio, privacy and notification categories |
| Android-owned controls | Connectivity and volume use Android system panels. Rotation opens Android Display settings. Brightness is limited to the active NovaOS window. Privacy opens Android's settings in Wave A. Working Mode is offered by the registry only when its module is available. |
| Protected notifications | Control Centre reuses the existing safe Guardian summary. It does not register a notification listener or drive Guardian cancellation. |

## Verification on API 37

Run the dedicated Wave A workflow for the exact branch head. The workflow runs JVM tests, API 37 foundation and feature suites, existing Hidden and Sanctuary boundary tests, existing updater tests, a two-process launcher restart and both existing Guardian notification fixtures. Record run IDs and inspect the actual Home and Control Centre screenshots for both window widths, including light system-bar contrast. The emulator performance measurements are debug window and theme action samples. Physical responsiveness, battery and Samsung behaviours require the Fold.

## Galaxy Z Fold acceptance checklist

Once the owner approves the protected release process and the verified production update is available, install through **Settings → Updates & About → Check for updates**.

1. Start on the cover display with several placed apps, a folder, a widget and a dock shortcut. Open Themes Studio, preview all three themes, choose a wallpaper and accent, apply each theme and restore the default. Check that home pages, folder contents, widget and dock placement and the App Library behave as before.
2. Open Control Centre by swiping down on Nova Home and by using its shortcut. Collapse and expand it, reorder quick actions, and test media, brightness, volume, connectivity, flashlight and rotation. Try tapping the relevant action with its permission withheld. Check battery and privacy status, the Themes Studio route and Sanctuary interaction.
3. Open the Fold and confirm that the Home layout uses the available width, Studio displays the preview next to its settings, and the expanded Control Centre and widgets remain readable. Check notice text and contrast in both light and dark appearance.
4. Close, reopen and rotate the Fold with a theme applied and with Control Centre open. Return from another app and restart the launcher. Check for lost placements, duplicated panels, changed appearance or clipped controls.
5. Check both existing Guardian replacement modes with synthetic safe and held messages. Verify each event appears exactly once where expected, held content stays protected and the existing Main/Friends separation still works. Verify Sanctuary restrictions and confirm existing protected Primary and Hidden contents are intact.
6. Capture cover and inner Home screenshots for each theme and the collapsed and expanded Control Centre using synthetic notification content. Record launcher, theme and panel responsiveness on the device. Confirm the updater reports the installed release and preserves the old data.

## Protected release preparation

Wave A targets `2.0.0`, versionCode `99`. Build 97 is the published upgrade baseline. PR #77 retains its Build 98 history. A future Nova Intelligence release must use both a versionCode above 99 and a semantic version above `2.0.0` for the production updater to offer it.

The stable Build 97 APK SHA-256 is `75760970c6dcc00f560cbb5493b67a6bbc190397948dabb32e50e4753e12c988` and the original signing certificate SHA-256 is `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`.

Under the [repository release policy](../release-policy/POLICY.md), the 2.0 release requires the full Android 17 and security lane, including cryptography, authentication, isolation, network and media tests, and independent review. Any carry-forward needs a separately reviewed exception with exact prior evidence. The focused Wave A development run is an input to that review. Repeat exact-source CI after the final version and corresponding strict scope validation are pinned.

Wave A needs separate owner authorization for production integration and the protected original-key rehearsal in `.github/workflows/sanctuary-production.yml`. Its current Build 98 verifier pins a different tree, identity and prior stable release. Prepare and independently review the Wave A identity and provenance settings before invoking it. Bind the exact release tree, successful API 37 workflow and verified stable APK to the owner-authenticated rehearsal marker. Use the protected receipt-only pre-merge rehearsal to verify the candidate upgrade. Pin the authentic starting APK explicitly. The old updater rehearsal infers the previous versionCode by subtraction. Rehearse a populated Build 97 → 99 in-place and updater upgrade, and also Build 98 → 99 if Build 98 ships first. Verify the package, API levels, original certificate, Primary and Hidden encrypted data, Guardian, persisted launcher layouts and recovery across process restarts. Preserve the existing artifact checks for a single signer, 16 KiB alignment, supported ABI distribution and backup exclusions. Retain the exact-source rehearsal receipts. The rehearsal deletes its temporary APK and key. After approved integration, the production signing run retains the signed artifact and evidence.

After those gates pass, production publication has its own owner-authorized marker for `.github/workflows/novaos-publish-verified.yml`. The publisher uses the named `novaos-production-publication` environment. Its Wave A identity and retained receipts must be reviewed for the exact release source. The existing updater already accepts numeric version tags such as `v2.0.0` and verifies the official APK, metadata and checksum. Live stable updater discovery and physical Fold acceptance follow approved publication.
