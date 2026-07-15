package dev.bleu.relay.ui.screens

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bleu.relay.ui.components.AnimatedSuccessState
import dev.bleu.relay.ui.components.AnimatedErrorState
import dev.bleu.relay.ui.components.BottomNavigationBar
import dev.bleu.relay.ui.components.NavTab
import androidx.compose.animation.core.animateFloatAsState
import dev.bleu.relay.ui.theme.*
import dev.bleu.relay.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgresPenerimaanScreen(
    vm: MainViewModel,
    onCancel: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val progress by vm.transferProgress.collectAsState()
    val sender by vm.incomingSender.collectAsState()

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
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("INBOUND TRANSFER", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(
                "Receiving",
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp
            )
            Text(
                "← ${sender?.name ?: "Device"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(8.dp))

            Surface(
                color = SurfaceContainerLow,
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = progress.percentLabel,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-2).sp,
                        color = if (progress.isDone) Primary else MaterialTheme.colorScheme.onSurface
                    )

                    val animatedProgress by animateFloatAsState(targetValue = progress.fraction, label = "progress")
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Secondary,
                        trackColor = SurfaceContainerHighest
                    )

                    Text(
                        progress.fileName.ifBlank { "Waiting…" },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("SPEED", color = Primary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text(progress.speedLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("ETA", color = Primary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text(progress.etaLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }

                    if (progress.isDone) {
                        AnimatedSuccessState(message = "File(s) saved to Downloads/Relay.", modifier = Modifier.padding(top = 8.dp))
                    }

                    if (progress.isFailed || progress.isCancelled) {
                        AnimatedErrorState(
                            message = if (progress.isCancelled) "Transfer cancelled." else (progress.errorMessage ?: "Transfer failed."),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    if (!progress.isDone) {
                        vm.cancelTransfer()
                    }
                    onCancel()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (progress.isDone) Primary else SurfaceContainerHighest,
                    contentColor = if (progress.isDone) Color.White else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(if (progress.isDone) "Done" else "Cancel", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
