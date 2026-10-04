#define _GNU_SOURCE
#include <jni.h>
#include <stddef.h>
#include <sys/random.h>
#include <sys/syscall.h>
#include <unistd.h>

/* Private fixed entropy leaf only. Its caller must own and bind the Java target
 * and separately fund actual JNI lifetime BEFORE invocation. No key authority
 * is granted by this entrypoint. No retry, provider, FD, heap or ArrayElements. */
JNIEXPORT jint JNICALL
Java_uk_co_traynor_privategallery_core_security_staging_StagingEntropyInvocation_fillNative(
        JNIEnv *env, jclass type, jbyteArray target) {
    (void)type;
    jbyte entropy[32] = {0};
    jint result = 0;
    if (target == NULL || (*env)->ExceptionCheck(env)) goto cleanup;
    jsize length = (*env)->GetArrayLength(env, target);
    if ((*env)->ExceptionCheck(env) || length != 32) goto cleanup;
    long count = syscall(SYS_getrandom, entropy, sizeof entropy, GRND_NONBLOCK);
    if (count != (long)sizeof entropy) goto cleanup;
    (*env)->SetByteArrayRegion(env, target, 0, (jsize)sizeof entropy, entropy);
    if ((*env)->ExceptionCheck(env)) goto cleanup;
    result = 32;
cleanup:
    /* Wipe the known scratch BEFORE every native return, preserving any pending
     * VM exception. Never touch a Java array while that exception is pending.
     * This does not prove register/JIT/GC-copy/OEM erasure. */
    {
        volatile unsigned char *known = (volatile unsigned char *)entropy;
        for (size_t i = 0; i < sizeof entropy; i++) known[i] = 0;
    }
    return result;
}
