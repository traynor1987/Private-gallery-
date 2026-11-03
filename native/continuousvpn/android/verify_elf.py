#!/usr/bin/env python3
"""Reject misaligned, incorrectly named, or colliding native builds."""
import re
import subprocess
import sys
readelf, binary = sys.argv[1:]
headers = subprocess.check_output([readelf, '-lW', binary], text=True)
loads = [line.split() for line in headers.splitlines() if line.strip().startswith('LOAD ')]
assert loads, 'Missing LOAD segments'
for load in loads:
    offset, virtual, alignment = int(load[1], 16), int(load[2], 16), int(load[-1], 16)
    assert alignment >= 16384 and offset % 16384 == virtual % 16384, 'Not 16KB compatible'
dynamic = subprocess.check_output([readelf, '-dW', binary], text=True)
assert re.search(r'\(SONAME\).*\[libnova-wg.so\]', dynamic), 'Wrong SONAME'
symbols = subprocess.check_output([readelf, '--dyn-syms', '-W', binary], text=True)
expected = {'nCreate','nReplace','nEpoch','nBeginProbe','nRegister','nRemove','nSequence','nPublish','nInvalidate','nInvalidateTransport','nRetire','nRetireCurrent','nRelease','nStatistics'}
prefix='Java_uk_co_traynor_privategallery_core_vpn_continuity_NativeVpnFence_'
actual=set(re.findall(r'\b'+prefix+r'(\w+)', symbols))
assert actual == expected, ('Missing/unexpected JNI exports', actual)
assert 'Java_com_wireguard_android_backend_GoBackend' not in symbols, 'Original backend collision'
print(binary + ': 16KB LOAD alignment, independent SONAME and all 14 JNI exports verified')
