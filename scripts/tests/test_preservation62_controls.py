"""Exercise the actual exact-delta gate against disposable Git histories, not source substrings."""
import ast,hashlib,subprocess,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
source=ast.parse((ROOT/'scripts/verify_v1_preservation1711.py').read_text())
function=next(n for n in source.body if isinstance(n,ast.FunctionDef) and n.name=='exact_delta')
namespace=dict(subprocess=subprocess,hashlib=hashlib);exec(compile(ast.Module(body=[function],type_ignores=[]),'<actual preservation gate>','exec'),namespace)
exact_delta=namespace['exact_delta']
class PreservationControls(unittest.TestCase):
 def test_only_exact_complete_reviewed_committed_and_working_delta_passes(self):
  for mutation in ('none','working','committed','untracked','ignored','extra','delete','rename','wrong-status','wrong-hash'):
   with self.subTest(mutation=mutation),tempfile.TemporaryDirectory() as tmp:
    root=Path(tmp)
    def git(*args):return subprocess.check_output(['git',*args],cwd=root,stderr=subprocess.DEVNULL).decode().strip()
    git('init','-q');git('config','user.name','Synthetic');git('config','user.email','synthetic@example.invalid')
    (root/'vault').mkdir();(root/'vault/kept.kt').write_text('old unchanged');(root/'vault/edited.kt').write_text('old')
    git('add','.');git('commit','-qm','baseline');base=git('rev-parse','HEAD')
    (root/'vault/edited.kt').write_text('reviewed');(root/'vault/added.kt').write_text('addition');git('add','.');git('commit','-qm','reviewed')
    allowed={'vault/edited.kt':('M',hashlib.sha256(b'reviewed').hexdigest()),'vault/added.kt':('A',hashlib.sha256(b'addition').hexdigest())}
    if mutation in ('working','committed'):(root/'vault/edited.kt').write_text('unreviewed')
    if mutation in ('untracked','ignored','extra'):(root/'vault/extra.kt').write_text('unexpected')
    if mutation=='ignored':(root/'.gitignore').write_text('vault/extra.kt\n')
    if mutation=='delete':(root/'vault/kept.kt').unlink()
    if mutation=='rename':(root/'vault/added.kt').rename(root/'vault/renamed.kt')
    if mutation in ('committed','extra','delete','rename'):git('add','-A');git('commit','-qm','unreviewed')
    if mutation=='wrong-status':allowed['vault/added.kt']=('M',allowed['vault/added.kt'][1])
    if mutation=='wrong-hash':allowed['vault/edited.kt']=('M','0'*64)
    if mutation=='none':exact_delta(root,base,'vault',allowed)
    else:
     with self.assertRaises((AssertionError,FileNotFoundError)):exact_delta(root,base,'vault',allowed)
if __name__=='__main__':unittest.main()
