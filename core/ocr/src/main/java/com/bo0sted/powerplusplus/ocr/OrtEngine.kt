package com.bo0sted.powerplusplus.ocr

import ai.onnxruntime.OrtEnvironment
import android.annotation.SuppressLint
import android.system.Os
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.File

/**
 * Loads the downloaded ONNX Runtime engine and checks models against it.
 *
 * How the downloaded engine is used (verified against ORT 1.30 and bionic, AGENTS.md §4): on
 * Android, `OnnxRuntime.init()` only `System.loadLibrary`s the bundled JNI glue, whose `DT_NEEDED`
 * is `libonnxruntime.so`. Loading the engine first with `System.load(path)` registers that SONAME,
 * and the linker reuses it when the glue loads. So [load] must run before any `ai.onnxruntime` class
 * is touched. A loaded engine can't be unloaded; removing it takes effect after a restart.
 *
 * Microsoft's AAR also ships 1DS telemetry, enabled by default in native code. It is shut off three
 * ways (verified in ORT 1.30 sources, AGENTS.md §4): `ORT_DISABLE_TELEMETRY=1` before the engine
 * loads (full, irreversible suppression: no uploader, events or device id), removal of the AAR's
 * `TelemetryInitializer` provider in the app manifest (so its Java HTTP transport never exists), and
 * `OrtEnvironment.setTelemetry(false)`.
 */
object OrtEngine {
    @Volatile
    private var loadedFrom: String? = null

    val isLoaded: Boolean get() = loadedFrom != null

    // Loading downloaded native code is the point (the user chose not to bundle any OCR engine). It is
    // only ever extracted from the AAR whose pinned SHA-256 was verified (SHA-1 matches Maven Central).
    @SuppressLint("UnsafeDynamicallyLoadedCode")
    @Synchronized
    fun load(library: File) {
        if (loadedFrom != null) return
        // Android 17 (targetSdk ≥ 37) rejects loading writable files.
        library.setReadOnly()
        // Read with getenv when ORT initializes telemetry; must be set before the engine loads.
        Os.setenv("ORT_DISABLE_TELEMETRY", "1", true)
        System.load(library.absolutePath)
        OrtEnvironment.getEnvironment().setTelemetry(false)
        loadedFrom = library.absolutePath
    }

    /** What the OCR pipeline needs to know about a model pair, read from the models themselves. */
    data class ModelInfo(val recognitionClasses: Long)

    /**
     * Opens both models and checks their shapes: detection takes `[n, 3, h, w]` and gives one
     * probability map `[n, 1, h, w]`; recognition takes `[n, 3, 48, w]` and gives `[n, t, classes]`,
     * where classes is the dictionary plus CTC blank (and PaddleOCR's appended space).
     */
    fun checkModels(detection: File, recognition: File, dictionarySize: Int): ModelInfo {
        val environment = OrtEnvironment.getEnvironment()
        environment.createSession(detection.absolutePath, OrtSession.SessionOptions()).use { session ->
            val input = session.inputInfo.values.single().tensorShape()
            val output = session.outputInfo.values.first().tensorShape()
            check(input.size == 4 && input[1] == 3L) { "Detection input ${input.contentToString()}" }
            check(output.size == 4 && output[1] == 1L) { "Detection output ${output.contentToString()}" }
        }
        environment.createSession(recognition.absolutePath, OrtSession.SessionOptions()).use { session ->
            val input = session.inputInfo.values.single().tensorShape()
            val output = session.outputInfo.values.first().tensorShape()
            check(input.size == 4 && input[1] == 3L && input[2] == 48L) { "Recognition input ${input.contentToString()}" }
            check(output.size == 3) { "Recognition output ${output.contentToString()}" }
            val classes = output[2]
            check(classes == dictionarySize + 1L || classes == dictionarySize + 2L) {
                "Recognition has $classes classes for a $dictionarySize-character dictionary"
            }
            return ModelInfo(recognitionClasses = classes)
        }
    }

    private fun ai.onnxruntime.NodeInfo.tensorShape(): LongArray = (info as TensorInfo).shape
}
