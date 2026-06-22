package dev.bleu.relay.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import dev.bleu.relay.data.db.TransferDirection
import dev.bleu.relay.data.db.TransferRecord
import dev.bleu.relay.data.db.TransferStatus
import dev.bleu.relay.ui.components.AnimatedEmptyState
import dev.bleu.relay.ui.components.BottomNavigationBar
import dev.bleu.relay.ui.components.NavTab
import dev.bleu.relay.ui.theme.*
import dev.bleu.relay.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiwayatTransferScreen(
    vm: MainViewModel,
    onTransferClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val allRecords by vm.transferHistory.collectAsState()
    var filter by remember { mutableStateOf(0) } // 0=All, 1=Sent, 2=Received

    val displayed = when (filter) {
        1 -> allRecords.filter { it.direction == TransferDirection.SENT }
        2 -> allRecords.filter { it.direction == TransferDirection.RECEIVED }
        else -> allRecords
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
                        Icon(Icons.Filled.LeakAdd, contentDescription = null, tint = Primary)
                        Text("Relay", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Primary)
                    }
                }
            }
        },
        bottomBar = {
            BottomNavigationBar(active = NavTab.HISTORY, onTransfer = onTransferClick, onHistory = {}, onSettings = onSettingsClick)
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
                start = 24.dp,
                end = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        text = "History",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = "${allRecords.size} Transfers Logged",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    // Segmented control
                    Surface(
                        color = SurfaceContainerLow,
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp)
                        ) {
                            listOf("All", "Sent", "Received").forEachIndexed { idx, label ->
                                val selected = filter == idx
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) Primary else Color.Transparent)
                                        .clickable { filter = idx }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (displayed.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedEmptyState(message = "No transfer records yet.")
                    }
                }
            } else {
                items(displayed) { record ->
                    TransferHistoryItem(record = record, onClick = {
                        if (record.direction == TransferDirection.RECEIVED && record.status == TransferStatus.SUCCESS) {
                            openReceivedFile(context, record.fileName)
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun TransferHistoryItem(record: TransferRecord, onClick: () -> Unit = {}) {
    val isSent = record.direction == TransferDirection.SENT
    val isSuccess = record.status == TransferStatus.SUCCESS
    val isFailed = record.status == TransferStatus.FAILED

    val iconBg = when {
        isFailed -> Error.copy(alpha = 0.15f)
        isSent   -> PrimaryContainer.copy(alpha = 0.12f)
        else     -> Secondary.copy(alpha = 0.12f)
    }
    val icon = when {
        isFailed -> Icons.Filled.Error
        isSent   -> Icons.Filled.Upload
        else     -> Icons.Filled.Download
    }
    val iconTint = when {
        isFailed -> Error
        isSent   -> PrimaryContainer
        else     -> Secondary
    }
    val statusColor = when {
        isFailed -> Error
        isSent   -> Primary
        else     -> Secondary
    }
    val statusLabel = when (record.status) {
        TransferStatus.SUCCESS   -> if (isSent) "Sent" else "Received"
        TransferStatus.FAILED    -> "Failed"
        TransferStatus.CANCELLED -> "Cancelled"
    }
    val statusIcon = when (record.status) {
        TransferStatus.SUCCESS   -> Icons.Filled.CheckCircle
        TransferStatus.FAILED    -> Icons.Filled.Cancel
        TransferStatus.CANCELLED -> Icons.Filled.Cancel
    }

    val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        .format(Date(record.timestampMs))

    val isClickable = record.direction == TransferDirection.RECEIVED && record.status == TransferStatus.SUCCESS

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(if (isClickable) Modifier.clickable(onClick = onClick) else Modifier),
        color = SurfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(record.fileName, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SecondaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(record.extension, color = OnSecondaryContainer, fontSize = 9.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    }
                    Text(record.sizeLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Box(modifier = Modifier.size(4.dp).clip(RoundedCornerShape(50)).background(OutlineVariant))
                    Text(dateStr, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(statusLabel, color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(4.dp))
                Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(22.dp))
            }
        }
    }
}

private fun openReceivedFile(context: android.content.Context, fileName: String) {
    val resolver = context.contentResolver
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Downloads.EXTERNAL_CONTENT_URI
    }
    val projection = arrayOf(MediaStore.Downloads._ID)
    val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
    val selectionArgs = arrayOf(fileName, "%Relay%")
    resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
            val uri = Uri.withAppendedPath(collection, id.toString())
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, resolver.getType(uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open file"))
        }
    }
}
