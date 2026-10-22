#!/usr/bin/env python3
"""Disposable real Tor onion fixture on a GitHub runner, never on an owner device."""
import hashlib
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


def guard():
    if os.environ.get("GITHUB_ACTIONS") != "true" or not os.environ.get("GITHUB_RUN_ID"):
        raise SystemExit("Disposable GitHub runner only")
    return Path(os.environ["RUNNER_TEMP"]) / "private-gallery-onion-probe"


def stop(root):
    for name in ("tor.pid", "http.pid"):
        path = root / name
        if path.exists():
            pid = int(path.read_text())
            command = Path(f"/proc/{pid}/cmdline")
            if command.exists() and str(root).encode() in command.read_bytes():
                os.kill(pid, signal.SIGTERM)
    shutil.rmtree(root, ignore_errors=True)


def serve(root):
    payload = (root / "response").read_bytes()

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            self.send_response(200 if self.path == "/" else 404)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

        def log_message(self, *_):
            pass

    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    (root / "http.port").write_text(str(server.server_port))
    server.serve_forever()


def start(root):
    if shutil.which("tor") is None:
        raise SystemExit("Install the runner's Tor package first")
    if root.exists():
        raise SystemExit("Fixture already exists; refusing to overwrite it")
    root.mkdir(mode=0o700)
    (root / "data").mkdir(mode=0o700)
    (root / "service").mkdir(mode=0o700)
    expected = "private-gallery-controlled-onion-" + secrets.token_hex(24)
    (root / "response").write_text(expected)
    log = open(root / "fixture.log", "ab")
    http = subprocess.Popen([sys.executable, __file__, "--serve", str(root)], stdout=log, stderr=log, start_new_session=True)
    (root / "http.pid").write_text(str(http.pid))
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
    with open(os.environ["GITHUB_ENV"], "a") as env:
        env.write(f"HIDDEN_ONION_PROBE_URL=http://{hostname}/\nHIDDEN_ONION_PROBE_EXPECTED={expected}\n")
    evidence = Path("app/build/acceptance-diagnostics/onion-server-evidence.txt")
    evidence.parent.mkdir(parents=True, exist_ok=True)
    evidence.write_text("Real v3 onion descriptor upload observed; HTTP backend binds loopback only.\n"
                        + "Expected response SHA256: " + hashlib.sha256(expected.encode()).hexdigest() + "\n")
    print("Controlled real onion service published; client must retrieve its unique response through Tor.")


if __name__ == "__main__":
    fixture_root = guard()
    if len(sys.argv) > 1 and sys.argv[1] == "--serve":
        if Path(sys.argv[2]) != fixture_root:
            raise SystemExit("Unexpected fixture directory")
        serve(fixture_root)
    elif len(sys.argv) > 1 and sys.argv[1] == "--stop":
        stop(fixture_root)
    else:
        try:
            start(fixture_root)
        except Exception as error:
            evidence = Path("app/build/acceptance-diagnostics/onion-server-evidence.txt")
            evidence.parent.mkdir(parents=True, exist_ok=True)
            log_path = fixture_root / "fixture.log"
            phases = re.findall(r"Bootstrapped ([0-9]+)%", log_path.read_text(errors="replace")) if log_path.exists() else []
            evidence.write_text("Controlled onion setup failed: " + type(error).__name__ + "; numeric bootstrap phases=" + ",".join(phases) + "\n")
            stop(fixture_root)
            raise
