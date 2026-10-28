#!/usr/bin/env python3
"""Disposable CI VPN-only synthetic origin/DNS. Never use real gallery/user data."""
import argparse, base64, hashlib, http.server, json, select, socket, socketserver, struct, threading, time
from pathlib import Path
PNG = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a0uoAAAAASUVORK5CYII=')
PAGE = b'''<!doctype html><title>Hidden VPN fixture</title><h1>VPN fixture ready</h1>
<button id="post" onclick="fetch('/post',{method:'POST',body:'fixture-post-body'}).then(r=>r.text()).then(t=>{document.getElementById('result').textContent=t;document.title='POST_OK'})">Run JavaScript POST</button>
<div id="result"></div><form method="POST" action="/form-result"><input name="value" value="fixture-form-body"><button>Submit form</button></form>
<form method="POST" action="/uploaded" enctype="multipart/form-data"><input type="file" name="photo" accept=".png"><button>Upload media</button></form>
<a href="/media.png">Open fixture image</a><button onclick="alert('Fixture alert')">Alert</button>
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
    if pos+4!=len(packet): raise ValueError('unexpected DNS payload')
    kind,cls=struct.unpack_from('!HH',packet,pos)
    if cls!=1: raise ValueError('IN only')
    return '.'.join(labels).lower(),kind,pos+4

def fixture_answer(packet):
    name,kind,end=question(packet)
    if name not in ('fixture.invalid','other.fixture.invalid'): return None
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
    def run(self,dns_ip,forward_dns):
        fixture=self
        class Origin(http.server.BaseHTTPRequestHandler):
            def log_message(self,*args): pass
            def reply(self,body,status=200,mime='text/html',headers=()):
                self.send_response(status);self.send_header('Content-Type',mime);self.send_header('Content-Length',str(len(body)))
                for key,value in headers:self.send_header(key,value)
                self.end_headers();self.wfile.write(body)
            def do_GET(self):
                if self.path=='/evidence':
                    with fixture.lock:events=list(fixture.events)
                    return self.reply(json.dumps(dict(events=events)).encode(),mime='application/json')
                fixture.record(kind='http',source=self.client_address[0],method='GET',path=self.path)
                if self.path=='/redirect':return self.reply(b'',302,headers=[('Location','/')])
                if self.path=='/media.png':return self.reply(PNG,mime='image/png')
                if self.path=='/cookie':return self.reply(b'cookie set',headers=[('Set-Cookie','hiddenfixture=only; SameSite=Lax')])
                if self.path=='/cookie-echo':return self.reply(self.headers.get('Cookie','').encode())
                if self.path=='/slow':
                    fixture.record(kind='slow-start',source=self.client_address[0])
                    deadline=time.monotonic()+20
                    while time.monotonic()<deadline:
                        readable,_,_=select.select([self.connection],[],[],.2)
                        if readable and self.connection.recv(1,socket.MSG_PEEK)==b'':
                            fixture.record(kind='slow-closed',source=self.client_address[0]);return
                    return self.reply(b'slow complete')
                return self.reply(PAGE)
            def do_POST(self):
                length=int(self.headers.get('Content-Length','0'))
                if length<1 or length>1024*1024:return self.reply(b'rejected',413)
                body=self.rfile.read(length)
                fixture.record(kind='http',source=self.client_address[0],method='POST',path=self.path,length=len(body),sha256=hashlib.sha256(body).hexdigest(),javascript_body=body==b'fixture-post-body',form_body=b'fixture-form-body' in body,multipart_png=b'\x89PNG\r\n\x1a\n' in body,content_type=self.headers.get('Content-Type',''))
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
                        if kind==28:response=packet[:2]+struct.pack('!HHHHH',0x8180,1,0,0,0)+packet[12:]
                        else:
                            with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as upstream:
                                upstream.settimeout(5);upstream.sendto(packet,(forward_dns,53));response=upstream.recv(4096)
                    sock.sendto(response,self.client_address)
                except (ValueError,IndexError,UnicodeError,OSError):return
        httpd=http.server.ThreadingHTTPServer(('203.0.113.10',80),Origin)
        dns=socketserver.ThreadingUDPServer((dns_ip,53),DNS)
        threading.Thread(target=dns.serve_forever,daemon=True).start()
        (self.folder/'ready').write_text('ready\n')
        httpd.serve_forever()

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--evidence',required=True);parser.add_argument('--dns-ip',default='10.91.0.1');parser.add_argument('--forward-dns',required=True)
    args=parser.parse_args();Fixture(args.evidence).run(args.dns_ip,args.forward_dns)
