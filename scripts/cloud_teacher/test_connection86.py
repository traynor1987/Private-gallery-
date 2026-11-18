"""HTTPS integration against the real existing backend handler; isolated owner token only."""
import json
from http.server import ThreadingHTTPServer
from pathlib import Path
import ssl
import subprocess
import tempfile
from threading import Thread
import unittest
from unittest.mock import patch
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from server import Handler, configure


class Connection86Test(unittest.TestCase):
    def test_validated_tls_authenticated_status_and_staging_never_invoke_analysis(self):
        with tempfile.TemporaryDirectory(prefix='nova86-tls-') as tmp:
            root = Path(tmp)
            cert, key = root / 'cert.pem', root / 'key.pem'
            subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes',
                            '-keyout', str(key), '-out', str(cert), '-days', '1',
                            '-subj', '/CN=localhost', '-addext', 'subjectAltName=DNS:localhost'],
                           check=True, capture_output=True)
            with ThreadingHTTPServer(('127.0.0.1', 0), Handler) as server:
                with patch('server.secret', return_value='synthetic-owner-token-' * 3), patch('server.analyze') as analysis:
                    configure(server, staging=True)
                    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
                    tls.load_cert_chain(cert, key)
                    server.socket = tls.wrap_socket(server.socket, server_side=True)
                    worker = Thread(target=server.serve_forever, daemon=True)
                    worker.start()
                    origin = f'https://localhost:{server.server_port}'
                    # Only this isolated test trusts its synthetic CA, with normal hostname verification.
                    trust = ssl.create_default_context(cafile=str(cert))
                    try:
                        with urlopen(origin + '/health', context=trust, timeout=3) as response:
                            self.assertEqual({'status': 'ok'}, json.load(response))
                        with self.assertRaises(URLError):
                            urlopen(origin + '/health', timeout=3)  # System trust rejects self-signed certificate.
                        with self.assertRaises(HTTPError) as rejected:
                            urlopen(origin + '/guardian/status', context=trust, timeout=3)
                        self.assertEqual(401, rejected.exception.code)
                        request = Request(origin + '/guardian/status', headers={'Authorization': 'Bearer ' + 'synthetic-owner-token-' * 3})
                        with urlopen(request, context=trust, timeout=3) as response:
                            status = json.load(response)
                            self.assertEqual('authenticated', status['status'])
                            self.assertEqual('staging', status['model'])
                            self.assertFalse(status['analysis_enabled'])
                            self.assertIsNone(status['budget'])
                        analysis.assert_not_called()
                    finally:
                        server.shutdown()
                        worker.join()


if __name__ == '__main__':
    unittest.main()
