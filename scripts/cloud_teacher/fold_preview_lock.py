"""Take the shared Fold preview lock without following links or truncating it."""

import fcntl
import os
from pathlib import Path
import stat
import sys


LOCK_PATH = Path('/run/lock/nova-fold-owner-preview.lock')
SHELL_FD = 9


def acquire(path):
    flags = os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW | os.O_CLOEXEC | os.O_NONBLOCK
    fd = os.open(path, flags, 0o600)
    try:
        info = os.fstat(fd)
        if (not stat.S_ISREG(info.st_mode) or info.st_uid != os.geteuid()
                or info.st_nlink != 1 or info.st_mode & 0o022):
            raise ValueError('Preview lock is not a private, singly linked regular file.')
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        if not os.path.samestat(info, path.lstat()):
            raise ValueError('Preview lock changed while it was opened.')
        return fd
    except BaseException:
        os.close(fd)
        raise


def main():
    if len(sys.argv) != 2 or os.geteuid() != 0 or not os.isatty(0) or not os.isatty(1):
        sys.exit('Open the preview from the existing Fold SSH terminal.')
    try:
        fd = acquire(LOCK_PATH)
    except BlockingIOError:
        sys.exit('A Fold preview is already running.')
    except (OSError, ValueError):
        sys.exit('The existing Fold preview lock is unsafe or unavailable.')
    try:
        if fd != SHELL_FD:
            os.dup2(fd, SHELL_FD, inheritable=True)
            os.close(fd)
        else:
            os.set_inheritable(fd, True)
        launcher = Path(__file__).resolve().with_name('fold-owner-preview.sh')
        os.execv('/usr/bin/bash', ['bash', str(launcher), sys.argv[1], '--lock-held'])
    except OSError:
        sys.exit('The preview launcher could not start.')


if __name__ == '__main__':
    main()
