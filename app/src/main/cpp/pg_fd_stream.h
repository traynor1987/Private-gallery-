#pragma once
// Private Gallery-owned FD adapter. Model bytes remain data; no path permission is widened.
#include <istream>
#include <fstream>
#include <streambuf>
#include <string>
#include <algorithm>
#include <cerrno>
#include <climits>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
inline int pg_model_fd = -1; // One isolated process, one generation, held until free_sd_ctx.
inline bool pg_is_model(const std::string& path) { return path == "pg://model"; }
inline int pg_open_read(const std::string& path) {
    return pg_is_model(path) ? fcntl(pg_model_fd, F_DUPFD_CLOEXEC, 0) : open(path.c_str(), O_RDONLY | O_CLOEXEC);
}
class PgFdBuffer final : public std::streambuf {
    int fd_ = -1;
    off_t position_ = 0;
    char peek_ = 0;
    off_t logical() const { return position_ - (gptr() && gptr() < egptr() ? 1 : 0); }
protected:
    int_type underflow() override {
        if (gptr() && gptr() < egptr()) return traits_type::to_int_type(*gptr());
        ssize_t count;
        do { count = pread(fd_, &peek_, 1, position_); } while (count < 0 && errno == EINTR);
        if (count != 1) return traits_type::eof();
        ++position_; setg(&peek_, &peek_, &peek_ + 1);
        return traits_type::to_int_type(peek_);
    }
    std::streamsize xsgetn(char* output, std::streamsize size) override {
        off_t offset = logical(); setg(nullptr, nullptr, nullptr);
        std::streamsize done = 0;
        while (done < size) {
            const auto wanted = static_cast<size_t>(std::min<std::streamsize>(size - done, 1 << 20));
            ssize_t count = pread(fd_, output + done, wanted, offset + done);
            if (count < 0 && errno == EINTR) continue;
            if (count <= 0) break;
            done += count;
        }
        position_ = offset + done; return done;
    }
    pos_type seekoff(off_type delta, std::ios_base::seekdir direction, std::ios_base::openmode) override {
        off_t base = 0;
        if (direction == std::ios_base::cur) base = logical();
        else if (direction == std::ios_base::end) { struct stat st{}; if (fstat(fd_, &st)) return pos_type(off_type(-1)); base = st.st_size; }
        if ((delta > 0 && base > LLONG_MAX - delta) || (delta < 0 && delta < -base)) return pos_type(off_type(-1));
        position_ = base + delta; setg(nullptr, nullptr, nullptr); return position_;
    }
    pos_type seekpos(pos_type pos, std::ios_base::openmode mode) override { return seekoff(off_type(pos), std::ios_base::beg, mode); }
public:
    bool openFile(const std::string& path) { closeFile(); fd_ = pg_open_read(path); position_ = 0; return fd_ >= 0; }
    bool opened() const { return fd_ >= 0; }
    void closeFile() { if (fd_ >= 0) close(fd_); fd_ = -1; setg(nullptr, nullptr, nullptr); }
    ~PgFdBuffer() override { closeFile(); }
};
class PgInputStream final : public std::istream {
    PgFdBuffer buffer_;
public:
    PgInputStream() : std::istream(nullptr) { rdbuf(&buffer_); }
    explicit PgInputStream(const std::string& path, std::ios_base::openmode mode = std::ios::in) : PgInputStream() { open(path, mode); }
    void open(const std::string& path, std::ios_base::openmode = std::ios::in) { clear(); if (!buffer_.openFile(path)) setstate(std::ios::failbit); }
    bool is_open() const { return buffer_.opened(); }
    void close() { buffer_.closeFile(); }
};
