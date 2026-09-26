package com.rohit.downloaderpro

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONArray
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

    fun extractInfo(
        url: String,
        onLog: (String) -> Unit,
    ): Result<MediaInfo> = runCatching {
        ensurePythonStarted()

        val python = Python.getInstance()
        val module = python.getModule("ytdlp_runner")
        val payload = module.callAttr(
            "extract_info",
            url,
            YtDlpLogCallback(onLog),
        ).toJava(String::class.java)
        parseMediaInfo(payload, url)
    }

    fun download(
        url: String,
        audioOnly: Boolean,
        onLog: (String) -> Unit,
    ): Result<String> = download(
        url = url,
        choice = FormatChoice(
            id = if (audioOnly) "audio_m4a" else "video_best",
            category = if (audioOnly) "audio" else "video",
            label = if (audioOnly) "Audio" else "Video",
            maxHeight = 0,
            audioOnly = audioOnly,
            convertMp3 = false,
            sizeBytes = 0,
            sizeLabel = "",
        ),
        onLog = onLog,
        onProgress = {},
    ).map { result ->
        JSONObject()
            .put("status", "ok")
            .put("filepath", result.filepath)
            .put("output_dir", "Download/YtDlpApp")
            .toString()
    }

    fun download(
        url: String,
        choice: FormatChoice,
        onLog: (String) -> Unit,
        onProgress: (DownloadProgress) -> Unit,
    ): Result<DownloadResult> = runCatching {
        ensurePythonStarted()

        val outputDir = File(context.cacheDir, "ytdlp-downloads")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val python = Python.getInstance()
        val module = python.getModule("ytdlp_runner")
        val callback = progressCallback(onLog, onProgress)

        val tempFile = if (choice.audioOnly) {
            onProgress(DownloadProgress(0.02f, 0, choice.sizeBytes, 0))
            val payload = module.callAttr(
                "download_track",
                url,
                outputDir.absolutePath,
                "audio",
                YtDlpLogCallback(callback),
                0,
            ).toJava(String::class.java)
            val audioFile = File(JSONObject(payload).getString("filepath"))
            if (choice.convertMp3) {
                onProgress(DownloadProgress(0.9f, 0, choice.sizeBytes, 0))
                convertToMp3(audioFile, outputDir, onLog)
            } else {
                audioFile
            }
        } else {
            val videoDir = File(outputDir, "video")
            val audioDir = File(outputDir, "audio")
            videoDir.mkdirs()
            audioDir.mkdirs()

            onLog("Downloading separate video and audio tracks...")
            val videoPayload = module.callAttr(
                "download_track",
                url,
                videoDir.absolutePath,
                "video",
                YtDlpLogCallback { line ->
                    remapProgress(line, 0.05f, 0.50f, choice.sizeBytes, onProgress)
                    if (!line.startsWith("PROGRESS:")) onLog("VIDEO: $line")
                },
                choice.maxHeight,
            ).toJava(String::class.java)
            val audioPayload = module.callAttr(
                "download_track",
                url,
                audioDir.absolutePath,
                "audio",
                YtDlpLogCallback { line ->
                    remapProgress(line, 0.55f, 0.85f, choice.sizeBytes, onProgress)
                    if (!line.startsWith("PROGRESS:")) onLog("AUDIO: $line")
                },
                0,
            ).toJava(String::class.java)

            val videoFile = File(JSONObject(videoPayload).getString("filepath"))
            val audioFile = File(JSONObject(audioPayload).getString("filepath"))
            val title = JSONObject(videoPayload).optString("title").ifBlank { videoFile.nameWithoutExtension }
            onProgress(DownloadProgress(0.88f, 0, choice.sizeBytes, 0))
            mergeWithFfmpeg(videoFile, audioFile, outputDir, title, onLog)
        }

        onLog("Publishing to Download/YtDlpApp...")
        onProgress(DownloadProgress(0.96f, tempFile.length(), tempFile.length(), 0))
        val published = publishToDownloads(tempFile)
        onLog("Saved to: ${published.filepath}")
        onProgress(DownloadProgress(1f, published.filesize, published.filesize, 0))
        published.copy(title = published.title.ifBlank { tempFile.nameWithoutExtension })
    }

    private fun progressCallback(
        onLog: (String) -> Unit,
        onProgress: (DownloadProgress) -> Unit,
    ): (String) -> Unit = { line ->
        val progress = parseProgressLine(line)
        if (progress != null) {
            onProgress(progress)
        } else {
            onLog(line)
        }
    }

    private fun remapProgress(
        line: String,
        start: Float,
        end: Float,
        fallbackTotal: Long,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        val parsed = parseProgressLine(line) ?: return
        val scaled = start + ((end - start) * parsed.percent.coerceIn(0f, 1f))
        onProgress(
            parsed.copy(
                percent = scaled,
                totalBytes = if (parsed.totalBytes > 0) parsed.totalBytes else fallbackTotal,
            )
        )
    }

    private fun parseProgressLine(line: String): DownloadProgress? {
        if (!line.startsWith("PROGRESS:")) return null
        return runCatching {
            val json = JSONObject(line.removePrefix("PROGRESS:"))
            val total = json.optLong("total")
            val percent = if (total > 0) {
                (json.optDouble("percent") / 100.0).toFloat()
            } else {
                0f
            }
            DownloadProgress(
                percent = percent.coerceIn(0f, 1f),
                downloadedBytes = json.optLong("downloaded"),
                totalBytes = total,
                speedBytesPerSec = json.optDouble("speed").toLong(),
            )
        }.getOrNull()
    }

    private fun convertToMp3(
        audioFile: File,
        outputDir: File,
        onLog: (String) -> Unit,
    ): File {
        require(audioFile.isFile) { "Audio track was not found: ${audioFile.absolutePath}" }

        val mp3File = File(outputDir, audioFile.nameWithoutExtension + ".mp3")
        if (mp3File.exists()) {
            mp3File.delete()
        }

        onLog("Converting to MP3 with FFmpegKit...")
        val session = runFfmpeg(
            "-y",
            "-i", audioFile.absolutePath,
            "-vn",
            "-b:a", "320k",
            mp3File.absolutePath,
        )
        if (!ReturnCode.isSuccess(session.returnCode)) {
            throw IllegalStateException(
                "FFmpeg MP3 conversion failed: ${session.failStackTrace ?: session.output}"
            )
        }

        audioFile.delete()
        onLog("Conversion complete: ${mp3File.absolutePath}")
        return mp3File
    }

    private fun mergeWithFfmpeg(
        videoFile: File,
        audioFile: File,
        outputDir: File,
        title: String,
        onLog: (String) -> Unit,
    ): File {
        require(videoFile.isFile) { "Video track was not found: ${videoFile.absolutePath}" }
        require(audioFile.isFile) { "Audio track was not found: ${audioFile.absolutePath}" }

        val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "download" }
        val mergedFile = File(outputDir, "$safeTitle.mp4")
        if (mergedFile.exists()) {
            mergedFile.delete()
        }

        onLog("Merging with FFmpegKit...")
        val session = runFfmpeg(
            "-y",
            "-i", videoFile.absolutePath,
            "-i", audioFile.absolutePath,
            "-c:v", "copy",
            "-c:a", "aac",
            "-movflags", "+faststart",
            mergedFile.absolutePath,
        )
        if (!ReturnCode.isSuccess(session.returnCode)) {
            throw IllegalStateException(
                "FFmpeg merge failed: ${session.failStackTrace ?: session.output}"
            )
        }

        videoFile.delete()
        audioFile.delete()
        onLog("Merge complete: ${mergedFile.absolutePath}")
        return mergedFile
    }

    private fun runFfmpeg(vararg arguments: String): FFmpegSession {
        return try {
            FFmpegKit.executeWithArguments(arguments)
        } catch (error: Throwable) {
            throw IllegalStateException(error.localizedMessage ?: error.toString(), error)
        }
    }

    private fun publishToDownloads(source: File): DownloadResult {
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
            val size = source.length()
            val title = source.nameWithoutExtension
            source.delete()
            return DownloadResult(
                filepath = "$relativePath/${source.name}",
                uri = uri.toString(),
                title = title,
                ext = extension,
                filesize = size,
            )
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun parseMediaInfo(payload: String, url: String): MediaInfo {
        val json = JSONObject(payload)
        val optionsJson = json.optJSONArray("options") ?: JSONArray()
        val options = buildList {
            for (index in 0 until optionsJson.length()) {
                val item = optionsJson.getJSONObject(index)
                add(
                    FormatChoice(
                        id = item.optString("id"),
                        category = item.optString("category", "video"),
                        label = item.optString("label"),
                        maxHeight = item.optInt("max_height"),
                        audioOnly = item.optBoolean("audio_only"),
                        convertMp3 = item.optBoolean("convert_mp3"),
                        sizeBytes = item.optLong("size_bytes"),
                        sizeLabel = item.optString("size_label"),
                    )
                )
            }
        }
        return MediaInfo(
            title = json.optString("title", "Untitled"),
            source = json.optString("source", sourceFromUrl(url)),
            thumbnail = json.optString("thumbnail"),
            duration = json.optLong("duration"),
            webpageUrl = json.optString("webpage_url", url),
            options = options,
        )
    }
}

fun sourceFromUrl(url: String): String {
    val lowered = url.lowercase()
    return when {
        "youtu" in lowered -> "YouTube"
        "instagram" in lowered -> "Instagram"
        "tiktok" in lowered -> "TikTok"
        "facebook" in lowered || "fb.watch" in lowered -> "Facebook"
        "x.com" in lowered || "twitter" in lowered -> "X"
        else -> "Web"
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 10) "%.0f MB".format(mb) else if (mb >= 1) "%.1f MB".format(mb) else "%.0f KB".format(bytes / 1024.0)
}

fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return ""
    val mb = bytesPerSec / (1024.0 * 1024.0)
    return if (mb >= 1) "%.1f MB/s".format(mb) else "%.0f KB/s".format(bytesPerSec / 1024.0)
}
