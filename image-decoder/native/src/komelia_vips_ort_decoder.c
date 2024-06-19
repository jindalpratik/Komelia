#include "vips_jni.h"
#include "onnxruntime_c_api.h"
#include <pthread.h>

#ifdef _WIN32
#include <stdlib.h>
#include <string.h>
#include <wchar.h>
#include <windows.h>

wchar_t *fromUTF8(
        const char *src,
        size_t src_length,  /* = 0 */
        size_t *out_length  /* = NULL */
) {
    if (!src) { return NULL; }

    if (src_length == 0) { src_length = strlen(src); }
    int length = MultiByteToWideChar(CP_UTF8, 0, src, src_length, 0, 0);
    wchar_t *output_buffer = (wchar_t *) malloc((length + 1) * sizeof(wchar_t));
    if (output_buffer) {
        MultiByteToWideChar(CP_UTF8, 0, src, src_length, output_buffer, length);
        output_buffer[length] = L'\0';
    }
    if (out_length) { *out_length = length; }
    return output_buffer;
}
#endif

#ifdef USE_DML
#include "dml_provider_factory.h"
#endif

typedef enum {
    CUDA,
    ROCm,
    DML,
    CPU
} ExecutionProvider;

const OrtApi *g_ort = NULL;
OrtEnv *ort_env = NULL;

char *session_model_path = NULL;
OrtSessionOptions *session_options = NULL;
OrtSession *session = NULL;
OrtMemoryInfo *memory_info = NULL;
OrtRunOptions *run_options = NULL;
ExecutionProvider session_execution_provider = CPU;

pthread_mutex_t session_mutex = PTHREAD_MUTEX_INITIALIZER;


struct UpscaleCacheEntry {
    char *key;
    VipsImage *image;
};

// TODO use hashmap and store on disk
struct UpscaleCacheEntry upscaled_cache[4] = {0};
int cache_next_element_index = 0;
char *temp_dir = NULL;

VipsImage *get_cache_entry(const char *key) {
    if (key == NULL) return NULL;

    for (int i = 0; i < 4; ++i) {
        if (upscaled_cache[i].key != NULL && strcmp(upscaled_cache[i].key, key) == 0)
            return upscaled_cache[i].image;
    }

    return NULL;
}


void addToCache(VipsImage *image, const char *key, int key_length) {
    if (key == NULL) return;

    char *cache_key = malloc(sizeof(char *) * key_length);
    strcpy(cache_key, key);
    struct UpscaleCacheEntry cacheEntry = {cache_key, image};


    if (cache_next_element_index == 4) {
        struct UpscaleCacheEntry first_entry = upscaled_cache[0];
        if (first_entry.key != NULL) {
            g_object_unref(first_entry.image);
            free(first_entry.key);
        }
        upscaled_cache[0] = cacheEntry;
        cache_next_element_index = 1;
    } else {
        struct UpscaleCacheEntry next_entry = upscaled_cache[cache_next_element_index];
        if (next_entry.key != NULL) {
            g_object_unref(next_entry.image);
            free(next_entry.key);
        }

        upscaled_cache[cache_next_element_index] = cacheEntry;
        ++cache_next_element_index;
    }
}

void throw_jvm_ort_exception(JNIEnv *env, const char *message) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "io/github/snd_r/OrtException"), message);
}

JNIEXPORT void JNICALL
Java_io_github_snd_1r_VipsOnnxRuntimeDecoder_init(JNIEnv *env, jobject this, jstring provider, jstring tempDir) {
    g_ort = OrtGetApiBase()->GetApi(ORT_API_VERSION);
    if (!g_ort) {
        throw_jvm_ort_exception(env, "Failed to init ONNX Runtime engine");
        return;
    }

    OrtStatus *onnx_status = g_ort->CreateEnv(ORT_LOGGING_LEVEL_WARNING, "komelia", &ort_env);
    if (onnx_status != NULL) {
        const char *msg = g_ort->GetErrorMessage(onnx_status);
        throw_jvm_ort_exception(env, msg);
        g_ort->ReleaseStatus(onnx_status);
        return;
    }

    const char *provider_chars = (*env)->GetStringUTFChars(env, provider, 0);
    if (strcmp(provider_chars, "CUDA") == 0) session_execution_provider = CUDA;
    else if (strcmp(provider_chars, "ROCM") == 0) session_execution_provider = ROCm;
    else if (strcmp(provider_chars, "DML") == 0) session_execution_provider = DML;
    else if (strcmp(provider_chars, "CPU") == 0) session_execution_provider = CPU;
    (*env)->ReleaseStringUTFChars(env, provider, provider_chars);

    const char *temp_dir_chars = (*env)->GetStringUTFChars(env, tempDir, 0);
    temp_dir = malloc(sizeof(char *) * (*env)->GetStringLength(env, tempDir));
    strcpy(temp_dir, temp_dir_chars);
}

void hwc_to_chw(const uint8_t *input, size_t h, size_t w, float **output) {
    size_t stride = h * w;
    size_t output_count = stride * 3;
    float *output_data = (float *) malloc(output_count * sizeof(float));
    for (size_t i = 0; i != stride; ++i) {
        for (size_t c = 0; c != 3; ++c) {
            output_data[c * stride + i] = (float) input[i * 3 + c] / 255.0f;
        }
    }
    *output = output_data;
}

static void chw_to_hwc(const float *input, size_t h, size_t w, uint8_t **output) {
    size_t stride = h * w;
    uint8_t *output_data = (uint8_t *) malloc(stride * 3);
    for (size_t c = 0; c != 3; ++c) {
        size_t t = c * stride;
        for (size_t i = 0; i != stride; ++i) {
            float f = input[t + i];

            if (f < 0.f) { f = 0.f; }
            else if (f > 1.f) { f = 1.f; }

            output_data[i * 3 + c] = (uint8_t) nearbyintf(f * 255);
        }
    }
    *output = output_data;
}

VipsImage *run_inference(JNIEnv *env, VipsImage *input, const char *cache_key, int key_length) {
    VipsImage *cache_entry = get_cache_entry(cache_key);
    if (cache_entry != NULL) return cache_entry;

    int input_height = vips_image_get_height(input);
    int input_width = vips_image_get_width(input);

    VipsInterpretation interpretation = vips_image_get_interpretation(input);
    int input_bands = vips_image_get_bands(input);

    if (interpretation != VIPS_INTERPRETATION_sRGB) {
        VipsImage *transformed;
        int vips_error = vips_colourspace(input, &transformed, VIPS_INTERPRETATION_sRGB, NULL);
        if (vips_error) { return NULL; }
        g_object_unref(input);
        input = transformed;
    }

    if (input_bands == 4) {
        VipsImage *flattened;
        int vips_error = vips_flatten(input, &flattened, NULL);
        if (vips_error) { return NULL; }
        g_object_unref(input);
        input = flattened;
    }

    float *model_input_data;
    size_t model_input_ele_count = input_height * input_width * 3;
    unsigned char *input_data = (unsigned char *) vips_image_get_data(input);
    hwc_to_chw(input_data, input_height, input_width, &model_input_data);

    const int64_t input_shape[] = {1, 3, input_height, input_width};
    const size_t input_shape_len = sizeof(input_shape) / sizeof(input_shape[0]);
    const size_t model_input_len = model_input_ele_count * sizeof(float);

    OrtValue *input_tensor = NULL;
    OrtStatus *onnx_status = g_ort->CreateTensorWithDataAsOrtValue(memory_info,
                                                                   model_input_data, model_input_len,
                                                                   input_shape, input_shape_len,
                                                                   ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT,
                                                                   &input_tensor);
    if (onnx_status != NULL) {
        throw_jvm_ort_exception(env, g_ort->GetErrorMessage(onnx_status));
        g_ort->ReleaseStatus(onnx_status);
        return NULL;
    }
    int is_tensor;
    onnx_status = g_ort->IsTensor(input_tensor, &is_tensor);
    if (onnx_status != NULL) {
        throw_jvm_ort_exception(env, g_ort->GetErrorMessage(onnx_status));
        g_ort->ReleaseStatus(onnx_status);
        return NULL;
    }

    if (!is_tensor) {
        return NULL;
    }


    const char *input_names[] = {"input"};
    const char *output_names[] = {"output"};
    OrtValue *output_tensor = NULL;


    onnx_status = g_ort->Run(session, run_options, input_names, (const OrtValue *const *) &input_tensor, 1,
                             output_names, 1, &output_tensor);
    if (onnx_status != NULL) {
        throw_jvm_ort_exception(env, g_ort->GetErrorMessage(onnx_status));
        g_ort->ReleaseStatus(onnx_status);
        return NULL;
    }

    onnx_status = g_ort->IsTensor(output_tensor, &is_tensor);
    if (onnx_status != NULL) {
        throw_jvm_ort_exception(env, g_ort->GetErrorMessage(onnx_status));
        g_ort->ReleaseStatus(onnx_status);
        return NULL;
    }
    if (!is_tensor) { return NULL; }

    float *output_tensor_data = NULL;
    onnx_status = g_ort->GetTensorMutableData(output_tensor, (void **) &output_tensor_data);
    if (onnx_status != NULL) {
        throw_jvm_ort_exception(env, g_ort->GetErrorMessage(onnx_status));
        g_ort->ReleaseStatus(onnx_status);
        return NULL;
    }

    int output_height = input_height * 2;
    int output_width = input_width * 2;
    int output_size = output_height * output_width * 3;

    uint8_t *output_image_data = NULL;
    chw_to_hwc(output_tensor_data, output_height, output_width, &output_image_data);

    g_object_unref(input);
    free(model_input_data);
    VipsImage *output_image = vips_image_new_from_memory_copy(output_image_data, output_size,
                                                              output_width, output_height, 3,
                                                              VIPS_FORMAT_UCHAR);
    free(output_image_data);
    addToCache(output_image, cache_key, key_length);
    return output_image;
}

int enable_cuda(JNIEnv *env) {
    OrtCUDAProviderOptions o;
    memset(&o, 0, sizeof(o));
    o.cudnn_conv_algo_search = OrtCudnnConvAlgoSearchExhaustive;
    o.gpu_mem_limit = SIZE_MAX;
    OrtStatus *onnx_status = g_ort->SessionOptionsAppendExecutionProvider_CUDA(session_options, &o);
    if (onnx_status != NULL) {
        throw_jvm_ort_exception(env, g_ort->GetErrorMessage(onnx_status));
        g_ort->ReleaseStatus(onnx_status);
        return -1;
    }
    return 0;
}

int enable_rocm(JNIEnv *env) {
    OrtROCMProviderOptions o;
    memset(&o, 0, sizeof(o));
    o.device_id = 0;
    o.miopen_conv_exhaustive_search = 0;
    o.gpu_mem_limit = SIZE_MAX;
    o.arena_extend_strategy = 0;
    o.do_copy_in_default_stream = 1;
    o.has_user_compute_stream = 0;
    o.user_compute_stream = 0;
    o.enable_hip_graph = 0;
    o.tunable_op_enable = 0;
    o.tunable_op_tuning_enable = 0;
    o.tunable_op_max_tuning_duration_ms = 0;

    OrtStatus *onnx_status = g_ort->SessionOptionsAppendExecutionProvider_ROCM(session_options, &o);
    if (onnx_status != NULL) {
        throw_jvm_ort_exception(env, g_ort->GetErrorMessage(onnx_status));
        g_ort->ReleaseStatus(onnx_status);
        return -1;
    }
    return 0;
}

#ifdef USE_DML
int enable_dml(JNIEnv *env) {
    OrtStatus *onnx_status = OrtSessionOptionsAppendExecutionProvider_DML(session_options, 0);
    if (onnx_status != NULL) {
        throw_jvm_ort_exception(env, g_ort->GetErrorMessage(onnx_status));
        g_ort->ReleaseStatus(onnx_status);
        return -1;
    }
    return 0;
}
#endif

int init_onnx_session(JNIEnv *env,
                      jstring modelPath
) {
    if (modelPath == NULL) return 0;

    const char *model_path_chars = (*env)->GetStringUTFChars(env, modelPath, 0);
    if (session_model_path == NULL || strcmp(session_model_path, model_path_chars) != 0) {
        free(session_model_path);
        g_ort->ReleaseSessionOptions(session_options);
        g_ort->ReleaseSession(session);
        g_ort->ReleaseMemoryInfo(memory_info);

        jsize model_path_char_length = (*env)->GetStringLength(env, modelPath);
        session_model_path = malloc(sizeof(char) * model_path_char_length);
        strcpy(session_model_path, model_path_chars);

        OrtStatus *onnx_status = g_ort->CreateSessionOptions(&session_options);
        if (onnx_status != NULL) {
            throw_jvm_ort_exception(env, g_ort->GetErrorMessage(onnx_status));
            g_ort->ReleaseStatus(onnx_status);
            return -1;
        }

        onnx_status = g_ort->CreateSessionOptions(&session_options);
        if (onnx_status != NULL) {
            throw_jvm_ort_exception(env, g_ort->GetErrorMessage(onnx_status));
            g_ort->ReleaseStatus(onnx_status);
            return -1;
        }

        onnx_status = g_ort->SetSessionGraphOptimizationLevel(session_options, ORT_ENABLE_BASIC);
        if (onnx_status != NULL) {
            throw_jvm_ort_exception(env, g_ort->GetErrorMessage(onnx_status));
            g_ort->ReleaseStatus(onnx_status);
            return -1;
        }

        int provider_init_error = 0;
        switch (session_execution_provider) {
            case CUDA:
                provider_init_error = enable_cuda(env);
                break;
            case ROCm:
                provider_init_error = enable_rocm(env);
                break;
            case DML:
#ifdef USE_DML
                provider_init_error = enable_dml(env);
#endif
                break;
            case CPU:
                break;
        }
        if (provider_init_error) {
            return -1;
        }

#ifdef _WIN32
        size_t *wide_length = NULL;
        wchar_t *wide = fromUTF8(model_path_chars, model_path_char_length, wide_length);
        onnx_status = g_ort->CreateSession(ort_env, wide, session_options, &session);
        free(wide);
#else
        onnx_status = g_ort->CreateSession(ort_env, session_model_path, session_options, &session);
#endif
        (*env)->ReleaseStringUTFChars(env, modelPath, model_path_chars);
        if (onnx_status != NULL) {
            throw_jvm_ort_exception(env, g_ort->GetErrorMessage(onnx_status));
            g_ort->ReleaseStatus(onnx_status);
            return -1;
        }

        onnx_status = g_ort->CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &memory_info);
        if (onnx_status != NULL) {
            throw_jvm_ort_exception(env, g_ort->GetErrorMessage(onnx_status));
            g_ort->ReleaseStatus(onnx_status);
            return -1;
        }

        onnx_status = g_ort->CreateRunOptions(&run_options);
        if (onnx_status != NULL) {
            throw_jvm_ort_exception(env, g_ort->GetErrorMessage(onnx_status));
            g_ort->ReleaseStatus(onnx_status);
            return -1;
        }

        onnx_status = g_ort->AddRunConfigEntry(run_options, "kOrtRunOptionsConfigEnableMemoryArenaShrinkage", "cpu:0;gpu:0");
        if (onnx_status != NULL) {
            throw_jvm_ort_exception(env, g_ort->GetErrorMessage(onnx_status));
            g_ort->ReleaseStatus(onnx_status);
            return -1;
        }
    } else {
        (*env)->ReleaseStringUTFChars(env, modelPath, model_path_chars);
    }

    return 0;
}

JNIEXPORT jobject JNICALL Java_io_github_snd_1r_VipsOnnxRuntimeDecoder_decodeAndResize(
        JNIEnv *env,
        jobject this,
        jbyteArray encoded,
        jstring modelPath,
        jstring cacheKey,
        jint scaleWidth,
        jint scaleHeight,
        jboolean crop
) {

    jsize inputLen = (*env)->GetArrayLength(env, encoded);
    jbyte *inputBytes = (*env)->GetByteArrayElements(env, encoded, JNI_FALSE);

    VipsImage *input_image = vips_image_new_from_buffer((unsigned char *) inputBytes, inputLen, "", NULL);
    if (!input_image) {
        throw_jvm_vips_exception(env, vips_error_buffer());
        vips_error_clear();
        return NULL;
    }

    int input_width = vips_image_get_width(input_image);
    int input_height = vips_image_get_height(input_image);
    if (input_width == scaleWidth && input_height == scaleHeight) {
        jobject jvm_image = komelia_vips_image_to_jvm(env, input_image);
        (*env)->ReleaseByteArrayElements(env, encoded, inputBytes, JNI_ABORT);
        g_object_unref(input_image);
        return jvm_image;
    }

    VipsImage *output_image = NULL;
    if (vips_image_get_width(input_image) >= scaleWidth ||
        vips_image_get_height(input_image) >= scaleHeight) {
        if (crop) {
            vips_thumbnail_image(input_image, &output_image, scaleWidth,
                                 "height", scaleHeight,
                                 "crop", VIPS_INTERESTING_ENTROPY,
                                 NULL
            );
        } else {
            vips_thumbnail_image(input_image, &output_image, scaleWidth, "height", scaleHeight, NULL);
        }

        if (!output_image) {
            g_object_unref(input_image);
            throw_jvm_vips_exception(env, vips_error_buffer());
            vips_error_clear();
            (*env)->ReleaseByteArrayElements(env, encoded, inputBytes, JNI_ABORT);
            return NULL;
        } else {
            jobject jvm_image = komelia_vips_image_to_jvm(env, output_image);
            (*env)->ReleaseByteArrayElements(env, encoded, inputBytes, JNI_ABORT);
            g_object_unref(output_image);
            return jvm_image;
        }

    } else {
        pthread_mutex_lock(&session_mutex);
        int initError = init_onnx_session(env, modelPath);
        if (initError) { return NULL; }

        if (cacheKey != NULL) {
            const char *cache_key_chars = (*env)->GetStringUTFChars(env, cacheKey, 0);
            int cache_key_length = (*env)->GetStringLength(env, cacheKey);
            output_image = run_inference(env, input_image, cache_key_chars, cache_key_length);
            (*env)->ReleaseStringUTFChars(env, modelPath, cache_key_chars);
        } else {
            output_image = run_inference(env, input_image, NULL, 0);
        }

        pthread_mutex_unlock(&session_mutex);

        if (!output_image) {
            g_object_unref(input_image);
            (*env)->ReleaseByteArrayElements(env, encoded, inputBytes, JNI_ABORT);
            return NULL;
        }
    }

    int output_width = vips_image_get_width(input_image);
    int output_height = vips_image_get_height(input_image);
    if (output_width != scaleWidth || output_height != scaleHeight) {
        VipsImage *rescaled_image = NULL;
        vips_thumbnail_image(output_image, &rescaled_image, scaleWidth,
                             "height", scaleHeight,
                             "crop", VIPS_INTERESTING_ENTROPY,
                             NULL
        );

        if (!rescaled_image) {
            g_object_unref(output_image);
            throw_jvm_vips_exception(env, vips_error_buffer());
            vips_error_clear();
            return NULL;
        }
        g_object_unref(output_image);
        output_image = rescaled_image;
    }

    jobject jvm_image = komelia_vips_image_to_jvm(env, output_image);
    (*env)->ReleaseByteArrayElements(env, encoded, inputBytes, JNI_ABORT);

    g_object_unref(output_image);

    return jvm_image;
}