package com.bo0sted.powerplusplus.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.core.graphics.scale
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/** One recognised line of text and its box in the recognised image's pixels. */
data class OcrLine(val text: String, val score: Float, val box: TextBox)

/**
 * PP-OCR text detection and recognition on ONNX Runtime, reproducing PaddleX's general OCR pipeline
 * (`paddlex/configs/pipelines/OCR.yaml` and the predictors it uses, checked 2026-10-08) without its
 * optional document-unwarping and text-line-orientation models, which screen text doesn't need.
 *
 * Both models take BGR images: PaddleX reads images as BGR and the models' `DecodeImage` keeps BGR.
 * Not thread-safe; call from one thread at a time, off the main thread. [close] releases the models.
 */
class PpOcr private constructor(
    private val detection: OrtSession,
    private val recognition: OrtSession,
    private val decoder: CtcDecoder,
) : AutoCloseable {

    private val environment = OrtEnvironment.getEnvironment()

    /** Detects and recognises the text in [image], a software bitmap. Lines come in detection order. */
    fun recognize(image: Bitmap): List<OcrLine> {
        val started = SystemClock.elapsedRealtime()
        val boxes = detect(image)
        val detected = SystemClock.elapsedRealtime()
        val lines = recognizeLines(image, boxes)
        Log.d(TAG, "${image.width}×${image.height}: ${boxes.size} boxes in ${detected - started} ms, " +
            "recognised in ${SystemClock.elapsedRealtime() - detected} ms")
        return lines
    }

    private fun detect(image: Bitmap): List<TextBox> {
        val (width, height) = detectionSize(image.width, image.height)
        val resized = if (width == image.width && height == image.height) image else image.scale(width, height)
        val input = FloatArray(3 * width * height)
        val pixels = IntArray(width * height).also { resized.getPixels(it, 0, width, 0, 0, width, height) }
        if (resized !== image) resized.recycle()
        val plane = width * height
        for (i in pixels.indices) {
            val pixel = pixels[i]
            // PaddleX's NormalizeImage applies mean/std index by index to the BGR channels.
            input[i] = ((pixel and 0xFF) / 255f - MEAN[0]) / STD[0]
            input[plane + i] = ((pixel shr 8 and 0xFF) / 255f - MEAN[1]) / STD[1]
            input[2 * plane + i] = ((pixel shr 16 and 0xFF) / 255f - MEAN[2]) / STD[2]
        }
        val map = infer(detection, input, longArrayOf(1, 3, height.toLong(), width.toLong())).first
        return DbPostProcess.boxes(
            map, width, height, image.width, image.height,
            thresh = DET_THRESH, boxThresh = DET_BOX_THRESH, unclipRatio = DET_UNCLIP_RATIO, maxCandidates = DET_MAX_CANDIDATES,
        )
    }

    private fun recognizeLines(image: Bitmap, boxes: List<TextBox>): List<OcrLine> {
        val crops = boxes.filter { it.width > 0 && it.height > 0 }.map { box -> box to crop(image, box) }
        // PaddleX recognises lines sorted by aspect ratio, in batches, so padding stays small.
        val order = crops.indices.sortedBy { crops[it].second.width.toFloat() / crops[it].second.height }
        val lines = arrayOfNulls<OcrLine>(crops.size)
        for (batchIndices in order.chunked(REC_BATCH_SIZE)) {
            val batch = batchIndices.map { crops[it] }
            val widths = batch.map { (_, bitmap) -> recognitionWidths(bitmap.width, bitmap.height) }
            val batchWidth = widths.maxOf { it.second }
            val input = FloatArray(batch.size * 3 * REC_HEIGHT * batchWidth) // Zero padding, as in PaddleX.
            batch.forEachIndexed { index, (_, bitmap) ->
                writeRecognitionInput(bitmap, widths[index].first, input, index, batchWidth)
            }
            val (output, shape) = infer(recognition, input, longArrayOf(batch.size.toLong(), 3, REC_HEIGHT.toLong(), batchWidth.toLong()))
            val steps = shape[1].toInt()
            val classes = shape[2].toInt()
            batch.forEachIndexed { index, (box, _) ->
                val (text, score) = decoder.decode(output, index * steps * classes, steps)
                lines[batchIndices[index]] = OcrLine(text, score, box)
            }
        }
        // createBitmap returns the image itself for a box covering all of it.
        crops.forEach { (_, bitmap) -> if (bitmap !== image) bitmap.recycle() }
        return lines.filterNotNull()
    }

    /** The box's pixels; a box at least 1.5× taller than wide is turned 90° anticlockwise (`np.rot90`). */
    private fun crop(image: Bitmap, box: TextBox): Bitmap {
        if (box.height < VERTICAL_RATIO * box.width) {
            return Bitmap.createBitmap(image, box.left, box.top, box.width, box.height)
        }
        // Android rotates clockwise for positive angles (y points down).
        val anticlockwise = Matrix().apply { setRotate(-90f) }
        return Bitmap.createBitmap(image, box.left, box.top, box.width, box.height, anticlockwise, false)
    }

    /** Resizes [line] to [resizedWidth]×48 and writes it, normalised to [-1, 1], as batch item [index]. */
    private fun writeRecognitionInput(line: Bitmap, resizedWidth: Int, input: FloatArray, index: Int, batchWidth: Int) {
        val resized = line.scale(resizedWidth, REC_HEIGHT)
        val pixels = IntArray(resizedWidth * REC_HEIGHT).also { resized.getPixels(it, 0, resizedWidth, 0, 0, resizedWidth, REC_HEIGHT) }
        if (resized !== line) resized.recycle()
        val plane = REC_HEIGHT * batchWidth
        val base = index * 3 * plane
        for (y in 0 until REC_HEIGHT) {
            for (x in 0 until resizedWidth) {
                val pixel = pixels[y * resizedWidth + x]
                val at = base + y * batchWidth + x
                input[at] = (pixel and 0xFF) / 127.5f - 1f
                input[plane + at] = (pixel shr 8 and 0xFF) / 127.5f - 1f
                input[2 * plane + at] = (pixel shr 16 and 0xFF) / 127.5f - 1f
            }
        }
    }

    /** Runs a single-input, single-output model and returns the output values and shape. */
    private fun infer(session: OrtSession, input: FloatArray, shape: LongArray): Pair<FloatArray, LongArray> =
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), shape).use { tensor ->
            session.run(mapOf(session.inputNames.single() to tensor)).use { result ->
                val output = result.get(0) as OnnxTensor
                val buffer = output.floatBuffer
                FloatArray(buffer.remaining()).also { buffer.get(it) } to output.info.shape
            }
        }

    override fun close() {
        detection.close()
        recognition.close()
    }

    companion object {
        private const val TAG = "PowerPP.Ocr"

        // OCR.yaml's TextDetection settings, which override the model's own inference.yml.
        private const val DET_LIMIT_SIDE_LEN = 64
        private const val DET_MAX_SIDE_LIMIT = 4000
        private const val DET_THRESH = 0.3f
        private const val DET_BOX_THRESH = 0.6f
        private const val DET_UNCLIP_RATIO = 1.5f
        private const val DET_MAX_CANDIDATES = 3000 // The model's inference.yml.
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

        private const val REC_HEIGHT = 48
        private const val REC_MIN_WIDTH = 320 // RecResizeImg image_shape [3, 48, 320].
        private const val REC_MAX_WIDTH = 3200
        private const val REC_BATCH_SIZE = 6 // OCR.yaml's TextRecognition batch_size.
        private const val VERTICAL_RATIO = 1.5f
        private const val MAX_THREADS = 4

        /** Loads the engine and models. Slow (seconds); call off the main thread and keep the result. */
        fun open(engine: File, modelDirectory: File): PpOcr {
            OrtEngine.load(engine)
            val dictionary = RecognitionDictionary.parse(File(modelDirectory, OcrCatalog.RECOGNITION_CONFIG).readText())
            val environment = OrtEnvironment.getEnvironment()
            val detection = environment.createSession(File(modelDirectory, OcrCatalog.DETECTION_MODEL).absolutePath, sessionOptions())
            val recognition = try {
                environment.createSession(File(modelDirectory, OcrCatalog.RECOGNITION_MODEL).absolutePath, sessionOptions())
            } catch (e: Exception) {
                detection.close()
                throw e
            }
            return PpOcr(detection, recognition, CtcDecoder(recognitionCharacters(dictionary, recognition)))
        }

        /**
         * Plain CPU with at most 4 threads. Measured on a Pixel 9 Pro XL (2026-10-08, full screen,
         * Medium): ORT's default of one thread per core (8, including the 4 slow ones) took 12 s to
         * detect; 4 threads took 7 s. The XNNPACK provider crashed (SIGSEGV) in ORT 1.30.
         */
        private fun sessionOptions() = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(min(MAX_THREADS, Runtime.getRuntime().availableProcessors()))
        }

        /** PaddleX appends a space to the dictionary (`use_space_char`); the model's class count shows it. */
        private fun recognitionCharacters(dictionary: List<String>, recognition: OrtSession): List<String> {
            val classes = (recognition.outputInfo.values.first().info as TensorInfo).shape[2]
            return when (classes) {
                dictionary.size + 2L -> dictionary + " "
                dictionary.size + 1L -> dictionary
                else -> error("Recognition has $classes classes for a ${dictionary.size}-character dictionary")
            }
        }

        /**
         * PaddleX's `DetResizeForTest` with `limit_type: min`: scale up so the short side is at least
         * 64, scale down so the long side is at most 4000, then round both sides to a multiple of 32
         * (Python's `round`, half to even).
         */
        internal fun detectionSize(width: Int, height: Int): Pair<Int, Int> {
            var ratio = if (min(width, height) < DET_LIMIT_SIDE_LEN) DET_LIMIT_SIDE_LEN.toDouble() / min(width, height) else 1.0
            var resizedWidth = (width * ratio).toInt()
            var resizedHeight = (height * ratio).toInt()
            if (max(resizedWidth, resizedHeight) > DET_MAX_SIDE_LIMIT) {
                ratio = DET_MAX_SIDE_LIMIT.toDouble() / max(resizedWidth, resizedHeight)
                resizedWidth = (resizedWidth * ratio).toInt()
                resizedHeight = (resizedHeight * ratio).toInt()
            }
            return roundTo32(resizedWidth) to roundTo32(resizedHeight)
        }

        private fun roundTo32(side: Int): Int = max(round(side / 32.0).toInt() * 32, 32)

        /**
         * PaddleX's `OCRReisizeNormImg`: the line is resized to height 48 keeping its aspect ratio
         * (at most 3200 wide) and padded to at least 320. Returns (resized width, padded width).
         */
        internal fun recognitionWidths(width: Int, height: Int): Pair<Int, Int> {
            val ratio = width.toDouble() / height
            val padded = (REC_HEIGHT * max(REC_MIN_WIDTH.toDouble() / REC_HEIGHT, ratio)).toInt()
            if (padded > REC_MAX_WIDTH) return REC_MAX_WIDTH to REC_MAX_WIDTH
            return min(ceil(REC_HEIGHT * ratio).toInt(), padded) to padded
        }
    }
}
