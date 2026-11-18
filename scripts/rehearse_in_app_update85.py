#!/usr/bin/env python3
"""Disposable API37 rehearsal of authentic Build86's existing updater and Android installer.

Only release transport is controlled; immutable baseline application code, candidate signature
checks, private FileProvider and Android's package installation remain real. No live owner data.
"""
import argparse
import hashlib
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import threading
import time
import xml.etree.ElementTree as ET

PACKAGE = 'uk.co.traynor.privategallery'
CERTIFICATE = '94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17'

def adb(*args):
    return subprocess.run(['adb', *args], check=True, capture_output=True, text=True, timeout=35).stdout

def installer_tree(output):
    """Observe a fresh installer hierarchy across Android's transient null-root handoff."""
    deadline=time.monotonic()+60
    attempts=[]
    while time.monotonic()<deadline:
        dump=""
        try:
            # uiautomator may exit zero with no file while the Activity changes.
            # Delete every previous dump; a stale hierarchy must never authorize a tap.
            adb('shell','rm','-f','/sdcard/nova85-installer.xml')
            dump=adb('shell','uiautomator','dump','--compressed','/sdcard/nova85-installer.xml')
            xml=adb('shell','cat','/sdcard/nova85-installer.xml')
            ET.fromstring(xml)
            return xml
        except (subprocess.CalledProcessError,subprocess.TimeoutExpired,ET.ParseError) as failure:
            attempts.append(dict(attempt=len(attempts)+1,result=type(failure).__name__,dump=dump.strip()))
            (output/'in-app-installer-wait.txt').write_text(json.dumps(attempts,indent=2)+'\n')
            time.sleep(1)
    # Disposable synthetic device only. Preserve why observation failed before cleanup.
    for label,command in (('window',('shell','dumpsys','window')),('activity',('shell','dumpsys','activity','activities'))):
        try:(output/('in-app-installer-'+label+'.txt')).write_text(adb(*command))
        except (subprocess.CalledProcessError,subprocess.TimeoutExpired):pass
    raise RuntimeError('Android did not expose a fresh installer hierarchy within 60 seconds; inspect in-app-installer-wait.txt')

def installed():
    match = re.search(r'versionCode=(\d+)\b',adb('shell','dumpsys','package',PACKAGE))
    return int(match[1]) if match else None

def rehearsal(apk, output, source):
    assert os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('PRODUCTION_UPGRADE')=='1'
    assert adb('shell','getprop','ro.kernel.qemu').strip()=='1'
    assert adb('shell','getprop','ro.build.version.sdk').strip()=='37'
    assert installed()==86, 'Authentic populated Build86 must already be installed'
    output.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='nova85-controlled-update-') as temporary:
        root=Path(temporary)
        digest=hashlib.file_digest(apk.open('rb'),'sha256').hexdigest()
        shutil.copyfile(apk,root/'candidate.apk')
        (root/'candidate.sha256').write_text(digest+'  private-gallery-release.apk\n')
        (root/'metadata.json').write_text(json.dumps(dict(package=PACKAGE,versionName='1.7.41',versionCode=87,certificateSha256=CERTIFICATE,apkSha256=digest)))
        prefix='https://github.com/traynor1987/Private-gallery-/releases/download/v1.7.41/'
        assets=[dict(name=name,browser_download_url=prefix+name,**({'digest':'sha256:'+digest,'size':apk.stat().st_size} if name.endswith('.apk') else {})) for name in ('private-gallery-release.apk','private-gallery-release.apk.sha256','private-gallery-release.metadata.json')]
        (root/'release.json').write_text(json.dumps(dict(tag_name='v1.7.41',draft=False,prerelease=False,body='Controlled Build87 release rehearsal',assets=assets)))
        class Handler(SimpleHTTPRequestHandler):
            def __init__(self,*args,**kwargs):super().__init__(*args,directory=str(root),**kwargs)
            def log_message(self,*args):pass
            def do_GET(self):
                if self.path not in ('/release.json','/candidate.apk','/candidate.sha256','/metadata.json'):
                    self.send_error(404);return
                super().do_GET()
        server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
        threading.Thread(target=server.serve_forever,daemon=True).start()
        port=server.server_port
        process=None
        log=output/'in-app-update-instrumentation.txt'
        try:
            adb('reverse',f'tcp:{port}',f'tcp:{port}')
            adb('shell','appops','set',PACKAGE,'REQUEST_INSTALL_PACKAGES','allow')
            with log.open('w') as transcript:
                process=subprocess.Popen(['adb','shell','am','instrument','-w','-e','baselineCode','86','-e','candidateCode','87','-e','updateFixturePort',str(port),'-e','class',PACKAGE+'.upgrade.UpgradeFixtureTest#prepareInAppUpdate',PACKAGE+'.test/androidx.test.runner.AndroidJUnitRunner'],stdout=transcript,stderr=subprocess.STDOUT)
                deadline=time.monotonic()+240
                while 'NOVA_UPDATE_HANDOFF_READY' not in log.read_text():
                    if process.poll() is not None:raise RuntimeError('Build86 in-app discovery/download/installer failed; inspect in-app-update-instrumentation.txt')
                    if time.monotonic()>deadline:raise RuntimeError('Build86 in-app update handoff timed out')
                    time.sleep(1)
                clicked=False
                for _ in range(20):
                    xml=installer_tree(output)
                    (output/'in-app-installer-ui.xml').write_text(xml)
                    for node in ET.fromstring(xml).iter('node'):
                        if node.get('package') not in ('com.android.packageinstaller','com.google.android.packageinstaller','com.android.permissioncontroller'):continue
                        if node.get('text') not in ('Update','Install') or node.get('enabled')!='true':continue
                        bounds=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.get('bounds',''))
                        if bounds:
                            x1,y1,x2,y2=map(int,bounds.groups());adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));clicked=True;break
                    if clicked:break
                    time.sleep(1)
                assert clicked, 'Android installer confirmation was not available'
                deadline=time.monotonic()+90
                while installed()!=87:
                    if time.monotonic()>deadline:raise RuntimeError('Android did not report installed Build87')
                    time.sleep(1)
                (output/'in-app-update.json').write_text(json.dumps(dict(schema=1,result='PASS',source=source,baseline_code=86,candidate_code=87,installed_code=87,apk_sha256=digest,transport='controlled-loopback',baseline_application='authentic-unmodified',discovery='PASS',download_and_verification='PASS',android_installer='confirmed',live_github_discovery='requires_postpublication_confirmation'),indent=2)+'\n')
        finally:
            if process is not None and process.poll() is None:
                process.terminate()
                try:process.wait(timeout=10)
                except subprocess.TimeoutExpired:process.kill();process.wait(timeout=10)
            adb('reverse','--remove',f'tcp:{port}')
            server.shutdown();server.server_close()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk',required=True,type=Path);parser.add_argument('--output',required=True,type=Path);parser.add_argument('--source',required=True)
    args=parser.parse_args()
    assert re.fullmatch(r'[a-f0-9]{40}',args.source)
    rehearsal(args.apk,args.output,args.source)

if __name__=='__main__':main()
