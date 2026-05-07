#include <jni.h>
#include <string>
#include <vector>

extern int gguf_split_tool_main(int argc, const char ** argv);

extern "C"
JNIEXPORT jint JNICALL
Java_com_santiya_localaihub_distributedruntime_internal_GgufShardTool_splitModel(
        JNIEnv * env,
        jobject /*unused*/,
        jstring jinput_path,
        jstring joutput_path,
        jint jmax_tensors,
        jstring jmax_size_arg,
        jboolean jno_tensor_first_split,
        jboolean jdry_run) {
    const auto * input_path = env->GetStringUTFChars(jinput_path, 0);
    const auto * output_path = env->GetStringUTFChars(joutput_path, 0);

    std::vector<std::string> owned_args;
    owned_args.emplace_back("gguf-split");
    owned_args.emplace_back("--split");

    if (jmax_size_arg != nullptr) {
        const auto * max_size_arg = env->GetStringUTFChars(jmax_size_arg, 0);
        owned_args.emplace_back("--split-max-size");
        owned_args.emplace_back(max_size_arg);
        env->ReleaseStringUTFChars(jmax_size_arg, max_size_arg);
    } else {
        owned_args.emplace_back("--split-max-tensors");
        owned_args.emplace_back(std::to_string(jmax_tensors > 0 ? jmax_tensors : 128));
    }

    if (jno_tensor_first_split == JNI_TRUE) {
        owned_args.emplace_back("--no-tensor-first-split");
    }
    if (jdry_run == JNI_TRUE) {
        owned_args.emplace_back("--dry-run");
    }

    owned_args.emplace_back(input_path);
    owned_args.emplace_back(output_path);

    std::vector<const char *> argv;
    argv.reserve(owned_args.size());
    for (const auto & arg : owned_args) {
        argv.push_back(arg.c_str());
    }

    const int result = gguf_split_tool_main(static_cast<int>(argv.size()), argv.data());

    env->ReleaseStringUTFChars(jinput_path, input_path);
    env->ReleaseStringUTFChars(joutput_path, output_path);
    return result;
}
