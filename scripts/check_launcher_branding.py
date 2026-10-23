#!/usr/bin/env python3
"""Verify packaged resource geometry and owner-art export reproducibility."""
from pathlib import Path
import hashlib
import subprocess
import xml.etree.ElementTree as ET
from PIL import Image
R = Path(__file__).resolve().parents[1]
RES = R / 'app/src/main/res'
A = '{http://schemas.android.com/apk/res/android}'

def check():
    app = ET.parse(R/'app/src/main/AndroidManifest.xml').getroot().find('application')
    assert app.get(A+'icon') == '@mipmap/ic_launcher'
    assert app.get(A+'roundIcon') == '@mipmap/ic_launcher_round'
    assert app.get(A+'label') == '@string/app_name'
    for version in (26,33):
        for name in ('ic_launcher','ic_launcher_round'):
            root=ET.parse(RES/f'mipmap-anydpi-v{version}/{name}.xml').getroot()
            assert root.tag == 'adaptive-icon'
            assert root.find('foreground').get(A+'drawable') == '@drawable/ic_private_gallery_foreground'
            assert root.find('background').get(A+'drawable') == '@color/private_gallery_icon_background'
            if version == 33:
                assert root.find('monochrome').get(A+'drawable') == '@drawable/ic_private_gallery_monochrome'
    files=[]
    for density,scale in {'mdpi':1,'hdpi':1.5,'xhdpi':2,'xxhdpi':3,'xxxhdpi':4}.items():
        f=RES/f'drawable-{density}/private_gallery_launcher_artwork.png'; files.append(f)
        image=Image.open(f); assert image.size == (round(108*scale),)*2
        # Nontransparent owner artwork remains centred and proportionally scaled.
        x0,y0,x1,y1=image.getbbox()
        assert abs((x0+x1)/2-image.width/2) <= 1
        assert abs((y0+y1)/2-image.height/2) <= 1
        assert abs((x1-x0)/scale-56) <= 1
        for suffix in ('','_round'):
            f=RES/f'mipmap-{density}/ic_launcher{suffix}.png'; files.append(f)
            assert Image.open(f).size == (round(48*scale),)*2
    before={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
    subprocess.run(['python3',str(R/'scripts/export_launcher_icon.py')],check=True)
    assert before == {str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
    print('PASS: launcher references, adaptive/round/themed layers, five densities, centring and deterministic owner-art exports')

if __name__=='__main__': check()
