import threading
import unittest
from types import SimpleNamespace
from scripts.hidden_continuity_fixture17103 import before_verify_dns

class ReadinessBarrierTests(unittest.TestCase):
    def test_only_readiness_dns_waits_and_release_does_not_forge_reply(self):
        reached=threading.Event()
        gate=dict(held=False,release=threading.Event())
        fixture=SimpleNamespace(lock=threading.Lock(),continuity_gate=gate,record=lambda **event: reached.set())
        before_verify_dns(fixture,'site-a.fixture.invalid')
        self.assertFalse(gate['held'])
        worker=threading.Thread(target=before_verify_dns,args=(fixture,'example.com'))
        worker.start()
        try:
            self.assertTrue(reached.wait(1))
            self.assertTrue(gate['held'])
            self.assertTrue(worker.is_alive())
            # Retries cannot slip through the same barrier.
            retry=threading.Thread(target=before_verify_dns,args=(fixture,'example.com'))
            retry.start()
            self.assertTrue(retry.is_alive())
        finally:
            gate['release'].set()
            worker.join(1)
            if 'retry' in locals():retry.join(1)
        self.assertFalse(worker.is_alive())
        self.assertFalse(retry.is_alive())


class GateCleanupTests(unittest.TestCase):
    def setUp(self):
        from scripts import hidden_continuity_fixture17103 as endpoint
        self.endpoint=endpoint
        self.fixture=SimpleNamespace(lock=threading.Lock(),record=lambda **event:None)
        self.server=endpoint.start_gate_control(self.fixture,port=0)
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)
    def release(self,token):
        import socket
        with socket.create_connection(self.server.server_address,timeout=1) as client:
            client.sendall(('RELEASE '+token+'\n').encode('ascii'))
            return client.makefile('rb').readline(32)
    def test_revoked_route_cleanup_releases_gate_and_next_readiness_query(self):
        # There is deliberately no Network/HTTP route in this fixture.
        token=self.endpoint.arm_gate(self.fixture)
        reached=threading.Event();self.fixture.record=lambda **event:reached.set()
        worker=threading.Thread(target=before_verify_dns,args=(self.fixture,'example.com'));worker.start()
        try:
            self.assertTrue(reached.wait(1));self.assertTrue(worker.is_alive())
            self.assertEqual(b'RELEASED\n',self.release(token))
            worker.join(1);self.assertFalse(worker.is_alive())
            # A later runtime's readiness must not inherit the previous wait(30).
            next_query=threading.Thread(target=before_verify_dns,args=(self.fixture,'example.com'));next_query.start();next_query.join(1)
            self.assertFalse(next_query.is_alive())
            self.assertEqual(b'RELEASED\n',self.release(token))
        finally:
            self.fixture.continuity_gate['release'].set();worker.join(1)
    def test_http_status_hides_token_and_unowned_release_is_rejected(self):
        import json
        def request(query):
            result=[]
            handler=SimpleNamespace(path='/continuity17103/gate'+query,reply=lambda body,status=200,**kw:result.append((status,body)))
            self.assertTrue(self.endpoint.get(handler,self.fixture));return result[0]
        status,body=request('?action=arm');self.assertEqual(200,status)
        token=json.loads(body)['cleanup_token'];self.assertEqual(64,len(token))
        self.assertNotIn('cleanup_token',json.loads(request('')[1]))
        self.assertEqual(405,request('?action=release')[0])
        self.assertFalse(self.fixture.continuity_gate['release'].is_set())
        self.assertEqual(b'RELEASED\n',self.release(token))
    def test_wrong_and_previous_generation_cannot_release_current_gate(self):
        first=self.endpoint.arm_gate(self.fixture)
        self.assertEqual(b'DENIED\n',self.release('0'*64));self.assertFalse(self.fixture.continuity_gate['release'].is_set())
        with self.assertRaises(ValueError):self.endpoint.arm_gate(self.fixture)
        self.assertEqual(b'RELEASED\n',self.release(first))
        second=self.endpoint.arm_gate(self.fixture);self.assertNotEqual(first,second)
        self.assertEqual(b'DENIED\n',self.release(first));self.assertFalse(self.fixture.continuity_gate['release'].is_set())
        self.assertEqual(b'RELEASED\n',self.release(second))
        self.assertEqual('127.0.0.1',self.server.server_address[0])

if __name__=='__main__':unittest.main()
