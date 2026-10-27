#!/usr/bin/env python3
"""Reject missing, skipped or failed named synthetic release checks."""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

required = {
    'uk.co.traynor.privategallery.ui.HiddenRestoreDialogTest',
    'uk.co.traynor.privategallery.ui.PrimaryCopyDialogTest',
    'uk.co.traynor.privategallery.ui.PasscodeKeypadTest',
    'uk.co.traynor.privategallery.ui.BrowserUploadPolicyTest',
    'uk.co.traynor.privategallery.ui.ExternalEditingUi176Test',
    'uk.co.traynor.privategallery.core.media.ExternalEditorIntents1761Test',
    'uk.co.traynor.privategallery.core.media.ExternalPhotoExport1761Test',
    'uk.co.traynor.privategallery.core.browser.hidden.HiddenBrowserIsolationTest',
    'uk.co.traynor.privategallery.core.browser.hidden.HiddenSocksTransportTest',
    'uk.co.traynor.privategallery.core.browser.hidden.HiddenNavigationTransportTest',
}
named = {
    'transferredEligibleItemsAreImmediatelyVisibleToFreshPrimaryPickerQuery',
    'primaryMoveOnlyTrashesVerifiedSourceAndCanBeRecovered',
    'missingAndWrongPrimaryAuthenticationNeverPublishesOrDeletes',
    'restrictionsSurvivePrimaryTransferAndPreventPhonePublication',
}
cases = []
for report in Path(sys.argv[1]).rglob('*.xml'):
    cases.extend(ET.parse(report).getroot().iter('testcase'))
if not cases or any(c.find(k) is not None for c in cases for k in ('failure','error','skipped')):
    raise SystemExit('Missing or non-passing Android test results')
classes = {c.get('classname') for c in cases}
methods = {c.get('name') for c in cases if c.get('classname') == 'uk.co.traynor.privategallery.core.hidden.HiddenRestoreAccessTest'}
if not required <= classes or not named <= methods:
    raise SystemExit(f'Missing required classes {required-classes} or transfer methods {named-methods}')
print(f'PASS: {len(cases)} executed Android cases, all required classes and transfer methods present')
