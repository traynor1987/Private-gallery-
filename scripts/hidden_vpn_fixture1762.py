#!/usr/bin/env python3
"""Disposable CI VPN-only synthetic origin/DNS. Never use real gallery/user data."""
import argparse, base64, hashlib, http.server, json, select, ssl, socket, socketserver, struct, threading, time
from pathlib import Path
PNG = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a0uoAAAAASUVORK5CYII=')
PAGE = b'''<!doctype html><title>Hidden VPN fixture</title><h1>VPN fixture ready</h1>
<button id="post" onclick="fetch('/post',{method:'POST',body:'fixture-post-body'}).then(r=>r.text()).then(t=>{document.getElementById('result').textContent=t;document.title='POST_OK'})">Run JavaScript POST</button>
<div id="result"></div><button onclick="navigator.serviceWorker.register('/worker.js').then(()=>navigator.serviceWorker.ready).then(r=>r.active.postMessage('go'))">Run worker POST</button><form method="POST" action="/form-result"><input name="value" value="fixture-form-body"><button>Submit form</button></form>
<form method="POST" action="/uploaded" enctype="multipart/form-data"><button type="button" onclick="document.getElementById('photo').click()">Choose Hidden upload</button><input id="photo" style="display:none" type="file" name="photo" accept=".png"><button>Upload media</button></form>
<button onclick="const s=new WebSocket(location.origin.replace('https:','wss:').replace('http:','ws:')+'/socket');s.onopen=()=>s.send('fixture-websocket-body');s.onmessage=e=>{document.getElementById('result').textContent=e.data;s.close()}">Run WebSocket</button>
<button onclick="const p=new RTCPeerConnection({iceServers:[{urls:'stun:stun-phase.fixture.invalid:3478'}]});p.createDataChannel('route-probe');fetch('/probe-start',{method:'POST',body:'webrtc-started'});p.onicegatheringstatechange=()=>{if(p.iceGatheringState==='complete'){p.close();fetch('/probe-complete',{method:'POST',body:'webrtc-complete'})}};p.createOffer().then(o=>p.setLocalDescription(o)).catch(()=>{p.close();fetch('/probe-error',{method:'POST',body:'webrtc-error'})})">Run RTC route probe</button>
<button onclick="if(!window.rtcLoop){fetch('/probe-continuous',{method:'POST',body:'continuous-rtc-started'}).then(()=>{window.rtcLoop=setInterval(()=>{const p=new RTCPeerConnection({iceServers:[{urls:'stun:continuous-phase.fixture.invalid:3478'}]});p.createDataChannel('continuous');p.createOffer().then(o=>p.setLocalDescription(o));setTimeout(()=>p.close(),2000)},200)})}">Start continuous RTC</button>
<button onclick="fetch('/slow').catch(()=>{})">Start slow fetch</button>
<iframe title="Dialog origin fixture" src="https://dialog-frame.fixture.invalid/frame"></iframe><a href="/media.png">Open fixture image</a><button onclick="alert('Fixture alert')">Alert</button>
<button onclick="document.getElementById('result').textContent=confirm('Fixture confirm')?'confirmed':'cancelled'">Confirm</button>
<button onclick="document.getElementById('result').textContent=prompt('Fixture prompt','default')||'cancelled'">Prompt</button>'''

def question(packet):
    if len(packet)<17 or struct.unpack_from('!H',packet,4)[0]!=1: raise ValueError('one question required')
    pos=12;labels=[]
    while True:
        length=packet[pos];pos+=1
        if length==0: break
        if length>63 or pos+length>len(packet): raise ValueError('invalid DNS label')
        labels.append(packet[pos:pos+length].decode('ascii'));pos+=length
    end=pos+4
    if end>len(packet): raise ValueError('truncated DNS question')
    additional=struct.unpack_from('!H',packet,10)[0]
    if struct.unpack_from('!HH',packet,6)!=(0,0): raise ValueError('query contains answers')
    if additional==0:
        if end!=len(packet): raise ValueError('unexpected DNS payload')
    elif additional==1:
        if end+11>len(packet) or packet[end]!=0 or struct.unpack_from('!H',packet,end+1)[0]!=41:raise ValueError('only EDNS permitted')
        if end+11+struct.unpack_from('!H',packet,end+9)[0]!=len(packet):raise ValueError('invalid EDNS payload')
    else:raise ValueError('unexpected DNS records')
    kind,cls=struct.unpack_from('!HH',packet,pos)
    if cls!=1: raise ValueError('IN only')
    return '.'.join(labels).lower(),kind,pos+4

def fixture_answer(packet):
    name,kind,end=question(packet)
    if name!='fixture.invalid' and not name.endswith('.fixture.invalid'): return None
    answer=b''
    if kind==1: answer=b'\xc0\x0c'+struct.pack('!HHIH',1,1,0,4)+socket.inet_aton('203.0.113.10')
    # IPv4-only tunnel: AAAA receives an authoritative empty answer, never a bypass address.
    return packet[:2]+struct.pack('!HHHHH',0x8180,1,bool(answer),0,0)+packet[12:end]+answer

class Fixture:
    def __init__(self,folder):
        self.folder=Path(folder);self.folder.mkdir(parents=True,exist_ok=True);self.lock=threading.Lock();self.events=[]
    def record(self,**event):
        with self.lock:
            entry=dict(time=time.time(),**event);self.events.append(entry)
            with (self.folder/'events.jsonl').open('a') as out: out.write(json.dumps(entry)+'\n')
    def run(self,dns_ip,forward_dns,tls_cert=None,tls_key=None):
        fixture=self
        class Origin(http.server.BaseHTTPRequestHandler):
            protocol_version="HTTP/1.1"
            def log_message(self,*args): pass
            def reply(self,body,status=200,mime='text/html',headers=()):
                self.send_response(status);self.send_header('Content-Type',mime);self.send_header('Content-Length',str(len(body)))
                for key,value in headers:self.send_header(key,value)
                self.end_headers();self.wfile.write(body)
            def do_GET(self):
                if self.path=='/evidence':
                    with fixture.lock:events=list(fixture.events)
                    return self.reply(json.dumps(dict(events=events)).encode(),mime='application/json')
                fixture.record(kind='http',source=self.client_address[0],method='GET',path=self.path,tls=isinstance(self.connection,ssl.SSLSocket),cookie_present='hiddenfixture=only' in self.headers.get('Cookie',''),basic_present=self.headers.get('Authorization','')=='Basic '+base64.b64encode(b'fixture-user:fixture-pass').decode())
                if self.path=='/socket':
                    self.close_connection=True
                    key=self.headers.get('Sec-WebSocket-Key','')
                    if self.headers.get('Upgrade','').lower()!='websocket' or len(base64.b64decode(key))!=16:return self.reply(b'bad websocket',400)
                    accept=base64.b64encode(hashlib.sha1((key+'258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest()).decode()
                    self.send_response(101);self.send_header('Upgrade','websocket');self.send_header('Connection','Upgrade');self.send_header('Sec-WebSocket-Accept',accept);self.end_headers()
                    header=self.rfile.read(2)
                    if len(header)!=2 or header[0]!=0x81 or header[1]&0x80==0:return
                    length=header[1]&127
                    if length==126:length=struct.unpack('!H',self.rfile.read(2))[0]
                    if length>512:return
                    mask=self.rfile.read(4);payload=self.rfile.read(length)
                    body=bytes(value^mask[i%4] for i,value in enumerate(payload))
                    fixture.record(kind='websocket',source=self.client_address[0],tls=isinstance(self.connection,ssl.SSLSocket),exact_body=body==b'fixture-websocket-body')
                    reply=b'WebSocket verified';self.wfile.write(bytes([0x81,len(reply)])+reply);self.wfile.flush();return
                if self.path=='/basic':
                    if self.headers.get('Authorization','')!='Basic '+base64.b64encode(b'fixture-user:fixture-pass').decode():return self.reply(b'Fixture sign in required',401,headers=[('WWW-Authenticate','Basic realm="Fixture website account"')])
                    return self.reply(b'Fixture website authenticated')
                if self.path=='/frame':return self.reply(b"<!doctype html><button onclick=\"alert('Frame alert body')\">Frame alert</button>")
                if self.path=='/worker.js':return self.reply(b"self.addEventListener('install',e=>e.waitUntil(self.skipWaiting()));self.addEventListener('activate',e=>e.waitUntil(self.clients.claim()));self.addEventListener('message',e=>e.waitUntil(fetch('/worker-post',{method:'POST',body:'fixture-worker-body'})));",mime='application/javascript')
                if self.path=='/redirect':return self.reply(b'',302,headers=[('Location','/')])
                if self.path=='/media.png':return self.reply(PNG,mime='image/png',headers=[('Content-Disposition','attachment; filename="vpn-fixture.png"')])
                if self.path=='/cookie':return self.reply(b'cookie set',headers=[('Set-Cookie','hiddenfixture=only; SameSite=Lax')])
                if self.path=='/cookie-echo':return self.reply(self.headers.get('Cookie','').encode())
                if self.path in ('/slow','/network-loss'):
                    fixture.record(kind='slow-start',source=self.client_address[0],path=self.path)
                    deadline=time.monotonic()+20
                    while time.monotonic()<deadline:
                        readable,_,_=select.select([self.connection],[],[],.2)
                        if readable and self.connection.recv(1)==b'':
                            fixture.record(kind='slow-closed',source=self.client_address[0],path=self.path);return
                    return self.reply(b'slow complete')
                return self.reply(PAGE)
            def do_POST(self):
                length=int(self.headers.get('Content-Length','0'))
                if length<1 or length>1024*1024:return self.reply(b'rejected',413)
                body=self.rfile.read(length)
                fixture.record(kind='http',source=self.client_address[0],method='POST',path=self.path,tls=isinstance(self.connection,ssl.SSLSocket),length=len(body),sha256=hashlib.sha256(body).hexdigest(),javascript_body=body==b'fixture-post-body',worker_body=body==b'fixture-worker-body',probe_started=body==b'webrtc-started',probe_complete=body==b'webrtc-complete',continuous_started=body==b'continuous-rtc-started',revocation_barrier=body==b'child-revoked-udp-barrier',form_body=b'fixture-form-body' in body,multipart_png=b'\x89PNG\r\n\x1a\n' in body,content_type=self.headers.get('Content-Type',''))
                if self.path.startswith('/probe-'):return self.reply(b'probe recorded',mime='text/plain')
                if self.path=='/worker-post' and body==b'fixture-worker-body':return self.reply(b'Worker POST verified',mime='text/plain')
                if self.path=='/post' and body==b'fixture-post-body':return self.reply(b'JavaScript POST verified',mime='text/plain')
                if self.path=='/form-result' and b'fixture-form-body' in body:return self.reply(b'<h1>Form POST verified</h1>')
                if self.path=='/uploaded' and b'\x89PNG\r\n\x1a\n' in body:return self.reply(b'<h1>Multipart upload verified</h1>')
                return self.reply(b'Unexpected synthetic body',400)
        class DNS(socketserver.BaseRequestHandler):
            def handle(self):
                packet,sock=self.request
                try:
                    name,kind,_=question(packet);fixture.record(kind='dns',source=self.client_address[0],name=name,qtype=kind)
                    response=fixture_answer(packet)
                    if response is None:
                        # The existing real HTTPS readiness probe must also traverse this VPN.
                        # Forward IPv4 DNS only; the IPv4-only WireGuard profile blocks IPv6.
                        if kind==28:response=packet[:2]+struct.pack('!HHHHH',0x8180,1,0,0,0)+packet[12:question(packet)[2]]
                        else:
                            with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as upstream:
                                upstream.settimeout(5);upstream.sendto(packet,(forward_dns,53));response=upstream.recv(4096)
                    sock.sendto(response,self.client_address)
                except (ValueError,IndexError,UnicodeError,OSError):return
        class STUN(socketserver.BaseRequestHandler):
            def handle(self):
                packet,sock=self.request
                if len(packet)<20 or packet[:2]!=b'\0\1' or packet[4:8]!=b'\x21\x12\xa4\x42':return
                fixture.record(kind='stun',source=self.client_address[0])
                mapped=b'\0\1'+struct.pack('!H',self.client_address[1]^0x2112)+bytes(a^b for a,b in zip(socket.inet_aton(self.client_address[0]),b'\x21\x12\xa4\x42'))
                attribute=struct.pack('!HH',0x20,len(mapped))+mapped
                response=struct.pack('!HH',0x101,len(attribute))+packet[4:20]+attribute
                sock.sendto(response,self.client_address)
        stun=socketserver.ThreadingUDPServer(('203.0.113.10',3478),STUN)
        threading.Thread(target=stun.serve_forever,daemon=True).start()
        httpd=http.server.ThreadingHTTPServer(('203.0.113.10',80),Origin)
        if tls_cert and tls_key:
            httpsd=http.server.ThreadingHTTPServer(('203.0.113.10',443),Origin)
            tls=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);tls.load_cert_chain(tls_cert,tls_key)
            httpsd.socket=tls.wrap_socket(httpsd.socket,server_side=True)
            threading.Thread(target=httpsd.serve_forever,daemon=True).start()
        dns=socketserver.ThreadingUDPServer((dns_ip,53),DNS)
        threading.Thread(target=dns.serve_forever,daemon=True).start()
        (self.folder/'ready').write_text('ready\n')
        httpd.serve_forever()

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--evidence',required=True);parser.add_argument('--dns-ip',default='10.91.0.1');parser.add_argument('--forward-dns',required=True);parser.add_argument('--tls-cert');parser.add_argument('--tls-key')
    args=parser.parse_args();Fixture(args.evidence).run(args.dns_ip,args.forward_dns,args.tls_cert,args.tls_key)
