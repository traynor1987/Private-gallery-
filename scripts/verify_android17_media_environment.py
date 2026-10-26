"""Record and require the independently controlled API37 CI decoder environment."""
import json,subprocess,sys
from pathlib import Path

def observe():
 def command(*args):return subprocess.check_output(args,text=True).strip()
 return {'sdk':command('adb','shell','getprop','ro.build.version.sdk'),
         'qemu':command('adb','shell','getprop','ro.kernel.qemu'),
         'selinux':command('adb','shell','getenforce'),
         'avcdec':command('adb','shell','getprop','ro.boot.qemu.hwcodec.avcdec'),
         'source_sha':command('git','rev-parse','HEAD')}

def capture(output):
 values=observe();out=Path(output);out.parent.mkdir(parents=True,exist_ok=True)
 out.write_text(json.dumps(values,indent=2)+'\n') # Retain observed values on failure.
 validate(values)
 return values

def validate(values,source=None):
 if source is not None and values.get('source_sha')!=source:raise ValueError('Media environment source mismatch')
 if values['sdk']!='37' or values['qemu']!='1':raise ValueError('Only disposable API37 emulator permitted')
 if values['selinux']!='Enforcing':raise ValueError('SELinux must remain Enforcing')
 if values['avcdec'] not in ('','0'):raise ValueError('Controlled platform software decoding required')
 return values
if __name__=='__main__':capture(sys.argv[1])
