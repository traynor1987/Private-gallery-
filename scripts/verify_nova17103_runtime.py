#!/usr/bin/env python3
"""Exact continuity case accounting plus actual route evidence; never inferred from approval UI."""
import argparse,zipfile,json
from pathlib import Path
from verify_nova17102_runtime import verify
from verify_hidden_continuity_packets17103 import RECEIPTS, evaluate

def validate(path,matrix,source=None):
 selected={(cls,method) for cls,methods in json.loads(matrix.read_text()).items() for method in methods}
 required={
  ('uk.co.traynor.privategallery.core.browser.hidden.NativeTorIsolation1762Test','packagedTorNativeFormsUploadsDownloadsAndNetworkSink'),
  ('uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnLive1762Test','nativeChildPostsFormsUploadsAndRevokesOnOwnedVpn'),
  ('uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnNetworkLoss1762Test','lostOwnedNetworkRetiresNativeChildAndCannotReuseOldCapability'),
  ('uk.co.traynor.privategallery.core.browser.hidden.HiddenBrowserContinuity17103Test','sameOwnedViewsResumeAcrossBackgroundAndOldEpochStaysDead'),
  ('uk.co.traynor.privategallery.core.browser.hidden.HiddenBrowserContinuity17103Test','expiredGraceRetiresRetainedChild'),
  ('uk.co.traynor.privategallery.core.browser.hidden.HiddenBrowserContinuity17103Test','retainedChildProcessDeathCannotResumeOldContinuation'),
  ('uk.co.traynor.privategallery.core.browser.hidden.HiddenBrowserContinuity17103Test','panicDuringDeniedReconnectRevokesHiddenWithoutIoAuthority'),
  ('uk.co.traynor.privategallery.HiddenContinuationPanic17103Test','primaryExpiryDoesNotDisarmCurrentHiddenPanicAndStaleRuntimeIsDenied'),
  ('uk.co.traynor.privategallery.core.browser.hidden.HiddenBrowserContinuity17103Test','touchExplorationCanLockDeniedReconnectWithoutUncoveringPage'),
  ('uk.co.traynor.privategallery.core.browser.hidden.HiddenMainRuntimeContinuity17103Test','productionRuntimePreservesContinuationAfterPrimaryExpiry'),
  ('uk.co.traynor.privategallery.core.browser.hidden.HiddenMainRuntimeContinuity17103Test','productionRuntimeHandlesRouteLossDuringSuspensionHandshake'),
  ('uk.co.traynor.privategallery.core.browser.hidden.HiddenMainRuntimeContinuity17103Test','productionRuntimeRejectsVerificationAndBoundCommitRaces'),
 }
 if not required<=selected:raise ValueError('Required real continuity and fail-closed route cases missing')
 count=verify(path,matrix,require_routes=True)
 with zipfile.ZipFile(path) as z:
  names=z.namelist()
  if source is not None:
   import re
   if not re.fullmatch('[a-f0-9]{40}',source):raise ValueError('Invalid frozen source')
   sources=[n for n in names if n.endswith('nova17102/source-sha.txt')]
   if len(sources)!=1 or z.read(sources[0]).decode().strip()!=source:raise ValueError('Runtime artifact source differs')
  for suffix in ['nova17102/runtime-result.txt','hidden-vpn1762/result.txt','nova17102/tor-route-result.txt','nova17102/baseline-route-accounting.txt','nova17102/baseline-vpn/result.txt']:
   matches=[n for n in names if n.endswith(suffix)]
   if len(matches)!=1 or not z.read(matches[0]).decode().lstrip().startswith('PASS:'):raise ValueError('Missing passing actual route receipt '+suffix)
  def unique(suffix):
   matches=[name for name in names if name.endswith(suffix)]
   if len(matches)!=1:raise ValueError('Missing/duplicate evidence '+suffix)
   return z.read(matches[0])
  receipts={}
  for suffix in RECEIPTS:
   matches=[name for name in names if 'outputs/connected_android_test_additional_output/' in name and name.endswith('/'+suffix)]
   if len(matches)!=1:raise ValueError('Missing/duplicate original actual child receipt '+suffix)
   receipts[suffix]=z.read(matches[0])
   if unique('hidden-vpn1762/continuity-final/'+suffix)!=receipts[suffix]:raise ValueError('Copied child receipt differs '+suffix)
  observed=evaluate(unique('hidden-vpn1762/continuity-final/events.jsonl'),receipts,unique('hidden-vpn1762/continuity-final/packets.txt'),unique('hidden-vpn1762/continuity-final/traffic.pcap'))
  if json.loads(unique('hidden-vpn1762/continuity-final/continuity-transition-result.json'))!=observed:raise ValueError('Transition packet verdict differs from raw evidence')
 return count
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('archive');p.add_argument('--matrix',type=Path,required=True);p.add_argument('--source',required=True);a=p.parse_args()
 print('PASS: exact selected continuity/runtime cases and actual VPN/Tor route receipts;',validate(a.archive,a.matrix,a.source),'cases')
