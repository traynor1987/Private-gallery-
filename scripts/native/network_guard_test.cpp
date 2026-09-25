#include "../../app/src/main/cpp/pg_network_guard.h"
#include <cassert>
#include <cerrno>
#include <sys/socket.h>
#include <sys/wait.h>
#include <unistd.h>
#include <thread>
#include <atomic>

// Catches a missing filter, missing IPv6 denial, and filtering only the caller thread.
int main() {
    const pid_t child = fork();
    assert(child >= 0);
    if (child == 0) {
        std::atomic<bool> ready{false}, run{false};
        std::thread existing([&] {
            ready = true;
            while (!run) std::this_thread::yield();
            errno = 0;
            assert(socket(AF_INET, SOCK_STREAM, 0) == -1 && errno == EPERM);
        });
        while (!ready) std::this_thread::yield();
        assert(pg_restrict_network());
        assert(pg_network_denied());
        run = true;
        existing.join();
        int pipeFds[2];
        assert(pipe(pipeFds) == 0);
        assert(write(pipeFds[1], "a", 1) == 1);
        char byte = 0;
        assert(read(pipeFds[0], &byte, 1) == 1 && byte == 'a');
        close(pipeFds[0]); close(pipeFds[1]);
        _exit(0);
    }
    int status = 0;
    assert(waitpid(child, &status, 0) == child);
    assert(WIFEXITED(status) && WEXITSTATUS(status) == 0);
}
