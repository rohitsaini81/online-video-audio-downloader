package com.example.ytdlpapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytdlpapp.ui.theme.Accent
import com.example.ytdlpapp.ui.theme.AccentDim
import com.example.ytdlpapp.ui.theme.AppBg
import com.example.ytdlpapp.ui.theme.AudioBlue
import com.example.ytdlpapp.ui.theme.BorderColor
import com.example.ytdlpapp.ui.theme.BottomNavBg
import com.example.ytdlpapp.ui.theme.CardBg
import com.example.ytdlpapp.ui.theme.InstagramPink
import com.example.ytdlpapp.ui.theme.MusicBlue
import com.example.ytdlpapp.ui.theme.Placeholder
import com.example.ytdlpapp.ui.theme.SheetBg
import com.example.ytdlpapp.ui.theme.TextMain
import com.example.ytdlpapp.ui.theme.TextMuted
import com.example.ytdlpapp.ui.theme.YouTubeIconBg
import com.example.ytdlpapp.ui.theme.YouTubeRed
import com.example.ytdlpapp.ui.theme.YtDlpAppTheme

class MainActivity : ComponentActivity() {
    private val viewModel: DownloaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YtDlpAppTheme(dynamicColor = false) {
                DownloaderApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderApp(viewModel: DownloaderViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(viewModel.snackbarMessage) {
        val message = viewModel.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeSnackbar()
    }

    Scaffold(
        containerColor = AppBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { AppHeader() },
        bottomBar = {
            AppBottomBar(
                selected = viewModel.selectedTab,
                onSelected = viewModel::onTabSelected,
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            when (viewModel.selectedTab) {
                AppTab.Home -> HomeScreen(viewModel)
                AppTab.Downloads -> DownloadsScreen(viewModel)
                AppTab.History -> HistoryScreen(viewModel)
            }
        }
    }

    if (viewModel.showFormatSheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeFormatSheet,
            sheetState = sheetState,
            containerColor = SheetBg,
            contentColor = TextMain,
        ) {
            FormatSheetContent(viewModel)
        }
    }

    if (viewModel.showMorePlatforms) {
        AlertDialog(
            onDismissRequest = viewModel::toggleMorePlatforms,
            containerColor = CardBg,
            title = { Text("Supported platforms", color = TextMain) },
            text = {
                Text(
                    "TikTok, Facebook, X, Threads, Twitch, and Vimeo work through the same paste-and-download flow.",
                    color = TextMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::toggleMorePlatforms) {
                    Text("Got it", color = Accent)
                }
            }
        )
    }
}

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentDim),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Android Downloader Pro",
                color = TextMain,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Icon(
            imageVector = Icons.Filled.NotificationsNone,
            contentDescription = "Notifications",
            tint = TextMuted,
        )
    }
    HorizontalDivider(color = BorderColor, thickness = 1.dp)
}

@Composable
private fun AppBottomBar(
    selected: AppTab,
    onSelected: (AppTab) -> Unit,
) {
    NavigationBar(
        containerColor = BottomNavBg,
        contentColor = TextMuted,
        modifier = Modifier.navigationBarsPadding(),
    ) {
        AppTab.entries.forEach { tab ->
            val isSelected = selected == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelected(tab) },
                icon = {
                    Icon(
                        imageVector = when (tab) {
                            AppTab.Home -> Icons.Filled.Home
                            AppTab.Downloads -> Icons.Filled.Download
                            AppTab.History -> Icons.Filled.History
                        },
                        contentDescription = tab.name,
                    )
                },
                label = {
                    Text(
                        text = tab.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Accent,
                    selectedTextColor = Accent,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                    indicatorColor = AccentDim,
                )
            )
        }
    }
}

@Composable
private fun HomeScreen(viewModel: DownloaderViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            text = "Paste YouTube or Instagram Link",
            color = TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        UrlInputRow(
            url = viewModel.url,
            onUrlChange = viewModel::onUrlChange,
            onDownload = { viewModel.requestDownload("video") },
        )
        Spacer(Modifier.height(20.dp))

        FeatureCard(
            icon = Icons.Outlined.PlayArrow,
            iconBg = YouTubeIconBg,
            title = "YouTube Video",
            subtitle = "MP4 formats in 1080p, 720p, 480p",
            onClick = { viewModel.requestDownload("video") },
        )
        Spacer(Modifier.height(12.dp))
        FeatureCard(
            icon = Icons.Filled.MusicNote,
            iconBg = MusicBlue,
            title = "YouTube Audio",
            subtitle = "Shorts & music tracks to MP3, M4A",
            onClick = { viewModel.requestDownload("audio") },
        )
        Spacer(Modifier.height(12.dp))
        FeatureCard(
            icon = Icons.Filled.Videocam,
            iconBg = Brush.linearGradient(listOf(Color(0xFFF59E0B), InstagramPink, Color(0xFF8B5CF6))),
            title = "Instagram Reels",
            subtitle = "Download reels & video clips directly",
            onClick = { viewModel.requestDownload("video") },
        )
        Spacer(Modifier.height(14.dp))
        MorePlatformsCard(onClick = viewModel::toggleMorePlatforms)
    }
}

@Composable
private fun UrlInputRow(
    url: String,
    onUrlChange: (String) -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Paste link here...", color = Placeholder, fontSize = 13.sp) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Accent,
                focusedTextColor = TextMain,
                unfocusedTextColor = TextMain,
            ),
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Accent)
                .clickable(onClick = onDownload)
                .padding(horizontal = 15.dp, vertical = 9.dp),
        ) {
            Text(
                text = "Download",
                color = AppBg,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    iconBg: Any,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconModifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (iconBg is Brush) Modifier.background(iconBg)
                else Modifier.background(iconBg as Color)
            )
        Box(modifier = iconModifier, contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MorePlatformsCard(onClick: () -> Unit) {
    val stroke = Stroke(
        width = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF142832), Color(0xFF101C24))))
            .drawBehind {
                drawRoundRect(
                    color = Accent.copy(alpha = 0.5f),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = stroke,
                )
            }
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = AppBg)
        }
        Spacer(Modifier.height(8.dp))
        Text("Tap for More Platforms", color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("TikTok", color = TextMuted, fontSize = 12.sp)
            Text("Facebook", color = TextMuted, fontSize = 12.sp)
            Text("X", color = TextMuted, fontSize = 12.sp)
            Text("Twitch", color = TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DownloadsScreen(viewModel: DownloaderViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        SectionTitle("In Progress")
        if (viewModel.activeDownloads.isEmpty()) {
            EmptyHint("Downloads in progress will show up here.")
        } else {
            viewModel.activeDownloads.forEach { item ->
                ActiveDownloadCard(item)
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle(
            title = "Completed",
            action = if (viewModel.recentCompleted.isNotEmpty()) "Clear All" else null,
            onAction = viewModel::clearRecentCompleted,
        )
        if (viewModel.recentCompleted.isEmpty()) {
            EmptyHint("Finished files will appear here after a download.")
        } else {
            viewModel.recentCompleted.forEach { item ->
                HistoryCard(
                    item = item,
                    subtitle = "Completed • ${item.sizeLabel.ifBlank { item.extension.uppercase() }}",
                    onMenu = { viewModel.toggleHistoryMenu(item.id) },
                    expanded = viewModel.expandedHistoryId == item.id,
                    onPlay = { viewModel.openItem(item) },
                    onShare = { viewModel.shareItem(item) },
                    onDelete = { viewModel.removeHistoryItem(item) },
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun HistoryScreen(viewModel: DownloaderViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardBg)
                .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
            TextField(
                value = viewModel.historyQuery,
                onValueChange = viewModel::onHistoryQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search downloaded history...", color = Placeholder, fontSize = 13.sp) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Accent,
                    focusedTextColor = TextMain,
                    unfocusedTextColor = TextMain,
                ),
            )
        }
        Spacer(Modifier.height(16.dp))
        SectionTitle("Past Downloads")
        val items = viewModel.filteredHistory
        if (items.isEmpty()) {
            EmptyHint("Nothing in history yet. Completed downloads are saved here.")
        } else {
            items.forEach { item ->
                HistoryCard(
                    item = item,
                    subtitle = listOf(
                        item.source,
                        relativeTime(item.completedAt),
                        item.sizeLabel.ifBlank { item.extension.uppercase() },
                    ).filter { it.isNotBlank() }.joinToString(" • "),
                    onMenu = { viewModel.toggleHistoryMenu(item.id) },
                    expanded = viewModel.expandedHistoryId == item.id,
                    onPlay = { viewModel.openItem(item) },
                    onShare = { viewModel.shareItem(item) },
                    onDelete = { viewModel.removeHistoryItem(item) },
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun FormatSheetContent(viewModel: DownloaderViewModel) {
    val info = viewModel.mediaInfo
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("Select Download Format", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))

        if (viewModel.isFetchingFormats || info == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = Accent, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Fetching available formats…", color = TextMuted, fontSize = 13.sp)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBg)
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = Accent)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = info.title,
                    color = TextMain,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val videoOptions = info.options.filter { it.category == "video" }
            val audioOptions = info.options.filter { it.category == "audio" }

            if (videoOptions.isNotEmpty()) {
                Text(
                    "VIDEO (MP4)",
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                )
                videoOptions.forEach { option ->
                    FormatRow(
                        option = option,
                        selected = option.id == viewModel.selectedFormatId,
                        icon = Icons.Filled.Videocam,
                        onClick = { viewModel.selectFormat(option.id) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (audioOptions.isNotEmpty()) {
                Text(
                    "AUDIO (MP3 / M4A)",
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                audioOptions.forEach { option ->
                    FormatRow(
                        option = option,
                        selected = option.id == viewModel.selectedFormatId,
                        icon = Icons.Filled.Headphones,
                        onClick = { viewModel.selectFormat(option.id) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            val selected = viewModel.selectedFormat
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 18.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Accent)
                    .clickable(enabled = selected != null, onClick = viewModel::confirmSelectedDownload)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (selected != null) "Download in ${selected.label.substringBefore(" (")}" else "Download",
                    color = AppBg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun FormatRow(
    option: FormatChoice,
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Accent.copy(alpha = 0.08f) else CardBg)
            .border(1.dp, if (selected) Accent else BorderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = TextMain, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(option.label, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        if (option.sizeLabel.isNotBlank()) {
            Text(option.sizeLabel, color = TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ActiveDownloadCard(item: DownloadItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF232C3D)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (item.extension == "mp3" || item.extension == "m4a") Icons.Filled.Headphones else Icons.Filled.Videocam,
                    contentDescription = null,
                    tint = Accent,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = TextMain,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${item.source} • ${item.formatLabel}",
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.status == DownloadStatus.FAILED && item.error.isNotBlank()) {
                    Text(item.error, color = YouTubeRed, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { item.progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(10.dp)),
            color = Accent,
            trackColor = BorderColor,
        )
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = listOf(item.percentLabel, item.speedLabel).filter { it.isNotBlank() }.joinToString(" • "),
                color = TextMuted,
                fontSize = 10.sp,
            )
            Text(item.sizeLabel, color = TextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun HistoryCard(
    item: DownloadItem,
    subtitle: String,
    onMenu: () -> Unit,
    expanded: Boolean,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E2837)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = historyIcon(item),
                    contentDescription = null,
                    tint = historyTint(item),
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = item.extension.uppercase().ifBlank { "FILE" },
                    color = Accent,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.Black, RoundedCornerShape(3.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = TextMain,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(subtitle, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = TextMuted)
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniAction("Play", Icons.Outlined.PlayArrow, Accent, onPlay)
                MiniAction("Share", Icons.Filled.Share, TextMain, onShare)
                MiniAction("Delete", Icons.Outlined.Delete, YouTubeRed, onDelete)
            }
        }
    }
}

@Composable
private fun MiniAction(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E2837))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (action != null && onAction != null) {
            Text(
                text = action,
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onAction),
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        color = TextMuted,
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

private fun historyIcon(item: DownloadItem): ImageVector {
    return when {
        item.extension.equals("mp3", true) || item.extension.equals("m4a", true) -> Icons.Filled.AudioFile
        item.source.equals("Instagram", true) -> Icons.Filled.Videocam
        item.status == DownloadStatus.COMPLETED -> Icons.Filled.CheckCircle
        else -> Icons.Filled.Videocam
    }
}

private fun historyTint(item: DownloadItem): Color {
    return when {
        item.extension.equals("mp3", true) || item.extension.equals("m4a", true) -> AudioBlue
        item.source.equals("Instagram", true) -> InstagramPink
        item.source.equals("YouTube", true) -> YouTubeRed
        else -> Accent
    }
}

private fun relativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val delta = System.currentTimeMillis() - timestamp
    val minutes = delta / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "Yesterday"
        days < 7 -> "$days days ago"
        else -> "${days / 7}w ago"
    }
}
