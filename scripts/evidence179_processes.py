#!/usr/bin/env python3
"""Disposable fixture process ownership, shared by launch, archive and every cleanup.

PID files alone are never authority. A pidfd pins the exact process while its
boot/start/executable/argv/UID and open output-file identities are rechecked.
"""
import argparse
import json
import os
from pathlib import Path
import select
import signal
import time


def inventory(pid):
    root=Path('/proc')/str(pid)
    try:
        stat=(root/'stat').read_text();tail=stat[stat.rindex(')')+2:].split()
        if tail[0]=='Z':return None
        argv=[os.fsdecode(x) for x in (root/'cmdline').read_bytes().split(b'\0') if x]
        if not argv:return None
        uid=int(next(line for line in (root/'status').read_text().splitlines() if line.startswith('Uid:')).split()[1])
        files={}
        for fd in (root/'fd').iterdir():
            try:
                target=os.readlink(fd)
                if target.startswith('/'):
                    item=fd.stat();files[target]=dict(device=item.st_dev,inode=item.st_ino)
            except FileNotFoundError:pass
        return dict(pid=pid,boot_id=Path('/proc/sys/kernel/random/boot_id').read_text().strip(),
                    start_ticks=int(tail[19]),uid=uid,executable=os.readlink(root/'exe'),argv=argv,files=files)
    except (FileNotFoundError,ProcessLookupError):return None


def descendants(pid):
    result=[pid]
    for current in result:
        path=Path(f'/proc/{current}/task/{current}/children')
        try:result.extend(int(x) for x in path.read_text().split() if int(x) not in result)
        except FileNotFoundError:pass
    return result


def matching(value, executable, arguments, outputs):
    return value is not None and Path(value['argv'][0]).name==executable and all(x in value['argv'] for x in arguments) and all(str(Path(p).resolve()) in value['files'] for p in outputs)


def register(path,parent,executable,arguments,outputs,timeout=10):
    path=Path(path)
    if path.exists():raise RuntimeError('Refuse to replace process ownership identity')
    deadline=time.monotonic()+timeout
    previous=None
    while True:
        matches=[v for pid in descendants(parent) if matching(v:=inventory(pid),executable,arguments,outputs)]
        if len(matches)==1:
            value=dict(matches[0])
            value['outputs']={str(Path(p).resolve()):value['files'][str(Path(p).resolve())] for p in outputs}
            del value['files']
            if value==previous:
                path.write_text(json.dumps(value,sort_keys=True)+'\n');path.chmod(0o644)
                return value
            previous=value
        if len(matches)>1 or time.monotonic()>=deadline:raise RuntimeError('Cannot establish exactly one owned fixture process')
        time.sleep(.05)


class System:
    inventory=staticmethod(inventory)
    @staticmethod
    def open(pid):return os.pidfd_open(pid)
    @staticmethod
    def send(fd,sig):signal.pidfd_send_signal(fd,sig)
    @staticmethod
    def wait(fd,timeout):
        poll=select.poll();poll.register(fd,select.POLLIN)
        return bool(poll.poll(int(timeout*1000)))
    @staticmethod
    def close(fd):os.close(fd)


def owned(identity,value):
    if value is None:return False
    keys=('pid','boot_id','start_ticks','uid','executable','argv')
    return all(value.get(k)==identity.get(k) for k in keys) and bool(identity.get('outputs')) and all(value['files'].get(path)==item for path,item in identity['outputs'].items())


def terminate(identity,sig=signal.SIGTERM,timeout=15,system=System):
    """No fallback signal on any rejected/missing/unflushed identity."""
    try:fd=system.open(identity['pid'])
    except ProcessLookupError:return 'missing'
    try:
        if not owned(identity,system.inventory(identity['pid'])):return 'ownership-rejected'
        try:system.send(fd,sig)
        except ProcessLookupError:return 'missing'
        return 'stopped' if system.wait(fd,timeout) else 'unflushed'
    finally:system.close(fd)


def stop_file(path,sig=signal.SIGTERM,timeout=15):
    path=Path(path)
    if not path.exists():return 'missing-identity'
    identity=json.loads(path.read_text())
    return terminate(identity,sig,timeout)


def main():
    parser=argparse.ArgumentParser(description=__doc__);sub=parser.add_subparsers(dest='command',required=True)
    launch=sub.add_parser('register');launch.add_argument('--identity',required=True);launch.add_argument('--parent',type=int,required=True);launch.add_argument('--executable',required=True);launch.add_argument('--argument',action='append',default=[]);launch.add_argument('--output',action='append',required=True)
    stop=sub.add_parser('stop');stop.add_argument('--identity',required=True);stop.add_argument('--signal',choices=['TERM','INT'],default='TERM')
    args=parser.parse_args()
    if args.command=='register':register(args.identity,args.parent,args.executable,args.argument,args.output)
    else:
        result=stop_file(args.identity,signal.SIGINT if args.signal=='INT' else signal.SIGTERM);print(result)
        if result!='stopped':raise SystemExit(1)

if __name__=='__main__':main()
