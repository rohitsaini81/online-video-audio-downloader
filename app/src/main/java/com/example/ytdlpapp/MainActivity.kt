package com.example.ytdlpapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.ytdlpapp.ui.theme.YtDlpAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YtDlpAppTheme {
                YtDlpDownloaderScreen()
            }
        }
    }
}

@Composable
fun YtDlpDownloaderScreen() {
    val context = LocalContext.current
    val runner = remember(context) { YtDlpRunner(context) }
    val scope = rememberCoroutineScope()

    var url by rememberSaveable {
        mutableStateOf("https://www.youtube.com/watch?v=YOUR_TEST_VIDEO")
    }
    var isDownloading by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("Paste a video URL and tap Download.") }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            YtDlpDownloaderContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                url = url,
                isDownloading = isDownloading,
                status = status,
                onUrlChange = { url = it },
                onDownloadClick = {
                    val trimmedUrl = url.trim()
                    if (trimmedUrl.isEmpty()) {
                        status = "Enter a URL first."
                    } else {
                        isDownloading = true
                        status = "Starting download..."

                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runner.download(trimmedUrl)
                            }

                            status = result.fold(
                                onSuccess = { output ->
                                    if (output.isBlank()) {
                                        "Download finished."
                                    } else {
                                        "Download finished.\n\n$output"
                                    }
                                },
                                onFailure = { error ->
                                    "Download failed:\n${error.message ?: error.toString()}"
                                }
                            )
                            isDownloading = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun YtDlpDownloaderContent(
    modifier: Modifier = Modifier,
    url: String,
    isDownloading: Boolean,
    status: String,
    onUrlChange: (String) -> Unit,
    onDownloadClick: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "YT-DLP Downloader",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = url,
            onValueChange = onUrlChange,
            label = { Text("Video URL") },
            placeholder = { Text("Paste or enter a URL") },
            singleLine = true,
            enabled = !isDownloading
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onDownloadClick,
            enabled = !isDownloading
        ) {
            Text("Download")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isDownloading) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(showBackground = true)
@Composable
fun YtDlpDownloaderScreenPreview() {
    YtDlpAppTheme {
        YtDlpDownloaderContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            url = "https://www.youtube.com/watch?v=YOUR_TEST_VIDEO",
            isDownloading = false,
            status = "Paste a video URL and tap Download.",
            onUrlChange = {},
            onDownloadClick = {}
        )
    }
}
