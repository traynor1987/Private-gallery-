from pathlib import Path
root = Path(__file__).resolve().parents[2]
main = (root / 'app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt').read_text()
data = (root / 'app/src/main/java/uk/co/traynor/privategallery/home2/PreviewData.kt').read_text()
home = (root / 'app/src/main/java/uk/co/traynor/privategallery/home2/IntegratedHome.kt').read_text()
# These guard the integration boundaries that isolated preference/policy tests cannot exercise.
assert 'PrivateGalleryTheme(selectedNativeTheme())' in main, 'Native surfaces ignore the selected Space theme'
assert 'onThemeCommitted' in data and 'onThemeConnection' in home, 'Home theme has no native persistence bridge'
assert 'EditingContextPolicy.allowed' in main, 'External edit/recovery has no destination boundary'
assert 'if (!hideContent && mediaDestination) viewerRequest?.let' in main, 'Photo editor can remain over Browser/Settings'
assert 'if (!hideContent && primaryPresentation && mediaDestination) collectionTarget' in main, 'Media sheet can remain over Browser/Settings'
restore = main.split('private fun restoreBrowserPresentation() {', 1)[1].split('\n    private fun ', 1)[0]
assert '!browserV2Session.hasPrivateAppTabs()' in restore, 'Async native restore can replace a known app session'
print('Theme/editor integration boundaries: PASS')
