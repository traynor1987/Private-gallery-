"""Read-only collision check for the single-use Fold preview preparation.

Run as root with the preview deployment lock held. Only local state locations are
searched. Container images and caches are excluded, with Docker volumes retained.
No credential files are opened, and SQLite is opened immutable with no active journal.
"""

from contextlib import closing
from pathlib import Path
import os
import re
import sqlite3
import stat
import sys


PREVIEW_ROOT = Path('/var/lib/nova-fold-owner-preview')
SEARCH_ROOTS = tuple(map(Path, (
    '/var/lib', '/opt', '/srv', '/root', '/home', '/etc', '/var/backups',
    '/var/local', '/var/tmp', '/tmp', '/mnt', '/media', '/run', '/data', '/storage',
    '/backup', '/backups',
)))
PREVIEW_NAME = re.compile(
    r'(?<![a-z0-9])(?:fold[_. -]+owner(?:[_. -]+preview)?|nova[_. -]+owner[_. -]+preview'
    r'|approval[_. -]+20[0-9]{6})(?![a-z0-9])', re.IGNORECASE,
)
BUDGET_NAME = re.compile(r'(?<![a-z0-9])budget(?:[_. -][a-z0-9]+)*\.(?:db|sqlite3?)(?![a-z0-9])', re.IGNORECASE)
SOURCE_FILES = tuple(map(Path, (
    'scripts/cloud_teacher/fold-owner-preview.sh',
    'scripts/cloud_teacher/fold_owner_preview.py',
    'scripts/cloud_teacher/test_fold_owner_preview.py',
    '.github/workflows/nova-brain-fold-owner-preview.yml',
)))
SIDECAR_SUFFIXES = ('-journal', '-shm', '-wal')
MAX_LEDGER_SIZE = 128 * 1024 * 1024
MAX_ENTRIES = 250_000
MAX_DEPTH = 18

TABLES = {
    'reservations': ('cycle:TEXT', 'total:INTEGER'),
    'daily_reservations': ('day:TEXT', 'total:INTEGER', 'requests:INTEGER'),
    'budget_metadata': ('key:TEXT', 'value:TEXT'),
    'legacy_reservation_dates': ('day:TEXT', 'total:INTEGER'),
    'request_reservations': ('id:TEXT', 'digest:TEXT', 'cycle:TEXT', 'day:TEXT', 'bound:INTEGER',
                             'state:TEXT', 'actual:INTEGER', 'settled_day:TEXT'),
}
PRODUCTION_SCHEMAS = ({'reservations'}, {'reservations', 'daily_reservations'}, set(TABLES))
PRODUCTION_METADATA_KEYS = {'legacy_unattributed', 'latest_request_time', 'halted'}
COMPOSE_VOLUMES = {'nova-cloud-teacher_state', 'nova-cloud-teacher-staging_state'}
DEFAULT_DOCKER_ROOT = Path('/var/lib/docker')
DOCKER_IMAGE_DIRECTORIES = {
    'aufs', 'btrfs', 'buildkit', 'containerd', 'containers', 'devicemapper', 'fuse-overlayfs',
    'image', 'network', 'overlay2', 'overlayfs', 'plugins', 'vfs', 'zfs',
}
DOCKER_SCAN_LABEL = 'Docker volume or mounted state'


class PreflightRefused(Exception):
    """Carries only a fixed reason, never a candidate path or ledger value."""


def verify_canonical_root(root):
    try:
        info = root.lstat()
    except FileNotFoundError:
        return
    if (not stat.S_ISDIR(info.st_mode) or info.st_uid != os.geteuid() or info.st_mode & 0o077):
        raise PreflightRefused('The canonical preview directory is not private or has uncertain identity.')
    with os.scandir(root) as entries:
        for entry in entries:
            if entry.name != 'approval-20261123' or not empty_historical_directories(Path(entry.path)):
                raise PreflightRefused('Prior or partial state exists in the canonical preview directory.')


def empty_historical_directories(path):
    directory = path.lstat()
    if (not stat.S_ISDIR(directory.st_mode) or directory.st_uid != os.geteuid()
            or directory.st_mode & 0o077):
        return False
    with os.scandir(path) as entries:
        child = next(entries, None)
        if child is None:
            return True
        if child.name != 'ledger' or next(entries, None) is not None:
            return False
        ledger = child.stat(follow_symlinks=False)
        if (not stat.S_ISDIR(ledger.st_mode) or ledger.st_uid not in (os.geteuid(), 10001)
                or ledger.st_mode & 0o077):
            return False
        with os.scandir(child.path) as ledger_entries:
            return next(ledger_entries, None) is None


def standard_compose_volume(path, docker_volumes=DEFAULT_DOCKER_ROOT / 'volumes'):
    return (path.name == 'budget.db' and path.parent.name == '_data' and
            path.parent.parent.name in COMPOSE_VOLUMES and
            path.parent.parent.parent == docker_volumes)


def ledger_identity(path):
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode) or not 0 < info.st_size <= MAX_LEDGER_SIZE:
        raise PreflightRefused('A budget candidate is missing a verifiable regular SQLite database.')
    return info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_ctime_ns


def verify_sidecars(path):
    base = path.name.casefold()
    names = {base + suffix for suffix in SIDECAR_SUFFIXES}
    with os.scandir(path.parent) as siblings:
        for sibling in siblings:
            if sibling.name.casefold() not in names:
                continue
            info = sibling.stat(follow_symlinks=False)
            if not stat.S_ISREG(info.st_mode) or info.st_size != 0:
                raise PreflightRefused('A budget database has an active or unreadable SQLite sidecar.')


def verify_normal_ledger(path, docker_volumes):
    before = ledger_identity(path)
    verify_sidecars(path)
    with closing(sqlite3.connect(path.as_uri() + '?mode=ro&immutable=1', uri=True, timeout=1)) as db:
        db.execute('PRAGMA query_only=ON')
        db.execute('PRAGMA trusted_schema=OFF')
        db.execute('PRAGMA temp_store=MEMORY')
        steps = iter(range(10_000))
        db.set_progress_handler(lambda: next(steps, None) is None, 1000)
        objects = set(db.execute('SELECT type,name FROM sqlite_master WHERE sql IS NOT NULL LIMIT 32'))
        tables = {name for kind, name in objects if kind == 'table'}
        if any(str(name).casefold() == 'fold_one_per_fixture' for _, name in objects):
            raise PreflightRefused('A relocated SQLite budget contains the Fold preview index.')
        if tables not in PRODUCTION_SCHEMAS or objects != {('table', name) for name in tables}:
            raise PreflightRefused('A SQLite budget could not be identified as a production ledger.')
        for name in tables:
            columns = tuple(f'{row[1]}:{row[2]}' for row in db.execute(f'PRAGMA table_info({name})'))
            if columns != TABLES[name]:
                raise PreflightRefused('A SQLite budget has an unrecognised schema.')
        if db.execute('PRAGMA quick_check(1)').fetchone() != ('ok',):
            raise PreflightRefused('A SQLite budget did not pass its read-only integrity check.')

        has_cycle = False
        for table in ('reservations', 'request_reservations'):
            if table not in tables:
                continue
            # These are fixed, verified table names. Cycle values remain in memory.
            for (cycle,) in db.execute(f'SELECT cycle FROM {table}'):
                if not isinstance(cycle, str) or not cycle.strip() or len(cycle) > 200 or PREVIEW_NAME.search(cycle):
                    raise PreflightRefused('A SQLite budget contains a possible Fold preview approval.')
                has_cycle = True
        if 'budget_metadata' in tables:
            metadata = {key for (key,) in db.execute('SELECT key FROM budget_metadata LIMIT 4')}
            if 'legacy_unattributed' not in metadata or not metadata <= PRODUCTION_METADATA_KEYS:
                raise PreflightRefused('A SQLite budget contains unrecognised approval or accounting metadata.')
        if not has_cycle and not standard_compose_volume(path, docker_volumes):
            raise PreflightRefused('An empty budget outside the known production volumes has no verifiable origin.')
    verify_sidecars(path)
    if before != ledger_identity(path):
        raise PreflightRefused('A SQLite budget changed while its identity was being checked.')


def excluded_directory(path, docker_root):
    if path.name in {'.git', '.cache', 'node_modules', '__pycache__'}:
        return True
    if (path.parent == docker_root or path.parent.name == 'docker') and path.name in DOCKER_IMAGE_DIRECTORIES:
        return True
    return path.parent.name == 'containerd' and path.name.startswith(('io.containerd.snapshotter.', 'io.containerd.content.'))


def reviewed_checkout(path):
    return ((path / '.git').is_dir() or (path / '.git').is_file()) and (
        path / 'scripts' / 'cloud_teacher' / 'fold-owner-preview.sh'
    ).is_file()


def reviewed_source(path):
    for source in SOURCE_FILES:
        depth = len(source.parts)
        if path.parts[-depth:] == source.parts:
            return reviewed_checkout(path.parents[depth - 1])
    return False


def scan_relocations(roots, preview_root, docker_root, docker_volumes, required_roots):
    pending = []
    for root, scope in roots.items():
        try:
            info = root.lstat()
        except FileNotFoundError as exc:
            if root in required_roots:
                raise PreflightRefused(f'Mounted state could not be read reliably. Search location: {scope}.') from exc
            continue
        except OSError as exc:
            raise PreflightRefused(f'State could not be read reliably. Search location: {scope}.') from exc
        if not stat.S_ISDIR(info.st_mode):
            raise PreflightRefused(f'A relocation search root has uncertain identity. Search location: {scope}.')
        pending.append((root, 0, scope))
    scanned = 0
    visited = set()
    while pending:
        directory, depth, scope = pending.pop()
        if directory in visited:
            continue
        visited.add(directory)
        try:
            with os.scandir(directory) as entries:
                for entry in entries:
                    scanned += 1
                    if scanned > MAX_ENTRIES:
                        raise PreflightRefused('The relocation search exceeded its safe size limit.')
                    path = Path(entry.path)
                    if path in (preview_root, Path('/run/lock/nova-fold-owner-preview.lock')):
                        continue
                    is_directory = entry.is_dir(follow_symlinks=False)
                    if PREVIEW_NAME.search(entry.name) and not (
                        (is_directory and reviewed_checkout(path)) or
                        (entry.is_file(follow_symlinks=False) and reviewed_source(path))
                    ):
                        raise PreflightRefused('A prior preview marker was found outside the canonical directory.')
                    if BUDGET_NAME.search(entry.name):
                        suffix = next((suffix for suffix in SIDECAR_SUFFIXES if entry.name.casefold().endswith(suffix)), None)
                        candidate = path if suffix is None else path.with_name(entry.name[:-len(suffix)])
                        verify_normal_ledger(candidate, docker_volumes)
                    if is_directory and path not in visited and not excluded_directory(path, docker_root):
                        if depth >= MAX_DEPTH:
                            raise PreflightRefused('The relocation search exceeded its safe directory depth.')
                        pending.append((path, depth + 1, scope))
        except (OSError, sqlite3.Error, UnicodeError) as exc:
            raise PreflightRefused(f'State could not be read reliably. Search location: {scope}.') from exc
        except PreflightRefused as exc:
            raise PreflightRefused(f'{exc} Search location: {scope}.') from exc


def verify_fresh_preview(preview_root=PREVIEW_ROOT, search_roots=SEARCH_ROOTS, *, docker_root=None, state_mounts=()):
    roots = {path: str(path) for path in search_roots}
    if docker_root is None:
        docker_root, docker_volumes = DEFAULT_DOCKER_ROOT, DEFAULT_DOCKER_ROOT / 'volumes'
        mounts = ()
    else:
        docker_root, docker_volumes, mounts = resolve_docker_locations(docker_root, state_mounts)
        for path in (docker_volumes, *mounts):
            # Always add these roots. The enclosing host traversal may skip caches,
            # container layers, or parent paths reached through a symlink.
            roots.setdefault(path, DOCKER_SCAN_LABEL)
    verify_canonical_root(preview_root)
    scan_relocations(roots, preview_root, docker_root, docker_volumes, set(mounts))
    verify_canonical_root(preview_root)


def resolve_docker_locations(docker_root, state_mounts):
    reported_paths = (docker_root, *state_mounts)
    if not all(path.is_absolute() for path in reported_paths):
        raise PreflightRefused('A Docker state location has uncertain identity.')
    try:
        docker_root = docker_root.resolve(strict=True)
        docker_volumes = (docker_root / 'volumes').resolve()
        mounts = tuple(path.resolve(strict=True) for path in reported_paths[1:])
    except (OSError, RuntimeError) as exc:
        raise PreflightRefused(f'State could not be read reliably. Search location: {DOCKER_SCAN_LABEL}.') from exc
    if Path('/') in (docker_root, docker_volumes, *mounts):
        raise PreflightRefused('Docker reports a state location too broad to verify safely.')
    if any(PREVIEW_NAME.search(part) for path in (*reported_paths, docker_root, docker_volumes, *mounts)
           for part in path.parts):
        raise PreflightRefused('A prior preview marker was found in a Docker state location.')
    return docker_root, docker_volumes, mounts


def main():
    if len(sys.argv) < 2 or os.geteuid() != 0:
        sys.exit('Run this preflight only through the root preview deployment script.')
    try:
        docker_root, *mounts = map(Path, sys.argv[1:])
        verify_fresh_preview(docker_root=docker_root, state_mounts=mounts)
    except PreflightRefused as exc:
        sys.exit(f'Preview preparation refused. {exc}')
    except (OSError, sqlite3.Error, UnicodeError):
        sys.exit('Preview preparation refused. The canonical state could not be read reliably.')


if __name__ == '__main__':
    main()
