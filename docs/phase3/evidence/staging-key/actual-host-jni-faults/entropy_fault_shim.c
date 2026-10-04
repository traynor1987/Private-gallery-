#define _GNU_SOURCE
#include <jni.h>
#include <stdarg.h>
#include <sys/random.h>
#include <sys/syscall.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <pthread.h>
#include <stdatomic.h>

/* TEST ONLY: separate host library/JVM, never linked into any application APK. */
extern jint pg_entropy_candidate(JNIEnv *,jclass,jbyteArray);
static _Thread_local JNIEnv *actual_env;
static _Atomic int native_calls, copied_bytes;
static pthread_mutex_t gate=PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t changed=PTHREAD_COND_INITIALIZER;
static int entered, released;
static const char *mode(void) { const char *m=getenv("PG_STAGING_ENTROPY_TEST_MODE");if(!m)abort();return m; }
long __wrap_syscall(long number,...) {
    if(number!=SYS_getrandom)abort();
    va_list arguments;va_start(arguments,number);
    void *target=va_arg(arguments,void *);size_t length=va_arg(arguments,size_t);int flags=va_arg(arguments,int);va_end(arguments);
    if(length!=32 || flags!=GRND_NONBLOCK)abort();
    native_calls++;memset(target,37,32);
    const char *m=mode();
    if(!strcmp(m,"eagain")){errno=EAGAIN;return -1;}
    if(!strcmp(m,"eintr")){errno=EINTR;return -1;}
    if(!strcmp(m,"partial")||!strcmp(m,"blocked")||!strcmp(m,"success"))return 32;
    char *end=NULL;long count=strtol(m,&end,10);if(!end||*end)abort();return count;
}
static jboolean JNICALL proxy_exception(JNIEnv *unused) { (void)unused;return (*actual_env)->ExceptionCheck(actual_env); }
static jsize JNICALL proxy_length(JNIEnv *unused,jarray target) { (void)unused;return (*actual_env)->GetArrayLength(actual_env,target); }
static void JNICALL proxy_region(JNIEnv *unused,jbyteArray target,jsize start,jsize length,const jbyte *source) {
    (void)unused;if(start!=0||length!=32)abort();
    const char *m=mode();jsize n=!strcmp(m,"partial")?7:length;
    (*actual_env)->SetByteArrayRegion(actual_env,target,start,n,source);
    if((*actual_env)->ExceptionCheck(actual_env))return;
    copied_bytes=n;
    if(!strcmp(m,"partial")) {
        jclass type=(*actual_env)->FindClass(actual_env,"java/io/IOException");
        if(type)(*actual_env)->ThrowNew(actual_env,type,"Public partial-copy JNI fixture");
    } else if(!strcmp(m,"blocked")) {
        pthread_mutex_lock(&gate);entered=1;pthread_cond_broadcast(&changed);
        while(!released)pthread_cond_wait(&changed,&gate);
        pthread_mutex_unlock(&gate);
    }
}
JNIEXPORT jint JNICALL Java_uk_co_traynor_privategallery_core_security_staging_StagingEntropyInvocation_fillNative(JNIEnv *env,jclass type,jbyteArray target) {
    actual_env=env;
    const struct JNINativeInterface_ table={.ExceptionCheck=proxy_exception,.GetArrayLength=proxy_length,.SetByteArrayRegion=proxy_region};
    JNIEnv proxy=&table;
    // Every VM delegate above receives actual_env, NEVER the synthetic table's address.
    jint result=pg_entropy_candidate(&proxy,type,target);actual_env=NULL;return result;
}
JNIEXPORT jint JNICALL Java_uk_co_traynor_privategallery_core_security_staging_StagingEntropyFaultHarness_nativeCalls(JNIEnv *env,jclass type) {(void)env;(void)type;return native_calls;}
JNIEXPORT jint JNICALL Java_uk_co_traynor_privategallery_core_security_staging_StagingEntropyFaultHarness_copiedBytes(JNIEnv *env,jclass type) {(void)env;(void)type;return copied_bytes;}
JNIEXPORT jboolean JNICALL Java_uk_co_traynor_privategallery_core_security_staging_StagingEntropyFaultHarness_nativeEntered(JNIEnv *env,jclass type) {(void)env;(void)type;pthread_mutex_lock(&gate);int ready=entered;pthread_mutex_unlock(&gate);return ready?JNI_TRUE:JNI_FALSE;}
JNIEXPORT void JNICALL Java_uk_co_traynor_privategallery_core_security_staging_StagingEntropyFaultHarness_unblock(JNIEnv *env,jclass type) {(void)env;(void)type;pthread_mutex_lock(&gate);released=1;pthread_cond_broadcast(&changed);pthread_mutex_unlock(&gate);}
