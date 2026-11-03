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

if __name__=='__main__':unittest.main()
