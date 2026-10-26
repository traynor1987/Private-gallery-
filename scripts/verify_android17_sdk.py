"""Verify the installed compile SDK's actual metadata, not its directory label alone."""
from pathlib import Path
import hashlib,json,sys

def verify(root):
 p=Path(root)/'platforms/android-37.1/source.properties'
 data=p.read_bytes();props={}
 for line in data.decode().splitlines():
  if '=' in line and not line.lstrip().startswith('#'):
   k,v=line.split('=',1);props[k.strip()]=v.strip()
 reported=props.get('AndroidVersion.ApiLevel')
 if reported not in ('37','37.1'):raise ValueError('Installed compile platform is not Android17/API37')
 if props.get('Platform.Version','17').split('.')[0]!='17':raise ValueError('Installed platform release is not Android17')
 if props.get('AndroidVersion.PreviewSdkInt','0')!='0':raise ValueError('Preview SDK cannot satisfy release API37')
 if props.get('AndroidVersion.CodeName','REL') not in ('','REL'):raise ValueError('Preview platform cannot satisfy release API37')
 return {'api_level':37,'reported_api_level':reported,'minor_api_level':1 if reported=='37.1' else 0,'platform':'android-37.1','metadata_sha256':hashlib.sha256(data).hexdigest(),'preview':False}
if __name__=='__main__':
 out=Path(sys.argv[2]);out.parent.mkdir(parents=True,exist_ok=True)
 metadata=Path(sys.argv[1])/'platforms/android-37.1/source.properties'
 if metadata.exists():(out.parent/'android17-sdk-source.properties').write_bytes(metadata.read_bytes())
 result=verify(sys.argv[1]);out.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
