package com.example.ytdlpapp

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloaderViewModel(application: Application) : AndroidViewModel(application) {
    private val runner = YtDlpRunner(application)
    private val historyStore = DownloadHistoryStore(application)

    var url by mutableStateOf("")
        private set
    var selectedTab by mutableStateOf(AppTab.Home)
        private set
    var isFetchingFormats by mutableStateOf(false)
        private set
    var showFormatSheet by mutableStateOf(false)
        private set
    var showMorePlatforms by mutableStateOf(false)
        private set
    var snackbarMessage by mutableStateOf<String?>(null)
    var mediaInfo by mutableStateOf<MediaInfo?>(null)
        private set
    var selectedFormatId by mutableStateOf<String?>(null)
        private set
    var preferredCategory by mutableStateOf("video")
        private set
    var historyQuery by mutableStateOf("")
        private set
    var expandedHistoryId by mutableStateOf<String?>(null)

    val activeDownloads = mutableStateListOf<DownloadItem>()
    val recentCompleted = mutableStateListOf<DownloadItem>()
    val history = mutableStateListOf<DownloadItem>()

    val selectedFormat: FormatChoice?
        get() = mediaInfo?.options?.firstOrNull { it.id == selectedFormatId }
            ?: mediaInfo?.options?.firstOrNull()

    val filteredHistory: List<DownloadItem>
        get() {
            val query = historyQuery.trim()
            val items = history.toList()
            if (query.isEmpty()) return items
            return items.filter { item ->
                item.title.contains(query, ignoreCase = true) ||
                    item.source.contains(query, ignoreCase = true) ||
                    item.extension.contains(query, ignoreCase = true)
            }
        }

    init {
        history.addAll(historyStore.load())
        viewModelScope.launch(Dispatchers.IO) {
            runner.probe()
        }
    }

    fun onUrlChange(value: String) {
        url = value
    }

    fun onTabSelected(tab: AppTab) {
        selectedTab = tab
    }

    fun onHistoryQueryChange(value: String) {
        historyQuery = value
    }

    fun toggleMorePlatforms() {
        showMorePlatforms = !showMorePlatforms
    }

    fun consumeSnackbar() {
        snackbarMessage = null
    }

    fun closeFormatSheet() {
        showFormatSheet = false
        isFetchingFormats = false
    }

    fun selectFormat(id: String) {
        selectedFormatId = id
    }

    fun toggleHistoryMenu(id: String) {
        expandedHistoryId = if (expandedHistoryId == id) null else id
    }

    fun dismissHistoryMenu() {
        expandedHistoryId = null
    }

    fun requestDownload(preferredCategory: String = "video") {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            snackbarMessage = "Paste a link first."
            return
        }
        this.preferredCategory = preferredCategory
        showFormatSheet = true
        isFetchingFormats = true
        mediaInfo = null
        selectedFormatId = null

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runner.extractInfo(trimmed) {}
            }
            isFetchingFormats = false
            result.fold(
                onSuccess = { info ->
                    mediaInfo = info
                    val preferred = info.options.firstOrNull { it.category == preferredCategory }
                    selectedFormatId = preferred?.id ?: info.options.firstOrNull()?.id
                },
                onFailure = { error ->
                    showFormatSheet = false
                    snackbarMessage = error.message ?: "Couldn't read this link."
                }
            )
        }
    }

    fun confirmSelectedDownload() {
        val choice = selectedFormat ?: return
        val info = mediaInfo
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return

        showFormatSheet = false
        selectedTab = AppTab.Downloads
        startDownload(trimmed, info, choice)
    }

    private fun startDownload(trimmedUrl: String, info: MediaInfo?, choice: FormatChoice) {
        val id = UUID.randomUUID().toString()
        val item = DownloadItem(
            id = id,
            url = trimmedUrl,
            title = info?.title ?: trimmedUrl,
            source = info?.source ?: sourceFromUrl(trimmedUrl),
            formatLabel = choice.label,
            extension = if (choice.convertMp3) "mp3" else if (choice.audioOnly) "m4a" else "mp4",
            status = DownloadStatus.RUNNING,
            progress = 0.02f,
            speedLabel = "Starting…",
            percentLabel = "0%",
            sizeLabel = choice.sizeLabel.ifBlank { "Estimating size" },
            fileSizeBytes = choice.sizeBytes,
        )
        activeDownloads.add(0, item)

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runner.download(
                    url = trimmedUrl,
                    choice = choice,
                    onLog = {},
                    onProgress = { progress ->
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            updateActive(id) { current ->
                                val percent = (progress.percent * 100).toInt().coerceIn(0, 99)
                                val speed = formatSpeed(progress.speedBytesPerSec)
                                val downloaded = formatBytes(progress.downloadedBytes)
                                val total = formatBytes(
                                    if (progress.totalBytes > 0) progress.totalBytes else current.fileSizeBytes
                                )
                                current.copy(
                                    progress = progress.percent.coerceIn(0f, 0.99f),
                                    percentLabel = "$percent%",
                                    speedLabel = speed.ifBlank { current.speedLabel },
                                    sizeLabel = when {
                                        downloaded.isNotBlank() && total.isNotBlank() -> "$downloaded / $total"
                                        total.isNotBlank() -> total
                                        else -> current.sizeLabel
                                    },
                                )
                            }
                        }
                    },
                )
            }

            result.fold(
                onSuccess = { downloaded ->
                    val completed = activeDownloads.firstOrNull { it.id == id }?.copy(
                        status = DownloadStatus.COMPLETED,
                        progress = 1f,
                        percentLabel = "Completed",
                        speedLabel = "",
                        sizeLabel = formatBytes(downloaded.filesize).ifBlank { "Saved" },
                        filepath = downloaded.filepath,
                        uri = downloaded.uri,
                        title = downloaded.title.ifBlank { info?.title ?: "Download" },
                        extension = downloaded.ext.ifBlank { item.extension },
                        completedAt = System.currentTimeMillis(),
                        fileSizeBytes = downloaded.filesize,
                    ) ?: item.copy(
                        status = DownloadStatus.COMPLETED,
                        progress = 1f,
                        filepath = downloaded.filepath,
                        uri = downloaded.uri,
                        completedAt = System.currentTimeMillis(),
                    )
                    activeDownloads.removeAll { it.id == id }
                    recentCompleted.add(0, completed)
                    history.add(0, completed)
                    persistHistory()
                    snackbarMessage = "Saved to ${downloaded.filepath}"
                },
                onFailure = { error ->
                    updateActive(id) { current ->
                        current.copy(
                            status = DownloadStatus.FAILED,
                            percentLabel = "Failed",
                            speedLabel = "",
                            error = error.message ?: error.toString(),
                        )
                    }
                    snackbarMessage = error.message ?: "Download failed."
                }
            )
        }
    }

    fun clearRecentCompleted() {
        recentCompleted.clear()
    }

    fun removeHistoryItem(item: DownloadItem) {
        history.removeAll { it.id == item.id }
        recentCompleted.removeAll { it.id == item.id }
        persistHistory()
        expandedHistoryId = null
        if (item.uri.isNotBlank()) {
            runCatching {
                getApplication<Application>().contentResolver.delete(Uri.parse(item.uri), null, null)
            }
        }
    }

    fun openItem(item: DownloadItem) {
        expandedHistoryId = null
        val context = getApplication<Application>()
        if (item.uri.isBlank()) {
            snackbarMessage = "File isn't available on this device."
            return
        }
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(item.extension.lowercase())
            ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(item.uri), mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            ContextCompat.startActivity(context, Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null)
        }.onFailure {
            snackbarMessage = "No app can open this file."
        }
    }

    fun shareItem(item: DownloadItem) {
        expandedHistoryId = null
        val context = getApplication<Application>()
        if (item.uri.isBlank()) {
            snackbarMessage = "File isn't available on this device."
            return
        }
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(item.extension.lowercase())
            ?: "*/*"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            ContextCompat.startActivity(context, Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null)
        }.onFailure {
            snackbarMessage = "Couldn't share this file."
        }
    }

    private fun updateActive(id: String, transform: (DownloadItem) -> DownloadItem) {
        val index = activeDownloads.indexOfFirst { it.id == id }
        if (index >= 0) {
            activeDownloads[index] = transform(activeDownloads[index])
        }
    }

    private fun persistHistory() {
        historyStore.save(history.toList())
    }
}
