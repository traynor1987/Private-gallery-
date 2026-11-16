# Code 81 Galaxy Z Fold acceptance

Record the candidate commit, Android 17 CI run, test package ID, Fold model, Android build, display size and font setting. Use the separately identified Code 81 test build and disposable data. Leave the installed Code 80 Build 83 and its data in place.

## Cover and inner display

Run each case on the cover and inner display at default and large font sizes. Repeat the active case while opening and closing the Fold. Record the route, expected outcome, observed outcome and any clipping or lost state.

| Case | Expected outcome |
| --- | --- |
| Open Notes, Tasks, Calendar, Files and Clipboard from the launcher and App Library | The expected screen opens in Primary Space. Primary actions and Done remain reachable with large text. Back returns to the originating surface. |
| Create a note, fold with the editor open, background and reopen | The latest draft returns without replacing a newer saved version. A protected notebook prompts for the PIN again after backgrounding. |
| Attach a disposable text file through Android's picker | The copy opens in Notes after the source is removed. Cancellation or a denied grant leaves the draft and source intact. Repeat from Files into Notes. |
| Create, edit and complete a task with a reminder | The updated time replaces the previous alarm. Completing the task cancels it. Check notification permission denied and allowed, then verify a future reminder after reboot. |
| Create a recurring Calendar event, move one occurrence and restore it | The rest of the series stays in place. Restoration removes the exception without duplicating the event. The Android calendar handoff waits for explicit confirmation. |
| Use a disposable Files directory and a disposable Clipboard snippet | Reads and edits respect the selected tree. Revoking the grant removes access. Inserting a snippet into Notes happens only when requested. |
| Create and restore an encrypted fixture backup | Notes and attachments, tasks, calendar, and Clipboard recover in the intended Space. An incorrect recovery key or interrupted restore preserves existing data. A tree selected in Files requires its own Android grant. |
| Move an app out of a launcher folder and reopen Home | A single remaining icon takes the folder's former slot without duplication. The dock and other folders remain usable on both displays. |
| Open Labs demonstrations, Guardian, Sanctuary and both vaults | Labs demonstrations still open. Guardian and Sanctuary controls remain reachable. Hidden Vault uses its separate PIN and has no launcher entry. |

Record emulator and physical results separately. An in-place upgrade from the installed original-key Build 83 requires a separately authorized candidate and an explicit data-preservation check.
