package com.example.ytdlpapp

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject
import java.io.File

class YtDlpLogCallback(private val onLog: (String) -> Unit) {
    fun log(message: String) = onLog(message)
}

class YtDlpRunner(private val context: Context) {

    @Volatile
    private var pythonStarted = false

    private fun ensurePythonStarted() {
        if (pythonStarted) return

        synchronized(this) {
            if (pythonStarted) return

            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context.applicationContext))
            }
            pythonStarted = true
        }
    }

    fun probe(): Result<String> = runCatching {
        ensurePythonStarted()

        val python = Python.getInstance()
        val module = python.getModule("ytdlp_runner")
        module.callAttr("probe").toJava(String::class.java)
    }

    fun download(
        url: String,
        audioOnly: Boolean,
        onLog: (String) -> Unit,
    ): Result<String> = runCatching {
        ensurePythonStarted()

        val outputDir = File(context.cacheDir, "ytdlp-downloads")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val python = Python.getInstance()
        val module = python.getModule("ytdlp_runner")
        val payload = module.callAttr(
            "download",
            url,
            outputDir.absolutePath,
            if (audioOnly) "audio" else "video",
            YtDlpLogCallback(onLog),
        ).toJava(String::class.java)

        val tempFile = File(JSONObject(payload).getString("filepath"))
        onLog("Publishing to Download/YtDlpApp...")
        val publicLocation = publishToDownloads(tempFile)
        onLog("Saved to: $publicLocation")

        JSONObject()
            .put("status", "ok")
            .put("filepath", publicLocation)
            .put("output_dir", "Download/YtDlpApp")
            .toString()
    }

    private fun publishToDownloads(source: File): String {
        require(source.isFile) { "Downloaded file was not found: ${source.absolutePath}" }
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Public Downloads publishing requires Android 10 or newer"
        }

        val extension = source.extension.lowercase()
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/YtDlpApp"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = checkNotNull(resolver.insert(collection, values)) {
            "Android couldn't create the destination file in $relativePath"
        }

        try {
            resolver.openOutputStream(uri, "w").use { output ->
                checkNotNull(output) { "Android couldn't open the destination file" }
                source.inputStream().use { input -> input.copyTo(output) }
            }

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            source.delete()
            return "$relativePath/${source.name}"
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
