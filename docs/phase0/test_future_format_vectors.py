"""Synthetic review-only F1 serialization/KDF checks; no production crypto/storage implementation."""
import hashlib
import hmac
import json
import struct
import unittest
from pathlib import Path

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

ROOT = Path(__file__).resolve().parents[2]
VECTORS = json.loads((ROOT / 'app/src/test/resources/phase0/future-format-v1-vectors.json').read_text())


def reference_hkdf(ikm, salt, info, length=32):
    """RFC 5869 test-only reference using Python HMAC, checked against an independent provider."""
    prk = hmac.new(salt, ikm, hashlib.sha256).digest()
    result, block = b'', b''
    for counter in range(1, (length + 31) // 32 + 1):
        block = hmac.new(prk, block + info + bytes([counter]), hashlib.sha256).digest()
        result += block
    return result[:length]


def key_info(container, master_id, purpose, obj, generation, algorithm=1):
    label = b'private-gallery:future:key:v1'
    return (struct.pack('>H', len(label)) + label + struct.pack('>H', 1) + container + master_id
            + struct.pack('>H', purpose) + obj + struct.pack('>QH', generation, algorithm))


class FrozenFutureFormatVectorsTest(unittest.TestCase):
    def setUp(self):
        self.master = bytes.fromhex(VECTORS['master'])
        self.salt = bytes.fromhex(VECTORS['salt'])
        self.container = bytes.fromhex(VECTORS['containerId'])
        self.master_id = bytes.fromhex(VECTORS['masterId'])
        self.obj = bytes.fromhex(VECTORS['objectId'])

    def check_hkdf(self, ikm, salt, info, expected, length=32):
        self.assertEqual(expected, reference_hkdf(ikm, salt, info, length).hex())
        self.assertEqual(expected, HKDF(algorithm=hashes.SHA256(), length=length,
                                       salt=salt, info=info).derive(ikm).hex())

    def test_rfc5869_sha256_case1(self):
        v = VECTORS['rfc5869Case1']
        ikm, salt, info = (bytes.fromhex(v[k]) for k in ('ikm', 'salt', 'info'))
        self.assertEqual(v['prk'], hmac.new(salt, ikm, hashlib.sha256).hexdigest())
        self.check_hkdf(ikm, salt, info, v['okm'], v['length'])

    def test_every_purpose_domain_matches_frozen_bytes_and_two_providers(self):
        keys = set()
        self.assertEqual(list(range(1, 12)), [v['purpose'] for v in VECTORS['vectors']])
        for v in VECTORS['vectors']:
            with self.subTest(purpose=v['purpose']):
                info = key_info(self.container, self.master_id, v['purpose'], self.obj, 7)
                self.assertEqual(93, len(info))
                self.assertEqual(v['info'], info.hex())
                self.check_hkdf(self.master, self.salt, info, v['key'])
                keys.add(v['key'])
        self.assertEqual(11, len(keys))

    def test_fixed_whole_header_matches_frozen_aad_bytes(self):
        for v in VECTORS['vectors']:
            if v['wholeHeader'] is None:
                continue
            with self.subTest(purpose=v['purpose']):
                header = struct.pack('>8sHHHH16s16s16sQ32s12sQQQIIII',
                                     b'PGFUTR01', 1, 156, 1, v['purpose'], self.container, self.master_id,
                                     self.obj, 7, self.salt, bytes.fromhex(VECTORS['nonce']),
                                     0, 16, 0, 0, 0, 0, 0)
                self.assertEqual(156, len(header))
                self.assertEqual(v['wholeHeader'], header.hex())

    def test_slot_info_matches_frozen_bytes_and_two_providers(self):
        v = VECTORS['slot']
        label = b'private-gallery:future:slot:v1'
        info = (struct.pack('>H', len(label)) + label + struct.pack('>H', 1)
                + self.container + self.master_id + bytes.fromhex(v['slotId'])
                + struct.pack('>QHH', v['slotGeneration'], v['slotType'], v['policyId']))
        self.assertEqual(94, len(info))
        self.assertEqual(v['info'], info.hex())
        self.check_hkdf(self.master, self.salt, info, v['syntheticRecoveryKey'])

    def test_each_context_field_changes_key_domain(self):
        baseline = VECTORS['vectors'][0]['key']
        alternate = bytes([255]) * 16
        arguments = (self.container, self.master_id, 1, self.obj, 7, 1)
        alternatives = (alternate, alternate, 2, alternate, 8, 2)
        for position, value in enumerate(alternatives):
            with self.subTest(field=position):
                mutated = list(arguments)
                mutated[position] = value
                self.assertNotEqual(baseline, reference_hkdf(self.master, self.salt, key_info(*mutated)).hex())
        self.assertNotEqual(baseline, reference_hkdf(bytes([255]) * 32, self.salt, key_info(*arguments)).hex())
        self.assertNotEqual(baseline, reference_hkdf(self.master, bytes([255]) * 32, key_info(*arguments)).hex())


if __name__ == '__main__':
    unittest.main(verbosity=2)
