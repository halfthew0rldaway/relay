package dev.bleu.relay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bleu.relay.ui.components.BottomNavigationBar
import dev.bleu.relay.ui.components.NavTab
import dev.bleu.relay.ui.theme.*
import dev.bleu.relay.util.formatFileSize
import dev.bleu.relay.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KonfirmasiPenerimaanScreen(
    vm: MainViewModel,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val sender by vm.incomingSender.collectAsState()
    val files by vm.incomingFiles.collectAsState()
    val totalBytes = files.sumOf { it.sizeBytes }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.LeakAdd, contentDescription = null, tint = Primary)
                    Text("Relay", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Primary)
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
            Text("INCOMING TRANSFER", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "From ${sender?.name ?: "Unknown"}",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp
            )
            sender?.ipAddress?.let { ip ->
                Text(ip, color = Outline, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(20.dp))

            Surface(
                color = SurfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("FILES", color = Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("${files.size}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("TOTAL SIZE", color = Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(formatFileSize(totalBytes), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files) { pf ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = SurfaceContainerLow,
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SecondaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(pf.extension, color = OnSecondaryContainer, fontSize = 9.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pf.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                                Text(pf.sizeLabel, color = Outline, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Error)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reject", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        vm.startReceive()
                        onAccept()
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Accept", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

