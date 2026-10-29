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
 require(proof.get('version')=='1.7.6.2' and type(proof.get('version_code')) is int and proof['version_code']==45,'Wrong release identity')
 require(proof.get('package')=='uk.co.traynor.privategallery' and proof.get('certificate_sha256')==CERTIFICATE,'Wrong package or original certificate')
 scope=proof.get('scope',{})
 require(isinstance(scope,dict) and set(scope)==REQUIRED and all(v=='PASS' for v in scope.values()),'Original scope missing, deferred or unverified')
 require(type(proof.get('focused_run_id')) is int and proof['focused_run_id']==run_id and run_id>0,'Focused run mismatch')
 review=proof.get('independent_review',{})
 require(review.get('source_sha')==source and review.get('decision')=='APPROVED_FOR_SIGNING','Independent review missing or different source')
 require(type(review.get('critical_open')) is int and type(review.get('important_open')) is int and review['critical_open']==review['important_open']==0,'Open security findings')
 require(re.fullmatch('[0-9a-f]{64}',review.get('report_sha256','')) is not None,'Review digest missing')
 require(proof.get('physical_fold_acceptance')=='OWNER_REQUIRED' and proof.get('physical_samsung_acceptance')=='OWNER_REQUIRED','Do not claim physical acceptance from CI')
def validate_focused(focused,source):
 require(focused.get('source_sha')==source,'Focused evidence source mismatch')
 if focused.get('conclusion')=='success':return
 # One immutable completed run had all13 Android cases pass, then a proven
 # host-ADB DNS-question-order parser false positive. Never accept other failed runs.
 require(source=='97e94452ceb9c5262521d7552f0c25eeb07374eb' and focused.get('run_id')==40848723808 and focused.get('artifact_id')==12887270723,'Unreviewed failed run')
 require(focused.get('conclusion')=='failure' and focused.get('original_exit_status')==1,'Original failure must be preserved')
 require(focused.get('artifact_digest')=='sha256:b01b11d5ba2bbfeadfa097957b1ca5b7ab2313c502c1a622617a1abf7457a2fc','Wrong immutable capture')
 require(focused.get('verified_build_steps') is True and focused.get('evidence_validation')=='PASS' and focused.get('validated_case_count')==13,'Missing fresh pre-sign evidence validation')
 require(focused.get('reviewed_validator_sha256')=='a9359061347d0dd79bf527fe724dcbceb4e7f998332e19570c922f33eb33e3c0','Wrong reviewed packet validator')
def validate_activation(gate):
 # Evidence must describe enabled production modes, not a matching comment or old declaration.
 code=re.sub(r'/\*.*?\*/|//[^\n]*','',gate,flags=re.S)
 for name in ('INTERACTIVE_VERIFIED','TOR_NATIVE_VERIFIED'):
  values=re.findall(r'^\s*const val '+name+r'\s*=\s*(true|false)\s*$',code,flags=re.M)
  require(values==['true'],f'Production browser mode disabled or ambiguous: {name}')
if __name__=='__main__':
 approval,source,report_path,focused_path=sys.argv[1:]
 proof=json.loads(Path(approval).read_text());focused=json.loads(Path(focused_path).read_text())
 require(subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()==source,'Wrong checkout')
 validate_focused(focused,source)
 validate(proof,source,focused['run_id']);report=Path(report_path).read_bytes()
 require(hashlib.sha256(report).hexdigest()==proof['independent_review']['report_sha256'],'Review bytes differ from approval')
 require(source in report.decode(),'Review does not name exact source')
 gate=Path('app/src/main/java/uk/co/traynor/privategallery/core/browser/hidden/HiddenVpnActivationGate.kt').read_text()
 validate_activation(gate)
 print('PASS: exact full-scope source and independent security approval; physical acceptance remains owner-required')
