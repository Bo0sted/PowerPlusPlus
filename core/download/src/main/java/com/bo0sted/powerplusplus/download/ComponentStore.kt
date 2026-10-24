package com.bo0sted.powerplusplus.download

import android.util.Log
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

/** One file to download: tried from each of [urls] in order, accepted only if size and hash match. */
data class RemoteFile(val name: String, val sizeBytes: Long, val sha256: String, val urls: List<String>)

/** Something installed on demand from pinned [files] (an engine or a model). */
interface Downloadable {
    /** Stable; also the directory name on disk. */
    val id: String
    val version: String
    val files: List<RemoteFile>

    val downloadBytes: Long get() = files.sumOf { it.sizeBytes }
}

sealed interface ComponentState {
    data object NotInstalled : ComponentState
    data class Downloading(val bytes: Long, val total: Long) : ComponentState
    data object Verifying : ComponentState
    data class Installed(val bytesOnDisk: Long) : ComponentState

    /**
     * An earlier version ([installedVersion]) is on disk; it doesn't count as installed, since files
     * from another version may not work with this build (e.g. an engine library and its Java glue).
     * Installing replaces it.
     */
    data class Outdated(val installedVersion: String, val bytesOnDisk: Long) : ComponentState
    data class Failed(val reason: String) : ComponentState
}

/**
 * Downloads, verifies, stores and removes [components] under [root], one directory each. A
 * component counts as installed only after its files are verified **and** [finishInstall] passes;
 * a marker file written last records that, with the version installed. Only the catalog's current
 * version counts; an earlier one is [ComponentState.Outdated] until the user installs the new one. Downloads run in a process-wide scope (the accessibility service
 * keeps the process alive) and resume after interruption.
 *
 * [root] should be in `noBackupFilesDir`: Auto Backup includes filesDir, and with more than 25 MB it
 * backs up nothing at all, so large downloads would silently stop the settings from being backed up.
 */
abstract class ComponentStore<C : Downloadable>(
    private val root: File,
    val components: List<C>,
    private val tag: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = HashMap<String, Job>()

    private val states = MutableStateFlow(components.associate { it.id to initialState(it) })
    val state: StateFlow<Map<String, ComponentState>> = states.asStateFlow()

    fun directory(component: C) = File(root, component.id)

    /** Installed at the catalog's current version. */
    fun isInstalled(component: C) = installedVersion(component) == component.version

    /** The version on disk, or `null` if none is fully installed. */
    private fun installedVersion(component: C): String? =
        File(directory(component), INSTALLED_MARKER).takeIf { it.isFile }?.readText()

    /** Downloads and installs [component], unless that's already under way. */
    fun install(component: C) {
        if (jobs[component.id]?.isActive == true) return
        jobs[component.id] = scope.launch { if (prepare(component)) installNow(component) }
    }

    fun remove(component: C) {
        jobs.remove(component.id)?.cancel()
        scope.launch {
            withContext(Dispatchers.IO) { directory(component).deleteRecursively() }
            set(component, ComponentState.NotInstalled)
        }
    }

    /**
     * Runs before [component]'s download; returning false cancels it (after setting a
     * [ComponentState.Failed] with [fail]). Used for prerequisites, see [ensureInstalled].
     */
    protected open suspend fun prepare(component: C): Boolean = true

    /** Whether [file] can be skipped because [finishInstall] already produced what it's needed for. */
    protected open fun isAlreadyUnpacked(component: C, file: RemoteFile, directory: File): Boolean = false

    /** Runs on an IO thread once every file is verified: unpacks and load-checks. Throws on failure. */
    protected abstract fun finishInstall(component: C, directory: File)

    /** Installs [dependency] now unless it is installed or being installed; returns whether it is installed. */
    protected suspend fun ensureInstalled(dependency: C): Boolean {
        if (isInstalled(dependency)) return true
        val running = jobs[dependency.id]
        if (running?.isActive == true) running.join() else installNow(dependency)
        return isInstalled(dependency)
    }

    protected fun fail(component: C, reason: String) = set(component, ComponentState.Failed(reason))

    private suspend fun installNow(component: C) {
        try {
            val directory = directory(component)
            // Another version's files (and any partial download for it) mustn't be mixed with this one's.
            if (installedVersion(component).let { it != null && it != component.version }) {
                withContext(Dispatchers.IO) { directory.deleteRecursively() }
            }
            directory.mkdirs()
            val total = component.downloadBytes
            var finishedBytes = 0L
            var shownPermille = -1L
            for (file in component.files) {
                if (isAlreadyUnpacked(component, file, directory)) break
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
            withContext(Dispatchers.IO) {
                finishInstall(component, directory)
                File(directory, INSTALLED_MARKER).writeText(component.version)
            }
            set(component, ComponentState.Installed(directory.sizeOnDisk()))
        } catch (e: CancellationException) {
            throw e // Removed while downloading; remove() sets the state.
        } catch (e: Exception) {
            Log.w(tag, "Install of ${component.id} failed", e)
            fail(component, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun initialState(component: C): ComponentState {
        val installed = installedVersion(component) ?: return ComponentState.NotInstalled
        val size = directory(component).sizeOnDisk()
        return if (installed == component.version) ComponentState.Installed(size) else ComponentState.Outdated(installed, size)
    }

    private fun set(component: C, state: ComponentState) = states.update { it + (component.id to state) }

    private fun File.sizeOnDisk(): Long = walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private companion object {
        const val INSTALLED_MARKER = "installed"
    }
}
