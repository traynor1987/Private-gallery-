#include <jni.h>
#include <assert.h>
#include <stdarg.h>
#include <stddef.h>
#include <string.h>
#include <sys/syscall.h>
#include <sys/random.h>

extern jint Java_uk_co_traynor_privategallery_core_security_staging_StagingEntropyInvocation_fillNative(JNIEnv *, jclass, jbyteArray);
static int length, pending, length_exception, copy_exception, syscall_calls, copy_calls, length_calls;
static long syscall_result;
static unsigned char target[32];
static jsize mock_length(JNIEnv *env, jarray array) { (void)env;(void)array;length_calls++;if(length_exception)pending=1;return length; }
static jboolean mock_exception(JNIEnv *env) { (void)env;return pending?JNI_TRUE:JNI_FALSE; }
static void mock_copy(JNIEnv *env, jbyteArray array, jsize offset, jsize count, const jbyte *bytes) {
    (void)env; assert(array!=NULL&&offset==0&&count==32&&!pending);copy_calls++;
    memcpy(target,bytes,copy_exception?7:32);if(copy_exception)pending=1;
}
long __wrap_syscall(long number, ...) {
    va_list args;va_start(args,number);void *p=va_arg(args,void *);size_t count=va_arg(args,size_t);int flags=va_arg(args,int);va_end(args);
    assert(number==SYS_getrandom&&count==32&&flags==GRND_NONBLOCK);syscall_calls++;
    memset(p,37,32);return syscall_result;
}
static const struct JNINativeInterface_ table={.GetArrayLength=mock_length,.ExceptionCheck=mock_exception,.SetByteArrayRegion=mock_copy};
static JNIEnv environment=&table;
static void reset(void) { length=32;pending=length_exception=copy_exception=syscall_calls=copy_calls=length_calls=0;syscall_result=32;memset(target,9,32); }
static int unchanged(void) { for(int i=0;i<32;i++)if(target[i]!=9)return 0;return 1; }
int main(void) {
    int tests=0;
    reset();assert(Java_uk_co_traynor_privategallery_core_security_staging_StagingEntropyInvocation_fillNative(&environment,NULL,NULL)==0);assert(syscall_calls==0&&copy_calls==0&&length_calls==0&&unchanged());tests++;
    for(int n=0;n<5;n++){int invalid[]={-1,0,31,33,65536};reset();length=invalid[n];assert(Java_uk_co_traynor_privategallery_core_security_staging_StagingEntropyInvocation_fillNative(&environment,NULL,(jbyteArray)target)==0);assert(syscall_calls==0&&copy_calls==0&&length_calls==1&&unchanged());tests++;}
    reset();pending=1;assert(Java_uk_co_traynor_privategallery_core_security_staging_StagingEntropyInvocation_fillNative(&environment,NULL,(jbyteArray)target)==0);assert(pending&&length_calls==0&&syscall_calls==0&&copy_calls==0&&unchanged());tests++;
    reset();length_exception=1;assert(Java_uk_co_traynor_privategallery_core_security_staging_StagingEntropyInvocation_fillNative(&environment,NULL,(jbyteArray)target)==0);assert(pending&&syscall_calls==0&&copy_calls==0&&unchanged());tests++;
    for(int n=0;n<6;n++){long results[]={-1,0,1,7,31,33};reset();syscall_result=results[n];assert(Java_uk_co_traynor_privategallery_core_security_staging_StagingEntropyInvocation_fillNative(&environment,NULL,(jbyteArray)target)==0);assert(syscall_calls==1&&copy_calls==0&&unchanged());tests++;}
    reset();copy_exception=1;assert(Java_uk_co_traynor_privategallery_core_security_staging_StagingEntropyInvocation_fillNative(&environment,NULL,(jbyteArray)target)==0);assert(pending&&syscall_calls==1&&copy_calls==1);for(int i=0;i<32;i++)assert(target[i]==(i<7?37:9));tests++;
    reset();assert(Java_uk_co_traynor_privategallery_core_security_staging_StagingEntropyInvocation_fillNative(&environment,NULL,(jbyteArray)target)==32);assert(!pending&&syscall_calls==1&&copy_calls==1);for(int i=0;i<32;i++)assert(target[i]==37);tests++;
    // Model tests only: no native stack access after lifetime, no Java root/ART/OEM acceptance.
    return tests==16?0:1;
}
