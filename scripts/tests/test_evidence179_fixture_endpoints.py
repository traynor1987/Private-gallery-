import hashlib
import http.server
import json
from pathlib import Path
import socket
import ssl
import tempfile
import threading
import time
import unittest
from scripts import evidence179_fixture_endpoints as endpoints
from scripts.create_hidden_vpn_fixture_tls1762 import generate

class HttpsReceiptsTest(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name);generate(self.root)
  (self.root/'evidence179-ready').write_bytes(b'unique-ready')
  root=self.root
  class Handler(http.server.BaseHTTPRequestHandler):
   def do_GET(self):endpoints.get(self,root)
   def do_POST(self):endpoints.post(self,root)
   def log_message(self,*args):pass
  self.server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler)
  tls=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);tls.load_cert_chain(self.root/'fixture.crt',self.root/'fixture.key')
  self.server.socket=tls.wrap_socket(self.server.socket,server_side=True)
  self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
 def tearDown(self):
  self.server.shutdown();self.server.server_close();self.thread.join();self.temp.cleanup()
 def connect(self,address=None):
  ctx=ssl.create_default_context(cafile=str(self.root/'fixture-ca.crt'))
  return ctx.wrap_socket(socket.create_connection(address or self.server.server_address,timeout=3),server_hostname='fixture.invalid')
 def send(self,phase='success',headers='',framing=None,address=None,case='vpn_revoke'):
  body=json.dumps(dict(case=case,nonce='a'*32,phase=phase),separators=(',',':')).encode()
  payload=f'{len(body):x}\r\n'.encode()+body+b'\r\n0\r\n\r\n' if framing is None else framing
  path='/evidence179/post' if phase=='success' else '/evidence179/slow/'+case+'/'+phase
  conn=self.connect(address);conn.sendall((f'POST {path} HTTP/1.1\r\nHost: fixture.invalid\r\nX-Evidence179-Case: '+('a'*32)+'\r\nTransfer-Encoding: chunked\r\n'+headers+'\r\n').encode()+payload)
  return conn,body
 def test_real_chunked_tls_post_has_exact_hash_and_response(self):
  conn,body=self.send()
  with conn:
   raw=b''
   while part:=conn.recv(4096):raw+=part
  self.assertTrue(raw.endswith(endpoints.RESPONSE))
  ev=endpoints.events(self.root);self.assertEqual([e['kind'] for e in ev],['post','response'])
  self.assertEqual(ev[0]['body_sha256'],hashlib.sha256(body).hexdigest());self.assertTrue(ev[0]['tls']);self.assertFalse(ev[0]['auth_present'])
 def test_real_cancel_closes_tls_stream_without_completion(self):
  conn,_=self.send('cancel');self.assertIn(b'200',conn.recv(1024));conn.close()
  deadline=time.monotonic()+5
  while time.monotonic()<deadline and not any(e['kind']=='slow-closed' for e in endpoints.events(self.root)):time.sleep(.05)
  self.assertEqual([e['kind'] for e in endpoints.events(self.root)],['post','slow-start','slow-closed'])
 def test_ambiguous_framing_and_credential_headers_never_receipted(self):
  for headers in ('Content-Length: 1\r\n','Authorization: synthetic-forbidden\r\n','Cookie: synthetic-forbidden\r\n'):
   conn,_=self.send(headers=headers)
   with conn:self.assertIn(b'400',conn.recv(1024))
  self.assertEqual(endpoints.events(self.root),[])
 def test_exact_https_ready(self):
  with self.connect() as conn:
   conn.sendall(b'GET /evidence179/ready HTTP/1.1\r\nHost: fixture.invalid\r\n\r\n');raw=b''
   while part:=conn.recv(4096):raw+=part
  self.assertTrue(raw.endswith(b'unique-ready'))

 def test_arming_rejects_stream_already_closed_and_generic_tls_is_not_peer_close(self):
  conn,_=self.send('revoke');conn.recv(1024)
  receipt=endpoints.arm(self.root,'vpn_revoke','a'*32,'revoke','d'*32)
  self.assertEqual(receipt['kind'],'action-arm')
  conn.close();deadline=time.monotonic()+5
  while time.monotonic()<deadline and not any(e['kind']=='slow-closed' for e in endpoints.events(self.root)):time.sleep(.05)
  with self.assertRaises(ValueError):endpoints.arm(self.root,'vpn_revoke','a'*32,'revoke','e'*32)
  self.assertIsNone(endpoints.peer_close(ssl.SSLError(1,'generic TLS protocol error')))
  self.assertIsNone(endpoints.peer_close(TimeoutError('local timeout')))
  self.assertEqual(endpoints.peer_close(BrokenPipeError(32,'peer closed')),'broken_pipe')
  self.assertEqual(endpoints.peer_close(ssl.SSLEOFError(8,'EOF')),'tls_eof')
 def test_admitted_tls_blackhole_local_cancel_does_not_imply_remote_close(self):
  import select
  listener=socket.socket();listener.bind(('127.0.0.1',0));listener.listen(1)
  blackhole=threading.Event();stop=threading.Event();accepted=[];sockets=[]
  def relay():
   client,_=listener.accept();upstream=socket.create_connection(self.server.server_address);accepted.append(1);sockets.extend([client,upstream])
   try:
    while not stop.is_set():
     if blackhole.is_set():time.sleep(.01);continue # Deliberately cannot deliver client FIN/RST to origin.
     readable,_,_=select.select([client,upstream],[],[],.05)
     for source in readable:
      data=source.recv(65536)
      if not data:return
      (upstream if source is client else client).sendall(data)
   finally:
    for item in sockets:
     try:item.close()
     except OSError:pass
  proxy=threading.Thread(target=relay,daemon=True);proxy.start()
  conn=None;worker=None
  try:
   conn,_=self.send('loss',address=listener.getsockname(),case='vpn_loss')
   self.assertIn(b'200',conn.recv(1024))
   arm=endpoints.arm(self.root,'vpn_loss','a'*32,'loss','d'*32)
   self.assertEqual(arm['kind'],'action-arm')
   failed=threading.Event();started=threading.Event()
   def read_active():
    started.set()
    try:
     while conn.recv(4096):pass
    except OSError:pass
    finally:failed.set()
   worker=threading.Thread(target=read_active,daemon=True);worker.start();started.wait(1)
   blackhole.set();time.sleep(.1)
   self.assertFalse(failed.is_set()) # Genuinely admitted live read at path severance.
   action=time.monotonic();conn.shutdown(socket.SHUT_RDWR);conn.close()
   self.assertTrue(failed.wait(1));self.assertLess(time.monotonic()-action,1)
   time.sleep(.6)
   observations=endpoints.events(self.root)
   self.assertEqual([e['kind'] for e in observations],['post','slow-start','action-arm'])
   self.assertEqual(len(accepted),1) # No reconnect/fallback by this controlled caller.
  finally:
   # Explicit host cleanup is separate from the unavailable remote-close observation.
   stop.set()
   for item in sockets:
    try:item.shutdown(socket.SHUT_RDWR)
    except OSError:pass
   if conn is not None:conn.close()
   listener.close();proxy.join(2)
   if worker is not None:worker.join(2)

if __name__=='__main__':unittest.main()
