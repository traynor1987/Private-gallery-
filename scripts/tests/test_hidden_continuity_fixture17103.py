"""Synthetic provider callbacks must produce a distinct authenticated website session."""
import io,threading,unittest
from unittest.mock import patch
from scripts import hidden_continuity_fixture17103 as endpoint
class TLS:pass
class Fixture:
 def __init__(self):self.lock=threading.Lock();self.events=[]
 def record(self,**event):self.events.append(event)
class Handler:
 def __init__(self,path,body=b'',host='site-a.fixture.invalid',cookie='',tls=True):
  self.path=path;self.headers={'Content-Length':str(len(body)),'Host':host,'Cookie':cookie};self.rfile=io.BytesIO(body);self.connection=TLS()if tls else object();self.response=None
 def reply(self,body,status=200,**kw):self.response=(status,body,kw)
class ContinuityEndpointTest(unittest.TestCase):
 def setUp(self):self.fixture=Fixture();self.mock=patch.object(endpoint.ssl,'SSLSocket',TLS);self.mock.start();self.addCleanup(self.mock.stop)
 def send(self,role,phase='a',body=b'state:document',**kw):
  handler=Handler('/continuity17103/'+role+'-'+phase,body,**kw);self.assertTrue(endpoint.post(handler,self.fixture));return handler.response
 def get(self,role,phase='a',**kw):
  handler=Handler('/continuity17103/'+role+'-'+phase,**kw);self.assertTrue(endpoint.get(handler,self.fixture));return handler.response
 def test_callback_requires_owned_one_use_state_tls_origin_and_preauth_cookie(self):
  self.assertEqual(200,self.send('begin')[0]);self.assertEqual(403,self.get('session')[0])
  for kw in [dict(cookie=''),dict(cookie='continuity17103=a',tls=False),dict(cookie='continuity17103=a',host='site-b.fixture.invalid'),dict(cookie='continuity17103=a',body=b'foreign')]:
   self.assertEqual(403,self.send('callback',**kw)[0])
  response=self.send('callback',cookie='continuity17103=a');self.assertEqual(200,response[0]);self.assertNotEqual(b'WEBSITE_SESSION_OK',response[1])
  cookie=response[2]['headers'][0][1];self.assertIn('Secure',cookie);self.assertIn('HttpOnly',cookie);self.assertIn('SameSite=Strict',cookie)
  self.assertEqual((200,b'WEBSITE_SESSION_OK'),self.get('session',cookie=cookie)[0:2])
  self.assertEqual(403,self.get('session',cookie=cookie,host='site-b.fixture.invalid')[0])
  self.assertEqual(403,self.get('session',cookie=cookie,tls=False)[0])
  self.assertEqual(403,self.send('callback',cookie='continuity17103=a')[0])
 def test_independent_origin_cannot_inherit_other_website_session(self):
  self.send('begin');one=self.send('callback',cookie='continuity17103=a')[2]['headers'][0][1]
  self.send('begin',phase='b',body=b'other:document',host='site-b.fixture.invalid')
  self.assertEqual(403,self.get('session',phase='b',host='site-b.fixture.invalid',cookie=one)[0])
  self.assertEqual(403,self.send('callback',phase='b',host='site-b.fixture.invalid',cookie='continuity17103=b')[0])
 def test_get_cannot_replay_one_use_post_callback(self):
  self.assertEqual(405,self.get('callback')[0])
 def test_negative_observation_is_owned_and_never_consumes_or_creates_session(self):
  self.send('begin');before=dict(self.fixture.continuity_states)
  for reason in (b'origin',b'state'):
   self.assertEqual(200,self.send('rejected',body=reason,cookie='continuity17103=a')[0])
  for kw in (dict(host='site-b.fixture.invalid'),dict(tls=False),dict(cookie=''),dict(body=b'secret-token')):
   args=dict(body=b'origin',cookie='continuity17103=a');args.update(kw)
   self.assertEqual(403,self.send('rejected',**args)[0])
  self.assertEqual(before,self.fixture.continuity_states)
  self.assertFalse(getattr(self.fixture,'continuity_sessions',{}))
  self.assertEqual(200,self.send('callback',cookie='continuity17103=a')[0])
