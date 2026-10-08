package com.bo0sted.powerplusplus.ocr

import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ComponentState {
    data object NotInstalled : ComponentState
    data class Downloading(val bytes: Long, val total: Long) : ComponentState
    data object Verifying : ComponentState
    data class Installed(val bytesOnDisk: Long) : ComponentState
    data class Failed(val reason: String) : ComponentState
}

/**
 * Downloads, verifies, stores and removes the OCR engine and models. A component counts as
 * installed only after its files are verified **and** a load check passes; a marker file written
 * last records that. Downloads run in a process-wide scope (the accessibility service keeps the
 * process alive) and resume after interruption.
 */
@Singleton
class OcrComponents @Inject constructor(@ApplicationContext private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = HashMap<String, Job>()
    /**
     * Downloads live in no-backup storage: Auto Backup includes filesDir, and with more than 25 MB
     * it backs up nothing at all — the ~170 MB of OCR files would silently stop the settings from
     * being backed up. Both locations are deleted on uninstall.
     */
    private val root = File(context.noBackupFilesDir, "ocr").also { migrateFrom(File(context.filesDir, "ocr"), it) }

    private val states = MutableStateFlow(OcrCatalog.ALL.associate { it.id to initialState(it) })
    val state: StateFlow<Map<String, ComponentState>> = states.asStateFlow()

    fun directory(component: OcrComponent) = File(root, component.id)

    fun isInstalled(component: OcrComponent) = File(directory(component), INSTALLED_MARKER).isFile

    /** The extracted engine library, once installed. */
    fun engineLibrary(): File = File(directory(OcrCatalog.ENGINE), ENGINE_LIBRARY)

    /** Downloads and installs [component]; a model first installs the engine if it's missing. */
    fun install(component: OcrComponent) {
        if (jobs[component.id]?.isActive == true) return
        jobs[component.id] = scope.launch {
            if (component.kind == OcrComponent.Kind.Model && !isInstalled(OcrCatalog.ENGINE)) {
                val engineJob = jobs[OcrCatalog.ENGINE.id]
                if (engineJob?.isActive == true) engineJob.join() else installNow(OcrCatalog.ENGINE)
                if (!isInstalled(OcrCatalog.ENGINE)) {
                    set(component, ComponentState.Failed("The OCR engine couldn't be installed"))
                    return@launch
                }
            }
            installNow(component)
        }
    }

    private suspend fun installNow(component: OcrComponent) {
        try {
            val directory = directory(component).apply { mkdirs() }
            val total = component.downloadBytes
            var finishedBytes = 0L
            var shownPermille = -1L
            for (file in component.files) {
                if (component.kind == OcrComponent.Kind.Engine && File(directory, ENGINE_LIBRARY).isFile) break
                VerifiedDownloader.download(file, directory) { bytes ->
                    // Publish at most once per 0.1 %, not on every buffer.
                    val done = finishedBytes + bytes
                    val permille = done * 1000 / total
                    if (permille != shownPermille) {
                        shownPermille = permille
                        set(component, ComponentState.Downloading(done, total))
                    }
                }
                finishedBytes += file.sizeBytes
            }
            set(component, ComponentState.Verifying)
            withContext(Dispatchers.IO) { finishInstall(component, directory) }
            set(component, ComponentState.Installed(directory.sizeOnDisk()))
        } catch (e: CancellationException) {
            throw e // Removed while downloading; remove() sets the state.
        } catch (e: Exception) {
            Log.w(TAG, "Install of ${component.id} failed", e)
            set(component, ComponentState.Failed(e.message ?: e.javaClass.simpleName))
        }
    }

    fun remove(component: OcrComponent) {
        jobs.remove(component.id)?.cancel()
        scope.launch {
            withContext(Dispatchers.IO) { directory(component).deleteRecursively() }
            set(component, ComponentState.NotInstalled)
        }
    }

    private fun finishInstall(component: OcrComponent, directory: File) {
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
        File(directory, INSTALLED_MARKER).writeText(component.version)
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

    /** Moves downloads made by earlier builds out of backed-up storage (same filesystem: a rename). */
    private fun migrateFrom(old: File, new: File) {
        if (!old.isDirectory) return
        if (new.exists() || !old.renameTo(new)) {
            Log.w(TAG, "Couldn't move $old to $new; removing the old copy")
            old.deleteRecursively()
        }
    }

    private fun initialState(component: OcrComponent): ComponentState =
        if (isInstalled(component)) ComponentState.Installed(directory(component).sizeOnDisk()) else ComponentState.NotInstalled

    private fun set(component: OcrComponent, state: ComponentState) = states.update { it + (component.id to state) }

    private fun File.sizeOnDisk(): Long = walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private companion object {
        const val TAG = "PowerPP.Ocr"
        const val INSTALLED_MARKER = "installed"
        const val ENGINE_LIBRARY = "libonnxruntime.so"
    }
}
