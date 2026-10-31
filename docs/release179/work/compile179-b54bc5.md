# Compile diagnostic b54bc5 — not runtime approval

Exactsource b54bc5ca3e478539a755de0dbe59aa570a250e3d; push fast-development.yml run41229152294/job124619480901 failed during Gradle configuration, before Kotlin compilation. Existing broadfast/178 jobs skipped. SDK/tool installation, pinned model preparation and scoped host steps passed. No Android instrumentation or actual APK distribution check executed.

Exact failure: app/build.gradle.kts:113 `You cannot add Provider instances to the Android SourceSet API.` Existing `assets.srcDir(evidence179Assets)` supplied Provider<Directory> to legacy SourceSet API. Raw log retained /workspace/scratch/4181ccd09b2e/diagnostic179-b54bc5/job124619480901.log; authoritative run https://github.com/traynor1987/Private-gallery-/actions/runs/41229152294 .

Correction prepared: typed PrepareEvidence179ModelTask with OutputDirectory DirectoryProperty; generated assets registered through variant.sources.assets.addGeneratedSourceDirectory(taskProvider, taskOutputProperty). Keep pinned model prep/verification and preBuild dependency; no android.sourceset.disallowProvider opt-out. Official API https://developer.android.com/reference/tools/gradle-api/8.6/com/android/build/api/variant/SourceDirectories . Changed model wiring host assertions and reran eight model/preparer tests with verified cache: exit0, no skips. This host result does not establish Gradle compile success; actual configuration/compilation must be rerun on a coherent changed source batch.

No signing or release approval. Continue active export fix, queued integration/realroute fixes; batch next diagnostic after the verified cause correction, no identicalsource rerun.
