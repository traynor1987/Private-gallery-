#!/usr/bin/env python3
"""Replay SLL2 decoding with immutable original interface-name witnesses.

Dummy interfaces below restore decoder lookup names only. They do not constitute
an actual WireGuard tunnel, routing observation, or a replacement packet capture.
"""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import struct
import subprocess
import sys
import tempfile


def require(condition,message):
    if not condition:raise ValueError(message)
def sha(raw):return hashlib.sha256(raw).hexdigest()

HEADER=re.compile(r'^(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d\.\d{6}) (\S+)( +)(In|Out|B|M|P)( +)(.*)$')
DIRECTIONS=('In','B','M','P','Out')

def packet_records(text):
    require(text.endswith('\n'),'Unterminated packet rendering')
    records=[]
    for line in text.splitlines(keepends=True):
        match=HEADER.fullmatch(line[:-1]) if line.endswith('\n') else None
        if match:
            timestamp,name,pad,direction,after,rest=match.groups()
            require(name+pad==f'{name:<5} ' and direction+after==f'{direction:<3} ','Unexpected packet header padding')
            records.append([timestamp,name,direction,timestamp+' @INTERFACE@ '+direction+after+rest+'\n'])
        else:
            require(records and (line.startswith(('\t',' ')) or line=='\n'),'Unrecognized packet rendering record')
            records[-1][3]+=line
    require(bool(records),'Empty packet rendering')
    return records

def normalized_packets(text):return ''.join(row[3] for row in packet_records(text))

def sll2_records(raw):
    require(len(raw)>=24,'Truncated pcap header')
    endian={b'\xd4\xc3\xb2\xa1':'<',b'\xa1\xb2\xc3\xd4':'>'}.get(raw[:4])
    require(endian is not None,'Only original microsecond classic pcap is supported')
    major,minor,zone,sigfigs,snaplen,link=struct.unpack(endian+'HHiIII',raw[4:24])
    require((major,minor,zone,sigfigs,link)==(2,4,0,0,276) and 20<=snaplen<=65535,'Wrong original SLL2 pcap format')
    records=[];offset=24
    while offset<len(raw):
        require(len(records)<1000000 and offset+16<=len(raw),'Truncated/oversized pcap record inventory')
        seconds,micros,caplen,length=struct.unpack_from(endian+'IIII',raw,offset);offset+=16
        require(micros<1000000 and 20<=caplen<=min(length,snaplen) and offset+caplen<=len(raw),'Truncated/invalid pcap packet')
        protocol,reserved,index,hatype,direction,halen,address=struct.unpack_from('!HHIHBB8s',raw,offset)
        require(reserved==0 and 0<index<2**31 and direction<len(DIRECTIONS) and halen<=8,'Invalid original SLL2 header')
        timestamp=datetime.datetime.fromtimestamp(seconds,datetime.timezone.utc).strftime('%Y-%m-%d %H:%M:%S')+f'.{micros:06}'
        records.append((timestamp,index,DIRECTIONS[direction]));offset+=caplen
    require(bool(records),'Empty original capture')
    return records

def interface_witness(raw,text):
    packets=sll2_records(raw);rendered=packet_records(text)
    require(len(packets)==len(rendered),'Original pcap/rendering record count differs')
    mapping={};names={}
    for (timestamp,index,direction),(actual,name,shown,_) in zip(packets,rendered):
        require(timestamp==actual and direction==shown,'Original pcap/rendering order, time or direction differs')
        require(re.fullmatch(r'[A-Za-z0-9_.:-]{1,15}',name) is not None and name not in ('.','..'),'Unknown/unsafe original interface name')
        require((index!=1 and name!='lo') or (index==1 and name=='lo'),'Invalid original loopback witness')
        require(mapping.get(str(index),name)==name and names.get(name,index)==index,'Conflicting original interface witness')
        mapping[str(index)]=name;names[name]=index
    return dict(sorted(mapping.items(),key=lambda item:int(item[0])))

def verify_original_hashes(proof,root,checkout):
    hashes=proof.get('hashes',{});require(isinstance(hashes,dict) and hashes,'Missing original raw hashes')
    seen=set()
    for original,digest in hashes.items():
        require(isinstance(original,str) and '\\' not in original and re.fullmatch('[a-f0-9]{64}',digest) is not None,'Invalid original raw binding')
        if '/evidence179/' in original:key='evidence179/'+original.split('/evidence179/',1)[1]
        else:key=original
        path=Path(key)
        require(not path.is_absolute() and '..' not in path.parts and path.as_posix()==key and key not in seen,'Unsafe/duplicate original raw binding')
        seen.add(key)
        location=root.parent/path if key.startswith('evidence179/') else checkout/path
        require(location.is_file() and not location.is_symlink() and sha(location.read_bytes())==digest,'Original raw/source hash differs: '+key)
    require({'evidence179/vpn/traffic.pcap','evidence179/vpn/packets.txt'}<=seen,'Missing original raw capture/rendering bindings')

def decode(pcap):
    result=subprocess.run(['tcpdump','-tttt','-nn','-vv','-X','-r',str(pcap)],capture_output=True,text=True,check=True,env={**os.environ,'TZ':'UTC'})
    return result.stdout

def replay(root,source,receipts):
    """Called only after original archive/static/source hashes have been verified."""
    pcap=root/'vpn/traffic.pcap';packets=root/'vpn/packets.txt'
    original=packets.read_bytes();text=original.decode('utf-8',errors='strict')
    mapping=interface_witness(pcap.read_bytes(),text)
    fresh=decode(pcap)
    require(normalized_packets(text)==normalized_packets(fresh),'Fresh packet decode differs beyond interface-name presentation')
    witness=dict(schema=1,interfaces=mapping,source_sha=source,pcap_sha256=sha(pcap.read_bytes()),packets_sha256=sha(original),
        normalized_decode_sha256=sha(normalized_packets(fresh).encode()),parent_network_namespace=os.readlink('/proc/self/ns/net'))
    with tempfile.TemporaryDirectory(prefix='evidence179-decoder-',dir=root.parent) as folder:
        witness_path=Path(folder)/'witness.json';witness_path.write_text(json.dumps(witness,sort_keys=True)+'\n')
        command=['sudo','unshare','--net','--',sys.executable,str(Path(__file__).resolve()),'--namespace-root',str(root.resolve()),'--source',source,
            '--witness',str(witness_path.resolve()),'--witness-sha256',sha(witness_path.read_bytes()),'--receipt',str((receipts/'packet-replay.json').resolve())]
        subprocess.run(command,check=True)
    require(packets.read_bytes()==original,'Frozen validator changed original packet rendering')

def namespace_replay(args):
    require(sha(args.witness.read_bytes())==args.witness_sha256,'Decoder witness changed')
    witness=json.loads(args.witness.read_text());root=args.namespace_root
    require(witness['source_sha']==args.source and os.readlink('/proc/self/ns/net')!=witness['parent_network_namespace'],'Decoder did not enter separate network namespace')
    require(dict(socket.if_nameindex())=={1:'lo'},'Decoder namespace is not fresh/offline')
    pcap=root/'vpn/traffic.pcap';packets=root/'vpn/packets.txt';original=packets.read_bytes()
    require(sha(pcap.read_bytes())==witness['pcap_sha256'] and sha(original)==witness['packets_sha256'],'Replay inputs changed')
    mapping=interface_witness(pcap.read_bytes(),original.decode())
    require(mapping==witness['interfaces'],'Original decoder identity witness differs')
    for index,name in mapping.items():
        if int(index)!=1:subprocess.run(['ip','link','add','name',name,'index',index,'type','dummy'],check=True)
    require(dict(socket.if_nameindex())=={1:'lo',**{int(index):name for index,name in mapping.items()}},'Decoder interface lookup differs')
    links=json.loads(subprocess.check_output(['ip','-j','address','show'],text=True))
    require(all(not link.get('addr_info') and 'UP' not in link.get('flags',[]) for link in links),'Decoder fixture unexpectedly network-active')
    fresh=decode(pcap)
    require(fresh.encode()==original and sha(normalized_packets(fresh).encode())==witness['normalized_decode_sha256'],'Restored decoder does not reproduce exact original rendering')
    subprocess.run([sys.executable,'scripts/verify_evidence179_routes.py','--android-results',str(root/'android-results'),
        '--vpn-root',str(root/'vpn'),'--onion-root',str(root/'onion'),'--results-root',str(root),'--source-sha',args.source,
        '--cases','scripts/evidence179_cases.json'],check=True)
    require(packets.read_bytes()==original and sha(pcap.read_bytes())==witness['pcap_sha256'],'Frozen replay changed original capture/rendering')
    binary=Path(shutil.which('tcpdump')).resolve()
    receipt=dict(schema=1,decision='PASS',source_sha=args.source,interfaces=mapping,pcap_sha256=witness['pcap_sha256'],
        original_packets_sha256=witness['packets_sha256'],replayed_packets_sha256=sha(fresh.encode()),
        normalized_decode_sha256=witness['normalized_decode_sha256'],isolated_offline_decoder_namespace=True,
        interface_purpose='Original capture name lookup only; dummy decoder fixtures are not tunnel or routing evidence',
        helper_sha256=sha(Path(__file__).read_bytes()),tcpdump_binary_sha256=sha(binary.read_bytes()),
        tcpdump_version=subprocess.check_output(['tcpdump','--version'],text=True).strip())
    args.receipt.write_text(json.dumps(receipt,sort_keys=True,indent=2)+'\n')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--namespace-root',type=Path,required=True);p.add_argument('--source',required=True)
    p.add_argument('--witness',type=Path,required=True);p.add_argument('--witness-sha256',required=True);p.add_argument('--receipt',type=Path,required=True)
    namespace_replay(p.parse_args())
