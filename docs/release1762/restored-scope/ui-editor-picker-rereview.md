# Scoped UI/editor/picker re-review

Reviewed only the live corrections to the two findings from ui-editor-picker-review.md and the new ExternalEditorChooser1762Test. No tests rerun; VPN and other changes excluded.

## Result

Both reviewed findings are resolved by source inspection. No remaining Critical or Important findings within this re-review scope.

- `app/src/main/java/uk/co/traynor/privategallery/ui/ExternalEditingDialog.kt:72–75`: chooser body now uses a plain Column. GalleryAlertDialog supplies the sole vertical scroller, eliminating the nested infinite-height measurement path. `app/src/androidTest/java/uk/co/traynor/privategallery/ui/ExternalEditorChooser1762Test.kt:15–29` mounts the real ExternalEditingDialog with two editor targets, verifies the chooser renders, scrolls to Samsung, clicks it, and checks explicit component delivery. This test meaningfully covers the previously crashing path.
- `app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt:2301–2303,2312,2381–2387`: loadPreview rejects concealed, locked, or Hidden-selected requests before its cache fast path, rechecks Primary authentication/selection and owner identity during IO and before publishing, and returns a failure on stale completion. Stale success cleanup recycles only locally owned decoded bitmaps, preserving shared cache entries.

No definite compile issue found in these edits or the new test by inspection. This is not a build/test-success assertion or full-release approval.
