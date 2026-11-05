"""Reject the documented dotted-API android-0 AVD misconfiguration before boot."""
import os,pathlib,json

def validate(text):
 values=dict(line.split('=',1) for line in text.splitlines() if '=' in line)
 if values.get('target')!='android-37.0':raise ValueError('API37.0 AVD target must be android-37.0; old avdmanager can silently write android-0')
 return values['target']
if __name__=='__main__':
 root=pathlib.Path(os.environ.get('ANDROID_AVD_HOME',str(pathlib.Path.home()/'.android/avd')))
 target=validate((root/'test.ini').read_text());p=pathlib.Path('preview/artifacts');p.mkdir(exist_ok=True);(p/'avd-target.json').write_text(json.dumps({'target':target,'validatedBeforeBoot':True}));print('Verified AVD target:',target)
