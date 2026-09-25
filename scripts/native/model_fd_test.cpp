#include "../../app/src/main/cpp/pg_fd_stream.h"
#include <cassert>
#include <thread>
#include <sys/wait.h>
int main() {
 char filename[]="/tmp/pg-fd-XXXXXX";
 int fd=mkstemp(filename); assert(fd>=0);
 assert(write(fd,"0123456789",10)==10); fchmod(fd,0600);
 assert(unlink(filename)==0); // No path remains to reopen. The inherited descriptor is authoritative.
 pg_model_fd=fd;
 pid_t child=fork(); assert(child>=0);
 if(child==0) {
  PgInputStream a("pg://model"),b("pg://model"); assert(a.is_open()&&b.is_open());
  char x[5]{}; a.read(x,4); assert(std::string(x)=="0123");
  b.seekg(6); b.read(x,4); assert(std::string(x)=="6789");
  a.read(x,4); assert(std::string(x)=="4567");
  a.seekg(0,std::ios::end); assert(a.tellg()==10);
  a.seekg(-2,std::ios::end); assert(a.peek()=='8'); assert(a.tellg()==8); assert(a.get()=='8'); assert(a.tellg()==9);
  assert(a.get()=='9'); assert(a.get()==std::char_traits<char>::eof());
  a.clear(); a.seekg(0); assert(a.get()=='0');
  std::thread t1([&] { for(int i=0;i<500;i++){ a.seekg(3);assert(a.get()=='3'); } });
  std::thread t2([&] { for(int i=0;i<500;i++){ b.seekg(8);assert(b.get()=='8'); } });
  t1.join();t2.join(); _exit(0);
 }
 int result=0;waitpid(child,&result,0);close(fd);unlink(filename);assert(WIFEXITED(result)&&WEXITSTATUS(result)==0);
}
