# Working Mode acceptance

Open Nova Launcher Settings, NovaOS systems, then Working Mode. Start and finish a manual shift. Record a delivery status, earnings, fuel and mileage, then reopen and verify the last shift. Set a short local-time schedule and verify one foreground start from that schedule. Overnight schedules use the day on which the window begins.

## Paired vehicle

1. Pair a vehicle audio device using Android Settings. Open **Set up vehicle** in Working Mode and grant **Nearby devices** when Android prompts. Select the paired device in the protected chooser. The regular screen shows only generic connection status.
2. Leave automatic start off for the first trial. With Working Mode visible, connect the selected device and wait for Android to confirm its A2DP or Headset connection. Accept the offer or skip that connection. Connecting a different paired device should leave the shift idle.
3. Enable **Start the shift automatically** and leave **Also open Driving Mode** off, disconnect, then reconnect while Working Mode is visible. Confirm one shift starts with Driving Mode off. End it manually and reopen Working Mode while the same audio connection remains active. A verified disconnect is required to rearm the trigger. Changes outside the foreground are reconciled when the page returns, with uncertain results leaving the trigger idle. Removing the selected vehicle turns off this trigger.
4. Deny or revoke Nearby devices permission and verify the manual controls remain available. Switch away from Home or make another app the top-resumed window and verify the page does not activate Bluetooth or location features in the background.

The selected device identifier and handled state use NovaOS's encrypted local settings. The protected chooser reads already-paired Android devices. It has no discovery, pairing, or scanning. Android audio-profile state can be unavailable on some devices, and wireless Android Auto or generic BLE connectivity does not necessarily expose either supported audio profile. Disconnecting the vehicle rearms the trigger while preserving the shift and its totals.

## Driving screen

Check from the parked vehicle or as a passenger using the Galaxy Z Fold. Enter Driving Mode manually. Deny location access once and confirm speed shows unavailable, editing controls are hidden, and Emergency phone remains visible. Grant precise foreground location and verify plausible speed in mph, declared accuracy where Android reports it, and a return to no fresh reading when updates stop. Location updates run only in foreground Driving Mode. Working Mode records no route coordinates. The shift clock ends at the configured 18-hour maximum.

Speed limit and camera observations need a verified external source. Courier apps and Shift Tracker can be configured as launch shortcuts. Shift records entered here reflect the owner's entries.

## Optional vehicle driving screen

With one paired audio device selected, enable **Start the shift automatically**, then **Also open Driving Mode**. Connect while Working Mode is visible and verify the simplified controls appear. Leaving Driving Mode manually keeps that decision for the same connection, including after recreation. A verified disconnect and reconnect rearms the option. A radio drop alone keeps the simplified screen visible. An existing setup defaults to shift-only activation.

## Shift Tracker dashboard

Shift Tracker v2.2.77 publishes Android medium and large Home widgets with the current activity, delivery count, timer and estimated pay from its own local shift mirror. Its work-event broadcasts specifically target the separate James OS application. Working Mode hosts the published widget through Nova Home’s existing widget permissions and Sanctuary guards. It checks the stable Shift Tracker package, its release signing identity and the exact provider. This feature copies no figures into Nova’s manual totals.

On the Fold, while parked:

1. Install the existing signed Shift Tracker from its normal updater. Open Working Mode and choose **Choose a Home widget**. Select Shift Tracker’s medium or large widget. Declining Android’s binding prompt should leave the summary unavailable.
2. Tap **Show shift summary**. Compare the count and estimated pay with Shift Tracker’s own Home widget. Record one delivery in Shift Tracker and check the updated widget. Close the summary, fold and unfold, then reopen it. The original Home widget must still update.
3. Check that the protected summary cannot be screenshotted, the widget disappears in Driving Mode, and Sanctuary activation removes access. Removing the Home widget removes the summary binding. A missing or differently signed installation cannot supply a summary.
4. Check the vehicle driving option both with a manually started shift and with a scheduled shift. Verify one clock, continuous totals, manual override and no automatic switch from a different device.

Reviewed Tracker source: [ShiftWidgets.kt](https://github.com/traynor1987/shift-tracker-android/blob/2a731182da1ca488bab274c8e87712b3808c95e7/android-shell/app/src/main/java/site/chatgpt/traynor1987/dominosshifttracker/ShiftWidgets.kt). Release APK SHA-256: `8000f661c149a2cd7e33b8230d32d52d85cd12a1a23c1f190ec916fb94b7c620`. Signing-certificate SHA-256: `fdfd0db43b2d1ef9a7516aad5c78f62a16c1f6ae048eccb8c7024f3ceda5d4e1`.

## Road information

The installed Speed Buddy app can display its own road information through a large Driving Mode shortcut. Navigation remains assigned independently to Google Maps or the owner's chosen app. Both shortcuts pass through Nova's usual launcher and Sanctuary admission. Select a different road-information app while parked if desired. The Speed Buddy manifest at [6584c5a](https://github.com/traynor1987/Speedbuddy/blob/6584c5a7575fd72e7fd8d7d4b9f4d2cb551d25b1/app/src/main/AndroidManifest.xml) exposes a launcher entry and keeps its DrivingService and update FileProvider private. It supplies no cross-app road-limit or camera observation feed. Nova shows its own source as unavailable and does not upload location or copy Speed Buddy’s databases.
