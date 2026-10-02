"""Nonproduction reference constraints. No AEAD, key unwrap, OS durability or APK code."""
import hashlib
import struct

CAP=2**20
MAX=2**63-1

def check(value):
    if not value: raise ValueError('design constraint rejected')

def reference(purpose,object_id,generation,length,sha):
    check(purpose in range(1,12) and len(object_id)==16 and any(object_id))
    check(0<generation<=MAX and 172<=length<=MAX and len(sha)==32)
    return struct.pack('>H16sQQ32s',purpose,object_id,generation,length,sha)

def parse_reference(raw):
    check(len(raw)==66)
    fields=struct.unpack('>H16sQQ32s',raw)
    check(reference(*fields)==raw)
    return fields

def closure(contexts):
    check(len(contexts)<=256 and len(set(contexts))==len(contexts))
    check(contexts==sorted(contexts))
    check(all(p in {1,5,7} and len(o)==16 and any(o) and 0<g<=MAX for p,o,g in contexts))
    return contexts

def ledger(invocations,blocks,queries,sequence,sha,attempt):
    check(0<=invocations<=CAP and 0<=blocks<=2**32 and 0<=queries<=CAP)
    check(0<sequence<=MAX and len(sha)==32 and len(attempt)==16 and any(attempt))
    return struct.pack('>HQQQQ32s16s',2,invocations,blocks,queries,sequence,sha,attempt)

def parse_ledger(raw):
    check(len(raw)==82)
    version,*fields=struct.unpack('>HQQQQ32s16s',raw)
    check(version==2 and ledger(*fields)==raw)
    return tuple(fields)

def video_charge(size):
    check(0<=size<=8*2**30)
    n=(size+2**20-1)//2**20
    return n, n*11+(size+15)//16

def preflight(used,cost):
    check(len(used)==len(cost))
    check(all(0<=u<=CAP and 0<=n<=CAP and u+n<=CAP for u,n in zip(used,cost)))
    return [u+n for u,n in zip(used,cost)]

def abandon(used): return list(used)

def two_pass(first,second,limit,total_seconds,no_progress_seconds):
    check(len(first)<=limit and total_seconds<=1200 and no_progress_seconds<=30)
    check(len(first)==len(second) and hashlib.sha256(first).digest()==hashlib.sha256(second).digest())

def item():
    return dict(item='i1',payload=b'H'*32,generation=1,length=3,sha=b'P'*32,
                state='active',origin=1,restrictions=1,name='fixture',crop=None,meta=1)

def cleanup_predicate(old,new):
    return all(old[k]==new[k] for k in ['item','payload','generation','length','sha','origin']) and new['state']=='active' and new['restrictions']&old['restrictions']==old['restrictions']

def unreferenced(files,dependencies):
    live=set().union(*dependencies.values()) if dependencies else set()
    return set(files)-live

def release_allowed(state,paired_ack): return state=='RELEASE_ACK' and paired_ack

EDGES={(1,2),(2,3),(3,4),(4,5),(1,11),(2,11),(3,10),(4,11),(5,6),(6,7),(5,8),(8,9),(7,12),(9,12),(10,12),(11,12)}
def transition(before,after): check((before,after) in EDGES)
def backup_allowed(states): return all(s in {7,9,10,11,12} for s in states)
def restore(current,item_id,metadata):
    check(item_id not in current)
    return dict(current,**{item_id:metadata})
def restart(selector,authenticated):
    check(selector in authenticated)
    return selector

def lock_path(ranks): check(all(a<b for a,b in zip(ranks,ranks[1:])))
def cleanup(held_locks): check(not held_locks)
def admit_after_cleanup(queued,failed,unfinished): return queued==0 and not failed and unfinished==0

def authorize(operations): check(all(operations.get(k) for k in ['primary','hidden','fresh']))
def proof():
    return dict(primary=True,hidden=True,fresh=True,active=True,catalog_hash=True,
                anchor_hash=True,selected=True,confirmed=True,slot_hash=True,
                ledger=True,cleanup=True,transfer='t1',revision=1,consumed=False)
def proof_valid(value):
    authorize(value)
    check(all(value.get(k) for k in ['active','catalog_hash','anchor_hash','selected','confirmed','slot_hash','ledger','cleanup']))
    return True

def consume(value,action,transfer,revision):
    proof_valid(value)
    check(not value['consumed'] and action=='restore' and transfer==value['transfer'] and revision==value['revision'])
    value['consumed']=True

def restore_crash(selector):
    check(selector in {'prior','next'})
    return dict(payload_retained=True,ordinary=selector=='next',hold=selector=='prior')

def dag(edges):
    completed=set()
    while len(completed)<len(edges):
        next_nodes={n for n,deps in edges.items() if n not in completed and set(deps)<=completed}
        check(next_nodes)
        completed|=next_nodes
    return completed

def string(value):
    raw=value.encode('utf8',errors='strict')
    check(len(raw)<=4096 and not raw.startswith(b'\xef\xbb\xbf'))
    return struct.pack('>I',len(raw))+raw

def optional_string(value):
    return b'\0' if value is None else b'\1'+string(value)

def merge_bytes(item_body,rows,favourite):
    # item_body is a separately validated source-field projection, not a parser.
    check(0<len(item_body)<=65536 and len(rows)<=128)
    ids=[r[0].encode('utf8') for r in rows]
    check(ids==sorted(ids) and len(set(ids))==len(ids))
    out=b'PGMRG001'+struct.pack('>HI',1,len(item_body))+item_body+struct.pack('>H',len(rows))
    for identity,name,created,pinned,cover,member in rows:
        check(identity and name and pinned in {0,1})
        out+=string(identity)+string(name)+struct.pack('>qH',created,pinned)+optional_string(cover)
        out+=b'\0' if member is None else b'\1'+struct.pack('>q',member)
    out+=optional_string(favourite)
    check(len(out)<=65536)
    return out

def legal_attempt_path(path,owned_names):
    pieces=path.split('/')
    check(len(pieces)<=6 and pieces[:3]==['transactions','media','attempts'])
    check(len(pieces)==6 and pieces[4]=='files' and pieces[5] in owned_names)
    check(len(pieces[3])==32 and all(x in '0123456789abcdef' for x in pieces[3]))

def charge_pair(used,cost,fail_after=None):
    target=preflight(used,cost)
    result=list(used)
    for i in range(len(result)):
        result[i]=target[i]
        if fail_after==i: return result,False
    return result,True
