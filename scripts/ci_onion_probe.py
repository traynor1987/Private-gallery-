#!/usr/bin/env python3
"""Disposable real Tor onion fixture on a GitHub runner, never on an owner device."""
import hashlib
import json
import ssl
import http.server
import os
import re
from pathlib import Path
import secrets
import shutil
import signal
import socket
import subprocess
import sys
import time
try:
    import evidence179_processes as processes179
except ModuleNotFoundError:
    from scripts import evidence179_processes as processes179


def guard():
    if os.environ.get("GITHUB_ACTIONS") != "true" or not os.environ.get("GITHUB_RUN_ID"):
        raise SystemExit("Disposable GitHub runner only")
    return Path(os.environ["RUNNER_TEMP"]) / "private-gallery-onion-probe"


def stop(root):
    evidence = Path("app/build/acceptance-diagnostics/onion-fixture")
    evidence.mkdir(parents=True, exist_ok=True)
    for name in ("fixture.log", "client.log", "client-readiness.txt", "backend-requests.txt", "fixture-health.txt", "interactive-events.jsonl", "evidence179-events.jsonl", "evidence179-ready", "evidence179-tls-readiness.json", "fixture.crt", "fixture-ca.crt"):
        if (root / name).exists():
            shutil.copyfile(root / name, evidence / name)
    outcomes={}
    for name in ('client','tor','http','https'):
        identity=root/(name+'.identity.json')
        if identity.exists():outcomes[name]=processes179.stop_file(identity)
        elif (root/(name+'.pid')).exists():outcomes[name]='missing-identity'
    (evidence/'cleanup-ownership.json').write_text(json.dumps(outcomes,sort_keys=True)+'\n')
    if any(value not in ('stopped','missing') for value in outcomes.values()):
        raise RuntimeError('Fixture cleanup refused an unverified or unflushed process')
    shutil.rmtree(root, ignore_errors=True)



def serve(root, tls=False):
    payload = (root / "response").read_bytes()

    from hidden_tor_fixture1762 import handler_class
    Handler = handler_class(http.server.BaseHTTPRequestHandler, root, payload)

    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    if tls:
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(root / "fixture.crt", root / "fixture.key")
        server.socket = context.wrap_socket(server.socket, server_side=True)
    (root / ("https.port" if tls else "http.port")).write_text(str(server.server_port))
    server.serve_forever()


def verify_independent_retrieval(root, hostname, expected):
    """Descriptor upload alone does not establish fixture readiness.

    A separate Tor client/cache must retrieve the exact backend bytes before
    Android gets the URL. This is fixture setup, not a retry of the app test.
    """
    if shutil.which("curl") is None:
        raise RuntimeError("Fixture readiness requires curl")
    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        port = reservation.getsockname()[1]
    client_data = root / "client"
    client_data.mkdir(mode=0o700)
    log = open(root / "client.log", "ab")
    client = subprocess.Popen([
        "tor", "--DataDirectory", str(client_data), "--SocksPort", f"127.0.0.1:{port}",
        "--SafeLogging", "1", "--Log", "notice stdout",
    ], stdout=log, stderr=log, start_new_session=True)
    (root / "client.pid").write_text(str(client.pid))
    processes179.register(root / "client.identity.json",client.pid,"tor",[str(client_data)],[str(root/"client.log")])
    (root / "client.port").write_text(str(port))
    retrieve_exact(root, hostname, expected, port, client.poll, root / "client-readiness.txt")


def retrieve_exact(root, hostname, expected, port, poll, report):
    deadline = time.monotonic() + 300
    attempts = []
    while time.monotonic() < deadline:
        if poll() is not None:
            raise RuntimeError("Independent fixture Tor client stopped")
        try:
            result = subprocess.run([
                "curl", "--fail", "--silent", "--show-error", "--noproxy", "",
                "--proxy", f"socks5h://127.0.0.1:{port}",
                "--connect-timeout", "30", "--max-time", "45", f"http://{hostname}/",
            ], capture_output=True, timeout=50)
            attempts.append(f"curl_exit={result.returncode}; bytes={len(result.stdout)}")
            if result.returncode == 0 and result.stdout == expected.encode():
                report.write_text(
                    "PASS: independent Tor client retrieved exact controlled backend bytes; remote DNS via SOCKS only.\n"
                    + "\n".join(attempts) + "\n")
                return
        except subprocess.TimeoutExpired:
            attempts.append("curl_process_timeout")
        report.write_text("NOT READY\n" + "\n".join(attempts) + "\n")
        time.sleep(2)
    raise RuntimeError("Controlled onion not retrievable by an independent Tor client")


def retrieve_tls(root, hostname):
    """Exact HTTPS through independent socks5h; ordinary CA and hostname validation."""
    if re.fullmatch(r'[a-z2-7]{56}\.onion', hostname) is None:
        raise RuntimeError('Invalid v3 onion hostname')
    expected = (root / 'evidence179-ready').read_bytes()
    port = int((root / 'client.port').read_text())
    deadline = time.monotonic() + 300
    while time.monotonic() < deadline:
        result = subprocess.run(['curl', '--fail', '--silent', '--show-error', '--noproxy', '',
            '--proxy', f'socks5h://127.0.0.1:{port}', '--cacert', str(root / 'fixture-ca.crt'),
            '--connect-timeout', '30', '--max-time', '45', f'https://{hostname}/evidence179/ready'],
            capture_output=True, timeout=50)
        if result.returncode == 0 and result.stdout == expected:
            (root / 'evidence179-tls-readiness.json').write_text(json.dumps(dict(
                hostname=hostname, url=f'https://{hostname}/evidence179/ready', epoch=time.time(),
                bytes=len(expected), sha256=hashlib.sha256(expected).hexdigest(),
                ca_sha256=hashlib.sha256((root / 'fixture-ca.crt').read_bytes()).hexdigest(),
                leaf_sha256=hashlib.sha256((root / 'fixture.crt').read_bytes()).hexdigest(),
                exit_status=0, independent_socks5h=True, tls_verified=True), sort_keys=True)+'\n')
            return
        time.sleep(2)
    raise RuntimeError('Controlled onion HTTPS not independently ready')


def verify_fixture(root, phase):
    if phase not in ("preflight", "full", "diagnostic-before", "diagnostic-after"):
        raise RuntimeError("Unknown fixture verification phase")
    # Setup readiness may be tens of minutes old by the unfiltered suite.
    # Recheck only the independent runner fixture; never retry an app assertion.
    for name in ("client.pid", "tor.pid", "http.pid") + (("https.pid",) if (root / "evidence179-enabled").is_file() else ()):
        pid = int((root / name).read_text())
        command = Path(f"/proc/{pid}/cmdline")
        if not command.exists() or str(root).encode() not in command.read_bytes():
            raise RuntimeError("Controlled onion fixture process unavailable")
    if (root / "evidence179-enabled").is_file():
        for name in ("client","tor","http","https"):
            identity=json.loads((root/(name+".identity.json")).read_text())
            if not processes179.owned(identity,processes179.inventory(identity["pid"])):
                raise RuntimeError("Fixture process start/output identity changed")
    hostname = (root / "service/hostname").read_text().strip()
    expected = (root / "response").read_text()
    report = root / "client-readiness.txt"
    retrieve_exact(root, hostname, expected, int((root / "client.port").read_text()), lambda: None, report)
    if (root / "evidence179-enabled").is_file():
        retrieve_tls(root, hostname)
    with (root / "fixture-health.txt").open("a") as history:
        history.write(f"phase={phase}; epoch={int(time.time())}; exact_bytes=PASS\n" + report.read_text())
    print(f"Controlled onion fixture fresh exact-byte health check PASS before {phase}")


def start(root, evidence179=False):
    if shutil.which("tor") is None:
        raise SystemExit("Install the runner's Tor package first")
    if root.exists():
        raise SystemExit("Fixture already exists; refusing to overwrite it")
    root.mkdir(mode=0o700)
    if evidence179:
        (root / "evidence179-enabled").touch()
    (root / "data").mkdir(mode=0o700)
    (root / "service").mkdir(mode=0o700)
    expected = "private-gallery-controlled-onion-" + secrets.token_hex(24)
    (root / "response").write_text(expected)
    log = open(root / "fixture.log", "ab")
    http = subprocess.Popen([sys.executable, __file__, "--serve", str(root)], stdout=log, stderr=log, start_new_session=True)
    (root / "http.pid").write_text(str(http.pid))
    processes179.register(root / "http.identity.json",http.pid,Path(sys.executable).name,["--serve",str(root)],[str(root/"fixture.log")])
    deadline = time.monotonic() + 240
    while not (root / "http.port").exists():
        if http.poll() is not None or time.monotonic() > deadline:
            raise RuntimeError("Loopback fixture failed")
        time.sleep(0.1)
    port = int((root / "http.port").read_text())
    config = root / "torrc"
    config.write_text(f"""DataDirectory {root / 'data'}
SocksPort 0
ControlPort auto
ControlPortWriteToFile {root / 'control.port'}
CookieAuthentication 1
CookieAuthFile {root / 'control.cookie'}
DisableNetwork 1
SafeLogging 1
Log notice stdout
HiddenServiceDir {root / 'service'}
HiddenServiceVersion 3
HiddenServicePort 80 127.0.0.1:{port}
""")
    tor = subprocess.Popen(["tor", "-f", str(config)], stdout=log, stderr=log, start_new_session=True)
    (root / "tor.pid").write_text(str(tor.pid))
    processes179.register(root / "tor.identity.json",tor.pid,"tor",["-f",str(config)],[str(root/"fixture.log")])
    while not all((root / p).exists() for p in ("control.port", "control.cookie")):
        if tor.poll() is not None or time.monotonic() > deadline:
            raise RuntimeError("Fixture Tor control unavailable")
        time.sleep(0.1)
    control_port = int((root / "control.port").read_text().strip().rsplit(":", 1)[1])
    with socket.create_connection(("127.0.0.1", control_port), timeout=10) as control:
        control.settimeout(240)
        reader = control.makefile("rb")

        def command(value):
            control.sendall((value + "\r\n").encode())
            while True:
                line = reader.readline().decode().strip()
                if line == "250 OK":
                    return
                if not line or line.startswith(("4", "5")):
                    raise RuntimeError("Fixture Tor control command rejected")

        command("AUTHENTICATE " + (root / "control.cookie").read_bytes().hex())
        if evidence179:
            hostname_path = root / 'service/hostname'
            while not hostname_path.exists():
                if tor.poll() is not None or time.monotonic() > deadline:
                    raise RuntimeError('Onion hostname unavailable')
                time.sleep(.1)
            hostname = hostname_path.read_text().strip()
            from create_hidden_vpn_fixture_tls1762 import generate
            generate(root, onion_host=hostname)
            (root / 'evidence179-ready').write_text('evidence179-ready:' + expected)
            https = subprocess.Popen([sys.executable, __file__, '--serve-tls', str(root)], stdout=log, stderr=log, start_new_session=True)
            (root / 'https.pid').write_text(str(https.pid))
            processes179.register(root / "https.identity.json",https.pid,Path(sys.executable).name,["--serve-tls",str(root)],[str(root/"fixture.log")])
            while not (root / 'https.port').exists():
                if https.poll() is not None or time.monotonic() > deadline:
                    raise RuntimeError('TLS fixture listener unavailable')
                time.sleep(.1)
            tls_port = int((root / 'https.port').read_text())
            command(f'SETCONF HiddenServiceDir="{root / "service"}" HiddenServiceVersion=3 HiddenServicePort="80 127.0.0.1:{port}" HiddenServicePort="443 127.0.0.1:{tls_port}"')
        command("SETEVENTS HS_DESC")
        command("SETCONF DisableNetwork=0")
        published = False
        while time.monotonic() < deadline:
            line = reader.readline().decode().strip()
            if not line:
                raise RuntimeError("Fixture Tor control closed")
            hostname_path = root / "service" / "hostname"
            if hostname_path.exists():
                hostname = hostname_path.read_text().strip()
                if line.startswith("650 HS_DESC UPLOADED ") and hostname.removesuffix(".onion") in line:
                    published = True
                    break
        if not published:
            raise RuntimeError("Real onion descriptor was not published")
    verify_independent_retrieval(root, hostname, expected)
    if evidence179:
        retrieve_tls(root, hostname)
    with open(os.environ["GITHUB_ENV"], "a") as env:
        if evidence179:
            env.write(f"EVIDENCE179_ONION_HTTPS_URL=https://{hostname}\n")
        env.write(f"HIDDEN_ONION_PROBE_URL=http://{hostname}/\nHIDDEN_ONION_PROBE_EXPECTED={expected}\n")
    evidence = Path("app/build/acceptance-diagnostics/onion-server-evidence.txt")
    evidence.parent.mkdir(parents=True, exist_ok=True)
    evidence.write_text("Real v3 onion descriptor upload and independent Tor retrieval verified; HTTP backend binds loopback only.\n"
                        + "Expected response SHA256: " + hashlib.sha256(expected.encode()).hexdigest() + "\n")
    print("Controlled real onion verified by an independent client; Android must retrieve its unique response through Tor.")


if __name__ == "__main__":
    fixture_root = guard()
    if len(sys.argv) > 1 and sys.argv[1] in ("--serve", "--serve-tls"):
        if Path(sys.argv[2]) != fixture_root:
            raise SystemExit("Unexpected fixture directory")
        serve(fixture_root, tls=sys.argv[1] == "--serve-tls")
    elif len(sys.argv) == 3 and sys.argv[1] == "--verify":
        verify_fixture(fixture_root, sys.argv[2])
    elif len(sys.argv) > 1 and sys.argv[1] == "--stop":
        stop(fixture_root)
    else:
        try:
            start(fixture_root, evidence179="--evidence179" in sys.argv[1:])
        except Exception as error:
            evidence = Path("app/build/acceptance-diagnostics/onion-server-evidence.txt")
            evidence.parent.mkdir(parents=True, exist_ok=True)
            log_path = fixture_root / "fixture.log"
            phases = re.findall(r"Bootstrapped ([0-9]+)%", log_path.read_text(errors="replace")) if log_path.exists() else []
            evidence.write_text("Controlled onion setup failed: " + type(error).__name__ + "; numeric bootstrap phases=" + ",".join(phases) + "\n")
            stop(fixture_root)
            raise
