# Modal implementation

Changed only UI files and one Android UI test; no commits or pushes.

- AiEditingSettings.kt: actual prompt editor now scrolls its title and introduction with its form; pinned Reset/Cancel/Save actions use FlowRow so they wrap at narrow widths and large font sizes. Editor visibility changed private to internal solely for direct component testing.
- CreateImageSheet.kt: reference picker introduction is a full-span item in its bounded lazy grid, allowing explanatory chrome to scroll rather than consume the fixed viewport. Done remains outside the grid.
- BrowserV2Home.kt: upload picker introduction/loading/empty messages are full-span lazy-grid content. Grid gets remaining bounded space; Cancel/Continue use FlowRow outside scrolling content. No session, callback, transport or permission logic changed.
- HiddenRestoreDialog.kt: non-authentication content now uses GalleryDialogSurface for the shared outline/elevation/shape/palette. Fullscreen Primary PIN surface, SecureOn, dismissal, transfer and authentication logic preserved. Existing restore actions remain within their scrollable form, deliberately avoiding a behavioral redesign.
- TransferHistoryDialog.kt: resume/cancel row now wraps through FlowRow.
- PromptTemplateLayout1762Test.kt: adds a real prompt-editor test at 320x640 and 2x text, requiring all three footer actions displayed, scrolling to form content, and Cancel invoking dismissal without saving/resetting.

Validation: git diff --check passed. Source diff inspected. Android build and the new instrumentation test were not run: controller reports no usable SDK/network in this environment. No runtime, compile or visual success is claimed.

Still requires acceptance: real upload/reference grids in cover/landscape windows and both themes, IME-visible prompt editor, secure restore states and transfer history; keyboard focus/dismissal and physical Fold transitions. Legacy PrivateBrowser popup concern from audit was not changed because reachability/ownership needs confirmation and this implementation focuses active modal layout defects.
