"""Fail before full regression unless Android17 focused security evidence is complete."""
from pathlib import Path
import sys
from verify_release175_evidence import count_reports
from verify_professional_ui_results import verify as verify_named
from verify_release1761_platforms import SECURITY_REQUIRED

def verify(root,source):
 root=Path(root)
 if (root/'source-sha.txt').read_text().strip()!=source:raise ValueError('Focused source mismatch')
 if (root/'sdk.txt').read_text().strip()!='37':raise ValueError('Focused SDK mismatch')
 if (root/'android-release.txt').read_text().strip().split('.')[0]!='17':raise ValueError('Focused Android release mismatch')
 count_reports(root/'results',101)
 verify_named(root/'results',SECURITY_REQUIRED)
 logs='\n'.join(p.read_text(errors='replace') for p in (root/'results').rglob('logcat-*HiddenTorBootstrapTest-*.txt'))
 if 'phase=subscribed' not in logs or 'readerAlive=false' not in logs or 'readerAlive=true' in logs:raise ValueError('Focused Tor observer did not terminate')
 return 'PASS: exact source/API37,101 passing cases,named Tor controls,observer termination'
if __name__=='__main__':print(verify(*sys.argv[1:]))
