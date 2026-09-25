#pragma once
#include <cerrno>
#include <cstddef>
#include <initializer_list>
#include <linux/audit.h>
#include <linux/filter.h>
#include <linux/seccomp.h>
#include <sys/prctl.h>
#include <sys/socket.h>
#include <sys/syscall.h>
#include <unistd.h>

// Defence in depth for the app-UID GPU process, NOT a substitute for Android UID
// isolation. Binder/filesystem permissions are unchanged. Installed before any
// image is mapped, on ALL existing threads, inherited by newly created threads.
inline bool pg_restrict_network() {
#if defined(__aarch64__) || defined(__x86_64__)
#if defined(__aarch64__)
    constexpr unsigned int architecture = AUDIT_ARCH_AARCH64;
#else
    constexpr unsigned int architecture = AUDIT_ARCH_X86_64;
#endif
    const sock_filter filter[] = {
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, arch)),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, architecture, 1, 0),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_KILL_PROCESS),
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, nr)),
#if defined(__x86_64__)
        // Reject the alternate x32 syscall ABI rather than allowing bypasses.
        BPF_JUMP(BPF_JMP | BPF_JSET | BPF_K, 0x40000000, 0, 1),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | EPERM),
#endif
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_connect, 0, 1),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | EPERM),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_sendto, 0, 1),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | EPERM),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_sendmsg, 0, 1),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | EPERM),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_sendmmsg, 0, 1),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | EPERM),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_socket, 0, 5),
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, args[0])),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, AF_INET, 0, 1),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | EPERM),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, AF_INET6, 0, 1),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | EPERM),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
    };
    sock_fprog program{static_cast<unsigned short>(sizeof(filter) / sizeof(filter[0])),
        const_cast<sock_filter*>(filter)};
    return prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) == 0 &&
        syscall(__NR_seccomp, SECCOMP_SET_MODE_FILTER, SECCOMP_FILTER_FLAG_TSYNC, &program) == 0;
#else
    return false;
#endif
}
inline bool pg_network_denied() {
    bool blocked = true;
    for (int domain : {AF_INET, AF_INET6}) {
        errno = 0;
        const int fd = socket(domain, SOCK_STREAM, 0);
        blocked = blocked && fd == -1 && errno == EPERM;
        if (fd >= 0) close(fd);
    }
    return blocked;
}
