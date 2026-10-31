#!/usr/bin/env python3
"""Fail-closed exact-source 1.7.8 signing gate; never itself signs or approves source."""
import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

CERTIFICATE = '94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17'
BASELINE = '04b91ae01c64323aac02975672d6046dfba62e30'
REQUIRED = {
    'encrypted_catalogue', 'vault_and_transport_ownership', 'library_and_app_controls',
    'ordering_and_favourites', 'pwa_discovery_and_fallback', 'bounded_website_and_custom_icons',
    'fresh_pin_authentication', 'honest_shared_profile_session_controls',
    'hidden_ephemeral_login_cleanup', 'owned_uploads_and_downloads',
    'encrypted_document_lifecycle', 'explicit_screenshot_policy',
    'cancellation_and_stale_callback_rejection', 'vpn_tor_dns_packet_isolation',
    'revocation_and_process_death', 'light_theme_fold_and_accessibility',
    'existing_media_pin_collections_settings_browser_preservation',
    'recently_deleted_preservation', 'android17_only',
}
NETWORK = {'owned_vpn', 'native_tor', 'dns_and_packet_observation', 'revocation'}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def cases_from_manifest(raw):
    manifest = json.loads(raw)
    require(isinstance(manifest, list) and len(manifest) > 0, 'Empty/non-list testcase manifest')
    cases = []
    for entry in manifest:
        require(set(entry) == {'classname', 'name'}, 'Invalid testcase manifest entry')
        pair = (entry['classname'], entry['name'])
        require(all(isinstance(value, str) and value for value in pair), 'Invalid testcase identity')
        cases.append(pair)
    require(len(cases) == len(set(cases)), 'Duplicate expected testcase')
    return set(cases)


def verify_xml(root, expected):
    paths = list(Path(root).rglob('*.xml'))
    require(bool(paths), 'Missing Android XML evidence')
    actual = []
    for path in paths:
        for case in ET.parse(path).getroot().iter('testcase'):
            require(not any(case.find(tag) is not None for tag in ('failure', 'error', 'skipped')), 'Failed/error/skipped testcase')
            actual.append((case.get('classname'), case.get('name')))
    require(len(actual) == len(set(actual)), 'Duplicate executed testcase')
    require(set(actual) == expected, f'Exact testcase set differs: missing={expected-set(actual)}, extra={set(actual)-expected}')
    return len(actual)


def validate(approval, source, review, run, gates, manifest, fixture, validator):
    require(bool(re.fullmatch('[a-f0-9]{40}', source)) and source != '0'*40, 'Unfrozen source')
    require(approval.get('source_sha') == source and approval.get('decision') == 'APPROVED', 'Source not approved')
    require((approval.get('version'), approval.get('version_code'), approval.get('package')) == ('1.7.8', 48, 'uk.co.traynor.privategallery'), 'Wrong identity')
    require(approval.get('certificate_sha256') == CERTIFICATE, 'Wrong signer')
    require(approval.get('required_upgrade_from') == 47 and approval.get('baseline_source_sha') == BASELINE, 'Wrong frozen baseline')
    scope = approval.get('scope', {})
    require(set(scope) == REQUIRED and set(scope.values()) == {'PASS'}, 'Incomplete original feature scope')
    independent = approval.get('independent_review', {})
    require(independent.get('source_sha') == source and independent.get('decision') == 'APPROVED', 'Independent review not approved')
    require(all(type(independent.get(name)) is int and independent[name] == 0 for name in ('critical_open', 'important_open')), 'Open review findings')
    require(independent.get('report_sha256') == digest(review), 'Review hash changed')
    decisions = re.findall(r'^## Final machine-readable decision\s*\n```json\n(.*?)\n```', review.decode(), re.M | re.S)
    require(len(decisions) == 1, 'Missing/duplicate final review decision')
    require(json.loads(decisions[0]) == {key: independent[key] for key in ('source_sha', 'decision', 'critical_open', 'important_open')}, 'Review decision differs')
    for name, raw in (('case_manifest_sha256', manifest), ('upgrade_fixture_sha256', fixture), ('validator_sha256', validator)):
        require(approval.get(name) == digest(raw), f'Unreviewed control: {name}')
    cases = cases_from_manifest(manifest)
    for name in ('run_id', 'artifact_id'):
        require(type(run.get(name)) is int and run[name] > 0 and approval.get('focused_' + name) == run[name], f'Wrong {name}')
    require(re.fullmatch('sha256:[a-f0-9]{64}', run.get('artifact_digest', '')) is not None and run['artifact_digest'] != 'sha256:'+'0'*64, 'Invalid artifact digest')
    require(approval.get('focused_artifact_digest') == run['artifact_digest'], 'Unreviewed artifact')
    require(run.get('source_sha') == source and run.get('conclusion') == 'success', 'Wrong/failed run source')
    require(run.get('workflow_path') == '.github/workflows/fast-development.yml' and run.get('head_branch') == 'release/1.7.8-private-apps' and run.get('event') == 'push', 'Wrong run origin')
    require(run.get('verified_build_steps') is True and run.get('evidence_validation') == 'PASS', 'Unvalidated evidence')
    require(type(run.get('original_exit_status')) is int and run['original_exit_status'] == 0, 'Failed original run')
    require(type(run.get('validated_case_count')) is int and run['validated_case_count'] == len(cases), 'Wrong runtime case count')
    require(gates.get('source_sha') == source and gates.get('decision') == 'PASS', 'Missing exact-source final gates')
    require(type(gates.get('original_exit_status')) is int and gates['original_exit_status'] == 0, 'Final runtime gate failed')
    require(gates.get('network') == {name: 'PASS' for name in NETWORK}, 'Incomplete network proof')
    require(run.get('network_evidence_revalidated') is True, 'Packet/protocol evidence not independently revalidated')
    require(approval.get('physical_acceptance') == 'OWNER_REQUIRED', 'Physical acceptance cannot be inferred from CI')


def main():
    approval_path, source, review_path, run_path, evidence_path, manifest_path, fixture_path = sys.argv[1:]
    evidence = Path(evidence_path)
    run = json.loads(Path(run_path).read_text())
    manifest = Path(manifest_path).read_bytes()
    count = verify_xml(evidence/'outputs/androidTest-results/connected/debug', cases_from_manifest(manifest))
    require(run.get('validated_case_count') == count, 'Runtime count differs')
    require((evidence/'privateapps178/source-sha.txt').read_text().strip() == source, 'Artifact source differs')
    gates = json.loads((evidence/'privateapps178/final-gates.json').read_text())
    validate(json.loads(Path(approval_path).read_text()), source, Path(review_path).read_bytes(), run, gates,
             manifest, Path(fixture_path).read_bytes(), Path(__file__).read_bytes())
    print('PASS: exact reviewed 1.7.8 source and focused evidence; original-key code47 upgrade remains mandatory')


if __name__ == '__main__':
    main()
