// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Private Gallery contributors.
// Independent JNI namespace: never implements original GoBackend exports.
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include "_cgo_export.h"
#define JNI_METHOD(name) Java_uk_co_traynor_privategallery_core_vpn_continuity_NativeVpnFence_##name
#define INVALID -1
#define FAILED -4
struct protector { JavaVM *vm; jobject object; jmethodID method; };
// Volatile byte stores are observable C operations and cannot be elided even
// after the buffer's last ordinary use. noinline keeps the optimized Android
// implementation directly inspectable; this wipes only our C stack copies.
static __attribute__((noinline)) void wipe_secret(void *buffer, size_t length) {
    volatile unsigned char *bytes = (volatile unsigned char *)buffer;
    while (length--) *bytes++ = 0;
}

static int clear_exception(JNIEnv *env) {
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); return 1; }
    return 0;
}
// Strict bounded ASCII avoids modified-UTF8/NUL ambiguities and any allocation
// proportional to an untrusted string. Inputs are numeric/key configuration.
static int copy_ascii(JNIEnv *env, jstring value, char *out, size_t capacity) {
    if (!value) return 0;
    jsize length = (*env)->GetStringLength(env, value);
    if (clear_exception(env) || length < 0 || (size_t)length >= capacity) return 0;
    const jchar *chars = (*env)->GetStringChars(env, value, NULL);
    if (clear_exception(env) || !chars) return 0;
    int ok = 1;
    for (jsize i = 0; i < length; ++i) {
        if (!chars[i] || chars[i] > 127) { ok = 0; break; }
        out[i] = (char)chars[i];
    }
    out[length] = 0;
    (*env)->ReleaseStringChars(env, value, chars);
    return !clear_exception(env) && ok;
}
static JNIEnv *attach(struct protector *p, int *attached) {
    JNIEnv *env = NULL;
    *attached = 0;
    jint result = (*p->vm)->GetEnv(p->vm, (void **)&env, JNI_VERSION_1_6);
    if (result == JNI_OK) return env;
    if (result != JNI_EDETACHED || (*p->vm)->AttachCurrentThread(p->vm, &env, NULL) != JNI_OK) return NULL;
    *attached = 1;
    return env;
}
int nova_protect(uintptr_t handle, int fd) {
    struct protector *p = (struct protector *)handle;
    int attached;
    JNIEnv *env = attach(p, &attached);
    if (!env) return 0;
    jboolean protected = (*env)->CallBooleanMethod(env, p->object, p->method, (jint)fd);
    if (clear_exception(env)) protected = JNI_FALSE;
    if (attached) (*p->vm)->DetachCurrentThread(p->vm);
    return protected == JNI_TRUE;
}
void nova_release_protector(uintptr_t handle) {
    struct protector *p = (struct protector *)handle;
    int attached;
    JNIEnv *env = attach(p, &attached);
    // At JVM shutdown attach may fail; retain the reference/storage rather than
    // free memory that might still be reachable. No callback runs after retire.
    if (!env) return;
    (*env)->DeleteGlobalRef(env, p->object);
    clear_exception(env);
    if (attached) (*p->vm)->DetachCurrentThread(p->vm);
    free(p);
}
static struct protector *new_protector(JNIEnv *env, jobject callback) {
    if (!callback) return NULL;
    struct protector *p = calloc(1, sizeof(*p));
    if (!p) return NULL;
    if ((*env)->GetJavaVM(env, &p->vm) != JNI_OK) { free(p); return NULL; }
    jclass type = (*env)->GetObjectClass(env, callback);
    if (clear_exception(env) || !type) { free(p); return NULL; }
    p->method = (*env)->GetMethodID(env, type, "protect", "(I)Z");
    (*env)->DeleteLocalRef(env, type);
    if (clear_exception(env) || !p->method) { free(p); return NULL; }
    p->object = (*env)->NewGlobalRef(env, callback);
    if (clear_exception(env) || !p->object) { free(p); return NULL; }
    return p;
}
JNIEXPORT jlong JNICALL JNI_METHOD(nCreate)(JNIEnv *env, jclass type, jint borrowedDuplicate, jint mtu, jstring name) {
    (void)type;
    // Java retains its temporary duplicate throughout this call. Only the new
    // F_DUPFD_CLOEXEC descriptor is transferred to Go; consume it on every path.
    int owned = fcntl(borrowedDuplicate, F_DUPFD_CLOEXEC, 0);
    if (owned < 0) return INVALID;
    char n[16];
    if (!copy_ascii(env, name, n, sizeof(n))) { close(owned); return INVALID; }
    return goNovaCreate(owned, mtu, n);
}
JNIEXPORT jlong JNICALL JNI_METHOD(nReplace)(JNIEnv *env, jclass type, jlong f, jlong g, jlong e, jstring privateKey, jstring peerKey, jstring psk, jstring endpoint, jstring routes, jint keepalive, jint listen, jobject callback) {
    (void)type;
    char priv[65] = {0}, preshared[65] = {0};
    char peer[65], end[65], allowed[1041];
    jlong result = INVALID;
    struct protector *p = NULL;
    if (!copy_ascii(env, privateKey, priv, sizeof(priv)) ||
        !copy_ascii(env, peerKey, peer, sizeof(peer)) ||
        !copy_ascii(env, psk, preshared, sizeof(preshared)) ||
        !copy_ascii(env, endpoint, end, sizeof(end)) ||
        !copy_ascii(env, routes, allowed, sizeof(allowed))) goto cleanup;
    p = new_protector(env, callback);
    if (!p) goto cleanup;
    // Go consumes the protector on every result; C must not release it again.
    result = goNovaReplace(f,g,e,priv,peer,preshared,end,allowed,keepalive,listen,(uintptr_t)p);
cleanup:
    wipe_secret(priv, sizeof(priv));
    wipe_secret(preshared, sizeof(preshared));
    return result;
}
JNIEXPORT jlong JNICALL JNI_METHOD(nRegister)(JNIEnv *env,jclass type,jlong f,jlong t,jlong e,jint protocol,jstring source,jstring destination) {
    (void)type; char s[65],d[65];
    if (!copy_ascii(env,source,s,sizeof(s)) || !copy_ascii(env,destination,d,sizeof(d))) return INVALID;
    return goNovaRegister(f,t,e,protocol,s,d);
}
#define SIMPLE1(name,go) JNIEXPORT jlong JNICALL JNI_METHOD(name)(JNIEnv *env,jclass type,jlong f){(void)env;(void)type;return go(f);}
#define SIMPLE2(name,go) JNIEXPORT jlong JNICALL JNI_METHOD(name)(JNIEnv *env,jclass type,jlong f,jlong t){(void)env;(void)type;return go(f,t);}
#define SIMPLE3(name,go) JNIEXPORT jlong JNICALL JNI_METHOD(name)(JNIEnv *env,jclass type,jlong f,jlong t,jlong e){(void)env;(void)type;return go(f,t,e);}
#define SIMPLE4(name,go) JNIEXPORT jlong JNICALL JNI_METHOD(name)(JNIEnv *env,jclass type,jlong f,jlong t,jlong e,jlong s){(void)env;(void)type;return go(f,t,e,s);}
SIMPLE1(nEpoch,goNovaEpoch)
SIMPLE1(nInvalidate,goNovaInvalidate)
SIMPLE2(nInvalidateTransport,goNovaInvalidateTransport)
SIMPLE2(nRetire,goNovaRetire)
SIMPLE3(nRetireCurrent,goNovaRetireCurrent)
SIMPLE3(nBeginProbe,goNovaBegin)
SIMPLE3(nSequence,goNovaSequence)
SIMPLE4(nPublish,goNovaPublish)
SIMPLE4(nRemove,goNovaRemove)
JNIEXPORT jlong JNICALL JNI_METHOD(nRelease)(JNIEnv *env,jclass type,jlong f,jboolean proof){(void)env;(void)type;return goNovaRelease(f,proof==JNI_TRUE);}
JNIEXPORT jlongArray JNICALL JNI_METHOD(nStatistics)(JNIEnv *env,jclass type,jlong f,jlong t,jlong e){
    (void)type; long long fields[4]={0}; jlong values[5]={0};
    values[0]=goNovaStatistics(f,t,e,fields);
    for(int i=0;i<4;++i) values[i+1]=fields[i];
    jlongArray result=(*env)->NewLongArray(env,5);
    if(clear_exception(env) || !result) return NULL;
    (*env)->SetLongArrayRegion(env,result,0,5,values);
    if(clear_exception(env)) {(*env)->DeleteLocalRef(env,result);return NULL;}
    return result;
}
