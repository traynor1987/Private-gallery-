import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class CheckedCommand(unittest.TestCase):
    def run_case(self, mode):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ("app/build/outputs/apk/debug/app-debug.apk",
                         "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
                file = root / name
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_bytes(b"synthetic immutable fixture APK")
            binary = root / "bin"
            binary.mkdir()
            adb = binary / "adb"
            adb.write_text('''#!/usr/bin/env python3
import os,sys
from pathlib import Path
args=" ".join(sys.argv[1:])
with open("adb-calls.txt","a") as log: log.write(args+"\\n")
if args=="shell getprop ro.kernel.qemu": print("0" if os.environ["MODE"]=="physical" else "1")
elif args=="shell getprop ro.build.version.sdk": print("37")
elif args=="shell dumpsys window":
 print("mCurrentFocus=Application Not Responding: com.google.android.apps.nexuslauncher" if not Path("recovered").exists() and os.environ["MODE"]!="app" else "mCurrentFocus=NovaOS")
elif args=="shell am force-stop com.google.android.apps.nexuslauncher": Path("recovered").touch()
elif args not in ("shell input keyevent KEYCODE_WAKEUP","shell wm dismiss-keyguard"): sys.exit(9)
''')
            adb.chmod(0o755)
            command = root / "instrumentation"
            command.write_text('''#!/usr/bin/env python3
import os,sys
from pathlib import Path
counter=Path("attempts"); count=int(counter.read_text())+1 if counter.exists() else 1; counter.write_text(str(count))
if count==2 and os.environ["MODE"]=="changed": Path("app/build/outputs/apk/debug/app-debug.apk").write_bytes(b"changed artifact")
sys.exit(23 if count==1 or os.environ["MODE"] in ("app","again","physical") else 0)
''')
            command.chmod(0o755)
            env = dict(os.environ, GITHUB_ACTIONS="true", MODE=mode, PATH=str(binary)+":"+os.environ["PATH"])
            result = subprocess.run(["bash", str(ROOT / "scripts/run_android17_checked_command.sh"),
                                     "evidence", "--", str(command)], cwd=root, env=env, capture_output=True, text=True)
            return result.returncode, int((root / "attempts").read_text()), (root / "adb-calls.txt").read_text() if (root / "adb-calls.txt").exists() else ""

    def test_known_external_anr_retries_one_unchanged_command(self):
        status, attempts, calls = self.run_case("external")
        self.assertEqual((0, 2), (status, attempts))
        self.assertIn("force-stop com.google.android.apps.nexuslauncher", calls)
        self.assertNotIn("clear", calls)

    def test_application_failure_is_never_retried(self):
        status, attempts, calls = self.run_case("app")
        self.assertEqual((23, 1), (status, attempts))
        self.assertNotIn("force-stop", calls)

    def test_external_recovery_is_bounded(self):
        status, attempts, _ = self.run_case("again")
        self.assertEqual((23, 2), (status, attempts))

    def test_changed_artifact_rejects_a_successful_retry(self):
        status, attempts, _ = self.run_case("changed")
        self.assertNotEqual(0, status)
        self.assertEqual(2, attempts)

    def test_physical_device_is_never_recovered(self):
        status, attempts, calls = self.run_case("physical")
        self.assertEqual((23, 1), (status, attempts))
        self.assertNotIn("force-stop", calls)


if __name__ == "__main__":
    unittest.main()
