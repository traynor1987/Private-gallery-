"""Interactive endpoints for the disposable CI onion service; no app transport mocks."""
import base64
import hashlib
import json
import threading
import time
from urllib.parse import parse_qs

PNG = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/h9sAAAAASUVORK5CYII=')
HTML = b'''<!doctype html><meta name="viewport" content="width=device-width"><title>Tor native fixture</title>
<h1>Tor native fixture</h1><p>Forms and uploads work without JavaScript.</p>
<form action="/form-result" method="post"><input name="value" value="fixture-form-body"><button type="submit">Submit Tor form</button></form>
<form action="/uploaded" method="post" enctype="multipart/form-data"><label>Choose Tor upload<input type="file" name="file" accept="image/png"></label><button type="submit">Upload Tor image</button></form>
<a href="/media.png" download="tor-fixture.png">Download Tor image</a><a href="/slow">Slow Tor request</a>'''


def handler_class(base, root, payload):
    lock = threading.Lock()
    events_path = root / 'interactive-events.jsonl'

    class Handler(base):
        protocol_version = 'HTTP/1.1'

        def event(self, **values):
            with lock, events_path.open('a') as output:
                output.write(json.dumps(values, sort_keys=True) + '\n')

        def send_bytes(self, status, body, mime='text/plain; charset=utf-8', extra=()):
            self.send_response(status)
            self.send_header('Content-Type', mime)
            self.send_header('Content-Length', str(len(body)))
            self.send_header('Cache-Control', 'no-store')
            for name, value in extra:
                self.send_header(name, value)
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            self.event(method='GET', path=self.path)
            if self.path == '/':
                self.send_bytes(200, payload)
                with (root / 'backend-requests.txt').open('a') as hits:
                    hits.write('Controlled HTTP backend served response\n')
            elif self.path == '/interactive':
                self.send_bytes(200, HTML, 'text/html; charset=utf-8')
            elif self.path == '/media.png':
                self.send_bytes(200, PNG, 'image/png', [('Content-Disposition', 'attachment; filename="tor-fixture.png"')])
            elif self.path == '/evidence':
                with lock:
                    events = [json.loads(line) for line in events_path.read_text().splitlines()]
                self.send_bytes(200, json.dumps({'events': events, 'png_sha256': hashlib.sha256(PNG).hexdigest()}).encode(), 'application/json')
            elif self.path == '/slow':
                self.event(kind='slow-start')
                self.send_response(200)
                self.send_header('Content-Type', 'text/plain')
                self.send_header('Content-Length', '1048576')
                self.end_headers()
                try:
                    for _ in range(1024):
                        self.wfile.write(b'x' * 1024)
                        self.wfile.flush()
                        time.sleep(.1)
                except (BrokenPipeError, ConnectionResetError, OSError):
                    self.event(kind='slow-closed')
            else:
                self.send_bytes(404, b'Not found')

        def do_POST(self):
            length = self.headers.get('Content-Length', '')
            if not length.isdecimal() or not 0 < int(length) <= 2 * 1024 * 1024:
                self.close_connection = True
                self.send_bytes(413, b'Bounded Content-Length required')
                return
            body = self.rfile.read(int(length))
            form_ok = self.path == '/form-result' and parse_qs(body.decode('utf-8', errors='replace')).get('value') == ['fixture-form-body']
            multipart_png = (self.path == '/uploaded' and self.headers.get('Content-Type', '').startswith('multipart/form-data;') and b'name="file"' in body and PNG in body)
            self.event(method='POST', path=self.path, length=len(body), sha256=hashlib.sha256(body).hexdigest(), form_ok=form_ok, multipart_png=multipart_png)
            self.send_bytes(200 if form_ok or multipart_png else 400, b'Tor form received' if form_ok else b'Tor upload received' if multipart_png else b'Unexpected request')

        def log_message(self, *_):
            pass

    return Handler
