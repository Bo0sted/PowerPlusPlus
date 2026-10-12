// JNI bridge between SpeechModel (Kotlin) and whisper.cpp. Kept deliberately thin: parameters are
// chosen in Kotlin; this file only marshals arrays and strings.

#include <jni.h>
#include <android/log.h>

#include <string>

#include "ggml-backend.h"
#include "whisper.h"

namespace {

constexpr const char * TAG = "PowerPP.Whisper";

void log_to_logcat(ggml_log_level level, const char * text, void *) {
    // whisper.cpp is chatty at info level (every tensor while loading); keep warnings and errors.
    if (level == GGML_LOG_LEVEL_WARN) __android_log_write(ANDROID_LOG_WARN, TAG, text);
    else if (level == GGML_LOG_LEVEL_ERROR) __android_log_write(ANDROID_LOG_ERROR, TAG, text);
}

std::string to_string(JNIEnv * env, jstring value) {
    const char * chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_bo0sted_powerplusplus_whisper_WhisperNative_loadBackends(JNIEnv * env, jobject, jstring library_dir) {
    whisper_log_set(log_to_logcat, nullptr);
    ggml_backend_load_all_from_path(to_string(env, library_dir).c_str());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_bo0sted_powerplusplus_whisper_WhisperNative_load(JNIEnv * env, jobject, jstring model_path) {
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    return reinterpret_cast<jlong>(whisper_init_from_file_with_params(to_string(env, model_path).c_str(), params));
}

// Returns UTF-8 bytes (NewStringUTF expects modified UTF-8 and aborts on 4-byte sequences), or null.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_bo0sted_powerplusplus_whisper_WhisperNative_transcribe(
        JNIEnv * env, jobject, jlong handle, jfloatArray samples, jstring language, jint threads, jint audio_ctx) {
    auto * context = reinterpret_cast<whisper_context *>(handle);
    std::string lang = to_string(env, language);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads;
    params.language = lang.c_str();
    params.translate = false;
    params.no_context = true;
    params.no_timestamps = true;
    params.single_segment = true;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_special = false;
    params.print_timestamps = false;
    params.suppress_nst = true;
    params.temperature_inc = 0.0f;
    params.audio_ctx = audio_ctx;

    jsize count = env->GetArrayLength(samples);
    jfloat * pcm = env->GetFloatArrayElements(samples, nullptr);
    int status = whisper_full(context, params, pcm, count);
    env->ReleaseFloatArrayElements(samples, pcm, JNI_ABORT);
    if (status != 0) return nullptr;

    std::string text;
    for (int i = 0; i < whisper_full_n_segments(context); i++) text += whisper_full_get_segment_text(context, i);
    jbyteArray result = env->NewByteArray(static_cast<jsize>(text.size()));
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_bo0sted_powerplusplus_whisper_WhisperNative_free(JNIEnv *, jobject, jlong handle) {
    whisper_free(reinterpret_cast<whisper_context *>(handle));
}
