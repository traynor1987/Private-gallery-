package uk.co.traynor.privategallery.core.backup

internal object BackupPolicy {
 val preferences=setOf("nova_preview_v1","nova_mind_v1","nova_shell_v1","nova_widgets_v1","nova_arcade_v1","nova_calculator_v1")
 fun allowed(name:String)=name in preferences
 fun requireOwner(actual:String,expected:String){require(actual==expected){"This archive is not eligible for the current Space"}}
 fun mediaEditDescription(owner:String)=when(owner){"primary"->"crop/undo edits";"hidden"->"edited media copies";else->throw IllegalArgumentException("Unsupported Space")}
 fun scheduledStatus()="Needs unlock"
 fun canSchedule(owner:String)=owner=="primary"
 val unsupported="Excluded: Evidence records/reports (immutable audit chains need a lineage-preserving recovery adapter), web-app sessions and other browser profiles, non-note widget configuration, browser data, credentials, native security/authentication settings. Saved-app URL queries/fragments are omitted; the built-in Store catalogue is supplied by the app. Gallery sort/retention remain destination choices; an unset favourite is restored only with explicit confirmation."
}
