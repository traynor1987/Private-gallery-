#!/usr/bin/env python3
"""Fail-closed arrival-time/route checks for controlled child native UDP probes.

Arrival timestamps cannot distinguish an in-flight pre-revocation datagram from a
later send. Any arrival in a denied interval therefore rejects automated approval
for investigation; no timing tolerance or packet-drop grace silently makes it pass.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
from verify_hidden_vpn_fixture1762 import verify_packet_routes

RECEIPTS = {
 'hidden-continuity17103-result.json': ['actual_child','same_view_profile_opener_app','suspended_io_denied','old_authority_and_proxy_dead','fresh_verified_network','foreign_callback_rejected','one_use_post_replay_rejected','no_get_replay','authenticated_session_verified','cancelled_approval_no_session','panic_revoked','stale_worker_denied_before_key_io'],
 'hidden-continuity17103-expiry.json': ['expired','child_dead','old_authority_dead'],
 'hidden-continuity17103-death.json': ['child_dead','continuation_dead','old_authority_dead','owned_roots_removed'],
 'hidden-continuity17103-denied-panic.json': ['io_denied','panic_revoked','child_dead','hidden_locked','no_resume_commit','shortcut_disabled','sensitive_descendants_concealed'],
 'hidden-continuity17103-accessible-lock.json': ['io_denied','lock_revoked','child_dead','hidden_locked','no_resume_commit','shortcut_disabled','sensitive_descendants_concealed','touch_exploration'],
 'hidden-continuity17103-main.json': ['production_main_runtime','primary_expired','same_view_profile_app','protected_website_session','foreground_route_loss','panic_revoked'],
 'hidden-continuity17103-main-suspend-race.json': ['production_main_runtime','actual_on_stop','concurrent_route_loss','native_probe_positive','same_view'],
 'hidden-continuity17103-main-races.json': ['production_main_runtime'],
}
ACTIONS = {'background','screen_off','expiry','lock','panic','replacement'}
PHASES = {'verification','bound_before_commit'}

def number(value,label):
 if type(value) not in (float,int) or not math.isfinite(value) or value<=0:raise ValueError('Invalid time '+label)
 return value

def validate_receipts(receipts):
 if set(receipts)!=set(RECEIPTS):raise ValueError('Missing/extra continuity receipt')
 for name,flags in RECEIPTS.items():
  data=receipts[name]
  if not isinstance(data,dict) or any(data.get(flag) is not True for flag in flags):raise ValueError('Nonpassing receipt '+name)
 success=receipts['hidden-continuity17103-result.json']
 if type(success.get('sites')) is not int or success['sites']!=2 or type(success.get('website_sessions')) is not int or success['website_sessions']!=2:raise ValueError('Two authenticated website sessions required')
 for name,flag in [('hidden-continuity17103-denied-panic.json','touch_exploration'),('hidden-continuity17103-denied-panic.json','lock_revoked'),('hidden-continuity17103-accessible-lock.json','panic_revoked')]:
  if receipts[name].get(flag) is not False:raise ValueError('Wrong control scenario '+name)
 cases=receipts['hidden-continuity17103-main-races.json'].get('cases')
 if not isinstance(cases,list) or len(cases)!=12:raise ValueError('Twelve transition scenarios required')
 identities=[]
 for case in cases:
  if not isinstance(case,dict):raise ValueError('Invalid transition case')
  identities.append((case.get('phase'),case.get('action')))
 if set(identities)!={(phase,action) for phase in PHASES for action in ACTIONS} or len(set(identities))!=12:raise ValueError('Missing/duplicate transition scenario')
 return cases

def validate(events,receipts,packets,pcap):
 cases=validate_receipts(receipts)
 verify_packet_routes(packets)
 if len(pcap)<=24:raise ValueError('Missing full PCAP evidence')
 if not isinstance(events,list) or not events:raise ValueError('Missing fixture events')
 probes=[]
 for event in events:
  number(event.get('time'),'fixture event')
  if event.get('kind')=='stun':
   if event.get('source')!='10.91.0.2':raise ValueError('Native UDP was not from the verified WireGuard peer')
   if event.get('continuity_probe') is True:probes.append(event['time'])
 if not probes:raise ValueError('No marked native child probe baseline')
 def count(start,end):return sum(start<=at<end for at in probes)
 def denied(start,end,label):
  if count(start,end):raise ValueError('Native probe arrival in denied interval '+label+'; send time/in-flight attribution is unresolved, not silently tolerated')
 def baseline(start):
  total=count(start-30,start)
  if total<1:raise ValueError('Missing positive native probe immediately before transition')
  return total
 suspension=receipts['hidden-continuity17103-main-suspend-race.json']
 times=[number(suspension.get(key),key) for key in ('suspend_started','denied_confirmed','transport_published','resumed')]
 if times!=sorted(times) or not times[0]<times[1]<times[2]<=times[3] or times[1]-times[0]>15 or times[3]-times[0]>240:raise ValueError('Invalid suspension handoff timing')
 denied(times[1],times[2],'suspension handoff')
 summary=[dict(phase='suspension_handoff',baseline=baseline(times[0]),shutdown_arrivals=count(times[0],times[1]),verified_route_arrivals=count(times[2],times[3]),denied_arrivals=0)]
 windows=[(times[0],times[3])]
 for case in cases:
  phase,action=case['phase'],case['action']
  if any(case.get(key) is not True for key in ('native_probe_positive','proxy_denied','retired')):raise ValueError('Missing transition proof '+phase+'/'+action)
  if case.get('actual_task_background') is not (phase=='verification' and action=='background') or case.get('injected_on_pause') is not (phase=='bound_before_commit' and action=='background'):raise ValueError('Misrepresented lifecycle injection')
  start,denial,boundary,requested,loss,death=[number(case.get(key),key) for key in ('suspend_started','denied_confirmed','boundary_reached','action_requested','authority_loss','child_dead')]
  if not start<denial<=boundary<=requested<=loss<=death or denial-start>15 or death-start>240 or death-loss>15:raise ValueError('Invalid transition order/bounds '+phase+'/'+action)
  if action=='expiry':
   expected='real_30s' if phase=='bound_before_commit' else 'injected_continuation_monotonic'
   if case.get('expiry_mode')!=expected or case.get('expiry_current_live_before') is not True or case.get('expiry_original_deadline_preserved') is not True or case.get('expiry_original_grace_ms')!=30000:raise ValueError('Missing actual expiry cause attribution')
   number(case.get('expiry_original_stop_elapsed_ms'),'original continuation stop')
   if phase=='verification':
    advance=number(case.get('expiry_advanced'),'injected monotonic advance')
    if not requested<=advance<=loss or loss-advance>2:raise ValueError('Expiry preceded advance or could be DNS timeout')
   elif case.get('expiry_advanced') is not None or loss-start<30:raise ValueError('Bound expiry was not real original grace')
  elif any(case.get(key) is not None for key in ('expiry_mode','expiry_advanced','expiry_original_grace_ms','expiry_original_stop_elapsed_ms')) or case.get('expiry_current_live_before') is not False or case.get('expiry_original_deadline_preserved') is not False:raise ValueError('Unexpected expiry attribution')
  windows.append((start,death))
  published=case.get('transport_published')
  if phase=='bound_before_commit':
   published=number(published,'transport_published')
   if not denial<=published<=boundary:raise ValueError('Publication outside verified boundary')
   denied(denial,published,phase+'/'+action+' before publication')
  else:
   if published is not None:raise ValueError('Verification scenario published a child route')
   denied(denial,loss,phase+'/'+action+' before revocation')
  denied(loss,math.nextafter(death,math.inf),phase+'/'+action+' hard revocation')
  summary.append(dict(phase=phase,action=action,baseline=baseline(start),shutdown_arrivals=count(start,denial),verified_route_arrivals=count(published,loss) if published else 0,denied_arrivals=0,revoked_arrivals=0))
 windows.sort()
 if any(left[1]>=right[0] for left,right in zip(windows,windows[1:])):raise ValueError('Overlapping scenario windows')
 return dict(schema=1,verdict='PASS',full_pcap_route=True,verified_peer='10.91.0.2',transition_cases=12,suspension_handoffs=1,
             clock_policy='strict fixture arrival timestamps; no edge trimming or automatic in-flight allowance',scenarios=summary)

def digest(data):return hashlib.sha256(data).hexdigest()
def evaluate(event_bytes,receipt_bytes,packet_bytes,pcap_bytes):
 events=[json.loads(line) for line in event_bytes.splitlines() if line.strip()]
 result=validate(events,{name:json.loads(data) for name,data in receipt_bytes.items()},packet_bytes.decode(),pcap_bytes)
 result.update(events_sha256=digest(event_bytes),packets_sha256=digest(packet_bytes),pcap_sha256=digest(pcap_bytes),receipts_sha256={name:digest(data) for name,data in sorted(receipt_bytes.items())})
 return result

def main():
 parser=argparse.ArgumentParser();parser.add_argument('folder',type=Path);args=parser.parse_args();root=args.folder
 result=evaluate((root/'events.jsonl').read_bytes(),{name:(root/name).read_bytes() for name in RECEIPTS},(root/'packets.txt').read_bytes(),(root/'traffic.pcap').read_bytes())
 (root/'continuity-transition-result.json').write_text(json.dumps(result,sort_keys=True,indent=2)+'\n')
 print('PASS: all 12 transition cases and suspension handoff; full captured route check; strict native-probe arrival denial')
if __name__=='__main__':main()
