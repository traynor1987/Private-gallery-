#!/usr/bin/env python3
"""Verify the actual preview APK before publishing; fail closed on safety mismatch."""
import argparse,hashlib,json,pathlib,re,struct,subprocess,zipfile
PRODUCTION_CERT='94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17'
PACKAGE='uk.co.traynor.novaos.preview'
def run(*args):return subprocess.check_output(args,text=True,stderr=subprocess.STDOUT)
def verify(apk,aapt,apksigner,zipalign,source,output):
 apk=pathlib.Path(apk);out=pathlib.Path(output);out.mkdir(parents=True,exist_ok=True)
 badging=run(aapt,'dump','badging',str(apk));signature=run(apksigner,'verify','--verbose','--print-certs',str(apk));manifest=run(aapt,'dump','xmltree',str(apk),'AndroidManifest.xml')
 assert "package: name='"+PACKAGE+"'" in badging,'Wrong application ID'
 assert "sdkVersion:'37'" in badging and "targetSdkVersion:'37'" in badging,'Wrong Android compatibility'
 assert "versionCode='6'" in badging and "versionName='0.6-preview'" in badging,'Unexpected preview version'
 assert 'application-debuggable' in badging,'Expected a development build'
 assert 'Android Debug' in signature and re.search(r'APK Signature Scheme v2\): true',signature),'Development APK signature not verified'
 certs=re.findall(r'certificate SHA-256 digest: ([a-fA-F0-9]{64})',signature)
 assert len(certs)==1 and certs[0].lower()=='81cda5b72cb76940fa50972bc1c284b25e641b88814d2b9041c7111cb1d042d8' and certs[0].lower()!=PRODUCTION_CERT,'Wrong or production signing certificate'
 permissions=re.findall(r"^uses-permission: name='([^']+)'",badging,re.M)
 assert set(permissions)=={'android.permission.INTERNET',PACKAGE+'.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'},permissions
 assert 'sharedUserId' not in manifest and 'uk.co.traynor.privategallery' not in manifest and 'VpnService' not in manifest,'Production/shared identity or VPN component'
 assert re.search(r'android:allowBackup[^\n]*0x0',manifest),'Backup must remain disabled'
 run(zipalign,'-c','-P','16','4',str(apk))
 with zipfile.ZipFile(apk) as z:
  libs=[n for n in z.namelist() if n.endswith('.so')]
  assert libs and all(pathlib.PurePosixPath(n).name=='libandroidx.graphics.path.so' for n in libs),libs
  for n in z.namelist():
   if re.fullmatch(r'classes\d*\.dex',n):assert b'Luk/co/traynor/privategallery/' not in z.read(n),'Production classes included'
  arm=z.read('lib/arm64-v8a/libandroidx.graphics.path.so');assert arm[:6]==b'\x7fELF\x02\x01'
  offset=struct.unpack_from('<Q',arm,32)[0];size,count=struct.unpack_from('<HH',arm,54)
  for i in range(count):
   ph=struct.unpack_from('<IIQQQQQQ',arm,offset+i*size)
   if ph[0]==1:assert ph[7]>=16384 and ph[2]%16384==ph[3]%16384,'ARM64 native alignment unsafe'
 result={'packageId':PACKAGE,'versionName':'0.6-preview','versionCode':6,'minSdk':37,'targetSdk':37,'sourceCommit':source,'artifactSourceCommit':source,'bytes':apk.stat().st_size,'apkSha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'developmentCertificateSha256':certs[0].lower(),'permissions':permissions,'packageIsolation':'verified distinct application ID; no shared UID or production classes/components','nativeAlignment':'16KB ZIP and ARM64 ELF verified','runtimeVerification':__import__('os').environ.get('PREVIEW_RUNTIME_RESULT','Not run in this host verification; see workflow runtime receipt'),'experimental':True}
 (out/'APK-VERIFICATION.json').write_text(json.dumps(result,indent=2)+'\n');(out/'package.txt').write_text(badging);(out/'signature.txt').write_text(signature);(out/'SHA256SUMS.txt').write_text(result['apkSha256']+'  '+apk.name+'\n')
 return result
if __name__=='__main__':
 p=argparse.ArgumentParser()
 for name in ['apk','aapt','apksigner','zipalign','source','output']:p.add_argument('--'+name,required=True)
 args=p.parse_args();print(json.dumps(verify(args.apk,args.aapt,args.apksigner,args.zipalign,args.source,args.output),indent=2))
