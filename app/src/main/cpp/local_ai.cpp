#include <jni.h>
#include <algorithm>
#include <cstring>
#include <string>
#include <cstdlib>
#include "stable-diffusion.h"
#include "pg_fd_stream.h"
#include "model_io/safetensors_io.h"
#include "core/util.h"

namespace {
struct Progress { JNIEnv* env; jobject callback; jmethodID method; };
void quiet(enum sd_log_level_t, const char*, void*) {}
void progress(int step, int steps, float, void* data) {
    auto* p = static_cast<Progress*>(data);
    p->env->CallVoidMethod(p->callback, p->method, step, steps);
    if (p->env->ExceptionCheck()) p->env->ExceptionClear();
}
void wipe(void* data, size_t size) {
    auto* p = static_cast<volatile unsigned char*>(data);
    while (size--) *p++ = 0;
}
}
extern "C" JNIEXPORT jboolean JNICALL
Java_uk_co_traynor_privategallery_core_editor_local_LocalNative_generate(
    JNIEnv* env, jobject, jint modelFd, jobject pixels, jint width, jint height,
    jstring promptValue, jboolean masked, jint threads, jobject callback) {
    if (width < 64 || height < 64 || width > 768 || height > 768 || width % 64 || height % 64) return false;
    const size_t count = static_cast<size_t>(width) * height;
    auto* data = static_cast<uint8_t*>(env->GetDirectBufferAddress(pixels));
    if (!data || env->GetDirectBufferCapacity(pixels) != static_cast<jlong>(count * 7)) return false;
    const char* promptChars = env->GetStringUTFChars(promptValue, nullptr);
    if (!promptChars) return false;
    std::string prompt(promptChars);
    env->ReleaseStringUTFChars(promptValue, promptChars);
    if (prompt.size() > 16000) { wipe(prompt.data(), prompt.size()); return false; }
    sd_ctx_t* context = nullptr;
    sd_image_t* images = nullptr;
    int imageCount = 0;
    bool success = false;
    sd_set_log_callback(quiet, nullptr); // Never log prompts, tensors, filenames or model internals.
    auto method = env->GetMethodID(env->GetObjectClass(callback), "onStep", "(II)V");
    if (!method) { env->ExceptionClear(); wipe(prompt.data(), prompt.size()); return false; }
    Progress report{env, callback, method};
    sd_set_progress_callback(progress, &report);
    try {
        pg_model_fd = modelFd;
        std::string path = "pg://model";
        sd_ctx_params_t options;
        sd_ctx_params_init(&options);
        options.model_path = path.c_str();
        options.rng_type = STD_DEFAULT_RNG;
        options.sampler_rng_type = STD_DEFAULT_RNG;
        options.backend = "cpu";
        options.params_backend = "disk";
        options.enable_mmap = true;
        options.n_threads = std::clamp(static_cast<int>(threads), 1, 4);
        options.conditioning_cache_size = 0;
        options.flash_attn = true;
        options.diffusion_flash_attn = true;
        options.disable_prefetch = true;
        context = new_sd_ctx(&options);
        if (context) {
            sd_img_gen_params_t params;
            sd_img_gen_params_init(&params);
            params.prompt = prompt.c_str();
            params.negative_prompt = "";
            params.width = width; params.height = height;
            params.init_image = {static_cast<uint32_t>(width), static_cast<uint32_t>(height), 3, data};
            if (masked) params.mask_image = {static_cast<uint32_t>(width), static_cast<uint32_t>(height), 1, data + count * 3};
            params.strength = masked ? 0.85f : 0.65f;
            params.batch_count = 1;
            params.seed = -1;
            params.sample_params.sample_steps = 20;
            params.sample_params.guidance.txt_cfg = 7.0f;
            params.sample_params.sample_method = sd_get_default_sample_method(context);
            params.sample_params.scheduler = sd_get_default_scheduler(context, params.sample_params.sample_method);
            params.vae_tiling_params.enabled = true;
            params.vae_tiling_params.tile_size_w = 256;
            params.vae_tiling_params.tile_size_h = 256;
            if (generate_image(context, &params, &images, &imageCount) && imageCount == 1 && images &&
                images[0].data && images[0].width == static_cast<uint32_t>(width) &&
                images[0].height == static_cast<uint32_t>(height) && images[0].channel == 3) {
                memcpy(data + count * 4, images[0].data, count * 3);
                // Preserve unselected pixels exactly at the bounded input resolution.
                if (masked) for (size_t i = 0; i < count; ++i)
                    if (data[count * 3 + i] < 128) memcpy(data + count * 4 + i * 3, data + i * 3, 3);
                success = true;
            }
        }
    } catch (...) { success = false; }
    if (images) {
        for (int i = 0; i < imageCount; ++i) {
            if (images[i].data) wipe(images[i].data, static_cast<size_t>(images[i].width) * images[i].height * images[i].channel);
            free(images[i].data);
        }
        free(images);
    }
    if (context) free_sd_ctx(context);
    pg_model_fd = -1;
    sd_set_progress_callback(nullptr, nullptr);
    wipe(prompt.data(), prompt.size());
    return success;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_uk_co_traynor_privategallery_core_editor_local_LocalNative_canReadModel(JNIEnv*, jobject, jint fd) {
    sd_set_log_callback(quiet, nullptr);
    pg_model_fd = fd;
    bool valid = false;
    try {
        if (is_safetensors_file("pg://model")) {
            auto mapped = MmapWrapper::create("pg://model", false);
            PgInputStream first("pg://model"), second("pg://model");
            if (mapped && mapped->size() >= 16 && first.is_open() && second.is_open()) {
                char head[8]{}, tail[8]{};
                first.read(head, 8);
                second.seekg(-8, std::ios::end); second.read(tail, 8);
                valid = first && second && !memcmp(head, mapped->data(), 8) &&
                    !memcmp(tail, mapped->data() + mapped->size() - 8, 8) && first.tellg() == 8;
            }
        }
    } catch (...) { valid = false; }
    pg_model_fd = -1;
    return valid;
}
