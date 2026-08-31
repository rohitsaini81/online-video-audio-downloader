package com.example.ytdlpapp

import android.content.Context
import java.io.File

class YtDlpRunner(private val context: Context) {

    private fun prepareBinary(): File {
        val binDir = File(context.filesDir, "bin")

        if (!binDir.exists()) {
            binDir.mkdirs()
        }

        val binary = File(binDir, "yt-dlp")

        if (!binary.exists()) {
            context.assets.open("bin/yt-dlp").use { input ->
                binary.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        binary.setExecutable(true)

        return binary
    }

    fun download(url: String): Result<String> {
        return try {
            val ytDlp = prepareBinary()

            val outputDir = File(
                context.getExternalFilesDir(null),
                "downloads"
            )

            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val outputTemplate = File(
                outputDir,
                "%(title)s.%(ext)s"
            ).absolutePath

            val process = ProcessBuilder(
                ytDlp.absolutePath,
                "-o",
                outputTemplate,
                url
            )
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream
                .bufferedReader()
                .readText()

            val exitCode = process.waitFor()

            if (exitCode == 0) {
                Result.success(output)
            } else {
                Result.failure(
                    Exception("yt-dlp failed:\n$output")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
