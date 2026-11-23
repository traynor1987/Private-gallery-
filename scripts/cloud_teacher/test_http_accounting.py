import json
import os
import unittest
from concurrent.futures import ThreadPoolExecutor
from http.client import HTTPConnection
from http.server import ThreadingHTTPServer
from pathlib import Path
from tempfile import TemporaryDirectory
from threading import Thread
from unittest.mock import Mock,patch
from urllib.request import Request, urlopen
from uuid import uuid4

from budget import Budget
from protocol import AnalysisResult
from server import Handler,RequestLimiter,configure

LESSON={'assessment':'UNCERTAIN','patterns':[],'uncertainty':'HIGH','summary':'Synthetic example needs context.'}


class HttpAccountingTest(unittest.TestCase):
    def setUp(self):
        self.directory=TemporaryDirectory()
        self.server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
        self.server.owner_token='t'*64
        self.server.openai_key='synthetic-server-only'
        self.server.model='gpt-5-mini'
        self.server.output_tokens=1000
        self.server.reasoning_effort='low'
        self.server.cost_bound=102000
        self.server.analysis_enabled=True
        self.server.limiter=RequestLimiter(limit=1000)
        self.server.budget=Budget(Path(self.directory.name)/'budget.db','test',100,1_000_000,102000,clock=lambda:99)
        self.worker=Thread(target=self.server.serve_forever,daemon=True);self.worker.start()

    def tearDown(self):
        self.server.shutdown();self.worker.join();self.server.server_close();self.directory.cleanup()

    def send(self,identity,*,route="/guardian/analyze",authorized=True,**extra):
        connection=HTTPConnection('127.0.0.1',self.server.server_port,timeout=5)
        payload={'message':'Synthetic approved excerpt','consent':True,'request_id':identity,**extra}
        if identity is None:
            payload.pop('request_id',None)
        try:
            connection.request('POST',route,json.dumps(payload),{'Authorization':'Bearer '+'t'*64} if authorized else {})
            result=connection.getresponse();return result.status,json.loads(result.read())
        finally:connection.close()

    def test_success_reconciles_usage_but_same_identity_never_calls_provider_again(self):
        identity=str(uuid4())
        with patch('server.analyze',return_value=AnalysisResult(LESSON,338)) as upstream:
            code,body=self.send(identity)
            self.assertEqual(code,200)
            self.assertEqual(body['lesson'],LESSON)
            self.assertEqual(body['usage_cost_micro_usd'],338)
            self.assertFalse(body['provider_billing_verified'])
            self.assertEqual(self.send(identity)[0],409)
            self.assertEqual(self.send(identity,message='Changed approved excerpt')[0],409)
            upstream.assert_called_once()
        self.assertEqual(self.server.budget.status()['reserved_upper_bound_micro_usd'],338)

    def test_automatic_mode_requires_separate_server_approval_and_uses_same_ledger(self):
        identity = str(uuid4())
        automatic = {'mode': 'automatic', 'protocol_version': 2}
        with patch('server.analyze', return_value=AnalysisResult(LESSON, 338)) as upstream:
            self.assertEqual(self.send(identity, **automatic)[0], 503)
            self.assertEqual(self.server.budget.status()['reserved_upper_bound_micro_usd'], 0)
            upstream.assert_not_called()
            self.server.automatic_analysis_enabled = True
            self.assertEqual(self.send(identity, message='My password is secret', **automatic)[0], 400)
            self.assertEqual(self.send(identity, message='Call 07123456789', **automatic)[0], 400)
            self.assertEqual(self.send(identity, message='Visit example.com please', **automatic)[0], 400)
            self.assertEqual(self.send(identity, **automatic)[0], 200)
            self.assertEqual(self.send(identity, **automatic)[0], 409)
            upstream.assert_called_once()
        self.assertEqual(self.server.budget.status()['reserved_upper_bound_micro_usd'], 338)

    def test_assistant_shares_auth_consent_and_paid_request_ledger(self):
        identity = str(uuid4())
        request = {'route': '/assistant/ask', 'message': 'What is a haiku?'}
        result = AnalysisResult({'summary': 'A short poetic form.'}, 321)
        with patch('server.answer_question', return_value=result) as upstream:
            self.assertEqual(self.send(identity, authorized=False, **request)[0], 401)
            self.assertEqual(self.send(identity, consent=False, **request)[0], 400)
            self.assertEqual(self.send(identity, mode='automatic', **request)[0], 400)
            self.assertEqual(self.send(None, **request)[0], 400)
            self.server.analysis_enabled = False
            self.assertEqual(self.send(identity, **request)[0], 503)
            upstream.assert_not_called()
            self.server.analysis_enabled = True
            code, body = self.send(identity, **request)
            self.assertEqual(code, 200)
            self.assertEqual(body['source'], 'assistant_prediction')
            self.assertEqual(body['answer'], 'A short poetic form.')
            self.assertNotIn('lesson', body)
            self.assertEqual(self.send(identity, **request)[0], 409)
            self.assertEqual(self.send(identity)[0], 409)
            upstream.assert_called_once()
        self.assertEqual(self.server.budget.status()['reserved_upper_bound_micro_usd'], 321)

    def test_assistant_preflight_does_not_reserve_and_exact_question_reaches_provider(self):
        identity = str(uuid4())
        url = f'http://127.0.0.1:{self.server.server_port}/assistant/ask'
        request = Request(url, headers={'Authorization': 'Bearer ' + self.server.owner_token})
        reply = {
            'status': 'completed', 'model': 'gpt-5-mini', 'service_tier': 'default',
            'usage': {'input_tokens': 200, 'output_tokens': 50, 'total_tokens': 250,
                      'input_tokens_details': {'cached_tokens': 0},
                      'output_tokens_details': {'reasoning_tokens': 0}},
            'output': [{'type': 'message', 'content': [{'type': 'output_text', 'text': 'A short poetic form.'}]}],
        }
        fake_opener = Mock()
        fake_opener.open.return_value.__enter__ = Mock(return_value=Mock(read=Mock(return_value=json.dumps(reply).encode())))
        fake_opener.open.return_value.__exit__ = Mock(return_value=False)
        with patch('server.build_opener', return_value=fake_opener):
            with urlopen(request, timeout=5) as response:
                status = json.load(response)
                self.assertTrue(status['assistant_available'])
                self.assertEqual(status['assistant_context_max_chars'], 4096)
            fake_opener.open.assert_not_called()
            self.assertEqual(0, self.server.budget.status()['reserved_upper_bound_micro_usd'])

            route = {'route': '/assistant/ask', 'message': 'What is a haiku?'}
            code, result = self.send(identity, **route)
            self.assertEqual(200, code)
            self.assertEqual('A short poetic form.', result['answer'])
            self.assertEqual(150, result['usage_cost_micro_usd'])
            self.assertEqual(409, self.send(identity, **route)[0])
            fake_opener.open.assert_called_once()
            sent = json.loads(fake_opener.open.call_args.args[0].data)
            self.assertEqual(route['message'], sent['input'])
            self.assertFalse(sent['store'])
            self.assertEqual([], sent['tools'])

    def test_approved_follow_up_uses_exact_context_with_one_reservation(self):
        identity = str(uuid4())
        route = '/assistant/ask'
        transcript = (
            'Earlier turns for context:\nOwner: Tell me about clouds.\nNova: '
            + 'Cloud droplets form when air cools. ' * 20
            + '\nLatest question: Where does fog fit?'
        )
        self.assertGreater(len(transcript), 600)
        with patch('server.answer_question', return_value=AnalysisResult({'summary': 'Fog is a cloud.'}, 321)) as upstream:
            self.assertEqual(self.send(identity, route=route, message='x' * 4097)[0], 400)
            self.assertEqual(self.send(identity, route=route, message=transcript, consent=False)[0], 400)
            upstream.assert_not_called()
            self.assertEqual(self.send(identity, route=route, message=transcript)[0], 200)
            self.assertEqual(self.send(identity, route=route, message=transcript)[0], 409)
            self.assertEqual(self.send(identity, route=route, message=transcript + '?')[0], 409)
            upstream.assert_called_once_with(transcript, self.server.openai_key, self.server.model,
                                             output_tokens=1000, reasoning_effort='low')
        self.assertEqual(self.server.budget.status()['reserved_upper_bound_micro_usd'], 321)

    def test_concurrent_duplicates_dispatch_exactly_one_paid_request(self):
        identity=str(uuid4())
        with patch('server.analyze',return_value=AnalysisResult(LESSON,338)) as upstream:
            with ThreadPoolExecutor(max_workers=6) as workers:
                statuses=list(workers.map(lambda _:self.send(identity)[0],range(12)))
            self.assertEqual(statuses.count(200),1)
            self.assertEqual(statuses.count(409),11)
            upstream.assert_called_once()

    def test_timeout_keeps_full_reservation_and_retry_has_no_second_provider_call(self):
        identity=str(uuid4())
        with patch('server.analyze',side_effect=TimeoutError('never print private diagnostics')) as upstream:
            code,body=self.send(identity)
            self.assertEqual(code,504)
            self.assertNotIn('private diagnostics',json.dumps(body))
            self.assertEqual(self.send(identity)[0],409)
            upstream.assert_called_once()
        self.assertEqual(self.server.budget.status()['reserved_upper_bound_micro_usd'],102000)

    def test_legacy_client_without_uuid_is_still_deduplicated_by_owner_keyed_digest(self):
        with patch('server.analyze',return_value=AnalysisResult(LESSON,338)) as upstream:
            self.assertEqual(self.send(None)[0],200)
            self.assertEqual(self.send(None)[0],409)
            upstream.assert_called_once()

    def test_annotation_failure_does_not_refund_a_timed_out_request(self):
        import sqlite3
        with patch('server.analyze',side_effect=TimeoutError('private provider error')), \
             patch.object(self.server.budget,'mark_uncertain',side_effect=sqlite3.OperationalError('synthetic failure')):
            code,body=self.send(str(uuid4()))
            self.assertEqual(code,504)
            self.assertNotIn('private provider error',json.dumps(body))
        self.assertEqual(self.server.budget.status()['reserved_upper_bound_micro_usd'],102000)

    def test_client_cannot_submit_its_own_cost_or_budget_configuration(self):
        with patch('server.analyze') as upstream:
            for extra in ({'cost':0},{'monthly_limit':999999999},{'consent':False},{'message':'  '},{'request_id':'not-a-uuid'}):
                with self.subTest(extra=extra):self.assertEqual(self.send(str(uuid4()),**extra)[0],400)
            upstream.assert_not_called()
        self.assertEqual(self.server.budget.status()['reserved_upper_bound_micro_usd'],0)

    def test_missing_usage_stays_reserved_and_budget_exhaustion_blocks_provider(self):
        self.server.budget=Budget(Path(self.directory.name)/'other.db','small',100,102000,102000,clock=lambda:99)
        with patch('server.analyze',return_value=AnalysisResult(LESSON,None)) as upstream:
            self.assertEqual(self.send(str(uuid4()))[0],200)
            self.assertEqual(self.send(str(uuid4()))[0],402)
            upstream.assert_called_once()
        self.assertEqual(self.server.budget.status()['remaining_micro_usd'],0)

    def test_unverified_pricing_halts_paid_dispatch_without_releasing_the_bound(self):
        with patch('server.analyze',return_value=AnalysisResult(LESSON,None,True)) as upstream:
            self.assertEqual(self.send(str(uuid4()))[0],503)
            self.assertEqual(self.send(str(uuid4()))[0],402)
            upstream.assert_called_once()
        self.assertTrue(self.server.budget.status()['paid_requests_halted'])
        self.assertEqual(self.server.budget.status()['reserved_upper_bound_micro_usd'],102000)

    def test_saved_funding_and_key_do_not_enable_analysis_without_explicit_server_approval(self):
        environment={'NOVA_CLOUD_CREDIT_CYCLE':'funded','NOVA_CLOUD_TEACHER_MODEL':'gpt-5-mini',
            'NOVA_CLOUD_MAX_REQUEST_MICRO_USD':'102000','NOVA_CLOUD_BUDGET_DB':'synthetic-db',
            'NOVA_CLOUD_CREDIT_EXPIRY':'4102444800','NOVA_CLOUD_CONFIRMED_CREDIT_MICRO_USD':'1000000'}
        with patch.dict(os.environ,environment,clear=True), \
             patch('server.secret',return_value='t'*64) as secret,patch('server.Budget') as budget:
            configure(self.server,staging=False)
            self.assertFalse(self.server.analysis_enabled)
            secret.assert_called_once_with('NOVA_CLOUD_TEACHER_TOKEN')
            budget.assert_called_once()  # Ledger reporting is allowed; paid key access is not.

    def test_explicit_server_approval_starts_with_small_single_request_allowance(self):
        environment={'NOVA_CLOUD_ANALYSIS_APPROVED':'true','NOVA_CLOUD_CREDIT_CYCLE':'synthetic-cycle',
            'NOVA_CLOUD_TEACHER_MODEL':'gpt-5-mini','NOVA_CLOUD_MAX_REQUEST_MICRO_USD':'102000',
            'NOVA_CLOUD_BUDGET_DB':str(Path(self.directory.name)/'approved.db'),
            'NOVA_CLOUD_CREDIT_EXPIRY':'4102444800','NOVA_CLOUD_CONFIRMED_CREDIT_MICRO_USD':'1000000'}
        with patch.dict(os.environ,environment,clear=True),patch('server.secret',side_effect=lambda key:'t'*64 if key=='NOVA_CLOUD_TEACHER_TOKEN' else 'synthetic-provider-key'):
            configure(self.server,staging=False)
            self.assertTrue(self.server.analysis_enabled)
            self.assertEqual(self.server.budget.ceiling,250000)
            self.assertEqual(self.server.budget.monthly_limit,100000000)
            self.assertEqual(self.server.budget.request_limit,1)
        # No provider call was made: this verifies only synthetic configuration.
