#!/usr/bin/env python3
"""Publicly reproducible TLS identity for reserved .invalid DEBUG fixtures ONLY.
This is never an APK signing key and never trusted by a production build.
"""
from pathlib import Path
import datetime,hashlib,sys
from cryptography import x509
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import NameOID,ExtendedKeyUsageOID

root=Path(sys.argv[1]);root.mkdir(parents=True,exist_ok=True)
# Deliberately public seed: restricted to debug-only reserved-domain testing.
key=ec.derive_private_key(int.from_bytes(hashlib.sha256(b'PrivateGallery publicly reproducible CI TLS fixture never production').digest(),'big'),ec.SECP256R1())
name=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'Private Gallery CI fixture CA - NEVER PRODUCTION')])
start=datetime.datetime(2026,1,1,tzinfo=datetime.timezone.utc);end=datetime.datetime(2036,1,1,tzinfo=datetime.timezone.utc)
ca=(x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key()).serial_number(17624401).not_valid_before(start).not_valid_after(end).add_extension(x509.BasicConstraints(ca=True,path_length=0),critical=True).sign(key,hashes.SHA256()))
leaf=(x509.CertificateBuilder().subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'fixture.invalid')])).issuer_name(name).public_key(key.public_key()).serial_number(17624402).not_valid_before(start).not_valid_after(end).add_extension(x509.BasicConstraints(ca=False,path_length=None),critical=True).add_extension(x509.SubjectAlternativeName([x509.DNSName('fixture.invalid'),x509.DNSName('*.fixture.invalid')]),critical=False).add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]),critical=False).sign(key,hashes.SHA256()))
(root/'fixture.key').write_bytes(key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
(root/'fixture.crt').write_bytes(leaf.public_bytes(serialization.Encoding.PEM))
(root/'fixture-ca.crt').write_bytes(ca.public_bytes(serialization.Encoding.PEM))
