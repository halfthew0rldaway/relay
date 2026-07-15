package dev.bleu.relay.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bleu.relay.data.model.DiscoveredDevice
import dev.bleu.relay.data.model.DeviceType
import dev.bleu.relay.ui.components.BottomNavigationBar
import dev.bleu.relay.ui.components.NavTab
import dev.bleu.relay.ui.components.LottieStateAnimation
import dev.bleu.relay.ui.theme.*
import dev.bleu.relay.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BerandaScreen(
    vm: MainViewModel,
    onDeviceTapped: (DiscoveredDevice) -> Unit,
    onFilesClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val devices       by vm.devices.collectAsState()
    val deviceName    by vm.deviceName.collectAsState()
    val hasSeenWelcome by vm.hasSeenWelcome.collectAsState()
    val pinnedDevices by vm.pinnedDevices.collectAsState()
    val quickTarget   by vm.quickSendTarget.collectAsState()

    if (!hasSeenWelcome) {
        WelcomeModal(onDismiss = { vm.dismissWelcome() })
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale"
    )

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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.LeakAdd, null, tint = Primary)
                        Text("Relay", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Primary)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Files button
                        IconButton(onClick = onFilesClick, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Folder, null, tint = Primary)
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(50))
                                .background(SurfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                deviceName.take(2).uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            BottomNavigationBar(
                active = NavTab.TRANSFER,
                onTransfer = {},
                onHistory = onHistoryClick,
                onSettings = onSettingsClick
            )
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text("NEARBY NODES", color = Primary, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Transfer", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .graphicsLayer {
                                        scaleX = pulseScale
                                        scaleY = pulseScale
                                    }
                                    .background(if (devices.isEmpty()) Outline else Primary, CircleShape)
                            )
                            Text(
                                if (devices.isEmpty()) "Scanning…" else "${devices.size} device(s) found",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp, fontFamily = FontFamily.Monospace
                            )
                        }
                        IconButton(onClick = { vm.refreshScan() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Refresh, null, tint = Primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Pinned target banner
            quickTarget?.let { target ->
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Primary.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.PushPin, null, tint = Primary, modifier = Modifier.size(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pinned target", color = Primary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text(target.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Primary)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "LIVE",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize = 9.sp,
                                    color = Primary,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            if (devices.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(SurfaceContainerLow).padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.WifiOff, null, tint = Outline, modifier = Modifier.size(40.dp))
                            Text("No devices detected on this network.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Text("Make sure both devices are on the same WiFi or hotspot and Relay is open.",
                                color = Outline, fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { vm.refreshScan() }, shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Refresh Scan")
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "LONG-PRESS TO PIN AS SEND TARGET",
                        color = Outline, fontSize = 9.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                    )
                }
                items(devices, key = { it.id }) { device ->
                    val isPinned = pinnedDevices.any { it.id == device.id }
                    DeviceCard(
                        device = device,
                        isPinned = isPinned,
                        modifier = Modifier.animateItemPlacement(spring(stiffness = Spring.StiffnessLow)),
                        onClick = { onDeviceTapped(device) },
                        onLongClick = { vm.togglePin(device) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceCard(
    device: DiscoveredDevice,
    isPinned: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = if (isPinned) Primary.copy(alpha = 0.08f) else SurfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(PrimaryContainer.copy(alpha = if (isPinned) 0.25f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (device.type) {
                        DeviceType.LAPTOP  -> Icons.Filled.Laptop
                        DeviceType.TABLET  -> Icons.Filled.TabletAndroid
                        DeviceType.DESKTOP -> Icons.Filled.Computer
                        else               -> Icons.Filled.PhoneAndroid
                    },
                    contentDescription = null,
                    tint = PrimaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(device.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (isPinned) {
                        Icon(Icons.Filled.PushPin, null, tint = Primary, modifier = Modifier.size(12.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("${device.ipAddress}:${device.port}", color = Outline, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Outline)
        }
    }
}

@Composable
fun WelcomeModal(onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LottieStateAnimation(
                        modifier = Modifier.fillMaxSize(),
                        rawRes = dev.bleu.relay.R.raw.lottie_welcome, // Offline welcome animation
                        fallback = {
                            Box(
                                modifier = Modifier.size(64.dp).background(Primary.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.LeakAdd, null, tint = Primary, modifier = Modifier.size(32.dp))
                            }
                        }
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("Welcome to Relay", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Primary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(
                    "A fully offline, instantaneous peer-to-peer file transfer system.\n\n" +
                    "Zero file-size limits. Zero internet. Zero compression.\n\n" +
                    "Long-press a device to pin it as your send target, then open a file and send it instantly.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Start Transferring", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}
