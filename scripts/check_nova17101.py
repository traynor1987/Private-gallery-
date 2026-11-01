#!/usr/bin/env python3
"""Verify approved artwork identity, packaged icon geometry and unchanged Android identities."""
from pathlib import Path
import hashlib,json,re,struct,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent.parent
manifest=json.loads((root/'artwork/novaos-icon-manifest.json').read_text())
assert hashlib.sha256((root/'artwork/novaos-approved-icon.png').read_bytes()).hexdigest()==manifest['source_sha256']
build=(root/'app/build.gradle.kts').read_text()
assert 'applicationId = "uk.co.traynor.privategallery"' in build
assert 'versionCode = 53' in build and 'versionName = "1.7.10.1"' in build
assert 'minSdk = 37' in build and 'targetSdk = 37' in build
assert 'resValue("string", "app_name", "NovaOS")' in build
res=root/'app/src/main/res'
def dimensions(path):
 raw=path.read_bytes();assert raw[:8]==b'\x89PNG\r\n\x1a\n';return struct.unpack('>II',raw[16:24])
for density,pixels,scale in [('mdpi',48,1),('hdpi',72,1.5),('xhdpi',96,2),('xxhdpi',144,3),('xxxhdpi',192,4)]:
 for name in ('ic_launcher.png','ic_launcher_round.png'):assert dimensions(res/f'mipmap-{density}'/name)==(pixels,pixels)
 canvas=round(108*scale);assert dimensions(res/f'drawable-{density}/private_gallery_launcher_artwork.png')==(canvas,canvas)
assert dimensions(res/'drawable-nodpi/nova_identity.png')==(256,256)
android='{http://schemas.android.com/apk/res/android}'
for name in ('ic_launcher.xml','ic_launcher_round.xml'):
 icon=ET.parse(res/'mipmap-anydpi-v33'/name).getroot();assert icon.tag=='adaptive-icon'
 assert icon.find('monochrome').get(android+'drawable')=='@drawable/ic_private_gallery_monochrome'
assert ET.parse(res/'drawable/ic_private_gallery_monochrome.xml').getroot().tag=='vector'
print('PASS: approved ringed N source, adaptive/legacy/monochrome assets, NovaOS code53 identity, API37 and unchanged package')
