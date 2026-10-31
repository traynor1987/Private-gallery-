"""Bounded synthetic HTTPS fixture body decoding; no actual evidence/API credentials."""
import importlib.util
import io
from email.message import Message
from pathlib import Path
import unittest

class Body179(unittest.TestCase):
    def setUp(self):
        path=Path(__file__).resolve().parents[1]/'evidence179_fixture_http.py'
        spec=importlib.util.spec_from_file_location('fixture_http179',path)
        self.parser=importlib.util.module_from_spec(spec);spec.loader.exec_module(self.parser)
    def headers(self,*pairs):
        result=Message()
        for key,value in pairs:result[key]=value
        return result
    def read(self,pairs,body,maximum=16):
        return self.parser.read_body(self.headers(*pairs),io.BytesIO(body),maximum)
    def test_content_length_and_chunked_decode_exact_body_without_reading_next_message(self):
        self.assertEqual(self.read([('Content-Length','5')],b'helloNEXT'),b'hello')
        stream=io.BytesIO(b'2\r\nhe\r\n3\r\nllo\r\n0\r\n\r\nNEXT')
        self.assertEqual(self.parser.read_body(self.headers(('Transfer-Encoding','chunked')),stream,16),b'hello')
        self.assertEqual(stream.read(),b'NEXT')
    def test_ambiguous_missing_or_unsupported_framing_rejected(self):
        for pairs in [[],[('Content-Length','1'),('Content-Length','1')],[('Content-Length','1'),('Transfer-Encoding','chunked')],[('Transfer-Encoding','gzip, chunked')],[('Transfer-Encoding','chunked'),('Transfer-Encoding','chunked')],[('Content-Length','-1')],[('Content-Length','+1')]]:
            with self.subTest(pairs=pairs),self.assertRaises(ValueError):self.read(pairs,b'X')
    def test_malformed_incomplete_oversized_chunked_refused(self):
        for body in [b'1\nX\r\n0\r\n\r\n',b'Z\r\n',b'1;ext=x\r\nX\r\n0\r\n\r\n',b'3\r\nxy',b'1\r\nXxx0\r\n\r\n',b'0\r\nX-Trailer: x\r\n\r\n',b'11\r\n',b'F'*129+b'\r\n',b'8\r\n12345678\r\n9\r\n123456789\r\n0\r\n\r\n']:
            with self.subTest(body=body),self.assertRaises(ValueError):self.read([('Transfer-Encoding','chunked')],body)
    def test_size_bound_and_truncated_content_length(self):
        for value,body in [('17',b''),('3',b'xy')]:
            with self.assertRaises(ValueError):self.read([('Content-Length',value)],body)
        self.assertEqual(self.read([('Content-Length','16')],b'1234567890123456'),b'1234567890123456')
        self.assertEqual(self.read([('Transfer-Encoding','chunked')],b'10\r\n1234567890123456\r\n0\r\n\r\n'),b'1234567890123456')
if __name__=='__main__':unittest.main()
