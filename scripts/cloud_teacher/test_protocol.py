import json
import unittest
from protocol import parse_lesson, usage_cost


class ProtocolTest(unittest.TestCase):
    def envelope(self):
        return {'model':'gpt-5-mini-2025-08-07','service_tier':'default',
                'usage':{'input_tokens':1000,'input_tokens_details':{'cached_tokens':500,'cache_write_tokens':0},
                         'output_tokens':100,'output_tokens_details':{'reasoning_tokens':80},'total_tokens':1100}}

    def test_cached_input_and_reasoning_are_counted_once_and_cost_rounds_up(self):
        self.assertEqual(usage_cost(self.envelope(),'gpt-5-mini',1000),338)

    def test_full_context_and_output_are_bounded_by_the_existing_reservation(self):
        body=self.envelope();body['usage'].update(input_tokens=400000,output_tokens=1000,total_tokens=401000)
        body['usage']['input_tokens_details']['cached_tokens']=0
        self.assertEqual(usage_cost(body,'gpt-5-mini',1000),102000)

    def test_unknown_price_components_never_authorize_a_refund(self):
        for key,value in [('service_tier','priority'),('model','unpriced'),('tool_usage',{'web_search':1})]:
            body=self.envelope();body[key]=value
            with self.subTest(key=key):self.assertIsNone(usage_cost(body,'gpt-5-mini',1000))
        body=self.envelope();body['usage']['input_tokens_details']['cache_write_tokens']=1
        self.assertIsNone(usage_cost(body,'gpt-5-mini',1000))
        self.assertIsNone(usage_cost({},'gpt-5-mini',1000))

    def test_inconsistent_and_noninteger_usage_retains_the_entire_bound(self):
        for key,value in [('input_tokens',True),('input_tokens',-1),('total_tokens',1101),('output_tokens',1001)]:
            body=self.envelope();body['usage'][key]=value
            with self.subTest(key=key):self.assertIsNone(usage_cost(body,'gpt-5-mini',1000))
        body=self.envelope();body['usage']['output_tokens_details']['reasoning_tokens']=101
        self.assertIsNone(usage_cost(body,'gpt-5-mini',1000))
        body=self.envelope();body['usage']['input_tokens_details']['cached_tokens']=1001
        self.assertIsNone(usage_cost(body,'gpt-5-mini',1000))

    def test_structured_predictions_are_bounded_and_never_include_extra_fields(self):
        lesson={'assessment':'UNCERTAIN','patterns':['BOUNDARY_PRESSURE'],'uncertainty':'HIGH','summary':'Context is missing.'}
        self.assertEqual(parse_lesson(json.dumps(lesson)),lesson)
        invalid=[{**lesson,'diagnosis':'unsupported'}, {**lesson,'assessment':'CERTAIN'},
                 {**lesson,'patterns':['invented']},{**lesson,'patterns':['INSULT','INSULT']},
                 {**lesson,'summary':'x'*1001},{**lesson,'summary':'\x00'}]
        for value in invalid:
            with self.subTest(keys=list(value)),self.assertRaises(ValueError):parse_lesson(json.dumps(value))
