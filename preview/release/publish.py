#!/usr/bin/env python3
"""Publish only the verified experimental preview, then download it publicly."""
import hashlib,json,os,pathlib,re,subprocess,time,urllib.request
REPO='traynor1987/Private-gallery-';TAG='novaos-home2-store-preview-0.8'
ROOT=pathlib.Path('preview/release-out');APK=ROOT/'NovaOS-Home2-Store-Preview.apk'
def api(path,method='GET',data=None,missing_ok=False):
 args=['gh','api','repos/'+REPO+'/'+path]
 if data is not None:
  payload=ROOT/'request.json';payload.write_text(json.dumps(data));args+=['--method',method,'--input',str(payload)]
 p=subprocess.run(args,capture_output=True,text=True)
 if p.returncode:
  if missing_ok and '404' in p.stderr:return None
  raise RuntimeError(p.stderr.strip())
 return json.loads(p.stdout) if p.stdout.strip() else None

def main():
 assert os.environ['GITHUB_REPOSITORY']==REPO
 source=os.environ['GITHUB_SHA'];assert re.fullmatch('[a-f0-9]{40}',source)
 verification=json.loads((ROOT/'APK-VERIFICATION.json').read_text());expected=verification['apkSha256']
 assert hashlib.sha256(APK.read_bytes()).hexdigest()==expected
 body=pathlib.Path('preview/release/NOTES.md').read_text()+'\nFocused Android runtime result: **'+verification['runtimeVerification']+'** (synthetic fixtures; no personal Google account tested).\n'+'\nVerified publication/source commit: `'+source+'`\n'
 release=api('releases/tags/'+TAG,missing_ok=True)
 if release is None:
  existing_tag=api('git/ref/tags/'+TAG,missing_ok=True)
  assert existing_tag is None or (existing_tag['object']['type']=='commit' and existing_tag['object']['sha']==source),'Existing tag points at another source; refusing publication'
  release=api('releases','POST',{'tag_name':TAG,'target_commitish':source,'name':'NovaOS Home 2.0 + Nova Store Preview 0.8 — EXPERIMENTAL','body':body,'draft':True,'prerelease':True,'make_latest':'false'})
 else:
  assert release['prerelease'] and 'preview-chat-inspector-code10' in release['body'],'Refusing to change an unrelated or production release'
 if release['draft']:
  subprocess.run(['gh','release','upload',TAG,str(APK),str(ROOT/'SHA256SUMS.txt'),str(ROOT/'APK-VERIFICATION.json'),'--repo',REPO,'--clobber'],check=True)
  release=api('releases/'+str(release['id']))
  asset=next(a for a in release['assets'] if a['name']==APK.name)
  assert asset['size']==APK.stat().st_size and asset.get('digest')=='sha256:'+expected,'Uploaded asset digest mismatch'
  release=api('releases/'+str(release['id']),'PATCH',{'draft':False,'prerelease':True,'make_latest':'false'})
 asset=next(a for a in release['assets'] if a['name']==APK.name)
 assert release['prerelease'] and not release['draft']
 assert asset['size']==APK.stat().st_size and asset.get('digest')=='sha256:'+expected,'Existing asset differs; not overwriting a published APK'
 url=asset['browser_download_url'];assert url=='https://github.com/'+REPO+'/releases/download/'+TAG+'/'+APK.name
 last=None
 for attempt in range(6):
  try:
   digest=hashlib.sha256();count=0
   with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'NovaOS-Preview-Delivery-Verification'}),timeout=60) as r:
    assert r.status==200
    while True:
     data=r.read(1024*1024)
     if not data:break
     count+=len(data);digest.update(data)
   assert digest.hexdigest()==expected and count==APK.stat().st_size
   last=None;break
  except Exception as e:last=e;time.sleep(5)
 if last is not None:raise last
 result={'releaseUrl':release['html_url'],'apkUrl':url,'sourceCommit':source,'artifactSourceCommit':verification['artifactSourceCommit'],'apkSha256':expected,'certificateSha256':verification['developmentCertificateSha256'],'bytes':APK.stat().st_size,'prerelease':True,'publicDownloadVerified':True}
 (ROOT/'RELEASE-PUBLICATION.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
if __name__=='__main__':main()
