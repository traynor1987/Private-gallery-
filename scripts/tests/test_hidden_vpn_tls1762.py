import subprocess,sys,tempfile,unittest,xml.etree.ElementTree as ET
from pathlib import Path
from cryptography import x509
from cryptography.hazmat.primitives.asymmetric import ec

class HiddenVpnTlsFixtureTest(unittest.TestCase):
 def test_runtime_leaf_verifies_against_debug_only_public_fixture_anchor(self):
  with tempfile.TemporaryDirectory() as folder:
   subprocess.run([sys.executable,'scripts/create_hidden_vpn_fixture_tls1762.py',folder],check=True)
   trusted=x509.load_pem_x509_certificate(Path('app/src/debug/res/raw/hidden_vpn_fixture_ca.crt').read_bytes())
   leaf=x509.load_pem_x509_certificate((Path(folder)/'fixture.crt').read_bytes())
   trusted.public_key().verify(leaf.signature,leaf.tbs_certificate_bytes,ec.ECDSA(leaf.signature_hash_algorithm))
   names=leaf.extensions.get_extension_for_class(x509.SubjectAlternativeName).value.get_values_for_type(x509.DNSName)
   self.assertEqual({'fixture.invalid','*.fixture.invalid'},set(names))
   self.assertNotEqual(trusted.subject,leaf.subject)
 def test_public_fixture_trust_cannot_enter_release_or_tor_domains(self):
  main=Path('app/src/main/res/xml/hidden_network_security.xml').read_text()
  self.assertNotIn('hidden_vpn_fixture_ca',main);self.assertNotIn('fixture.invalid',main)
  # Follow the manifest-selected resource, not an unused similarly named XML.
  application=ET.parse('app/src/debug/AndroidManifest.xml').getroot().find('application')
  selected=application.get('{http://schemas.android.com/apk/res/android}networkSecurityConfig')
  self.assertTrue(selected.startswith('@xml/'))
  root=ET.parse(Path('app/src/debug/res/xml')/(selected.split('/')[-1]+'.xml')).getroot()
  config=[item for item in root.findall('domain-config') if item.find('trust-anchors') is not None]
  self.assertEqual(1,len(config))
  self.assertEqual(['fixture.invalid'],[node.text for node in config[0].findall('domain')])

if __name__=='__main__':unittest.main()
