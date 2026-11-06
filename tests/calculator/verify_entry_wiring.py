"""Exact authority-bound entry and unchanged legacy version path regression; native tests cover gestures/lifecycle."""
from pathlib import Path
root=Path(__file__).resolve().parents[2]
main=(root/'app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt').read_text()
entry=main.split('internal fun openHiddenFromCalculator(',1)[1].split('@Composable internal fun HiddenCalculatorEntrySettings',1)[0]
for required in ('gesture.consume()', '!current()', '!context.current()', 'context.spaceId!="primary"', '!activityResumed', 'hideContent', 'retained.hidden.selected', '!homeVisible', '!displayPrivateHome()', '!session.isUnlocked', 'sessionKey==null', 'Lifecycle.State.RESUMED', 'HiddenCalculatorEntryStore(applicationContext).enabled()', 'retained.hidden.selectLocked()'):
 assert required in entry, required
state=(root/'app/src/main/java/uk/co/traynor/privategallery/ui/HiddenVaultState.kt').read_text()
assert 'fun selectLocked() {lock();select()}' in state
assert 'complete(' not in entry and 'unlock(' not in entry
assert 'internal fun openHiddenFromSettings' in main and 'sequence.consumeCompletion()' in main
ui=(root/'app/src/main/java/uk/co/traynor/privategallery/home2/CalculatorUi.kt').read_text()
public=ui.split('/** This component is only composed',1)[0]
assert '"Hold equals"' in public and 'onLongClick=if(label=="=")onHold' in public
assert 'putString' not in public and 'gesture.reset()' in public
assert 'epoch==capturedEpoch' in public and 'activeNow.value' in public
assert 'session.isAuthenticationCurrent(identity)' in ui
broker=(root/'app/src/main/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenVpnBrowserBroker.kt').read_text()
assert '.unlockForComparison(pin)' in broker and 'now-lastPinAttempt>=1000' in broker
policy=(root/'app/src/main/java/uk/co/traynor/privategallery/core/backup/BackupPolicy.kt').read_text()
assert 'calculator-entry-v1' not in policy and 'attempts-v1' not in policy
print('Calculator concealed-entry wiring checks passed')
