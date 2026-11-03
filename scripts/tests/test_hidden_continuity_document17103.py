"""Execute the served synthetic website's real message handler, without real accounts."""
import json,re,subprocess,unittest
from scripts.tests.test_hidden_continuity_fixture17103 import Fixture,Handler
from scripts import hidden_continuity_fixture17103 as endpoint
class ContinuityDocumentTest(unittest.TestCase):
 def test_both_served_documents_reject_foreign_origin_and_state_before_callback(self):
  for phase in ('a','b'):
   handler=Handler('/continuity17103/site-'+phase);endpoint.get(handler,Fixture())
   page=handler.response[1].decode();script=re.search(r'<script>(.*?)</script>',page,re.S).group(1)
   click=re.search(r'onclick="([^"]+)">Reject foreign callback',page).group(1)
   program=r'''
const vm=require('vm'),assert=require('assert'),input=JSON.parse(require('fs').readFileSync(0,'utf8'));
(async()=>{let n=0,listener;const status={textContent:'Preparing state'},requests=[];
const origin='https://site-'+input.phase+'.fixture.invalid';
const sandbox={crypto:{randomUUID:()=> 'nonce-'+(++n)},location:{origin},document:{getElementById:()=>status},
 fetch:async(path,options)=>{requests.push({path,body:options?.body});return {ok:true,text:async()=> 'WEBSITE_SESSION_OK'}},
 addEventListener:(kind,fn)=>{if(kind==='message')listener=fn},postMessage:data=>queueMicrotask(()=>listener({origin,data}))};
const context=vm.createContext(sandbox);vm.runInContext(input.script,context);await new Promise(setImmediate);
assert.equal(status.textContent,'SITE_READY');vm.runInContext(input.click,context);await new Promise(setImmediate);
assert.equal(status.textContent,'FOREIGN_REJECTED');vm.runInContext('finish()',context);await new Promise(setImmediate);
assert.equal(requests.filter(r=>r.path.includes('/callback-')).length,0);
listener({origin:'https://foreign.fixture.invalid',data:{state:'nonce-1',approved:true}});assert.equal(status.textContent,'FOREIGN_REJECTED');
listener({origin:'https://provider-'+input.phase+'.fixture.invalid',data:{state:'foreign',approved:true}});assert.equal(status.textContent,'FOREIGN_REJECTED');
vm.runInContext('finish()',context);await new Promise(setImmediate);assert.equal(requests.filter(r=>r.path.includes('/callback-')).length,0);
listener({origin:'https://provider-'+input.phase+'.fixture.invalid',data:{state:'nonce-1',approved:false}});assert.equal(status.textContent,'APPROVAL_CANCELLED');
listener({origin:'https://provider-'+input.phase+'.fixture.invalid',data:{state:'nonce-1',approved:true}});assert.equal(status.textContent,'APPROVAL_RECEIVED');
vm.runInContext('finish()',context);await new Promise(setImmediate);assert.equal(status.textContent,'WEBSITE_SESSION_OK');
assert.deepEqual(requests.filter(r=>r.path.includes('/callback-')).map(r=>r.body),['nonce-1:nonce-2']);
})().catch(e=>{console.error(e);process.exit(1)});
'''
   result=subprocess.run(['node','-e',program],input=json.dumps(dict(phase=phase,script=script,click=click)),text=True,capture_output=True)
   self.assertEqual(result.returncode,0,result.stderr)
