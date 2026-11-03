// SPDX-License-Identifier: MIT
// Actual JNI implementation, compiled with host-only function instrumentation.
// No test branch/hook is compiled into the Android library. The fake JNI table
// supplies strings/ref operations; cleanup control flow and wiping are real.
#include <assert.h>
#include <stdio.h>
void __cyg_profile_func_enter(void*,void*) __attribute__((no_instrument_function));
void __cyg_profile_func_exit(void*,void*) __attribute__((no_instrument_function));
#include "../jni.c"
static unsigned wipes,go_calls,global_deletes;
void __cyg_profile_func_enter(void *function,void *caller){(void)caller;if(function==(void*)wipe_secret)++wipes;}
void __cyg_profile_func_exit(void *function,void *caller){(void)function;(void)caller;}
struct string_value {const jchar *text;jsize length;};
static jsize get_length(JNIEnv*env,jstring s){(void)env;return ((struct string_value*)s)->length;}
static const jchar* get_chars(JNIEnv*env,jstring s,jboolean*copy){(void)env;(void)copy;return ((struct string_value*)s)->text;}
static void release_chars(JNIEnv*env,jstring s,const jchar*c){(void)env;(void)s;(void)c;}
static jboolean exception_check(JNIEnv*env){(void)env;return JNI_FALSE;}
static void exception_clear(JNIEnv*env){(void)env;assert(!"unexpected exception");}
static JNIEnv fake_env;
static jint get_env(JavaVM*vm,void**env,jint version){(void)vm;assert(version==JNI_VERSION_1_6);*env=&fake_env;return JNI_OK;}
static const struct JNIInvokeInterface vm_functions={.GetEnv=get_env};
static JavaVM fake_vm=&vm_functions;
static jint get_vm(JNIEnv*env,JavaVM**vm){(void)env;*vm=&fake_vm;return JNI_OK;}
static jclass get_class(JNIEnv*env,jobject obj){(void)env;return (jclass)obj;}
static jmethodID get_method(JNIEnv*env,jclass cls,const char*name,const char*signature){(void)env;(void)cls;assert(!strcmp(name,"protect")&&!strcmp(signature,"(I)Z"));return (jmethodID)1;}
static jobject global_ref(JNIEnv*env,jobject obj){(void)env;return obj;}
static void delete_local(JNIEnv*env,jobject obj){(void)env;(void)obj;}
static void delete_global(JNIEnv*env,jobject obj){(void)env;(void)obj;++global_deletes;}
static const struct JNINativeInterface env_functions={.GetStringLength=get_length,.GetStringChars=get_chars,.ReleaseStringChars=release_chars,.ExceptionCheck=exception_check,.ExceptionClear=exception_clear,.GetJavaVM=get_vm,.GetObjectClass=get_class,.GetMethodID=get_method,.NewGlobalRef=global_ref,.DeleteLocalRef=delete_local,.DeleteGlobalRef=delete_global};
long long goNovaReplace(long long f,long long g,long long e,char*private_key,char*peer,char*psk,char*endpoint,char*routes,int keepalive,int listen,uintptr_t protector){
 (void)f;(void)g;(void)e;(void)peer;(void)endpoint;(void)routes;(void)keepalive;(void)listen;
 assert(strlen(private_key)==64 && strlen(psk)==64);++go_calls;nova_release_protector(protector);return 37;
}
int main(void){
 fake_env=&env_functions;
 unsigned char bytes[67];memset(bytes,0xAB,sizeof(bytes));wipe_secret(bytes+1,65);assert(bytes[0]==0xAB&&bytes[66]==0xAB);for(int i=1;i<=65;++i)assert(bytes[i]==0);
 jchar key_chars[64];for(int i=0;i<64;++i)key_chars[i]='1';jchar endpoint_chars[]={'1','2','7','.','0','.','0','.','1',':','5','1','8','2','0'};jchar route_chars[]={'0','.','0','.','0','.','0','/','0'};jchar non_ascii[]={0x100};
 struct string_value key={key_chars,64},endpoint={endpoint_chars,15},routes={route_chars,9},too_long_key={key_chars,65},bad_endpoint={non_ascii,1},too_long_routes={route_chars,1041};
 struct string_value *peer_cases[]={&too_long_key,&key,&key,&key,&key};
 struct string_value *endpoint_cases[]={&endpoint,&bad_endpoint,&endpoint,&endpoint,&endpoint};
 struct string_value *route_cases[]={&routes,&routes,&too_long_routes,&routes,&routes};
 for(int i=0;i<5;++i){wipes=go_calls=global_deletes=0;
  jlong result=JNI_METHOD(nReplace)(&fake_env,NULL,1,1,1,(jstring)&key,(jstring)peer_cases[i],(jstring)&key,(jstring)endpoint_cases[i],(jstring)route_cases[i],0,0,i==3?NULL:(jobject)1);
  assert(wipes==2);assert(result==(i==4?37:INVALID));assert(go_calls==(unsigned)(i==4));assert(global_deletes==(unsigned)(i==4));
 }
 puts("JNI secret cleanup: malformed peer/endpoint/routes, missing callback and transferred-call return each wipe both buffers; bounded wipe verified");return 0;
}
