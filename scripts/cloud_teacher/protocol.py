"""Bounded structured advice and reviewed token-price accounting; no provider calls."""
from dataclasses import dataclass
import json

MODELS = {'gpt-5-mini', 'gpt-5-mini-2025-08-07'}
PATTERNS = ('GUILT','GASLIGHTING','EMOTIONAL_PRESSURE','REPEATED_DEMAND','BLAME_SHIFTING',
            'PASSIVE_AGGRESSION','COERCION','BOUNDARY_PRESSURE','THREAT','INSULT','REPEATED_CONTACT')
LESSON_SCHEMA = {
    'type':'object','additionalProperties':False,
    'properties':{
        'assessment':{'type':'string','enum':['LOW_RISK','UPSETTING','UNCERTAIN']},
        'patterns':{'type':'array','items':{'type':'string','enum':list(PATTERNS)}},
        'uncertainty':{'type':'string','enum':['LOW','MEDIUM','HIGH']},
        'summary':{'type':'string'},
    },
    'required':['assessment','patterns','uncertainty','summary'],
}


@dataclass(frozen=True, repr=False)
class AnalysisResult:
    lesson: dict
    usage_cost_micro_usd: int | None
    pricing_unverified: bool = False

    @property
    def advice(self):
        return self.lesson['summary']

    def __repr__(self):
        return 'AnalysisResult(redacted)'


def parse_lesson(text):
    value = json.loads(text)
    if (not isinstance(value,dict) or set(value) != set(LESSON_SCHEMA['required']) or
        value['assessment'] not in ('LOW_RISK','UPSETTING','UNCERTAIN') or
        value['uncertainty'] not in ('LOW','MEDIUM','HIGH') or
        not isinstance(value['patterns'],list) or len(value['patterns']) > len(PATTERNS) or
        any(not isinstance(x,str) or x not in PATTERNS for x in value['patterns']) or
        len(set(value['patterns'])) != len(value['patterns']) or
        not isinstance(value['summary'],str) or not 1 <= len(value['summary'].strip()) <= 1000 or
        any(ord(x)<32 and x not in '\n\t' for x in value['summary'])):
        raise ValueError('Invalid structured advisory')
    return {**value,'summary':value['summary'].strip()}


def usage_cost(result, model, max_output_tokens):
    """Reviewed standard text rates; reasoning is a SUBSET of output, not extra.

    Return None for missing/unpriced usage: retain the entire reservation instead
    of inventing a refund or claiming an invoice/provider-credit balance.
    """
    try:
        if model not in MODELS or result.get('model') not in MODELS or result.get('service_tier') != 'default':
            return None
        if result.get('tool_usage') not in (None, {}):
            return None
        usage = result['usage']
        incoming, outgoing, total = (usage[x] for x in ('input_tokens','output_tokens','total_tokens'))
        cached = usage['input_tokens_details']['cached_tokens']
        cache_writes = usage['input_tokens_details'].get('cache_write_tokens',0)
        reasoning = usage['output_tokens_details']['reasoning_tokens']
        if (any(type(x) is not int or x<0 for x in (incoming,outgoing,total,cached,cache_writes,reasoning)) or
            incoming<=0 or outgoing<=0 or incoming>400_000 or outgoing>max_output_tokens or cached>incoming or reasoning>outgoing or
            total!=incoming+outgoing or cache_writes!=0):
            return None
        # micro-USD / million tokens. Round UP once; never use binary floats.
        return ((incoming-cached)*250_000 + cached*25_000 + outgoing*2_000_000 + 999_999)//1_000_000
    except (KeyError,TypeError,AttributeError):
        return None


def response_pricing_verified(result):
    try:
        return (result.get('model') in MODELS and result.get('service_tier') == 'default' and
                result.get('tool_usage') in (None,{}) and
                result.get('usage',{}).get('input_tokens_details',{}).get('cache_write_tokens',0) == 0)
    except (TypeError,AttributeError):
        return False
