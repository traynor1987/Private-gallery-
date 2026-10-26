"""Verify the installed compile SDK's actual metadata, not its directory label alone."""
from pathlib import Path
import hashlib,json,sys

def verify(root):
 p=Path(root)/'platforms/android-37.1/source.properties'
 data=p.read_bytes();props={}
 for line in data.decode().splitlines():
  if '=' in line and not line.lstrip().startswith('#'):
   k,v=line.split('=',1);props[k.strip()]=v.strip()
 if props.get('AndroidVersion.ApiLevel')!='37':raise ValueError('Installed compile platform is not API37')
 if props.get('AndroidVersion.CodeName','REL') not in ('','REL'):raise ValueError('Preview platform cannot satisfy release API37')
 return {'api_level':37,'platform':'android-37.1','metadata_sha256':hashlib.sha256(data).hexdigest(),'preview':False}
if __name__=='__main__':
 result=verify(sys.argv[1]);out=Path(sys.argv[2]);out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
