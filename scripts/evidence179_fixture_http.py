"""Strict bounded body framing for isolated synthetic179 test endpoints.

Use only on new fixture endpoints; historical handlers stay unchanged. The
HTTP handler must set a socket read timeout before calling. No body/header
logging here. Chunk extensions/trailers are deliberately unsupported.
"""
import re


def read_body(headers, stream, maximum=1_048_576):
    if type(maximum) is not int or maximum < 0:
        raise ValueError('Invalid body quota')
    lengths = headers.get_all('Content-Length', [])
    encodings = headers.get_all('Transfer-Encoding', [])
    if lengths and encodings:
        raise ValueError('Ambiguous body framing')

    def exact(count):
        value = bytearray()
        while len(value) < count:
            part = stream.read(count-len(value))
            if not part or len(part) > count-len(value):
                raise ValueError('Truncated or invalid body')
            value.extend(part)
        return bytes(value)

    if lengths:
        if len(lengths) != 1 or re.fullmatch(r'[0-9]{1,10}', lengths[0]) is None:
            raise ValueError('Invalid Content-Length')
        count = int(lengths[0])
        if count > maximum:
            raise ValueError('Body quota exceeded')
        return exact(count)
    if len(encodings) != 1 or encodings[0].strip().lower() != 'chunked':
        raise ValueError('Require one supported bounded body framing')
    body = bytearray()
    while True:
        line = stream.readline(130)
        if not line.endswith(b'\r\n') or re.fullmatch(rb'[0-9a-fA-F]{1,8}\r\n', line) is None:
            raise ValueError('Invalid chunk header')
        count = int(line[:-2], 16)
        if count > maximum-len(body):
            raise ValueError('Body quota exceeded')
        if count == 0:
            if exact(2) != b'\r\n':
                raise ValueError('Unsupported or malformed trailers')
            return bytes(body)
        body.extend(exact(count))
        if exact(2) != b'\r\n':
            raise ValueError('Invalid chunk terminator')
