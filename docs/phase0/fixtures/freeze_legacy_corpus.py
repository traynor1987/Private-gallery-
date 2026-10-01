#!/usr/bin/env python3
"""One-time independent corpus authoring tool; NEVER invoked by Gradle/tests.
Synthetic public key/nonces only. Python struct + AESGCM/scrypt implements the
published baseline wire formats without importing/calling any Kotlin writer.
"""
from pathlib import Path
import struct, json, hashlib, hmac, base64, io, zipfile, sys
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

out = Path(sys.argv[1])
if any(out.iterdir()):
    raise SystemExit('Refusing to regenerate immutable corpus')
key = bytes(range(32))
image_id = '11111111-1111-4111-8111-111111111111'
video_id = '22222222-2222-4222-8222-222222222222'
collection_id = '33333333-3333-4333-8333-333333333333'
image = b'SYNTHETIC PHASE0 IMAGE\n' + bytes(range(256))
video = bytes((i * 17 + 3) % 251 for i in range(1048576 + 33))
image_nonce = bytes(range(12))
video_nonce = bytes(range(12, 24))

def integer(n): return struct.pack('>i', n)
def long(n): return struct.pack('>q', n)
def boolean(b): return bytes([bool(b)])
def utf(s):
    b = s.encode('ascii') # ASCII equals Java modified UTF-8 for fixture strings
    return struct.pack('>H', len(b)) + b

def item(ident, plain, nonce, version, video_item=False, size_override=None, state_override=None):
    is_trash = version >= 6 and video_item
    b = utf(ident) + utf('video/mp4' if video_item else 'image/jpeg') + utf('synthetic-video.mp4' if video_item else 'synthetic-image.jpg')
    b += long(1700000000001 if video_item else 1700000000000) + long(len(plain) if size_override is None else size_override)
    b += integer(32) + hashlib.sha256(plain).digest() + integer(12) + nonce
    b += integer((5 if is_trash else 3) if state_override is None else state_override) + boolean(True) + utf('content://synthetic/' + ('video' if video_item else 'image'))
    if version >= 5: b += integer(2 if video_item else 1) + boolean(video_item)
    if version >= 6: b += boolean(is_trash) + (long(1700000000999) if is_trash else b'')
    return b

def index_plain(version, duplicate=False, negative=False, state=None):
    items = item(image_id, image, image_nonce, version, size_override=-1 if negative else None, state_override=state)
    items += item(image_id if duplicate else video_id, video, video_nonce, version, True)
    b = (b'' if version == 1 else integer(-0x50470002) + integer(version)) + integer(2) + items
    if version >= 2:
        b += integer(1) + utf(collection_id) + utf('Synthetic favourites') + long(1700000000100) + integer(-1) + boolean(True) + utf(image_id)
        b += integer(2) + utf(collection_id) + utf(image_id) + long(1700000000101) + utf(collection_id) + utf(video_id) + long(1700000000102)
    if version >= 3:
        b += integer(1) + utf(image_id) + struct.pack('>ffff', .1, .2, .8, .9) + boolean(True) + struct.pack('>ffff', 0., 0., 1., 1.)
    if version >= 4: b += boolean(True) + utf(collection_id)
    return b

def encrypted_index(plain, salt=30):
    nonce = bytes(range(salt, salt+12))
    return nonce + AESGCM(key).encrypt(nonce, plain, b'private-gallery:index:v1')

def save(name, data):
    p = out / name; p.parent.mkdir(parents=True, exist_ok=True); p.write_bytes(data)

for v in range(1,7): save(f'index-v{v}.enc', encrypted_index(index_plain(v), 30+v))
save(f'payloads/{image_id}.vault', AESGCM(key).encrypt(image_nonce, image, image_id.encode()))
header_unsigned = b'PGVIDEO1' + integer(1) + integer(1048576) + long(len(video)) + video_nonce
mac_key = hmac.new(key, b'private-gallery:video-header:v1', 'sha256').digest()
header = header_unsigned + hmac.new(mac_key, video_id.encode() + header_unsigned, 'sha256').digest()
framed = header
for i, start in enumerate(range(0, len(video), 1048576)):
    chunk = video[start:start+1048576]; nonce = bytes(range(70+i, 82+i))
    aad = utf('private-gallery:video-chunk:v1') + utf(video_id) + integer(i) + integer(len(chunk))
    framed += nonce + AESGCM(key).encrypt(nonce, chunk, aad)
save(f'payloads/{video_id}.vault', framed)
pin = '735209'; recovery = base64.urlsafe_b64encode(bytes(range(32,64))).decode().rstrip('=')
def envelope(secret, start):
    salt = bytes(range(start,start+16)); nonce = bytes(range(start+16,start+28))
    wrapping = hashlib.scrypt(secret.encode(), salt=salt, n=32768, r=8, p=1, dklen=32, maxmem=67108864)
    ciphertext = AESGCM(wrapping).encrypt(nonce, key, None)
    return salt, nonce, ciphertext
pin_env = envelope(pin,100); recovery_env = envelope(recovery,140)
save('pin-envelope.bin', b''.join(pin_env)); save('recovery-envelope.bin', b''.join(recovery_env))
expected = dict(provenance='Synthetic independent Python struct/AESGCM fixture; not owner data', keyHex=key.hex(), pin=pin, recovery=recovery,
    imageId=image_id, videoId=video_id, collectionId=collection_id,
    imageSize=len(image), videoSize=len(video), imageSha256=hashlib.sha256(image).hexdigest(), videoSha256=hashlib.sha256(video).hexdigest(),
    imageNonceHex=image_nonce.hex(), videoNonceHex=video_nonce.hex(), importedAt=1700000000000, deletedAt=1700000000999,
    expectedEntries=['vault-index.enc', f'payloads/{image_id}.vault', f'payloads/{video_id}.vault', 'manifest.json'])
save('expected.json', (json.dumps(expected, indent=2, sort_keys=True)+'\n').encode())
archive_entries = {'vault-index.enc':(out/'index-v6.enc').read_bytes(), f'payloads/{image_id}.vault':(out/f'payloads/{image_id}.vault').read_bytes(), f'payloads/{video_id}.vault':framed}
manifest = dict(version=1, hashes={n:hashlib.sha256(b).hexdigest() for n,b in archive_entries.items()}, recovery={n:base64.b64encode(b).decode() for n,b in zip(['salt','nonce','ciphertext'],recovery_env)})
archive_entries['manifest.json'] = json.dumps(manifest, separators=(',',':')).encode()
def archive(entries):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, 'w', compression=zipfile.ZIP_STORED) as zip:
        for name, content in entries:
            info = zipfile.ZipInfo(name, (2026,9,29,0,0,0)); info.compress_type=zipfile.ZIP_STORED
            zip.writestr(info, content)
    return stream.getvalue()
save('backup-v1.pgvault', archive(archive_entries.items()))
# Authenticated parser corruption proves validation beyond GCM/tag rejection.
for name, b in {'legacy-duplicate':index_plain(1,duplicate=True), 'legacy-negative-size':index_plain(1,negative=True),
    'legacy-trailing':index_plain(1)+b'\x01', 'v6-trailing':index_plain(6)+b'\x01', 'v6-negative-size':index_plain(6,negative=True),
    'legacy-trash-without-date':index_plain(1,state=5), 'unknown-version':integer(-0x50470002)+integer(7),
    'negative-count':integer(-1), 'excessive-count':integer(100001)}.items(): save(f'corrupt/{name}.enc',encrypted_index(b))
original=(out/'index-v6.enc').read_bytes(); altered=bytearray(original); altered[-1]^=1
save('corrupt/index-tag.enc',altered); save('corrupt/index-truncated.enc',original[:18])
save('corrupt/archive-truncated.pgvault',(out/'backup-v1.pgvault').read_bytes()[:120])
save('corrupt/archive-duplicate.pgvault',archive(list(archive_entries.items())+[('vault-index.enc',archive_entries['vault-index.enc'])]))
bad = dict(archive_entries); bad[f'payloads/{video_id}.vault']=framed[:-1]
save('corrupt/archive-payload-truncated.pgvault',archive(bad.items()))
bad = dict(archive_entries); x=bytearray(framed); x[-1]^=1; bad[f'payloads/{video_id}.vault']=bytes(x)
# Recompute unauthenticated manifest digest: payload authentication must still reject.
m = json.loads(bad['manifest.json']); m['hashes'][f'payloads/{video_id}.vault']=hashlib.sha256(bytes(x)).hexdigest(); bad['manifest.json']=json.dumps(m,separators=(',',':')).encode()
save('corrupt/archive-video-tag-rehashed.pgvault',archive(bad.items()))
paths=sorted(p for p in out.rglob('*') if p.is_file())
save('SHA256SUMS', ''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.relative_to(out).as_posix()}\n' for p in paths).encode())
print(f'Frozen {len(paths)} independently authored files')
