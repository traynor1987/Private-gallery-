"""Require original-key production upgrade/restart before publishing one patch APK."""
import hashlib,json,sys
from pathlib import Path
from release_policy import load_approved
CERT='94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17'
def verify(root,source,apk):
    root=Path(root);scope,_=load_approved()
    summary=json.loads((root/'patch-summary.json').read_text())
    if summary.get('fresh_result')!='PASS' or summary.get('source_sha')!=source or summary.get('debug_upgrade_restart')!='PASS':raise ValueError('Patch admission not verified')
    if sorted(summary.get('fresh_executed_methods',[]))!=scope['required_methods']:raise ValueError('Fresh methods absent/substituted')
    if summary.get('historical_full_verdict')!='FAILED':raise ValueError('Historical failure misrepresented')
    if (root/'upgrade-candidate-source-sha.txt').read_text().strip()!=source:raise ValueError('Production upgrade source mismatch')
    if (root/'upgrade-baseline-source-sha.txt').read_text().strip()!=scope['baseline_source']:raise ValueError('Wrong original baseline')
    digest=hashlib.sha256(Path(apk).read_bytes()).hexdigest()
    if (root/'upgrade-candidate-sha256.txt').read_text().split()[0]!=digest:raise ValueError('Upgraded APK differs from deliverable')
    for phase in ('seed','verify','after-restart'):
        if 'OK (1 test)' not in (root/('upgrade-'+phase+'.txt')).read_text():raise ValueError('Production upgrade phase missing: '+phase)
    if 'PASS:' not in (root/'upgrade-result.txt').read_text():raise ValueError('Production upgrade verdict absent')
    for phase,code in [('baseline',41),('candidate',42)]:
        if 'Signer #1 certificate SHA-256 digest: '+CERT not in (root/('upgrade-'+phase+'-signature.txt')).read_text():raise ValueError('Original signing certificate mismatch')
        if "package: name='uk.co.traynor.privategallery' versionCode='"+str(code)+"'" not in (root/('upgrade-'+phase+'-package.txt')).read_text():raise ValueError('Wrong production package/code')
    package=(root/'upgrade-candidate-package.txt').read_text()
    if "versionName='1.7.6.1'" not in package or "sdkVersion:'37'" not in package or "targetSdkVersion:'37'" not in package or 'application-debuggable' in package:raise ValueError('Wrong release APK metadata')
    return {'source_sha':source,'production_apk_sha256':digest,'original_certificate_sha256':CERT,'original_key_upgrade_restart':'PASS','patch_gate':'PASS','historical_full_verdict':'FAILED','owner_acceptance':'NOT_PERFORMED'}
if __name__=='__main__':print(json.dumps(verify(*sys.argv[1:]),indent=2))
