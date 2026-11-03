"""Synthetic validator inputs only: these tests are never real packet approval."""
import copy
import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from verify_hidden_continuity_packets17103 import RECEIPTS, evaluate, validate

PACKETS='2026-11-03 10:00:00.000000 pg1762 In IP 10.91.0.2.42000 > 203.0.113.10.3478: UDP\n2026-11-03 10:00:00.100000 pg1762 In IP 10.91.0.2.42001 > 10.91.0.1.53: A? fixture.invalid.\n'
PCAP=b'SYNTHETIC_UNIT_TEST_NOT_REAL_PCAP'*2

def fixture():
 receipts={name:dict.fromkeys(flags,True) for name,flags in RECEIPTS.items()}
 receipts['hidden-continuity17103-result.json'].update(sites=2,website_sessions=2)
 receipts['hidden-continuity17103-denied-panic.json'].update(touch_exploration=False,lock_revoked=False)
 receipts['hidden-continuity17103-accessible-lock.json']['panic_revoked']=False
 start=1700000000.0
 receipts['hidden-continuity17103-main-suspend-race.json'].update(suspend_started=start,denied_confirmed=start+1,transport_published=start+2,resumed=start+3)
 events=[]
 def probe(at):events.append(dict(kind='stun',time=at,source='10.91.0.2',continuity_probe=True))
 probe(start-.2);probe(start+.2);probe(start+2.1)
 cases=[]
 for phase in ('verification','bound_before_commit'):
  for action in ('background','screen_off','expiry','lock','panic','replacement'):
   start+=100
   boundary=start+(22 if phase=='bound_before_commit' and action=='expiry' else 2)
   loss=start+30.1 if phase=='bound_before_commit' and action=='expiry' else boundary+.2
   case=dict(phase=phase,action=action,suspend_started=start,denied_confirmed=start+1,boundary_reached=boundary,action_requested=boundary+.1,authority_loss=loss,child_dead=loss+.1,
             transport_published=boundary-.1 if phase=='bound_before_commit' else None,native_probe_positive=True,proxy_denied=True,retired=True,
             actual_task_background=phase=='verification' and action=='background',injected_on_pause=phase=='bound_before_commit' and action=='background',
             expiry_mode=('real_30s' if phase=='bound_before_commit' else 'injected_continuation_monotonic') if action=='expiry' else None,
             expiry_advanced=boundary+.15 if phase=='verification' and action=='expiry' else None,expiry_original_grace_ms=30000 if action=='expiry' else None,
             expiry_original_stop_elapsed_ms=10000 if action=='expiry' else None,expiry_current_live_before=action=='expiry',expiry_original_deadline_preserved=action=='expiry')
   cases.append(case);probe(start-.2);probe(start+.2)
   if phase=='bound_before_commit':probe(boundary)
 receipts['hidden-continuity17103-main-races.json']['cases']=cases
 return events,receipts,PACKETS,PCAP

class TransitionPacketGate(unittest.TestCase):
 def test_all_twelve_complete_cases_and_untrimmed_shutdown_and_verified_windows(self):
  result=validate(*fixture())
  self.assertEqual(12,result['transition_cases']);self.assertEqual(13,len(result['scenarios']))
  self.assertTrue(result['full_pcap_route'])
 def test_denied_and_revoked_arrivals_at_edges_are_not_trimmed(self):
  for index in range(12):
   for interval in ('denied_confirmed','authority_loss','child_dead'):
    events,receipts,packets,pcap=fixture();case=receipts['hidden-continuity17103-main-races.json']['cases'][index]
    events.append(dict(kind='stun',time=case[interval],source='10.91.0.2',continuity_probe=True))
    with self.subTest(case=index,edge=interval),self.assertRaisesRegex(ValueError,'arrival in denied interval'):validate(events,receipts,packets,pcap)
 def test_suspend_handoff_rejects_probe_before_actual_publication(self):
  events,receipts,packets,pcap=fixture();at=receipts['hidden-continuity17103-main-suspend-race.json']['transport_published']-.000001
  events.append(dict(kind='stun',time=at,source='10.91.0.2',continuity_probe=True))
  with self.assertRaises(ValueError):validate(events,receipts,packets,pcap)
 def test_foreign_peer_and_default_route_are_never_exempt(self):
  for source in ('10.0.2.15','127.0.0.1',None):
   events,receipts,packets,pcap=fixture();events[0]['source']=source
   with self.assertRaises(ValueError):validate(events,receipts,packets,pcap)
  for interface in ('eth0','lo','wlan0'):
   events,receipts,packets,pcap=fixture()
   with self.assertRaises(AssertionError):validate(events,receipts,packets.replace('pg1762',interface),pcap)
 def test_missing_baseline_duplicate_scenario_and_invalid_times_fail(self):
  for mutate in (lambda e,r:e.clear(),lambda e,r:r['hidden-continuity17103-main-races.json']['cases'].pop(),
                 lambda e,r:r['hidden-continuity17103-main-races.json']['cases'].__setitem__(1,copy.deepcopy(r['hidden-continuity17103-main-races.json']['cases'][0])),
                 lambda e,r:r['hidden-continuity17103-main-suspend-race.json'].__setitem__('transport_published',float('nan')),
                 lambda e,r:r['hidden-continuity17103-main-races.json']['cases'][0].__setitem__('child_dead',1)):
   events,receipts,packets,pcap=fixture();mutate(events,receipts)
   with self.assertRaises(ValueError):validate(events,receipts,packets,pcap)
 def test_timeout_cannot_impersonate_expiry(self):
  for field,value in [('expiry_mode','real_30s'),('expiry_current_live_before',False),('expiry_original_deadline_preserved',False),('expiry_original_grace_ms',60000),('expiry_advanced',1700009999)]:
   events,receipts,packets,pcap=fixture();case=next(c for c in receipts['hidden-continuity17103-main-races.json']['cases'] if c['phase']=='verification' and c['action']=='expiry');case[field]=value
   with self.subTest(field=field),self.assertRaises(ValueError):validate(events,receipts,packets,pcap)
 def test_result_binds_all_raw_inputs_and_eight_receipts(self):
  events,receipts,packets,pcap=fixture()
  result=evaluate(('\n'.join(map(json.dumps,events))+'\n').encode(),{name:json.dumps(data).encode() for name,data in receipts.items()},packets.encode(),pcap)
  self.assertEqual(8,len(result['receipts_sha256']))
  for key in ('events_sha256','packets_sha256','pcap_sha256'):self.assertEqual(64,len(result[key]))

if __name__=='__main__':unittest.main()
