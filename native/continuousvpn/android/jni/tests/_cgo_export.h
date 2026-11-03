// Test-only declarations matching the exported Go C ABI; not in production include paths.
#include <stdint.h>
long long goNovaCreate(int,int,char*);
long long goNovaReplace(long long,long long,long long,char*,char*,char*,char*,char*,int,int,uintptr_t);
long long goNovaEpoch(long long);
long long goNovaInvalidate(long long);
long long goNovaInvalidateTransport(long long,long long);
long long goNovaRetire(long long,long long);
long long goNovaRetireCurrent(long long,long long,long long);
long long goNovaBegin(long long,long long,long long);
long long goNovaSequence(long long,long long,long long);
long long goNovaPublish(long long,long long,long long,long long);
long long goNovaRemove(long long,long long,long long,long long);
long long goNovaRegister(long long,long long,long long,int,char*,char*);
long long goNovaRelease(long long,int);
long long goNovaStatistics(long long,long long,long long,long long*);
