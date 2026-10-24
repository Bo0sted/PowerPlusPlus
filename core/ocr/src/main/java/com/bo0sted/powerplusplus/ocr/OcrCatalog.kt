package com.bo0sted.powerplusplus.ocr

import com.bo0sted.powerplusplus.download.Downloadable
import com.bo0sted.powerplusplus.download.RemoteFile

/**
 * Everything OCR downloads, pinned to exact files from their official publishers. A file is
 * accepted only if its size and SHA-256 match, so a mirror can serve it as long as the bytes are
 * identical. Verified 2026-10-08 (the engine 2026-10-24); see AGENTS.md §4.
 */
object OcrCatalog {

    /** ONNX Runtime from Microsoft's official Maven Central artifact (its SHA-1 matches Maven's). */
    val ENGINE = OcrComponent(
        id = "engine-onnxruntime",
        kind = OcrComponent.Kind.Engine,
        version = "1.31.0",
        files = listOf(
            RemoteFile(
                name = "onnxruntime-android.aar",
                sizeBytes = 55_138_854,
                sha256 = "72683787a80d0608a3121b967d8e1239aef48286826661c953211626690540a7",
                urls = listOf(
                    "https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.31.0/onnxruntime-android-1.31.0.aar",
                ),
            ),
        ),
    )

    /**
     * PP-OCRv6 Medium (Apache-2.0) from the official PaddlePaddle organisations on Hugging Face,
     * pinned to commits, with ModelScope (byte-identical) as the mirror.
     */
    val PP_OCR_V6_MEDIUM = OcrComponent(
        id = "model-ppocrv6-medium",
        kind = OcrComponent.Kind.Model,
        version = "PP-OCRv6 Medium",
        files = listOf(
            RemoteFile(
                name = DETECTION_MODEL,
                sizeBytes = 62_032_837,
                sha256 = "eb13b44b25bb36f89528b68720af8a61d9cf381176107f465db1757b65d086e1",
                urls = listOf(
                    "https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_det_onnx/resolve/61323801669c338b7891481ec7bac61ce31b576a/inference.onnx",
                    "https://www.modelscope.cn/models/PaddlePaddle/PP-OCRv6_medium_det_onnx/resolve/8cb026ecab7a28b7f7e479dccd7f93ebe3ff47c1/inference.onnx",
                ),
            ),
            RemoteFile(
                name = RECOGNITION_MODEL,
                sizeBytes = 76_554_979,
                sha256 = "9c09abf0957f7968c7586464b7397b84ad2387a0497a351af40e9acc71b673ba",
                urls = listOf(
                    "https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_rec_onnx/resolve/50c7eacafc52fa7bcf4194e8cd08e46f8558504b/inference.onnx",
                    "https://www.modelscope.cn/models/PaddlePaddle/PP-OCRv6_medium_rec_onnx/resolve/4b0e4965d5d68d048ec6ff9e1fdaaaaed0b7abd5/inference.onnx",
                ),
            ),
            RemoteFile(
                name = RECOGNITION_CONFIG,
                sizeBytes = 150_580,
                sha256 = "991b700facf5b50a7de193468207d5f4255b538dde0d312ae3b7c7a9b6873129",
                urls = listOf(
                    "https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_rec_onnx/resolve/50c7eacafc52fa7bcf4194e8cd08e46f8558504b/inference.yml",
                    "https://www.modelscope.cn/models/PaddlePaddle/PP-OCRv6_medium_rec_onnx/resolve/4b0e4965d5d68d048ec6ff9e1fdaaaaed0b7abd5/inference.yml",
                ),
            ),
        ),
    )

    val ALL = listOf(ENGINE, PP_OCR_V6_MEDIUM)

    const val DETECTION_MODEL = "det.onnx"
    const val RECOGNITION_MODEL = "rec.onnx"
    const val RECOGNITION_CONFIG = "rec.yml"
}

data class OcrComponent(
    override val id: String,
    val kind: Kind,
    override val version: String,
    override val files: List<RemoteFile>,
) : Downloadable {
    enum class Kind { Engine, Model }
}
