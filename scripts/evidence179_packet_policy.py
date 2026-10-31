"""Exact inherited host-adb multicast classifier; no general host exemption."""
import re

def host_fixture_adb_multicast(record):
 # The fixture owns203.0.113.10 on host lo. Host adb joins mDNS on that
 # address; these exact TTL1 local reports/queries are not Android origin routes.
 # Keep every other occurrence subject to the original WireGuard-only assertion.
 lines=record.splitlines()
 if len(lines)<2 or not re.match(r'^\d{4}-\d\d-\d\d \S+ lo\s+(?:In|Out)\s+IP \(tos [^,]+, ttl 1,',lines[0]):return False
 endpoint=lines[1].strip()
 if 'proto IGMP (2)' in lines[0]:
  return endpoint=='203.0.113.10 > 224.0.0.22: igmp v3 report, 1 group record(s) [gaddr 224.0.0.251 to_ex { }]'
 if 'proto UDP (17)' not in lines[0]:return False
 match=re.fullmatch(r'203\.0\.113\.10\.5353 > 224\.0\.0\.251\.5353: (?:\[bad udp cksum 0x[0-9a-f]+ -> 0x[0-9a-f]+!\] )?0 \[(2|3|4)q\] (.+) \((\d+)\)',endpoint)
 if match is None:return False
 count=int(match.group(1));body=match.group(2);size=int(match.group(3))
 questions=re.findall(r'(ANY|AAAA|A|SRV) ANY \(QM\)\? (\S+)',body)
 if len(questions)!=count or ' '.join(kind+' ANY (QM)? '+name for kind,name in questions)!=body:return False
 # These observed adb queries use uncompressed questions. Require both DNS
 # wire length and IPv4/UDP length, not merely a familiar name in the payload.
 wire_size=12+sum(sum(1+len(label) for label in name[:-1].split('.'))+5 for _,name in questions)
 ip_length=re.search(r', length (\d+)\)$',lines[0])
 if size!=wire_size or ip_length is None or int(ip_length.group(1))!=size+28:return False
 if count==3 and sorted(questions)==sorted(('ANY',name) for name in ('_adb._tcp.local.','_adb-tls-connect._tcp.local.','_adb-tls-pairing._tcp.local.')):return True
 # A single A and one or two AAAA questions: adb may duplicate IPv6 queries.
 service=[name for kind,name in questions if kind=='SRV']
 addresses=[(kind,name) for kind,name in questions if kind!='SRV']
 if sorted(kind for kind,_ in addresses) not in (['A','AAAA'],['A','AAAA','AAAA']):return False
 names={name for _,name in addresses}
 if len(names)!=1:return False
 name=next(iter(names))
 if not service:return name=='Android.local.'
 return len(service)==1 and re.fullmatch(r'adb-EMULATOR37X2X13X0-[A-Za-z0-9]{6}\._adb\._tcp\.local\.',service[0]) is not None and re.fullmatch(r'Android_[A-Z0-9]{8}\.local\.',name) is not None

