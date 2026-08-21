#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

#include <net.h>

namespace {

constexpr const char* kLogTag = "AlprNcnn";

struct NcnnContext {
    ncnn::Net net;
    int input_index = -1;
    int output_index = -1;
    int width = 0;
    int height = 0;
    int channels = 0;
    std::vector<int> output_shape;
};

void throw_java(JNIEnv* env, const char* class_name, const std::string& message) {
    jclass type = env->FindClass(class_name);
    if (type != nullptr) {
        env->ThrowNew(type, message.c_str());
        env->DeleteLocalRef(type);
    }
}

std::string from_jstring(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

NcnnContext* context_from(jlong handle) {
    return reinterpret_cast<NcnnContext*>(static_cast<intptr_t>(handle));
}

std::vector<int> output_shape(const ncnn::Mat& output) {
    if (output.dims == 2) return {1, output.h, output.w};
    return {};
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_alpr_1v1_inference_NcnnBackend_nativeCreate(
        JNIEnv* env,
        jclass,
        jstring param_path,
        jstring model_path,
        jint threads,
        jint width,
        jint height,
        jint channels) {
    const std::string param = from_jstring(env, param_path);
    const std::string model = from_jstring(env, model_path);
    if (param.empty() || model.empty()) {
        throw_java(env, "java/lang/IllegalArgumentException", "Brak plików param/bin modelu NCNN");
        return 0;
    }
    if (width <= 0 || height <= 0 || channels <= 0) {
        throw_java(env, "java/lang/IllegalArgumentException", "Nieprawidłowy kształt wejścia NCNN");
        return 0;
    }

    NcnnContext* context = new NcnnContext();
    context->width = width;
    context->height = height;
    context->channels = channels;
    context->net.opt.num_threads = std::max(1, static_cast<int>(threads));
    context->net.opt.use_vulkan_compute = false;
    context->net.opt.use_fp16_packed = false;
    context->net.opt.use_fp16_storage = false;
    context->net.opt.use_fp16_arithmetic = false;
    context->net.opt.use_bf16_storage = false;

    int result = context->net.load_param(param.c_str());
    if (result == 0) result = context->net.load_model(model.c_str());
    if (result != 0) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Model load failed: %d", result);
        delete context;
        throw_java(env, "java/lang/IllegalStateException", "Nie można otworzyć modelu NCNN");
        return 0;
    }

    const std::vector<int>& inputs = context->net.input_indexes();
    const std::vector<int>& outputs = context->net.output_indexes();
    if (inputs.size() != 1 || outputs.size() != 1) {
        delete context;
        throw_java(
                env,
                "java/lang/IllegalArgumentException",
                "Backend NCNN v1 wymaga dokładnie jednego wejścia i jednego wyjścia");
        return 0;
    }
    context->input_index = inputs[0];
    context->output_index = outputs[0];
    return static_cast<jlong>(reinterpret_cast<intptr_t>(context));
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_alpr_1v1_inference_NcnnBackend_nativeRun(
        JNIEnv* env,
        jclass,
        jlong handle,
        jobject input_buffer) {
    NcnnContext* context = context_from(handle);
    if (context == nullptr) {
        throw_java(env, "java/lang/IllegalStateException", "Backend NCNN został zamknięty");
        return nullptr;
    }
    void* input_data = env->GetDirectBufferAddress(input_buffer);
    const jlong input_capacity = env->GetDirectBufferCapacity(input_buffer);
    const jlong expected_capacity = static_cast<jlong>(context->width)
            * context->height * context->channels * sizeof(float);
    if (input_data == nullptr || input_capacity != expected_capacity) {
        throw_java(
                env,
                "java/lang/IllegalArgumentException",
                "Bufor wejściowy NCNN nie odpowiada tensorowi modelu");
        return nullptr;
    }

    ncnn::Mat input(
            context->width,
            context->height,
            context->channels,
            input_data,
            sizeof(float));
    ncnn::Extractor extractor = context->net.create_extractor();
    int result = extractor.input(context->input_index, input);
    ncnn::Mat output;
    if (result == 0) result = extractor.extract(context->output_index, output);
    if (result != 0 || output.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Inference failed: %d", result);
        throw_java(env, "java/lang/IllegalStateException", "Błąd inferencji NCNN");
        return nullptr;
    }
    if (output.elemsize != sizeof(float) || output.elempack != 1) {
        throw_java(
                env,
                "java/lang/IllegalStateException",
                "NCNN zwrócił nieobsługiwany typ lub packing tensora");
        return nullptr;
    }

    context->output_shape = output_shape(output);
    if (context->output_shape.empty()) {
        throw_java(
                env,
                "java/lang/IllegalStateException",
                "Backend NCNN v1 obsługuje wyłącznie dwuwymiarowe wyjście YOLO");
        return nullptr;
    }
    const size_t element_count = output.total();
    if (element_count > static_cast<size_t>(INT32_MAX)) {
        throw_java(env, "java/lang/IllegalStateException", "Wyjście NCNN jest zbyt duże");
        return nullptr;
    }
    jfloatArray result_array = env->NewFloatArray(static_cast<jsize>(element_count));
    if (result_array == nullptr) return nullptr;
    env->SetFloatArrayRegion(
            result_array,
            0,
            static_cast<jsize>(element_count),
            static_cast<const float*>(output.data));
    return result_array;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_example_alpr_1v1_inference_NcnnBackend_nativeOutputShape(
        JNIEnv* env,
        jclass,
        jlong handle) {
    NcnnContext* context = context_from(handle);
    if (context == nullptr || context->output_shape.empty()) {
        throw_java(env, "java/lang/IllegalStateException", "Brak kształtu wyjścia NCNN");
        return nullptr;
    }
    jintArray result = env->NewIntArray(static_cast<jsize>(context->output_shape.size()));
    if (result == nullptr) return nullptr;
    env->SetIntArrayRegion(
            result,
            0,
            static_cast<jsize>(context->output_shape.size()),
            context->output_shape.data());
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_alpr_1v1_inference_NcnnBackend_nativeClose(
        JNIEnv*,
        jclass,
        jlong handle) {
    delete context_from(handle);
}
