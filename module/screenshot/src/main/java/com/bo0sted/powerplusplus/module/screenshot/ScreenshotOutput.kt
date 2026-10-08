package com.bo0sted.powerplusplus.module.screenshot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Where a finished screenshot goes. [copy] and [share] use a PNG in the app's private cache exposed
 * through a FileProvider — Android's clipboard holds content URIs, never image bytes — so nothing
 * lands in the gallery. Only [save] writes to shared storage. Call off the main thread.
 */
internal object ScreenshotOutput {

    fun copy(context: Context, image: Bitmap): Boolean {
        val uri = writeCacheFile(context, image) ?: return false
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newUri(context.contentResolver, "Power++ screenshot", uri))
        return true
    }

    /** Returns a chooser intent; the caller starts it while its overlay is still visible. */
    fun shareIntent(context: Context, image: Bitmap): Intent? {
        val uri = writeCacheFile(context, image) ?: return null
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "Power++ screenshot", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Saves to Pictures/Power++. Needs no permission for the app's own files (API 29+). */
    fun save(context: Context, image: Bitmap): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${fileStem()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, MIME)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Power++")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
        }.getOrDefault(false)
        if (!written) {
            resolver.delete(uri, null, null)
            return false
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return true
    }

    private fun writeCacheFile(context: Context, image: Bitmap): Uri? {
        val directory = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        prune(directory)
        val file = File(directory, "${fileStem()}.png")
        val written = runCatching { file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            .getOrDefault(false)
        if (!written) return null
        return FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
    }

    /** Keeps the most recent few files: enough for clipboard history, never an ever-growing cache. */
    private fun prune(directory: File) {
        directory.listFiles()?.sortedByDescending { it.lastModified() }?.drop(KEEP_FILES - 1)?.forEach { it.delete() }
    }

    private fun fileStem(): String = "Power++_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    private const val MIME = "image/png"
    private const val CACHE_DIR = "screenshots"
    private const val KEEP_FILES = 5
    const val AUTHORITY_SUFFIX = ".screenshots"
}
