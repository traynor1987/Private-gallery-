"""Synthetic HTTPS site-owned state/opener fixtures. Never accepts real OAuth credentials."""
import json, ssl, time, secrets, threading, socketserver
from urllib.parse import urlparse, parse_qs
from http.cookies import SimpleCookie, CookieError

def cookie(handler,name):
    try:
        parsed=SimpleCookie();parsed.load(handler.headers.get('Cookie',''));value=parsed.get(name)
        return value.value if value else None
    except CookieError:return None



def arm_gate(fixture):
    with fixture.lock:
        previous=getattr(fixture,'continuity_gate',None)
        if previous and not previous['release'].is_set():raise ValueError('Previous gate requires explicit cleanup')
        token=secrets.token_hex(32)
        fixture.continuity_gate=dict(held=False,release=threading.Event(),token=token)
        return token


def release_gate(fixture,token):
    with fixture.lock:
        gate=getattr(fixture,'continuity_gate',None)
        if not gate or not secrets.compare_digest(gate['token'],token):return False
        gate['release'].set()
        return True


def start_gate_control(fixture,port=17103):
    """Test orchestration only: loopback release capability, no origin/browser data or routing."""
    with fixture.lock:
        existing=getattr(fixture,'continuity_gate_control',None)
        if existing:return existing
        class Control(socketserver.StreamRequestHandler):
            def handle(self):
                self.connection.settimeout(2)
                try:
                    line=self.rfile.readline(80)
                    valid=len(line)==73 and line.startswith(b'RELEASE ') and line.endswith(b'\n')
                    token=line[8:-1].decode('ascii') if valid else ''
                    valid=valid and all(c in '0123456789abcdef' for c in token)
                    released=valid and release_gate(fixture,token)
                    self.wfile.write(b'RELEASED\n' if released else b'DENIED\n')
                except (OSError,UnicodeError):return
        class Server(socketserver.ThreadingTCPServer):
            daemon_threads=True
        server=Server(('127.0.0.1',port),Control)
        fixture.continuity_gate_control=server
        threading.Thread(target=server.serve_forever,daemon=True).start()
        return server


def before_verify_dns(fixture,name):
    if name!='example.com':return
    with fixture.lock:
        gate=getattr(fixture,'continuity_gate',None)
        if gate and gate['release'].is_set():gate=None
        first=bool(gate and not gate['held'])
        if gate:gate['held']=True
    if gate:
        if first:fixture.record(kind='continuity-verification-held',valid=True)
        gate['release'].wait(30)


def get(handler, fixture):
    path=urlparse(handler.path).path
    if path=='/continuity17103/gate':
        action=parse_qs(urlparse(handler.path).query).get('action',['status'])[0]
        token=None
        if action=='arm':
            start_gate_control(fixture)
            try:token=arm_gate(fixture)
            except ValueError:handler.reply(b'Gate cleanup required',409);return True
        elif action!='status':handler.reply(b'Unsupported gate action',405);return True
        with fixture.lock:
            gate=getattr(fixture,'continuity_gate',None)
            held=bool(gate and gate['held'] and not gate['release'].is_set())
        response=dict(held=held,server_time=time.time())
        if token:response['cleanup_token']=token
        handler.reply(json.dumps(response).encode(),mime='application/json');return True
    if not path.startswith('/continuity17103/'):return False
    if path=='/continuity17103/evidence':
        with fixture.lock:events=list(fixture.events)
        handler.reply(json.dumps(dict(events=events,server_time=time.time())).encode(),mime='application/json');return True
    phase=path.rsplit('-',1)[-1]
    if phase not in ('a','b'):handler.reply(b'invalid',400);return True
    tls=isinstance(handler.connection,ssl.SSLSocket)
    role=path.rsplit('/',1)[-1].rsplit('-',1)[0]
    if role in ('callback','begin','probe','workerprobe'):
        fixture.record(kind='continuity-wrong-method',phase=phase,valid=False,tls=tls)
        handler.reply(b'METHOD_NOT_ALLOWED',405,mime='text/plain');return True
    if role not in ('site','provider','session','worker'):handler.reply(b'not found',404);return True
    if '/session-' in path:
        token=getattr(fixture,'continuity_sessions',{}).get(phase)
        valid=bool(token and tls and handler.headers.get('Host')=='site-'+phase+'.fixture.invalid' and cookie(handler,'continuity_session17103')==token)
        fixture.record(kind='continuity-session',phase=phase,valid=valid,tls=tls)
        handler.reply(b'WEBSITE_SESSION_OK' if valid else b'REJECTED',200 if valid else 403,mime='text/plain');return True
    if '/worker-' in path:
        handler.reply(("onmessage=()=>{if(!self.probing)self.probing=setInterval(()=>fetch('/continuity17103/workerprobe-PHASE',{method:'POST',body:'probe'}).catch(()=>{}),200)}".replace('PHASE',phase)).encode(),mime='application/javascript');return True
    fixture.record(kind='continuity-document',phase=phase,role='provider' if '/provider-' in path else 'site',tls=tls)
    if '/provider-' in path:
        page='''<meta name="viewport" content="width=device-width,initial-scale=1"><h1>Approval fixture PHASE</h1>
<button onclick="opener.postMessage({state:location.hash.slice(1),approved:true},'https://site-PHASE.fixture.invalid');window.close()">Approve synthetic login</button>
<button onclick="opener.postMessage({state:location.hash.slice(1),approved:false},'https://site-PHASE.fixture.invalid');window.close()">Cancel synthetic login</button>'''
    else:
        page='''<meta name="viewport" content="width=device-width,initial-scale=1"><h1>Website fixture PHASE</h1><p id="status">Preparing state</p>
<script>const state=crypto.randomUUID(),documentId=crypto.randomUUID();let approved=false;
fetch('/continuity17103/begin-PHASE',{method:'POST',body:state+':'+documentId}).then(()=>statusNode().textContent='SITE_READY');
function statusNode(){return document.getElementById('status')}
addEventListener('message',e=>{if(e.origin!=='https://provider-PHASE.fixture.invalid'||!e.data||e.data.state!==state){statusNode().textContent='FOREIGN_REJECTED';fetch('/continuity17103/rejected-PHASE',{method:'POST',body:e.origin!=='https://provider-PHASE.fixture.invalid'?'origin':'state'}).catch(()=>{});return}approved=e.data.approved;statusNode().textContent=approved?'APPROVAL_RECEIVED':'APPROVAL_CANCELLED'});
function finish(){if(!approved)return;fetch('/continuity17103/callback-PHASE',{method:'POST',body:state+':'+documentId}).then(r=>r.ok?fetch('/continuity17103/session-PHASE').then(s=>s.text()):Promise.resolve('REJECTED')).then(t=>statusNode().textContent=t)}
</script><button onclick="window.open('https://provider-PHASE.fixture.invalid/continuity17103/provider-PHASE#'+state)">Open synthetic approval</button>
<button onclick="finish()">Finish website session</button>
<button onclick="postMessage({state:'foreign',approved:true},location.origin)">Reject foreign callback</button>
<button onclick="navigator.serviceWorker.register('/continuity17103/worker-PHASE').then(()=>navigator.serviceWorker.ready).then(r=>r.active.postMessage('start'));if(!window.probes)window.probes=setInterval(()=>{fetch('/continuity17103/probe-PHASE',{method:'POST',body:'probe'}).catch(()=>{});const p=new RTCPeerConnection({iceServers:[{urls:'stun:continuous-phase.fixture.invalid:3478'}]});p.createDataChannel('continuity');p.createOffer().then(o=>p.setLocalDescription(o));setTimeout(()=>p.close(),1000)},200)">Start continuity probes</button>'''
    handler.reply(page.replace('PHASE',phase).encode(),headers=[('Set-Cookie','continuity17103='+phase+'; Secure; HttpOnly; SameSite=Lax')]);return True


def post(handler, fixture):
    path=urlparse(handler.path).path
    if not path.startswith('/continuity17103/'):return False
    phase=path.rsplit('-',1)[-1]
    length=int(handler.headers.get('Content-Length','0'))
    if phase not in ('a','b') or not 0<length<256:handler.reply(b'invalid',400);return True
    body=handler.rfile.read(length).decode('ascii')
    tls=isinstance(handler.connection,ssl.SSLSocket)
    if not hasattr(fixture,'continuity_states'):fixture.continuity_states={}
    valid=False;session_cookie=None
    with fixture.lock:
        if '/begin-' in path:
            valid=tls and handler.headers.get('Host')=='site-'+phase+'.fixture.invalid'
            if valid:fixture.continuity_states[phase]=body
        elif '/rejected-' in path:
            # A synthetic diagnostic only: no supplied origin/state/token is logged.
            valid=tls and handler.headers.get('Host')=='site-'+phase+'.fixture.invalid' and cookie(handler,'continuity17103')==phase and body in ('origin','state')
        elif '/callback-' in path:
            valid=handler.headers.get('Host')=='site-'+phase+'.fixture.invalid' and fixture.continuity_states.get(phase)==body and cookie(handler,'continuity17103')==phase and tls
            if valid:
                del fixture.continuity_states[phase]
                if not hasattr(fixture,'continuity_sessions'):fixture.continuity_sessions={}
                session_cookie=secrets.token_hex(24);fixture.continuity_sessions[phase]=session_cookie
        elif '/probe-' in path or '/workerprobe-' in path:valid=body=='probe' and tls
    fixture.record(kind='continuity-'+path.split('/')[-1].rsplit('-',1)[0],phase=phase,valid=valid,tls=tls)
    handler.reply(b'CALLBACK_ACCEPTED' if valid else b'REJECTED',200 if valid else 403,mime='text/plain',headers=[('Set-Cookie','continuity_session17103='+session_cookie+'; Secure; HttpOnly; SameSite=Strict; Path=/continuity17103/')] if session_cookie else []);return True
