#!/usr/bin/env python3
"""Reject missing, skipped or failed named synthetic release checks."""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

required = {
    'uk.co.traynor.privategallery.core.browser.hidden.HiddenTorRelayPolicy1762Test',
    'uk.co.traynor.privategallery.core.browser.hidden.HiddenUploadPickerPaging1762Test',
    'uk.co.traynor.privategallery.ui.ModalCompletion1762Test',
    'uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnShutdown1762Test',
    'uk.co.traynor.privategallery.HiddenVpnHandoff1762Test',
    'uk.co.traynor.privategallery.ui.BrowserToolbarLayout1762Test',
    'uk.co.traynor.privategallery.core.browser.hidden.HiddenTorBootstrapTest',
    'uk.co.traynor.privategallery.core.browser.hidden.HiddenRuntimeFailureTest',
    'uk.co.traynor.privategallery.ui.PromptTemplateLayout1762Test',
    'uk.co.traynor.privategallery.ui.BrowserUploadThumbnail1762Test',
    'uk.co.traynor.privategallery.ui.ExternalEditorChooser1762Test',
    'uk.co.traynor.privategallery.ui.HiddenRestoreAuthentication1762Test',
    'uk.co.traynor.privategallery.core.media.ExternalEditorDiscovery1762Test',
    'uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnBoundary1762Test',
    'uk.co.traynor.privategallery.core.browser.hidden.NativeTorIsolation1762Test',
    'uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnLive1762Test',
    'uk.co.traynor.privategallery.core.browser.hidden.HiddenLibraryParcelPressure1762Test',
    'uk.co.traynor.privategallery.ui.GalleryDialog1762Test',
    'uk.co.traynor.privategallery.core.browser.v2.BrowserScriptDialogs1762Test',
    'uk.co.traynor.privategallery.ui.HiddenBrowser17LayoutTest',
    'uk.co.traynor.privategallery.ExternalEditingLifecycle176Test',
    'uk.co.traynor.privategallery.ui.SettingsPolish1761Test',
    'uk.co.traynor.privategallery.ui.PrivacyShareDialog1762Test',
    'uk.co.traynor.privategallery.ui.BrowserConsent1752Test',
    'uk.co.traynor.privategallery.CollectionDialogs1762Test',
    'uk.co.traynor.privategallery.core.browser.hidden.HiddenSessionLibrary1762Test',
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
if not any(c.get('classname') == 'uk.co.traynor.privategallery.ui.BrowserConsent1752Test' and c.get('name') == 'extensionBasedUploadUsesThemedVaultPickerWithoutPrematureEgress' for c in cases):
    raise SystemExit('Missing browser picker interaction test')
revocation = {'primaryRevocationBetweenItemsStopsBatchAndPreservesCompletedCopy',
              'hiddenLockStopsBatchAndOldSourceCannotInheritNewUnlock',
              'backgroundRevokesTransferEvenWithinHiddenGrace'}
actual_revocation = {c.get('name') for c in cases if c.get('classname') == 'uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccessTest'}
if not revocation <= actual_revocation:
    raise SystemExit(f'Missing transfer revocation checks {revocation-actual_revocation}')
if not required <= classes or not named <= methods:
    raise SystemExit(f'Missing required classes {required-classes} or transfer methods {named-methods}')


for method in ('realWebsiteAlertAndBeforeUnloadRequireExplicitChoice', 'cancelledPreparationCannotClearNewerChooserRequest', 'websiteDialogsUseThemedShellAndRevokeOnBackground', 'movedEligiblePhotoAppearsWhenPickerReopensButRestrictedPhotoDoesNot'):
    if not any(c.get('classname') == 'uk.co.traynor.privategallery.ui.BrowserConsent1752Test' and c.get('name') == method for c in cases):
        raise SystemExit(f'Missing new browser boundary test {method}')

# Whole affected security suites must execute every named @Test, not just one case per class.
import re
full_suites = ['uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccessTest', 'uk.co.traynor.privategallery.core.hidden.HiddenRestoreAccessTest', 'uk.co.traynor.privategallery.core.hidden.HiddenCredentialSeparationTest', 'uk.co.traynor.privategallery.core.hidden.HiddenCredentialStoreTest', 'uk.co.traynor.privategallery.core.media.ExternalEditing176Test', 'uk.co.traynor.privategallery.core.browser.hidden.HiddenRedirectRenderingTest']
full_suites += [
    'uk.co.traynor.privategallery.core.browser.hidden.HiddenTorRelayPolicy1762Test',
    'uk.co.traynor.privategallery.core.browser.hidden.HiddenUploadPickerPaging1762Test',
    'uk.co.traynor.privategallery.ui.ModalCompletion1762Test',
    'uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnShutdown1762Test',
    'uk.co.traynor.privategallery.HiddenVpnHandoff1762Test',
    'uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnBoundary1762Test',
    'uk.co.traynor.privategallery.core.browser.hidden.NativeTorIsolation1762Test',
    'uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnLive1762Test',
    'uk.co.traynor.privategallery.core.browser.hidden.HiddenLibraryParcelPressure1762Test',
    'uk.co.traynor.privategallery.core.browser.hidden.HiddenTorBootstrapTest',
    'uk.co.traynor.privategallery.core.browser.hidden.HiddenRuntimeFailureTest',
]
for class_name in full_suites:
    source = Path('app/src/androidTest/java').joinpath(*class_name.split('.')).with_suffix('.kt')
    expected = set(re.findall(r'@Test(?:\([^)]*\))?\s+fun\s+(\w+)', source.read_text()))
    actual = {c.get('name') for c in cases if c.get('classname') == class_name}
    if not expected or not expected <= actual:
        raise SystemExit(f'Missing whole-security-suite cases {class_name}: {expected-actual}')
print(f'PASS: {len(cases)} executed Android cases; required UI and full affected security methods present')
