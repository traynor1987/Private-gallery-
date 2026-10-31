"""Additive synthetic HTTPS receipts. Never records body/header values or credentials."""
import errno
import fcntl
import hashlib
import json
import re
import socket
import select
import ssl
import time
try:
    from evidence179_fixture_http import read_body
except ModuleNotFoundError:
    from scripts.evidence179_fixture_http import read_body

RESPONSE = b'evidence179-post-accepted\n'
TOKEN = re.compile(r'[a-z0-9_-]{1,64}\Z')


def append_locked(out, values):
    out.seek(0)
    sequence=sum(1 for _ in out)+1
    event=dict(sequence=sequence,epoch=time.time(),monotonic=time.monotonic(),**values)
    out.write(json.dumps(event,sort_keys=True)+'\n');out.flush()
    return event


def record(root, **values):
    with (root/'evidence179-events.jsonl').open('a+') as out:
        fcntl.flock(out,fcntl.LOCK_EX)
        return append_locked(out,values)


def arm(root,case,nonce,phase,token):
    """Independent pre-action receipt; never accepts an already terminated stream."""
    with (root/'evidence179-events.jsonl').open('a+') as out:
        fcntl.flock(out,fcntl.LOCK_EX);out.seek(0)
        selected=[json.loads(line) for line in out]
        selected=[e for e in selected if (e.get('case'),e.get('nonce'),e.get('phase'))==(case,nonce,phase)]
        if [e['kind'] for e in selected]!=['post','slow-start']:
            raise ValueError('Stream is not uniquely admitted and unclosed')
        identity={k:v for k,v in selected[-1].items() if k not in ('sequence','epoch','monotonic','kind')}
        return append_locked(out,dict(identity,kind='action-arm',action_token=token))


def peer_close(error):
    if isinstance(error,BrokenPipeError) and error.errno==errno.EPIPE:return 'broken_pipe'
    if isinstance(error,ConnectionResetError) and error.errno==errno.ECONNRESET:return 'connection_reset'
    if isinstance(error,ssl.SSLEOFError) and error.errno==ssl.SSL_ERROR_EOF:return 'tls_eof'
    if isinstance(error,ssl.SSLZeroReturnError) and error.errno==ssl.SSL_ERROR_ZERO_RETURN:return 'tls_close_notify'
    return None


def events(root):
    path = root / 'evidence179-events.jsonl'
    if not path.exists():
        return []
    with path.open() as source:
        fcntl.flock(source, fcntl.LOCK_SH)
        return [json.loads(line) for line in source]


def reply(handler, body, status=200):
    handler.send_response(status)
    handler.send_header('Content-Type', 'application/json')
    handler.send_header('Content-Length', str(len(body)))
    handler.send_header('Cache-Control', 'no-store')
    handler.send_header('Connection', 'close')
    handler.end_headers()
    handler.wfile.write(body)
    handler.wfile.flush()
    handler.close_connection = True


def get(handler, root):
    if not handler.path.startswith('/evidence179/'):
        return False
    match=re.fullmatch(r'/evidence179/arm/([a-z_]+)/([a-f0-9]{32})/([a-z]+)/([a-f0-9]{32})',handler.path)
    if match:
        try:reply(handler,json.dumps(dict(event=arm(root,*match.groups()))).encode())
        except ValueError:reply(handler,b'Stream unavailable for action',409)
    elif handler.path == '/evidence179/events':
        reply(handler, json.dumps(dict(events=events(root))).encode())
    elif handler.path == '/evidence179/ready' and isinstance(handler.connection, ssl.SSLSocket):
        reply(handler, (root / 'evidence179-ready').read_bytes())
    else:
        reply(handler, b'unknown endpoint', 404)
    return True


def post(handler, root):
    if not handler.path.startswith('/evidence179/'):
        return False
    handler.close_connection = True
    handler.connection.settimeout(10)
    if not isinstance(handler.connection, ssl.SSLSocket):
        reply(handler, b'HTTPS required', 400)
        return True
    try:
        body = read_body(handler.headers, handler.rfile)
        data = json.loads(body)
        if not isinstance(data, dict) or set(data) != {'case', 'nonce', 'phase'}:
            raise ValueError('invalid synthetic body')
        if not all(isinstance(x, str) and TOKEN.fullmatch(x) for x in data.values()):
            raise ValueError('invalid marker')
        if handler.headers.get_all('X-Evidence179-Case') != [data['nonce']]:
            raise ValueError('marker mismatch')
        expected = '/evidence179/post' if data['phase'] == 'success' else '/evidence179/slow/'+data['case']+'/'+data['phase']
        if handler.path != expected or handler.headers.get('Authorization') or handler.headers.get('Proxy-Authorization') or handler.headers.get('Cookie'):
            raise ValueError('unexpected request')
    except (ValueError, OSError, UnicodeError):
        reply(handler, b'invalid bounded synthetic request', 400)
        return True
    identity = dict(case=data['case'], nonce=data['nonce'], phase=data['phase'], source=handler.client_address[0],
                    method='POST', path=handler.path, host=handler.headers.get('Host'), tls=True,
                    body_length=len(body), body_sha256=hashlib.sha256(body).hexdigest(), auth_present=False, cookie_present=False)
    record(root, kind='post', **identity)
    if data['phase'] == 'success':
        reply(handler, RESPONSE)
        record(root, kind='response', response_sha256=hashlib.sha256(RESPONSE).hexdigest(), **identity)
    else:
        record(root, kind='slow-start', **identity)
        handler.send_response(200)
        handler.send_header('Content-Type', 'application/octet-stream')
        handler.send_header('Content-Length', '1048576')
        handler.send_header('Connection', 'close')
        handler.end_headers()
        try:
            # Long enough to distinguish actual cancellation from normal response completion.
            for _ in range(1024):
                if select.select([handler.connection],[],[],0)[0]:
                    incoming=handler.connection.recv(1)
                    if incoming==b'':
                        record(root,kind='slow-closed',close_type='peer_eof',close_errno=0,close_evidence='tls_eof',**identity)
                        return True
                    record(root,kind='slow-io-error',close_type='unexpected_client_data',close_errno=0,**identity)
                    return True
                handler.wfile.write(b'x' * 1024)
                handler.wfile.flush()
                time.sleep(.25)
            record(root, kind='slow-complete', **identity)
        except (TimeoutError, socket.timeout):
            record(root, kind='slow-timeout', **identity)
        except OSError as error:
            evidence=peer_close(error)
            record(root,kind='slow-closed' if evidence else 'slow-io-error',close_type=type(error).__name__,close_errno=error.errno,close_evidence=evidence,**identity)
    return True
