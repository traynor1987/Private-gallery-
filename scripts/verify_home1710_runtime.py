#!/usr/bin/env python3
"""Deny signer access without complete successful original Android reports for the changed boundaries."""
import sys,zipfile,xml.etree.ElementTree as ET
EXPECTED={
 'uk.co.traynor.privategallery.HomeNavigation1710Test',
 'uk.co.traynor.privategallery.ui.PrivateAppsUi178Test',
 'uk.co.traynor.privategallery.core.home.Home1710Test',
 'uk.co.traynor.privategallery.core.home.EvidenceImport1710Test',
 'uk.co.traynor.privategallery.core.evidence.EvidenceStore179Test',
 'uk.co.traynor.privategallery.core.evidence.EvidenceAccess179Test',
 'uk.co.traynor.privategallery.core.evidence.EvidenceController179Test',
 'uk.co.traynor.privategallery.EvidenceAuthority179Test',
 'uk.co.traynor.privategallery.ui.HiddenNavigationAuthorityTest',
 'uk.co.traynor.privategallery.ui.EvidenceLocker179UiTest',
 'uk.co.traynor.privategallery.PrivacyViewerRepair1751Test',
}
def require(value,message):
 if not value:raise ValueError(message)
def verify(path):
 seen=set();cases=set()
 with zipfile.ZipFile(path) as archive:
  names=archive.namelist();require(len(names)==len(set(names)),'Duplicate runtime archive members')
  for name in names:
   if 'outputs/androidTest-results/' not in name or not name.endswith('.xml'):continue
   require(archive.getinfo(name).file_size<=20_000_000,'Oversized XML')
   root=ET.fromstring(archive.read(name))
   for suite in root.iter('testsuite'):
    require(int(suite.get('failures','0'))==0 and int(suite.get('errors','0'))==0,'Android runtime failure')
   for case in root.iter('testcase'):
    require(case.find('failure') is None and case.find('error') is None,'Failed runtime case')
    cls=case.get('classname','')
    if cls in EXPECTED:
     require(case.find('skipped') is None,'Required class skipped')
     seen.add(cls);cases.add((cls,case.get('name')))
 require(EXPECTED<=seen,'Missing required Android classes: '+str(sorted(EXPECTED-seen)))
 return len(cases)
if __name__=='__main__':print('PASS: complete Android runtime reports;',verify(sys.argv[1]),'successful distinct cases')
