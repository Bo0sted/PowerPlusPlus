package com.bo0sted.powerplusplus.whisper

import android.content.Context
import com.bo0sted.powerplusplus.download.ComponentStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The downloadable speech models, each with its speech detector (see [ComponentStore]); one counts
 * as installed once both load.
 */
@Singleton
class WhisperModels @Inject constructor(@ApplicationContext private val context: Context) : ComponentStore<WhisperModel>(
    root = File(context.noBackupFilesDir, "whisper"),
    components = WhisperCatalog.ALL,
    tag = TAG,
) {
    /** The installed model to use, or `null` if none is installed. */
    fun installed(): WhisperModel? = components.firstOrNull(::isInstalled)

    fun modelFile(model: WhisperModel) = File(directory(model), WhisperCatalog.MODEL_FILE)

    fun speechDetectorFile(model: WhisperModel) = File(directory(model), WhisperCatalog.SPEECH_DETECTOR_FILE)

    override fun finishInstall(component: WhisperModel, directory: File) {
        SpeechModel.load(context, File(directory, WhisperCatalog.MODEL_FILE), component.language).close()
        SpeechDetector.load(context, File(directory, WhisperCatalog.SPEECH_DETECTOR_FILE)).close()
    }

    internal companion object {
        const val TAG = "PowerPP.Whisper"
    }
}
