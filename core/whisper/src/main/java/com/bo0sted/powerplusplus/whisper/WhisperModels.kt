package com.bo0sted.powerplusplus.whisper

import android.content.Context
import com.bo0sted.powerplusplus.download.ComponentStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The downloadable speech models (see [ComponentStore]); a model counts as installed once it loads. */
@Singleton
class WhisperModels @Inject constructor(@ApplicationContext private val context: Context) : ComponentStore<WhisperModel>(
    root = File(context.noBackupFilesDir, "whisper"),
    components = WhisperCatalog.ALL,
    tag = TAG,
) {
    /** The installed model to use, or `null` if none is installed. */
    fun installed(): WhisperModel? = components.firstOrNull(::isInstalled)

    fun modelFile(model: WhisperModel) = File(directory(model), WhisperCatalog.MODEL_FILE)

    override fun finishInstall(component: WhisperModel, directory: File) {
        SpeechModel.load(context, File(directory, WhisperCatalog.MODEL_FILE), component.language).close()
    }

    internal companion object {
        const val TAG = "PowerPP.Whisper"
    }
}
