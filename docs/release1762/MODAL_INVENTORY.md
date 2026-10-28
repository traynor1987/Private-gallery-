# Modal source inventory

Generated source index, not a visual or security audit. System-owned permission UI is excluded by manual review only; false positives and indirect surfaces are possible. Each entry still requires review for theme, accessibility, geometry, lifecycle, and security. Native date/time pickers use app-selected light/dark themes as of this branch.

- `app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt:3415` — `AlertDialog(onDismissRequest = { if (!restoring) onCancelBackupRestore() },`
- `app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt:3987` — `AlertDialog(onDismissRequest = onCancelBackupExport,`
- `app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt:4007` — `AlertDialog(`
- `app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt:4033` — `AlertDialog(`
- `app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt:4060` — `AlertDialog(`
- `app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt:4362` — `AlertDialog(onDismissRequest={pendingHiddenMove=null},title={Text("$action ${ids.size} selected items?")},`
- `app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt:4373` — `if (transferring) AlertDialog(onDismissRequest={},title={Text("Transferring to Hidden")},`
- `app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt:4380` — `if (confirmingBulkTrash) AlertDialog(onDismissRequest = { confirmingBulkTrash = false },`
- `app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt:4390` — `if (confirmingBulkRestore) AlertDialog(onDismissRequest = { confirmingBulkRestore = false },`
- `app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt:4400` — `permanentlyDelete?.let { item -> AlertDialog(onDismissRequest = { permanentlyDelete = null },`
- `app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt:4433` — `AlertDialog(onDismissRequest={choosingCover=null},title={Text("Choose collection cover")},`
- `app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt:4593` — `AlertDialog(`
- `app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt:4607` — `AlertDialog(`
- `app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt:4622` — `AlertDialog(`
- `app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt:4687` — `AlertDialog(`
- `app/src/main/java/uk/co/traynor/privategallery/ui/AiEditingSettings.kt:144` — `Dialog(onDismissRequest = dismiss, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn, usePlatformDefaultWidth = false)) {`
- `app/src/main/java/uk/co/traynor/privategallery/ui/AiEditingSettings.kt:204` — `Dialog(onDismissRequest = close, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn, usePlatformDefaultWidth = false)) {`
- `app/src/main/java/uk/co/traynor/privategallery/ui/AiSourceCards.kt:92` — `AlertDialog(onDismissRequest = onDismiss,`
- `app/src/main/java/uk/co/traynor/privategallery/ui/BrowserPanels.kt:44` — `Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {`
- `app/src/main/java/uk/co/traynor/privategallery/ui/BrowserPanels.kt:175` — `if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear browsing history?") },`
- `app/src/main/java/uk/co/traynor/privategallery/ui/BrowserPanels.kt:243` — `if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear browsing data?") },`
- `app/src/main/java/uk/co/traynor/privategallery/ui/BrowserV2Home.kt:478` — `androidx.compose.ui.window.Dialog(onDismissRequest = { ui.internalMedia = null },`
- `app/src/main/java/uk/co/traynor/privategallery/ui/BrowserV2Home.kt:698` — `if (ui.saveVideoDialog) AlertDialog(onDismissRequest = { if (!ui.saveBusy) ui.saveVideoDialog = false },`
- `app/src/main/java/uk/co/traynor/privategallery/ui/BrowserV2Home.kt:791` — `if (ui.vaultUploadChoice) AlertDialog(onDismissRequest = ::cancelUpload,`
- `app/src/main/java/uk/co/traynor/privategallery/ui/BrowserV2Home.kt:800` — `if (ui.vaultPickerOpen) androidx.compose.ui.window.Dialog(onDismissRequest = ::cancelUpload,`
- `app/src/main/java/uk/co/traynor/privategallery/ui/BrowserV2Home.kt:825` — `if (ui.selectedUploads.isNotEmpty() && !ui.vaultPickerOpen) AlertDialog(onDismissRequest = ::cancelUpload,`
- `app/src/main/java/uk/co/traynor/privategallery/ui/BrowserV2Home.kt:851` — `if (ui.findOpen) AlertDialog(`
- `app/src/main/java/uk/co/traynor/privategallery/ui/BrowserV2Home.kt:858` — `if (ui.diagnosticsOpen && BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS) AlertDialog(`
- `app/src/main/java/uk/co/traynor/privategallery/ui/BrowserV2Home.kt:888` — `ui.message?.let { value -> AlertDialog(onDismissRequest = { ui.message = null }, text = { Text(value) }, confirmButton = { TextButton(onClick = { ui.message = null }) { Text("OK") `
- `app/src/main/java/uk/co/traynor/privategallery/ui/BrowserV2Home.kt:889` — `ui.pendingPermission?.let { request -> AlertDialog(`
- `app/src/main/java/uk/co/traynor/privategallery/ui/BrowserV2Home.kt:907` — `ui.pendingGeolocation?.let { (origin, callback) -> AlertDialog(`
- `app/src/main/java/uk/co/traynor/privategallery/ui/BrowserV2Home.kt:918` — `ui.pendingImageResource?.let { resource -> AlertDialog(`
- `app/src/main/java/uk/co/traynor/privategallery/ui/BrowserV2Home.kt:930` — `if (ui.pendingScreenshotFallback) AlertDialog(`
- `app/src/main/java/uk/co/traynor/privategallery/ui/BrowserV2Home.kt:942` — `AlertDialog(`
- `app/src/main/java/uk/co/traynor/privategallery/ui/CreateImageSheet.kt:164` — `if (consentPending) AlertDialog(onDismissRequest = { consentPending = false },`
- `app/src/main/java/uk/co/traynor/privategallery/ui/CreateImageSheet.kt:169` — `if (diagnostic) AlertDialog(onDismissRequest = { diagnostic = false },`
- `app/src/main/java/uk/co/traynor/privategallery/ui/CreateImageSheet.kt:176` — `if (pickingReferences) androidx.compose.ui.window.Dialog(onDismissRequest = { pickingReferences = false }) {`
- `app/src/main/java/uk/co/traynor/privategallery/ui/ExternalEditingDialog.kt:41` — `AlertDialog(onDismissRequest={if(!busy)close()},title={Text("Edit in another app")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spac`
- `app/src/main/java/uk/co/traynor/privategallery/ui/ExternalEditingDialog.kt:69` — `if(consent)AlertDialog(onDismissRequest={consent=false},title={Text("Allow external editing?")},`
- `app/src/main/java/uk/co/traynor/privategallery/ui/ExternalEditingDialog.kt:73` — `if(exportConsent)AlertDialog(onDismissRequest={exportConsent=false},title={Text(if(exportChosen)"Open exported photo?" else "Export a public copy?")},`
- `app/src/main/java/uk/co/traynor/privategallery/ui/ExternalEditingDialog.kt:77` — `if(replace)AlertDialog(onDismissRequest={replace=false},title={Text("Replace encrypted original?")},text={Text("The edited result will be imported and verified first. Only then is `
- `app/src/main/java/uk/co/traynor/privategallery/ui/FullscreenMediaViewer.kt:322` — `AlertDialog(`
- `app/src/main/java/uk/co/traynor/privategallery/ui/FullscreenMediaViewer.kt:789` — `if (error != null) AlertDialog(onDismissRequest = close,`
- `app/src/main/java/uk/co/traynor/privategallery/ui/HiddenBrowserHome.kt:133` — `AlertDialog(modifier=Modifier.semantics { testTag="hidden-browser-settings" },onDismissRequest={settings=false},title={Text("Browser settings")},text={`
- `app/src/main/java/uk/co/traynor/privategallery/ui/HiddenBrowserHome.kt:153` — `if(overview)AlertDialog(onDismissRequest={overview=false},title={Text("Hidden tabs")},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) {`
- `app/src/main/java/uk/co/traynor/privategallery/ui/HiddenFoundationDialog.kt:129` — `Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,securePolicy=SecureFlagPolicy.SecureOn)) {`
- `app/src/main/java/uk/co/traynor/privategallery/ui/HiddenRestoreDialog.kt:42` — `Dialog(onDismissRequest={finish("Restore cancelled. Completed copies remain; incomplete moves retain their Hidden sources.")},properties=DialogProperties(usePlatformDefaultWidth=fa`
- `app/src/main/java/uk/co/traynor/privategallery/ui/HiddenVaultDialog.kt:273` — `viewerCollectionItem?.let {id ->AlertDialog(onDismissRequest={viewerCollectionItem=null},title={Text("Add to collection")},`
- `app/src/main/java/uk/co/traynor/privategallery/ui/MediaDetailsDialog.kt:123` — `dateDialog=DatePickerDialog(context,pickerTheme,{_,year,month,day->`
- `app/src/main/java/uk/co/traynor/privategallery/ui/MediaDetailsDialog.kt:124` — `if(alive.get()) {timeDialog=TimePickerDialog(context,pickerTheme,{_,hour,minute->`
- `app/src/main/java/uk/co/traynor/privategallery/ui/MediaDetailsDialog.kt:134` — `AlertDialog(onDismissRequest=onClose,title={Text("Media details")},text={Column(Modifier.fillMaxWidth().heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangeme`
- `app/src/main/java/uk/co/traynor/privategallery/ui/MediaSelectionDialog.kt:38` — `AlertDialog(onDismissRequest=onDismiss,title={Text("Select by date or size")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8`
- `app/src/main/java/uk/co/traynor/privategallery/ui/PhotoEditor.kt:547` — `if (bitmap != null) androidx.compose.ui.window.Dialog(onDismissRequest = { enlargedSource = null },`
- `app/src/main/java/uk/co/traynor/privategallery/ui/PhotoEditor.kt:559` — `androidx.compose.ui.window.Dialog(onDismissRequest = { enlargedResult = false },`
- `app/src/main/java/uk/co/traynor/privategallery/ui/PhotoEditor.kt:573` — `if (promptDiagnostic) AlertDialog(onDismissRequest = { promptDiagnostic = false },`
- `app/src/main/java/uk/co/traynor/privategallery/ui/PhotoEditor.kt:581` — `if (predictionDiagnostic) AlertDialog(onDismissRequest = { predictionDiagnostic = false },`
- `app/src/main/java/uk/co/traynor/privategallery/ui/PhotoEditor.kt:588` — `if (otherBusy && tool == "AI Edit" && savingStage == null) AlertDialog(onDismissRequest = {},`
- `app/src/main/java/uk/co/traynor/privategallery/ui/PhotoEditor.kt:600` — `savingStage?.let { stage -> AlertDialog(onDismissRequest = {}, title = { Text(stage) },`
- `app/src/main/java/uk/co/traynor/privategallery/ui/PhotoEditor.kt:604` — `if (showConsent) AlertDialog(onDismissRequest = { showConsent = false }, title = { Text("Remote AI processing") }, text = { Column {`
- `app/src/main/java/uk/co/traynor/privategallery/ui/PhotoEditor.kt:609` — `if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard edits?") }, text = { Text("Your original stays untouched.") }, confirmButton = { TextButton`
- `app/src/main/java/uk/co/traynor/privategallery/ui/PrimaryCopyDialog.kt:49` — `Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn)) {`
- `app/src/main/java/uk/co/traynor/privategallery/ui/PrivacyShareDialog.kt:109` — `AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text("Review share")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {`
- `app/src/main/java/uk/co/traynor/privategallery/ui/PrivacyShieldSettings.kt:55` — `if(testing) AlertDialog(onDismissRequest={testing=false},title={Text("Test Panic Exit?")},`
- `app/src/main/java/uk/co/traynor/privategallery/ui/PrivateBrowser.kt:617` — `AlertDialog(`
- `app/src/main/java/uk/co/traynor/privategallery/ui/PrivateBrowser.kt:627` — `AlertDialog(`
- `app/src/main/java/uk/co/traynor/privategallery/ui/PrivateBrowser.kt:641` — `if (showBookmarks) AlertDialog(`
- `app/src/main/java/uk/co/traynor/privategallery/ui/PrivateBrowser.kt:663` — `if (showDiagnostics) AlertDialog(`
- `app/src/main/java/uk/co/traynor/privategallery/ui/RetiredModelCleanup.kt:26` — `if (confirm) AlertDialog(onDismissRequest = { confirm = false },`
- `app/src/main/java/uk/co/traynor/privategallery/ui/SyntheticHiddenGalleryDialog.kt:58` — `Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth=false, securePolicy=SecureFlagPolicy.SecureOn)) {`
- `app/src/main/java/uk/co/traynor/privategallery/ui/TransferHistoryDialog.kt:31` — `AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("Transfer history")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {`
- `app/src/main/java/uk/co/traynor/privategallery/ui/VaultCameraScreen.kt:403` — `AlertDialog(onDismissRequest=onDismiss,title={Text("Recording details")},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) {`
- `app/src/main/java/uk/co/traynor/privategallery/ui/VaultStoragePanel.kt:28` — `if(open)AlertDialog(onDismissRequest={open=false;summary=null},title={Text("Vault storage")},text={Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),vertic`
- `app/src/main/java/uk/co/traynor/privategallery/ui/VaultStoragePanel.kt:51` — `if(confirmClear)AlertDialog(onDismissRequest={confirmClear=false},title={Text("Clear disposable previews?")},text={Text("Only regenerable Primary preview-cache files and memory thu`

## Final source audit, 28 October 2026

All 59 alert call sites in 19 production files now explicitly alias `GalleryAlertDialog`, including copy/move/delete, collections, backup, privacy, camera recording options, browser permissions/uploads/downloads/error/recovery, AI and external editing. The shared shell sets selected-app surface/content/accent colours, 20dp rounded shape, subtle outline/elevation, width bound, default scrolling text and separate Material48dp actions. Bodies already owning bounded scroll/lazy containers opt out of additional scrolling. There are no `android.app.AlertDialog.Builder`, legacy Material2 alerts or popup windows in production source.

Custom bounded dialogs: browser Vault picker and image-creation reference picker use `GalleryDialogSurface`; prompt templates and API credentials now use it too. Hidden restore uses the same selected palette/shape; its numeric auth, Primary-copy and Hidden entry screens remain secure full-screen experiences. Full-screen tabs/settings/history, video and photo preview dialogs intentionally retain their existing theme-aware screen layout or black media canvas with contrasting controls. Black media canvases are viewing surfaces, not unexpectedly white alerts.

All three bottom-sheet families (gallery actions, image creation, AI provider/model choice) use selected Material3 palette, rounded sheet shape, scrolling bounded content and system insets. Dropdown menus (albums, sort, video speed, legacy browser overflow) inherit the selected Material3 palette and provide scrollable native Compose menus. Native date/time picker styles explicitly select light/dark and the existing blue-grey accent; Android permission, biometric, VPN-consent, chooser, photo-picker and document-provider UI remain system-owned.

Active Primary V2 JavaScript alert/confirm/prompt/before-unload requests previously fell through to WebView's native theme. They now use the shared alert shell with website origin and explicit actions; pending requests cancel on background, network revocation, tab close/change, navigation and renderer loss. Hidden keeps JavaScript disabled and GET-only transport. No JS/POST capability is enabled for Hidden.

This source audit covers identified app-owned surface families. The added real-window tests exercise cover/inner/landscape dimensions, explicit light/dark and 1.6x text with scrolling/pinned actions. Android execution is pending for these latest changes. Physical Samsung keyboard, fold/unfold and OEM system-dialog appearance remain owner acceptance tasks; no claim that every OEM layout has been physically observed.
