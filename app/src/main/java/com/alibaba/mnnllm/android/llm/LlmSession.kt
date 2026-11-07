// Minimal Java/JNI ABI adapter for the unmodified Apache-2.0 MNN Chat runtime.
// The package, class and native method names must match the upstream JNI exports.
package com.alibaba.mnnllm.android.llm

fun interface GenerateProgressListener {
    /** Return true to stop generation. null marks the native end-of-response. */
    fun onProgress(text: String?): Boolean
}

class LlmSession {
    external fun initNative(configPath: String, history: List<String>?, config: String, extra: String): Long
    external fun submitNative(pointer: Long, prompt: String, keepHistory: Boolean,
                              listener: GenerateProgressListener): HashMap<String, Any>
    external fun releaseNative(pointer: Long)
    companion object {
        init { System.loadLibrary("MNN"); System.loadLibrary("mnnllmapp") }
    }
}
