#!/usr/bin/env python3
"""Fail-closed original-key signing approval for the exact 1.7.7 candidate."""
import hashlib,json,re,sys
from pathlib import Path
CERTIFICATE='94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17'
REQUIRED={'carried_forward_repairs','independent_encrypted_bins','verified_restore','permanent_delete','retention','authenticated_scheduling','interrupted_operations','authentication_revocation','hidden_pin_only','egress_exclusion','thumbnails','browser_transport','samsung_editor','existing_media_preservation'}
def validate(approval,source,review,run):
 assert re.fullmatch('[a-f0-9]{40}',source)
 assert approval['source_sha']==source
 assert (approval['version'],approval['version_code'],approval['package'])==('1.7.7',46,'uk.co.traynor.privategallery')
 assert approval['certificate_sha256']==CERTIFICATE
 assert set(approval['scope'])==REQUIRED and set(approval['scope'].values())=={'PASS'}
 independent=approval['independent_review']
 assert independent['source_sha']==source and independent['decision']=='APPROVED_FOR_SIGNING'
 assert independent['critical_open']==0 and independent['important_open']==0
 assert independent['report_sha256']==hashlib.sha256(review).hexdigest()
 assert approval['physical_acceptance']=='OWNER_REQUIRED' and approval['required_upgrade_from']==45
 assert run['source_sha']==source and run['conclusion']=='success'
 assert run['verified_build_steps'] is True and run['evidence_validation']=='PASS'
 assert run['original_exit_status']==0 and run['validated_case_count']==21
 assert re.fullmatch('sha256:[a-f0-9]{64}',run['artifact_digest'])
if __name__=='__main__':
 validate(json.loads(Path(sys.argv[1]).read_text()),sys.argv[2],Path(sys.argv[3]).read_bytes(),json.loads(Path(sys.argv[4]).read_text()))
 print('PASS: exact reviewed source and successful focused evidence; original-key45→46 upgrade remains mandatory')
