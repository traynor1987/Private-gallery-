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
 def test_public_fixture_trust_is_exactly_reserved_and_debug_only(self):
  main=Path('app/src/main/res/xml/hidden_network_security.xml').read_text()
  self.assertNotIn('hidden_vpn_fixture_ca',main);self.assertNotIn('fixture.invalid',main)
  # Follow the manifest-selected resource, not an unused similarly named XML.
  application=ET.parse('app/src/debug/AndroidManifest.xml').getroot().find('application')
  selected=application.get('{http://schemas.android.com/apk/res/android}networkSecurityConfig')
  self.assertTrue(selected.startswith('@xml/'))
  root=ET.parse(Path('app/src/debug/res/xml')/(selected.split('/')[-1]+'.xml')).getroot()
  config=[item for item in root.findall('domain-config') if item.find('trust-anchors') is not None]
  # Both were present before this repair: HTTPS Tor-probe and reserved VPN fixture.
  self.assertEqual(2,len(config))
  actual={tuple(node.text for node in item.findall('domain')):set(node.get('src') for node in item.find('trust-anchors').findall('certificates')) for item in config}
  self.assertEqual({('onion',):{'system','@raw/hidden_vpn_fixture_ca'},('fixture.invalid',):{'@raw/hidden_vpn_fixture_ca'}},actual)
  self.assertTrue(all(node.get('includeSubdomains')=='true' for item in config for node in item.findall('domain')))
  self.assertEqual('false',root.find('base-config').get('cleartextTrafficPermitted'))
  production=ET.parse('app/src/main/AndroidManifest.xml').getroot().find('application')
  self.assertEqual('@xml/hidden_network_security',production.get('{http://schemas.android.com/apk/res/android}networkSecurityConfig'))

if __name__=='__main__':unittest.main()
