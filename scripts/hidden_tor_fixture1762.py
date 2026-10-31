"""Interactive endpoints for the disposable CI onion service; no app transport mocks."""
import base64
import hashlib
from email.parser import BytesParser
from email import policy
import json
import threading
import time
from urllib.parse import parse_qs

PNG = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=')
DOCUMENT = b'Private Apps document download fixture 178\n'
DOCUMENT_UPLOAD = b'Private Apps document upload fixture 178\n'
HTML = b'''<!doctype html><meta name="viewport" content="width=device-width"><title>Tor native fixture</title>
<link rel="manifest" href="/manifest178.json"><h1>Tor native fixture</h1>
<a href="/private-app-document178.txt" download="private-app-document178.txt">Download app document</a>
<form method="POST" action="/private-app-document-upload178" enctype="multipart/form-data"><label>Choose app document<input type="file" name="document" accept="text/plain"></label><button>Upload app document</button></form><p>Forms and uploads work without JavaScript.</p>
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
            self.event(method='GET', path=self.path, cookie_present='hiddentorfixture=only' in self.headers.get('Cookie', ''))
            if self.path == '/':
                self.send_bytes(200, payload)
                with (root / 'backend-requests.txt').open('a') as hits:
                    hits.write('Controlled HTTP backend served response\n')
            elif self.path == '/interactive':
                self.send_bytes(200, HTML, 'text/html; charset=utf-8')
            elif self.path == '/manifest178.json':
                self.send_bytes(200, json.dumps(dict(name='Owned launch fixture',start_url='/interactive',scope='/',display='standalone',icons=[dict(src='/media.png',type='image/png')])).encode(), 'application/manifest+json')
            elif self.path == '/private-app-document178.txt':
                self.send_bytes(200, DOCUMENT, 'text/plain', [('Content-Disposition', 'attachment; filename="private-app-document178.txt"')])
            elif self.path == '/script-probe':
                self.send_bytes(200, b'<!doctype html><h1>Tor script probe</h1><script>fetch("/script-must-not-run")</script>', 'text/html; charset=utf-8')
            elif self.path == '/script-must-not-run':
                self.send_bytes(200, b'Unexpected JavaScript execution')
            elif self.path == '/cookie':
                self.send_bytes(200, b'Tor cookie set', extra=[('Set-Cookie', 'hiddentorfixture=only; SameSite=Lax')])
            elif self.path == '/cookie-echo':
                self.send_bytes(200, self.headers.get('Cookie', '').encode())
            elif self.path == '/redirect':
                self.send_bytes(302, b'', extra=[('Location', '/interactive')])
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
            # Separate admission from completion: a stalled multipart body must not look
            # like a touch that never reached the controlled origin. Synthetic fixture only.
            self.event(kind='body-start', path=self.path, declared_length=int(length))
            body = self.rfile.read(int(length))
            self.event(kind='body-complete', path=self.path, received_length=len(body), declared_length=int(length))
            form_ok = self.path == '/form-result' and parse_qs(body.decode('utf-8', errors='replace')).get('value') == ['fixture-form-body']
            file_bytes = None
            content_type = self.headers.get('Content-Type', '')
            if self.path in ('/uploaded','/private-app-document-upload178') and content_type.startswith('multipart/form-data;'):
                message = BytesParser(policy=policy.default).parsebytes(b'Content-Type: ' + content_type.encode('ascii') + b'\r\nMIME-Version: 1.0\r\n\r\n' + body)
                if message.is_multipart():
                    files = [part for part in message.iter_parts() if part.get_param('name', header='content-disposition') == ('document' if self.path == '/private-app-document-upload178' else 'file')]
                    if len(files) == 1:
                        file_bytes = files[0].get_payload(decode=True)
            document_exact = self.path == '/private-app-document-upload178' and file_bytes == DOCUMENT_UPLOAD
            if self.path == '/private-app-document-upload178':
                self.event(method='POST',path=self.path,document_exact=document_exact,upload_sha256=hashlib.sha256(file_bytes).hexdigest() if file_bytes is not None else None)
                self.send_bytes(200 if document_exact else 400,b'Document upload verified' if document_exact else b'Invalid document')
                return
            multipart_png = file_bytes is not None and file_bytes.startswith(b'\x89PNG\r\n\x1a\n')
            self.event(method='POST', path=self.path, length=len(body), sha256=hashlib.sha256(body).hexdigest(), form_ok=form_ok, multipart_png=multipart_png, upload_sha256=hashlib.sha256(file_bytes).hexdigest() if file_bytes is not None else None)
            self.send_bytes(200 if form_ok or multipart_png else 400, b'Tor form received' if form_ok else b'Tor upload received' if multipart_png else b'Unexpected request')

        def log_message(self, *_):
            pass

    return Handler
