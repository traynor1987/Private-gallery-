import uk.co.traynor.privategallery.core.backup.AutomaticBackupPolicy;
public final class AutomaticBackupChecks {
 private static int checks;
 private static void expect(boolean condition){checks++;if(!condition)throw new AssertionError("Automatic backup policy violated at "+checks);}
 public static void main(String[] args){
  long now=2_000_000L;
  expect(AutomaticBackupPolicy.eligible(true,true,true,now,now,0,true,true,true,true));
  expect(!AutomaticBackupPolicy.eligible(false,true,true,now,now,0,false,true,false,true));
  expect(!AutomaticBackupPolicy.eligible(true,false,true,now,now,0,false,true,false,true));
  expect(!AutomaticBackupPolicy.eligible(true,true,false,now,now,0,false,true,false,true));
  expect(!AutomaticBackupPolicy.eligible(true,true,true,0,now,0,false,true,false,true));
  expect(!AutomaticBackupPolicy.eligible(true,true,true,now+1,now,0,false,true,false,true));
  expect(!AutomaticBackupPolicy.eligible(true,true,true,now,now,0,true,false,false,true));
  expect(!AutomaticBackupPolicy.eligible(true,true,true,now,now,0,false,true,true,false));
  expect(AutomaticBackupPolicy.eligible(true,true,true,now,now,0,false,false,false,false));
  expect(!AutomaticBackupPolicy.eligible(true,true,true,now,now,now-899_999,false,true,false,true));
  expect(AutomaticBackupPolicy.eligible(true,true,true,now,now,now-900_000,false,true,false,true));
  expect(!AutomaticBackupPolicy.eligible(true,true,true,now,now,now+1,false,true,false,true));
  System.out.println("PASS: "+checks+" automatic Primary foreground/authority/constraint/retry checks");
 }
}
