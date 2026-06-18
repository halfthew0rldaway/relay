package dev.bleu.relay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bleu.relay.ui.components.BottomNavigationBar
import dev.bleu.relay.ui.components.NavTab
import dev.bleu.relay.ui.theme.*
import dev.bleu.relay.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PengaturanScreen(
    vm: MainViewModel,
    onHistoryClick: () -> Unit,
    onTransferClick: () -> Unit
) {
    val deviceName by vm.deviceName.collectAsState()
    val downloadPath by vm.downloadPath.collectAsState()
    val autoAccept by vm.autoAccept.collectAsState()
    var editingName by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf(deviceName) }

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
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(50))
                            .background(SurfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = deviceName.take(2).uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        bottomBar = {
            BottomNavigationBar(active = NavTab.SETTINGS, onTransfer = onTransferClick, onHistory = onHistoryClick, onSettings = {})
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("SYSTEM CONFIGURATION", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(2.dp))
            Text("Settings", fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
            Box(modifier = Modifier.width(48.dp).height(4.dp).clip(RoundedCornerShape(50)).background(Primary))
            Spacer(Modifier.height(8.dp))

            // Device Identity card
            SettingCard(
                tag = "DEVICE IDENTITY",
                title = deviceName,
                subtitle = "Visible to other nodes on the local network",
                trailingIcon = Icons.Filled.Edit,
                onClick = {
                    nameInput = deviceName
                    editingName = true
                }
            )

            // Storage path card
            SettingCard(
                tag = "STORAGE PATH",
                title = downloadPath,
                subtitle = "Files received will be saved here",
                trailingIcon = Icons.Filled.Folder,
                chips = listOf("EXT4", "Write Access")
            )

            // Auto-accept toggle
            Surface(
                color = SurfaceContainerLow,
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("NETWORK LOGIC", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Auto-accept trusted", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Bypass confirmation for devices in your verified list.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                    Switch(
                        checked = autoAccept,
                        onCheckedChange = { vm.toggleAutoAccept() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = SurfaceContainerHighest
                        )
                    )
                }
            }

            // About card
            SettingCard(
                tag = "SYSTEM BUILD",
                title = "About",
                subtitle = "Version 1.0.0 (Build 1)",
                trailingIcon = Icons.Filled.Info
            )

            Spacer(Modifier.height(4.dp))
            Text(
                "RELAY • END-TO-END ENCRYPTED",
                color = Outline,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (editingName) {
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("Device Name") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.updateDeviceName(nameInput.trim().ifBlank { "Relay Device" })
                        editingName = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingName = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingCard(
    tag: String,
    title: String,
    subtitle: String,
    trailingIcon: ImageVector? = null,
    chips: List<String> = emptyList(),
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
        onClick = onClick ?: {}
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tag, color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(6.dp))
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                if (chips.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        chips.forEach { chip ->
                            Surface(color = SecondaryContainer, shape = RoundedCornerShape(6.dp)) {
                                Text(chip, color = OnSecondaryContainer, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            }
            if (trailingIcon != null) {
                Icon(trailingIcon, contentDescription = null, tint = Outline, modifier = Modifier.size(22.dp))
            }
        }
    }
}
