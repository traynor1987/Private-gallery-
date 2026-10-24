"""The emulator action executes each script line as its own sh -c command."""
import subprocess
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def validate(script):
 for line in script.splitlines():
  if line.strip():subprocess.run(['sh','-n','-c',line],check=True,capture_output=True)
class ScriptLinesTest(unittest.TestCase):
 def test_rejects_split_multiline_if(self):
  with self.assertRaises(subprocess.CalledProcessError):validate('if true; then\necho yes\nfi')
 def test_all_android_emulator_lines_are_complete_shell_commands(self):
  for name in ('android.yml','launcher-acceptance.yml','release172-acceptance.yml'):
   lines=(ROOT/'.github/workflows'/name).read_text().splitlines()
   # These workflows use literal script blocks (plus a single-line bash call).
   # Check every script command exactly as the action's line splitter sees it.
   for index,line in enumerate(lines):
    if not line.startswith('          script:'):continue
    value=line.split('script:',1)[1].strip()
    if value=='|':
     block=[]
     for following in lines[index+1:]:
      if following and not following.startswith('            '):break
      block.append(following[12:])
     validate('\n'.join(block))
    else:validate(value)
if __name__=='__main__':unittest.main()
