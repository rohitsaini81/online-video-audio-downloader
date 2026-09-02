package com.example.ytdlpapp

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File

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

    fun download(url: String): Result<String> = runCatching {
        ensurePythonStarted()

        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val outputDir = File(baseDir, "downloads")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val python = Python.getInstance()
        val module = python.getModule("ytdlp_runner")
        module.callAttr("download", url, outputDir.absolutePath).toJava(String::class.java)
    }
}
