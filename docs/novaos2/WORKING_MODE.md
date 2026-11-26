# Working Mode acceptance

Open Nova Launcher Settings, NovaOS systems, then Working Mode. Start and finish a manual shift. Record a delivery status, earnings, fuel and mileage, then reopen and verify the last shift. Set a short local-time schedule and verify one foreground start from that schedule. Overnight schedules use the day on which the window begins.

## Paired vehicle

1. Pair a vehicle audio device using Android Settings. Open **Set up vehicle** in Working Mode and grant **Nearby devices** when Android prompts. Select the paired device in the protected chooser. The regular screen shows only generic connection status.
2. Leave automatic start off for the first trial. With Working Mode visible, connect the selected device and wait for Android to confirm its A2DP or Headset connection. Accept the offer or skip that connection. Connecting a different paired device should leave the shift idle.
3. Enable **Start the shift automatically** from the chooser, disconnect, then reconnect while Working Mode is visible. Confirm one shift starts with Driving Mode off. End it manually and reopen Working Mode while the same audio connection remains active. A verified disconnect is required to rearm the trigger. Changes outside the foreground are reconciled when the page returns, with uncertain results leaving the trigger idle. Removing the selected vehicle turns off this trigger.
4. Deny or revoke Nearby devices permission and verify the manual controls remain available. Switch away from Home or make another app the top-resumed window and verify the page does not activate Bluetooth or location features in the background.

The selected device identifier and handled state use NovaOS's encrypted local settings. The protected chooser reads already-paired Android devices. It has no discovery, pairing, or scanning. Android audio-profile state can be unavailable on some devices, and wireless Android Auto or generic BLE connectivity does not necessarily expose either supported audio profile. Disconnecting the vehicle rearms the trigger while preserving the shift and its totals.

## Driving screen

Check from the parked vehicle or as a passenger using the Galaxy Z Fold. Enter Driving Mode manually. Deny location access once and confirm speed shows unavailable, editing controls are hidden, and Emergency phone remains visible. Grant precise foreground location and verify plausible speed in mph, declared accuracy where Android reports it, and a return to no fresh reading when updates stop. Location updates run only in foreground Driving Mode. Working Mode records no route coordinates. The shift clock ends at the configured 18-hour maximum.

Speed limit and camera observations need a verified external source. Courier apps and Shift Tracker can be configured as launch shortcuts. Shift records entered here reflect the owner's entries.
