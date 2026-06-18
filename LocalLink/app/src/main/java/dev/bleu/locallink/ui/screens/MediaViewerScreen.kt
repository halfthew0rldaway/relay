package dev.bleu.locallink.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import dev.bleu.locallink.ui.theme.*
import dev.bleu.locallink.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MediaViewerScreen(
    vm: MainViewModel,
    file: MediaFile,
    onBack: () -> Unit,
    onSendNavigate: () -> Unit
) {
    val context      = LocalContext.current
    val scope        = rememberCoroutineScope()
    val pinnedTarget by vm.quickSendTarget.collectAsState()

    var feedbackMsg  by remember { mutableStateOf("") }
    var showFeedback by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // ── Media ────────────────────────────────────────────────────────────
        if (file.category == MediaCategory.IMAGES) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(file.uri).crossfade(true).build(),
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(file.uri).crossfade(true).build(),
                    contentDescription = file.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                Icon(Icons.Filled.PlayCircle, null, tint = Color.White.copy(.75f), modifier = Modifier.size(72.dp))
            }
        }

        // ── Top bar ──────────────────────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth().height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(.7f), Color.Transparent)))
        )
        Row(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(file.name, color = Color.White, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(file.sizeLabel, color = Color.White.copy(.55f), fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace)
            }
            IconButton(onClick = {
                scope.launch {
                    val target = pinnedTarget
                    if (target != null) {
                        vm.setPendingFiles(listOf(file.toPendingFile()))
                        vm.selectDevice(target)
                        vm.startSend()
                        feedbackMsg = "Sending to ${target.name}…"
                        showFeedback = true; delay(2500); showFeedback = false
                        onSendNavigate()
                    } else {
                        feedbackMsg = "No pinned device — long-press a device to pin first"
                        showFeedback = true; delay(3000); showFeedback = false
                    }
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White)
            }
        }

        // ── Bottom info ──────────────────────────────────────────────────────
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.85f))))
                .navigationBarsPadding()
                .padding(bottom = 20.dp, top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            pinnedTarget?.let {
                Text(
                    "→ ${it.name}",
                    color = Primary.copy(.85f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            } ?: Text(
                "No pinned device — long-press a device to pin",
                color = Color(0xFFEF4444).copy(.85f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // ── Feedback toast ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showFeedback,
            enter = fadeIn() + slideInVertically { it },
            exit  = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 160.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = InverseSurface, contentColor = InverseOnSurface
            ) {
                Text(
                    feedbackMsg,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
