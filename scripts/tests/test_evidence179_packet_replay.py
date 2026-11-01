"""Decoder identity tests; synthetic witnesses are never Android or release proof."""
import copy
import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
from unittest import mock
from types import SimpleNamespace

class PacketReplay179(unittest.TestCase):
    def helper(self):
        spec=importlib.util.find_spec('scripts.replay_evidence179_packets')
        self.assertIsNotNone(spec,'Missing original-witness-bound packet replay guard')
        from scripts import replay_evidence179_packets
        return replay_evidence179_packets
    def capture(self, rows=((5,0),(2,4))):
        raw=struct.pack('<IHHIIII',0xa1b2c3d4,2,4,0,0,256,276)
        for i,(index,direction) in enumerate(rows):
            packet=struct.pack('!HHIHBB8s',0x800,0,index,1,direction,6,b'12345678')+b'E'+bytes(19)
            raw+=struct.pack('<IIII',1700000000+i,123456,len(packet),len(packet))+packet
        return raw
    def rendered(self,names=('pg1762','eth0')):
        return ''.join(f'2023-11-14 22:13:{20+i:02}.123456 {name:<5} {direction:<3} IP (length 20)\n\t0x0000:  4500 0000\n' for i,(name,direction) in enumerate(zip(names,('In','Out'))))
    def test_witness_uses_original_order_direction_and_names(self):
        h=self.helper();w=h.interface_witness(self.capture(),self.rendered())
        self.assertEqual(w,{'2':'eth0','5':'pg1762'})
        self.assertEqual(h.normalized_packets(self.rendered()),h.normalized_packets(self.rendered(('?','lo'))))
    def test_unknown_conflicting_and_unsafe_original_names_fail(self):
        h=self.helper()
        for text,raw in [(self.rendered(('?','eth0')),self.capture()),(self.rendered(('pg1762','pg1762')),self.capture()),(self.rendered(('a/b','eth0')),self.capture()),(self.rendered(),self.capture(((5,0),(5,4))))]:
            with self.subTest(text=text),self.assertRaises(ValueError):h.interface_witness(raw,text)
    def test_missing_reordered_and_wrong_direction_records_fail(self):
        h=self.helper()
        for text in [self.rendered().split('\n',2)[0]+'\n',self.rendered().replace('22:13:20','22:13:22'),self.rendered().replace('In  ','Out ')]:
            with self.subTest(text=text),self.assertRaises(ValueError):h.interface_witness(self.capture(),text)
    def test_malformed_pcap_rejected_without_text_fallback(self):
        h=self.helper();raw=self.capture()
        for bad in [raw[:23],raw[:-1],raw+b'x',raw[:20]+struct.pack('<I',1)+raw[24:]]:
            with self.subTest(length=len(bad)),self.assertRaises(ValueError):h.interface_witness(bad,self.rendered())
    def test_only_interface_presentation_is_normalized(self):
        h=self.helper();original=self.rendered()
        self.assertNotEqual(h.normalized_packets(original),h.normalized_packets(original.replace('4500','4501')))
        self.assertNotEqual(h.normalized_packets(original),h.normalized_packets(original.replace('length 20','length 21')))
        self.assertNotEqual(h.normalized_packets(original),h.normalized_packets(original.replace('In  ','Out ')))
    def test_original_hashes_bind_raw_rendering_and_source_before_replay(self):
        h=self.helper()
        with tempfile.TemporaryDirectory() as folder:
            base=Path(folder);root=base/'evidence179';(root/'vpn').mkdir(parents=True)
            (root/'vpn/traffic.pcap').write_bytes(self.capture());(root/'vpn/packets.txt').write_text(self.rendered())
            proof={'hashes':{'app/build/evidence179/vpn/'+n:h.sha((root/'vpn'/n).read_bytes()) for n in ('traffic.pcap','packets.txt')}}
            h.verify_original_hashes(proof,root,base)
            (root/'vpn/packets.txt').write_text(self.rendered(('evil','eth0')))
            with self.assertRaises(ValueError):h.verify_original_hashes(proof,root,base)
            for key in ['../outside','/etc/passwd','evidence179/../outside']:
                bad=copy.deepcopy(proof);bad['hashes']={key:'0'*64}
                with self.subTest(key=key),self.assertRaises(ValueError):h.verify_original_hashes(bad,root,base)
    def test_changed_independent_decode_fails_before_privileged_replay(self):
        h=self.helper()
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)/'evidence179';(root/'vpn').mkdir(parents=True)
            (root/'vpn/traffic.pcap').write_bytes(self.capture());original=self.rendered().encode()
            (root/'vpn/packets.txt').write_bytes(original)
            with mock.patch.object(h,'decode',return_value=self.rendered().replace('4500','4501')),mock.patch.object(h.subprocess,'run') as invoke:
                with self.assertRaisesRegex(ValueError,'beyond interface-name'):h.replay(root,'1'*40,Path(folder))
                invoke.assert_not_called()
            self.assertEqual((root/'vpn/packets.txt').read_bytes(),original)
    def test_namespace_entry_refuses_changed_witness_or_same_namespace(self):
        h=self.helper()
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'witness.json'
            path.write_text(__import__('json').dumps({'source_sha':'1'*40,'parent_network_namespace':__import__('os').readlink('/proc/self/ns/net')}))
            args=SimpleNamespace(witness=path,witness_sha256='0'*64,source='1'*40,namespace_root=Path(folder))
            with mock.patch.object(h.subprocess,'run') as invoke:
                with self.assertRaisesRegex(ValueError,'witness changed'):h.namespace_replay(args)
                args.witness_sha256=h.sha(path.read_bytes())
                with self.assertRaisesRegex(ValueError,'separate network namespace'):h.namespace_replay(args)
                invoke.assert_not_called()
if __name__=='__main__':unittest.main()
