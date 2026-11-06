# Nova Arcade checkpoint B evidence

Status: development checkpoint. Three complete offline native games are implemented. Native API37 execution, visual/device Fold acceptance and original-key upgrade remain pending; compilation is not native runtime acceptance.

Starting checkpoint: `f6e1b82d40985f66309d82bf72c8e7312074bfb6` (reviewed/published Backup A). No versions, signing, workflows, browser/Tor engines, release gates or existing authentication authority were changed. This writer did not push or launch a workflow.

## Delivered behavior

- Nova Arcade is in the existing built-in Home catalogue. Its library lists three installed games, recent/continue actions, per-Space scores and five earned achievements, settings, and a clearly deferred, non-playable Chess entry.
- 2048, Sudoku and Snake each have an optional Add to Home action using the existing `PreviewData.addHome` layout model. Their built-in drawer entries route into the same Arcade host. Home's existing internal recents can reopen Arcade; encrypted game recents survive restart independently in each Space.
- All game logic is pure Java with immutable states and explicit deterministic game random state. No network, account, ads, trackers, web engines or installation mechanism are added.
- 2048: standard 4×4 sliding, once-per-source-tile merging in all four directions, 90% 2 / 10% 4 placement only after a changed board, swipe/keyboard/touch arrows, one-step undo including previous random state, restart confirmation, score/best, 2048 victory and continued play, terminal detection and automatic save.
- Sudoku: Easy/Medium/Hard/Expert clue-density levels, seeded shuffled valid solutions, uniqueness-preserving clue removal with independent solver verification, pencil notes displayed in a 3×3 grid, erase, one-step undo, hints, mistake count, valid-solution completion only, timer, explicit pause/resume and automatic save. Keyboard arrows select cells; digits enter values; touch number controls and Previous/Next cell provide accessible alternatives.
- Snake: 16×16 board, swipe/keyboard/touch direction controls, five speeds, pause/resume, restart confirmation, score/best, wall/body collisions, legal movement into a vacated tail, deterministic food placement selected from actual free cells, full-board victory, and a bounded two-turn queue. Each queued turn is validated against the last queued heading; callbacks read the latest model so rapid taps cannot overwrite the queue with a stale board.
- Optional game sounds use short native `ToneGenerator` feedback, and optional haptics use native view haptic feedback. Both default off and persist independently per Space. Tone resources are released on disposal.
- Existing Home light/dark/system themes are inherited. Boards scale by available width; a wide layout places controls alongside the board, while cover and short-height layouts scroll. Buttons have descriptive text/content descriptions; cells expose positions/values/fixed/error/note state. Sound/haptic hardware behavior, font scaling and TalkBack still require native/physical acceptance.

## Persistence, authority and Backup

`ArcadeData` requires a captured `SpaceContext`; its only namespace is `nova_arcade_v1`. It stores versioned 2048/Sudoku/Snake saves, best scores, solved count, achievements, recent game IDs, sound/haptics and speed. No cross-Space global store, authentication value or framework preference is added. Existing Space encryption and lease-gated atomic publication are reused.

Each accepted move/edit/tick saves synchronously through the current capability before publishing the new UI model. Failed writes retain the last saved state and show an error. Lifecycle pause/stop, leaving the Arcade route and disposal pause Sudoku/Snake and discard Snake's queued turns. Cold reopening resumes timed games paused. Retired authority refuses later mutations; IntegratedHome's existing retirement/Panic behavior removes the surface.

The bounded versioned codec uses explicit binary fields and Base64 within encrypted preferences, never Java object deserialization. It validates board sizes/ranges, score/time/counter bounds, booleans, Sudoku clues/solution/uniqueness/current grid/notes/undo, and Snake body geometry/occupancy/heading/queue/food/score. Truncated, trailing and malformed records fail closed. Existing corrupt saves produce an error surface; they are never silently reset.

`PortablePreferenceSchema` delegates Arcade values to the same exact key/type/bounds validator and full game decoders used for local loading. This runs before Backup settings staging/overwrite and also on export. Calculator remains reserved until C installs its validator. Backup A's native host, replacement confirmation, transaction and lock-after-settings-restore are preserved.

## Executed verification

- Initial test-first host invocation recorded the missing implementation API in `/tmp/nova62-arcade-red.log`. An initial invocation also discovered `javac` was not on PATH; subsequent commands use the existing pinned JDK17.
- Boundary regression red: `/tmp/nova62-arcade-boundary-red.log` failed with `Malformed state accepted` for a running Snake save whose heading opposed its body. The validator was fixed, and the expanded rules run passed. The same review fixed terminal detection at the explicit 2048 maximum tile bound.
- Imported-counter regression red: `/tmp/nova62-arcade-import-bounds-red.log` failed when another legal move on a maximum imported score exceeded the allowed save bound. Score/mistake/hint counters now saturate at their declared limits, keeping valid restored states usable.
- `PATH=/tmp/nova59-tools/jdk-17.0.21+9/bin:$PATH bash scripts/check_arcade.sh`: **122 behavioral checks passed**. Log `/tmp/nova62-arcade-host-green.log`. Includes once-only merges/all directions/no-op randomness/seeded undo/win/loss; generated unique Sudoku at all four levels/zero and multiple solutions/notes/hints/mistakes/valid and invalid completion; Snake reversal/queue/pause/wall/body/vacated tail/safe food/full board; save roundtrips/malformed types/truncation/trailing data and imported bounds.
- `JAVA_HOME=/tmp/nova59-tools/jdk-17.0.21+9 GRADLE_USER_HOME=/tmp/nova59-tools/cache PATH=/tmp/nova59-tools/jdk-17.0.21+9/bin:$PATH bash scripts/check_home2_host.sh`: **passed**, log `/tmp/nova62-arcade-home-host.log`. The real `SpaceContext`/`VaultCipher` harness now exports/restores all three game saves and settings. Wrong-typed settings and malformed saves for each game are rejected before publication, with all existing destination ciphertext byte-for-byte unchanged. Existing Home/widget/theme/Hidden-entry assertions remain passing.
- Initial `:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`: **BUILD SUCCESSFUL in 1m58s**, `/tmp/nova62-arcade-initial-compile.log`.
- First focused `:app:testDebugUnitTest --tests '*arcade*' --tests '*PortablePreferenceSchemaTest' :app:compileDebugAndroidTestJavaWithJavac`: **BUILD SUCCESSFUL in 1m53s**, 9 tests / 2 classes, zero failures/errors, `/tmp/nova62-arcade-focused-first-green.log`. Final frozen-source results follow below; a sixth Arcade JUnit test was added for imported maximum counters.

Existing unrelated deprecation/nullability warnings remain. Only focused game/schema tests and the existing Home host checks were run for B; no broad device suite was launched.

## Pending native runtime selection

- `uk.co.traynor.privategallery.core.arcade.ArcadePersistenceTest#bothSpacesKeepIndependentEncryptedGamesRecordsAndSettingsAfterReopen`: actual Primary/Hidden encrypted preferences, independent records/recents/settings, paused cold reopen and retirement preserving ciphertext.
- `uk.co.traynor.privategallery.home2.ArcadeUiTest#catalogueShortcutLaunchesNativeGameAndAddsToExistingHomeLayout`: existing Home drawer routing, real Add to Home, 2048 input/save and shortcut relaunch.
- `uk.co.traynor.privategallery.home2.ArcadeUiTest#coverInnerLandscapeKeepBoardsAndTouchControlsReachable`: all three games at 360×720, 840×720 and 840×360 dp.
- `uk.co.traynor.privategallery.home2.ArcadeUiTest#snakeLifecyclePauseDiscardsQueuedInputAndResumeIsExplicit`: native lifecycle pause, queue clearing and explicit resume.

These fixtures are compiled, not executed in this checkpoint. The consolidated run should retain existing Home/Panic/Space/Backup coverage and add the classes above. Real fold/unfold, process restart, sound/haptic output, keyboard focus, TalkBack, large text and rendering/performance on the owner's Fold remain physical acceptance items.

## Deliberate limits and self-review

- Chess is explicitly deferred; no movement-only chess claim.
- Sudoku level labels describe decreasing clue-density targets (42/35/29/25), not a human solving-technique rating. Removal stops when further removal would break uniqueness, so some generated puzzles retain more clues than their target. Every accepted puzzle has exactly one solution.
- One-step undo is retained for 2048/Sudoku. Saves include that step. Sudoku's timer is a paused/resumed elapsed-seconds counter, not wall-clock time spent away from the game.
- Defensive bounds: 2048 tiles up to 2^29; score up to Long.MAX_VALUE/2; Sudoku mistakes/hints up to 1,000,000 and timer up to 315,360,000 seconds; saved strings at most 32,768 characters. Food selection terminates even at a full Snake board. Solver work has an explicit visit budget and rejects unverifiable imported puzzles.
- Self-review checked the narrow Home route diff, same-Space capabilities, no plaintext/on-disk save path, Backup preflight before overwrite, stale callbacks, rapid turns, single-host routing, all game termination conditions, codec structure and resource disposal. Independent review belongs to the root after this commit.
- Root's previously observed Backup schedule checkbox hydration issue is recorded in the progress ledger for the final A repair wave; B did not alter scheduling.

## Final frozen-source result

`python /tmp/nova62-backup-build.py :app:testDebugUnitTest --tests '*arcade*' --tests '*PortablePreferenceSchemaTest' :app:compileDebugAndroidTestJavaWithJavac` completed **BUILD SUCCESSFUL in 1m33s**. The two freshly written test XML files contain **10 tests / 2 classes, 0 failures, 0 errors** (6 Arcade + 4 schema); other older XML files in the directory were not counted. Log: `/tmp/nova62-arcade-final-green.log`. Application Kotlin/Java and instrumentation Kotlin/Java compilation succeeded. Native tests were not executed.

The final host rules result remains **122 checks passed**. `git diff --check` and the staged equivalent passed before commit. B implementation is ready for the root's independent review/push gate; no production or physical acceptance claim is made.

## Task2 review repair round1 — Sudoku completion credit

Independent checkpoint review `/tmp/nova62-arcade-review.md` identified one Important scoring issue: solve → Undo → re-enter the final digit could repeatedly credit the same puzzle. The game state now carries a mandatory strict Boolean `completedOnce`, separate from whether the current board is complete. Initial completion sets it; Undo, edits, pause and timer updates retain it; a new puzzle starts false. Its encoded value is validated with the complete game save, including rejection of a completed board falsely marked uncredited. The solved counter and completion achievements are awarded only on the first completion. This also prevents undoing a final hint and re-entering its value from newly claiming the no-hints achievement.

Minor M1 was repaired in the same completion path: completed Sudoku boards do not become paused on reopen, and the renderer never obscures a completed board. Undo remains available; the resulting incomplete board can be resumed normally. Minor M2 (dense engine formatting) is recorded for final triage; no broad formatting rewrite was made during this correctness repair.

Regression evidence:

- `/tmp/nova62-arcade-r1-red.log`: meaningful host assertion failed with `First completion is credited` against the initial missing-credit implementation.
- Pinned-JDK `bash scripts/check_arcade.sh`: **126 behavioral checks passed**, `/tmp/nova62-arcade-r1-host-green.log`. Added completion/undo/persist/new-puzzle credit assertions.
- Pinned-JDK/cache `bash scripts/check_home2_host.sh`: **passed**, `/tmp/nova62-arcade-r1-home-green.log`.
- Pinned-JDK/cache `python tests/home2/verify_space_preferences.py`: **passed**, `/tmp/nova62-arcade-r1-model-green.log`. This harness now compiles the actual `ArcadeData` alongside real `SpaceContext`/`VaultCipher` (with minimal Compose state-delegate fixtures, not a native UI). It executes first completion, three undo/recompletion cycles, one-time achievements, saved/reopened/restored credit, second-puzzle credit, completed-board reopen and malformed-credit Backup import preserving all destination ciphertext. The independent malformed-credit run also passed in `/tmp/nova62-arcade-r1-credit-backup-green.log`.
- `ArcadeRulesTest` adds strict credited-save validation and completed reopen coverage. `ArcadePersistenceTest` now contains the real-device counterpart, including solved counts 1 → repeated re-completion still 1 → new puzzle 2; native execution remains pending.

Final frozen-source scoped JVM/compilation result is appended below. No push, workflow, version/signing change or unrelated A/C feature edit was made.

Round1 final frozen command: `python /tmp/nova62-backup-build.py :app:testDebugUnitTest --tests '*arcade*' --tests '*PortablePreferenceSchemaTest' :app:compileDebugAndroidTestJavaWithJavac` — **BUILD SUCCESSFUL in 1m29s**, **11 tests / 2 classes, 0 failures/errors** (7 Arcade + 4 schema). Application and instrumentation Kotlin/Java compilation passed; log `/tmp/nova62-arcade-r1-final-green.log`. Existing unrelated warnings remain. Native tests were compiled, not run. Final diff/staged whitespace checks passed. Important I1 and minor M1 are ready for scoped independent re-review; M2 remains deferred.
