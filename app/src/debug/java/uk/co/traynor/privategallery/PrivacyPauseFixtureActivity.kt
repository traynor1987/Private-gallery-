package uk.co.traynor.privategallery

/** Same-process, debug-only floating window whose lifecycle the instrumentation can finish directly. */
class PrivacyPauseFixtureActivity:android.app.Activity() {
    override fun onCreate(state:android.os.Bundle?) {
        super.onCreate(state)
        setContentView(android.widget.TextView(this).apply {text="Permission-style fixture"})
    }
}
