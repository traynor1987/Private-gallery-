#!/usr/bin/env python3
"""Check Ask Nova on the existing VPS without reserving budget or contacting OpenAI."""
import argparse
import json
from pathlib import Path
import re
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import ProxyHandler, Request, build_opener

from server import NoRedirect


def verify(origin, token):
    address = urlsplit(origin)
    loopback = address.scheme == "http" and address.hostname == "127.0.0.1"
    if ((address.scheme != "https" and not loopback) or not address.hostname or address.username is not None
            or address.query or address.fragment or address.path not in ("", "/")):
        raise ValueError("Use the HTTPS Nova Brain origin or its local 127.0.0.1 staging port.")
    if not re.fullmatch(r"[A-Za-z0-9_.-]{32,256}", token) or token.startswith("sk-"):
        raise ValueError("Use the backend owner-token file.")
    opener = build_opener(ProxyHandler({}), NoRedirect())
    route = origin.rstrip("/") + "/assistant/ask"

    def probe(authenticated=False, body=None):
        headers = {"Accept": "application/json"}
        if authenticated:
            headers["Authorization"] = f"Bearer {token}"
        if body is not None:
            headers["Content-Type"] = "application/json"
        try:
            response = opener.open(Request(route, data=body, headers=headers), timeout=10)
        except HTTPError as error:
            response = error
        with response:
            code = response.code
            try:
                raw = response.read(32769)
                payload = json.loads(raw) if len(raw) <= 32768 else None
            except ValueError:
                payload = None
        return code, payload if isinstance(payload, dict) else {}

    code, payload = probe()
    if code != 401 or payload.get("error") != "authentication_required":
        raise ValueError(f"Assistant GET authentication check returned HTTP {code}. Expected the backend's 401.")
    code, status = probe(authenticated=True)
    if code != 200 or status.get("status") != "authenticated" or status.get("assistant_available") is not True:
        raise ValueError(f"Authenticated assistant GET returned HTTP {code} without the chat capability.")
    if type(status.get("analysis_enabled")) is not bool:
        raise ValueError("Assistant status omitted the analysis mode.")
    code, result = probe(authenticated=True, body=b'{"consent":false}')
    if code != 400 or result.get("error") != "invalid_approved_excerpt_or_request_identity":
        raise ValueError(f"Consent-denied assistant POST returned HTTP {code}. Expected the backend's 400.")
    return {
        "authenticated": True,
        "assistant_available": True,
        "staging": status.get("model") == "staging",
        "analysis_enabled": status["analysis_enabled"],
        "budget_reported": isinstance(status.get("budget"), dict),
        "consent_denied": True,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("origin", help="Existing HTTPS origin or HTTP loopback origin on the VPS")
    parser.add_argument("--token-file", type=Path, default=Path("/etc/nova-cloud-teacher/secrets/owner-token"))
    args = parser.parse_args()
    try:
        result = verify(args.origin, args.token_file.read_text().strip())
    except (OSError, URLError, ValueError) as error:
        if type(error) is ValueError:
            print(error, file=sys.stderr)
        else:
            print("Couldn't reach or authenticate to the assistant route with the configured token file.",
                  file=sys.stderr)
        return 1
    print(json.dumps(result))
    return 0


if __name__ == "__main__":
    sys.exit(main())
