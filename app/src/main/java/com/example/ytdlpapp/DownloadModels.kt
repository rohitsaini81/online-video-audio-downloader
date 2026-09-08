package com.example.ytdlpapp

data class FormatChoice(
    val id: String,
    val category: String,
    val label: String,
    val maxHeight: Int,
    val audioOnly: Boolean,
    val convertMp3: Boolean,
    val sizeBytes: Long,
    val sizeLabel: String,
)

data class MediaInfo(
    val title: String,
    val source: String,
    val thumbnail: String,
    val duration: Long,
    val webpageUrl: String,
    val options: List<FormatChoice>,
)

data class DownloadProgress(
    val percent: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
)

data class DownloadResult(
    val filepath: String,
    val uri: String,
    val title: String,
    val ext: String,
    val filesize: Long,
)

enum class DownloadStatus {
    RUNNING,
    COMPLETED,
    FAILED,
}

data class DownloadItem(
    val id: String,
    val url: String,
    val title: String,
    val source: String,
    val formatLabel: String,
    val extension: String,
    val status: DownloadStatus,
    val progress: Float,
    val speedLabel: String,
    val percentLabel: String,
    val sizeLabel: String,
    val filepath: String = "",
    val uri: String = "",
    val error: String = "",
    val completedAt: Long = 0L,
    val fileSizeBytes: Long = 0L,
)

enum class AppTab {
    Home,
    Downloads,
    History,
}
