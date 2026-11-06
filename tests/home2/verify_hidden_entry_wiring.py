"""Concealed-entry wiring regression: public Primary navigation is never an admission grant."""
from pathlib import Path
root=Path(__file__).resolve().parents[2]
main=(root/'app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt').read_text()
ui=(root/'app/src/main/java/uk/co/traynor/privategallery/home2/PreviewUi.kt').read_text()
data=(root/'app/src/main/java/uk/co/traynor/privategallery/home2/PreviewData.kt').read_text()
start=main.index('    internal fun switchNovaSpace()')
end=main.index('    internal fun lockNovaSpace()',start)
assert 'secretDiscovered' not in main[start:end], 'Persisted discovery still grants Primary a public Hidden-entry path'
assert 'if(hidden||secretDiscovered)' not in main, 'Primary Home still receives a Space-switch action'
assert 'consumeCompletion()' in main, 'Settings admission must consume the transient version sequence'
assert 'Switch Space' not in ui, 'Public Space-switch labels must not disclose Hidden'
assert 'owner=="hidden"' in data and 'val onSwitch=onSwitch?.takeIf' in data, 'Primary model must discard supplied/stale switch callbacks'
print('Concealed Hidden entry wiring: PASS')
