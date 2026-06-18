package dev.bleu.relay.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bleu.relay.data.model.PendingFile
import dev.bleu.relay.ui.components.BottomNavigationBar
import dev.bleu.relay.ui.components.NavTab
import dev.bleu.relay.ui.theme.*
import dev.bleu.relay.util.formatFileSize
import dev.bleu.relay.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PilihFileScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onSend: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val device by vm.selectedDevice.collectAsState()
    val pendingFiles by vm.pendingFiles.collectAsState()

    val totalBytes = pendingFiles.sumOf { it.sizeBytes }
    val totalLabel = formatFileSize(totalBytes)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = mutableListOf<Uri>()
            result.data?.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
            } ?: result.data?.data?.let { uris.add(it) }

            val files = uris.mapNotNull { uri ->
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use { c ->
                    if (!c.moveToFirst()) return@use null
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIdx >= 0) c.getString(nameIdx) else uri.lastPathSegment ?: "file"
                    val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
                    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    PendingFile(uri = uri, name = name, sizeBytes = size, mimeType = mime)
                }
            }
            vm.setPendingFiles(files)
        }
    }

    fun openPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        launcher.launch(intent)
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Primary)
                        }
                        Text("Relay", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Primary)
                    }
                }
            }
        },
        bottomBar = {
            BottomNavigationBar(active = NavTab.TRANSFER, onTransfer = {}, onHistory = onHistoryClick, onSettings = onSettingsClick)
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("SEND TO", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = device?.name ?: "Unknown Device",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp
            )
            device?.ipAddress?.let { ip ->
                Text(ip, color = Outline, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(20.dp))

            // File list
            if (pendingFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceContainerLow),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Outline, modifier = Modifier.size(40.dp))
                        Text("No files selected", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pendingFiles) { pf ->
                        FileRow(pf = pf, onRemove = {
                            vm.setPendingFiles(pendingFiles.filter { it !== pf })
                        })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceContainerLow)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${pendingFiles.size} file(s)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(totalLabel, color = Outline, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { openPicker() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                    Button(
                        onClick = {
                            if (pendingFiles.isNotEmpty()) {
                                vm.startSend()
                                onSend()
                            }
                        },
                        enabled = pendingFiles.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Send")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FileRow(pf: PendingFile, onRemove: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 0.dp
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
                Text(pf.extension, color = OnSecondaryContainer, fontSize = 9.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(pf.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                Text(pf.sizeLabel, color = Outline, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Outline, modifier = Modifier.size(16.dp))
            }
        }
    }
}

