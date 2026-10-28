import hashlib
import http.client
import http.server
import importlib.util
import json
from pathlib import Path
import tempfile
import threading
import unittest

spec = importlib.util.spec_from_file_location('tor_fixture1762', Path(__file__).resolve().parents[1] / 'hidden_tor_fixture1762.py')
f = importlib.util.module_from_spec(spec)
spec.loader.exec_module(f)


class TorFixtureTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), f.handler_class(http.server.BaseHTTPRequestHandler, Path(self.temp.name), b'exact-private-health-nonce'))
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        self.temp.cleanup()

    def request(self, method, path, body=None, headers=None):
        connection = http.client.HTTPConnection(*self.server.server_address, timeout=3)
        try:
            connection.request(method, path, body, headers or {})
            response = connection.getresponse()
            return response.status, dict(response.getheaders()), response.read()
        finally:
            connection.close()

    def test_root_exact_health_and_javascript_free_interactive_page(self):
        self.assertEqual(self.request('GET', '/')[2], b'exact-private-health-nonce')
        status, _, body = self.request('GET', '/interactive')
        self.assertEqual(status, 200)
        self.assertNotIn(b'<script', body)
        self.assertIn(b'method="post"', body)
        self.assertIn(b'type="file"', body)
        self.assertEqual(self.request('GET', '/missing')[0], 404)

    def test_real_post_bytes_and_download_are_recorded(self):
        body = b'value=fixture-form-body'
        self.assertEqual(self.request('POST', '/form-result', body)[0], 200)
        multipart = b'--fixture\r\nContent-Disposition: form-data; name="file"; filename="a.png"\r\nContent-Type: image/png\r\n\r\n' + f.PNG + b'\r\n--fixture--\r\n'
        self.assertEqual(self.request('POST', '/uploaded', multipart, {'Content-Type':'multipart/form-data; boundary=fixture'})[0], 200)
        self.assertEqual(self.request('POST', '/form-result', b'value=wrong')[0], 400)
        status, headers, png = self.request('GET', '/media.png')
        self.assertEqual(status, 200)
        self.assertIn('attachment', headers['Content-Disposition'])
        self.assertEqual(png, f.PNG)
        evidence = json.loads(self.request('GET', '/evidence')[2])
        posts = [event for event in evidence['events'] if event.get('method') == 'POST']
        self.assertTrue(posts[0]['form_ok'])
        self.assertEqual(posts[0]['sha256'], hashlib.sha256(body).hexdigest())
        self.assertTrue(posts[1]['multipart_png'])
        self.assertFalse(posts[2]['form_ok'])
        self.assertEqual(evidence['png_sha256'], hashlib.sha256(png).hexdigest())
