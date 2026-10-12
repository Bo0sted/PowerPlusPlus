package com.bo0sted.powerplusplus.ocr

import android.content.Context
import android.os.Build
import android.util.Log
import com.bo0sted.powerplusplus.download.ComponentStore
import com.bo0sted.powerplusplus.download.RemoteFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The OCR engine and models (see [ComponentStore]). Installing a model installs the engine first
 * if it's missing; the engine's AAR is reduced to this device's library once verified.
 */
@Singleton
class OcrComponents @Inject constructor(@ApplicationContext context: Context) : ComponentStore<OcrComponent>(
    root = File(context.noBackupFilesDir, "ocr").also { migrateFrom(File(context.filesDir, "ocr"), it) },
    components = OcrCatalog.ALL,
    tag = TAG,
) {

    /** The extracted engine library, once installed. */
    fun engineLibrary(): File = File(directory(OcrCatalog.ENGINE), ENGINE_LIBRARY)

    override suspend fun prepare(component: OcrComponent): Boolean {
        if (component.kind != OcrComponent.Kind.Model || ensureInstalled(OcrCatalog.ENGINE)) return true
        fail(component, "The OCR engine couldn't be installed")
        return false
    }

    override fun isAlreadyUnpacked(component: OcrComponent, file: RemoteFile, directory: File): Boolean =
        component.kind == OcrComponent.Kind.Engine && File(directory, ENGINE_LIBRARY).isFile

    override fun finishInstall(component: OcrComponent, directory: File) {
        when (component.kind) {
            OcrComponent.Kind.Engine -> {
                val aar = File(directory, component.files.single().name)
                val library = File(directory, ENGINE_LIBRARY)
                if (aar.isFile) {
                    extractEngine(aar, library)
                    aar.delete()
                }
                OrtEngine.load(library)
            }
            OcrComponent.Kind.Model -> {
                check(isInstalled(OcrCatalog.ENGINE)) { "Install the OCR engine first" }
                OrtEngine.load(engineLibrary())
                val dictionary = RecognitionDictionary.parse(File(directory, OcrCatalog.RECOGNITION_CONFIG).readText())
                val info = OrtEngine.checkModels(
                    File(directory, OcrCatalog.DETECTION_MODEL),
                    File(directory, OcrCatalog.RECOGNITION_MODEL),
                    dictionary.size,
                )
                Log.i(TAG, "${component.id}: ${dictionary.size} characters, ${info.recognitionClasses} recognition classes")
            }
        }
    }

    /** Takes this device's preferred ABI's engine out of the (already verified) AAR. */
    private fun extractEngine(aar: File, target: File) {
        ZipFile(aar).use { zip ->
            val entry = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi -> zip.getEntry("jni/$abi/$ENGINE_LIBRARY") }
                ?: error("No engine for ${Build.SUPPORTED_ABIS.joinToString()}")
            val partial = File(target.parentFile, "$ENGINE_LIBRARY.part")
            zip.getInputStream(entry).use { input -> partial.outputStream().use { input.copyTo(it) } }
            check(partial.renameTo(target)) { "rename failed" }
        }
    }

    private companion object {
        const val TAG = "PowerPP.Ocr"
        const val ENGINE_LIBRARY = "libonnxruntime.so"

        /** Moves downloads made by earlier builds out of backed-up storage (same filesystem: a rename). */
        fun migrateFrom(old: File, new: File) {
            if (!old.isDirectory) return
            if (new.exists() || !old.renameTo(new)) {
                Log.w(TAG, "Couldn't move $old to $new; removing the old copy")
                old.deleteRecursively()
            }
        }
    }
}
