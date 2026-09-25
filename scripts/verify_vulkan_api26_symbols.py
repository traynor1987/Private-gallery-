#!/usr/bin/env python3
"""Reject newer Vulkan ELF imports that would break Android API 26 CPU fallback.

Usage: python3 scripts/verify_vulkan_api26_symbols.py LIBRARY [NM]
Pass the NDK llvm-nm path for Android files; host nm also accepts host archives.
"""
import re
from pathlib import Path
import subprocess
import sys

library = sys.argv[1]
nm = sys.argv[2] if len(sys.argv) > 2 else "nm"
flags = [] if Path(library).suffix == ".a" else ["--dynamic"]
symbols = subprocess.check_output([nm, *flags, "--undefined-only", library], text=True)
imports = set(re.findall(r"\b(vk[A-Z]\w*)(?:@\S+)?\s*$", symbols, re.MULTILINE))
# The reviewed backend's only direct loader imports, all Vulkan 1.0 / API 24.
# All additional entry points must use its dynamically resolved dispatcher.
allowed = {"vkGetInstanceProcAddr", "vkGetDeviceProcAddr", "vkCmdCopyBuffer"}
unexpected = imports - allowed
if unexpected:
    raise SystemExit("FAIL: Vulkan imports outside the reviewed API 26 set: " + ", ".join(sorted(unexpected)))
if "vkGetInstanceProcAddr" not in imports:
    raise SystemExit("FAIL: expected Vulkan loader import absent; wrong or stripped symbol table")
print("PASS: direct Vulkan imports remain compatible with Android API 26")
