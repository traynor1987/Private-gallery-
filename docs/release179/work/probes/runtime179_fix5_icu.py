#!/usr/bin/env python3
"""Bounded native ICU regex probe; synthetic frames, not Android/route evidence."""
import ctypes as c
import json
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[4]
SOURCE=ROOT/'app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceRouteClockLifecycle179.kt'
# JSON and this Kotlin literal share the used quote/backslash escapes. Read the real source pattern.
literal=re.search(r'val match=checkNotNull\(Regex\(("(?:\\.|[^"\\])*")\)',SOURCE.read_text())
assert literal is not None
fixed=json.loads(literal.group(1))
assert fixed.count(r'\}')==1
old=fixed.replace(r'\}', '}', 1)

i18n=c.CDLL('libicui18n.so.74');common=c.CDLL('libicuuc.so.74')
UChar=c.c_uint16;Status=c.c_int32
class ParseError(c.Structure):
    _fields_=[('line',c.c_int32),('offset',c.c_int32),('pre',UChar*16),('post',UChar*16)]
def api(lib,name,args,result):
    f=getattr(lib,name+'_74');f.argtypes=args;f.restype=result;return f
version=api(common,'u_getVersion',[c.POINTER(c.c_uint8)],None)
error_name=api(common,'u_errorName',[Status],c.c_char_p)
open_regex=api(i18n,'uregex_open',[c.POINTER(UChar),c.c_int32,c.c_uint32,c.POINTER(ParseError),c.POINTER(Status)],c.c_void_p)
set_text=api(i18n,'uregex_setText',[c.c_void_p,c.POINTER(UChar),c.c_int32,c.POINTER(Status)],None)
matches=api(i18n,'uregex_matches',[c.c_void_p,c.c_int32,c.POINTER(Status)],c.c_int8)
start=api(i18n,'uregex_start',[c.c_void_p,c.c_int32,c.POINTER(Status)],c.c_int32)
end=api(i18n,'uregex_end',[c.c_void_p,c.c_int32,c.POINTER(Status)],c.c_int32)
close=api(i18n,'uregex_close',[c.c_void_p],None)
def utf16(text):
    data=text.encode('utf-16-le');return (UChar*(len(data)//2)).from_buffer_copy(data)
def compile_pattern(pattern):
    encoded=utf16(pattern);error=ParseError();status=Status(0)
    handle=open_regex(encoded,len(encoded),0,c.byref(error),c.byref(status))
    return handle,status.value,error.offset
v=(c.c_uint8*4)();version(v);print('Native ICU '+'.'.join(map(str,list(v))))
handle,status,offset=compile_pattern(old)
if handle:close(handle)
assert status>0, 'Old pattern unexpectedly compiled'
print(f'OLD RED: {error_name(status).decode()} status={status} offset={offset}')
handle,status,_=compile_pattern(fixed)
assert status<=0 and handle, f'Fixed source pattern compile failed: {status}'
def frame(code=179,data='eyJraW5kIjoiY29udHJvbCJ9',newline='\n'):
    result=f'Broadcasting: Intent {{ act=synthetic }}\nBroadcast completed: result={code}'
    if data is not None:result+=f', data="{data}"'
    return (result+'\n\nE179_CLOCK_STATUS=0\n').replace('\n',newline)
def check(text,want,groups=None):
    encoded=utf16(text);status=Status(0)
    set_text(handle,encoded,len(encoded),c.byref(status));assert status.value<=0
    actual=bool(matches(handle,0,c.byref(status)));assert status.value<=0
    assert actual==want, repr(text)
    if groups:
        # Probe strings are ASCII, so UTF-16 offsets and Python string offsets agree.
        got=[]
        for index in (1,2):
            a=start(handle,index,c.byref(status));b=end(handle,index,c.byref(status));assert status.value<=0
            got.append('' if a<0 else text[a:b])
        assert got==groups, got
try:
    positives=[(frame(0,None),['0','']), (frame(178),['178','eyJraW5kIjoiY29udHJvbCJ9']),
               (frame(),['179','eyJraW5kIjoiY29udHJvbCJ9']), (frame(newline='\r\n'),['179','eyJraW5kIjoiY29udHJvbCJ9'])]
    for text,groups in positives:check(text,True,groups)
    good=frame()
    negatives=[good.replace(' }\n',' \n'),good.replace('Intent {','Intent '),good.replace('STATUS=0','STATUS=1'),
               good.replace('result=179','result=180'),good.replace('eyJraW5kIjoiY29udHJvbCJ9','bad+base64'),
               'prefix'+good,good+'suffix',good[:-1],good.replace('\n\nE179','\nE179'),good.replace('act=synthetic','act=synthetic\nextra')]
    for text in negatives:check(text,False)
    print(f'FIXED GREEN: {len(positives)} complete frames/captures accepted; {len(negatives)} malformed/status/extra/truncated frames rejected')
finally:close(handle)
