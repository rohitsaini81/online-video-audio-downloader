package com.rohit.downloaderpro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class DownloadHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<DownloadItem> {
        val raw = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.getJSONObject(index)
                add(
                    DownloadItem(
                        id = json.optString("id"),
                        url = json.optString("url"),
                        title = json.optString("title"),
                        source = json.optString("source"),
                        formatLabel = json.optString("formatLabel"),
                        extension = json.optString("extension"),
                        status = runCatching {
                            DownloadStatus.valueOf(json.optString("status", DownloadStatus.COMPLETED.name))
                        }.getOrDefault(DownloadStatus.COMPLETED),
                        progress = 1f,
                        speedLabel = "",
                        percentLabel = "Completed",
                        sizeLabel = json.optString("sizeLabel"),
                        filepath = json.optString("filepath"),
                        uri = json.optString("uri"),
                        completedAt = json.optLong("completedAt"),
                        fileSizeBytes = json.optLong("fileSizeBytes"),
                    )
                )
            }
        }
    }

    fun save(items: List<DownloadItem>) {
        val array = JSONArray()
        items.take(MAX_ITEMS).forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("url", item.url)
                    .put("title", item.title)
                    .put("source", item.source)
                    .put("formatLabel", item.formatLabel)
                    .put("extension", item.extension)
                    .put("status", item.status.name)
                    .put("sizeLabel", item.sizeLabel)
                    .put("filepath", item.filepath)
                    .put("uri", item.uri)
                    .put("completedAt", item.completedAt)
                    .put("fileSizeBytes", item.fileSizeBytes)
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "download_history"
        private const val KEY_ITEMS = "items"
        private const val MAX_ITEMS = 100
    }
}
