import sys,struct,zipfile,hashlib,json
from cryptography import x509
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import padding
b=open(sys.argv[1],'rb').read()
u16=lambda x,o:struct.unpack_from('<H',x,o)[0]
u32=lambda x,o:struct.unpack_from('<I',x,o)[0]
u64=lambda x,o:struct.unpack_from('<Q',x,o)[0]
def lp(x,o):
 n=u32(x,o);return x[o+4:o+4+n],o+4+n
with zipfile.ZipFile(sys.argv[1]) as z:m=z.read('AndroidManifest.xml')
o=8;strings=[];attrs={}
while o<len(m):
 typ=u16(m,o);size=u32(m,o+4)
 assert size>=8
 if typ==1:
  count=u32(m,o+8);flags=u32(m,o+16);start=u32(m,o+20)
  def length(p):
   n=m[p];return (((n&127)<<8)|m[p+1],p+2) if n&128 else (n,p+1)
  for n in range(count):
   p=o+start+u32(m,o+28+4*n)
   if flags&256:
    _,p=length(p);ln,p=length(p);strings.append(m[p:p+ln].decode('utf-8'))
   else:
    ln=u16(m,p);p+=2
    if ln&32768:ln=((ln&32767)<<16)|u16(m,p);p+=2
    strings.append(m[p:p+2*ln].decode('utf-16le'))
 elif typ==0x102:
  name=strings[u32(m,o+20)]
  if name in ('manifest','application'):
   start=u16(m,o+24);stride=u16(m,o+26);count=u16(m,o+28)
   for n in range(count):
    p=o+16+start+n*stride;an=strings[u32(m,p+4)];raw=u32(m,p+8);dt=m[p+15];data=u32(m,p+16)
    if an in ('package','versionName','versionCode','debuggable'):
     attrs[an]=strings[raw] if raw!=0xffffffff else strings[data] if dt==3 else bool(data) if dt==18 else data
 o+=size
assert attrs['package']=='uk.co.traynor.privategallery.hiddenprototype',attrs
end=b.rfind(b'PK\x05\x06');assert end>0
cd=u32(b,end+16);assert b[cd-16:cd]==b'APK Sig Block 42'
blocksize=u64(b,cd-24);start=cd-blocksize-8;assert u64(b,start)==blocksize
p=start+8;v2=None
while p<cd-24:
 n=u64(b,p);ident=u32(b,p+8)
 if ident==0x7109871a:v2=b[p+12:p+8+n]
 p+=8+n
assert v2,'Missing v2 signature'
signers,_=lp(v2,0);signer,_=lp(signers,0);signed,p=lp(signer,0);sigs,p=lp(signer,p);public,p=lp(signer,p)
digests,p=lp(signed,0);certs,p=lp(signed,p);certder,_=lp(certs,0);cert=x509.load_der_x509_certificate(certder)
assert cert.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)==public
sigrec,_=lp(sigs,0);algo=u32(sigrec,0);sig,_=lp(sigrec,4)
digestrec,_=lp(digests,0);assert u32(digestrec,0)==algo
expected,_=lp(digestrec,4)
if algo in (0x101,0x103):h=hashes.SHA256();hf=hashlib.sha256
elif algo in (0x102,0x104):h=hashes.SHA512();hf=hashlib.sha512
else:raise ValueError('Unsupported signature algorithm '+hex(algo))
pad=padding.PSS(mgf=padding.MGF1(h),salt_length=h.digest_size) if algo in (0x101,0x102) else padding.PKCS1v15()
cert.public_key().verify(sig,signed,pad,h)
eocd=bytearray(b[end:]);struct.pack_into('<I',eocd,16,start)
chunks=[]
for section in (b[:start],b[cd:end],eocd):
 for o in range(0,len(section),1024*1024):
  part=section[o:o+1024*1024];chunks.append(hf(b'\xa5'+struct.pack('<I',len(part))+part).digest())
actual=hf(b'\x5a'+struct.pack('<I',len(chunks))+b''.join(chunks)).digest()
assert actual==expected,'APK content digest mismatch'
print(json.dumps({'manifest':attrs,'apk_sha256':hashlib.sha256(b).hexdigest(),'apk_bytes':len(b),'v2_signature_verified':True,'certificate_subject':cert.subject.rfc4514_string(),'certificate_sha256':cert.fingerprint(hashes.SHA256()).hex()},indent=2))
