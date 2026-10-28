#!/usr/bin/env python3
"""Exact-source scope/review gate. Approval is saved in a later control commit, not its own source."""
import hashlib,json,re,subprocess,sys
from pathlib import Path
CERTIFICATE='94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17'
REQUIRED=frozenset(('modal_inventory','cross_vault_authentication','transfer_and_picker_visibility','primary_hidden_interface_parity','interactive_vpn','vpn_dns_and_fail_closed','browser_profile_isolation','tor_transport_and_mode_switch','samsung_supported_api_workflow','fold_automated_layouts','encryption_preservation','upgrade_fixture_review'))
def require(condition,message):
 if not condition:raise ValueError(message)
def validate(proof,source,run_id):
 require(re.fullmatch('[0-9a-f]{40}',source) is not None,'Invalid frozen source')
 require(proof.get('source_sha')==source,'Approval covers another source')
 require(proof.get('version')=='1.7.6.2' and type(proof.get('version_code')) is int and proof['version_code']==44,'Wrong release identity')
 require(proof.get('package')=='uk.co.traynor.privategallery' and proof.get('certificate_sha256')==CERTIFICATE,'Wrong package or original certificate')
 scope=proof.get('scope',{})
 require(isinstance(scope,dict) and set(scope)==REQUIRED and all(v=='PASS' for v in scope.values()),'Original scope missing, deferred or unverified')
 require(type(proof.get('focused_run_id')) is int and proof['focused_run_id']==run_id and run_id>0,'Focused run mismatch')
 review=proof.get('independent_review',{})
 require(review.get('source_sha')==source and review.get('decision')=='APPROVED_FOR_SIGNING','Independent review missing or different source')
 require(type(review.get('critical_open')) is int and type(review.get('important_open')) is int and review['critical_open']==review['important_open']==0,'Open security findings')
 require(re.fullmatch('[0-9a-f]{64}',review.get('report_sha256','')) is not None,'Review digest missing')
 require(proof.get('physical_fold_acceptance')=='OWNER_REQUIRED' and proof.get('physical_samsung_acceptance')=='OWNER_REQUIRED','Do not claim physical acceptance from CI')
if __name__=='__main__':
 approval,source,report_path,focused_path=sys.argv[1:]
 proof=json.loads(Path(approval).read_text());focused=json.loads(Path(focused_path).read_text())
 require(subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()==source,'Wrong checkout')
 require(focused.get('source_sha')==source and focused.get('conclusion')=='success','Focused source not successful')
 validate(proof,source,focused['run_id']);report=Path(report_path).read_bytes()
 require(hashlib.sha256(report).hexdigest()==proof['independent_review']['report_sha256'],'Review bytes differ from approval')
 require(source in report.decode(),'Review does not name exact source')
 gate=Path('app/src/main/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenVpnActivationGate.kt').read_text()
 require(re.search(r'\bconst val INTERACTIVE_VERIFIED\s*=\s*true\b',gate) is not None,'Production interactive browsing disabled')
 print('PASS: exact full-scope source and independent security approval; physical acceptance remains owner-required')
