import concurrent.futures,urllib.request,urllib.parse,io,json,pathlib,hashlib,re
from PIL import Image
from html.parser import HTMLParser
root=pathlib.Path(__file__).resolve().parents[1]/'app/src/main/assets';p=root/'catalogue-v1.json';data=json.loads(p.read_text())
class Links(HTMLParser):
 def __init__(self):super().__init__();self.links=[]
 def handle_starttag(self,t,a):
  a=dict(a)
  if t=='link' and any(x in a.get('rel','').lower() for x in ['manifest','icon']):self.links.append(a)
allowed={'www.gstatic.com','www.youtube.com','music.youtube.com','s.ytimg.com','www.redditstatic.com','abs.twimg.com','static.whatsapp.net','web.whatsapp.com','cdn.discordapp.com','cdn.oaistatic.com'}
def get(url,base):
 u=urllib.parse.urlparse(url);b=urllib.parse.urlparse(base)
 if u.scheme!='https' or u.username or u.port not in (None,443) or not (u.hostname==b.hostname or u.hostname in allowed):raise ValueError('unreviewed icon host')
 class Redirect(urllib.request.HTTPRedirectHandler):
  def redirect_request(self,req,fp,code,msg,headers,newurl):
   v=urllib.parse.urlparse(newurl)
   if v.scheme!='https' or not(v.hostname==b.hostname or v.hostname in allowed):raise ValueError('unreviewed redirect')
   return super().redirect_request(req,fp,code,msg,headers,newurl)
 with urllib.request.build_opener(Redirect()).open(urllib.request.Request(url,headers={'User-Agent':'NovaOS-Preview-Catalogue-Verification/0.2'}),timeout=9) as r:
  raw=r.read(2_000_001)
  if len(raw)>2_000_000:raise ValueError('too large')
  return raw,r.url
manual={'youtube':['https://www.youtube.com/s/desktop/12d6b690/img/favicon_144x144.png','https://www.youtube.com/s/desktop/12d6b690/img/favicon_96x96.png'],'youtube-music':['https://music.youtube.com/img/on_platform_logo_dark.svg','https://music.youtube.com/img/favicon_144.png'],'github':['https://github.com/apple-touch-icon.png'],'wikipedia':['https://www.wikipedia.org/static/apple-touch/wikipedia.png']}
def process(e):
 candidates=[];manifest=False
 try:
  raw,url=get(e['url'],e['url']);h=Links();h.feed(raw.decode('utf-8','ignore'))
  for l in h.links:
   href=urllib.parse.urljoin(url,l.get('href',''))
   if 'manifest' in l.get('rel',''):
    try:
     raw,mu=get(href,e['url']);m=json.loads(raw)
     for i in m.get('icons',[]):candidates.append((urllib.parse.urljoin(mu,i['src']),'manifest'))
    except Exception:pass
   else:candidates.append((href,'website link'))
 except Exception:pass
 candidates += [(u,'official candidate') for u in manual.get(e['id'],[])]+[(e.get('iconSource',e['url']),'favicon largest embedded frame')]
 best=None
 for u,source in candidates[:14]:
  try:
   raw,final=get(u,e['url']);im=Image.open(io.BytesIO(raw))
   if im.format=='ICO':im=im.ico.getimage(max(im.ico.sizes(),key=lambda z:z[0]*z[1]))
   if im.width>2048 or im.height>2048 or min(im.size)<96:continue
   if not .5<=im.width/im.height<=2:continue
   im.load()
   if best is None or min(im.size)>min(best[0].size):best=(im.copy(),u,final,source)
   if min(im.size)>=512:break
  except Exception:pass
 if best:
  im,u,final,source=best;im=im.convert('RGBA');im.thumbnail((512,512));target=root/'icons'/f"{e['id']}.png";temp=target.with_suffix(".tmp");im.save(temp,format="PNG",optimize=True);temp.replace(target)
  e.update(iconAsset='icons/'+e['id']+'.png',iconSha256=hashlib.sha256(target.read_bytes()).hexdigest(),iconSizeBytes=target.stat().st_size,iconVerifiedSource=u,iconResolvedUrl=final,iconWidth=im.width,iconHeight=im.height,iconVerificationStatus=f'HTTPS official {source}; bounded decode; PNG reencoded; 2026-11-04')
  return e['id'],im.size
 # Don't upscale the tiny old image: show Nova's native initials fallback.
 e.pop('iconAsset',None);e.update(iconVerificationStatus='No suitable >=96px official icon retrieved; NovaOS vector/text fallback',iconWidth=0,iconHeight=0)
 return e['id'],'fallback'
with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
 for result in pool.map(process,data['entries']):print(result,flush=True)
temp=p.with_suffix('.tmp');temp.write_text(json.dumps(data,indent=2)+'\n');temp.replace(p)
