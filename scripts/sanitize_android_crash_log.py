#!/usr/bin/env python3
"""Remove URL-like and user-entered strings before CI crash logs are retained."""

import re
import sys
from pathlib import Path


URL = re.compile(r"(?i)\b(?:https?|wss?)://[^\s\]\[(){}<>\"']+")
DATA = re.compile(r"(?i)\bdata:[^\s\]\[(){}<>]+")
HOST = re.compile(r"(?i)\b(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+(?:[a-z]{2,63})(?::\d{1,5})?(?:/[^\s,;]*)?")
IP = re.compile(r"(?i)\b(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?\b")
KEY_VALUE = re.compile(r"(?i)\b(address|url|uri|query|text|value)\s*[=:]\s*[^,;]*")
QUOTED = re.compile(r""""[^\"]*\"|'[^']*'""")


def sanitize_line(line: str) -> str:
    line = URL.sub("[URL REDACTED]", line)
    line = DATA.sub("[DATA REDACTED]", line)
    line = KEY_VALUE.sub(lambda m: f"{m.group(1)}=[REDACTED]", line)
    line = QUOTED.sub("[QUOTED TEXT REDACTED]", line)

    # Preserve Java/Kotlin stack frames and exception class names verbatim;
    # hostnames in all other crash text are private address-like values.
    is_stack_frame = re.search(r"(?:^|:\s*)at\s+[\w.$/]+\([^)]*\)", line)
    is_exception_class = re.search(r"(?:^|:\s*)(?:[\w$]+\.)+[\w$]+(?:Exception|Error)(?::|$)", line)
    if not is_stack_frame and not is_exception_class:
        line = HOST.sub("[HOST REDACTED]", line)
    line = IP.sub("[IP REDACTED]", line)
    return line


def sanitize(text: str) -> str:
    return "\n".join(sanitize_line(line) for line in text.splitlines())


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: sanitize_android_crash_log.py INPUT OUTPUT")
    source, destination = map(Path, sys.argv[1:])
    destination.write_text(sanitize(source.read_text(errors="replace")), encoding="utf-8")


if __name__ == "__main__":
    main()
