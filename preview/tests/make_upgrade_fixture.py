"""Synthetic code4 schema fixture for an isolated CI emulator, never an owner phone."""
import json,pathlib,hashlib,xml.etree.ElementTree as ET
entries=json.loads(pathlib.Path('preview/app/src/main/assets/catalogue-v1.json').read_text())['entries']
e=next(x for x in entries if x['id']=='photopea').copy();e['id']='web:'+hashlib.sha256(e['url'].encode()).hexdigest()[:24]
p=dict(installed=[e],pages=[['folder:upgrade','store'],['wallpaper']],dock=[e['id'],'settings'],folders=[dict(id='folder:upgrade',name='Upgrade keep',apps=[e['id'],'photos'])],drawer=['store',e['id']],columns=5,iconSize=64,wallpaper=dict(builtin='Dusk',zoom=1.4,x=.2,y=-.1,blur=3,dim=.25))
state=dict(schema=1,current='primary',theme='light',primary=p,hidden=p)
root=ET.Element('map');ET.SubElement(root,'string',name='state').text=json.dumps(state)
ET.ElementTree(root).write('/tmp/nova05-upgrade-prefs.xml',encoding='utf-8',xml_declaration=True)
