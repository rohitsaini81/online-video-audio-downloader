package com.example.ytdlpapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ytdlpapp.ui.theme.YtDlpAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import androidx.compose.foundation.shape.RoundedCornerShape

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YtDlpAppTheme {
                YtDlpRuntimeScreen()
            }
        }
    }
}

private enum class DownloadMode(val label: String) {
    VIDEO("Video"),
    AUDIO("Audio"),
}

@Composable
fun YtDlpRuntimeScreen() {
    val context = LocalContext.current
    val runner = remember(context) { YtDlpRunner(context) }
    val scope = rememberCoroutineScope()
    val logs = remember { mutableStateListOf<String>() }

    var url by rememberSaveable {
        mutableStateOf("https://youtu.be/YmaU-VeCtoA?si=SLo5rWdh3OAjcEGL")
    }
    var isBusy by rememberSaveable { mutableStateOf(false) }
    var downloadMode by rememberSaveable { mutableStateOf(DownloadMode.VIDEO) }
    var status by rememberSaveable {
        mutableStateOf("Ready to probe Chaquopy and yt-dlp.")
    }
    var runtimeSummary by rememberSaveable {
        mutableStateOf("Tap Probe Runtime to verify Python and yt-dlp.")
    }

    suspend fun refreshProbe() {
        isBusy = true
        status = "Starting Python runtime..."
        logs.clear()
        logs.add("Loading embedded Python module...")

        val result = withContext(Dispatchers.IO) {
            runner.probe()
        }

        result.fold(
            onSuccess = { payload ->
                val parsed = parseProbePayload(payload)
                runtimeSummary = buildString {
                    appendLine("Python: ${parsed.pythonVersion}")
                    appendLine("yt-dlp: ${parsed.ytDlpVersion}")
                    append("yt-dlp path: ${parsed.ytDlpPath}")
                }
                status = "Python imported yt-dlp successfully."
                logs.add("Probe OK")
                logs.add("Python version: ${parsed.pythonVersion}")
                logs.add("yt-dlp version: ${parsed.ytDlpVersion}")
                logs.add("yt-dlp path: ${parsed.ytDlpPath}")
            },
            onFailure = { error ->
                runtimeSummary = "Probe failed."
                status = "Probe failed: ${error.message ?: error.toString()}"
                logs.add(status)
            }
        )

        isBusy = false
    }

    fun startDownload() {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            status = "Enter a URL first."
            return
        }

        scope.launch {
            isBusy = true
            status = "Downloading with yt-dlp..."
            logs.clear()
            logs.add("URL: $trimmed")
            logs.add("Mode: ${downloadMode.label}")

            val result = withContext(Dispatchers.IO) {
                runner.download(
                    url = trimmed,
                    audioOnly = downloadMode == DownloadMode.AUDIO,
                ) { line ->
                    scope.launch(Dispatchers.Main) {
                        if (line.startsWith("Downloading:") &&
                            logs.lastOrNull()?.startsWith("Downloading:") == true
                        ) {
                            logs[logs.lastIndex] = line
                        } else {
                            logs.add(line)
                        }

                        if (logs.size > 100) {
                            logs.removeAt(0)
                        }
                    }
                }
            }

            result.fold(
                onSuccess = { payload ->
                    val parsed = parseDownloadPayload(payload)
                    status = "Download finished."
                    runtimeSummary = buildString {
                        appendLine("Saved to: ${parsed.filepath}")
                        append("Output dir: ${parsed.outputDir}")
                    }
                    logs.add("Download OK")
                    logs.add("File: ${parsed.filepath}")
                    logs.add("Output dir: ${parsed.outputDir}")
                },
                onFailure = { error ->
                    status = "Download failed: ${error.message ?: error.toString()}"
                    logs.add(status)
                }
            )

            isBusy = false
        }
    }

    LaunchedEffect(Unit) {
        refreshProbe()
    }

    Scaffold(containerColor = Color.Transparent) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0B1020),
                            Color(0xFF101A33),
                            Color(0xFF1A2440)
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            YtDlpRuntimeContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                url = url,
                downloadMode = downloadMode,
                isBusy = isBusy,
                status = status,
                runtimeSummary = runtimeSummary,
                logs = logs,
                onUrlChange = { url = it },
                onDownloadModeChange = { downloadMode = it },
                onProbeClick = {
                    scope.launch {
                        refreshProbe()
                    }
                },
                onDownloadClick = {
                    startDownload()
                }
            )
        }
    }
}

@Composable
private fun YtDlpRuntimeContent(
    modifier: Modifier = Modifier,
    url: String,
    downloadMode: DownloadMode,
    isBusy: Boolean,
    status: String,
    runtimeSummary: String,
    logs: List<String>,
    onUrlChange: (String) -> Unit,
    onDownloadModeChange: (DownloadMode) -> Unit,
    onProbeClick: () -> Unit,
    onDownloadClick: () -> Unit,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF121B33).copy(alpha = 0.92f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Chaquopy yt-dlp test",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    text = "This screen verifies Python, imports yt-dlp, and can run a simple download into the app's private storage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB8C2E0)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0F1730).copy(alpha = 0.92f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Test URL",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = url,
                    onValueChange = onUrlChange,
                    label = { Text("Video URL") },
                    placeholder = { Text("Paste or edit a URL") },
                    singleLine = true,
                    enabled = !isBusy,
                )

                Text(
                    text = "Choose a yt-dlp mode",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeCard(
                        title = "Download Video",
                        description = "Downloads a progressive video file that already includes audio.",
                        selected = downloadMode == DownloadMode.VIDEO,
                        enabled = !isBusy,
                        onClick = { onDownloadModeChange(DownloadMode.VIDEO) },
                    )

                    ModeCard(
                        title = "Download Audio",
                        description = "Downloads the best audio stream in its original M4A, WebM, or other source format.",
                        selected = downloadMode == DownloadMode.AUDIO,
                        enabled = !isBusy,
                        onClick = { onDownloadModeChange(DownloadMode.AUDIO) },
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onProbeClick,
                        enabled = !isBusy
                    ) {
                        Text("Probe Runtime")
                    }

                    Button(
                        onClick = onDownloadClick,
                        enabled = !isBusy
                    ) {
                        Text("Start ${downloadMode.label} Download")
                    }
                }

                if (isBusy) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                        Text(
                            text = "Working...",
                            color = Color(0xFFE1E7F8)
                        )
                    }
                }

                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE1E7F8)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0E1529).copy(alpha = 0.92f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Runtime info",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )

                Text(
                    text = runtimeSummary,
                    color = Color(0xFFE9EEFF)
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0E1529).copy(alpha = 0.92f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Live output",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )

                if (logs.isEmpty()) {
                    Text(
                        text = "Probe and download logs will appear here.",
                        color = Color(0xFF93A2CD)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(logs) { line ->
                            Surface(
                                color = Color(0xFF18233F),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    text = line,
                                    modifier = Modifier.padding(12.dp),
                                    color = Color(0xFFE9EEFF),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val primary = Color(0xFF7CB7FF)
    val accent = Color(0xFFB8C2E0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFF172544) else Color(0xFF10192F)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 8.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (selected) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onClick,
                    enabled = enabled,
                ) {
                    Text("✓ $title")
                }
            } else {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onClick,
                    enabled = enabled,
                ) {
                    Text(title)
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = accent,
            )

            if (selected) {
                Text(
                    text = "Selected mode",
                    style = MaterialTheme.typography.labelSmall,
                    color = primary
                )
            }
        }
    }
}

private data class ProbeInfo(
    val pythonVersion: String,
    val ytDlpVersion: String,
    val ytDlpPath: String,
)

private data class DownloadInfo(
    val filepath: String,
    val outputDir: String,
)

private fun parseProbePayload(payload: String): ProbeInfo {
    val json = JSONObject(payload)
    return ProbeInfo(
        pythonVersion = json.optString("python_version", "unknown"),
        ytDlpVersion = json.optString("yt_dlp_version", "unknown"),
        ytDlpPath = json.optString("yt_dlp_path", "unknown"),
    )
}

private fun parseDownloadPayload(payload: String): DownloadInfo {
    val json = JSONObject(payload)
    return DownloadInfo(
        filepath = json.optString("filepath", "unknown"),
        outputDir = json.optString("output_dir", "unknown"),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1020)
@Composable
fun YtDlpRuntimeScreenPreview() {
    YtDlpAppTheme(dynamicColor = false) {
        YtDlpRuntimeContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            url = "https://youtu.be/YmaU-VeCtoA?si=SLo5rWdh3OAjcEGL",
            downloadMode = DownloadMode.VIDEO,
            isBusy = false,
            status = "Python imported yt-dlp successfully.",
            runtimeSummary = "Python: 3.14.5\nyt-dlp: 2026.08.19\nyt-dlp path: /data/user/0/com.example.ytdlpapp/files/chaquopy/...",
            logs = listOf(
                "Probe OK",
                "Python version: 3.14.5",
                "yt-dlp version: 2026.08.19",
                "yt-dlp path: /data/user/0/com.example.ytdlpapp/files/chaquopy/..."
            ),
            onUrlChange = {},
            onDownloadModeChange = {},
            onProbeClick = {},
            onDownloadClick = {}
        )
    }
}
