#!/usr/bin/env python3
"""Public DEBUG fixture TLS identities; never APK signing keys or release trust.

The default reserved-domain certificate is unchanged. --onion-host creates an
exact-hostname leaf for the isolated179 HTTPS onion fixture using the same
already-public DEBUG fixture CA. No hostname verification bypass is provided.
"""
import argparse
import datetime
import hashlib
from pathlib import Path
import re
from cryptography import x509
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import NameOID,ExtendedKeyUsageOID


def generate(root, onion_host=None):
    if onion_host is not None and re.fullmatch(r'[a-z2-7]{56}\.onion',onion_host) is None:
        raise ValueError('Require one exact lowercase v3 onion hostname')
    root=Path(root)
    # Deliberately public seed: DEBUG-only controlled synthetic testing.
    key=ec.derive_private_key(int.from_bytes(hashlib.sha256(b'PrivateGallery publicly reproducible CI TLS fixture never production').digest(),'big'),ec.SECP256R1())
    name=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'Private Gallery CI fixture CA - NEVER PRODUCTION')])
    start=datetime.datetime(2026,1,1,tzinfo=datetime.timezone.utc)
    end=datetime.datetime(2036,1,1,tzinfo=datetime.timezone.utc)
    ca=(x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key()).serial_number(17624401).not_valid_before(start).not_valid_after(end).add_extension(x509.BasicConstraints(ca=True,path_length=0),critical=True).sign(key,hashes.SHA256()))
    host=onion_host or 'fixture.invalid'
    hosts=[host] if onion_host else ['fixture.invalid','*.fixture.invalid']
    serial=(int.from_bytes(hashlib.sha256(host.encode()).digest()[:20],'big')>>1) if onion_host else 17624402
    leaf=(x509.CertificateBuilder().subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,host)])).issuer_name(name).public_key(key.public_key()).serial_number(serial).not_valid_before(start).not_valid_after(end).add_extension(x509.BasicConstraints(ca=False,path_length=None),critical=True).add_extension(x509.SubjectAlternativeName([x509.DNSName(value) for value in hosts]),critical=False).add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]),critical=False).sign(key,hashes.SHA256()))
    root.mkdir(parents=True,exist_ok=True)
    private=root/'fixture.key'
    private.write_bytes(key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
    private.chmod(0o600)
    (root/'fixture.crt').write_bytes(leaf.public_bytes(serialization.Encoding.PEM))
    (root/'fixture-ca.crt').write_bytes(ca.public_bytes(serialization.Encoding.PEM))
    return {'key':private,'certificate':root/'fixture.crt','ca':root/'fixture-ca.crt'}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('root',type=Path)
    parser.add_argument('--onion-host')
    args=parser.parse_args()
    try:generate(args.root,args.onion_host)
    except ValueError as error:parser.error(str(error))

if __name__=='__main__':main()
