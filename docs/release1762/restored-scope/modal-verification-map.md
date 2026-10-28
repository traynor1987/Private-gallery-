# Modal verification map — restored scope

Source reconciliation on 28 October 2026, current scan `da50796a2eaf3ee7b2549774c2378436014a3feb`. This preserves **all 93 original IDs** and original inventory source references in their original order; those stored line numbers are historical identifiers, not current anchors. Current constructor anchors and relocation relationships are in [current-modal-reconciliation.md](current-modal-reconciliation.md). Added parity/recovery variants R94–R97 remain separate. Owner-directed minimal CI now supersedes automatic broad reruns: reuse reviewed unchanged passing evidence and reverify demonstrated defects or necessary security boundaries only.

**D** means an actual component/path has an identified test definition; **R** means related policy/behavior only; **U** marks an unestablished direct test or a specified untested state/matrix. These letters are not runtime results. Latest inspected `852ba56` XML has **152 cases, 148 pass, four fail, zero skipped**; exact evidence and per-class boundaries follow below. It proves selected automated Android 17 component cases, not every original row/state or a later source SHA. Physical Fold transitions, contrary OS-theme/system-maximum-font matrices, Samsung keyboard/editor and TalkBack remain unproved. Historical pending/WIP wording is reconciled below rather than used to erase passing component evidence or native failures.

## Latest inspected execution:852ba56

Artifact `/tmp/focused-852ba56`, verified supplied ZIP SHA-256 `3c93bfe13f51493b63066661ee3c5266c36661f4b8bcdaa231a59842e10e9c51`; exact XML: `outputs/androidTest-results/connected/debug/TEST-focused1762(AVD) - 17-_app-.xml`. Parsed testcase elements, including failures/skips rather than relying on a green class summary. Totals are152/148pass/4fail/0skip. This section is the authoritative executed-status reconciliation for older “pending” statements; D/U below continue to describe definition/state coverage.

| Mapped surface / relevant suite | Actual852 XML result | Limit |
|---|---|---|
| T32 ModalCompletion1762Test |13/13 pass | Includes actual OS/Dialog font-scale probe, opaque last299 thumbnail pixels, grid extent, real IME, storage/history/Create Image/date-time cases; only declared viewports/states |
| T33 RemainingMediaModals1762Test |4/4 pass | Both menu palette captures and exact media-operation components; light/dark narrow/landscape OS2x |
| T35 HiddenNativeBrowserDialogs1762Test |22/22 pass | Actual shared settings/tabs/bookmarks/history/find/download/error/auth/upload/website/resubmit bodies, controls and keyboard; no live protocol success inferred |
| T36 BrowserModalSurfaces1762Test |10/10 pass | Actual Primary modal components, real system Back into focused Dialog, explicit consent/cancel; not Android permission grant/OEM appearance |
| T15 + R94 ExternalEditingUi176Test |6/6 pass | Includes both actual Dialog OS2x recovery acknowledgement cases and stale preview cleanup; external editor app not exercised by these UI fixtures |
| T09 AiEditorProfessionalTest |6/6 pass | Actual AI editor/source replacement/preview/prompt/foreground-loss cases; no remote-provider acceptance inferred |
| T10 BrowserPolishTest; T11 BrowserProfessionalUiTest |6/6;4/4 pass | Shared panels/history clear and specified viewport cases; not every IME/theme combination |
| BrowserToolbarLayout1762Test |4/4 pass | Adaptive chrome at selected2x viewports; no physical hinge/fold claim |
| T04 BrowserConsent1752Test |8/8 pass | Actual chooser/script/consent paths; website-origin ownership remains separately asserted |
| BrowserPermissionOwnership1762Test; BrowserDeviceSelection1762Test; BrowserSystemPicker1762Test |18/18;3/3;1/1 pass | Owned picker/permission request identity, actual provider read grants and system Back cancellation; Android-owned picker appearance not an app modal |
| HiddenBrowserCapturePolicy1762Test |6/6 pass | Actual owned Dialog/IME/security/capture/selected-theme state; no flag clearing in pixel fixtures |
| HiddenNativePresentation1762Test |1/1 pass | Actual child parent-selected theme and Hidden capture policy without cross-process preference read |
| T21 HiddenLibraryParcelPressure1762Test |2/2 pass | Actual child Binder/maximal payload and authenticated ciphertext preservation; not proof all native network callbacks pass |
| HiddenVpnHandoff1762Test |9/9 pass | Owned generation/auth/foreground/download return states; no physical screen/fold/OEM claim |
| ExternalEditorRecovery1762Test; ExternalEditorRecoveryImport1762Test; ExternalEditingLifecycle176Test; ExternalPhotoExport1761Test |4/4;2/2;5/5;2/2 pass | Actual isolated recovery/import/lifecycle/egress fixtures; unrelated native protocol failures remain |
| T01/T02/T03, T07/T08, T12–T14, T16/T17, T19, T22–T31 and other absent catalog suites |Not selected in this152 XML | Preserve earlier exact-SHA evidence; review unchanged boundaries for reuse rather than declaring852 passes or automatically expanding CI |

The remaining selected native boundary/runtime/shutdown helpers pass (4+3+4 cases); HiddenUploadPickerPaging's metadata round-trip case passes. The four failures are exactly:

| Failed actual native case | Claim still blocked |
|---|---|
| `core.browser.hidden.HiddenUploadPickerPaging1762Test.actualTransferredLastItemBeyondFiveHundredSurvivesCancelReopenAndTypedPages` | Real transferred-item picker/cancel/reopen protocol; shared501-item production grid UI passes separately |
| `core.browser.hidden.NativeTorIsolation1762Test.packagedTorNativeFormsUploadsDownloadsAndNetworkSink` | Real native Tor forms/uploads/downloads/isolated sink |
| `core.browser.hidden.HiddenVpnLive1762Test.nativeChildPostsFormsUploadsAndRevokesOnOwnedVpn` | Real native WebView POST/upload/download/auth/resubmit/revocation sequence; a component-body pass cannot complete this sequence |
| `core.browser.hidden.HiddenVpnNetworkLoss1762Test.lostOwnedNetworkRetiresNativeChildAndCannotReuseOldCapability` | Actual owned-network-loss retirement/capability reuse |

Owner-reported run40781462622 on `da50796` completed those four: NetworkLoss passed; Paging, NativeTorIsolation and HiddenVpnLive failed. This audit directly inspected the852 XML, not a new da507 artifact; its current three failures remain under source repair. No automatic305/307 rerun is required by this map. Owner-reviewed existing native cases plus affected Main boundary cases provide targeted revalidation; final approval remains pending. This audit does not create CI, commit, push or an acceptance claim.

### Reviewed earlier Main14 evidence reuse

Parsed `/tmp/focused-d5c246` XML: all14 `MainModalInventory1762Test` methods pass, zero skips/failures. Compared d5c246 to current source: MainActivity source from `internal fun SecretAuthenticationDialog` through the extracted modal/collection/backup functions is byte-for-byte unchanged (tail SHA-256 `5f50a306c3aba9083e4cb5b53a656355782d41b5b6bc9395adedc5964628969f`); the MainModalInventory1762Test class, GalleryAlertDialog and BrowserViewportRule also have no diff. MainActivity changes are earlier owner/editor recovery paths; those boundaries are covered/reviewed separately and are not silently approved by component reuse. Thus T34 retains actual d5c24614/14 evidence, not an852 result. The owner permits this unchanged-component reuse rather than a broad rerun.

OriginalID60 is an implemented but genuinely unmapped Main handoff body, not a missing implementation. Native settings own clear-history alert at current HiddenVpnBrowserActivity:475 is likewise not directly mounted by T35's separate shared History panel confirmation. These remain explicit coverage limits; no new scenario expansion is mandated without an observed defect or necessary security reason.

## Shared shells and findings

`A` = GalleryAlertDialog alias: selected Material3 surfaceContainerHigh/onSurface/onSurfaceVariant, outline, shape and tonal elevation; default body scrolling and pinned actions. `G` = raw Compose Dialog containing GalleryDialogSurface with matching selected-theme decoration. `M` = Material3 menu/sheet inheriting the selected theme. `S` = custom Material3 Surface/media canvas. `N` was the original native date/time picker family; the completion stage replaces both app-owned native pickers with A. Android permission/biometric/document-provider UI remains system-owned. Every alert importer in this inventory routes to A, except the underlying Material3 constructor in A itself. No Android AlertDialog.Builder or PopupWindow family was found in production search; a constructor count alone is not sufficient acceptance.

1. **Retained legacy-only popup close-action contrast/insets:** `ui/PrivateBrowser.kt` temporary popup WebView overlay (outside the 93 constructor rows) still places a bare IconButton over website content with no opaque theme backing and no safeDrawing inset. This is a concrete source contrast/inset risk. The composable is actually named `BrowserHome`; a complete `rg` for its calls finds only its declaration and `BrowserHomeRenderTest.browserChromeAndFailureStateRemainVisibleWhenWebViewCannotStart`. MainActivity Route.BROWSER selects HiddenBrowserHome for Hidden and BrowserV2ProductionDestination otherwise. It has no entry in the current production route graph, so this retained legacy risk is not an active-route release defect. The harness tests chrome/failure only, not the popup.
2. **History control accessible naming — component cases executed, TalkBack pending:** parent changed HiddenBrowserHome to a labeled minimum-48dp toggleable Row with Role.Switch and a non-interactive inner Switch. Current native child adds an explicit history contentDescription and minimum-48dp Switch. The initially observed unlabeled sibling Switch/Text risk is therefore addressed in current source. The selected shared settings cases pass in T35 on852; actual TalkBack focus behavior and affected-boundary execution remain separately reviewed.
3. **Nested scroll boundaries:** External editor choices now use an ordinary Column inside A's default scroll body (no nested lazy viewport), and the dedicated chooser test targets this. Lazy collection/upload/source/history lists generally use scrollBody=false plus explicit bounded viewports; do not wrap their lazy body in another unbounded vertical scroll. Inspection did not establish a current remaining infinite-height lazy-scroll crash. This is source reasoning, not full runtime proof.
4. **Layout evidence and remaining matrices:** T32 now passes the actual source grid (opaque thumbnail pixel verification and final299 selection), Create Image reference/footer, transfer-history scrolling and actual IME prompt form. T35 passes shared native library/authentication/IME cases. These resolve the previously wholly unexecuted candidates at their specified viewports; enlarged-source extremes, retained legacy diagnostics and unselected system-maximum/contrary-theme states remain gaps. No whole viewport matrix follows from these selected cases.
5. **Decoration exceptions are explicit:** PhotoEditor enlarged-source Surface inherits selected Material3 colors but does not use G's outline/elevation. Enlarged result/video/fullscreen media intentionally use black media canvases with white controls. Hidden foundation/PrimaryCopy/Synthetic authentication use selected-theme fullscreen Surface. These are not evidence of unstyled white dialogs.

## Complete constructor mapping

Catalog references below expand to exact test class and method names. A family prefix such as T10 means its a/b/c catalog entries. Every U remains a release verification gap; shared-shell coverage cannot turn it into D.

| ID | Inventory source | Family / source styling | Direct test mapping and boundary |
|---|---|---|---|
| 01 | `MainActivity.kt:3233` | Album dropdown · M | D T34a actual production album selector, last of100 albums and selected label at cover/light/2x. |
| 02 | `MainActivity.kt:3436` | Encrypted backup restore · A | D T34b actual restore form: blank validation, real keyboard, busy disabled actions, authenticated-failure display and Cancel; no archive/credential callback invoked before explicit submission. |
| 03 | `MainActivity.kt:4008` | Recovery-key export verification · A | D T34c actual export form: empty-key rejection, masked entry/real keyboard and one explicit export boundary. |
| 04 | `MainActivity.kt:4028` | Third-party licences · A | D T34d actual long licence body reaches scroll maximum while Close stays displayed in short landscape/dark/2x. |
| 05 | `MainActivity.kt:4054` | Owner identity confirmation · A | D T34e actual owner form: masked real-keyboard entry, wrong PIN then correct PIN; biometric eligibility remains caller-owned. |
| 06 | `MainActivity.kt:4081` | Change PIN · A | D T34f actual Change PIN form validation, wrong-current callback error and Cancel at cover/light/2x; no credential store is mutated by this UI fixture. |
| 07 | `MainActivity.kt:4383` | Copy/move selection to Hidden confirmation · A | D T34g/h actual Copy and Move variants: source/destination explanation, Cancel before callback, explicit confirmation only; related encrypted integrity tests remain separate. |
| 08 | `MainActivity.kt:4394` | Transfer-to-Hidden progress · A | D T34i actual progress: body can scroll, safe-stop callback once, action disables while stopping and dialog stays present. |
| 09 | `MainActivity.kt:4401` | Bulk Recently Deleted confirmation · A | D T34j actual Recently Deleted variant: Cancel invokes no action, explicit Move invokes once with pinned choices. |
| 10 | `MainActivity.kt:4411` | Bulk Gallery restore confirmation · A | D T34k actual Gallery restore variant in short landscape/dark/2x: restriction explanation and explicit choice. |
| 11 | `MainActivity.kt:4421` | Permanent delete confirmation · A | D T34l actual permanent-delete variant: cancellation and distinct explicit confirmation, no storage mutations in fixture. |
| 12 | `MainActivity.kt:4454` | Collection cover picker · A | D T34m actual bounded cover list with current selection and last of300 items; actual production callback receives selected identity. |
| 13 | `MainActivity.kt:4614` | New collection · A | D T34n actual New collection field: blank rejection, real keyboard and explicit Create at cover/dark/2x. |
| 14 | `MainActivity.kt:4628` | Manage collection · A | D T32i: actual large-text management actions scroll; Cancel/Save stay pinned; delete requires separate confirmation. Rename/IME states still need broader coverage. |
| 15 | `MainActivity.kt:4643` | Add to collection picker · A | D T03b; list scrolling/selection in light theme. |
| 16 | `MainActivity.kt:4713` | Add Vault media picker · A | D T03a; 300-item list scrolling/selection in dark theme. |
| 17 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:238` | Hidden VPN settings · A | D T35 settings choices, save-history labels, desktop controls and long body scrolling in selected light/dark; live native Settings and library paths also covered. Component cases: see852 execution table; live protocol/final-source status remains separate. |
| 18 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:247` | Hidden VPN tabs · A | D T35 actual native tab body, bounded long list, selection and close; live tab creation/selected-close/address restoration added. Component cases: see852 execution table; live protocol/final-source status remains separate. |
| 19 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:248` | Hidden VPN bookmark/history library · A | D T21 (both libraries via actual child); maximal payload assertion, not full visual matrix; child Binder cases pass852; real native protocol still failing. |
| 20 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:249` | Hidden GET download confirmation · A | D T20 now includes explicit GET confirmation, encrypted import verification and decrypted fixture SHA-256; live protocol case fails852; its complete GET/import assertions are not accepted. |
| 21 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:250` | Hidden upload picker · A | D T20 real eligible/restricted transfer/upload/preview path; T35 renders the actual shared native grid at cover/dark and short-landscape/light2x with loading/disabled actions and item501 selection. Component cases: see852 execution table; live protocol/final-source status remains separate. |
| 22 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:264` | Hidden upload consent · A | D T20 explicit Allow upload path; no contrary-theme/large-text matrix. |
| 23 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:274` | Hidden website HTTP authentication · A | D T35 actual username/password fields, Password/Next/Done IME options and explicit choices in light/dark; live HTTPS Basic-auth fixture added. Component cases: see852 execution table; live protocol/final-source status remains separate. |
| 24 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:278` | Hidden form resubmission · A | D T35 actual resend warning and explicit Cancel/Resend; real native POST reload fixture added. Component cases: see852 execution table; live protocol/final-source status remains separate. |
| 25 | `core/browser/hidden/HiddenVpnBrowserActivity.kt:281` | Hidden website JS alert/confirm/prompt · A | D T35 actual website message/confirm/prompt body and input at2x; live iframe alert origin and explicit confirm-cancel/prompt-confirm callbacks added. Component cases: see852 execution table; live protocol/final-source status remains separate. |
| 26 | `ui/AiEditingSettings.kt:145` | Prompt template editor · G | D T02 narrow 320×640/2x footer and scrolling; T32h adds actual keyboard-window visibility and unsaved Cancel. Contrary OS-theme matrix U. |
| 27 | `ui/AiEditingSettings.kt:203` | Provider credential setup · G | D T07 provider entry/remove/failure/background; visual/IME matrix U. |
| 28 | `ui/AiModelPicker.kt:46` | AI model picker sheet · M | D T08 model/provider sheet interaction; complete theme/IME matrix U. |
| 29 | `ui/AiSourceCards.kt:93` | AI source-photo picker · A | D T09 source replacement and stale-load handling; T32b passes852 with real short landscape/OS2x lazy-grid final299 selection and opaque rendered-thumbnail pixel checks. Complete matrix remains U. |
| 30 | `ui/BrowserPanels.kt:45` | Browser full-screen panel shell · S (selected-theme background, safeDrawing/IME insets) | D T10/T11 panel/settings/library interactions; shell theme sourced; complete IME/focus matrix U. |
| 31 | `ui/BrowserPanels.kt:176` | Clear Primary history confirmation · A | D T10c explicit history-clear confirmation; large-text/error states U. |
| 32 | `ui/BrowserPanels.kt:244` | Clear Primary browsing-data confirmation · A | D T32f actual browsing-data confirmation at cover/2x; Cancel invokes no cleanup, explicit Clear invokes once. |
| 33 | `ui/BrowserV2Home.kt:499` | Browser internal media viewer · S | D T36 actual production modal component at cover/short landscape2x selected light/dark; explicit Cancel/confirm/back and long-content/permission/resource states. See browser-modal-completion.md for exact case mapping. Component cases: see852 execution table; live protocol/final-source status remains separate. |
| 34 | `ui/BrowserV2Home.kt:719` | Save browser video · A | D T36 actual production modal component at cover/short landscape2x selected light/dark; explicit Cancel/confirm/back and long-content/permission/resource states. See browser-modal-completion.md for exact case mapping. Component cases: see852 execution table; live protocol/final-source status remains separate. |
| 35 | `ui/BrowserV2Home.kt:812` | Upload source choice · A | D T04a upload choice/extension path; all theme/viewport states U. |
| 36 | `ui/BrowserV2Home.kt:821` | Primary Vault upload picker · G | D T04a/T04d/T12; actual picker/reopen/preview; 2x/landscape grid matrix U. |
| 37 | `ui/BrowserV2Home.kt:859` | Decrypted upload confirmation · A | D T04a explicit upload confirmation; cancel race T04b; visual matrix U. |
| 38 | `ui/BrowserV2Home.kt:889` | Find in page · A | D T36 actual production modal component at cover/short landscape2x selected light/dark; explicit Cancel/confirm/back and long-content/permission/resource states. See browser-modal-completion.md for exact case mapping. Component cases: see852 execution table; live protocol/final-source status remains separate. |
| 39 | `ui/BrowserV2Home.kt:896` | Primary browser diagnostics · A | D T36 actual production modal component at cover/short landscape2x selected light/dark; explicit Cancel/confirm/back and long-content/permission/resource states. See browser-modal-completion.md for exact case mapping. Component cases: see852 execution table; live protocol/final-source status remains separate. |
| 40 | `ui/BrowserV2Home.kt:929` | Website JS/leave-page dialog · A | D T04c/T04e; T05 related once-only/revocation policy. |
| 41 | `ui/BrowserV2Home.kt:942` | Browser message · A | D T36 actual production modal component at cover/short landscape2x selected light/dark; explicit Cancel/confirm/back and long-content/permission/resource states. See browser-modal-completion.md for exact case mapping. Component cases: see852 execution table; live protocol/final-source status remains separate. |
| 42 | `ui/BrowserV2Home.kt:943` | Website camera/microphone permission · A | D T36 actual production modal component at cover/short landscape2x selected light/dark; explicit Cancel/confirm/back and long-content/permission/resource states. See browser-modal-completion.md for exact case mapping. Component cases: see852 execution table; live protocol/final-source status remains separate. |
| 43 | `ui/BrowserV2Home.kt:961` | Website geolocation permission · A | D T36 actual production modal component at cover/short landscape2x selected light/dark; explicit Cancel/confirm/back and long-content/permission/resource states. See browser-modal-completion.md for exact case mapping. Component cases: see852 execution table; live protocol/final-source status remains separate. |
| 44 | `ui/BrowserV2Home.kt:972` | Save image resource · A | D T36 actual production modal component at cover/short landscape2x selected light/dark; explicit Cancel/confirm/back and long-content/permission/resource states. See browser-modal-completion.md for exact case mapping. Component cases: see852 execution table; live protocol/final-source status remains separate. |
| 45 | `ui/BrowserV2Home.kt:984` | Screenshot fallback consent · A | D T36 actual production modal component at cover/short landscape2x selected light/dark; explicit Cancel/confirm/back and long-content/permission/resource states. See browser-modal-completion.md for exact case mapping. Component cases: see852 execution table; live protocol/final-source status remains separate. |
| 46 | `ui/BrowserV2Home.kt:996` | Open external app consent · A | D T36 actual production modal component at cover/short landscape2x selected light/dark; explicit Cancel/confirm/back and long-content/permission/resource states. See browser-modal-completion.md for exact case mapping. Component cases: see852 execution table; live protocol/final-source status remains separate. |
| 47 | `ui/CreateImageSheet.kt:90` | Create Image sheet · M | D T32g actual cover/2x sheet controls and all aspect choices; T13 cancellation. Complete IME matrix U. |
| 48 | `ui/CreateImageSheet.kt:166` | Create Image remote consent · A | D T32g actual remote-create consent Cancel before generator invocation; T14 remains a separate edit-consent path. |
| 49 | `ui/CreateImageSheet.kt:171` | Create Image diagnostics · A | D T32g actual original/effective prompt diagnostic; unusually long/error diagnostics remain U. |
| 50 | `ui/CreateImageSheet.kt:178` | Create Image reference grid · G | D T32g actual 300-item reference grid, final-item selection and Done at cover/2x. No provider is called. |
| 51 | `ui/ExternalEditingDialog.kt:44` | External editing status/handoff · A | D T15a/T15b entry/prepare/import states; full theme/IME/large-text matrix U. |
| 52 | `ui/ExternalEditingDialog.kt:72` | External editor choice · A | D T16 multi-editor choice without nested-scroll crash. |
| 53 | `ui/ExternalEditingDialog.kt:76` | External editing plaintext-egress consent · A | D T15b explicit editor preparation; all visual states U. |
| 54 | `ui/ExternalEditingDialog.kt:80` | Public-copy/export consent · A | D T33 actual extracted production component, or existing production sort button, in selected light/dark at 2x with narrow/short-landscape windows, long-data scroll, explicit actions and 48dp checks. T33 four-case matrix passes852; engine/physical acceptance not inferred. |
| 55 | `ui/ExternalEditingDialog.kt:84` | Replace encrypted original confirmation · A | D T15c explicit verified replacement confirmation; failure/large text U. |
| 56 | `ui/FullscreenMediaViewer.kt:322` | Viewer Recently Deleted confirmation · A | D T33 actual extracted production component, or existing production sort button, in selected light/dark at 2x with narrow/short-landscape windows, long-data scroll, explicit actions and 48dp checks. T33 four-case matrix passes852; engine/physical acceptance not inferred. |
| 57 | `ui/FullscreenMediaViewer.kt:789` | Video opening error · A | D T33 actual extracted production component, or existing production sort button, in selected light/dark at 2x with narrow/short-landscape windows, long-data scroll, explicit actions and 48dp checks. T33 four-case matrix passes852; engine/physical acceptance not inferred. |
| 58 | `ui/GalleryAlertDialog.kt:32` | Shared alert implementation · A | D T01 shared shell only; does not upgrade unverified call sites to verified. |
| 59 | `ui/HiddenBrowserHome.kt:139` | Hidden browser settings · A | D T17 settings/tabs at four synthetic viewports; disconnected combined-mode fixture; connected controls/IME semantics U. |
| 60 | `ui/HiddenBrowserHome.kt:171` | Hidden bookmark/history library · A | U direct component: actual current HiddenBrowserHome:180/182 calls HiddenNativeLibraryContent; no androidTest reference exists. T35 now mounts BrowserBookmarksPanel/BrowserHistoryPanel, so its passing native library cases are related evidence, not a direct test of this Main handoff wrapper/body. Implemented; no demonstrated defect or automatic new-scenario requirement follows. |
| 61 | `ui/HiddenBrowserHome.kt:187` | Hidden tab overview · A | D T17 empty/disconnected overview only; populated long list/focus U. |
| 62 | `ui/HiddenFoundationDialog.kt:129` | Hidden foundation authentication · S | D T19 setup/background; shared keypad T23; whole visual matrix U. |
| 63 | `ui/HiddenRestoreDialog.kt:42` | Hidden restore destination/authentication/progress · G | D T22 + T24 destination choices, wrong/correct/cancel/lock/expiry and move confirmation; 2x/IME non-auth form U. |
| 64 | `ui/HiddenVaultDialog.kt:274` | Hidden viewer collection chooser · A | D T33 actual extracted production component, or existing production sort button, in selected light/dark at 2x with narrow/short-landscape windows, long-data scroll, explicit actions and 48dp checks. T33 four-case matrix passes852; engine/physical acceptance not inferred. |
| 65 | `ui/MediaChrome.kt:68` | Shared media More sheet · M | D T25a actual viewer More sheet; all options/large-text/landscape U. |
| 66 | `ui/MediaChrome.kt:175` | Media sort dropdown · M | D T33 actual extracted production component, or existing production sort button, in selected light/dark at 2x with narrow/short-landscape windows, long-data scroll, explicit actions and 48dp checks. T33 four-case matrix passes852; engine/physical acceptance not inferred. |
| 67 | `ui/MediaDetailsDialog.kt:124` | App-owned date input stage · A, M3 DatePicker Input mode | D T26a migrated actual-window screenshot policy/exact selection/cancel; T32k/l narrow/short large-text and DST preservation. Mapped T32 cases pass852 where selected; earlier unchanged evidence reuse and affected-boundary review apply. |
| 68 | `ui/MediaDetailsDialog.kt:125` | App-owned numeric time stage · A | D T26a migrated actual-window policy/metadata-only round trip; T32k range validation/IME Done once, T32l invalid DST rejection. Mapped T32 cases pass852 where selected; earlier unchanged evidence reuse and affected-boundary review apply. |
| 69 | `ui/MediaDetailsDialog.kt:135` | Media details · A | D T26a/T26b details/date path; all long metadata/large-text/IME U. |
| 70 | `ui/MediaSelectionDialog.kt:39` | Select by date/size · A | D T27 date/size selection scope; T32j cover/2x preset geometry, all choices and last-preset result. Date-form IME matrix U. |
| 71 | `ui/PhotoEditor.kt:548` | Enlarged source preview · S | D T09 source enlargement flow; no full-height 2x/landscape guarantee. |
| 72 | `ui/PhotoEditor.kt:560` | Enlarged AI result · S | D T09 result preview/discard flow; intentional black media canvas. |
| 73 | `ui/PhotoEditor.kt:574` | Prompt diagnostics · A | D T33 actual extracted production component, or existing production sort button, in selected light/dark at 2x with narrow/short-landscape windows, long-data scroll, explicit actions and 48dp checks. T33 four-case matrix passes852; engine/physical acceptance not inferred. |
| 74 | `ui/PhotoEditor.kt:582` | Prediction diagnostics · A | D T33 actual extracted production component, or existing production sort button, in selected light/dark at 2x with narrow/short-landscape windows, long-data scroll, explicit actions and 48dp checks. T33 four-case matrix passes852; engine/physical acceptance not inferred. |
| 75 | `ui/PhotoEditor.kt:589` | AI operation progress · A | D T33 actual extracted production component, or existing production sort button, in selected light/dark at 2x with narrow/short-landscape windows, long-data scroll, explicit actions and 48dp checks. T33 four-case matrix passes852; engine/physical acceptance not inferred. |
| 76 | `ui/PhotoEditor.kt:601` | Saving-stage progress · A | D T33 actual extracted production component, or existing production sort button, in selected light/dark at 2x with narrow/short-landscape windows, long-data scroll, explicit actions and 48dp checks. T33 four-case matrix passes852; engine/physical acceptance not inferred. |
| 77 | `ui/PhotoEditor.kt:605` | Remote AI consent · A | D T14a consent-before-edit flow; large-text/theme matrix U. |
| 78 | `ui/PhotoEditor.kt:610` | Discard edits · A | D T09 discard result flow; all exit/discard states U. |
| 79 | `ui/PrimaryCopyDialog.kt:50` | Primary-to-Hidden copy/authentication picker · S | D T28 wrong→correct PIN/source/copy plus T23 keypad; lock/expiry/full picker large-text U. |
| 80 | `ui/PrivacyShareDialog.kt:112` | Privacy share review · A | D T29 dark scroll/cancel without media read; light/IME/landscape U. |
| 81 | `ui/PrivacyShieldSettings.kt:56` | Panic Exit test confirmation · A | D T32c actual Panic Exit test confirmation Cancel without settings changes; T30 remains settings/auth related. This does not assert an actual Main lock. |
| 82 | `ui/PrivateBrowser.kt:523` | Legacy browser dropdown · M | U legacy; retained constructor is themed M3, current production routes do not call legacy BrowserHome (test harness only). |
| 83 | `ui/PrivateBrowser.kt:617` | Legacy download consent · A | U legacy; direct download-consent layout not mapped. |
| 84 | `ui/PrivateBrowser.kt:627` | Legacy image/screenshot consent · A | U legacy; direct image/screenshot-consent layout not mapped. |
| 85 | `ui/PrivateBrowser.kt:641` | Legacy bookmarks · A | U legacy; T10b targets new BrowserBookmarksPanel, not this dialog. |
| 86 | `ui/PrivateBrowser.kt:663` | Legacy diagnostics · A | U legacy; no direct debug-console modal layout mapping. |
| 87 | `ui/PrivateVideoPlayer.kt:235` | Video playback-speed menu · M | D T33 actual extracted production component, or existing production sort button, in selected light/dark at 2x with narrow/short-landscape windows, long-data scroll, explicit actions and 48dp checks. T33 four-case matrix passes852; engine/physical acceptance not inferred. |
| 88 | `ui/RetiredModelCleanup.kt:27` | Retired model cleanup · A | D T32d actual confirmation, Cancel then explicit cleanup in isolated synthetic storage; unrelated vault sentinel retained. |
| 89 | `ui/SyntheticHiddenGalleryDialog.kt:58` | Synthetic Hidden gallery harness · S | D T31 synthetic fixture import/view/reset/background; not real-vault acceptance. |
| 90 | `ui/TransferHistoryDialog.kt:33` | Encrypted transfer history · A | D T32e actual encrypted journal with 30 operations, lazy scroll, pending-operation cancel and retained-media sentinel; retry execution remains separate security coverage. |
| 91 | `ui/VaultCameraScreen.kt:404` | Camera recording details · A | D T33 actual extracted production component, or existing production sort button, in selected light/dark at 2x with narrow/short-landscape windows, long-data scroll, explicit actions and 48dp checks. T33 four-case matrix passes852; engine/physical acceptance not inferred. |
| 92 | `ui/VaultStoragePanel.kt:29` | Vault storage summary · A | D T32a actual long summary, last explanation scrolling and pinned Close at cover/2x. |
| 93 | `ui/VaultStoragePanel.kt:52` | Clear disposable preview cache · A | D T32a actual cache confirmation Cancel/no callback then explicit Clear/callback once. |

## Exact runtime-test source catalog

Class names are relative to `uk.co.traynor.privategallery`; all files are under `app/src/androidTest/java`. Method existence was checked against current source. These entries define mappings. The latest execution table distinguishes selected passes from absent classes and failed live cases; T20 fails852, while both T21 Binder cases pass.

| Ref | Test class | Exact method(s) |
|---|---|---|
| T01 | `ui.GalleryDialog1762Test` | `foldCoverDark`, `foldCoverLight`, `foldInnerDark`, `foldLandscapeLight` |
| T02 | `ui.PromptTemplateLayout1762Test` | `narrowLargeTextKeepsActionsReachable` |
| T03a | `CollectionDialogs1762Test` | `largeMediaListCanScrollAndSelectInDarkTheme` |
| T03b | `CollectionDialogs1762Test` | `largeCollectionListCanChooseLastInLightTheme` |
| T04a | `ui.BrowserConsent1752Test` | `extensionBasedUploadUsesThemedVaultPickerWithoutPrematureEgress` |
| T04b | `ui.BrowserConsent1752Test` | `cancelledPreparationCannotClearNewerChooserRequest` |
| T04c | `ui.BrowserConsent1752Test` | `websiteDialogsUseThemedShellAndRevokeOnBackground` |
| T04d | `ui.BrowserConsent1752Test` | `movedEligiblePhotoAppearsWhenPickerReopensButRestrictedPhotoDoesNot` |
| T04e | `ui.BrowserConsent1752Test` | `realWebsiteAlertAndBeforeUnloadRequireExplicitChoice` |
| T05 | `core.browser.v2.BrowserScriptDialogs1762Test` | `backgroundAndLateReplyCannotApproveOldWebsiteDialog`, `networkRevocationAndInactiveTabRequestsFailClosed`, `explicitReplyIsOnceOnlyAndCloseCancels` |
| T07 | `ui.AiProviderSetupTest` | `ownerCanOpenProviderSetupWithoutAConfiguredCredential`, `verifiedTokenIsNeverPrefilledAndRemovalDisablesProvider`, `failedTokenIsNotSavedAndRetryRemainsAvailable`, `backgroundingCancelsVerificationAndDiscardsEntry` |
| T08 | `ui.AiModelPickerTest` | `compactSelectionOpensSheetAndUpdatesWithoutHorizontalClipping`, `providerUsesCompactSelectionAndBottomSheet` |
| T09 | `ui.AiEditorProfessionalTest` | `bothSourcesCanBeReplacedWithoutChangingExactPromptAndResultCanBeDiscarded`, `lateAuthenticatedPickerLoadsAreWipedAfterBackgroundAndCannotRestorePreviews`, `sourceCardsAndActionsRemainReachableAcrossFoldViewports`, `promptAndGenerateRemainReachableWithKeyboardOpen` |
| T10a | `ui.BrowserPolishTest` | `browserSettingsOpenLocallyAndReturnToSamePageAndView` |
| T10b | `ui.BrowserPolishTest` | `bookmarksFilterAndRemoveWithoutOpeningAnotherPage` |
| T10c | `ui.BrowserPolishTest` | `historyClearNeedsConfirmationAndRetainsDatesAndTitles` |
| T11 | `ui.BrowserProfessionalUiTest` | `coverLight`, `innerDark`, `coverLandscapeLargeText`, `innerLandscapeLight` |
| T12 | `ui.BrowserUploadThumbnail1762Test` | `closeAndReopenDoesNotRecycleSharedThumbnail`, `latePreviewDoesNotRecycleBorrowedCacheEntry` |
| T13 | `ui.CreateImageCancellationTest` | `lateCancelledGenerationCannotPublishOrReplaceNewRequestState` |
| T14a | `ui.AiEditorFlowTest` | `consentPrecedesRemoteEditAndResultWaitsForSaveCopy` |
| T14b | `ui.AiEditorFlowTest` | `backgroundLockBoundaryCancelsFutureOnDeviceProviderAndWipesInput` |
| T15a | `ui.ExternalEditingUi176Test` | `compactEntryKeepsHandoffBehindExplicitConsentAndCanCancel` |
| T15b | `ui.ExternalEditingUi176Test` | `confirmedEditorChoicePreparesOnceAndImportIsExplicit` |
| T15c | `ui.ExternalEditingUi176Test` | `replaceRequiresExplicitConfirmationAfterPreview` |
| T16 | `ui.ExternalEditorChooser1762Test` | `multipleEditorsRenderAndChooseWithoutNestedScrollCrash` |
| T17 | `ui.HiddenBrowser17LayoutTest` | `foldCoverLight`, `foldInnerDark`, `foldCoverLandscapeDark`, `foldInnerLandscapeLight` |
| T18 | `core.browser.hidden.HiddenSessionLibrary1762Test` | `encryptedLibrarySurvivesReauthenticationAndKeepsModesIndependent` |
| T19 | `ui.HiddenFoundationDialogTest` | `setupOwnPinUnlocksAndHiddenLockDoesNotReplacePrimaryCredentials`, `backgroundClearsUnsubmittedPinWithoutCredentialCreation` |
| T20 | `core.browser.hidden.HiddenVpnLive1762Test` | `nativeChildPostsFormsUploadsAndRevokesOnOwnedVpn` |
| T21 | `core.browser.hidden.HiddenLibraryParcelPressure1762Test` | `maximalResponseFitsParcelAndAuthenticationFailuresPreserveCiphertext`, `maximalLibraryTraversesActualClaimedChildBinderWithoutTransactionTooLarge` |
| T22 | `ui.HiddenRestoreAuthentication1762Test` | `wrongThenCorrectKeypadPinCopiesVerifiedMediaAndKeepsHiddenOriginal`, `correctKeypadPinMovesOnlyAfterSeparateConfirmation`, `cancellationAtKeypadDoesNotPublishOrDelete`, `lockWhileEnteringPrimaryPinRevokesWithoutPublishing`, `expiredHiddenGraceRevokesPrimaryAuthenticationDialog` |
| T23 | `ui.PasscodeKeypadTest` | `digitsAreMaskedBackspaceWorksAndVariablePinRequiresConfirmation`, `keypadControlsFitFoldCoverInnerAndLandscapeViewports`, `knownFixedLengthSubmitsOnceAtBoundary` |
| T24 | `ui.HiddenRestoreDialogTest` | `choicesAreExplicitAndPrimaryRequiresFreshPinWithoutChangingHidden`, `moveRequiresSeparateConfirmationAndLockRevokesDialogOnce` |
| T25a | `ui.PhotoEditorUiTest` | `chromeHidesTogetherAndMoreUsesSheet` |
| T26a | `ui.MediaDetails174Test` | `nativeDateAndTimePickersFollowOwnerScreenshotPolicy` |
| T26b | `ui.MediaDetails174Test` | `restoreOriginalDateRefreshesDetailsWithoutChangingMediaHash` |
| T27 | `ui.MediaSelection176Test` | `dateSelectionIncludesBothLocalBoundariesAndNeverAddsOutsideScope`, `sizeSelectionUsesMetadataAndStrictPresetBoundary` |
| T28 | `ui.PrimaryCopyDialogTest` | `sourceChoiceFreshPinSelectionCopyAndReturnStayInHidden` |
| T29 | `ui.PrivacyShareDialog1762Test` | `darkShareOptionsScrollAndCancelWithoutReadingMedia` |
| T30 | `ui.SettingsPolish1761Test` | `panicConfigurationIsAbsentFromSecretWhileHideContentAccessRemains`, `primaryScreenshotEnableStillRequiresFreshAuthenticationInSecurity` |
| T31 | `ui.SyntheticHiddenGalleryTest` | `syntheticPhotoImportViewingFavouritesCollectionsAndReset`, `backgroundClosesAndReopeningHasNoDisposableState` |
| T33 | `ui.RemainingMediaModals1762Test` | `narrowDarkTwoTimesText`, `narrowLightTwoTimesText`, `shortLandscapeDarkTwoTimesText`, `shortLandscapeLightTwoTimesText` |


## Completion-stage component definitions (13/13 pass on852)

The `ui.ModalCompletion1762Test` class has13 executed passing cases on852. T32a–l retain their historical catalog labels; the actual Android configuration probe is added below. These are exact selected-case results, not universal viewport claims:

| Ref | Exact method | Actual boundary |
|---|---|---|
| T32a | `coverDarkStorageLongContentAndCacheConsentRemainReachable` | VaultStoragePanel long data + cache confirmation; no cache work on Cancel. |
| T32b | `landscapeDarkSourcePickerCanReachLastOfLargeVaultAtLargeText` | Actual source grid and opaque cyan/amber PNG through production loader/decoder; final299 image visible extent≥48dp, rendered screenshot color patches, enabled selection and Cancel. |
| T32c | `coverLightPanicTestConfirmationCancelsWithoutChangingSettings` | Actual confirmation Cancel; no preference mutation; no Main lock claim. |
| T32d | `landscapeLightRetiredModelConsentCancelsBeforeIsolatedCleanup` | Actual cleanup consent with isolated retired-model file and retained sentinel. |
| T32e | `innerDarkEncryptedTransferHistoryScrollsAndCancelsOnlyRecordedOperation` | Actual encrypted journal/component, 30 operations and one pending cancellation. |
| T32f | `coverLightBrowsingDataConfirmationRequiresExplicitChoiceAtLargeText` | Shared browser settings confirmation, cancel then one explicit callback. |
| T32g | `coverDarkCreateImageChoicesReferencesDiagnosticsAndConsentFitAtLargeText` | All aspect chips, last reference, prompt diagnostics and remote consent; zero generator calls. |
| T32h | `coverDarkPromptEditorKeepsActionsVisibleWithActualKeyboard` | Real IME window observed, pinned actions and unsaved cancellation; keyboard setting restored. |
| T32i | `coverLightCollectionActionsScrollWhileFooterStaysReachable` | Actual manager tertiary actions/confirmation; no deletion on Cancel. |
| T32j | `coverDarkSizeSelectionPresetsWrapAndKeepLastChoiceUsable` | Preset widths/selection at2x; strict final scope result. |
| T32k | `coverDarkDateTimeFieldsScaleAndKeyboardDoneCommitsOnce` | Date/time stages, invalid hour rejection and single keyboard-Done submission. |
| T32l | `landscapeLightDstGapCannotChangeOriginalMetadataOrMediaHash` | Actual MediaDetails route rejects Europe/London spring DST gap before any write. |
| T32m | `coverDarkDialogUsesActualAndroidLargeFontConfiguration` | Actual Activity/Dialog Android resource font scale2 and composition density2; no parent-modifier-only inference. |

Production remedies from this stage:

- Create Image aspect chips and size-selection presets now wrap; increased text cannot squeeze later actions into the remaining width of a fixed row. Size fields request the numeric keyboard.
- Collection management tertiary actions move into the existing scrolling body; only Cancel and Save/confirmed Delete remain pinned (parent-owned MainActivity change).
- App-created DatePickerDialog/TimePickerDialog no longer inherit platform-default grey surfaces/corners. Both stages use GalleryAlertDialog. The M3 date input encodes selected calendar dates in UTC before applying the original zone; the bounded numeric time form uses scalable app typography, valid24-hour ranges and Next/Done focus handling. Final DisplayDateTime resolution still rejects ambiguous/missing DST times before the original metadata callback. Cancellation at either stage performs no update. The owner window's screenshot flag is explicitly applied to each app-owned dialog.
- `MediaDetails174Test.nativeDateAndTimePickersFollowOwnerScreenshotPolicy` keeps its historical evidence method name, but now drives the actual Compose stages and checks the actual focused dialog WindowManager flags. Its exact2024-06-06 13:42 round trip, independent cancellation at each stage, unchanged capture timestamp and unchanged media hash assertions remain. No security assertion is waived because the widget changed.

The original93 source entries have each been inspected against their A/G/M/S implementation family. Repeated alert aliases inherit the shared palette/shape/type/scroll/action contract; bounded lazy bodies were checked for scrollBody opt-out. Fullscreen authentication/media panels intentionally use full-window surfaces/canvases, with black playback canvases preserving white media controls. This source review does **not** verify actual measured layout/focus in each state. Remaining U entries in the table still require direct-state runtime evidence; no broad completion, Fold acceptance or final source approval follows from shared-shell coverage.

Rows 54/56/57/64/73–76/91 now call actual internal production components from `ui/MediaOperationDialogs.kt`; row 87 uses its extracted production speed menu, and row 66 retains MediaSortButton with explicit minimum targets. T33 mounts those exact components, not a duplicated shell. It covers public export/open variants; trash confirmation; retryable long video error; 300-item/empty Hidden collection lists; long prompt/prediction; cancellable AI operation/save and saved state; recording-details verification/busy state; sort/speed selection. UI callbacks remain owned by original call sites; no media/camera/AI/auth work is initialized by the fixture. All four T33 methods pass852, with real menu/light-dark palette capture; this does not replace engine/physical acceptance.


## Main application surface completion definitions (14/14 pass on d5c246; unchanged component boundary reused)

The actual Main call sites now delegate their UI to internal components, keeping the existing owner/authentication/storage/transfer callbacks in the original callers. This is testability extraction, not a second mirrored widget implementation. Backup forms retain request-URI-keyed, memory-only state and clear submitted strings; numeric PIN fields now request NumberPassword. Cover selection is a bounded lazy list with an explicit current radio state. Licence text uses a maximum-height scrollable body rather than forcing360dp into a short window.

All entries below are in `MainModalInventory1762Test` and use2x app text. Fixture callbacks accept synthetic strings only and wipe received CharArrays; no credential, backup archive or vault media store is changed. Form cases explicitly observe an actual IME window and restore the keyboard setting/service flags afterward.

| Ref | Exact method |
|---|---|
| T34a | `coverLightAlbumMenuScrollsAndSelectsBeyondFirstViewport` |
| T34b | `coverDarkBackupRestoreValidatesThenDisablesActionsUntilCompletion` |
| T34c | `coverLightBackupExportRequiresEnteredRecoveryKeyAndExplicitExport` |
| T34d | `landscapeDarkLicencesLongContentScrollsWhileCloseStaysVisible` |
| T34e | `coverDarkOwnerConfirmationMasksInputAndRejectsWrongPinBeforeCorrectPin` |
| T34f | `coverLightChangePinValidatesMismatchAndPreservesCallbackBoundary` |
| T34g | `coverDarkCopyConfirmationRequiresExplicitAcceptance` |
| T34h | `landscapeLightMoveConfirmationRequiresExplicitAcceptance` |
| T34i | `coverDarkTransferProgressCanRequestSafeStopOnceWithoutDismissing` |
| T34j | `coverLightRecentlyDeletedConfirmationKeepsCancelAndMoveReachable` |
| T34k | `landscapeDarkRestoreConfirmationKeepsRestrictionExplanationAndChoice` |
| T34l | `coverDarkPermanentDeleteRequiresSeparateExplicitChoice` |
| T34m | `coverLightCollectionCoverShowsSelectionAndReachesLastOfLargeList` |
| T34n | `coverDarkNewCollectionUsesActualFieldAndExplicitCreateWithKeyboard` |

These definitions close the prior wholly-unmapped Main surface IDs01–13 at the component-definition level. Reuse the d5c246 Main14 result after the unchanged extracted-component/test/shared-shell/viewport-rule comparison described above; affected owner/lifetime changes remain separately reviewed. Existing encrypted transfer, backup/credential and owner lifetime suites remain required for the corresponding storage/security behavior; these layout fixtures do not replace them. No physical acceptance is inferred.

## Overlay families beyond constructors

- **Privacy covers, secondary windows, panic shield and accessibility focus concealment:** MainActivity lifecycle/security path. `HiddenVpnHandoff1762Test.childHomeUsesIndependentHiddenGraceAndImmediatePrimaryPolicy`, `childScreenOffLocksBothEvenWhenMainIsAlreadyStopped`, `delayedLossAfterForegroundExpiresOnlyTheMatchingIndependentAuthentication`, `lateMainStartConsumesReturnBeforeOverdueTimerCanBeCancelled` cover synthetic handoff/lifecycle behavior. They are not physical recents/fold/window-stack/TalkBack screenshot proof. The nine selected HiddenVpnHandoff1762 cases pass852; physical lifecycle acceptance remains separate.
- **Fullscreen independent authentication:** T19/T22/T23/T24/T28 map Hidden setup and cross-vault PIN paths. Primary setup/change/recovery and numeric form IME/keyboard traversal still need direct actual-page coverage rather than inferring it from keypad geometry.
- **Media/editor/camera fullscreen canvases and controls:** T09/T25 and `PhotoEditorUiTest.foldSizedCanvasesKeepSaveAndToolsReachable`, `PhotoEditorUiTest.backgroundWipesLoadedSourceBeforeResume`; `VaultCameraUiTest.blackPhotoTapsVolumeAndNavigationGestures`, `readingPhotoTapsVolumeAndNavigationGestures`, `activityForegroundLossLocksBothVaultsAndNeverReopensHiddenCamera` are related active component tests. Recording-details actual modal is mapped to passing T33 on852. Black media backgrounds are intentional.
- **Primary browser full-screen panels:** T10/T11 cover current panels and adaptive chrome. Their tests do not establish all nested confirmation/find/permissions/diagnostic states.
- **Legacy temporary popup WebView:** source risk described above; absent from constructor inventory and unreachable through current production routes. A test-only BrowserHome harness exists; popup close/back/keyboard restoration remains unverified for retained legacy code.
- **Native Tor child and transport:** native VPN/Tor use the same actual panels/dialog bodies; T35 passes their selected component cases. The disconnected combined-mode fixture still cannot verify connected transport. NativeTorIsolation1762Test fails852, so real native-Tor forms/uploads/downloads/network-sink assertions remain unaccepted pending protocol repair and rerun.
- **System UI:** Android permissions, biometrics, VPN consent, photo picker, SAF and external editors are externally owned. App egress/permission explanation dialogs remain covered by this inventory; external OEM appearance must not be counted as app wrapper verification.

## Evidence use and remaining unestablished states

The owner explicitly requested minimal CI. Reuse unchanged passing modal/auth/editor evidence from d5c246/c9/852 after source-boundary review; do not automatically run305/307 cases or add scenarios to increase counts. Reverify only demonstrated defects and necessary security/owner boundaries with the agreed existing affected selection. U records distinguish physical/unestablished states and genuine direct-component gaps; they are not an instruction to create new mandatory tests.

Cover/inner/fold transitions, system-maximum fonts/contrary OS theme, OEM keyboard/editor behavior and TalkBack remain limited to their actual evidence. Existing selected IME/Back/security checks still apply. No all-state, physical acceptance, or release approval claim follows from reuse.

## Native and Primary browser completion case catalogs (selected component execution available)

T35 is `ui.HiddenNativeBrowserDialogs1762Test`, all22 current named methods (expanded by parity completion): actual production auth, website message/prompt, settings, bounded tabs/library, upload intro/consent and resubmit bodies. Source list and viewport/action mappings are in `native-child-modal-inventory.md`. Native live protocol definitions separately target callbacks and origin/authority fencing, but their failing852 cases do not establish a completed protocol run. A direct body test does not establish real network behavior.

T36 is `ui.BrowserModalSurfaces1762Test`, all10 named methods, exact case mappings in `browser-modal-completion.md`. BrowserV2Home calls the tested components for internal viewer, media-save progress/consent, Find, diagnostics, arbitrary message, camera/microphone, location, image resource, screenshot fallback and external-app consent. Owner callbacks, permission and egress policy remain in the original session.

## Historical broad selection accounting (not an instruction to rerun)

The retained broad runner configuration selects27 existing modal interaction methods in T07–T11, T13–T14, T19, T25a, T27 and T31 and uses exact class/method gate matching. This describes that configuration only; the owner's minimal-CI direction does not require invoking it. Reuse reviewed unchanged earlier exact-SHA evidence for absent methods; select existing cases again only for demonstrated defects or necessary affected owner/security boundaries. Physical Fold acceptance remains unestablished.

## Restored-scope parity/recovery variants added after2781

These rows extend the preserved original93 inventory; they never replace an original requirement. Their selected shared components and editor acknowledgement pass852 as accounted below; native callback/transport failures remain blockers.

| Variant | Actual shared production surface | Direct selected coverage |
|---|---|---|
| R94 External editing recovery acknowledgement | ExternalEditingDialog / GalleryAlertDialog; distinct Keep receipt and Acknowledge and forget; public/cache/cloud limits | ExternalEditingUi176Test.forgetRecoveryReceiptAtDoubleFontRequiresExplicitAcknowledgement and forgetRecoveryReceiptLandscapeDarkAtDoubleFontRequiresExplicitAcknowledgement (actual OS2x/Dialog resource probe); ExternalEditorRecovery1762Test persistent receipt/reopen/recreation cases |
| R95 Native tabs/settings/bookmarks/history panels | BrowserTabsPanel / BrowserPanel / BrowserBookmarksPanel / BrowserHistoryPanel, shared with Primary; selected card, bounded scrolling/search/keyboard/clear confirmation | HiddenNativeBrowserDialogs1762Test current complete class; BrowserPolishTest; BrowserProfessionalUiTest; HiddenLibraryParcelPressure1762Test actual child Binder |
| R96 Native Find and download status/return | Actual production `HiddenFindBar` and `HiddenDownloadsDialog`; bounded result scroll, keyboard Done, Previous/Next/Close; downloads explicit return/cancel-transfer explanation | `HiddenNativeBrowserDialogs1762Test.findLightCoverKeyboardAndActions`, `findDarkLandscapeKeyboardAndActions`, `downloadsLightCoverLongStatusAndExplicitActions`, `downloadsDarkLandscapeLongStatusAndExplicitActions` (2x font, actual production components); `HiddenVpnLive1762Test` actual WebView Find; `HiddenVpnHandoff1762Test` parent foreground/auth/generation |
| R97 Browser failure overlay | BrowserPageError shared Primary/native, selected theme, scroll/retry/settings; TLS errors still cancelled | HiddenNativeBrowserDialogs1762Test.sharedPageFailureOffersRealRetryAndSettingsCallbacks; HiddenVpnLive1762Test real HTTP failure/retry |

Current constructor reconciliation was regenerated from actual source (89 sites). Reuse removes obsolete native wrapper constructors while adding variants, so equal counts do not prove equal scope. The latest selected modal classes pass852; four native protocol cases fail, and later affected source needs only owner-reviewed targeted revalidation; no automatic broad rerun is prescribed. Earlier85/88 does not approve later bytes. Physical Fold/OEM/keyboard/assistive acceptance remains separate.
