"""Public DEBUG-only synthetic TLS identity; never an APK signing credential."""
from pathlib import Path
import socket
import ssl
import subprocess
import tempfile
import threading
import unittest
from cryptography import x509
from cryptography.hazmat.primitives import serialization

ROOT=Path(__file__).resolve().parents[2]
SCRIPT=ROOT/'scripts/create_hidden_vpn_fixture_tls1762.py'
HOST='a'*56+'.onion'
class Tls179(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.path=Path(self.tmp.name)
    def prepare(self,*args):
        return subprocess.run(['python3',str(SCRIPT),str(self.path),*args],capture_output=True,text=True)
    def test_existing_default_leaf_and_debug_ca_key_preserved(self):
        self.assertEqual(self.prepare().returncode,0)
        cert=x509.load_pem_x509_certificate((self.path/'fixture.crt').read_bytes())
        self.assertEqual(cert.extensions.get_extension_for_class(x509.SubjectAlternativeName).value.get_values_for_type(x509.DNSName),['fixture.invalid','*.fixture.invalid'])
        ca=x509.load_pem_x509_certificate((self.path/'fixture-ca.crt').read_bytes())
        anchor=x509.load_pem_x509_certificate((ROOT/'app/src/debug/res/raw/hidden_vpn_fixture_ca.crt').read_bytes())
        encode=lambda key:key.public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
        self.assertEqual(encode(ca.public_key()),encode(anchor.public_key()))
    def test_exact_onion_leaf_hostname_verified_and_untrusted_ca_refused(self):
        self.assertEqual(self.prepare('--onion-host',HOST).returncode,0)
        cert=x509.load_pem_x509_certificate((self.path/'fixture.crt').read_bytes())
        self.assertEqual(cert.extensions.get_extension_for_class(x509.SubjectAlternativeName).value.get_values_for_type(x509.DNSName),[HOST])
        def connect(host,trusted):
            server=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);server.load_cert_chain(self.path/'fixture.crt',self.path/'fixture.key')
            listener=socket.socket();listener.bind(('127.0.0.1',0));listener.listen();listener.settimeout(3)
            failures=[]
            def serve():
                try:
                    raw,_=listener.accept();raw.settimeout(3)
                    with raw:
                        with server.wrap_socket(raw,server_side=True) as tls:tls.sendall(b'ok')
                except ssl.SSLError:pass # expected client trust/hostname rejection
                except Exception as error:failures.append(error)
            worker=threading.Thread(target=serve,daemon=True);worker.start()
            try:
                client=ssl.create_default_context(cafile=str(self.path/'fixture-ca.crt')) if trusted else ssl.create_default_context()
                with socket.create_connection(listener.getsockname(),timeout=3) as raw:
                    with client.wrap_socket(raw,server_hostname=host) as tls:return tls.recv(2)
            finally:
                worker.join(5);listener.close();self.assertFalse(worker.is_alive());self.assertEqual(failures,[])
        self.assertEqual(connect(HOST,True),b'ok')
        with self.assertRaises(ssl.SSLCertVerificationError):connect('b'*56+'.onion',True)
        with self.assertRaises(ssl.SSLCertVerificationError):connect(HOST,False)
    def test_invalid_onion_name_refused_before_creating_files(self):
        result=self.prepare('--onion-host','example.com')
        self.assertNotEqual(result.returncode,0)
        self.assertEqual(list(self.path.iterdir()),[])
if __name__=='__main__':unittest.main()
