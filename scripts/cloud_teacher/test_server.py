import io
import json
import os
import unittest
from http.client import HTTPConnection
from http.server import ThreadingHTTPServer
from threading import Thread
from unittest.mock import Mock, patch
from urllib.error import HTTPError, URLError

from server import Handler, RequestLimiter, analyze, authorize, request_cost_bound


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
            self.assertEqual(json.loads(request.data)["service_tier"], "default")
            self.assertEqual(json.loads(request.data)["max_output_tokens"], 1000)
            self.assertNotIn("reasoning", json.loads(request.data))
            return io.BytesIO(json.dumps({"status": "completed", "output": [
                {"type": "message", "content": [{"type": "output_text", "text": "Possible pressure."}]}
            ]}).encode())
        self.assertEqual(analyze("only the selected message", "server-test", "gpt-5-mini", upstream), "Possible pressure.")

    def test_incomplete_response_is_rejected(self):
        with self.assertRaises(ValueError):
            analyze("selected", "server-test", "gpt-5-mini", lambda *a, **kw: io.BytesIO(b'{"status":"incomplete"}'))


    def test_model_effort_and_output_limits(self):
        for effort in (None, "minimal", "low", "medium", "high"):
            self.assertEqual(request_cost_bound("gpt-5-mini", 1000, effort), 102_000)
        self.assertEqual(request_cost_bound("gpt-5-mini-2025-08-07", 128_000, "low"), 356_000)
        for args in (("unknown", 1000, "low"), ("gpt-5-mini", 1000, "none"),
                     ("gpt-5-mini", 15, None), ("gpt-5-mini", 128_001, None)):
            with self.subTest(args=args), self.assertRaises(ValueError):
                request_cost_bound(*args)

        upstream = Mock(return_value=io.BytesIO(b'{"status":"incomplete"}'))
        with self.assertRaises(ValueError):
            analyze("selected", "server-test", "gpt-5-mini", upstream,
                    output_tokens=1600, reasoning_effort="low")
        payload = json.loads(upstream.call_args.args[0].data)
        self.assertEqual(payload["max_output_tokens"], 1600)
        self.assertEqual(payload["reasoning"], {"effort": "low"})

    def test_requests_without_consent_or_token_never_reach_openai(self):
        with ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
            server.owner_token = "owner-test"
            server.openai_key = "server-only"
            server.model = "gpt-5-mini"
            server.output_tokens = 1000
            server.reasoning_effort = "low"
            server.cost_bound = request_cost_bound(server.model, server.output_tokens, server.reasoning_effort)
            server.analysis_enabled = True
            server.limiter = RequestLimiter(limit=1)
            server.budget = Mock()
            server.budget.reserve.return_value = True
            server.budget.reserve_amount = 102_000
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
                    self.assertEqual(send("owner-test", True)[0], 429)
                    server.analysis_enabled = False
                    self.assertEqual(send("owner-test", True)[0], 503)
                    upstream.assert_called_once_with("synthetic", "server-only", "gpt-5-mini",
                                                     output_tokens=1000, reasoning_effort="low")

                server.analysis_enabled = True
                server.limiter = RequestLimiter()
                server.budget.reserve.reset_mock()
                server.budget.reserve_amount = 101_999
                with patch("server.analyze") as upstream:
                    self.assertEqual(send("owner-test", True)[0], 503)
                    server.budget.reserve.assert_not_called()
                    server.budget.reserve_amount = 102_000
                    server.budget.reserve.return_value = False
                    self.assertEqual(send("owner-test", True)[0], 402)
                    upstream.assert_not_called()

                server.budget.reserve.return_value = True
                server.budget.reserve.reset_mock()
                failures = [(TimeoutError("sensitive"), 504), (URLError(TimeoutError("sensitive")), 504),
                            (HTTPError("https://api.openai.com", 429, "sensitive", {}, None), 502),
                            (ValueError("sensitive"), 502)]
                for error, expected in failures:
                    with self.subTest(error=type(error)), patch("server.analyze", side_effect=error) as upstream:
                        status, body = send("owner-test", True)
                        self.assertEqual(status, expected)
                        self.assertNotIn(b"sensitive", body)
                        upstream.assert_called_once()
                self.assertEqual(server.budget.reserve.call_count, len(failures))
            finally:
                server.shutdown()
                worker.join()


    def test_staging_ignores_funded_settings_and_blocks_analysis(self):
        from server import configure

        with ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
            with patch.dict(os.environ, {"NOVA_CLOUD_CREDIT_CYCLE": "funded",
                                         "NOVA_CLOUD_TEACHER_MODEL": "gpt-5-mini",
                                         "OPENAI_API_KEY": "synthetic-key"}), \
                 patch("server.secret", return_value="t" * 32) as read_secret, \
                 patch("server.Budget") as budget, patch("server.analyze") as upstream:
                configure(server, staging=True)
                worker = Thread(target=server.serve_forever, daemon=True)
                worker.start()
                conn = HTTPConnection("127.0.0.1", server.server_port, timeout=3)
                try:
                    conn.request("GET", "/health")
                    response = conn.getresponse()
                    self.assertEqual(response.status, 200)
                    response.read()
                    conn.request("GET", "/guardian/status")
                    response = conn.getresponse()
                    self.assertEqual(response.status, 401)
                    response.read()
                    headers = {"Authorization": "Bearer " + "t" * 32}
                    conn.request("GET", "/guardian/status", headers=headers)
                    response = conn.getresponse()
                    self.assertEqual(response.status, 200)
                    status = json.loads(response.read())
                    self.assertFalse(status["analysis_enabled"])
                    self.assertEqual(status["model"], "staging")
                    self.assertIsNone(status["budget"])
                    conn.request("POST", "/guardian/analyze",
                                 json.dumps({"message": "synthetic", "consent": True}), headers)
                    response = conn.getresponse()
                    self.assertEqual(response.status, 503)
                    response.read()
                    read_secret.assert_called_once_with("NOVA_CLOUD_TEACHER_TOKEN")
                    budget.assert_not_called()
                    upstream.assert_not_called()
                finally:
                    conn.close()
                    server.shutdown()
                    worker.join()

    def test_limiter_reopens_at_window_boundary(self):
        time = [0]
        limiter = RequestLimiter(limit=1, window=60, clock=lambda: time[0])
        self.assertTrue(limiter.admit())
        time[0] = 59
        self.assertFalse(limiter.admit())
        time[0] = 60
        self.assertTrue(limiter.admit())
