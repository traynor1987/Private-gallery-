package uk.co.traynor.privategallery.core.backup
import org.junit.Assert.*
import org.junit.Test
class BackupPolicyTest {
 @Test fun onlyAuditedNamespacesAreEligible(){assertTrue(BackupPolicy.allowed("nova_arcade_v1"));assertTrue(BackupPolicy.allowed("nova_calculator_v1"));assertFalse(BackupPolicy.allowed("nova_calculator_secret_v1"));assertFalse(BackupPolicy.allowed("credentials"));assertFalse(BackupPolicy.allowed("browser"))}
 @Test fun ownerMismatchFailsBeforeAnyRestore(){assertThrows(IllegalArgumentException::class.java){BackupPolicy.requireOwner("hidden","primary")};BackupPolicy.requireOwner("primary","primary")}
 @Test fun includedMediaDescriptionNeverDisclosesAnotherSpace(){
  val primary=BackupPolicy.mediaEditDescription("primary");assertEquals("crop/undo edits",primary);assertFalse(primary.contains("hidden",ignoreCase=true))
  assertEquals("edited media copies",BackupPolicy.mediaEditDescription("hidden"))
  assertThrows(IllegalArgumentException::class.java){BackupPolicy.mediaEditDescription("unknown")}
 }
 @Test fun schedulesHaveNoUnattendedAuthority(){assertEquals("Needs unlock",BackupPolicy.scheduledStatus());assertFalse(BackupPolicy.canSchedule("hidden"));assertTrue(BackupPolicy.canSchedule("primary"))}
}
