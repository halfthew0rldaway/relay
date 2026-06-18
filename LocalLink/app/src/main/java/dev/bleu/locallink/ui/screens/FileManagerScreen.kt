package dev.bleu.locallink.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.bleu.locallink.data.model.MediaCategory
import dev.bleu.locallink.data.model.MediaFile
import dev.bleu.locallink.ui.components.BottomNavigationBar
import dev.bleu.locallink.ui.components.NavTab
import dev.bleu.locallink.ui.theme.*
import dev.bleu.locallink.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onFileOpen: (MediaFile) -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val activeCategory by vm.activeCategory.collectAsState()
    val mediaFiles     by vm.mediaFiles.collectAsState()
    val loading        by vm.mediaLoading.collectAsState()
    val pinnedDevices  by vm.pinnedDevices.collectAsState()
    val hasPinnedTarget by vm.quickSendTarget.collectAsState()

    LaunchedEffect(activeCategory) {
        vm.loadMedia(activeCategory)
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Primary)
                            }
                            Column {
                                Text("Files", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Primary)
                                if (hasPinnedTarget != null) {
                                    Text(
                                        "→ ${hasPinnedTarget!!.name}",
                                        fontSize = 10.sp,
                                        color = Primary.copy(alpha = 0.7f),
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                        if (hasPinnedTarget == null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ErrorContainer.copy(alpha = 0.4f)
                            ) {
                                Text(
                                    "No pinned device",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 10.sp,
                                    color = OnErrorContainer,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Category tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MediaCategory.entries.forEach { cat ->
                            val selected = cat == activeCategory
                            Surface(
                                modifier = Modifier.clickable { vm.loadMedia(cat) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) Primary else SurfaceContainerLow
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = when (cat) {
                                            MediaCategory.IMAGES -> Icons.Filled.Image
                                            MediaCategory.VIDEOS -> Icons.Filled.VideoLibrary
                                            MediaCategory.FILES  -> Icons.Filled.Description
                                        },
                                        contentDescription = null,
                                        tint = if (selected) Color.White else Outline,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        cat.name.lowercase().replaceFirstChar { it.uppercase() },
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.4f))
                }
            }
        },
        bottomBar = {
            BottomNavigationBar(
                active = NavTab.TRANSFER,
                onTransfer = onBack,
                onHistory = onHistoryClick,
                onSettings = onSettingsClick
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
                mediaFiles.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.FolderOff, null, tint = Outline, modifier = Modifier.size(48.dp))
                        Text("No files found", color = Outline, fontSize = 14.sp)
                    }
                }
                activeCategory == MediaCategory.IMAGES || activeCategory == MediaCategory.VIDEOS ->
                    MediaGrid(files = mediaFiles, onFileClick = onFileOpen)
                else ->
                    FileList(files = mediaFiles, onFileClick = onFileOpen)
            }
        }
    }
}

@Composable
private fun MediaGrid(files: List<MediaFile>, onFileClick: (MediaFile) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(files, key = { it.uri.toString() }) { file ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onFileClick(file) }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(file.uri)
                        .crossfade(true)
                        .size(300)
                        .build(),
                    contentDescription = file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (file.category == MediaCategory.VIDEOS) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50))
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow, null,
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.Center).size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileList(files: List<MediaFile>, onFileClick: (MediaFile) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(files, key = { it.uri.toString() }) { file ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFileClick(file) },
                color = SurfaceContainerLow,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SecondaryContainer)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            file.extension, color = OnSecondaryContainer,
                            fontSize = 9.sp, fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(file.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                        Text(file.sizeLabel, color = Outline, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Outline, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
