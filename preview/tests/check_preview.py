import json,pathlib,xml.etree.ElementTree as ET,hashlib
root=pathlib.Path(__file__).resolve().parents[1]
data=json.loads((root/'app/src/main/assets/catalogue-v1.json').read_text())
assert data['schemaVersion']==1 and len(data['entries'])==50
assert len({x['id'] for x in data['entries']})==50
assert len({x['url'] for x in data['entries']})==50
for e in data['entries']:
 assert e['url'].startswith('https://') and e['verifiedDate']=='2026-11-04'
 assert e['classification']=='Website Shortcut' and e['verificationSource'].startswith('https://')
 if e.get('iconAsset'):
  p=root/'app/src/main/assets'/e['iconAsset'];assert p.exists()
  assert hashlib.sha256(p.read_bytes()).hexdigest()==e['iconSha256']
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
ns='{http://schemas.android.com/apk/res/android}'
assert [p.get(ns+'name') for p in manifest.findall('uses-permission')]==['android.permission.INTERNET']
assert manifest.find('application').get(ns+'allowBackup')=='false'
assert not manifest.findall('.//service') and not manifest.findall('.//provider')
build=(root/'app/build.gradle.kts').read_text()
assert 'applicationId = "uk.co.traynor.novaos.preview"' in build
assert 'minSdk = 37' in build and 'targetSdk = 37' in build
for p in (root/'app/src/main/java').rglob('*'):
 if p.is_file():
  s=p.read_text();assert 'uk.co.traynor.privategallery' not in s, p
  assert 'addJavascriptInterface' not in s and 'VpnService' not in s
print('50 catalogue destinations, icon hashes, preview manifest/package and source boundary checks passed')
