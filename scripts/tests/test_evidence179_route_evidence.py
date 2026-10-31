import copy
import json
from pathlib import Path
import tempfile
import unittest
from scripts import verify_evidence179_routes as v


class RouteEvidenceTest(unittest.TestCase):
 def fixture(self,case='vpn_revoke'):
  method,mode,phases=v.CASES[case];nonce='a'*32;onion='a'*56+'.onion';source='b'*40
  value=dict(schema=1,case=case,method=method,classname=v.CLASS,mode=mode,nonce=nonce,source_sha=source,owner='HIDDEN',uid=10123,network_owner_uid=10123,network_handle=42,interface='tun0',route_identity='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',process_binding_before_null=True,process_binding_after_null=True,cleanup_retired=True,response_sha256=v.sha(v.RESPONSE),operations=[])
  events=[];dns=[]
  for phase in phases:
   host=onion if mode=='Tor' else f'e179-{nonce}-{phase}.fixture.invalid'
   path='/evidence179/post' if phase=='success' else f'/evidence179/slow/{case}/{phase}'
   body=f'{{"case":"{case}","nonce":"{nonce}","phase":"{phase}"}}'.encode()
   op=dict(phase=phase,host=host,path=path,body_length=len(body),body_sha256=v.sha(body));value['operations'].append(op)
   if phase=='retired':continue
   observed=[]
   for kind in (['post','response'] if phase=='success' else ['post','slow-start','action-arm','slow-closed']):
    seq=len(events)+1;event=dict(op,kind=kind,case=case,nonce=nonce,sequence=seq,epoch=1000+seq,monotonic=seq,source='127.0.0.1' if mode=='Tor' else '10.91.0.2',method='POST',tls=True,auth_present=False,cookie_present=False)
    if kind=='response':event['response_sha256']=v.sha(v.RESPONSE);value['success_observation']=seq
    if kind=='action-arm':event['action_token']='d'*32
    if kind=='slow-closed':event['close_type']='BrokenPipeError';event['close_errno']=32;event['close_evidence']='broken_pipe'
    events.append(event);observed.append(event)
   action=dict(arm=dict(sequence=observed[2]['sequence'],token='d'*32,sent_elapsed_ns=10_000_000_000,received_elapsed_ns=10_010_000_000),action_elapsed_ns=10_020_000_000,request_failed_elapsed_ns=10_030_000_000,live_at_action=True,published=False) if phase!='success' else {}
   if phase=='cancel':value['cancel']=dict(action,start=observed[1]['sequence'],closed=observed[3]['sequence'],cancelled=True)
   elif phase!='success':value['retirement']=dict(action,phase=phase,admitted_sequence=observed[1]['sequence'],admitted_epoch=observed[1]['epoch'],active_failed=True,old_route_current=False,new_post_rejected=True,credentials_retained=case in ('vpn_loss','tor_death'),wireguard_retained=case=='vpn_revoke',network_lost=case=='vpn_loss',tor_absent=mode=='Tor',admission_observed_elapsed_ns=9_000_000_000,transport_retired_elapsed_ns=10_040_000_000,remote_close='unavailable_after_path_loss' if case=='vpn_loss' else 'required_peer_close')
   if mode=='VPN':dns += [dict(name=host,qtype=q,source='10.91.0.2',time=1001) for q in (1,28)]
  if mode=='Tor':value['tor']=dict(pid=123,uid=10123,parent_uid=10123,socket_uid=10123,parent_mode=448,socket_type=49152,unix_ready=True,bootstrap=100,unix_relative='ts/synthetic/socket');value['sink']=dict(v4=1,v6=1,udp=3)
  return value,events,dns,onion,source,dict(start=999,end=1100)
 def test_all_four_complete_cross_observations_are_accepted(self):
  for case in v.CASES:
   data=self.fixture(case);v.check_events(data[1]);self.assertEqual(v.check_case(*data),'a'*32)
 def test_missing_duplicate_retry_and_timeout_receipts_fail(self):
  for mutation in ('missing','duplicate','timeout','retired','forged_digest','wrong_nonce','plaintext','auth'):
   with self.subTest(mutation=mutation):
    data=list(self.fixture());events=data[1]
    if mutation=='missing':events.pop()
    if mutation=='duplicate':events.insert(1,copy.deepcopy(events[0]))
    if mutation=='timeout':events[-1]['kind']='slow-timeout'
    if mutation=='retired':events[-1]['phase']='retired'
    if mutation=='forged_digest':events[0]['body_sha256']='0'*64
    if mutation=='wrong_nonce':events[0]['nonce']='c'*32
    if mutation=='plaintext':events[0]['tls']=False
    if mutation=='auth':events[0]['auth_present']=True
    with self.assertRaises(ValueError):v.check_case(*data)
 def test_source_dns_and_operation_forgery_fail(self):
  for field in ('source','dns_missing','dns_wrongpeer','dns_retired','operation','success_id','admission_id','window','network_owner','credentials'):
   data=list(self.fixture());value,events,dns=data[:3]
   if field=='source':value['source_sha']='c'*40
   if field=='dns_missing':dns.pop()
   if field=='dns_wrongpeer':dns[0]['source']='10.0.2.15'
   if field=='dns_retired':dns.append(dict(name=value['operations'][-1]['host'],qtype=1,source='10.91.0.2',time=1001))
   if field=='operation':value['operations'][0]['body_sha256']='0'*64
   if field=='success_id':value['success_observation']=999
   if field=='admission_id':value['retirement']['admitted_sequence']=1
   if field=='window':data[-1]['start']=1005
   if field=='network_owner':value['network_owner_uid']=999
   if field=='credentials':value['retirement']['credentials_retained']=True
   with self.subTest(field=field),self.assertRaises(ValueError):v.check_case(*data)
 def test_private_unix_sink_and_death_must_be_observed(self):
  for field in ('mode','uid','socket','path','bootstrap','sink','process','retained'):
   data=list(self.fixture('tor_death'));value=data[0]
   if field=='mode':value['tor']['parent_mode']=493
   if field=='uid':value['tor']['socket_uid']=0
   if field=='socket':value['tor']['socket_type']=32768
   if field=='path':value['tor']['unix_relative']='ts/../public.sock'
   if field=='bootstrap':value['tor']['bootstrap']=95
   if field=='sink':value['sink']['udp']=2
   if field=='process':value['retirement']['tor_absent']=False
   if field=='retained':value['retirement']['credentials_retained']=False
   with self.subTest(field=field),self.assertRaises(ValueError):v.check_case(*data)
 def test_loss_requires_honest_unavailable_remote_close_with_all_local_proof(self):
  data=list(self.fixture('vpn_loss'));data[1].pop() # Genuine blackhole may never deliver close.
  self.assertEqual(v.check_case(*data),'a'*32)
  data[0]['retirement']['remote_close']='required_peer_close'
  with self.assertRaises(ValueError):v.check_case(*data)
 def test_review_r2_negative_duration_generic_tls_and_preaction_failure_rejected(self):
  for case in ('vpn_loss','vpn_revoke','tor_death'):
   for defect in ('negative_epoch','generic_tls_1','preaction_failure','preaction_host_close','not_live','published','wrong_arm_token'):
    data=list(self.fixture(case));action=data[0]['retirement'];terminal=data[1][-1];arm=data[1][-2]
    if defect=='negative_epoch':terminal['epoch']=data[1][-3]['epoch']-.5
    if defect=='generic_tls_1':terminal.update(close_type='SSLError',close_errno=1,close_evidence='tls_eof')
    if defect=='preaction_failure':action['request_failed_elapsed_ns']=action['action_elapsed_ns']-1
    if defect=='preaction_host_close':terminal['monotonic']=arm['monotonic']+.001
    if defect=='not_live':action['live_at_action']=False
    if defect=='published':action['published']=True
    if defect=='wrong_arm_token':action['arm']['token']='e'*32
    with self.subTest(case=case,defect=defect),self.assertRaises(ValueError):v.check_case(*data)
 def test_duplicate_keys_and_nonfinite_json_are_rejected(self):
  for raw in ('{"case":1,"case":2}','{"epoch":NaN}','{"epoch":Infinity}'):
   with self.assertRaises(ValueError):v.decode(raw)
 def test_missing_xml_and_duplicate_case_identities_fail(self):
  with tempfile.TemporaryDirectory() as directory:
   p=Path(directory);manifest=[dict(classname=v.CLASS,name=v.CASES['vpn_loss'][0])]
   with self.assertRaises(ValueError):v.verify_cases(p,manifest)
   case=f'<testcase classname="{v.CLASS}" name="{manifest[0]["name"]}"/>'
   (p/'test.xml').write_text('<testsuite>'+case+case+'</testsuite>')
   with self.assertRaises(ValueError):v.verify_cases(p,manifest)
   for tag in ('skipped','failure','error'):
    (p/'test.xml').write_text('<testsuite>'+case.replace('/>',f'><{tag}/></testcase>')+'</testsuite>')
    with self.assertRaises(ValueError):v.verify_cases(p,manifest)


class PacketEvidenceTest(unittest.TestCase):
 def fixture(self):
  value,*_=RouteEvidenceTest().fixture('vpn_loss');tor,*_=RouteEvidenceTest().fixture('tor_death');tor['nonce']='b'*32
  stamp='1970-01-01 00:16:41.000000'
  packets=[f'{stamp} pg1762 In IP (tos 0x0, ttl 64, proto TCP (6))\n    10.91.0.2.50000 > 203.0.113.10.443: Flags [P.]\n']
  for op in value['operations'][:-1]:
   for kind in ('A','AAAA'):packets.append(f'{stamp} pg1762 In IP (tos 0x0, ttl 64, proto UDP (17))\n    10.91.0.2.50000 > 10.91.0.1.53: 1+ {kind}? {op["host"]}. (70)\n')
  return ''.join(packets),dict(vpn_loss=value,tor_death=tor),dict(vpn_loss=dict(start=1000,end=1010),tor_death=dict(start=1020,end=1030)),'a'*56+'.onion',dict(start=999,end=1040)
 def test_precise_vpn_and_quiet_tor_windows_pass(self):v.check_packets(*self.fixture())
 def test_direct_dns_v4_v6_onion_and_wrong_window_fail(self):
  for extra in ('origin','dns','v6','onion','tor_window','stale','empty'):
   data=list(self.fixture())
   if extra=='origin':data[0]=data[0].replace('pg1762','eth0',1)
   if extra=='dns':data[0]=data[0].replace('pg1762 In IP (tos 0x0, ttl 64, proto UDP','eth0 In IP (tos 0x0, ttl 64, proto UDP',1)
   if extra=='v6':data[0]+='1970-01-01 00:17:01.000000 eth0 Out IP6 2001:db8::10\n'
   if extra=='onion':data[0]+='1970-01-01 00:17:01.000000 eth0 Out IP DNS '+data[3]+'\n'
   if extra=='tor_window':data[2]['tor_death']=dict(start=1000,end=1030)
   if extra=='stale':data[4]['start']=1002
   if extra=='empty':data[0]=''
   with self.subTest(extra=extra),self.assertRaises(ValueError):v.check_packets(*data)


class StaticBoundaryTest(unittest.TestCase):
 def test_frozen_manifest_has_four_actual_routes_and_no_broad_legacy_suite(self):
  data=v.read('scripts/evidence179_cases.json');pairs={(x['classname'],x['name']) for x in data}
  self.assertEqual(len(pairs),len(data))
  for method,_,_ in v.CASES.values():self.assertIn((v.CLASS,method),pairs)
  other=[x for x in data if '179' not in x['classname']]
  self.assertEqual({x['name'] for x in other},{'panicRevokesBothSessionsWipesKeyAndNeverReopensHidden','concealedTabsRestartAndPanicRetainFlagWithoutDeletingSyntheticState'})
 def test_onion_trust_is_debug_only_and_exact_ca(self):
  import xml.etree.ElementTree as ET
  for name in ('hidden_test_network_security.xml','hidden_network_security.xml'):
   root=ET.parse(Path('app/src/debug/res/xml')/name).getroot()
   configs=[x for x in root.findall('domain-config') if any(d.text=='onion' for d in x.findall('domain'))]
   self.assertEqual(len(configs),1)
   self.assertIn('@raw/hidden_vpn_fixture_ca',[x.get('src') for x in configs[0].findall('trust-anchors/certificates')])
  for path in Path('app/src/main/res/xml').glob('*network_security*.xml'):
   self.assertNotIn('hidden_vpn_fixture_ca',path.read_text())
 def test_certificate_identity_rejects_wrong_onion_and_untrusted_ca(self):
  from scripts.create_hidden_vpn_fixture_tls1762 import generate
  with tempfile.TemporaryDirectory() as directory:
   root=Path(directory);host='a'*56+'.onion';generate(root,onion_host=host)
   self.assertEqual(v.certificate_identity(root,[host])['san'],[host])
   with self.assertRaises(ValueError):v.certificate_identity(root,['b'*56+'.onion'])
   # A syntactically valid public certificate cannot replace the trusted CA.
   (root/'fixture-ca.crt').write_bytes((root/'fixture.crt').read_bytes())
   with self.assertRaises(ValueError):v.certificate_identity(root,[host])

if __name__=='__main__':unittest.main()
