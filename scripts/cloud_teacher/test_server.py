import io
import json
import unittest
from http.client import HTTPConnection
from http.server import ThreadingHTTPServer
from threading import Thread
from unittest.mock import patch

from server import Handler, analyze, authorize


class TeacherTest(unittest.TestCase):
    def test_token_is_required_and_exact(self):
        self.assertFalse(authorize("", "owner"))
        self.assertFalse(authorize("Bearer owner", ""))
        self.assertTrue(authorize("Bearer owner", "owner"))

    def test_success_and_server_side_credential(self):
        def upstream(request, timeout):
            self.assertEqual(request.get_header("Authorization"), "Bearer server-test")
            self.assertEqual(json.loads(request.data)["input"], "only the selected message")
            self.assertFalse(json.loads(request.data)["store"])
            return io.BytesIO(json.dumps({"status": "completed", "output": [
                {"type": "message", "content": [{"type": "output_text", "text": "Possible pressure."}]}
            ]}).encode())
        self.assertEqual(analyze("only the selected message", "server-test", "model", upstream), "Possible pressure.")

    def test_incomplete_response_is_rejected(self):
        with self.assertRaises(ValueError):
            analyze("selected", "server-test", "model", lambda *a, **kw: io.BytesIO(b'{"status":"incomplete"}'))


    def test_requests_without_consent_or_token_never_reach_openai(self):
        with ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
            server.owner_token = "owner-test"
            server.openai_key = "server-only"
            server.model = "test"
            worker = Thread(target=server.serve_forever, daemon=True)
            worker.start()
            try:
                def send(token, consent):
                    conn = HTTPConnection("127.0.0.1", server.server_port, timeout=3)
                    try:
                        conn.request("POST", "/guardian/analyze",
                                     json.dumps({"message": "synthetic", "consent": consent}),
                                     {"Authorization": f"Bearer {token}"})
                        response = conn.getresponse()
                        return response.status, response.read()
                    finally:
                        conn.close()

                with patch("server.analyze", return_value="Advisory") as upstream:
                    self.assertEqual(send("wrong", True)[0], 401)
                    self.assertEqual(send("owner-test", False)[0], 400)
                    upstream.assert_not_called()
                    status, body = send("owner-test", True)
                    self.assertEqual(status, 200)
                    self.assertEqual(json.loads(body), {"advice": "Advisory"})
                    upstream.assert_called_once_with("synthetic", "server-only", "test")
            finally:
                server.shutdown()
                worker.join()
