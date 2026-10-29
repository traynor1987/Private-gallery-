import hashlib
import http.client
import http.server
import importlib.util
import json
from pathlib import Path
import tempfile
import threading
import unittest


def load(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).resolve().parents[1] / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class PrivateAppDocumentsFixtureTest(unittest.TestCase):
    def test_real_document_download_and_exact_multipart_evidence(self):
        tor = load('hidden_tor_fixture1762')
        vpn = load('hidden_vpn_fixture1762')
        self.assertEqual(tor.DOCUMENT, vpn.DOCUMENT)
        self.assertEqual(tor.DOCUMENT_UPLOAD, vpn.DOCUMENT_UPLOAD)
        for page in (tor.HTML, vpn.PAGE):
            self.assertIn(b'accept="text/plain"', page)
            self.assertIn(b'/private-app-document-upload178', page)
        with tempfile.TemporaryDirectory() as folder:
            server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), tor.handler_class(http.server.BaseHTTPRequestHandler, Path(folder), b'health'))
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            def request(method, path, body=None, headers=None):
                connection = http.client.HTTPConnection(*server.server_address, timeout=3)
                try:
                    connection.request(method, path, body, headers or {})
                    response = connection.getresponse()
                    return response.status, dict(response.getheaders()), response.read()
                finally:
                    connection.close()
            try:
                status, headers, body = request('GET', '/private-app-document178.txt')
                self.assertEqual(status, 200)
                self.assertEqual(headers['Content-Type'], 'text/plain')
                self.assertIn('attachment', headers['Content-Disposition'])
                self.assertEqual(body, tor.DOCUMENT)
                def multipart(payload):
                    return b'--fixture\r\nContent-Disposition: form-data; name="document"; filename="upload.txt"\r\nContent-Type: text/plain\r\n\r\n' + payload + b'\r\n--fixture--\r\n'
                headers = {'Content-Type': 'multipart/form-data; boundary=fixture'}
                self.assertEqual(request('POST', '/private-app-document-upload178', multipart(tor.DOCUMENT_UPLOAD), headers)[0], 200)
                self.assertEqual(request('POST', '/private-app-document-upload178', multipart(b'wrong'), headers)[0], 400)
                posts = [event for event in json.loads(request('GET', '/evidence')[2])['events'] if event['method'] == 'POST']
                self.assertTrue(posts[0]['document_exact'])
                self.assertEqual(posts[0]['upload_sha256'], hashlib.sha256(tor.DOCUMENT_UPLOAD).hexdigest())
                self.assertFalse(posts[1]['document_exact'])
            finally:
                server.shutdown()
                server.server_close()
                worker.join()
