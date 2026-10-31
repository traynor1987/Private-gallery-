#!/usr/bin/env python3
"""Verify actual packaged offline model and 64-bit OCR ELF load alignment."""
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import zipfile

MODEL_SHA='7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2'
MODEL_BYTES=4113088

def check_elf(data):
    if len(data)<64 or data[:6]!=b'\x7fELF\x02\x01':
        raise ValueError('Expected little-endian ELF64 OCR library')
    offset=struct.unpack_from('<Q',data,32)[0]
    size,count=struct.unpack_from('<HH',data,54)
    if size!=56 or not count or offset<64 or offset+size*count>len(data):
        raise ValueError('Invalid ELF program header table')
    loads=0
    for index in range(count):
        kind,flags,file_offset,address,physical,file_size,memory_size,alignment=struct.unpack_from('<IIQQQQQQ',data,offset+size*index)
        if kind!=1: continue
        loads+=1
        if alignment<16384 or alignment&(alignment-1) or file_offset%16384!=address%16384:
            raise ValueError('OCR native load segment is not 16KiB compatible')
        if file_size>memory_size or file_offset+file_size>len(data):
            raise ValueError('Invalid ELF load segment range')
    if not loads: raise ValueError('No ELF load segments')
    return loads

def verify(apk, required_abis):
    with zipfile.ZipFile(apk) as archive:
        names=archive.namelist()
        if len(names)!=len(set(names)): raise ValueError('Duplicate APK ZIP member')
        model=archive.read('assets/tessdata/eng.traineddata')
        if len(model)!=MODEL_BYTES or hashlib.sha256(model).hexdigest()!=MODEL_SHA:
            raise ValueError('Packaged OCR model identity mismatch')
        inventory=[]
        for abi in required_abis:
            natives=[name for name in names if re.fullmatch(r'lib/'+re.escape(abi)+r'/[^/]*(?:tess|lept)[^/]*\.so',name)]
            if not natives: raise ValueError('Missing OCR native library for '+abi)
            for name in natives:
                data=archive.read(name)
                inventory.append({'path':name,'sha256':hashlib.sha256(data).hexdigest(),'bytes':len(data),'load_segments':check_elf(data)})
        return {'model_sha256':MODEL_SHA,'model_bytes':MODEL_BYTES,'ocr_native':inventory,'required_abis':required_abis}

if __name__=='__main__':
    if len(sys.argv)!=4: raise SystemExit('Usage: verify_evidence179_distribution.py APK ABI[,ABI] OUTPUT_JSON')
    report=verify(Path(sys.argv[1]),sys.argv[2].split(','))
    output=Path(sys.argv[3]);output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(report,indent=2)+'\n')
    print('PASS: packaged pinned OCR model and 16KiB native load segments')
